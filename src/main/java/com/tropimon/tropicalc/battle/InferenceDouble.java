package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.tropimon.tropicalc.battle.BattleStateTracker.ActifDouble;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.Stat;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Déductions en combat double : on sait QUI a fait QUOI à QUI grâce aux
 * messages (lanceur, capacité, cible), et chaque Pokémon est suivi à part.
 *
 * - Vitesse : dans un tour, un adversaire qui agit avant / après un de mes
 *   Pokémon (même priorité) donne un plancher / plafond à sa vitesse, et un
 *   Mouchoir Choix si aucune version sans objet n'y arrive.
 * - Dégâts reçus : la perte réelle d'un de mes Pokémon (PV exacts, lus une
 *   fois la barre immobile) comparée au maximum possible sans objet : Bandeau
 *   ou Lunettes Choix au-delà.
 * - Messages du jeu : Orbe Vie, Restes, Casque Brut, Ballon, objet perdu,
 *   talents annoncés, attribués au bon Pokémon.
 * - Deux capacités différentes sans sortir : pas d'objet Choix.
 *
 * Les faits sont rangés par espèce dans ObservationCollector, comme en simple :
 * l'estimation adverse les utilise directement.
 */
public final class InferenceDouble {

    private InferenceDouble() {
    }

    // ------------------------------------------------------------------
    // Capacités de zone
    // ------------------------------------------------------------------

    private static Map<String, String> zone = null;

    /** "foes" (tous les adversaires), "all" (allié compris) ou null (une seule cible). */
    public static String typeZone(String idCoup) {
        if (zone == null) {
            Map<String, String> z = new HashMap<>();
            try (InputStream in = InferenceDouble.class.getResourceAsStream("/assets/tropicalc/capacites-zone.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String ligne;
                    while ((ligne = r.readLine()) != null) {
                        ligne = ligne.trim();
                        if (ligne.isEmpty() || ligne.startsWith("#")) continue;
                        String[] p = ligne.split(" ");
                        if (p.length == 2) z.put(p[0], p[1]);
                    }
                }
            } catch (Exception ignored) {
            }
            zone = z;
        }
        return idCoup == null ? null : zone.get(idCoup);
    }

    // ------------------------------------------------------------------
    // PV stables de mes Pokémon (absolus, exacts de mon côté)
    // ------------------------------------------------------------------

    /** Barre de PV d'un de mes Pokémon : valeurs restées immobiles 300 ms, et effets annoncés. */
    private static final class Suivi {
        final ArrayDeque<long[]> histo = new ArrayDeque<>();   // {instant, pv}
        final ArrayDeque<Long> effets = new ArrayDeque<>();     // arrivée des messages qui changent ses PV
        int valeur = -1;
        long depuis = 0;
        boolean stable = false;
        int pvMax = 0;

        void suivre(int pv, int max) {
            long t = System.currentTimeMillis();
            pvMax = max;
            if (pv != valeur) {
                valeur = pv;
                depuis = t;
                stable = false;
                return;
            }
            if (!stable && t - depuis >= 300) {
                stable = true;
                histo.addLast(new long[]{depuis, pv});
                while (histo.size() > 40) histo.removeFirst();
            }
        }

        void noterEffet(long t) {
            effets.addLast(t);
            while (effets.size() > 60) effets.removeFirst();
        }

        /** {avant, après} le coup arrivé à l'instant t, null si pas (encore) mesurable. */
        int[] mesure(long t) {
            long dernierChangement = 0;
            int v0 = -1;
            List<Integer> suite = new ArrayList<>();
            for (long[] p : histo) {
                if (p[0] <= t) {
                    dernierChangement = p[0];
                    v0 = (int) p[1];
                } else {
                    suite.add((int) p[1]);
                }
            }
            int enAttente = 0;
            for (long e : effets) {
                if (e > dernierChangement && e < t && e > t - 3000) enAttente++;
            }
            if (enAttente >= 2) return null;
            for (long e : effets) {
                if (e > t) {
                    if (e - t < 1800) return new int[]{-2, -2};   // effet collé au coup : mesure impossible
                    break;
                }
            }
            List<Integer> valeurs = new ArrayList<>();
            if (v0 >= 0) valeurs.add(v0);
            valeurs.addAll(suite);
            if (valeurs.size() < enAttente + 2) return null;
            int avant = valeurs.get(enAttente);
            int apres = valeurs.get(enAttente + 1);
            if (avant == apres) return null;
            return new int[]{avant, apres};
        }
    }

