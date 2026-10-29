package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PV réels des Pokémon de MON équipe pendant un combat.
 *
 * Pokemon.getCurrentHealth() (équipe côté client) n'est pas tenu à jour en
 * combat : Bétochef affiché à 56 % dans les overlays alors que l'écran de
 * switch du jeu le montrait à 317/317. L'écran de switch lit, lui, la
 * requête d'action envoyée par le serveur ("condition" : "317/317",
 * "0 fnt"). On fait pareil, plus le suivi du Pokémon actif en direct.
 *
 * Ordre : Pokémon actif (barre de combat en direct), sinon la valeur la plus
 * récente entre la dernière requête du serveur et le dernier passage sur le
 * terrain, sinon -1 (l'appelant garde getCurrentHealth()).
 */
public final class PvEquipe {

    private PvEquipe() {
    }

    private record Valeur(int pv, int pvMax, long instant) {
    }

    private static final Map<UUID, Valeur> DEPUIS_REQUETE = new HashMap<>();
    private static final Map<UUID, Valeur> DEPUIS_TERRAIN = new HashMap<>();
    /** Talent du Pokémon vu en dernier sur le terrain (Régé-Force). */
    private static final Map<UUID, Boolean> REGE_FORCE = new HashMap<>();
    private static Object derniereRequete = null;
    private static boolean lectureRequeteEchouee = false;

    public static void vider() {
        DEPUIS_REQUETE.clear();
        DEPUIS_TERRAIN.clear();
        REGE_FORCE.clear();
        derniereRequete = null;
    }

    /** À chaque tick client en combat. */
    static void tick() {
        long maintenant = System.currentTimeMillis();
        UUID actif = BattleStateTracker.getUuidJoueurActif();
        if (actif != null) {
            int pv = BattleStateTracker.getPvBrutJoueurActif();
            if (pv >= 0) {
                Valeur ancien = DEPUIS_TERRAIN.get(actif);
                DEPUIS_TERRAIN.put(actif, new Valeur(pv, ancien == null ? -1 : ancien.pvMax(), maintenant));
            }
            try {
                com.cobblemon.mod.common.pokemon.Pokemon complet = BattleStateTracker.getPokemonCompletJoueur();
                if (complet != null) {
                    String talent = complet.getAbility().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                    REGE_FORCE.put(actif, "regenerator".equals(talent));
                }
            } catch (Exception ignored) {
            }
        }
        lireRequete(maintenant);
    }

    /**
     * Lit l'équipe de la requête d'action en attente (side.pokemon[].condition).
     * Par réflexion : un nom d'accesseur différent dans une version de
     * Cobblemon ne doit pas casser le mod, seulement cette lecture.
     */
    private static void lireRequete(long maintenant) {
        if (lectureRequeteEchouee) return;
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        List<?> requetes = battle.getPendingActionRequests();
        if (requetes == null || requetes.isEmpty()) return;
        Object requete = requetes.get(0);
        if (requete == derniereRequete) return;
        try {
            Object side = appeler(requete, "getSide");
            if (side == null) return;
            Object liste = appeler(side, "getPokemon");
            if (!(liste instanceof List<?> pokemons)) return;
            derniereRequete = requete;
            StringBuilder journal = new StringBuilder("PV équipe (serveur) |");
            for (Object sp : pokemons) {
                String condition = String.valueOf(appeler(sp, "getCondition"));
                UUID uuid = uuidDe(sp);
                if (uuid == null) continue;
                int[] pv = lireCondition(condition);
                if (pv == null) continue;
                DEPUIS_REQUETE.put(uuid, new Valeur(pv[0], pv[1], maintenant));
                journal.append(' ').append(condition).append(';');
            }
            MessageDebugLogger.analyse(journal.toString());
        } catch (Throwable t) {
            lectureRequeteEchouee = true;
            MessageDebugLogger.analyse("PV équipe : requête illisible (" + t + ")");
        }
    }

    private static UUID uuidDe(Object sp) {
        try {
            Object u = appeler(sp, "getUuid");
            if (u instanceof UUID id) return id;
        } catch (Exception ignored) {
        }
        // Repli : "p1: <uuid>" (Cobblemon nomme les Pokémon par leur UUID)
        try {
            String ident = String.valueOf(appeler(sp, "getIdent"));
            int i = ident.indexOf(':');
            return UUID.fromString((i >= 0 ? ident.substring(i + 1) : ident).trim());
        } catch (Exception ignored) {
        }
        return null;
    }

    /** "317/317", "178/317 par", "0 fnt" → {pv, pvMax} (pvMax -1 si inconnu). */
    static int[] lireCondition(String condition) {
        if (condition == null) return null;
        String c = condition.trim();
        if (c.isEmpty()) return null;
        String premier = c.split(" ")[0];
        try {
            if (premier.contains("/")) {
                String[] p = premier.split("/");
                return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
            }
            return new int[]{Integer.parseInt(premier), -1};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object appeler(Object o, String methode) throws Exception {
        Method m = o.getClass().getMethod(methode);
        return m.invoke(o);
    }

    /**
     * % de PV donné par la dernière requête du serveur reçue AVANT cet
     * instant, -1 si aucune.
     */
    public static double pctRequeteAvant(UUID id, long instant) {
        if (id == null) return -1;
        Valeur v = DEPUIS_REQUETE.get(id);
        if (v == null || v.instant() >= instant || v.pvMax() <= 0) return -1;
        return 100.0 * v.pv() / v.pvMax();
    }

    /**
     * PV réels de ce membre de mon équipe, -1 si on n'en sait pas plus que
     * Pokemon.getCurrentHealth().
     */
    public static int pv(UUID id) {
        if (id == null) return -1;
        if (id.equals(BattleStateTracker.getUuidJoueurActif())) {
            int pv = BattleStateTracker.getPvBrutJoueurActif();
            if (pv >= 0) return pv;
        }
        Valeur requete = DEPUIS_REQUETE.get(id);
        Valeur terrain = DEPUIS_TERRAIN.get(id);
        if (terrain != null && (requete == null || terrain.instant() > requete.instant())) {
            int pv = terrain.pv();
            // Sorti depuis la dernière requête : Régé-Force lui a rendu 1/3
            if (pv > 0 && Boolean.TRUE.equals(REGE_FORCE.get(id)) && requete != null && requete.pvMax() > 0) {
                pv = Math.min(requete.pvMax(), pv + requete.pvMax() / 3);
            }
            return pv;
        }
        return requete != null ? requete.pv() : -1;
    }
}
