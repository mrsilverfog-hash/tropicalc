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

    // --- Accumulateurs modifiables par les objets/talents ---

    /** Multiplie la stat offensive (Attaque ou Attaque Spéciale) de l'attaquant. */
    public double multiplicateurAttaque = 1.0;

    /** Multiplie la stat défensive (Défense ou Défense Spéciale) du défenseur. */
    public double multiplicateurDefense = 1.0;

    /** Multiplicateur final appliqué directement aux dégâts (Orbe Vie, Ceinture Brutale...). */
    public double multiplicateurDegatsFinal = 1.0;

    /** Si vrai, le STAB passe de x1.5 à x2.0 (Adaptabilité). */
    public boolean stabAugmente = false;

    /** Si vrai, la capacité n'inflige aucun dégât (immunité de talent : Lévitation, Absorb'Eau...). */
    public boolean immuniteType = false;

    /** Si vrai (Lucidité côté attaquant), on ignore les stages de Défense/Déf. Spé. du défenseur. */
    public boolean ignorerStagesDefenseur = false;

    /** Si vrai (Lucidité côté défenseur), on ignore les stages d'Attaque/Att. Spé. de l'attaquant. */
    public boolean ignorerStagesAttaquant = false;

    /** Si vrai (Cran), on ignore la pénalité de brûlure sur les dégâts physiques. */
    public boolean ignorerPenaliteBrulure = false;

    public ModifierContext(Pokemon attaquant, Pokemon defenseur, Move capacite, Field terrain, boolean critique) {
        this.attaquant = attaquant;
        this.defenseur = defenseur;
        this.capacite = capacite;
        this.terrain = terrain;
        this.critique = critique;
    }
}
