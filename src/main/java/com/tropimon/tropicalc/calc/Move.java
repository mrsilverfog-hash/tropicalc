package com.tropimon.tropicalc.calc;

/**
 * Représente une capacité (attaque) utilisable en combat.
 * Couvre les informations nécessaires au calcul de dégâts : puissance,
 * type, catégorie, ainsi que les drapeaux utilisés par certains talents
 * et objets (Poing de Fer, Crocs Acier, Lance-Méga, etc.).
 */
public class Move {

    /** Catégorie d'une capacité. */
    public enum Categorie {
        PHYSIQUE,
        SPECIALE,
        STATUT
    }

    private final String nom;
    private final PokemonType type;
    private final Categorie categorie;
    private final int puissanceDeBase;
    private final int precision; // 0 = ne peut jamais manquer
    private final int prioritee;
    private final int ratioCritique; // 0 = normal, 1 = ratio augmenté (ex. Lame Feuille), etc.
    private final int coupsMin;
    private final int coupsMax;

    // Drapeaux utilisés par certains talents/objets
    private final boolean contact;
    private final boolean son;
    private final boolean poing;
    private final boolean morsure;
    private final boolean pulsation;
    private final boolean tranchant;
    private final boolean bombe;
    private final boolean rampant; // capacités "lancées au sol" affectées par Bouclier Royal etc.

    private Move(Builder b) {
        this.nom = b.nom;
        this.type = b.type;
        this.categorie = b.categorie;
        this.puissanceDeBase = b.puissanceDeBase;
        this.precision = b.precision;
        this.prioritee = b.prioritee;
        this.ratioCritique = b.ratioCritique;
        this.coupsMin = b.coupsMin;
        this.coupsMax = b.coupsMax;
        this.contact = b.contact;
        this.son = b.son;
        this.poing = b.poing;
        this.morsure = b.morsure;
        this.pulsation = b.pulsation;
        this.tranchant = b.tranchant;
        this.bombe = b.bombe;
        this.rampant = b.rampant;
    }

    public String getNom() {
        return nom;
    }

    public PokemonType getType() {
        return type;
    }

    public Categorie getCategorie() {
        return categorie;
    }

    public int getPuissanceDeBase() {
        return puissanceDeBase;
    }

    public int getPrecision() {
        return precision;
    }

    public int getPrioritee() {
        return prioritee;
    }

    public int getRatioCritique() {
        return ratioCritique;
    }

    public int getCoupsMin() {
        return coupsMin;
    }

    public int getCoupsMax() {
        return coupsMax;
    }

    public boolean isMultiCoups() {
        return coupsMax > 1;
    }

    public boolean isContact() {
        return contact;
    }

    public boolean isSon() {
        return son;
    }

    public boolean isPoing() {
        return poing;
    }

    public boolean isMorsure() {
        return morsure;
    }

    public boolean isPulsation() {
        return pulsation;
    }

    public boolean isTranchant() {
        return tranchant;
    }

    public boolean isBombe() {
        return bombe;
    }

    public boolean isRampant() {
        return rampant;
    }

    public boolean estCapaciteDeStatut() {
        return categorie == Categorie.STATUT;
    }

    public static Builder builder(String nom, PokemonType type, Categorie categorie) {
        return new Builder(nom, type, categorie);
    }

    /**
     * Constructeur fluide : permet de ne définir que les drapeaux pertinents
     * pour chaque capacité, le reste prenant des valeurs par défaut sûres.
     */
    public static class Builder {
        private final String nom;
        private final PokemonType type;
        private final Categorie categorie;
        private int puissanceDeBase = 0;
        private int precision = 100;
        private int prioritee = 0;
        private int ratioCritique = 0;
        private int coupsMin = 1;
        private int coupsMax = 1;
        private boolean contact = false;
        private boolean son = false;
        private boolean poing = false;
        private boolean morsure = false;
        private boolean pulsation = false;
        private boolean tranchant = false;
        private boolean bombe = false;
        private boolean rampant = false;

        private Builder(String nom, PokemonType type, Categorie categorie) {
            this.nom = nom;
            this.type = type;
            this.categorie = categorie;
        }

        public Builder puissance(int valeur) {
            this.puissanceDeBase = valeur;
            return this;
        }

        public Builder precision(int valeur) {
            this.precision = valeur;
            return this;
        }

        public Builder prioritee(int valeur) {
            this.prioritee = valeur;
            return this;
        }

        public Builder ratioCritique(int valeur) {
            this.ratioCritique = valeur;
            return this;
        }

        public Builder multiCoups(int min, int max) {
            this.coupsMin = min;
            this.coupsMax = max;
            return this;
        }

        public Builder contact() {
            this.contact = true;
            return this;
        }

        public Builder son() {
            this.son = true;
            return this;
        }

        public Builder poing() {
            this.poing = true;
            return this;
        }

        public Builder morsure() {
            this.morsure = true;
            return this;
        }

        public Builder pulsation() {
            this.pulsation = true;
            return this;
        }

        public Builder tranchant() {
            this.tranchant = true;
            return this;
        }

        public Builder bombe() {
            this.bombe = true;
            return this;
        }

        public Builder rampant() {
            this.rampant = true;
            return this;
        }

        public Move build() {
            return new Move(this);
        }
    }
}
