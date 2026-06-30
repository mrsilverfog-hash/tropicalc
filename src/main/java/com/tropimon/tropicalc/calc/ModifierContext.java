package com.tropimon.tropicalc.calc;

/**
 * Contexte passé aux ItemModifier et AbilityModifier lors du calcul de
 * dégâts. Contient les informations du combat en cours ainsi que des
 * accumulateurs mutables que chaque objet/talent peut modifier avant que
 * DamageCalculator ne les applique à la formule finale.
 */
public class ModifierContext {

    public final Pokemon attaquant;
    public final Pokemon defenseur;
    public final Move capacite;
    public final Field terrain;
    public final boolean critique;

    public double multiplicateurAttaque = 1.0;
    public double multiplicateurDefense = 1.0;
    public double multiplicateurDegatsFinal = 1.0;
    public boolean stabAugmente = false;
    public boolean immuniteType = false;
    public boolean ignorerStagesDefenseur = false;
    public boolean ignorerStagesAttaquant = false;
    public boolean ignorerPenaliteBrulure = false;

    public ModifierContext(Pokemon attaquant, Pokemon defenseur, Move capacite, Field terrain, boolean critique) {
        this.attaquant = attaquant;
        this.defenseur = defenseur;
        this.capacite = capacite;
        this.terrain = terrain;
        this.critique = critique;
    }
}
