package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Species;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ProfilAdversaire;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ObservationCollector {

    private ObservationCollector() {
    }

    private static final Map<String, ProfilAdversaire> PROFILS = new HashMap<>();

    private static Double dernierPourcentageJoueur = null;
    private static Double dernierPourcentageAdversaire = null;

    private static final double TOLERANCE_POURCENT = 1.5;

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) {
            dernierPourcentageJoueur = null;
            dernierPourcentageAdversaire = null;
            return;
        }

        Pokemon joueur = BattleStateTracker.getJoueurActif();
        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        if (joueur == null || adversaire == null) {
            return;
        }

        double pvJoueur = joueur.getPourcentagePv();
        double pvAdversaire = adversaire.getPourcentagePv();

        if (dernierPourcentageAdversaire != null && pvAdversaire < dernierPourcentageAdversaire - 0.5) {
            double perte = dernierPourcentageAdversaire - pvAdversaire;
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] Baisse PV adversaire détectée : {} -> {} (perte {})",
                dernierPourcentageAdversaire, pvAdversaire, perte);
            traiterObservation(false, adversaire, joueur, perte);
        }

        if (dernierPourcentageJoueur != null && pvJoueur < dernierPourcentageJoueur - 0.5) {
            double perte = dernierPourcentageJoueur - pvJoueur;
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] Baisse PV joueur détectée : {} -> {} (perte {})",
                dernierPourcentageJoueur, pvJoueur, perte);
            traiterObservation(true, adversaire, joueur, perte);
        }

        dernierPourcentageJoueur = pvJoueur;
        dernierPourcentageAdversaire = pvAdversaire;
    }

    private static void traiterObservation(boolean adversaireEtaitAttaquant, Pokemon adversaire, Pokemon joueur,
                                            double pourcentagePerdu) {
        String coupId = MoveUseTracker.getDernierCoupRecent();
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] traiterObservation : coupId={}", coupId);
        if (coupId == null) {
            return;
        }

        MoveTemplate template = Moves.INSTANCE.getByName(coupId);
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] template trouvé pour {} : {}", coupId, template != null);
        if (template == null) {
            return;
        }

        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.estCapaciteDeStatut()) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] capacite null ou statut, abandon");
            return;
        }

        MoveUseTracker.consommer();
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] Observation enregistrée pour espèce={}", adversaire.getEspece());

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
        double observeMin = Math.max(0, pourcentagePerdu - TOLERANCE_POURCENT);
        double observeMax = pourcentagePerdu + TOLERANCE_POURCENT;

        profil.enregistrerObservation(adversaireEtaitAttaquant, adversaire, joueur, capacite, terrainNeutre,
            observeMin, observeMax);
    }

    public static ProfilAdversaire getProfil(String espece) {
        return PROFILS.get(espece);
    }

    public static void reinitialiser() {
        PROFILS.clear();
        dernierPourcentageJoueur = null;
        dernierPourcentageAdversaire = null;
    }

    private static Set<String> getTalentsReelsEspece(Pokemon adversaire) {
        Set<String> resultat = new HashSet<>();
        Species espece = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(adversaire.getEspece());
        if (espece == null) {
            return null;
        }
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
