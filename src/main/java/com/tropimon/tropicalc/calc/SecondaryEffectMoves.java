package com.tropimon.tropicalc.calc;

import java.util.Set;

/**
 * Capacités offensives avec un effet secondaire à pourcentage (statut, stat,
 * flinch...) — celles concernées par Sans Limite (Sheer Force : +30% puissance,
 * effet secondaire supprimé). Liste curatée de l'essentiel du compétitif ;
 * une absence = pas de bonus affiché, jamais un bonus inventé.
 */
public final class SecondaryEffectMoves {

    private SecondaryEffectMoves() {
    }

    private static final Set<String> EFFET_SECONDAIRE = Set.of(
        // Brûlure / statut
        "flamethrower", "fireblast", "lavaplume", "scald", "sludgebomb",
        "sludgewave", "thunderbolt", "thunder", "discharge", "icebeam",
        "blizzard", "airslash", "crunch", "ironhead", "rockslide",
        "dragonbreath", "twister", "boltstrike", "fusionbolt",
        "fusionflare", "steameruption", "muddywater", "moonblast",
        "dazzlinggleam", "acidspray", "flareblitz", "wildcharge",
        "headbutt", "stomp", "extrasensory", "shadowball", "bugbuzz",
        // Poison
        "poisonjab", "gunkshot", "poisonfang", "sludge",
        // Paralysie
        "bodyslam", "lick", 
        // Flinch
         "zenheadbutt", "darkpulse", "waterfall",
         "iciclecrash", 
        // Baisse de stat adverse
        "nightdaze", "bulldoze", "mysticalfire", "psychic", 
        "energyball", "focusblast", "leafstorm", "mistball", "lusterpurge"
    );

    public static boolean aEffetSecondaire(String showdownId) {
        return showdownId != null && EFFET_SECONDAIRE.contains(showdownId);
    }
}
