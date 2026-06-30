package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Species;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ProfilAdversaire;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Construit des observations de dégâts en se basant sur les frontières de
 * tour (signal "cobblemon.battle.turn") plutôt que sur le timing des
 * paquets HP. Les HP de fin de tour N sont entièrement appliqués quand le
 * signal "tour N+1" arrive, ce qui rend la corrélation coup→dégâts fiable.
 *
 * Limitation connue : si l'adversaire switche, l'observation du tour est
 * ignorée (gain de PV apparent détecté = observation aberrante).
 */
public final class ObservationCollector {

    private ObservationCollector() {
    }

    private static final Map<String, ProfilAdversaire> PROFILS = new HashMap<>();
    private static final double TOLERANCE_POURCENT = 1.5;

    private static double pvJoueurDebutTour = -1;
    private static double pvAdversaireDebutTour = -1;
    private static MoveUseTracker.CoupDetecte coupDuTour = null;
    private static String espaceAdversaireDuTour = null;

    public static synchronized void signalerNouveauTour() {
        Pokemon joueur = BattleStateTracker.getJoueurActif();
        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        if (joueur == null || adversaire == null) {
            return;
        }

        double pvJoueurMaintenant = joueur.getPourcentagePv();
        double pvAdversaireMaintenant = adversaire.getPourcentagePv();

        if (coupDuTour != null && pvJoueurDebutTour >= 0 && pvAdversaireDebutTour >= 0) {
            double perteJoueur = pvJoueurDebutTour - pvJoueurMaintenant;
            double perteAdversaire = pvAdversaireDebutTour - pvAdversaireMaintenant;

            // Si l'adversaire a switché, son PV "augmente" → observation aberrante, on ignore.
            if (perteAdversaire < -5.0 || perteJoueur < -5.0) {
                pvJoueurDebutTour = pvJoueurMaintenant;
                pvAdversaireDebutTour = pvAdversaireMaintenant;
                espaceAdversaireDuTour = adversaire.getEspece();
                coupDuTour = null;
                return;
            }

            Boolean adversaireEtaitAttaquant = determinerAttaquant(coupDuTour.proprietaire());
            if (adversaireEtaitAttaquant == null) {
                adversaireEtaitAttaquant = perteJoueur > perteAdversaire;
            }

            double perte = adversaireEtaitAttaquant ? perteJoueur : perteAdversaire;

            if (perte >= 0.5) {
                enregistrerObservation(adversaireEtaitAttaquant, perte, adversaire, joueur);
            }
        }

        pvJoueurDebutTour = pvJoueurMaintenant;
        pvAdversaireDebutTour = pvAdversaireMaintenant;
        espaceAdversaireDuTour = adversaire.getEspece();
        coupDuTour = null;
    }

    public static synchronized void signalerCoupUtilise(MoveUseTracker.CoupDetecte coup) {
        coupDuTour = coup;
    }

    private static void enregistrerObservation(boolean adversaireEtaitAttaquant, double perte,
                                                Pokemon adversaire, Pokemon joueur) {
        if (coupDuTour == null) {
            return;
        }
        MoveTemplate template = Moves.INSTANCE.getByName(coupDuTour.showdownId());
        if (template == null) {
            return;
        }
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.estCapaciteDeStatut()) {
            return;
        }

        ProfilAdversaire profil = PROFILS.computeIfAbsent(adversaire.getEspece(), k -> {
            Set<String> talentsReels = getTalentsReelsEspece(adversaire);
            if (talentsReels == null) {
                Set<String> tous = new HashSet<>();
                tous.addAll(com.tropimon.tropicalc.calc.SetInferenceEngine.TALENTS_OFFENSIFS);
                tous.addAll(com.tropimon.tropicalc.calc.SetInferenceEngine.TALENTS_DEFENSIFS);
                talentsReels = tous;
            }
            return new ProfilAdversaire(talentsReels);
        });

        Field terrainNeutre = new Field();
        double observeMin = Math.max(0, perte - TOLERANCE_POURCENT);
        double observeMax = perte + TOLERANCE_POURCENT;

        profil.enregistrerObservation(adversaireEtaitAttaquant, adversaire, joueur, capacite, terrainNeutre,
            observeMin, observeMax);
    }

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) {
            reinitialiser();
        }
    }

    private static Boolean determinerAttaquant(String proprietaire) {
        if (proprietaire == null) {
            return null;
        }
        var joueurMc = MinecraftClient.getInstance().player;
        if (joueurMc == null) {
            return null;
        }
        String nomJoueur = joueurMc.getGameProfile().getName();
        return !proprietaire.equalsIgnoreCase(nomJoueur);
    }

    public static ProfilAdversaire getProfil(String espece) {
        return PROFILS.get(espece);
    }

    public static String getEspaceAdversaireCourant() {
        return espaceAdversaireDuTour;
    }

    public static void reinitialiser() {
        PROFILS.clear();
        pvJoueurDebutTour = -1;
        pvAdversaireDebutTour = -1;
        coupDuTour = null;
        espaceAdversaireDuTour = null;
    }

    private static Set<String> getTalentsReelsEspece(Pokemon adversaire) {
        Species espece = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(adversaire.getEspece());
        if (espece == null) {
            return null;
        }
        Set<String> resultat = new HashSet<>();
        for (var potentielle : espece.getAbilities()) {
            String nomShowdown = potentielle.getTemplate().getName();
            String nomFrancais = ShowdownIdMapper.talent(nomShowdown);
            if (nomFrancais != null) {
                resultat.add(nomFrancais);
            }
        }
        return resultat;
    }

    private static com.tropimon.tropicalc.calc.Move convertirCapacite(MoveTemplate template) {
        PokemonType type = ShowdownIdMapper.type(template.getElementalType().getName());
        if (type == null) {
            return null;
        }
        String categorieNom = template.getDamageCategory().getName();
        com.tropimon.tropicalc.calc.Move.Categorie categorie;
        if ("physical".equalsIgnoreCase(categorieNom)) {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        } else if ("special".equalsIgnoreCase(categorieNom)) {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE;
        } else {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.STATUT;
        }

        return com.tropimon.tropicalc.calc.Move.builder(template.getName(), type, categorie)
            .puissance((int) template.getPower())
            .build();
    }
}
