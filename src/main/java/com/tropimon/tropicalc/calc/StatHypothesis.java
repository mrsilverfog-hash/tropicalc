package com.tropimon.tropicalc.calc;

import java.util.HashSet;
import java.util.Set;

/**
 * Hypothèse courante sur une statistique précise (Attaque, Défense, etc.)
 * d'un Pokémon adverse partiellement inconnu. Se resserre au fil des
 * observations de dégâts via SetInferenceEngine.narrow().
 *
 * Au départ (aucune observation), tout est possible : 0-252 EV, n'importe
 * quelle nature, n'importe quel objet/talent pertinent pour cette stat
 * (y compris "aucun objet" / "aucun talent pertinent", représenté par la
 * constante AUCUN).
 */
public class StatHypothesis {

    /** Valeur sentinelle représentant explicitement "pas d'objet" ou "pas de talent pertinent". */
    public static final String AUCUN = "Aucun";

    public int evMin = 0;
    public int evMax = 252;

    public boolean peutEtreBoostee = true;
    public boolean peutEtreNeutre = true;
    public boolean peutEtreBaissee = true;

    /** Objets candidats restants (inclut toujours potentiellement AUCUN). */
    public final Set<String> objetsPossibles;

    public final Set<String> talentsPossibles;

    /** Nombre d'observations ayant servi à construire cette hypothèse. */
    public int nombreObservations = 0;

    public StatHypothesis(Set<String> objetsCandidatsInitiaux, Set<String> talentsCandidatsInitiaux) {
        this.objetsPossibles = new HashSet<>(objetsCandidatsInitiaux);
        this.objetsPossibles.add(AUCUN);
        this.talentsPossibles = new HashSet<>(talentsCandidatsInitiaux);
        this.talentsPossibles.add(AUCUN);
    }

    public boolean estResolue() {
        return evMin == evMax && objetsPossibles.size() <= 1 && talentsPossibles.size() <= 1
            && (peutEtreBoostee ? 1 : 0) + (peutEtreNeutre ? 1 : 0) + (peutEtreBaissee ? 1 : 0) == 1;
    }

    public boolean aucuneObservation() {
        return nombreObservations == 0;
    }
}
