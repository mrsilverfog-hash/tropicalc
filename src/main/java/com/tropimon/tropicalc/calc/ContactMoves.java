package com.tropimon.tropicalc.calc;

import java.util.Set;

/**
 * Capacités faisant contact (déclenchent Casque Brut, Épine de Fer, Peau Dure...).
 * Cobblemon n'expose pas les flags Showdown côté client : liste curatée
 * couvrant l'essentiel du jeu compétitif. Une absence = pas d'avertissement,
 * jamais de faux avertissement.
 */
public final class ContactMoves {

    private ContactMoves() {
    }

    private static final Set<String> CONTACT = Set.of(
        // Normal
        "tackle", "quickattack", "bodyslam", "doubleedge", "extremespeed", "return",
        "frustration", "facade", "megakick", "megapunch", "slash", "endeavor",
        "falseswipe", "rapidspin", "flail", "thrash", "gigaimpact", "headbutt",
        "doublehit", "furyswipes", "strength", "cut",
        // Combat
        "closecombat", "drainpunch", "machpunch", "bulletpunch", "firepunch",
        "icepunch", "thunderpunch", "dynamicpunch", "focuspunch", "superpower",
        "hammerarm", "crosschop", "brickbreak", "lowkick", "lowsweep",
        "highjumpkick", "jumpkick", "blazekick", "tripleaxel", "triplekick",
        "doublekick", "seismictoss", "bodypress", "forcepalm", "revenge",
        "reversal", "armthrust", "vitalthrow", "stormthrow", "circlethrow",
        "submission", "wakeupslap", "poweruppunch", "jetpunch", "collisioncourse",
        "sacredsword",
        // Feu
        "flareblitz", "firelash", "flamecharge", "ragingbull", "bitterblade",
        "temperflare",
        // Eau
        "waterfall", "liquidation", "aquajet", "wavecrash", "crabhammer",
        "flipturn", "surgingstrikes", "aquastep", "razorshell", "dive",
        // Plante
        "powerwhip", "woodhammer", "hornleech", "solarblade", "leafblade",
        "tropkick", "trailblaze", "branchpoke", "grassknot", 
        // Électrik
        "wildcharge", "voltackle", "zingzap", "supercellslam", "plasmafists",
        "thunderouskick",
        // Glace
        "icespinner", "tripledive", "avalanche", "icefang", "iceball",
        // Poison
        "poisonjab", "crosspoison", "poisontail", "direclaw",
        // Sol
        "stompingtantrum", "highhorsepower", "headlongrush", 
        "boneclub", "drillrun",
        // Vol
        "bravebird", "dualwingbeat", "drillpeck", "aerialace", "acrobatics",
        "wingattack", "skyattack", "beakblast", "floatyfall",
        // Psy
        "zenheadbutt", "psychicfangs", 
        // Insecte
        "xscissor", "leechlife", "lunge", "firstimpression", "megahorn",
        "bugbite", "pounce", "skittersmack", "uturn", "fellstinger",
        // Roche
        "accelerock", "headsmash", "rockclimb", "stoneaxe", "rollout",
        // Spectre
        "shadowclaw", "shadowsneak", "phantomforce", "spiritshackle",
        "shadowpunch", "ragefist", "shadowforce",
        // Dragon
        "dragonclaw", "outrage", "dragonrush", "dragontail", "dualchop",
        "glaiverush", "dragonhammer",
        // Ténèbres
        "knockoff", "suckerpunch", "pursuit", "crunch", "bite", "foulplay",
        "assurance", "payback", "throatchop", "darkestlariat", "jawlock",
        "nightslash", "thief", "covet", "kowtowcleave", "ceaselessedge",
        "wickedblow", "falsesurrender", "lashout",
        // Acier
        "ironhead", "heavyslam", "meteormash", "smartstrike", "anchorshot",
        "doubleironbash", "gyroball", "heatcrash", "ironroller",
        "behemothblade", "behemothbash", "hardpress",
        // Fée
        "playrough", "spiritbreak", "drainingkiss", "zippyzap",
        // Divers fangs
        "firefang", "thunderfang"
    );

    public static boolean estContact(String showdownId) {
        return showdownId != null && CONTACT.contains(showdownId);
    }
}
