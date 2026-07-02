package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.Stat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Suit les changements de stats (boosts/débuffs) des deux camps en interceptant
 * les messages "cobblemon.battle.boost.*" et "cobblemon.battle.unboost.*",
 * car Cobblemon ne synchronise pas statChanges côté client en cours de combat.
 */
public final class BoostTracker {

    private BoostTracker() {
    }

    private static final Map<Stat, Integer> STAGES_JOUEUR = new EnumMap<>(Stat.class);
    private static final Map<Stat, Integer> STAGES_ADVERSAIRE = new EnumMap<>(Stat.class);

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

        int delta;
        if (cle.startsWith("cobblemon.battle.boost.slight")) delta = 1;
        else if (cle.startsWith("cobblemon.battle.boost.sharp")) delta = 2;
        else if (cle.startsWith("cobblemon.battle.boost.severe")) delta = 3;
        else if (cle.startsWith("cobblemon.battle.unboost.slight")) delta = -1;
        else if (cle.startsWith("cobblemon.battle.unboost.sharp")) delta = -2;
        else if (cle.startsWith("cobblemon.battle.unboost.severe")) delta = -3;
        else if (cle.equals("cobblemon.battle.clearallboost")) {
            STAGES_JOUEUR.clear();
            STAGES_ADVERSAIRE.clear();
            return;
        } else {
            return;
        }

        // Args : %1$s = nom du Pokémon (owned_pokemon si joueur), %2$s = stat
        Boolean estJoueur = null;
        Stat stat = null;

        for (Object arg : contenu.getArgs()) {
            if (!(arg instanceof Text texteArg)) continue;
            if (!(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) continue;
            String sousCle = sousContenu.getKey();
            if (sousCle == null) continue;

            if ("cobblemon.battle.owned_pokemon".equals(sousCle)) {
                Object[] sousArgs = sousContenu.getArgs();
                if (sousArgs.length > 0 && sousArgs[0] instanceof String nom) {
                    var joueurMc = MinecraftClient.getInstance().player;
                    estJoueur = joueurMc != null && nom.equalsIgnoreCase(joueurMc.getGameProfile().getName());
                }
            } else if (sousCle.startsWith("cobblemon.stat.")) {
                String statId = sousCle.substring("cobblemon.stat.".length()).replace(".name", "");
                stat = switch (statId) {
                    case "attack" -> Stat.ATTAQUE;
                    case "defence" -> Stat.DEFENSE;
                    case "special_attack" -> Stat.ATTAQUE_SPE;
                    case "special_defence" -> Stat.DEFENSE_SPE;
                    case "speed" -> Stat.VITESSE;
                    default -> null;
                };
            }
        }

        if (stat == null) return;

        // Si owned_pokemon absent → Pokémon sauvage/adverse sans dresseur
        boolean joueur = Boolean.TRUE.equals(estJoueur);

        Map<Stat, Integer> cible = joueur ? STAGES_JOUEUR : STAGES_ADVERSAIRE;
        int actuel = cible.getOrDefault(stat, 0);
        int nouveau = Math.max(-6, Math.min(6, actuel + delta));
        cible.put(stat, nouveau);
    }

    public static int getStageJoueur(Stat stat) {
        return STAGES_JOUEUR.getOrDefault(stat, 0);
    }

    public static int getStageAdversaire(Stat stat) {
        return STAGES_ADVERSAIRE.getOrDefault(stat, 0);
    }

    /** À appeler quand le Pokémon du camp correspondant change (switch). */
    public static void reinitialiserJoueur() {
        STAGES_JOUEUR.clear();
    }

    public static void reinitialiserAdversaire() {
        STAGES_ADVERSAIRE.clear();
    }

    public static void reinitialiser() {
        STAGES_JOUEUR.clear();
        STAGES_ADVERSAIRE.clear();
    }
}
