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

    // Capacités à effet secondaire (statut, recul de stat adverse, peur, boost du lanceur
    // en effet secondaire) : celles que Sans Limite booste. Liste refaite le 02/11
    // (Poing de Feu de Darumacho oublié : x1,3 manquant).
    private static final Set<String> EFFET_SECONDAIRE = Set.of(
        "firepunch", "flamethrower", "fireblast", "lavaplume", "flamewheel", "ember", "flareblitz",
        "firefang", "heatwave", "blazekick", "sacredfire", "searingshot", "steameruption",
        "mysticalfire", "fierydance", "flamecharge", "blueflare", "pyroball", "burningjealousy",
        "torchsong", "infernalparade", "scald", "waterfall", "muddywater", "bubblebeam", "bubble",
        "waterpulse", "octazooka", "aquastep", "chillingwater", "liquidation", "razorshell",
        "sparklingaria", "thunderbolt", "thunder", "discharge", "thunderpunch", "thunderfang",
        "zapcannon", "nuzzle", "boltstrike", "volttackle", "electroweb", "spark", "thunderouskick",
        "zingzap", "icebeam", "blizzard", "icepunch", "icefang", "freezedry", "iciclecrash",
        "powdersnow", "icywind", "glaciate", "freezingglare", "mountaingale", "sludgebomb",
        "sludgewave", "poisonjab", "gunkshot", "sludge", "poisonfang", "crosspoison", "acidspray",
        "poisontail", "smog", "barbbarrage", "direclaw", "earthpower", "mudslap", "mudbomb",
        "bulldoze", "scorchingsands", "mudshot", "sandsearstorm", "airslash", "hurricane",
        "skyattack", "bleakwindstorm", "psychic", "psybeam", "extrasensory", "zenheadbutt",
        "lusterpurge", "mistball", "psychicnoise", "esperwing", "mysticalpower", "bugbuzz",
        "signalbeam", "strugglebug", "lunge", "pounce", "silverwind", "skittersmack", "rockslide",
        "ancientpower", "rocktomb", "shadowball", "ominouswind", "astonish", "spiritshackle",
        "bittermalice", "shadowbone", "dragonbreath", "twister", "dragonrush", "breakingswipe",
        "crunch", "darkpulse", "bite", "nightdaze", "snarl", "throatchop", "fierywrath", "ironhead",
        "flashcannon", "irontail", "meteormash", "steelwing", "mirrorshot", "anchorshot",
        "moonblast", "playrough", "spiritbreak", "strangesteam", "springtidestorm", "focusblast",
        "dynamicpunch", "lowsweep", "rocksmash", "triplearrows", "axekick", "forcepalm",
        "upperhand", "poweruppunch", "bodyslam", "headbutt", "stomp", "fakeout", "hyperfang",
        "triattack", "steamroller", "lick", "energyball", "seedflare", "needlearm", "leaftornado",
        "appleacid", "gravapple", "trailblaze", "syrupbomb"
    );

    public static boolean aEffetSecondaire(String showdownId) {
        return showdownId != null && DrapeauxCapacites.EFFET_SECONDAIRE.contains(showdownId);
    }
}
