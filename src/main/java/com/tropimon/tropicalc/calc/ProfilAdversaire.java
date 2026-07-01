package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfilAdversaire {

    public final StatHypothesis attaque;
    public final StatHypothesis attaqueSpe;
    public final StatHypothesis defense;
    public final StatHypothesis defenseSpe;
    private int nbObservations = 0;

    public int getNbObservations() { return nbObservations; }

    public ProfilAdversaire(Set<String> talentsReelsEspece) {
        Set<String> talentsOff = intersection(SetInferenceEngine.TALENTS_OFFENSIFS, talentsReelsEspece);
        Set<String> talentsDef = intersection(SetInferenceEngine.TALENTS_DEFENSIFS, talentsReelsEspece);
        this.attaque = new StatHypothesis(SetInferenceEngine.OBJETS_OFFENSIFS, talentsOff);
        this.attaqueSpe = new StatHypothesis(SetInferenceEngine.OBJETS_OFFENSIFS, talentsOff);
        this.defense = new StatHypothesis(SetInferenceEngine.OBJETS_DEFENSIFS, talentsDef);
        this.defenseSpe = new StatHypothesis(SetInferenceEngine.OBJETS_DEFENSIFS, talentsDef);
    }

    public ProfilAdversaire(Set<String> talentsReelsEspece, SmogonDataLoader.SmogonPokemonData smogon) {
        if (smogon == null || smogon.topSpreads().isEmpty()) {
            Set<String> talentsOff = intersection(SetInferenceEngine.TALENTS_OFFENSIFS, talentsReelsEspece);
            Set<String> talentsDef = intersection(SetInferenceEngine.TALENTS_DEFENSIFS, talentsReelsEspece);
            this.attaque = new StatHypothesis(SetInferenceEngine.OBJETS_OFFENSIFS, talentsOff);
            this.attaqueSpe = new StatHypothesis(SetInferenceEngine.OBJETS_OFFENSIFS, talentsOff);
            this.defense = new StatHypothesis(SetInferenceEngine.OBJETS_DEFENSIFS, talentsDef);
            this.defenseSpe = new StatHypothesis(SetInferenceEngine.OBJETS_DEFENSIFS, talentsDef);
            return;
        }

        Map<Stat, int[]> plages = calculerPlagesEV(smogon.topSpreads());

        Set<String> objetsSmogon = new HashSet<>();
        for (String itemId : smogon.topItemsShowdownId()) {
            String fr = ShowdownIdMapper.objet(itemId);
            if (fr != null) objetsSmogon.add(fr);
        }
        if (objetsSmogon.isEmpty()) objetsSmogon.addAll(SetInferenceEngine.OBJETS_OFFENSIFS);

        Set<String> talentsSmogon = new HashSet<>();
        for (String abilityId : smogon.topAbilitiesShowdownId()) {
            String fr = ShowdownIdMapper.talent(abilityId);
            if (fr != null && (talentsReelsEspece == null || talentsReelsEspece.contains(fr))) {
                talentsSmogon.add(fr);
            }
        }
        if (talentsSmogon.isEmpty() && talentsReelsEspece != null) {
            talentsSmogon.addAll(intersection(SetInferenceEngine.TALENTS_OFFENSIFS, talentsReelsEspece));
        }

        Set<String> talentsDefSmogon = intersection(SetInferenceEngine.TALENTS_DEFENSIFS,
            talentsSmogon.isEmpty() ? SetInferenceEngine.TALENTS_OFFENSIFS : talentsSmogon);

        this.attaque = construireHypothese(plages, Stat.ATTAQUE, objetsSmogon, talentsSmogon);
        this.attaqueSpe = construireHypothese(plages, Stat.ATTAQUE_SPE, objetsSmogon, talentsSmogon);
        this.defense = construireHypothese(plages, Stat.DEFENSE,
            SetInferenceEngine.OBJETS_DEFENSIFS, talentsDefSmogon);
        this.defenseSpe = construireHypothese(plages, Stat.DEFENSE_SPE,
            SetInferenceEngine.OBJETS_DEFENSIFS, talentsDefSmogon);
    }

    private static StatHypothesis construireHypothese(Map<Stat, int[]> plages, Stat stat,
                                                       Set<String> objets, Set<String> talents) {
        StatHypothesis h = new StatHypothesis(objets, talents);
        int[] plage = plages.get(stat);
        if (plage != null) {
            h.evMin = plage[0];
            h.evMax = plage[1];
        }
        return h;
    }

    private static Map<Stat, int[]> calculerPlagesEV(List<SmogonDataLoader.ParsedSpread> spreads) {
        Map<Stat, int[]> resultat = new HashMap<>();
        for (Stat s : new Stat[]{Stat.PV, Stat.ATTAQUE, Stat.DEFENSE,
                                  Stat.ATTAQUE_SPE, Stat.DEFENSE_SPE, Stat.VITESSE}) {
            int min = 252, max = 0;
            for (SmogonDataLoader.ParsedSpread spread : spreads) {
                int ev = getEvFromSpread(spread, s);
                min = Math.min(min, ev);
                max = Math.max(max, ev);
            }
            resultat.put(s, new int[]{Math.max(0, min - 4), Math.min(252, max + 4)});
        }
        return resultat;
    }

    private static int getEvFromSpread(SmogonDataLoader.ParsedSpread s, Stat stat) {
        return switch (stat) {
            case PV -> s.hpEv();
            case ATTAQUE -> s.atkEv();
            case DEFENSE -> s.defEv();
            case ATTAQUE_SPE -> s.spaEv();
            case DEFENSE_SPE -> s.spdEv();
            case VITESSE -> s.speEv();
        };
    }

    public StatHypothesis pour(Stat stat) {
        return switch (stat) {
            case ATTAQUE -> attaque;
            case ATTAQUE_SPE -> attaqueSpe;
            case DEFENSE -> defense;
            case DEFENSE_SPE -> defenseSpe;
            default -> null;
        };
    }

    public void enregistrerObservation(boolean adversaireEtaitAttaquant, Pokemon adversairePartiel,
                                        Pokemon nous, Move capacite, Field terrain,
                                        double pourcentageObserveMin, double pourcentageObserveMax) {
        if (capacite == null || capacite.estCapaciteDeStatut()) return;

        Stat statCible = capacite.getCategorie() == Move.Categorie.PHYSIQUE
            ? (adversaireEtaitAttaquant ? Stat.ATTAQUE : Stat.DEFENSE)
            : (adversaireEtaitAttaquant ? Stat.ATTAQUE_SPE : Stat.DEFENSE_SPE);

        StatHypothesis hypothese = pour(statCible);
        if (hypothese == null) return;
        SetInferenceEngine.narrow(hypothese, statCible, adversaireEtaitAttaquant, adversairePartiel, nous,
            capacite, terrain, pourcentageObserveMin, pourcentageObserveMax);
        nbObservations++;
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        if (b == null) return new HashSet<>(a);
        Set<String> r = new HashSet<>(a);
        r.retainAll(b);
        return r;
    }
}
