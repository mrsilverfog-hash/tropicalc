package com.tropimon.tropicalc.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mémoire de scouting entre combats : faits observés (objets, talents,
 * capacités révélées) par joueur adverse et par espèce, persistés sur disque.
 * En ranked on recroise les mêmes joueurs — leurs sets changent rarement.
 */
public final class ScoutingStore {

    public static final class Faits {
        public String objet;          // objet confirmé lors d'un combat passé
        public String talent;         // talent confirmé (ex: Soin Poison)
        public boolean chipTalent;    // Épine de Fer / Peau Dure observé
        public Set<String> capacites = new LinkedHashSet<>();
        // Stats apprises en combat (facteurs par rapport à l'estimation
        // Smogon, clé = nom de Stat) et vitesses observées. Effacées si une
        // capacité hors du set connu apparaît (nouveau set).
        public Map<String, Double> defPlafonds, defPlanchers, offPlafonds, offPlanchers;
        public Integer vitMin, vitMax;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Map<String, Faits>>>() {}.getType();

    // joueur adverse -> (espèce -> faits)
    private static Map<String, Map<String, Faits>> donnees = null;

    /**
     * Migration silencieuse des anciens noms français vers les noms corrects,
     * appliquée aux données déjà persistées sur disque avant ces corrections
     * (session de test où plusieurs noms ont été fixés en cours de route).
     * Sans ça, un fait scouté avec l'ancien nom devient inerte (affiché mais
     * sans effet, plus aucune clé du registre ne le reconnaît).
     */
    private static final Map<String, String> ANCIENS_NOMS = Map.ofEntries(
        Map.entry("Gant Boxe", "Gant de Boxe"),
        Map.entry("Épée de Ruine", "Épée du Fléau"),
        Map.entry("Vase de Ruine", "Urne du Fléau"),
        Map.entry("Tablettes de Ruine", "Tablettes du Fléau"),
        Map.entry("Perles de Ruine", "Perles du Fléau"),
        Map.entry("Adrénaline", "Agitation"),
        Map.entry("Lunatique", "Farceur"),
        Map.entry("Casque Clou", "Casque Brut"),
        Map.entry("Grosse Bottes", "Grosses Bottes"),
        Map.entry("Énergie Turbo", "Énergie Booster"),
        Map.entry("Écharpe Choix", "Mouchoir Choix"),
        Map.entry("Robuste", "Fermeté"),
        Map.entry("Seigneur Suprême", "Général Suprême"),
        Map.entry("Bandeau Muscles", "Bandeau Muscle"),
        Map.entry("Lunettes Savantes", "Lunettes Sages"),
        Map.entry("Mâchoire Brute", "Prognathe"),
        Map.entry("Analytique", "Analyste"),
        Map.entry("Verres Teintés", "Lentiteintée"),
        Map.entry("Multi-écailles", "Multiécaille")
    );

    private static String migrer(String nom) {
        return nom == null ? null : ANCIENS_NOMS.getOrDefault(nom, nom);
    }

    /**
     * Capacités Z - interdites en ranked, donc jamais rejouables par la suite.
     * Si l'une d'elles s'est retrouvée enregistrée comme capacité connue d'une
     * espèce (lors d'un tournoi où les capacités Z étaient autorisées), elle
     * doit être retirée : elle ne peut plus jamais réapparaître en ranked et
     * ne fait qu'encombrer le set scouté avec une fausse capacité.
     *
     * Deux groupes, tous deux confirmés par Bulbapedia/Poképédia : les 18
     * capacités Z offensives génériques (une par type), et les capacités Z
     * signature propres à un seul Pokémon (ex. Patati-Patattrape/Let's Snuggle
     * Forever, exclusive à Mimiqui et déclenchée via Câlinerie).
     *
     * Limite assumée : ne couvre pas les capacités Z "de statut" (version Z
     * d'une capacité de statut, qui d'après Bulbapedia garde en principe son
     * propre nom avec un effet additionnel plutôt qu'un nom distinct) - non
     * vérifié faute de log montrant ce cas précis utilisé.
     */
    private static final Set<String> CAPACITES_Z = Set.of(
        // Génériques par type (18)
        "breakneckblitz", "infernooverdrive", "hydrovortex", "bloomdoom",
        "gigavolthavoc", "subzeroslammer", "alloutpummeling", "aciddownpour",
        "tectonicrage", "supersonicskystrike", "shatteredpsyche", "savagespinout",
        "continentalcrush", "neverendingnightmare", "devastatingdrake",
        "blackholeeclipse", "corkscrewcrash", "twinkletackle",
        // Signature propres à un seul Pokémon
        "catastropika", "10000000voltthunderbolt", "stokedsparksurfer",
        "extremeevoboost", "pulverizingpancake", "genesissupernova",
        "oceanicoperetta", "letssnuggleforever", "searingsunrazesmash",
        "menacingmoonrazemaelstrom", "lightthatburnsthesky", "soulstealing7starstrike",
        "sinisterarrowraid", "maliciousmoonsault", "splinteredstormshards",
        "clangoroussoulblaze", "guardianofalola"
    );

