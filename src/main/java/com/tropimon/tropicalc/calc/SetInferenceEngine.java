package com.tropimon.tropicalc.calc;

import java.util.Set;

/**
 * Moteur d'inférence de sets adverses. Principe : pour UNE observation de
 * dégâts (un coup donné, contre une cible aux stats connues, ayant infligé
 * X% à Y% de PV), on teste exhaustivement les combinaisons plausibles
 * d'EV (0 à 252, pas de 4), de nature (boostée/neutre/baissée sur cette
 * stat précise) et d'objet/talent candidats, et on ne garde que celles qui
 * reproduisent une plage de dégâts chevauchant l'observation réelle.
 *
 * Une seule statistique est resserrée par observation (celle utilisée dans
 * le calcul : Attaque ou Att. Spé. si l'adversaire attaque, Défense ou
 * Déf. Spé. s'il défend). Les hypothèses sur Vitesse et PV ne sont pas
 * gérées ici (elles nécessitent d'autres types d'observations : ordre des
 * tours, etc.).
 */
public final class SetInferenceEngine {

    private SetInferenceEngine() {
    }

    /** Objets pertinents quand on infère une stat OFFENSIVE (Attaque ou Att. Spé.). */
    public static final Set<String> OBJETS_OFFENSIFS = Set.of(
        "Bandeau Choix", "Lunettes Choix", "Orbe Vie", "Ceinture Pro"
    );

    /** Talents pertinents quand on infère une stat OFFENSIVE. */
    public static final Set<String> TALENTS_OFFENSIFS = Set.of(
        "Cran", "Adrénaline", "Adaptabilité", "Technicien", "Poing de Fer",
        "Mâchoire Brute", "Force Sable", "Verres Teintés"
    );

    /** Objets pertinents quand on infère une stat DÉFENSIVE (Défense ou Déf. Spé.). */
    public static final Set<String> OBJETS_DEFENSIFS = Set.of(
        "Veste de Combat", "Évoluroc"
    );

    /** Talents pertinents quand on infère une stat DÉFENSIVE. */
    public static final Set<String> TALENTS_DEFENSIFS = Set.of(
        "Isograisse", "Filtre", "Solide Roc", "Multi-écailles", "Spectro-Bouclier"
    );

    /**
     * Resserre une hypothèse existante à partir d'une nouvelle observation
     * de dégâts.
     *
     * @param hypothese        l'hypothèse courante sur la stat à resserrer (modifiée sur place)
     * @param statCible         la stat dont on cherche à cerner la valeur (ATTAQUE, DEFENSE, ATTAQUE_SPE ou DEFENSE_SPE)
     * @param estStatAttaquant  true si statCible appartient à l'attaquant (on infère l'attaquant),
     *                          false si elle appartient au défenseur (on infère le défenseur)
     * @param pokemonInconnuPartiel le Pokémon adverse dont on connaît déjà l'espèce/niveau/types,
     *                              mais dont la statCible est encore incertaine
     * @param pokemonConnu      l'autre Pokémon du duel, entièrement connu (le nôtre)
     * @param capacite          la capacité utilisée lors de cette observation
     * @param terrain           l'état du terrain au moment de l'observation
     * @param pourcentageObserveMin borne basse du %PV perdu observé (ex: lecture de la barre de vie)
     * @param pourcentageObserveMax borne haute du %PV perdu observé
     */
    public static void narrow(StatHypothesis hypothese, Stat statCible, boolean estStatAttaquant,
                               Pokemon pokemonInconnuPartiel, Pokemon pokemonConnu, Move capacite,
                               Field terrain, double pourcentageObserveMin, double pourcentageObserveMax) {

        Set<String> objetsCandidats = hypothese.objetsPossibles;
        Set<String> talentsCandidats = hypothese.talentsPossibles;

        int nouveauEvMin = 252;
        int nouveauEvMax = 0;
        boolean nouvBoostee = false;
        boolean nouvNeutre = false;
        boolean nouvBaissee = false;
        java.util.Set<String> nouveauxObjets = new java.util.HashSet<>();
        java.util.Set<String> nouveauxTalents = new java.util.HashSet<>();
        boolean auMoinsUneCombinaisonValide = false;

        for (int ev = arrondirAuPlusProche4(hypothese.evMin); ev <= hypothese.evMax; ev += 4) {
            for (NatureBoost natureBoost : NatureBoost.values()) {
                if (natureBoost == NatureBoost.BOOSTEE && !hypothese.peutEtreBoostee) continue;
                if (natureBoost == NatureBoost.NEUTRE && !hypothese.peutEtreNeutre) continue;
                if (natureBoost == NatureBoost.BAISSEE && !hypothese.peutEtreBaissee) continue;

                for (String objet : avecAucun(objetsCandidats)) {
                    for (String talent : avecAucun(talentsCandidats)) {

                        Pokemon hypothetique = construirePokemonHypothetique(
                            pokemonInconnuPartiel, statCible, ev, natureBoost, objet, talent);

                        Pokemon attaquant = estStatAttaquant ? hypothetique : pokemonConnu;
                        Pokemon defenseur = estStatAttaquant ? pokemonConnu : hypothetique;

                        DamageCalculator.Resultat resultat =
                            DamageCalculator.calculer(attaquant, defenseur, capacite, terrain, null, false);

                        if (resultat.immunise) {
                            continue;
                        }

                        boolean chevauche = resultat.pourcentageMin <= pourcentageObserveMax
                            && resultat.pourcentageMax >= pourcentageObserveMin;

                        if (chevauche) {
                            auMoinsUneCombinaisonValide = true;
                            nouveauEvMin = Math.min(nouveauEvMin, ev);
                            nouveauEvMax = Math.max(nouveauEvMax, ev);
                            if (natureBoost == NatureBoost.BOOSTEE) nouvBoostee = true;
                            if (natureBoost == NatureBoost.NEUTRE) nouvNeutre = true;
                            if (natureBoost == NatureBoost.BAISSEE) nouvBaissee = true;
                            nouveauxObjets.add(objet);
                            nouveauxTalents.add(talent);
                        }
                    }
                }
            }
        }

        if (!auMoinsUneCombinaisonValide) {
            // Observation incompatible avec l'hypothèse actuelle (donnée
            // aberrante, coup critique non détecté, etc.) : on ne resserre
            // pas pour éviter de corrompre l'hypothèse avec une fausse piste.
            return;
        }

        hypothese.evMin = nouveauEvMin;
        hypothese.evMax = nouveauEvMax;
        hypothese.peutEtreBoostee = nouvBoostee;
        hypothese.peutEtreNeutre = nouvNeutre;
        hypothese.peutEtreBaissee = nouvBaissee;
        hypothese.objetsPossibles.retainAll(nouveauxObjets);
        hypothese.talentsPossibles.retainAll(nouveauxTalents);
        hypothese.nombreObservations++;
    }

