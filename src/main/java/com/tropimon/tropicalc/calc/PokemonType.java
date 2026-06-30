package com.tropimon.tropicalc.calc;

import java.util.EnumMap;
import java.util.Map;

/**
 * Les 18 types Pokémon et leur table d'efficacité (Génération 9).
 * Gère aussi le cas de la Téracristallisation, où le type défenseur
 * effectif est remplacé par le Type Téracristal du Pokémon.
 */
public enum PokemonType {
    NORMAL("Normal"),
    FEU("Feu"),
    EAU("Eau"),
    ELECTRIK("Électrik"),
    PLANTE("Plante"),
    GLACE("Glace"),
    COMBAT("Combat"),
    POISON("Poison"),
    SOL("Sol"),
    VOL("Vol"),
    PSY("Psy"),
    INSECTE("Insecte"),
    ROCHE("Roche"),
    SPECTRE("Spectre"),
    DRAGON("Dragon"),
    TENEBRES("Ténèbres"),
    ACIER("Acier"),
    FEE("Fée"),
    // Type spécial : un Pokémon Téracristallisé sans type Tera assigné explicitement
    // n'utilise jamais STELLAIRE comme type défenseur ; uniquement comme type
    // offensif possible pour les capacités Tera Brillante.
    STELLAIRE("Stellaire");

    private final String nomFrancais;

    PokemonType(String nomFrancais) {
        this.nomFrancais = nomFrancais;
    }

    public String getNomFrancais() {
        return nomFrancais;
    }

    // Table d'efficacité : pour chaque type attaquant, le multiplicateur appliqué
    // à chaque type défenseur. Absence d'entrée = x1 (efficacité normale).
    private static final Map<PokemonType, Map<PokemonType, Double>> TABLE = new EnumMap<>(PokemonType.class);

    private static void def(PokemonType attaquant, PokemonType defenseur, double multiplicateur) {
        TABLE.computeIfAbsent(attaquant, k -> new EnumMap<>(PokemonType.class)).put(defenseur, multiplicateur);
    }

