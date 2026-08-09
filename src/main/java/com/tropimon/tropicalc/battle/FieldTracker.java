package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.Field;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public final class FieldTracker {

    private FieldTracker() {
    }

    /**
     * Vrai si le Pokémon actif d'un des deux camps porte la Roche qui
     * prolonge cette météo à 8 tours (approximation : vérifie les deux
     * actifs sans savoir précisément lequel a lancé la capacité météo).
     */
    private static boolean rocheAllongeMeteo(Field.Meteo meteo) {
        String rocheAttendue = switch (meteo) {
            case SOLEIL -> "Roche Chaude";
            case PLUIE -> "Roche Humide";
            case SABLE -> "Roche Lisse";
            case NEIGE -> "Roche Glacée";
            default -> null;
        };
        if (rocheAttendue == null) return false;
        try {
            com.tropimon.tropicalc.calc.Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
            com.tropimon.tropicalc.calc.Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            return (joueur != null && rocheAttendue.equals(joueur.getObjet()))
                || (adversaire != null && rocheAttendue.equals(adversaire.getObjet()));
        } catch (Exception e) {
            return false;
        }
    }

    /** 5 tours par défaut, 8 si l'un des deux actifs porte Argile Pouvoir. */
    private static int dureeEcran() {
        try {
            com.tropimon.tropicalc.calc.Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
            com.tropimon.tropicalc.calc.Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            boolean argile = (joueur != null && "Argile Pouvoir".equals(joueur.getObjet()))
                || (adversaire != null && "Argile Pouvoir".equals(adversaire.getObjet()));
            return argile ? 8 : 5;
        } catch (Exception e) {
            return 5;
        }
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

    // Pièges d'entrée (côté joueur = posés par l'adversaire, subis par le joueur)
    private static boolean stealthRockJoueur = false;
    private static boolean stealthRockAdversaire = false;
    private static int spikesJoueur = 0;          // 0 à 3 couches
    private static int spikesAdversaire = 0;
    private static int toxicSpikesJoueur = 0;     // 0 à 2 couches
    private static int toxicSpikesAdversaire = 0;
    private static boolean stickyWebJoueur = false;
    private static boolean stickyWebAdversaire = false;

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
                toursMeteoRestants = 0;
            } else if ("start".equals(action) || "upkeep".equals(action)) {
                Field.Meteo nouvelle = switch (type) {
                    case "raindance" -> Field.Meteo.PLUIE;
                    case "sunnyday" -> Field.Meteo.SOLEIL;
                    case "sandstorm" -> Field.Meteo.SABLE;
                    case "snow", "snowscape", "hail" -> Field.Meteo.NEIGE;
                    case "primordialsea" -> Field.Meteo.PLUIE_INTENSE;
                    case "desolateland" -> Field.Meteo.SOLEIL_INTENSE;
                    default -> meteoActive;
                };
                // 5 tours au déclenchement, 8 si le lanceur porte la Roche
                // adéquate (approximation : on regarde les deux actifs, un
                // faux positif nécessiterait l'autre camp à porter la même
                // roche sans avoir lancé la météo - cas marginal). Les
                // upkeep ne réarment pas.
                if ("start".equals(action) && nouvelle != meteoActive) {
                    toursMeteoRestants = rocheAllongeMeteo(nouvelle) ? 8 : 5;
                }
                meteoActive = nouvelle;
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
                    if (allie) reflectJoueur = debut; else { reflectAdversaire = debut; if (debut) toursEcransAdversaireRestants = dureeEcran(); }
                }
                case "lightscreen" -> {
                    if (allie) lightScreenJoueur = debut; else { lightScreenAdversaire = debut; if (debut) toursEcransAdversaireRestants = dureeEcran(); }
                }
                case "auroraveil" -> {
                    if (allie) auroraVeilJoueur = debut; else { auroraVeilAdversaire = debut; if (debut) toursEcransAdversaireRestants = dureeEcran(); }
                }
                case "stealthrock" -> {
                    if (allie) stealthRockJoueur = debut; else stealthRockAdversaire = debut;
                }
                case "spikes" -> {
                    if (allie) spikesJoueur = debut ? Math.min(3, spikesJoueur + 1) : 0;
                    else spikesAdversaire = debut ? Math.min(3, spikesAdversaire + 1) : 0;
                }
                case "toxicspikes" -> {
                    if (allie) toxicSpikesJoueur = debut ? Math.min(2, toxicSpikesJoueur + 1) : 0;
                    else toxicSpikesAdversaire = debut ? Math.min(2, toxicSpikesAdversaire + 1) : 0;
                }
                case "stickyweb" -> {
                    if (allie) stickyWebJoueur = debut; else stickyWebAdversaire = debut;
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

    public static boolean isStealthRockJoueur() { return stealthRockJoueur; }
    public static int getSpikesJoueur() { return spikesJoueur; }
    public static int getToxicSpikesJoueur() { return toxicSpikesJoueur; }
    public static boolean isStickyWebJoueur() { return stickyWebJoueur; }

    // Durées restantes (hypothèse basse : 5 tours, sans Roche Lisse/Lumargile)
    private static int toursMeteoRestants = 0;
    private static int toursEcransAdversaireRestants = 0;

    public static int getToursMeteoRestants() { return toursMeteoRestants; }
    public static int getToursEcransAdversaireRestants() { return toursEcransAdversaireRestants; }

    public static boolean adversaireAUnEcran() {
        return reflectAdversaire || lightScreenAdversaire || auroraVeilAdversaire;
    }

    /** À appeler une fois par tour : décrémente les durées. */
    public static void nouveauTour() {
        if (toursMeteoRestants > 0) toursMeteoRestants--;
        if (toursEcransAdversaireRestants > 0) toursEcransAdversaireRestants--;
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
        stealthRockJoueur = false;
        stealthRockAdversaire = false;
        spikesJoueur = 0;
        spikesAdversaire = 0;
        toxicSpikesJoueur = 0;
        toxicSpikesAdversaire = 0;
        stickyWebJoueur = false;
        stickyWebAdversaire = false;
        toursMeteoRestants = 0;
        toursEcransAdversaireRestants = 0;
    }
}
