package com.tropimon.tropicalc.calc;

import java.util.Set;

public final class SetInferenceEngine {

    private SetInferenceEngine() {
    }

    public static final Set<String> OBJETS_OFFENSIFS = Set.of(
        "Bandeau Choix", "Lunettes Choix", "Écharpe Choix", "Orbe Vie", "Ceinture Pro",
        "Bandeau Muscles", "Lunettes Savantes", "Gant Boxe"
    );

    public static final Set<String> TALENTS_OFFENSIFS = Set.of(
        "Cran", "Adrénaline", "Adaptabilité", "Technicien", "Poing de Fer",
        "Mâchoire Brute", "Force Sable", "Verres Teintés", "Grand Chelem", "Télécharge"
    );

    public static final Set<String> OBJETS_DEFENSIFS = Set.of(
        "Veste de Combat", "Évoluroc", "Grosse Bottes", "Restes", "Boue Noire",
        "Baie Sitrus", "Baie Agava", "Baie Iapapa", "Baie Wiki", "Baie Mago", "Casque Clou"
    );

    public static final Set<String> TALENTS_DEFENSIFS = Set.of(
        "Isograisse", "Filtre", "Solide Roc", "Multi-écailles", "Spectro-Bouclier",
        "Régé-Force", "Médic Nature"
    );

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

                        if (resultat.immunise) continue;

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

        if (!auMoinsUneCombinaisonValide) return;

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

        if (!StatHypothesis.AUCUN.equals(objet)) b.objet(objet);
        if (!StatHypothesis.AUCUN.equals(talent)) b.talent(talent);

        Nature natureChoisie = trouverNaturePour(statCible, natureBoost);
        b.nature(natureChoisie);

        Pokemon p = b.build();
        p.setStatut(base.getStatut());
        p.setPvActuels(base.getPvActuels());
        for (Stat s : Stat.values()) {
            if (s != Stat.PV) p.setStage(s, base.getStage(s));
        }
        return p;
    }

    private static Nature trouverNaturePour(Stat statCible, NatureBoost boost) {
        if (boost == NatureBoost.NEUTRE) return Nature.HARDI;
        for (Nature n : Nature.values()) {
            if (boost == NatureBoost.BOOSTEE && n.getStatAugmentee() == statCible) return n;
            if (boost == NatureBoost.BAISSEE && n.getStatDiminuee() == statCible) return n;
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