    private static final Map<UUID, Suivi> SUIVIS = new HashMap<>();

    // ------------------------------------------------------------------
    // Actions du tour
    // ------------------------------------------------------------------

    private static final class Action {
        int ordre;
        boolean adverse;
        String cle;
        String espece;
        String idCoup;
        Pokemon pokemon;        // tel que vu au moment de l'action (stages compris)
        Pokemon estime;         // adversaire : set estimé au moment de l'action
        double vitesse;         // mon Pokémon : vitesse effective
        int stageVit;
        boolean ventArriere;
        boolean premiere;       // première action de ce Pokémon dans le tour
        boolean distorsionInitiale;
        boolean crit, multi, aide, bloqueTout;
        final Set<String> bloques = new HashSet<>();
    }

    /** Un coup sur un de mes Pokémon, mesuré quand ses PV se sont stabilisés. */
    private record CoupRecu(Action action, UUID cible, String cleCible, Pokemon defenseur, Field etat, long t) {
    }

    private static final List<Action> TOUR = new ArrayList<>();
    private static final List<CoupRecu> EN_ATTENTE = new ArrayList<>();
    private static final Set<String> AIDES = new HashSet<>();
    private static int perturbation = -1;      // à partir de cette action, l'ordre ne dit plus rien
    private static boolean distorsionTour = false;

    // Pas d'objet Choix : première capacité de chaque adversaire depuis son entrée
    private static final Map<String, String> PREMIER_COUP = new HashMap<>();
    private static final Map<String, Long> VU = new HashMap<>();

    private static final Set<String> COUPS_APPELANTS = Set.of(
        "sleeptalk", "metronome", "copycat", "mefirst", "assist", "mirrormove", "naturepower", "struggle");
    private static final Set<String> COUPS_ORDRE = Set.of("afteryou", "quash", "instruct");

    public static void vider() {
        SUIVIS.clear();
        TOUR.clear();
        EN_ATTENTE.clear();
        AIDES.clear();
        PREMIER_COUP.clear();
        VU.clear();
        perturbation = -1;
        distorsionTour = false;
    }

    // ------------------------------------------------------------------
    // Tick : PV stables, coups à mesurer, adversaires sortis
    // ------------------------------------------------------------------

    public static void tick() {
        long maintenant = System.currentTimeMillis();
        Set<String> clesAdv = new HashSet<>();
        for (ActifDouble a : BattleStateTracker.getActifsJoueurDouble()) {
            if (a == null) continue;
            SUIVIS.computeIfAbsent(a.uuid(), k -> new Suivi()).suivre(a.pokemon().getPvActuels(), a.pokemon().getPvMax());
        }
        for (ActifDouble a : BattleStateTracker.getActifsAdversaireDouble()) {
            if (a != null) clesAdv.add(a.cle());
        }
        for (String c : clesAdv) VU.put(c, maintenant);
        // Adversaire sorti : son verrou Choix repart de zéro
        Iterator<Map.Entry<String, String>> it = PREMIER_COUP.entrySet().iterator();
        while (it.hasNext()) {
            String c = it.next().getKey();
            Long vu = VU.get(c);
            if (!clesAdv.contains(c) && (vu == null || maintenant - vu > 5000)) it.remove();
        }

        Iterator<CoupRecu> ic = EN_ATTENTE.iterator();
        while (ic.hasNext()) {
            CoupRecu cr = ic.next();
            if (maintenant - cr.t() < 2500) continue;
            Suivi s = SUIVIS.get(cr.cible());
            int[] m = s == null ? null : s.mesure(cr.t());
            if (m == null && maintenant - cr.t() < 9000) continue;
            ic.remove();
            evaluer(cr, m, s);
        }
    }

