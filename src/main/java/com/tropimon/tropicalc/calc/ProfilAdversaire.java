package com.tropimon.tropicalc.calc;

import java.util.HashSet;
import java.util.Set;

/**
 * Regroupe les hypothèses courantes sur les 4 statistiques inférables
 * (Attaque, Att. Spé., Défense, Déf. Spé.) d'un Pokémon adverse donné, et
 * fournit un point d'entrée unique pour enregistrer une observation de
 * dégâts (qu'elle vienne d'un coup subi ou d'un coup infligé).
 */
public class ProfilAdversaire {

    public final StatHypothesis attaque;
    public final StatHypothesis attaqueSpe;
    public final StatHypothesis defense;
    public final StatHypothesis defenseSpe;

    /**
     * @param talentsReelsEspece les talents que l'espèce peut réellement avoir
     *                           dans le jeu (obtenus depuis Cobblemon), filtrés
     *                           à ceux qu'on sait déjà gérer dans AbilityModifier.
     *                           Un ensemble vide signifie "aucun de ses vrais
     *                           talents n'est encore géré par le moteur".
     */
    public ProfilAdversaire(Set<String> talentsReelsEspece) {
        Set<String> talentsOffensifsReels = intersection(SetInferenceEngine.TALENTS_OFFENSIFS, talentsReelsEspece);
        Set<String> talentsDefensifsReels = intersection(SetInferenceEngine.TALENTS_DEFENSIFS, talentsReelsEspece);

        this.attaque = new StatHypothesis(SetInferenceEngine.OBJETS_OFFENSIFS, talentsOffensifsReels);
        this.attaqueSpe = new StatHypothesis(SetInferenceEngine.OBJETS_OFFENSIFS, talentsOffensifsReels);
        this.defense = new StatHypothesis(SetInferenceEngine.OBJETS_DEFENSIFS, talentsDefensifsReels);
        this.defenseSpe = new StatHypothesis(SetInferenceEngine.OBJETS_DEFENSIFS, talentsDefensifsReels);
    }

    public StatHypothesis pour(Stat stat) {
        return switch (stat) {
            case ATTAQUE -> attaque;
            case ATTAQUE_SPE -> attaqueSpe;
            case DEFENSE -> defense;
            case DEFENSE_SPE -> defenseSpe;
            default -> null;
        };
    }

    /**
     * Enregistre une observation de dégâts et resserre l'hypothèse
     * correspondante.
     *
     * @param adversaireEtaitAttaquant true si l'adversaire a infligé les dégâts (on
     *                                 apprend sur son Attaque/Att.Spé.), false s'il
     *                                 les a subis (on apprend sur sa Défense/Déf.Spé.)
     * @param adversairePartiel l'adversaire, dont on connaît l'espèce/niveau/types
     *                          mais pas encore la stat ciblée
     * @param nous              notre propre Pokémon, entièrement connu
     */
    public void enregistrerObservation(boolean adversaireEtaitAttaquant, Pokemon adversairePartiel, Pokemon nous,
                                        Move capacite, Field terrain,
                                        double pourcentageObserveMin, double pourcentageObserveMax) {
        if (capacite == null || capacite.estCapaciteDeStatut()) {
            return;
        }

        Stat statCible = capacite.getCategorie() == Move.Categorie.PHYSIQUE
            ? (adversaireEtaitAttaquant ? Stat.ATTAQUE : Stat.DEFENSE)
            : (adversaireEtaitAttaquant ? Stat.ATTAQUE_SPE : Stat.DEFENSE_SPE);

        StatHypothesis hypothese = pour(statCible);
        SetInferenceEngine.narrow(hypothese, statCible, adversaireEtaitAttaquant, adversairePartiel, nous,
            capacite, terrain, pourcentageObserveMin, pourcentageObserveMax);
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> resultat = new HashSet<>(a);
        resultat.retainAll(b);
        return resultat;
    }
}
