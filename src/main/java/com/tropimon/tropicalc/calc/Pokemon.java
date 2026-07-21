package com.tropimon.tropicalc.calc;

import java.util.EnumMap;
import java.util.Map;

public class Pokemon {

    public enum Statut {
        AUCUN, BRULURE, POISON, POISON_GRAVE, PARALYSIE, SOMMEIL, GEL
    }

    private final String espece;
    private final int niveau;
    private final PokemonType type1;
    private final PokemonType type2;

    // Types modifiés en combat (Détrempage, Protéen, Libéro, Halloween...)
    private PokemonType typeOverride1 = null;
    private PokemonType typeOverride2 = null;
    private boolean typesModifies = false;

    private final Map<Stat, Integer> statsBase = new EnumMap<>(Stat.class);
    private final Map<Stat, Integer> ivs = new EnumMap<>(Stat.class);
    private final Map<Stat, Integer> evs = new EnumMap<>(Stat.class);
    private final Nature nature;

    private final String talent;
    private final String objet;

    // Poids en hectogrammes (données species Cobblemon). 0 = inconnu.
    private final double poidsHg;

    private final PokemonType teraType;
    private boolean teracristallise;
    private boolean mega;

    private int pvActuels;
    private Statut statut = Statut.AUCUN;

    private final Map<Stat, Integer> stages = new EnumMap<>(Stat.class);

    /** Corrections mesurées sur les dégâts réels (1.0 = aucune). */
    private final Map<Stat, Double> correctionsObservees = new EnumMap<>(Stat.class);

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
        this.poidsHg = b.poidsHg;
        this.correctionsObservees.putAll(b.correctionsObservees);
        this.teraType = b.teraType;
        this.teracristallise = b.teracristallise;
        this.mega = b.mega;

        for (Stat s : Stat.values()) {
            if (s != Stat.PV) stages.put(s, 0);
        }
        this.pvActuels = getStatCalculee(Stat.PV);
    }

    public String getEspece() { return espece; }
    public int getNiveau() { return niveau; }

    public PokemonType getType1() { return typesModifies ? typeOverride1 : type1; }
    public PokemonType getType2() { return typesModifies ? typeOverride2 : type2; }

    /** Remplace les types en combat (Détrempage, Protéen, Libéro). */
    public void setTypesOverride(PokemonType t1, PokemonType t2) {
        this.typeOverride1 = t1;
        this.typeOverride2 = t2;
        this.typesModifies = true;
    }

    public boolean isTypesModifies() { return typesModifies; }

    public boolean possedeType(PokemonType type) {
        return getType1() == type || getType2() == type;
    }

    public PokemonType getTypeDefenseurEffectif1() {
        if (teracristallise && teraType != null) return teraType;
        return getType1();
    }

    public PokemonType getTypeDefenseurEffectif2() {
        if (teracristallise && teraType != null) return null;
        return getType2();
    }

    public int getStatBase(Stat stat) { return statsBase.getOrDefault(stat, 0); }
    public int getIv(Stat stat) { return ivs.getOrDefault(stat, 31); }
    public int getEv(Stat stat) { return evs.getOrDefault(stat, 0); }
    public Nature getNature() { return nature; }

    public int getStatCalculee(Stat stat) {
        int base = getStatBase(stat);
        int iv = getIv(stat);
        int ev = getEv(stat);

        if (stat == Stat.PV) {
            if (espece != null && espece.equalsIgnoreCase("Ferpathie")) return 1;
            return ((2 * base + iv + ev / 4) * niveau) / 100 + niveau + 10;
        }

        int valeur = ((2 * base + iv + ev / 4) * niveau) / 100 + 5;
        double multiplicateurNature = nature != null ? nature.multiplicateur(stat) : 1.0;
        Double correction = correctionsObservees.get(stat);
        double multCorrection = correction == null ? 1.0 : correction;
        return (int) (valeur * multiplicateurNature * multCorrection);
    }

    /** Vrai si cette stat a été recalibrée d'après les dégâts réels observés. */
    public boolean estCorrigee(Stat stat) {
        return correctionsObservees.containsKey(stat);
    }

    public int getStatEnCombat(Stat stat) {
        if (stat == Stat.PV) return pvActuels;
        int statCalculee = getStatCalculee(stat);
        int stage = getStage(stat);
        double multiplicateur = stage >= 0 ? (2.0 + stage) / 2.0 : 2.0 / (2.0 - stage);
        return (int) (statCalculee * multiplicateur);
    }

    public int getStage(Stat stat) { return stages.getOrDefault(stat, 0); }

    public void setStage(Stat stat, int valeur) {
        if (stat == Stat.PV) return;
        stages.put(stat, Math.max(-6, Math.min(6, valeur)));
    }

    public void modifierStage(Stat stat, int delta) { setStage(stat, getStage(stat) + delta); }

    public int getPvMax() { return getStatCalculee(Stat.PV); }
    public int getPvActuels() { return pvActuels; }

    public void setPvActuels(int valeur) {
        this.pvActuels = Math.max(0, Math.min(getPvMax(), valeur));
    }

    public double getPourcentagePv() {
        int max = getPvMax();
        return max == 0 ? 0 : (100.0 * pvActuels) / max;
    }

    public boolean estKO() { return pvActuels <= 0; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public String getTalent() { return talent; }
    public String getObjet() { return objet; }

    /** Poids en hectogrammes (0 = inconnu). */
    public double getPoidsHg() { return poidsHg; }
    public PokemonType getTeraType() { return teraType; }
    public boolean isTeracristallise() { return teracristallise; }
    public void setTeracristallise(boolean v) { this.teracristallise = v; }
    public boolean isMega() { return mega; }
    public void setMega(boolean v) { this.mega = v; }

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
        private double poidsHg = 0;
        private PokemonType teraType;
        private boolean teracristallise = false;
        private boolean mega = false;

        private Builder(String espece, int niveau, PokemonType type1, PokemonType type2) {
            this.espece = espece;
            this.niveau = niveau;
            this.type1 = type1;
            this.type2 = type2;
            for (Stat s : Stat.values()) {
                ivs.put(s, 31);
                evs.put(s, 0);
            }
        }

        public Builder statBase(Stat stat, int v) { statsBase.put(stat, v); return this; }
        public Builder iv(Stat stat, int v) { ivs.put(stat, v); return this; }
        public Builder ev(Stat stat, int v) { evs.put(stat, v); return this; }
        private final Map<Stat, Double> correctionsObservees = new EnumMap<>(Stat.class);
        public Builder multiplicateurStat(Stat stat, double mult) {
            correctionsObservees.put(stat, mult);
            return this;
        }
        public Builder nature(Nature n) { this.nature = n; return this; }
        public Builder talent(String t) { this.talent = t; return this; }
        public Builder objet(String o) { this.objet = o; return this; }
        public Builder poids(double hg) { this.poidsHg = hg; return this; }
        public Builder teraType(PokemonType t) { this.teraType = t; return this; }
        public Builder teracristallise(boolean v) { this.teracristallise = v; return this; }
        public Builder mega(boolean v) { this.mega = v; return this; }

        public Pokemon build() { return new Pokemon(this); }
    }
}
