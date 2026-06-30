package com.tropimon.tropicalc.calc;

/**
 * Les 25 natures officielles (noms français vérifiés sur Poképédia).
 * Chaque nature augmente une stat de 10% et en diminue une autre de 10%,
 * sauf les 5 natures neutres (Hardi, Docile, Sérieux, Pudique, Bizarre)
 * qui n'ont aucun effet.
 */
public enum Nature {
    HARDI(null, null),
    SOLO(Stat.ATTAQUE, Stat.DEFENSE),
    BRAVE(Stat.ATTAQUE, Stat.VITESSE),
    RIGIDE(Stat.ATTAQUE, Stat.ATTAQUE_SPE),
    MAUVAIS(Stat.ATTAQUE, Stat.DEFENSE_SPE),
    ASSURE(Stat.DEFENSE, Stat.ATTAQUE),
    DOCILE(null, null),
    RELAX(Stat.DEFENSE, Stat.VITESSE),
    MALIN(Stat.DEFENSE, Stat.ATTAQUE_SPE),
    LACHE(Stat.DEFENSE, Stat.DEFENSE_SPE),
    TIMIDE(Stat.VITESSE, Stat.ATTAQUE),
    PRESSE(Stat.VITESSE, Stat.DEFENSE),
    SERIEUX(null, null),
    JOVIAL(Stat.VITESSE, Stat.ATTAQUE_SPE),
    NAIF(Stat.VITESSE, Stat.DEFENSE_SPE),
    MODESTE(Stat.ATTAQUE_SPE, Stat.ATTAQUE),
    DOUX(Stat.ATTAQUE_SPE, Stat.DEFENSE),
    PUDIQUE(null, null),
    FOUFOU(Stat.ATTAQUE_SPE, Stat.DEFENSE_SPE),
    DISCRET(Stat.ATTAQUE_SPE, Stat.VITESSE),
    CALME(Stat.DEFENSE_SPE, Stat.ATTAQUE),
    GENTIL(Stat.DEFENSE_SPE, Stat.DEFENSE),
    PRUDENT(Stat.DEFENSE_SPE, Stat.ATTAQUE_SPE),
    BIZARRE(null, null),
    MALPOLI(Stat.DEFENSE_SPE, Stat.VITESSE);

    private final Stat statAugmentee;
    private final Stat statDiminuee;

    Nature(Stat statAugmentee, Stat statDiminuee) {
        this.statAugmentee = statAugmentee;
        this.statDiminuee = statDiminuee;
    }

    public Stat getStatAugmentee() {
        return statAugmentee;
    }

    public Stat getStatDiminuee() {
        return statDiminuee;
    }

    public boolean estNeutre() {
        return statAugmentee == null;
    }

    /**
     * Renvoie le multiplicateur (0.9 / 1.0 / 1.1) appliqué par cette nature
     * sur une statistique donnée. Utilisé directement dans le calcul de stat.
     */
    public double multiplicateur(Stat stat) {
        if (stat == statAugmentee) {
            return 1.1;
        }
        if (stat == statDiminuee) {
            return 0.9;
        }
        return 1.0;
    }
}
