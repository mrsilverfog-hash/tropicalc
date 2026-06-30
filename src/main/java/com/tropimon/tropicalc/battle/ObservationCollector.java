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

/**
 * Détecte automatiquement les baisses de PV en combat (côté joueur ou côté
 * adverse) et, si un coup a été identifié récemment par MoveUseTracker, les
 * transforme en observations pour resserrer le ProfilAdversaire courant.
 *
 * À appeler une fois par frame (depuis l'overlay) via {@link #tick()}.
 *
 * La barre de PV de Cobblemon s'anime progressivement vers sa valeur finale
 * (plusieurs micro-changements en l'espace d'une seconde environ). Pour ne
 * générer qu'UNE SEULE observation propre par coup plutôt qu'une dizaine de
 * micro-observations bruitées, on attend que la valeur lue soit stable
 * pendant DEBOUNCE_MS avant de la considérer comme "définitive".
 */
public final class ObservationCollector {

    private ObservationCollector() {
    }

    private static final Map<String, ProfilAdversaire> PROFILS = new HashMap<>();

    private static final long DEBOUNCE_MS = 400;

    private static Double valeurEnAttenteJoueur = null;
    private static long dernierChangementJoueurMs = 0L;
    private static Double dernierePourcentageStableJoueur = null;

    private static Double valeurEnAttenteAdversaire = null;
    private static long dernierChangementAdversaireMs = 0L;
    private static Double dernierePourcentageStableAdversaire = null;

    private static final double TOLERANCE_POURCENT = 1.5;

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) {
            reinitialiser();
            return;
        }

        Pokemon joueur = BattleStateTracker.getJoueurActif();
        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        if (joueur == null || adversaire == null) {
            return;
        }

        long maintenant = System.currentTimeMillis();
        double pvJoueur = joueur.getPourcentagePv();
        double pvAdversaire = adversaire.getPourcentagePv();

        // --- Côté joueur ---
        if (valeurEnAttenteJoueur == null || valeurEnAttenteJoueur != pvJoueur) {
            valeurEnAttenteJoueur = pvJoueur;
            dernierChangementJoueurMs = maintenant;
        } else if (maintenant - dernierChangementJoueurMs >= DEBOUNCE_MS) {
            if (dernierePourcentageStableJoueur != null && pvJoueur < dernierePourcentageStableJoueur - 0.5) {
                double perte = dernierePourcentageStableJoueur - pvJoueur;
                com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                    "[TropiCalc-diag] Baisse PV joueur (stabilisée) : {} -> {} (perte {})",
                    dernierePourcentageStableJoueur, pvJoueur, perte);
                traiterObservation(true, adversaire, joueur, perte);
            }
            dernierePourcentageStableJoueur = pvJoueur;
        }

        // --- Côté adversaire ---
        if (valeurEnAttenteAdversaire == null || valeurEnAttenteAdversaire != pvAdversaire) {
            valeurEnAttenteAdversaire = pvAdversaire;
            dernierChangementAdversaireMs = maintenant;
        } else if (maintenant - dernierChangementAdversaireMs >= DEBOUNCE_MS) {
            if (dernierePourcentageStableAdversaire != null && pvAdversaire < dernierePourcentageStableAdversaire - 0.5) {
                double perte = dernierePourcentageStableAdversaire - pvAdversaire;
                com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                    "[TropiCalc-diag] Baisse PV adversaire (stabilisée) : {} -> {} (perte {})",
                    dernierePourcentageStableAdversaire, pvAdversaire, perte);
                traiterObservation(false, adversaire, joueur, perte);
            }
            dernierePourcentageStableAdversaire = pvAdversaire;
        }
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
        if (template == null) {
            return;
        }

        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.estCapaciteDeStatut()) {
            return;
        }

        MoveUseTracker.consommer();
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] Observation enregistrée pour espèce={} coup={}", adversaire.getEspece(), coupId);

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
        valeurEnAttenteJoueur = null;
        dernierePourcentageStableJoueur = null;
        valeurEnAttenteAdversaire = null;
        dernierePourcentageStableAdversaire = null;
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