    private static void evaluer(CoupRecu cr, int[] m, Suivi s) {
        Action a = cr.action();
        String debut = "DOUBLE COUP REÇU " + a.espece + " " + a.idCoup + " -> " + cr.defenseur().getEspece() + " | ";
        String raison = null;
        if (a.bloqueTout || a.bloques.contains(cr.cleCible())) raison = "bloqué (Abri, raté, immunité ou Clone)";
        else if (a.crit) raison = "coup critique dans l'action : pas de conclusion";
        else if (a.multi) raison = "multi-coups : pas de conclusion";
        else if (a.aide) raison = "Coup d'Main : pas de conclusion";
        else if (m == null || s == null || s.pvMax <= 0) raison = "PV non mesurables";
        else if (m[0] == -2) raison = "autre effet sur ses PV juste après le coup";
        if (raison != null) {
            MessageDebugLogger.analyse(debut + raison);
            return;
        }
        int avant = m[0], apres = m[1];
        boolean ko = apres <= 0;
        double perte = 100.0 * (avant - apres) / s.pvMax;
        if (perte <= 0) {
            MessageDebugLogger.analyse(debut + "PV en hausse : ignoré");
            return;
        }
        String ligne;
        try {
            ligne = ObservationCollector.analyserCoupRecuDouble(a.pokemon, a.estime, cr.defenseur(), a.idCoup, cr.etat(), perte, ko);
        } catch (Exception e) {
            ligne = "erreur : " + e;
        }
        MessageDebugLogger.analyse(debut + "PV " + avant + " -> " + apres + "/" + s.pvMax + " | " + ligne);
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;
        Object[] args = contenu.getArgs();
        long t = System.currentTimeMillis();

        try {
            if (cle.equals("cobblemon.battle.turn")) {
                fermerTour();
                return;
            }
            if (cle.equals("cobblemon.battle.used_move") || cle.equals("cobblemon.battle.used_move_on")) {
                actionUtilisee(args, t);
                return;
            }
            Action derniere = TOUR.isEmpty() ? null : TOUR.get(TOUR.size() - 1);
            if (cle.equals("cobblemon.battle.crit")) {
                if (derniere != null) derniere.crit = true;
                return;
            }
            if (cle.startsWith("cobblemon.battle.hit_count")) {
                if (derniere != null) derniere.multi = true;
                return;
            }
            if (cle.equals("cobblemon.battle.singleturn.helpinghand")) {
                String aide = args.length > 0 ? cleDe(args[0]) : null;
                if (aide != null) AIDES.add(aide);
                return;
            }
            if (cle.equals("cobblemon.battle.activate.protect") || cle.equals("cobblemon.battle.immune")
                    || cle.equals("cobblemon.battle.missed") || cle.equals("cobblemon.battle.fail")
                    || cle.equals("cobblemon.battle.nothing") || cle.equals("cobblemon.battle.activate.substitute")) {
                if (derniere != null) {
                    String bloque = args.length > 0 ? cleDe(args[0]) : null;
                    if (bloque != null) derniere.bloques.add(bloque);
                    else derniere.bloqueTout = true;
                }
                return;
            }

            // Ce qui change les vitesses au milieu du tour : l'ordre qui suit ne dit plus rien
            if (perturbeVitesse(cle, contenu)) perturbation = perturbation < 0 ? TOUR.size() : Math.min(perturbation, TOUR.size());
            if (cle.equals("cobblemon.battle.fieldstart.trickroom") || cle.equals("cobblemon.battle.fieldend.trickroom")) {
                distorsionTour = true;
            }

            effetsPv(cle, args, t, derniere);
            reveler(cle, args);
        } catch (Exception ignored) {
        }
    }

