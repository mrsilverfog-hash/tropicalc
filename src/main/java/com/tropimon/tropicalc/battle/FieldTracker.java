package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.Field;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public final class FieldTracker {

    private FieldTracker() {
    }

    private static Field.Meteo meteoActive = Field.Meteo.AUCUNE;
    private static Field.TypeTerrain terrainActif = Field.TypeTerrain.AUCUN;
    private static boolean distorsion = false;

    private static boolean reflectJoueur = false;
    private static boolean lightScreenJoueur = false;
    private static boolean auroraVeilJoueur = false;
    private static boolean reflectAdversaire = false;
    private static boolean lightScreenAdversaire = false;
    private static boolean auroraVeilAdversaire = false;

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

        // Distorsion (Trick Room)
        if (cle.equals("cobblemon.battle.fieldstart.trickroom")) {
            distorsion = true;
            return;
        }
        if (cle.equals("cobblemon.battle.fieldend.trickroom")) {
            distorsion = false;
            return;
        }

        // Météo
        if (cle.startsWith("cobblemon.battle.weather.")) {
            String reste = cle.substring("cobblemon.battle.weather.".length());
            String[] parties = reste.split("\\.");
            if (parties.length != 2) return;
            String type = parties[0];
            String action = parties[1];

            if ("end".equals(action)) {
                meteoActive = Field.Meteo.AUCUNE;
            } else if ("start".equals(action) || "upkeep".equals(action)) {
                meteoActive = switch (type) {
                    case "raindance" -> Field.Meteo.PLUIE;
                    case "sunnyday" -> Field.Meteo.SOLEIL;
                    case "sandstorm" -> Field.Meteo.SABLE;
                    case "snow", "snowscape", "hail" -> Field.Meteo.NEIGE;
                    case "primordialsea" -> Field.Meteo.PLUIE_INTENSE;
                    case "desolateland" -> Field.Meteo.SOLEIL_INTENSE;
                    default -> meteoActive;
                };
            }
            return;
        }

        // Écrans
        if (cle.startsWith("cobblemon.battle.sidestart.") || cle.startsWith("cobblemon.battle.sideend.")) {
            boolean debut = cle.startsWith("cobblemon.battle.sidestart.");
            String reste = cle.substring(debut
                ? "cobblemon.battle.sidestart.".length()
                : "cobblemon.battle.sideend.".length());
            String[] parties = reste.split("\\.");
            if (parties.length != 2) return;
            boolean allie = "ally".equals(parties[0]);
            String effet = parties[1];

            switch (effet) {
                case "reflect" -> {
                    if (allie) reflectJoueur = debut; else reflectAdversaire = debut;
                }
                case "lightscreen" -> {
                    if (allie) lightScreenJoueur = debut; else lightScreenAdversaire = debut;
                }
                case "auroraveil" -> {
                    if (allie) auroraVeilJoueur = debut; else auroraVeilAdversaire = debut;
                }
            }
            return;
        }

        // Terrains
        if (cle.contains("electricterrain")) {
            terrainActif = cle.endsWith(".end") ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.ELECTRIQUE;
        } else if (cle.contains("grassyterrain")) {
            terrainActif = cle.endsWith(".end") ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.HERBU;
        } else if (cle.contains("psychicterrain")) {
            terrainActif = cle.endsWith(".end") ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.PSYCHIQUE;
        } else if (cle.contains("mistyterrain")) {
            terrainActif = cle.endsWith(".end") ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.BRUMEUX;
        }
    }

    public static Field construireField() {
        Field f = new Field();
        f.setMeteo(meteoActive);
        f.setTerrain(terrainActif);
        f.getEcransJoueur().setProtection(reflectJoueur);
        f.getEcransJoueur().setMurLumiere(lightScreenJoueur);
        f.getEcransJoueur().setBrumeAurore(auroraVeilJoueur);
        f.getEcransAdversaire().setProtection(reflectAdversaire);
        f.getEcransAdversaire().setMurLumiere(lightScreenAdversaire);
        f.getEcransAdversaire().setBrumeAurore(auroraVeilAdversaire);
        return f;
    }

    public static boolean isDistorsion() {
        return distorsion;
    }

    public static void reinitialiser() {
        meteoActive = Field.Meteo.AUCUNE;
        terrainActif = Field.TypeTerrain.AUCUN;
        distorsion = false;
        reflectJoueur = false;
        lightScreenJoueur = false;
        auroraVeilJoueur = false;
        reflectAdversaire = false;
        lightScreenAdversaire = false;
        auroraVeilAdversaire = false;
    }
}
