package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.Field;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Suit la météo et les terrains actifs en interceptant les messages
 * "cobblemon.battle.weather.*" et "cobblemon.battle.terrain.*".
 */
public final class FieldTracker {

    private FieldTracker() {
    }

    private static Field.Meteo meteoActive = Field.Meteo.AUCUNE;
    private static Field.Terrain terrainActif = Field.Terrain.AUCUN;

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

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

        // Terrains
        if (cle.startsWith("cobblemon.battle.terrain.") || cle.contains("terrain")) {
            if (cle.contains("electricterrain") || cle.contains("electric_terrain")) {
                terrainActif = cle.endsWith(".end") ? Field.Terrain.AUCUN : Field.Terrain.ELECTRIQUE;
            } else if (cle.contains("grassyterrain") || cle.contains("grassy_terrain")) {
                terrainActif = cle.endsWith(".end") ? Field.Terrain.AUCUN : Field.Terrain.HERBU;
            } else if (cle.contains("psychicterrain") || cle.contains("psychic_terrain")) {
                terrainActif = cle.endsWith(".end") ? Field.Terrain.AUCUN : Field.Terrain.PSYCHIQUE;
            } else if (cle.contains("mistyterrain") || cle.contains("misty_terrain")) {
                terrainActif = cle.endsWith(".end") ? Field.Terrain.AUCUN : Field.Terrain.BRUMEUX;
            }
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
        terrainActif = Field.Terrain.AUCUN;
    }
}
