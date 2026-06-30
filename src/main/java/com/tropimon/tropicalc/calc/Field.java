package com.tropimon.tropicalc.calc;

/**
 * État du terrain de combat au moment du calcul : météo, terrain (au sol),
 * écrans actifs de chaque côté, gravité. Format Simple uniquement (1v1),
 * donc chaque côté n'a qu'un seul Pokémon actif.
 */
public class Field {

    /** Météo active sur le terrain. */
    public enum Meteo {
        AUCUNE,
        SOLEIL,
        SOLEIL_INTENSE, // ability Desolate Land
        PLUIE,
        PLUIE_INTENSE, // ability Primordial Sea
        SABLE,
        NEIGE
    }

    /** Terrain actif (sol). */
    public enum TypeTerrain {
        AUCUN,
        ELECTRIQUE,
        HERBU,
        PSYCHIQUE,
        BRUMEUX
    }

    /** Écrans défensifs actifs pour un côté du terrain. */
    public static class Ecrans {
        private boolean murLumiere = false;   // Light Screen (réduit dégâts spéciaux)
        private boolean protection = false;   // Reflect (réduit dégâts physiques)
        private boolean brumeAurore = false;  // Aurora Veil (les deux à la fois)

        public boolean isMurLumiere() {
            return murLumiere;
        }

        public void setMurLumiere(boolean murLumiere) {
            this.murLumiere = murLumiere;
        }

        public boolean isProtection() {
            return protection;
        }

        public void setProtection(boolean protection) {
            this.protection = protection;
        }

        public boolean isBrumeAurore() {
            return brumeAurore;
        }

        public void setBrumeAurore(boolean brumeAurore) {
            this.brumeAurore = brumeAurore;
        }

        /**
         * Multiplicateur de réduction de dégâts pour une capacité de la
         * catégorie donnée. 0.5 si un écran pertinent est actif, sinon 1.0.
         * (Les coups critiques ignorent les écrans : géré dans DamageCalculator.)
         */
        public double multiplicateur(Move.Categorie categorie) {
            if (categorie == Move.Categorie.PHYSIQUE && (protection || brumeAurore)) {
                return 0.5;
            }
            if (categorie == Move.Categorie.SPECIALE && (murLumiere || brumeAurore)) {
                return 0.5;
            }
            return 1.0;
        }
    }

    private Meteo meteo = Meteo.AUCUNE;
    private TypeTerrain terrain = TypeTerrain.AUCUN;
    private boolean gravite = false;

    private final Ecrans ecransJoueur = new Ecrans();
    private final Ecrans ecransAdversaire = new Ecrans();

    public Meteo getMeteo() {
        return meteo;
    }

    public void setMeteo(Meteo meteo) {
        this.meteo = meteo;
    }

    public TypeTerrain getTerrain() {
        return terrain;
    }

    public void setTerrain(TypeTerrain terrain) {
        this.terrain = terrain;
    }

    public boolean isGravite() {
        return gravite;
    }

    public void setGravite(boolean gravite) {
        this.gravite = gravite;
    }

    public Ecrans getEcransJoueur() {
        return ecransJoueur;
    }

    public Ecrans getEcransAdversaire() {
        return ecransAdversaire;
    }

    /**
     * Multiplicateur de dégâts lié à la météo pour un type de capacité donné.
     * Les météos "intenses" (Désolation, Ère Primordiale) annulent même les
     * types qu'elles affaiblissent normalement (ex: Feu sous pluie intense = 0).
     */
    public double multiplicateurMeteo(PokemonType typeCapacite) {
        switch (meteo) {
            case SOLEIL:
                if (typeCapacite == PokemonType.FEU) return 1.5;
                if (typeCapacite == PokemonType.EAU) return 0.5;
                return 1.0;
            case SOLEIL_INTENSE:
                if (typeCapacite == PokemonType.FEU) return 1.5;
                if (typeCapacite == PokemonType.EAU) return 0.0;
                return 1.0;
            case PLUIE:
                if (typeCapacite == PokemonType.EAU) return 1.5;
                if (typeCapacite == PokemonType.FEU) return 0.5;
                return 1.0;
            case PLUIE_INTENSE:
                if (typeCapacite == PokemonType.EAU) return 1.5;
                if (typeCapacite == PokemonType.FEU) return 0.0;
                return 1.0;
            default:
                return 1.0;
        }
    }

    /**
     * Multiplicateur de dégâts lié au terrain actif, pour l'attaquant
     * SI ET SEULEMENT SI il est au sol (non-Vol, pas Lévitation, etc.).
     * La vérification "au sol" est laissée à DamageCalculator, qui a accès
     * au Pokémon complet (type, talent, objet).
     */
    public double multiplicateurTerrain(PokemonType typeCapacite) {
        switch (terrain) {
            case ELECTRIQUE:
                return typeCapacite == PokemonType.ELECTRIK ? 1.3 : 1.0;
            case HERBU:
                return typeCapacite == PokemonType.PLANTE ? 1.3 : 1.0;
            case PSYCHIQUE:
                return typeCapacite == PokemonType.PSY ? 1.3 : 1.0;
            default:
                return 1.0;
        }
    }
}