    private ScoutingStore() {
    }

    public static boolean estCapaciteZ(String id) {
        return id != null && CAPACITES_Z.contains(id);
    }

    private static Path fichier() {
        return FabricLoader.getInstance().getConfigDir().resolve("tropicalc-scouting.json");
    }

    private static synchronized Map<String, Map<String, Faits>> charger() {
        if (donnees == null) {
            donnees = new HashMap<>();
            try {
                Path f = fichier();
                if (Files.exists(f)) {
                    Map<String, Map<String, Faits>> lu = GSON.fromJson(Files.readString(f), TYPE);
                    if (lu != null) donnees = lu;
                }
            } catch (Exception e) {
                // Fichier corrompu ou illisible : on repart de zéro sans crasher
            }

            // Migration des anciens noms sur les données fraîchement chargées
            boolean migrationAppliquee = false;
            for (Map<String, Faits> parEspece : donnees.values()) {
                for (Faits f : parEspece.values()) {
                    String talentMigre = migrer(f.talent);
                    String objetMigre = migrer(f.objet);
                    if (!java.util.Objects.equals(talentMigre, f.talent)) {
                        f.talent = talentMigre;
                        migrationAppliquee = true;
                    }
                    if (!java.util.Objects.equals(objetMigre, f.objet)) {
                        f.objet = objetMigre;
                        migrationAppliquee = true;
                    }
                    if (f.capacites.removeAll(CAPACITES_Z)) {
                        migrationAppliquee = true;
                    }
                }
            }
            // Purge unique des Champ'Duit enregistrés : tous venaient d'une
            // déduction fausse (compte de tours décalé d'un tour, champ
            // attribué à l'adversaire même posé par le joueur). Un faux objet
            // confirmé bloquait ensuite la détection du vrai (Bandeau Choix).
            // Fichier témoin pour ne la faire qu'une fois et garder les futurs
            // Champ'Duit, désormais prouvés.
            try {
                Path temoin = FabricLoader.getInstance().getConfigDir()
                    .resolve("tropicalc-scouting-purge-champduit.done");
                if (!Files.exists(temoin)) {
                    for (Map<String, Faits> parEspece : donnees.values()) {
                        for (Faits f : parEspece.values()) {
                            if ("Champ'Duit".equals(f.objet)) {
                                f.objet = null;
                                migrationAppliquee = true;
                            }
                        }
                    }
                    Files.writeString(temoin, "ok");
                }
            } catch (Exception ignored) {
            }
            // Purge unique de TOUS les objets mémorisés (10/10/2026) : une partie
            // venait de fausses confirmations (élimination par les dégâts qui
            // ignorait "aucun objet", Évoluroc déduit de Smogon) - ex. Grosses
            // Bottes sur un Pyrobut, Veste de Combat sur une Hatterene - sans
            // moyen de les distinguer des vraies. Talents et capacités gardés.
            // Depuis, seuls des objets observés sont enregistrés.
            try {
                Path temoin = FabricLoader.getInstance().getConfigDir()
                    .resolve("tropicalc-scouting-purge-objets.done");
                if (!Files.exists(temoin)) {
                    for (Map<String, Faits> parEspece : donnees.values()) {
                        for (Faits f : parEspece.values()) {
                            if (f.objet != null) {
                                f.objet = null;
                                migrationAppliquee = true;
                            }
                        }
                    }
                    Files.writeString(temoin, "ok");
                }
            } catch (Exception ignored) {
            }
            if (migrationAppliquee) {
                sauvegarder();
            }
        }
        return donnees;
    }

