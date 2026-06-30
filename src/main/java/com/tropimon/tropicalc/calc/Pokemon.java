package com.tropimon.tropicalc.calc;

import java.util.EnumMap;
import java.util.Map;

/**
 * Représente un Pokémon tel qu'il existe en combat : ses stats calculées,
 * son état actuel (PV, statut, stages de boost), son objet et son talent.
 * Ne contient aucune logique de calcul de dégâts (c'est le rôle de
 * DamageCalculator) ; cette classe ne fait que stocker et exposer l'état.
 */
public class Pokemon {

    /** Statuts altérant un Pokémon. */
    public enum Statut {
        AUCUN,
        BRULURE,
        POISON,
        POISON_GRAVE,
        PARALYSIE,
        SOMMEIL,
        GEL
    }

    private final String espece;
    private final int niveau;
    private final PokemonType type1;
    private final PokemonType type2; // peut être null (mono-type)

    private final Map<Stat, Integer> statsBase = new EnumMap<>(Stat.class);
    private final Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
    private final Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
    private final Nature nature;

    private final String talent;
    private final String objet;

    private final PokemonType teraType; // null si pas de type Tera défini
    private boolean teracristallise = false;
    private boolean mega = false;

    private int pvActuels;
    private Statut statut = Statut.AUCUN;

    // Stages de boost (-6 à +6), PV exclu (les PV ne se boostent jamais)
    private final Map<Stat, Integer> stages = new EnumMap<>(Stat.class);

