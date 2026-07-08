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
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Map<String, Faits>>>() {}.getType();

    // joueur adverse -> (espèce -> faits)
    private static Map<String, Map<String, Faits>> donnees = null;

    private ScoutingStore() {
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

    /** Fusionne les faits observés pendant un combat (null = pas de nouvelle info). */
    public static synchronized void enregistrer(String joueur, String espece,
                                                String objet, String talent,
                                                boolean chipTalent, Set<String> capacites) {
        if (joueur == null || espece == null) return;
        Faits f = charger()
            .computeIfAbsent(joueur.toLowerCase(), k -> new HashMap<>())
            .computeIfAbsent(espece, k -> new Faits());
        if (objet != null) f.objet = objet;
        if (talent != null) f.talent = talent;
        if (chipTalent) f.chipTalent = true;
        if (capacites != null) f.capacites.addAll(capacites);
        sauvegarder();
    }
}