    private static boolean perturbeVitesse(String cle, TranslatableTextContent contenu) {
        if (cle.contains("tailwind") || cle.contains("trickroom") || cle.contains("quickclaw")
                || cle.contains("quickdraw") || cle.contains("custap") || cle.equals("cobblemon.status.paralysis.apply")
                || cle.startsWith("cobblemon.battle.enditem.") || cle.equals("cobblemon.battle.item.eat")
                || cle.startsWith("cobblemon.battle.fieldstart.") || cle.startsWith("cobblemon.battle.weather.")) {
            return !cle.endsWith(".upkeep");
        }
        if (cle.startsWith("cobblemon.battle.boost.") || cle.startsWith("cobblemon.battle.unboost.")) {
            for (Object arg : contenu.getArgs()) {
                if (arg instanceof Text tx && tx.getContent() instanceof TranslatableTextContent sc
                        && sc.getKey() != null && sc.getKey().startsWith("cobblemon.stat.speed")) return true;
            }
        }
        return false;
    }

    private static void actionUtilisee(Object[] args, long t) {
        String auteur = null, idCoup = null, cible = null;
        for (Object arg : args) {
            if (!(arg instanceof Text tx) || !(tx.getContent() instanceof TranslatableTextContent sc)) continue;
            String k = sc.getKey();
            if (k == null) continue;
            if (k.startsWith("cobblemon.move.")) {
                if (idCoup == null) idCoup = k.substring("cobblemon.move.".length());
            } else if (k.equals("cobblemon.battle.owned_pokemon")) {
                String c = cleDe(arg);
                if (auteur == null) auteur = c;
                else if (cible == null) cible = c;
            }
        }
        if (auteur == null || idCoup == null) return;
        Map<String, ActifDouble> actifs = actifsParCle();
        ActifDouble a = actifs.get(auteur);
        if (a == null) return;

        Action act = new Action();
        act.ordre = TOUR.size();
        act.adverse = auteur.startsWith("A:");
        act.cle = auteur;
        act.espece = a.pokemon().getEspece();
        act.idCoup = idCoup;
        act.premiere = true;
        for (Action x : TOUR) if (x.cle.equals(auteur)) act.premiere = false;
        act.aide = AIDES.contains(auteur);
        Field etat = FieldTracker.construireField();
        act.distorsionInitiale = FieldTracker.isDistorsion();
        act.pokemon = a.pokemon();
        for (Stat s : Stat.values()) if (s != Stat.PV) act.pokemon.setStage(s, BoostsDouble.stage(auteur, s));
        act.stageVit = act.pokemon.getStage(Stat.VITESSE);
        if (act.adverse) {
            act.ventArriere = FieldTracker.isTailwindAdversaire();
            try {
                act.estime = ObservationCollector.construireAdversaireEstime(act.pokemon);
                for (Stat s : Stat.values()) if (s != Stat.PV) act.estime.setStage(s, act.pokemon.getStage(s));
            } catch (Exception e) {
                act.estime = act.pokemon;
            }
        } else {
            act.ventArriere = FieldTracker.isTailwindJoueur();
            act.vitesse = DamageCalculator.vitesseEnCombat(act.pokemon, etat.getMeteo(), etat.getTerrain(), act.ventArriere);
        }
        TOUR.add(act);
        if (COUPS_ORDRE.contains(idCoup)) perturbation = perturbation < 0 ? TOUR.size() : Math.min(perturbation, TOUR.size());

        // Pas d'objet Choix : deux capacités différentes depuis son entrée
        if (act.adverse && act.premiere && !COUPS_APPELANTS.contains(idCoup)) {
            String premier = PREMIER_COUP.get(auteur);
            if (premier == null) {
                PREMIER_COUP.put(auteur, idCoup);
            } else if (!premier.equals(idCoup)) {
                ObservationCollector.exclureChoixDouble(act.espece, nomCoup(premier) + " puis " + nomCoup(idCoup));
            }
        }

        // Cibles de mon côté : un coup à mesurer pour chacune
        String typeZone = typeZone(idCoup);
        List<ActifDouble> touches = new ArrayList<>();
        int vivantsMoi = 0, vivantsAdv = 0;
        for (ActifDouble x : actifs.values()) {
            if (x.pokemon().getPvActuels() <= 0) continue;
            if (x.cle().startsWith("J:")) vivantsMoi++;
            else vivantsAdv++;
        }
        if (typeZone != null) {
            for (ActifDouble x : actifs.values()) {
                if (!x.cle().startsWith("J:") || x.cle().equals(auteur) || x.pokemon().getPvActuels() <= 0) continue;
                // Zone "foes" de MON Pokémon : ne touche pas mon allié
                if (!act.adverse && !"all".equals(typeZone)) continue;
                touches.add(x);
            }
        } else if (cible != null && cible.startsWith("J:") && !cible.equals(auteur)) {
            ActifDouble x = actifs.get(cible);
            if (x != null) touches.add(x);
        }
        int cibles;
        if (typeZone == null) cibles = 1;
        else if (act.adverse) cibles = vivantsMoi + ("all".equals(typeZone) ? Math.max(0, vivantsAdv - 1) : 0);
        else cibles = vivantsAdv + ("all".equals(typeZone) ? Math.max(0, vivantsMoi - 1) : 0);

        for (ActifDouble x : touches) {
            Suivi s = SUIVIS.computeIfAbsent(x.uuid(), k -> new Suivi());
            s.noterEffet(t);
            if (!act.adverse) continue;   // mon propre coup sur mon allié : seulement noté comme effet
            Pokemon def = x.pokemon();
            for (Stat st : Stat.values()) if (st != Stat.PV) def.setStage(st, BoostsDouble.stage(x.cle(), st));
            Field e = FieldTracker.construireField();
            e.setCombatDouble(true);
            e.setCibleMultiple(cibles >= 2);
            EN_ATTENTE.add(new CoupRecu(act, x.uuid(), x.cle(), def, e, t));
        }
    }

