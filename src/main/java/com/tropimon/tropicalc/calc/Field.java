package com.tropimon.tropicalc.calc;

public class Field {

    public enum Meteo { AUCUNE, SOLEIL, SOLEIL_INTENSE, PLUIE, PLUIE_INTENSE, SABLE, NEIGE }
    public enum TypeTerrain { AUCUN, ELECTRIQUE, HERBU, PSYCHIQUE, BRUMEUX }

    public static class Ecrans {
        private boolean murLumiere = false;
        private boolean protection = false;
        private boolean brumeAurore = false;

        public boolean isMurLumiere() { return murLumiere; }
        public void setMurLumiere(boolean v) { this.murLumiere = v; }
        public boolean isProtection() { return protection; }
        public void setProtection(boolean v) { this.protection = v; }
        public boolean isBrumeAurore() { return brumeAurore; }
        public void setBrumeAurore(boolean v) { this.brumeAurore = v; }

        public double multiplicateur(Move.Categorie categorie) {
            if (categorie == Move.Categorie.PHYSIQUE && (protection || brumeAurore)) return 0.5;
            if (categorie == Move.Categorie.SPECIALE && (murLumiere || brumeAurore)) return 0.5;
            return 1.0;
        }
    }

    private Meteo meteo = Meteo.AUCUNE;
    private TypeTerrain terrain = TypeTerrain.AUCUN;
    private boolean gravite = false;

    private final Ecrans ecransJoueur = new Ecrans();
    private final Ecrans ecransAdversaire = new Ecrans();

    public Meteo getMeteo() { return meteo; }
    public void setMeteo(Meteo m) { this.meteo = m; }
    public TypeTerrain getTerrain() { return terrain; }
    public void setTerrain(TypeTerrain t) { this.terrain = t; }
    public boolean isGravite() { return gravite; }
    public void setGravite(boolean g) { this.gravite = g; }
    public Ecrans getEcransJoueur() { return ecransJoueur; }
    public Ecrans getEcransAdversaire() { return ecransAdversaire; }

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

    public double multiplicateurTerrain(PokemonType typeCapacite) {
        switch (terrain) {
            case ELECTRIQUE: return typeCapacite == PokemonType.ELECTRIK ? 1.3 : 1.0;
            case HERBU: return typeCapacite == PokemonType.PLANTE ? 1.3 : 1.0;
            case PSYCHIQUE: return typeCapacite == PokemonType.PSY ? 1.3 : 1.0;
            default: return 1.0;
        }
    }
}
