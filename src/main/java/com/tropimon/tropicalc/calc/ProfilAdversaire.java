package com.tropimon.tropicalc.calc;

import java.util.ArrayList;
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
            talentsReelsEspece != null ? talentsReelsEspece : SetInferenceEngine.TALENTS_DEFENSIFS);

        // Objets défensifs candidats : mêmes objets top Smogon que côté
        // offensif (Smogon ne sépare pas offensif/défensif, un objet peut
        // apparaître dans les deux rôles), croisés avec la liste des objets
        // effectivement défensifs. Sans ça, on testait TOUS les objets
        // défensifs génériques (Ceinture Focus, toutes les baies...) même
        // quand Smogon montre que 0% des joueurs les utilisent sur cette
        // espèce précise - élargissant inutilement l'espace d'hypothèses.
        Set<String> objetsDefSmogon = intersection(objetsSmogon, SetInferenceEngine.OBJETS_DEFENSIFS);
        if (objetsDefSmogon.isEmpty()) objetsDefSmogon = SetInferenceEngine.OBJETS_DEFENSIFS;

        this.attaque = construireHypothese(plages, Stat.ATTAQUE, objetsSmogon, talentsSmogon);
        this.attaqueSpe = construireHypothese(plages, Stat.ATTAQUE_SPE, objetsSmogon, talentsSmogon);
        this.defense = construireHypothese(plages, Stat.DEFENSE,
            objetsDefSmogon, talentsDefSmogon);
        this.defenseSpe = construireHypothese(plages, Stat.DEFENSE_SPE,
            objetsDefSmogon, talentsDefSmogon);
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

    /**
     * Ignore les spreads trop marginaux (moins de 15% du poids du spread le
     * plus populaire) avant de calculer la plage min/max par stat. Sans ce
     * filtre, un set minoritaire à 2-3% d'usage élargit la plage jusqu'à 0
     * EV même quand 80%+ des joueurs investissent le maximum sur cette
     * stat — donnant une impression trompeuse d'incertitude totale.
     */
    private static Map<Stat, int[]> calculerPlagesEV(List<SmogonDataLoader.ParsedSpread> spreads) {
        double poidsMax = 0;
        for (SmogonDataLoader.ParsedSpread s : spreads) {
            poidsMax = Math.max(poidsMax, s.poids());
        }
        double seuil = poidsMax * 0.15;
        List<SmogonDataLoader.ParsedSpread> spreadsRetenus = new ArrayList<>();
        for (SmogonDataLoader.ParsedSpread s : spreads) {
            if (s.poids() >= seuil) spreadsRetenus.add(s);
        }
        if (spreadsRetenus.isEmpty()) spreadsRetenus = spreads;   // filet de sécurité

        Map<Stat, int[]> resultat = new HashMap<>();
        for (Stat s : new Stat[]{Stat.PV, Stat.ATTAQUE, Stat.DEFENSE,
                                  Stat.ATTAQUE_SPE, Stat.DEFENSE_SPE, Stat.VITESSE}) {
            int min = 252, max = 0;
            for (SmogonDataLoader.ParsedSpread spread : spreadsRetenus) {
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

    /**
     * Parmi les N spreads Smogon les plus populaires (déjà triés par
     * poids), retourne le plus probable qui reste ENCORE cohérent avec
     * les dégâts observés en combat - au lieu d'afficher systématiquement
     * le spread #1 même après une observation qui le contredit clairement.
     *
     * Réutilise entièrement le narrowing déjà en place (StatHypothesis
     * resserré par SetInferenceEngine.narrow) : un spread est "cohérent"
     * si ses 4 EV offensifs/défensifs tombent tous dans les plages déjà
     * resserrées par l'observation, ET si sa nature est compatible avec
     * les stats que le narrowing a identifiées comme pouvant être
     * boostées/neutres/baissées. Aucun nouveau mécanisme d'observation :
     * juste une lecture, après coup, de ce que le narrowing sait déjà.
     *
     * Retourne null si aucun des N spreads testés ne reste cohérent
     * (l'appelant doit alors se rabattre sur un affichage générique).
     */
    public SmogonDataLoader.ParsedSpread spreadPlusProbable(List<SmogonDataLoader.ParsedSpread> topSpreads, int n, Pokemon reference) {
        int limite = Math.min(n, topSpreads.size());
        for (int i = 0; i < limite; i++) {
            SmogonDataLoader.ParsedSpread s = topSpreads.get(i);
            if (spreadEstCoherent(s, reference)) return s;
        }
        return null;
    }

    private boolean spreadEstCoherent(SmogonDataLoader.ParsedSpread s, Pokemon reference) {
        if (!evDansPlage(attaque, s.atkEv())) return false;
        if (!evDansPlage(attaqueSpe, s.spaEv())) return false;
        if (!evDansPlage(defense, s.defEv())) return false;
        if (!evDansPlage(defenseSpe, s.spdEv())) return false;

        Nature nature = ShowdownIdMapper.nature(s.natureShowdownId());
        if (!natureCoherente(attaque, Stat.ATTAQUE, nature)) return false;
        if (!natureCoherente(attaqueSpe, Stat.ATTAQUE_SPE, nature)) return false;
        if (!natureCoherente(defense, Stat.DEFENSE, nature)) return false;
        if (!natureCoherente(defenseSpe, Stat.DEFENSE_SPE, nature)) return false;

        // Vitesse : cross-check contre l'ordre d'action déjà observé en
        // combat (signal déjà collecté ailleurs, jamais exploité ici avant).
        // Si ce spread impliquerait une vitesse INFÉRIEURE au minimum
        // garanti par l'observation (l'adversaire a agi avant nous sans
        // priorité, donc sa vitesse réelle est au moins celle-ci), le
        // spread est provablement faux - il ne devine pas juste un peu
        // moins bien, il est mathématiquement incompatible avec un fait
        // déjà établi avec certitude.
        if (reference != null) {
            int vitesseMinConnue = com.tropimon.tropicalc.battle.ObservationCollector
                .getVitesseMinObservee(reference.getEspece());
            if (vitesseMinConnue > 0) {
                Pokemon hypothese = Pokemon.builder(reference.getEspece(), reference.getNiveau(),
                        reference.getType1(), reference.getType2())
                    .statBase(Stat.VITESSE, reference.getStatBase(Stat.VITESSE))
                    .iv(Stat.VITESSE, 31)
                    .ev(Stat.VITESSE, s.speEv())
                    .nature(nature)
                    .build();
                if (hypothese.getStatCalculee(Stat.VITESSE) < vitesseMinConnue) return false;
            }
        }

        return true;
    }

    private static boolean evDansPlage(StatHypothesis h, int ev) {
        return ev >= h.evMin && ev <= h.evMax;
    }

    private static boolean natureCoherente(StatHypothesis h, Stat stat, Nature nature) {
        if (nature.getStatAugmentee() == stat) return h.peutEtreBoostee;
        if (nature.getStatDiminuee() == stat) return h.peutEtreBaissee;
        return h.peutEtreNeutre;
    }
}