    /** Messages qui changent les PV d'un de mes Pokémon : notés, pour écarter une mesure qui les mélange. */
    private static void effetsPv(String cle, Object[] args, long t, Action derniere) {
        boolean effet = cle.startsWith("cobblemon.battle.damage.") || cle.startsWith("cobblemon.battle.heal.")
            || (cle.startsWith("cobblemon.status.") && cle.endsWith(".hurt"))
            || cle.equals("cobblemon.battle.item.eat") || cle.startsWith("cobblemon.battle.sethp.")
            || cle.startsWith("cobblemon.battle.end.futuresight") || cle.startsWith("cobblemon.battle.end.doomdesire");
        if (!effet || args.length == 0) return;
        String c = cleDe(args[0]);
        if (cle.equals("cobblemon.battle.heal.drain") && derniere != null) c = derniere.cle;   // le lanceur se soigne
        if (c == null || !c.startsWith("J:")) return;
        ActifDouble a = actifsParCle().get(c);
        if (a != null) SUIVIS.computeIfAbsent(a.uuid(), k -> new Suivi()).noterEffet(t);
    }

    /** Objets et talents annoncés par le jeu, attribués au Pokémon nommé. */
    private static void reveler(String cle, Object[] args) {
        if (args.length == 0) return;
        String c0 = cleDe(args[0]);
        String c1 = args.length > 1 ? cleDe(args[1]) : null;
        if (cle.equals("cobblemon.battle.damage.lifeorb")) {
            confirmer(c0, "Orbe Vie", "recul d'Orbe Vie annoncé");
        } else if (cle.equals("cobblemon.battle.damage.rockyhelmet")) {
            confirmer(c1, "Casque Brut", "Casque Brut annoncé");
        } else if (cle.equals("cobblemon.battle.heal.leftovers")) {
            confirmer(c0, "Restes", "soin des Restes annoncé");
        } else if (cle.equals("cobblemon.battle.heal.blacksludge")) {
            confirmer(c0, "Boue Noire", "soin de Boue Noire annoncé");
        } else if (cle.equals("cobblemon.battle.item.airballoon")) {
            confirmer(c0, "Ballon", "Ballon annoncé");
        } else if (cle.startsWith("cobblemon.battle.enditem.")) {
            String e = especeAdverse(c0);
            if (e != null) ObservationCollector.objetPerduDouble(e, cle.substring("cobblemon.battle.enditem.".length()));
        } else if (cle.equals("cobblemon.battle.ability.generic") && args.length >= 2 && args[1] instanceof String talent) {
            String e = especeAdverse(c0);
            if (e != null) ObservationCollector.confirmerTalentDouble(e, talent);
        } else if (cle.equals("cobblemon.battle.damage.roughskin") || cle.equals("cobblemon.battle.damage.ironbarbs")) {
            String e = especeAdverse(c1);
            if (e != null) ObservationCollector.confirmerTalentDouble(e, cle.endsWith("roughskin") ? "roughskin" : "ironbarbs");
        }
    }

