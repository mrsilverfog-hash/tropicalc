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
 * À appeler une fois par frame (depuis l'overlay, qui tourne déjà à chaque
 * rendu) via {@link #tick()}.
 *
 * Limitations connues, assumées pour cette première version :
 * - Le coup "récent" (fenêtre de 3 secondes) est associé à la baisse de PV
 *   suivante sans garantie absolue de correspondance : des dégâts de fin de
 *   tour (brûlure, sable, Leftovers...) survenant dans cette fenêtre
 *   pourraient être attribués à tort au coup détecté.
 * - N'exploite pas encore le fait qu'un talent/objet déjà révélé par
 *   Cobblemon (via les propriétés du combat) pourrait dispenser d'inférence :
 *   on infère systématiquement, même si la vraie valeur est déjà connue.
 */
public final class ObservationCollector {

    private ObservationCollector() {
    }

    private static final Map<String, ProfilAdversaire> PROFILS = new HashMap<>();

    private static Double dernierPourcentageJoueur = null;
    private static Double dernierPourcentageAdversaire = null;

    /** Tolérance pour absorber les imprécisions de lecture des PV adverses (fraction reprojetée). */
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
            // L'adversaire a perdu des PV : on a appris quelque chose sur sa DÉFENSE.
            double perte = dernierPourcentageAdversaire - pvAdversaire;
            traiterObservation(false, adversaire, joueur, perte);
        }

        if (dernierPourcentageJoueur != null && pvJoueur < dernierPourcentageJoueur - 0.5) {
            // Nous avons perdu des PV : on a appris quelque chose sur l'ATTAQUE adverse.
            double perte = dernierPourcentageJoueur - pvJoueur;
            traiterObservation(true, adversaire, joueur, perte);
        }

        dernierPourcentageJoueur = pvJoueur;
        dernierPourcentageAdversaire = pvAdversaire;
    }

    private static void traiterObservation(boolean adversaireEtaitAttaquant, Pokemon adversaire, Pokemon joueur,
                                            double pourcentagePerdu) {
        String coupId = MoveUseTracker.getDernierCoupRecent();
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

        ProfilAdversaire profil = PROFILS.computeIfAbsent(adversaire.getEspece(), k -> {
            Set<String> talentsReels = getTalentsReelsEspece(adversaire);
            if (talentsReels == null) {
                // Espèce introuvable : repli permissif plutôt que de bloquer l'inférence.
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

    /** Renvoie le profil accumulé pour une espèce adverse donnée, ou null si encore aucune observation. */
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
            // Espèce introuvable dans le registre : on ne restreint rien plutôt
            // que de bloquer l'inférence à tort.
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
