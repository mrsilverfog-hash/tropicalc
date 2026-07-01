package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

/**
 * Convertit les identifiants Showdown (anglais, minuscules, sans espaces)
 * utilisés en interne par Cobblemon vers les clés françaises attendues par
 * TropiCalc (PokemonType, Nature, et les noms d'objets/talents utilisés
 * dans ItemModifier / AbilityModifier).
 *
 * Volontairement limité aux talents et objets déjà implémentés dans le
 * moteur de calcul : à enrichir au fur et à mesure qu'on ajoute des entrées
 * à ItemModifier.REGISTRE / AbilityModifier.REGISTRE.
 */
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

        // Talents : identifiant Showdown -> clé française utilisée dans AbilityModifier
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

        // Objets : identifiant Showdown -> clé française utilisée dans ItemModifier
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
        OBJETS.put("powerherb", "Herbe Puissance");
        OBJETS.put("shellbell", "Grelot Coquille");
        OBJETS.put("lum", "Baie Lum");
        OBJETS.put("lumberry", "Baie Lum");
        OBJETS.put("salacberry", "Baie Salac");
        OBJETS.put("petayaberry", "Baie Petaya");
        OBJETS.put("lansat", "Baie Lansat");
        OBJETS.put("boosterenergy", "Énergie Turbo");
        OBJETS.put("clearamulet", "Amulette Claire");
        OBJETS.put("covertcloak", "Cape Secrète");
        OBJETS.put("mirrorherb", "Herbe Miroir");
        OBJETS.put("punchingglove", "Gant Boxe");
    }

    /** Nettoie un identifiant Showdown (espaces, tirets, apostrophes retirés, en minuscules). */
    private static String normaliser(String id) {
        if (id == null) {
            return null;
        }
        return id.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static PokemonType type(String showdownId) {
        return TYPES.get(normaliser(showdownId));
    }

    public static Nature nature(String showdownId) {
        Nature n = NATURES.get(normaliser(showdownId));
        return n != null ? n : Nature.HARDI;
    }

    /** Renvoie le nom français du talent, ou null si pas (encore) géré par AbilityModifier. */
    public static String talent(String showdownId) {
        return TALENTS.get(normaliser(showdownId));
    }

    /** Renvoie le nom français de l'objet, ou null si pas (encore) géré par ItemModifier. */
    public static String objet(String showdownId) {
        return OBJETS.get(normaliser(showdownId));
    }
}