    private enum NatureBoost { BOOSTEE, NEUTRE, BAISSEE }

    /**
     * Construit un Pokémon temporaire identique à pokemonInconnuPartiel,
     * sauf sur la statCible où l'on impose les EV/nature/objet/talent testés.
     * Les autres stats restent à leurs valeurs par défaut (31 IV, 0 EV,
     * nature neutre) puisqu'elles n'interviennent pas dans ce calcul précis.
     */
    private static Pokemon construirePokemonHypothetique(Pokemon base, Stat statCible, int ev,
                                                           NatureBoost natureBoost, String objet, String talent) {
        Pokemon.Builder b = Pokemon.builder(base.getEspece(), base.getNiveau(), base.getType1(), base.getType2())
            .statBase(Stat.PV, base.getStatBase(Stat.PV))
            .statBase(Stat.ATTAQUE, base.getStatBase(Stat.ATTAQUE))
            .statBase(Stat.DEFENSE, base.getStatBase(Stat.DEFENSE))
            .statBase(Stat.ATTAQUE_SPE, base.getStatBase(Stat.ATTAQUE_SPE))
            .statBase(Stat.DEFENSE_SPE, base.getStatBase(Stat.DEFENSE_SPE))
            .statBase(Stat.VITESSE, base.getStatBase(Stat.VITESSE))
            .ev(statCible, ev)
            .teraType(base.getTeraType())
            .teracristallise(base.isTeracristallise());

        if (!StatHypothesis.AUCUN.equals(objet)) {
            b.objet(objet);
        }
        if (!StatHypothesis.AUCUN.equals(talent)) {
            b.talent(talent);
        }

        // On encode le boost de nature directement via une "fausse" nature
        // ciblée : on cherche dans l'enum Nature une nature dont l'effet sur
        // statCible correspond au NatureBoost demandé, peu importe l'autre stat affectée.
        Nature natureChoisie = trouverNaturePour(statCible, natureBoost);
        b.nature(natureChoisie);

        Pokemon p = b.build();
        p.setStatut(base.getStatut());
        p.setPvActuels(base.getPvActuels());
        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                p.setStage(s, base.getStage(s));
            }
        }
        return p;
    }

    private static Nature trouverNaturePour(Stat statCible, NatureBoost boost) {
        if (boost == NatureBoost.NEUTRE) {
            return Nature.HARDI;
        }
        for (Nature n : Nature.values()) {
            if (boost == NatureBoost.BOOSTEE && n.getStatAugmentee() == statCible) {
                return n;
            }
            if (boost == NatureBoost.BAISSEE && n.getStatDiminuee() == statCible) {
                return n;
            }
        }
        return Nature.HARDI;
    }

    private static java.util.Set<String> avecAucun(Set<String> base) {
        java.util.Set<String> resultat = new java.util.HashSet<>(base);
        resultat.add(StatHypothesis.AUCUN);
        return resultat;
    }

    private static int arrondirAuPlusProche4(int valeur) {
        return (valeur / 4) * 4;
    }
}
