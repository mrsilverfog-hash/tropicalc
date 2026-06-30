package com.tropimon.tropicalc.calc;

/**
 * Les 6 statistiques d'un Pokémon. PV n'est jamais affectée par une nature.
 */
public enum Stat {
    PV("PV"),
    ATTAQUE("Attaque"),
    DEFENSE("Défense"),
    ATTAQUE_SPE("Attaque Spéciale"),
    DEFENSE_SPE("Défense Spéciale"),
    VITESSE("Vitesse");

    private final String nomFrancais;

    Stat(String nomFrancais) {
        this.nomFrancais = nomFrancais;
    }

    public String getNomFrancais() {
        return nomFrancais;
    }
}