    private static void confirmer(String cle, String objet, String raison) {
        String e = especeAdverse(cle);
        if (e != null) ObservationCollector.confirmerObjetDouble(e, objet, raison + " (double)");
    }

    private static String especeAdverse(String cle) {
        if (cle == null || !cle.startsWith("A:")) return null;
        ActifDouble a = actifsParCle().get(cle);
        return a == null ? null : a.pokemon().getEspece();
    }

    // ------------------------------------------------------------------
    // Fin du tour : vitesses
    // ------------------------------------------------------------------

    private static void fermerTour() {
        try {
            if (!distorsionTour) {
                int limite = perturbation < 0 ? TOUR.size() : perturbation;
                for (Action adv : TOUR) {
                    if (!adv.adverse || !adv.premiere || adv.ordre >= limite || adv.distorsionInitiale) continue;
                    for (Action moi : TOUR) {
                        if (moi.adverse || !moi.premiere || moi.ordre >= limite || moi.distorsionInitiale) continue;
                        ObservationCollector.observerVitesseDouble(adv.pokemon, adv.idCoup, adv.stageVit, adv.ventArriere,
                            moi.pokemon, moi.idCoup, moi.vitesse, adv.ordre < moi.ordre);
                    }
                }
            }
        } catch (Exception e) {
            MessageDebugLogger.analyse("DOUBLE VITESSE erreur : " + e);
        }
        TOUR.clear();
        AIDES.clear();
        perturbation = -1;
        distorsionTour = false;
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** Clé "J:nom" / "A:nom" d'un argument owned_pokemon, null sinon. */
    static String cleDe(Object arg) {
        if (!(arg instanceof Text tx) || !(tx.getContent() instanceof TranslatableTextContent sc)) return null;
        if (!"cobblemon.battle.owned_pokemon".equals(sc.getKey())) return null;
        Object[] a = sc.getArgs();
        if (a.length < 2 || !(a[0] instanceof String dresseur)) return null;
        Boolean adverse = ObservationCollector.determinerAttaquant(dresseur);
        if (adverse == null) return null;
        String nom = a[1] instanceof Text t ? t.getString() : String.valueOf(a[1]);
        return BattleStateTracker.cleDouble(adverse, nom);
    }

    private static Map<String, ActifDouble> actifsParCle() {
        Map<String, ActifDouble> m = new HashMap<>();
        for (ActifDouble a : BattleStateTracker.getActifsJoueurDouble()) if (a != null) m.put(a.cle(), a);
        for (ActifDouble a : BattleStateTracker.getActifsAdversaireDouble()) if (a != null) m.put(a.cle(), a);
        return m;
    }

    private static String nomCoup(String id) {
        try {
            MoveTemplate t = Moves.INSTANCE.getByName(id);
            if (t != null) return t.getDisplayName().getString();
        } catch (Exception ignored) {
        }
        return id;
    }
}
