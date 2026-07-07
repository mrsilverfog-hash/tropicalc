package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

public final class ShowdownIdMapper {

    private ShowdownIdMapper() {
    }

    private static final Map<String, PokemonType> TYPES = new HashMap<>();
    private static final Map<String, Nature> NATURES = new HashMap<>();
    private static final Map<String, String> TALENTS = new HashMap<>();
    private static final Map<String, String> OBJETS = new HashMap<>();

    static {
        TYPES.put("normal", PokemonType.NORMAL);
        TYPES.put("fire", PokemonType.FEU);
        TYPES.put("water", PokemonType.EAU);
        TYPES.put("electric", PokemonType.ELECTRIK);
        TYPES.put("grass", PokemonType.PLANTE);
        TYPES.put("ice", PokemonType.GLACE);
        TYPES.put("fighting", PokemonType.COMBAT);
        TYPES.put("poison", PokemonType.POISON);
        TYPES.put("ground", PokemonType.SOL);
        TYPES.put("flying", PokemonType.VOL);
        TYPES.put("psychic", PokemonType.PSY);
        TYPES.put("bug", PokemonType.INSECTE);
        TYPES.put("rock", PokemonType.ROCHE);
        TYPES.put("ghost", PokemonType.SPECTRE);
        TYPES.put("dragon", PokemonType.DRAGON);
        TYPES.put("dark", PokemonType.TENEBRES);
        TYPES.put("steel", PokemonType.ACIER);
        TYPES.put("fairy", PokemonType.FEE);
        TYPES.put("stellar", PokemonType.STELLAIRE);

        NATURES.put("hardy", Nature.HARDI);
        NATURES.put("lonely", Nature.SOLO);
        NATURES.put("brave", Nature.BRAVE);
        NATURES.put("adamant", Nature.RIGIDE);
        NATURES.put("naughty", Nature.MAUVAIS);
        NATURES.put("bold", Nature.ASSURE);
        NATURES.put("docile", Nature.DOCILE);
        NATURES.put("relaxed", Nature.RELAX);
        NATURES.put("impish", Nature.MALIN);
        NATURES.put("lax", Nature.LACHE);
        NATURES.put("timid", Nature.TIMIDE);
        NATURES.put("hasty", Nature.PRESSE);
        NATURES.put("serious", Nature.SERIEUX);
        NATURES.put("jolly", Nature.JOVIAL);
        NATURES.put("naive", Nature.NAIF);
        NATURES.put("modest", Nature.MODESTE);
        NATURES.put("mild", Nature.DOUX);
        NATURES.put("bashful", Nature.PUDIQUE);
        NATURES.put("rash", Nature.FOUFOU);
        NATURES.put("quiet", Nature.DISCRET);
        NATURES.put("calm", Nature.CALME);
        NATURES.put("gentle", Nature.GENTIL);
        NATURES.put("sassy", Nature.MALPOLI);
        NATURES.put("careful", Nature.PRUDENT);
        NATURES.put("quirky", Nature.BIZARRE);

        TALENTS.put("levitate", "Lévitation");
        TALENTS.put("waterabsorb", "Absorb'Eau");
        TALENTS.put("voltabsorb", "Absorb'Volt");
        TALENTS.put("stormdrain", "Lavabo");
        TALENTS.put("flashfire", "Torche");
        TALENTS.put("thickfat", "Isograisse");
        TALENTS.put("filter", "Filtre");
        TALENTS.put("solidrock", "Solide Roc");
        TALENTS.put("multiscale", "Multi-écailles");
        TALENTS.put("shadowshield", "Spectro-Bouclier");
        TALENTS.put("unaware", "Lucidité");
        TALENTS.put("adaptability", "Adaptabilité");
        TALENTS.put("guts", "Cran");
        TALENTS.put("hustle", "Adrénaline");
        TALENTS.put("technician", "Technicien");
        TALENTS.put("ironfist", "Poing de Fer");
        TALENTS.put("strongjaw", "Mâchoire Brute");
        TALENTS.put("sandforce", "Force Sable");
        TALENTS.put("tintedlens", "Verres Teintés");
        TALENTS.put("download", "Télécharge");
        TALENTS.put("regenerator", "Régé-Force");
        TALENTS.put("intimidate", "Intimidation");
        TALENTS.put("speedboost", "Turbo");
        TALENTS.put("drizzle", "Pluie");
        TALENTS.put("drought", "Sécheresse");
        TALENTS.put("sandstream", "Sable Crachin");
        TALENTS.put("snowwarning", "Alerte Neige");
        TALENTS.put("protosynthesis", "Proto-Synthèse");
        TALENTS.put("quarkdrive", "Moteur Quark");
        TALENTS.put("magicguard", "Garde Magik");
        TALENTS.put("naturalcure", "Médic Nature");
        TALENTS.put("serenegrace", "Grâce Sereine");
        TALENTS.put("trace", "Calque");
        TALENTS.put("analytic", "Analytique");
        TALENTS.put("imposter", "Imposteur");
        TALENTS.put("prankster", "Lunatique");
        TALENTS.put("unburden", "Allège");
        TALENTS.put("rockhead", "Tête de Roc");
        TALENTS.put("sheerforce", "Grand Chelem");
        TALENTS.put("roughskin", "Peau Dure");
        TALENTS.put("ironbarbs", "Épine de Fer");
        TALENTS.put("flamebody", "Corps Ardent");
        TALENTS.put("static", "Statik");
        TALENTS.put("poisonpoint", "Point Poison");
        TALENTS.put("effectspore", "Pose Spore");
        TALENTS.put("pressure", "Pression");
        TALENTS.put("moldbreaker", "Brise Moule");
        TALENTS.put("turboblaze", "Turboblaze");
        TALENTS.put("teravolt", "Téravolt");
        TALENTS.put("supremeoverlord", "Seigneur Suprême");
        TALENTS.put("vesselofruin", "Vase de Ruine");
        TALENTS.put("swordofruin", "Épée de Ruine");
        TALENTS.put("beadsofruin", "Perles de Ruine");
        TALENTS.put("tabletsofruin", "Tablettes de Ruine");
        TALENTS.put("protean", "Protéen");
        TALENTS.put("libero", "Libéro");
        TALENTS.put("disguise", "Fantômasque");

        OBJETS.put("choiceband", "Bandeau Choix");
        OBJETS.put("choicespecs", "Lunettes Choix");
        OBJETS.put("choicescarf", "Écharpe Choix");
        OBJETS.put("lifeorb", "Orbe Vie");
        OBJETS.put("expertbelt", "Ceinture Pro");
        OBJETS.put("assaultvest", "Veste de Combat");
        OBJETS.put("eviolite", "Évoluroc");
        OBJETS.put("heavydutyboots", "Grosse Bottes");
        OBJETS.put("leftovers", "Restes");
        OBJETS.put("rockyhelmet", "Casque Clou");
        OBJETS.put("blacksludge", "Boue Noire");
        OBJETS.put("flameorb", "Orbe Flamme");
        OBJETS.put("toxicorb", "Orbe Toxik");
        OBJETS.put("focussash", "Ceinture Focus");
        OBJETS.put("sitrusberry", "Baie Sitrus");
        OBJETS.put("aguavberry", "Baie Agava");
        OBJETS.put("iapapaberry", "Baie Iapapa");
        OBJETS.put("wikiberry", "Baie Wiki");
        OBJETS.put("magoberry", "Baie Mago");
        OBJETS.put("airballoon", "Ballon");
        OBJETS.put("muscleband", "Bandeau Muscles");
        OBJETS.put("wiseglasses", "Lunettes Savantes");
        OBJETS.put("lightclay", "Argile Pouvoir");
        OBJETS.put("heatrock", "Roche Chaude");
        OBJETS.put("damprock", "Roche Humide");
        OBJETS.put("smoothrock", "Roche Lisse");
        OBJETS.put("icyrock", "Roche Glacée");
        OBJETS.put("shellbell", "Grelot Coquille");
        OBJETS.put("lumberry", "Baie Lum");
        OBJETS.put("salacberry", "Baie Salac");
        OBJETS.put("petayaberry", "Baie Petaya");
        OBJETS.put("boosterenergy", "Énergie Turbo");
        OBJETS.put("clearamulet", "Amulette Claire");
        OBJETS.put("covertcloak", "Cape Secrète");
        OBJETS.put("mirrorherb", "Herbe Miroir");
        OBJETS.put("punchingglove", "Gant Boxe");
    }

    private static String normaliser(String id) {
        if (id == null) return null;
        return id.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static PokemonType type(String showdownId) {
        return TYPES.get(normaliser(showdownId));
    }

    public static Nature nature(String showdownId) {
        Nature n = NATURES.get(normaliser(showdownId));
        return n != null ? n : Nature.HARDI;
    }

    public static String talent(String showdownId) {
        return TALENTS.get(normaliser(showdownId));
    }

    public static String objet(String showdownId) {
        return OBJETS.get(normaliser(showdownId));
    }
}