    private static synchronized void sauvegarder() {
        try {
            Files.writeString(fichier(), GSON.toJson(donnees, TYPE));
        } catch (IOException e) {
            // Échec d'écriture : le scouting de la session est perdu, pas grave
        }
    }

    public static synchronized Faits get(String joueur, String espece) {
        if (joueur == null || espece == null) return null;
        Map<String, Faits> parEspece = charger().get(joueur.toLowerCase());
        return parEspece == null ? null : parEspece.get(espece);
    }

    /** Oublie tout ce qu'on sait de ce Pokémon chez ce joueur (nouveau set). */
    public static synchronized void oublier(String joueur, String espece) {
        if (joueur == null || espece == null) return;
        Map<String, Faits> parEspece = charger().get(joueur.toLowerCase());
        if (parEspece != null && parEspece.remove(espece) != null) sauvegarder();
    }

    /**
     * Stats apprises pendant un combat : les nouvelles valeurs remplacent les
     * anciennes stat par stat (le combat le plus récent a raison) ; vitesses :
     * plancher le plus haut et plafond le plus bas, sauf contradiction (on
     * garde alors celles de ce combat).
     */
    public static synchronized void enregistrerStats(String joueur, String espece,
                                                     Map<String, Double> defPlafonds, Map<String, Double> defPlanchers,
                                                     Map<String, Double> offPlafonds, Map<String, Double> offPlanchers,
                                                     Integer vitMin, Integer vitMax) {
        if (joueur == null || espece == null) return;
        Faits f = charger()
            .computeIfAbsent(joueur.toLowerCase(), k -> new HashMap<>())
            .computeIfAbsent(espece, k -> new Faits());
        f.defPlafonds = fusionner(f.defPlafonds, defPlafonds);
        f.defPlanchers = fusionner(f.defPlanchers, defPlanchers);
        f.offPlafonds = fusionner(f.offPlafonds, offPlafonds);
        f.offPlanchers = fusionner(f.offPlanchers, offPlanchers);
        Integer min = vitMin == null ? f.vitMin : f.vitMin == null ? vitMin : Integer.valueOf(Math.max(f.vitMin, vitMin));
        Integer max = vitMax == null ? f.vitMax : f.vitMax == null ? vitMax : Integer.valueOf(Math.min(f.vitMax, vitMax));
        if (min != null && max != null && min > max) {
            min = vitMin;
            max = vitMax;
        }
        f.vitMin = min;
        f.vitMax = max;
        sauvegarder();
    }

    private static Map<String, Double> fusionner(Map<String, Double> ancien, Map<String, Double> nouveau) {
        if (nouveau == null || nouveau.isEmpty()) return ancien;
        Map<String, Double> r = ancien == null ? new HashMap<>() : new HashMap<>(ancien);
        r.putAll(nouveau);
        return r;
    }

    /** Fusionne les faits observés pendant un combat (null = pas de nouvelle info). */
    public static synchronized void enregistrer(String joueur, String espece,
                                                String objet, String talent,
                                                boolean chipTalent, Set<String> capacites) {
        if (joueur == null || espece == null) return;
        Faits f = charger()
            .computeIfAbsent(joueur.toLowerCase(), k -> new HashMap<>())
            .computeIfAbsent(espece, k -> new Faits());
        if (objet != null) f.objet = objet;
        if (talent != null && !("Imposteur".equals(talent) && !"ditto".equals(espece))) f.talent = talent;
        if (chipTalent) f.chipTalent = true;
        if (capacites != null) {
            for (String c : capacites) {
                if (!CAPACITES_Z.contains(c)) f.capacites.add(c);
            }
        }
        sauvegarder();
    }
}
