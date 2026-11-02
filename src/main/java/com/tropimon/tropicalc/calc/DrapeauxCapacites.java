package com.tropimon.tropicalc.calc;

import java.util.Map;
import java.util.Set;

/**
 * Drapeaux des capacités, GÉNÉRÉS depuis les données de @smogon/calc
 * (tools/reference-calc/generer-drapeaux.js). Ne pas modifier à la main :
 * relancer le script. Remplace les listes écrites à la main, incomplètes
 * (Poing de Feu absent de Sans Limite, Nœud Herbe compté au contact...).
 */
public final class DrapeauxCapacites {

    private DrapeauxCapacites() {
    }

    public static final Set<String> CONTACT = Set.of(
        "accelerock", "acrobatics", "aerialace", "anchorshot", "aquajet", "aquastep", "aquatail",
        "armthrust", "assurance", "astonish", "avalanche", "axekick", "behemothbash",
        "behemothblade", "bide", "bind", "bite", "bitterblade", "blazekick", "bodypress",
        "bodyslam", "boltbeak", "boltstrike", "bounce", "branchpoke", "bravebird", "breakingswipe",
        "brickbreak", "brutalswing", "bugbite", "bulletpunch", "catastropika", "ceaselessedge",
        "chipaway", "circlethrow", "clamp", "closecombat", "collisioncourse", "cometpunch",
        "comeuppance", "constrict", "counter", "covet", "crabhammer", "crosschop", "crosspoison",
        "crunch", "crushclaw", "crushgrip", "cut", "darkestlariat", "dig", "direclaw", "dive",
        "dizzypunch", "doubleedge", "doublehit", "doubleironbash", "doublekick", "doubleshock",
        "doubleslap", "dragonascent", "dragonclaw", "dragonhammer", "dragonrush", "dragontail",
        "drainingkiss", "drainpunch", "drillpeck", "drillrun", "dualchop", "dualwingbeat",
        "dynamicpunch", "electrodrift", "endeavor", "extremespeed", "facade", "fakeout",
        "falsesurrender", "falseswipe", "feintattack", "fellstinger", "firefang", "firelash",
        "firepunch", "firstimpression", "fishiousrend", "flail", "flamecharge", "flamewheel",
        "flareblitz", "flipturn", "fly", "flyingpress", "focuspunch", "forcepalm", "foulplay",
        "frustration", "furyattack", "furycutter", "furyswipes", "geargrind", "gigaimpact",
        "glaiverush", "grassknot", "grassyglide", "guillotine", "gyroball", "hammerarm",
        "hardpress", "headbutt", "headcharge", "headlongrush", "headsmash", "heartstamp",
        "heatcrash", "heavyslam", "highhorsepower", "highjumpkick", "holdback", "hornattack",
        "horndrill", "hornleech", "hyperdrill", "hyperfang", "iceball", "icefang", "icehammer",
        "icepunch", "icespinner", "infestation", "ironhead", "irontail", "jawlock", "jetpunch",
        "jumpkick", "karatechop", "knockoff", "kowtowcleave", "lashout", "lastresort", "leafblade",
        "leechlife", "letssnuggleforever", "lick", "liquidation", "lowkick", "lowsweep", "lunge",
        "machpunch", "maliciousmoonsault", "megahorn", "megakick", "megapunch", "metalclaw",
        "meteormash", "mightycleave", "mortalspin", "multiattack", "needlearm", "nightslash",
        "nuzzle", "outrage", "payback", "peck", "petaldance", "phantomforce", "plasmafists",
        "playrough", "pluck", "poisonfang", "poisonjab", "poisontail", "populationbomb", "pounce",
        "pound", "powertrip", "poweruppunch", "powerwhip", "psyblade", "psychicfangs",
        "psyshieldbash", "pulverizingpancake", "punishment", "pursuit", "quickattack", "rage",
        "ragefist", "ragingbull", "rapidspin", "razorshell", "retaliate", "return", "revenge",
        "reversal", "rockclimb", "rocksmash", "rollingkick", "rollout", "sacredsword", "scratch",
        "searingsunrazesmash", "seismictoss", "shadowclaw", "shadowforce", "shadowpunch",
        "shadowsneak", "shadowstrike", "skittersmack", "skullbash", "skydrop", "skyuppercut",
        "slam", "slash", "smartstrike", "smellingsalts", "snaptrap", "solarblade",
        "soulstealing7starstrike", "spark", "spectralthief", "spinout", "spiritbreak",
        "steamroller", "steelroller", "steelwing", "stomp", "stompingtantrum", "stoneaxe",
        "stormthrow", "strength", "struggle", "submission", "suckerpunch", "sunsteelstrike",
        "supercellslam", "superfang", "superpower", "surgingstrikes", "tackle", "tailslap",
        "takedown", "temperflare", "thief", "thrash", "throatchop", "thunderfang", "thunderouskick",
        "thunderpunch", "trailblaze", "tripleaxel", "tripledive", "triplekick", "tropkick",
        "trumpcard", "upperhand", "uturn", "vcreate", "vinewhip", "visegrip", "vitalthrow",
        "volttackle", "wakeupslap", "waterfall", "wavecrash", "wickedblow", "wildcharge",
        "wingattack", "woodhammer", "wrap", "wringout", "xscissor", "zenheadbutt", "zingzap"
    );