    static {
        // NORMAL
        def(NORMAL, ROCHE, 0.5);
        def(NORMAL, ACIER, 0.5);
        def(NORMAL, SPECTRE, 0.0);

        // FEU
        def(FEU, FEU, 0.5);
        def(FEU, EAU, 0.5);
        def(FEU, PLANTE, 2.0);
        def(FEU, GLACE, 2.0);
        def(FEU, INSECTE, 2.0);
        def(FEU, ROCHE, 0.5);
        def(FEU, DRAGON, 0.5);
        def(FEU, ACIER, 2.0);

        // EAU
        def(EAU, FEU, 2.0);
        def(EAU, EAU, 0.5);
        def(EAU, PLANTE, 0.5);
        def(EAU, SOL, 2.0);
        def(EAU, ROCHE, 2.0);
        def(EAU, DRAGON, 0.5);

        // ELECTRIK
        def(ELECTRIK, EAU, 2.0);
        def(ELECTRIK, ELECTRIK, 0.5);
        def(ELECTRIK, PLANTE, 0.5);
        def(ELECTRIK, SOL, 0.0);
        def(ELECTRIK, VOL, 2.0);
        def(ELECTRIK, DRAGON, 0.5);

        // PLANTE
        def(PLANTE, FEU, 0.5);
        def(PLANTE, EAU, 2.0);
        def(PLANTE, PLANTE, 0.5);
        def(PLANTE, POISON, 0.5);
        def(PLANTE, SOL, 2.0);
        def(PLANTE, VOL, 0.5);
        def(PLANTE, INSECTE, 0.5);
        def(PLANTE, ROCHE, 2.0);
        def(PLANTE, DRAGON, 0.5);
        def(PLANTE, ACIER, 0.5);

        // GLACE
        def(GLACE, FEU, 0.5);
        def(GLACE, EAU, 0.5);
        def(GLACE, PLANTE, 2.0);
        def(GLACE, GLACE, 0.5);
        def(GLACE, SOL, 2.0);
        def(GLACE, VOL, 2.0);
        def(GLACE, DRAGON, 2.0);
        def(GLACE, ACIER, 0.5);

        // COMBAT
        def(COMBAT, NORMAL, 2.0);
        def(COMBAT, GLACE, 2.0);
        def(COMBAT, POISON, 0.5);
        def(COMBAT, VOL, 0.5);
        def(COMBAT, PSY, 0.5);
        def(COMBAT, INSECTE, 0.5);
        def(COMBAT, ROCHE, 2.0);
        def(COMBAT, SPECTRE, 0.0);
        def(COMBAT, TENEBRES, 2.0);
        def(COMBAT, ACIER, 2.0);
        def(COMBAT, FEE, 0.5);

        // POISON
        def(POISON, PLANTE, 2.0);
        def(POISON, POISON, 0.5);
        def(POISON, SOL, 0.5);
        def(POISON, ROCHE, 0.5);
        def(POISON, SPECTRE, 0.5);
        def(POISON, ACIER, 0.0);
        def(POISON, FEE, 2.0);

        // SOL
        def(SOL, FEU, 2.0);
        def(SOL, ELECTRIK, 2.0);
        def(SOL, PLANTE, 0.5);
        def(SOL, POISON, 2.0);
        def(SOL, VOL, 0.0);
        def(SOL, INSECTE, 0.5);
        def(SOL, ROCHE, 2.0);
        def(SOL, ACIER, 2.0);

        // VOL
        def(VOL, ELECTRIK, 0.5);
        def(VOL, PLANTE, 2.0);
        def(VOL, COMBAT, 2.0);
        def(VOL, INSECTE, 2.0);
        def(VOL, ROCHE, 0.5);
        def(VOL, ACIER, 0.5);

        // PSY
        def(PSY, COMBAT, 2.0);
        def(PSY, POISON, 2.0);
        def(PSY, PSY, 0.5);
        def(PSY, ACIER, 0.5);
        def(PSY, TENEBRES, 0.0);

        // INSECTE
        def(INSECTE, FEU, 0.5);
        def(INSECTE, PLANTE, 2.0);
        def(INSECTE, COMBAT, 0.5);
        def(INSECTE, POISON, 0.5);
        def(INSECTE, VOL, 0.5);
        def(INSECTE, PSY, 2.0);
        def(INSECTE, SPECTRE, 0.5);
        def(INSECTE, TENEBRES, 2.0);
        def(INSECTE, ACIER, 0.5);
        def(INSECTE, FEE, 0.5);

        // ROCHE
        def(ROCHE, FEU, 2.0);
        def(ROCHE, GLACE, 2.0);
        def(ROCHE, COMBAT, 0.5);
        def(ROCHE, SOL, 0.5);
        def(ROCHE, VOL, 2.0);
        def(ROCHE, INSECTE, 2.0);
        def(ROCHE, ACIER, 0.5);

        // SPECTRE
        def(SPECTRE, NORMAL, 0.0);
        def(SPECTRE, PSY, 2.0);
        def(SPECTRE, SPECTRE, 2.0);
        def(SPECTRE, TENEBRES, 0.5);

        // DRAGON
        def(DRAGON, DRAGON, 2.0);
        def(DRAGON, ACIER, 0.5);
        def(DRAGON, FEE, 0.0);

        // TENEBRES
        def(TENEBRES, COMBAT, 0.5);
        def(TENEBRES, PSY, 2.0);
        def(TENEBRES, SPECTRE, 2.0);
        def(TENEBRES, TENEBRES, 0.5);
        def(TENEBRES, FEE, 0.5);

        // ACIER
        def(ACIER, FEU, 0.5);
        def(ACIER, EAU, 0.5);
        def(ACIER, ELECTRIK, 0.5);
        def(ACIER, GLACE, 2.0);
        def(ACIER, ROCHE, 2.0);
        def(ACIER, ACIER, 0.5);
        def(ACIER, FEE, 2.0);

        // FEE
        def(FEE, FEU, 0.5);
        def(FEE, COMBAT, 2.0);
        def(FEE, POISON, 0.5);
        def(FEE, DRAGON, 2.0);
        def(FEE, TENEBRES, 2.0);
        def(FEE, ACIER, 0.5);

        // STELLAIRE : type offensif uniquement (capacité Tera Brillante).
        // Efficacité neutre (x1) contre tout, sauf x2 contre un Pokémon
        // Téracristallisé (peu importe son type Tera). Ce cas particulier
        // est géré directement dans DamageCalculator plutôt qu'ici.
    }

    /**
     * Renvoie le multiplicateur d'efficacité d'un type attaquant contre
     * UN type défenseur. Pour un Pokémon à deux types, multiplier les
     * deux résultats entre eux.
     */
    public double efficaciteContre(PokemonType defenseur) {
        Map<PokemonType, Double> ligne = TABLE.get(this);
        if (ligne == null) {
            return 1.0;
        }
        return ligne.getOrDefault(defenseur, 1.0);
    }

    /**
     * Calcule l'efficacité totale d'un type attaquant contre un Pokémon
     * pouvant avoir un ou deux types défenseurs.
     */
    public double efficaciteContre(PokemonType defenseur1, PokemonType defenseur2) {
        double multiplicateur = efficaciteContre(defenseur1);
        if (defenseur2 != null && defenseur2 != defenseur1) {
            multiplicateur *= efficaciteContre(defenseur2);
        }
        return multiplicateur;
    }
}
