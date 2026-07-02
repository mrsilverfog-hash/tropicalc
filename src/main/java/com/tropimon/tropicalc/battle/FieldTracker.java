package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.Field;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public final class FieldTracker {

    private FieldTracker() {
    }

    private static Field.Meteo meteoActive = Field.Meteo.AUCUNE;
    private static Field.TypeTerrain terrainActif = Field.TypeTerrain.AUCUN;

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

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
        return f;
    }

    public static void reinitialiser() {
        meteoActive = Field.Meteo.AUCUNE;
        terrainActif = Field.TypeTerrain.AUCUN;
    }
}