    public static final Set<String> POING = Set.of(
        "bulletpunch", "cometpunch", "dizzypunch", "doubleironbash", "drainpunch", "dynamicpunch",
        "firepunch", "focuspunch", "hammerarm", "headlongrush", "icehammer", "icepunch", "jetpunch",
        "machpunch", "megapunch", "meteormash", "plasmafists", "poweruppunch", "ragefist",
        "shadowpunch", "skyuppercut", "surgingstrikes", "thunderpunch", "wickedblow"
    );

    public static final Set<String> MORSURE = Set.of(
        "bite", "crunch", "firefang", "fishiousrend", "hyperfang", "icefang", "jawlock",
        "poisonfang", "psychicfangs", "thunderfang"
    );

    public static final Set<String> SON = Set.of(
        "alluringvoice", "boomburst", "bugbuzz", "chatter", "clangingscales", "clangoroussoul",
        "clangoroussoulblaze", "confide", "disarmingvoice", "echoedvoice", "eeriespell",
        "grasswhistle", "growl", "healbell", "howl", "hypervoice", "metalsound", "nobleroar",
        "overdrive", "partingshot", "perishsong", "psychicnoise", "relicsong", "roar", "round",
        "screech", "sing", "snarl", "snore", "sparklingaria", "supersonic", "torchsong", "uproar"
    );

    public static final Set<String> TRANCHANT = Set.of(
        "aerialace", "aircutter", "airslash", "aquacutter", "behemothblade", "bitterblade",
        "ceaselessedge", "crosspoison", "cut", "furycutter", "kowtowcleave", "leafblade",
        "mightycleave", "nightslash", "populationbomb", "psyblade", "psychocut", "razorleaf",
        "razorshell", "sacredsword", "secretsword", "slash", "solarblade", "stoneaxe",
        "tachyoncutter", "xscissor"
    );

    public static final Set<String> PULSATION = Set.of(
        "aurasphere", "darkpulse", "dragonpulse", "healpulse", "orderup", "originpulse",
        "terrainpulse", "waterpulse"
    );

    public static final Set<String> BALLE = Set.of(
        "acidspray", "aurasphere", "barrage", "beakblast", "bulletseed", "eggbomb", "electroball",
        "energyball", "focusblast", "gyroball", "iceball", "magnetbomb", "mistball", "mudbomb",
        "octazooka", "pollenpuff", "pyroball", "rockblast", "rockwrecker", "searingshot",
        "seedbomb", "shadowball", "sludgebomb", "syrupbomb", "weatherball", "zapcannon"
    );

    public static final Set<String> VENT = Set.of(
        "aeroblast", "aircutter", "bleakwindstorm", "blizzard", "fairywind", "gust", "heatwave",
        "hurricane", "icywind", "petalblizzard", "sandsearstorm", "sandstorm", "springtidestorm",
        "tailwind", "twister", "whirlwind", "wildboltstorm"
    );

