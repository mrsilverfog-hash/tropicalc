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

    /** 5 tours par défaut, 8 si l'un des deux actifs porte Lumargile. */
    private static int dureeEcran() {
        try {
            com.tropimon.tropicalc.calc.Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
            com.tropimon.tropicalc.calc.Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            boolean argile = (joueur != null && "Lumargile".equals(joueur.getObjet()))
                || (adversaire != null && "Lumargile".equals(adversaire.getObjet()));
            return argile ? 8 : 5;
        } catch (Exception e) {
            return 5;
        }
    }

    private static int dureeTerrain() {
        try {
            com.tropimon.tropicalc.calc.Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
            com.tropimon.tropicalc.calc.Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            boolean champduit = (joueur != null && "Champ'Duit".equals(joueur.getObjet()))
                || (adversaire != null && "Champ'Duit".equals(adversaire.getObjet()));
            return champduit ? 8 : 5;
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

    private static boolean substituteJoueur = false;
    private static boolean substituteAdversaire = false;

    /** Nombre de tours restants avant l'impact - 0 si aucune Prescience en vol. */
    private static int futureSightJoueurTours = 0;      // va me toucher
    private static int futureSightAdversaireTours = 0;  // va toucher l'adversaire

    public static boolean joueurAUnClone() { return substituteJoueur; }
    public static boolean adversaireAUnClone() { return substituteAdversaire; }
    public static int getFutureSightJoueurTours() { return futureSightJoueurTours; }
    public static int getFutureSightAdversaireTours() { return futureSightAdversaireTours; }

    /**
     * Espèce présumée avoir posé l'écran adverse actif, capturée au moment
     * du sidestart (le Pokémon adverse actif à cet instant en est
     * l'auteur quasi-certain, puisque c'est lui qui vient d'agir ce tour).
     * Sert uniquement à l'inférence Lumargile ci-dessous - ne
     * remplace pas un vrai suivi de propriétaire, non disponible dans ce
     * message précis.
     */
    private static String poseurEcranAdversaire = null;

    /** Évite de réappliquer la correction Lumargile en boucle une fois faite pour cet écran. */
    private static boolean correctionArgilePouvoirAppliquee = false;

    private static int toursTerrainRestants = 0;
    /** Même principe que poseurEcranAdversaire, mais pour le terrain (peut être posé par n'importe quel camp). */
    private static String poseurTerrainAdversaire = null;
    private static boolean correctionChampDuitAppliquee = false;

    public static int getToursTerrainRestants() { return toursTerrainRestants; }

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
                    if (allie) { reflectJoueur = debut; if (debut) toursEcransJoueurRestants = dureeEcran(); }
                    else { reflectAdversaire = debut; if (debut) { toursEcransAdversaireRestants = dureeEcran(); capturerPoseurEcran(); } }
                }
                case "lightscreen" -> {
                    if (allie) { lightScreenJoueur = debut; if (debut) toursEcransJoueurRestants = dureeEcran(); }
                    else { lightScreenAdversaire = debut; if (debut) { toursEcransAdversaireRestants = dureeEcran(); capturerPoseurEcran(); } }
                }
                case "auroraveil" -> {
                    if (allie) { auroraVeilJoueur = debut; if (debut) toursEcransJoueurRestants = dureeEcran(); }
                    else { auroraVeilAdversaire = debut; if (debut) { toursEcransAdversaireRestants = dureeEcran(); capturerPoseurEcran(); } }
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

        // Clone (Substitute) : le porteur est directement l'argument, jamais
        // une cible - déclenché par sa propre capacité, sur lui-même.
        if (cle.equals("cobblemon.battle.start.substitute") || cle.equals("cobblemon.battle.end.substitute")) {
            String poseur = MoveUseTracker.extraireProprietaire(contenu.getArgs().length > 0 ? contenu.getArgs()[0] : null);
            Boolean estAdversaire = ObservationCollector.determinerAttaquant(poseur);
            if (estAdversaire == null) return;
            boolean actif = cle.endsWith("start.substitute");
            if (estAdversaire) substituteAdversaire = actif; else substituteJoueur = actif;
            return;
        }

        // Prescience (Future Sight) : frappe le Pokémon qui occupe la
        // position visée 2 tours après le lancement, PAS forcément celui
        // ciblé au départ (un switch entre-temps change la cible réelle -
        // confirmé par observation directe). L'indicateur ne nomme donc
        // jamais de cible précise, juste "dans X tours".
        if (cle.equals("cobblemon.battle.start.futuresight")) {
            String lanceur = MoveUseTracker.extraireProprietaire(contenu.getArgs().length > 0 ? contenu.getArgs()[0] : null);
            Boolean estAdversaire = ObservationCollector.determinerAttaquant(lanceur);
            if (estAdversaire == null) return;
            // Le lanceur est CELUI qui a envoyé Prescience : si c'est
            // l'adversaire, l'impact me touchera MOI (compteur joueur) ;
            // si c'est moi, l'impact touchera l'ADVERSAIRE (compteur adv).
            if (estAdversaire) futureSightJoueurTours = 2; else futureSightAdversaireTours = 2;
            return;
        }
        if (cle.equals("cobblemon.battle.end.futuresight")) {
            String cible = MoveUseTracker.extraireProprietaire(contenu.getArgs().length > 0 ? contenu.getArgs()[0] : null);
            Boolean estAdversaire = ObservationCollector.determinerAttaquant(cible);
            if (estAdversaire == null) return;
            if (estAdversaire) futureSightAdversaireTours = 0; else futureSightJoueurTours = 0;
            return;
        }

        // Terrains
        if (cle.contains("electricterrain") || cle.contains("grassyterrain")
                || cle.contains("psychicterrain") || cle.contains("mistyterrain")) {
            boolean finTerrain = cle.endsWith(".end");
            if (cle.contains("electricterrain")) terrainActif = finTerrain ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.ELECTRIQUE;
            else if (cle.contains("grassyterrain")) terrainActif = finTerrain ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.HERBU;
            else if (cle.contains("psychicterrain")) terrainActif = finTerrain ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.PSYCHIQUE;
            else terrainActif = finTerrain ? Field.TypeTerrain.AUCUN : Field.TypeTerrain.BRUMEUX;

            if (finTerrain) {
                toursTerrainRestants = 0;
            } else {
                toursTerrainRestants = dureeTerrain();
                capturerPoseurTerrain();
            }
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
    private static int toursEcransJoueurRestants = 0;

    public static int getToursMeteoRestants() { return toursMeteoRestants; }
    public static int getToursEcransAdversaireRestants() { return toursEcransAdversaireRestants; }
    public static int getToursEcransJoueurRestants() { return toursEcransJoueurRestants; }

    public static boolean adversaireAUnEcran() {
        return reflectAdversaire || lightScreenAdversaire || auroraVeilAdversaire;
    }

    public static boolean joueurAUnEcran() {
        return reflectJoueur || lightScreenJoueur || auroraVeilJoueur;
    }

    public static boolean adversaireAReflet() { return reflectAdversaire; }
    public static boolean adversaireAMurLumiere() { return lightScreenAdversaire; }
    public static boolean adversaireAVoileAurore() { return auroraVeilAdversaire; }
    public static boolean joueurAReflet() { return reflectJoueur; }
    public static boolean joueurAMurLumiere() { return lightScreenJoueur; }
    public static boolean joueurAVoileAurore() { return auroraVeilJoueur; }

    /** À appeler une fois par tour : décrémente les durées. */
    private static void capturerPoseurEcran() {
        correctionArgilePouvoirAppliquee = false;
        try {
            com.tropimon.tropicalc.calc.Pokemon adv = BattleStateTracker.getAdversaireActif();
            if (adv != null) poseurEcranAdversaire = adv.getEspece();
        } catch (Exception ignored) {
        }
    }

    private static void capturerPoseurTerrain() {
        correctionChampDuitAppliquee = false;
        try {
            com.tropimon.tropicalc.calc.Pokemon adv = BattleStateTracker.getAdversaireActif();
            if (adv != null) poseurTerrainAdversaire = adv.getEspece();
        } catch (Exception ignored) {
        }
    }

    public static void nouveauTour() {
        if (toursMeteoRestants > 0) toursMeteoRestants--;
        if (futureSightJoueurTours > 0) futureSightJoueurTours--;
        if (futureSightAdversaireTours > 0) futureSightAdversaireTours--;
        if (toursEcransJoueurRestants > 0) toursEcransJoueurRestants--;
        if (toursEcransAdversaireRestants > 0) {
            toursEcransAdversaireRestants--;
            // Le mur dure encore alors que notre hypothèse de départ (5
            // tours, pas d'Lumargile détecté au moment du lancement)
            // vient d'expirer : c'est la preuve comportementale que le
            // porteur a Lumargile depuis le début. Corrige la durée
            // restante vers 8 tours totaux, et confirme l'objet plutôt que
            // de continuer à afficher '?' - certain, pas juste probable.
            // Ne se déclenche qu'une fois par écran (flag remis à zéro au
            // sidestart suivant) pour ne pas se redéclencher à tort si la
            // durée initiale était déjà 8.
            if (toursEcransAdversaireRestants == 0 && adversaireAUnEcran() && !correctionArgilePouvoirAppliquee) {
                toursEcransAdversaireRestants = 3;   // 8 tours totaux - 5 déjà écoulés
                ObservationCollector.confirmerObjetDirect(poseurEcranAdversaire, "Lumargile");
                correctionArgilePouvoirAppliquee = true;
            }
        }
        if (toursTerrainRestants > 0) {
            toursTerrainRestants--;
            // Même logique que Lumargile ci-dessus, pour Champ'Duit sur le terrain.
            if (toursTerrainRestants == 0 && terrainActif != Field.TypeTerrain.AUCUN && !correctionChampDuitAppliquee) {
                toursTerrainRestants = 3;
                ObservationCollector.confirmerObjetDirect(poseurTerrainAdversaire, "Champ'Duit");
                correctionChampDuitAppliquee = true;
            }
        }
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
        toursEcransJoueurRestants = 0;
        substituteJoueur = false;
        substituteAdversaire = false;
        futureSightJoueurTours = 0;
        futureSightAdversaireTours = 0;
        poseurEcranAdversaire = null;
        correctionArgilePouvoirAppliquee = false;
        toursTerrainRestants = 0;
        poseurTerrainAdversaire = null;
        correctionChampDuitAppliquee = false;
    }
}