    private Pokemon(Builder b) {
        this.espece = b.espece;
        this.niveau = b.niveau;
        this.type1 = b.type1;
        this.type2 = b.type2;
        this.statsBase.putAll(b.statsBase);
        this.ivs.putAll(b.ivs);
        this.evs.putAll(b.evs);
        this.nature = b.nature;
        this.talent = b.talent;
        this.objet = b.objet;
        this.teraType = b.teraType;
        this.teracristallise = b.teracristallise;
        this.mega = b.mega;

        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                stages.put(s, 0);
            }
        }

        this.pvActuels = getStatCalculee(Stat.PV);
    }

    // --- Identité ---

    public String getEspece() {
        return espece;
    }

    public int getNiveau() {
        return niveau;
    }

    public PokemonType getType1() {
        return type1;
    }

    public PokemonType getType2() {
        return type2;
    }

    public boolean possedeType(PokemonType type) {
        return type1 == type || type2 == type;
    }

    /**
     * Type défenseur effectif. Si le Pokémon est Téracristallisé, son type
     * Tera REMPLACE entièrement ses types d'origine pour la défense.
     */
    public PokemonType getTypeDefenseurEffectif1() {
        if (teracristallise && teraType != null) {
            return teraType;
        }
        return type1;
    }

    public PokemonType getTypeDefenseurEffectif2() {
        if (teracristallise && teraType != null) {
            return null; // un Pokémon Tera n'a qu'un seul type défenseur
        }
        return type2;
    }

    // --- Stats ---

    public int getStatBase(Stat stat) {
        return statsBase.getOrDefault(stat, 0);
    }

    public int getIv(Stat stat) {
        return ivs.getOrDefault(stat, 31);
    }

    public int getEv(Stat stat) {
        return evs.getOrDefault(stat, 0);
    }

    public Nature getNature() {
        return nature;
    }

    /**
     * Calcule la statistique réelle (hors stages de combat) selon les
     * formules officielles.
     */
    public int getStatCalculee(Stat stat) {
        int base = getStatBase(stat);
        int iv = getIv(stat);
        int ev = getEv(stat);

        if (stat == Stat.PV) {
            if (espece != null && espece.equalsIgnoreCase("Ferpathie")) {
                // Cas particulier : Ferpathie a toujours 1 PV (Shedinja)
                return 1;
            }
            return ((2 * base + iv + ev / 4) * niveau) / 100 + niveau + 10;
        }

        int valeur = ((2 * base + iv + ev / 4) * niveau) / 100 + 5;
        double multiplicateurNature = nature != null ? nature.multiplicateur(stat) : 1.0;
        return (int) (valeur * multiplicateurNature);
    }

    /**
     * Renvoie la statistique avec le multiplicateur de stage de combat
     * appliqué (-6 à +6). N'inclut PAS les effets de statut (brûlure,
     * paralysie) ni les modificateurs d'objet/talent : ceux-ci sont gérés
     * par DamageCalculator.
     */
    public int getStatEnCombat(Stat stat) {
        if (stat == Stat.PV) {
            return pvActuels;
        }
        int statCalculee = getStatCalculee(stat);
        int stage = getStage(stat);
        double multiplicateur = stage >= 0
            ? (2.0 + stage) / 2.0
            : 2.0 / (2.0 - stage);
        return (int) (statCalculee * multiplicateur);
    }

    public int getStage(Stat stat) {
        return stages.getOrDefault(stat, 0);
    }

    public void setStage(Stat stat, int valeur) {
        if (stat == Stat.PV) {
            return;
        }
        stages.put(stat, Math.max(-6, Math.min(6, valeur)));
    }

    public void modifierStage(Stat stat, int delta) {
        setStage(stat, getStage(stat) + delta);
    }

    // --- État de combat ---

    public int getPvMax() {
        return getStatCalculee(Stat.PV);
    }

    public int getPvActuels() {
        return pvActuels;
    }

    public void setPvActuels(int valeur) {
        this.pvActuels = Math.max(0, Math.min(getPvMax(), valeur));
    }

    public double getPourcentagePv() {
        int max = getPvMax();
        return max == 0 ? 0 : (100.0 * pvActuels) / max;
    }

    public boolean estKO() {
        return pvActuels <= 0;
    }

    public Statut getStatut() {
        return statut;
    }

    public void setStatut(Statut statut) {
        this.statut = statut;
    }

    // --- Objet / Talent / Tera / Méga ---

    public String getTalent() {
        return talent;
    }

    public String getObjet() {
        return objet;
    }

    public PokemonType getTeraType() {
        return teraType;
    }

    public boolean isTeracristallise() {
        return teracristallise;
    }

    public void setTeracristallise(boolean valeur) {
        this.teracristallise = valeur;
    }

    public boolean isMega() {
        return mega;
    }

    public void setMega(boolean valeur) {
        this.mega = valeur;
    }

    // --- Construction ---

    public static Builder builder(String espece, int niveau, PokemonType type1, PokemonType type2) {
        return new Builder(espece, niveau, type1, type2);
    }

    public static class Builder {
        private final String espece;
        private final int niveau;
        private final PokemonType type1;
        private final PokemonType type2;

        private final Map<Stat, Integer> statsBase = new EnumMap<>(Stat.class);
        private final Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
        private final Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
        private Nature nature = Nature.HARDI;

        private String talent;
        private String objet;
        private PokemonType teraType;
        private boolean teracristallise = false;
        private boolean mega = false;

        private Builder(String espece, int niveau, PokemonType type1, PokemonType type2) {
            this.espece = espece;
            this.niveau = niveau;
            this.type1 = type1;
            this.type2 = type2;
            // Valeurs par défaut sûres : IV parfaits, 0 EV
            for (Stat s : Stat.values()) {
                ivs.put(s, 31);
                evs.put(s, 0);
            }
        }

        public Builder statBase(Stat stat, int valeur) {
            statsBase.put(stat, valeur);
            return this;
        }

        public Builder iv(Stat stat, int valeur) {
            ivs.put(stat, valeur);
            return this;
        }

        public Builder ev(Stat stat, int valeur) {
            evs.put(stat, valeur);
            return this;
        }

        public Builder nature(Nature nature) {
            this.nature = nature;
            return this;
        }

        public Builder talent(String talent) {
            this.talent = talent;
            return this;
        }

        public Builder objet(String objet) {
            this.objet = objet;
            return this;
        }

        public Builder teraType(PokemonType teraType) {
            this.teraType = teraType;
            return this;
        }

        public Builder teracristallise(boolean valeur) {
            this.teracristallise = valeur;
            return this;
        }

        public Builder mega(boolean valeur) {
            this.mega = valeur;
            return this;
        }

        public Pokemon build() {
            return new Pokemon(this);
        }
    }
}