    public static final Set<String> EFFET_SECONDAIRE = Set.of(
        "acid", "acidspray", "airslash", "alluringvoice", "anchorshot", "ancientpower", "appleacid",
        "aquastep", "astonish", "aurawheel", "aurorabeam", "axekick", "barbbarrage", "bite",
        "bittermalice", "blazekick", "blazingtorque", "bleakwindstorm", "blizzard", "blueflare",
        "bodyslam", "boltstrike", "boneclub", "bounce", "breakingswipe", "bubble", "bubblebeam",
        "bugbuzz", "bulldoze", "burningjealousy", "ceaselessedge", "chargebeam", "chatter",
        "chillingwater", "clangoroussoulblaze", "combattorque", "confusion", "constrict",
        "crosspoison", "crunch", "crushclaw", "darkpulse", "diamondstorm", "direclaw", "discharge",
        "dizzypunch", "doubleironbash", "dragonbreath", "dragonrush", "drumbeating", "dynamicpunch",
        "earthpower", "eeriespell", "electroweb", "ember", "energyball", "esperwing",
        "extrasensory", "fakeout", "fierydance", "fierywrath", "fireblast", "firefang", "firelash",
        "firepunch", "flamecharge", "flamethrower", "flamewheel", "flareblitz", "flashcannon",
        "focusblast", "forcepalm", "freezedry", "freezeshock", "freezingglare", "genesissupernova",
        "glaciate", "gravapple", "gunkshot", "headbutt", "heartstamp", "heatwave", "hurricane",
        "hyperfang", "icebeam", "iceburn", "icefang", "icepunch", "iciclecrash", "icywind",
        "infernalparade", "inferno", "ironhead", "irontail", "lavaplume", "leaftornado", "lick",
        "liquidation", "lowsweep", "luminacrash", "lunge", "lusterpurge", "magicaltorque",
        "malignantchain", "matchagotcha", "metalclaw", "meteormash", "mirrorshot", "mistball",
        "moonblast", "mortalspin", "mountaingale", "mudbomb", "muddywater", "mudshot", "mudslap",
        "mysticalfire", "mysticalpower", "needlearm", "nightdaze", "noxioustorque", "nuzzle",
        "octazooka", "ominouswind", "paleowave", "playrough", "poisonfang", "poisonjab",
        "poisonsting", "poisontail", "polarflare", "pounce", "powdersnow", "poweruppunch",
        "psybeam", "psychic", "psychicnoise", "psyshieldbash", "pyroball", "rapidspin",
        "razorshell", "relicsong", "rockclimb", "rockslide", "rocksmash", "rocktomb", "rollingkick",
        "sacredfire", "saltcure", "sandsearstorm", "scald", "scorchingsands", "searingshot",
        "secretpower", "seedflare", "shadowball", "shadowbone", "shadowstrike", "shellsidearm",
        "signalbeam", "silverwind", "skittersmack", "skyattack", "sludge", "sludgebomb",
        "sludgewave", "smog", "snarl", "snore", "spark", "sparklingaria", "spiritbreak",
        "spiritshackle", "springtidestorm", "steameruption", "steamroller", "steelwing",
        "stokedsparksurfer", "stomp", "stoneaxe", "strangesteam", "strugglebug", "syrupbomb",
        "throatchop", "thunder", "thunderbolt", "thunderfang", "thunderouskick", "thunderpunch",
        "thundershock", "torchsong", "trailblaze", "triattack", "triplearrows", "tropkick",
        "twineedle", "twister", "upperhand", "volttackle", "waterfall", "waterpulse",
        "wickedtorque", "wildboltstorm", "zapcannon", "zenheadbutt", "zingzap"
    );

    public static final Set<String> CHUTE = Set.of(
        "axekick", "highjumpkick", "jumpkick", "supercellslam"
    );

    public static final Set<String> RECUL_PV_MAX = Set.of(
        "chloroblast", "mindblown", "steelbeam"
    );

    /** Recul : fraction des dégâts infligés {numérateur, dénominateur}. */
    public static final Map<String, int[]> RECUL = Map.ofEntries(
        Map.entry("bravebird", new int[]{33, 100}),
        Map.entry("doubleedge", new int[]{33, 100}),
        Map.entry("flareblitz", new int[]{33, 100}),
        Map.entry("headcharge", new int[]{1, 4}),
        Map.entry("headsmash", new int[]{1, 2}),
        Map.entry("lightofruin", new int[]{1, 2}),
        Map.entry("submission", new int[]{1, 4}),
        Map.entry("takedown", new int[]{1, 4}),
        Map.entry("volttackle", new int[]{33, 100}),
        Map.entry("wavecrash", new int[]{33, 100}),
        Map.entry("wildcharge", new int[]{1, 4}),
        Map.entry("woodhammer", new int[]{33, 100})
    );

    /** Priorité non nulle (les capacités de statut absentes des données en sont exclues). */
    public static final Map<String, Integer> PRIORITE = Map.ofEntries(
        Map.entry("accelerock", 1),
        Map.entry("allyswitch", 2),
        Map.entry("aquajet", 1),
        Map.entry("babydolleyes", 1),
        Map.entry("banefulbunker", 4),
        Map.entry("bide", 1),
        Map.entry("bulletpunch", 1),
        Map.entry("burningbulwark", 4),
        Map.entry("craftyshield", 3),
        Map.entry("detect", 4),
        Map.entry("endure", 4),
        Map.entry("extremespeed", 2),
        Map.entry("fakeout", 3),
        Map.entry("feint", 2),
        Map.entry("firstimpression", 2),
        Map.entry("followme", 2),
        Map.entry("helpinghand", 5),
        Map.entry("iceshard", 1),
        Map.entry("iondeluge", 1),
        Map.entry("jetpunch", 1),
        Map.entry("kingsshield", 4),
        Map.entry("machpunch", 1),
        Map.entry("magiccoat", 4),
        Map.entry("maxguard", 4),
        Map.entry("obstruct", 4),
        Map.entry("powder", 1),
        Map.entry("protect", 4),
        Map.entry("quickattack", 1),
        Map.entry("quickguard", 3),
        Map.entry("ragepowder", 2),
        Map.entry("shadowsneak", 1),
        Map.entry("silktrap", 4),
        Map.entry("snatch", 4),
        Map.entry("spikyshield", 4),
        Map.entry("spotlight", 3),
        Map.entry("suckerpunch", 1),
        Map.entry("thunderclap", 1),
        Map.entry("upperhand", 3),
        Map.entry("vacuumwave", 1),
        Map.entry("watershuriken", 1),
        Map.entry("wideguard", 3)
    );
}
