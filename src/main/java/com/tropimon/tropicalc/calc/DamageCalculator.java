package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

public class DamageCalculator {

    // Capacités multi-coups : {min hits, max hits} — puissance = puissance PAR coup
    private static final Map<String, int[]> MULTI_HIT = new HashMap<>();
    // Capacités à puissance croissante par coup : puissance totale fixe
    private static final Map<String, Integer> PUISSANCE_TOTALE = new HashMap<>();

    static {
        // 2 coups fixes
        MULTI_HIT.put("doublehit", new int[]{2, 2});
        MULTI_HIT.put("dualwingbeat", new int[]{2, 2});      // Double Volée
        MULTI_HIT.put("twineedle", new int[]{2, 2});
        MULTI_HIT.put("doublekick", new int[]{2, 2});
        MULTI_HIT.put("bonemerang", new int[]{2, 2});
        MULTI_HIT.put("geargrind", new int[]{2, 2});
        MULTI_HIT.put("dragondarts", new int[]{2, 2});
        MULTI_HIT.put("tachyoncutter", new int[]{2, 2});
        MULTI_HIT.put("doubleironbash", new int[]{2, 2});
        MULTI_HIT.put("dualchop", new int[]{2, 2});

        // 2-5 coups
        MULTI_HIT.put("scaleshot", new int[]{2, 5});          // Rafale Écaille
        MULTI_HIT.put("bulletseed", new int[]{2, 5});
        MULTI_HIT.put("rockblast", new int[]{2, 5});
        MULTI_HIT.put("iciclespear", new int[]{2, 5});
        MULTI_HIT.put("pinmissile", new int[]{2, 5});
        MULTI_HIT.put("tailslap", new int[]{2, 5});
        MULTI_HIT.put("armthrust", new int[]{2, 5});
        MULTI_HIT.put("furyattack", new int[]{2, 5});
        MULTI_HIT.put("furyswipes", new int[]{2, 5});
        MULTI_HIT.put("spikecannon", new int[]{2, 5});
        MULTI_HIT.put("barrage", new int[]{2, 5});
        MULTI_HIT.put("cometpunch", new int[]{2, 5});
        MULTI_HIT.put("doubleslap", new int[]{2, 5});
        MULTI_HIT.put("bonerush", new int[]{2, 5});
        MULTI_HIT.put("watershuriken", new int[]{2, 5});      // Sheauriken
        MULTI_HIT.put("rocksmash", new int[]{1, 1});

        // 3 coups fixes
        MULTI_HIT.put("surgingstrikes", new int[]{3, 3});     // Torrent de Coups (toujours crit)
        MULTI_HIT.put("tripledive", new int[]{3, 3});
        MULTI_HIT.put("waterspout", new int[]{1, 1});

        // 6-10 coups
        MULTI_HIT.put("populationbomb", new int[]{6, 10});    // Prolifération

        // 10 coups fixes
        MULTI_HIT.put("beatup", new int[]{1, 6});

        // Puissance croissante par coup (total si tous les coups touchent)
        PUISSANCE_TOTALE.put("tripleaxel", 120);   // Triple Axel : 20+40+60
        PUISSANCE_TOTALE.put("triplekick", 60);    // Triple Pied : 10+20+30
    }

    public static class Resultat {
        public final int[] degatsParRoll;
        public final int degatsMin;
        public final int degatsMax;
        public final double pourcentageMin;
        public final double pourcentageMax;
        public final boolean immunise;
        public final boolean koGaranti;
        public final boolean koPossible;
        public final double efficaciteType;

        private Resultat(int[] degatsParRoll, double pvMaxDefenseur, int pvActuelsDefenseur,
                          boolean immunise, double efficaciteType) {
            this.degatsParRoll = degatsParRoll;
            this.immunise = immunise;
            this.efficaciteType = efficaciteType;

            if (degatsParRoll.length == 0) {
                this.degatsMin = 0;
                this.degatsMax = 0;
                this.pourcentageMin = 0;
                this.pourcentageMax = 0;
                this.koGaranti = false;
                this.koPossible = false;
                return;
            }

            this.degatsMin = degatsParRoll[0];
            this.degatsMax = degatsParRoll[degatsParRoll.length - 1];
            this.pourcentageMin = pvMaxDefenseur == 0 ? 0 : (100.0 * degatsMin) / pvMaxDefenseur;
            this.pourcentageMax = pvMaxDefenseur == 0 ? 0 : (100.0 * degatsMax) / pvMaxDefenseur;
            this.koGaranti = degatsMin >= pvActuelsDefenseur;
            this.koPossible = degatsMax >= pvActuelsDefenseur;
        }

        public static Resultat immunise() { return new Resultat(new int[0], 0, 0, true, 0.0); }
        public static Resultat sansDegats() { return new Resultat(new int[0], 0, 0, false, 1.0); }

        private static Resultat depuis(int[] degats, Pokemon defenseur, double efficacite) {
            return new Resultat(degats, defenseur.getPvMax(), defenseur.getPvActuels(), false, efficacite);
        }
    }

    private DamageCalculator() {}

    public static Resultat calculer(Pokemon attaquant, Pokemon defenseur, Move capacite,
                                     Field terrain, Field.Ecrans ecransDefenseur, boolean critique) {

        if (capacite.estCapaciteDeStatut() || capacite.getPuissanceDeBase() <= 0) {
            return Resultat.sansDegats();
        }

        ModifierContext ctx = new ModifierContext(attaquant, defenseur, capacite, terrain, critique);

        AbilityModifier talentAttaquant = AbilityModifier.pour(attaquant.getTalent());
        if (talentAttaquant != null) talentAttaquant.appliquerCoteAttaquant(ctx);
        ItemModifier objetAttaquant = ItemModifier.pour(attaquant.getObjet());
        if (objetAttaquant != null) objetAttaquant.appliquerCoteAttaquant(ctx);

        AbilityModifier talentDefenseur = AbilityModifier.pour(defenseur.getTalent());
        if (talentDefenseur != null) talentDefenseur.appliquerCoteDefenseur(ctx);
        ItemModifier objetDefenseur = ItemModifier.pour(defenseur.getObjet());
        if (objetDefenseur != null) objetDefenseur.appliquerCoteDefenseur(ctx);

        if (ctx.immuniteType) return Resultat.immunise();

        double efficacite = calculerEfficaciteType(capacite, defenseur);
        if (efficacite == 0.0) return Resultat.immunise();

        // Déguisement (Mimiqui) : le premier coup est annulé, Mimiqui perd 1/8 de ses PV max
        if ("Déguisement".equals(defenseur.getTalent())
                && defenseur.getPvActuels() >= defenseur.getPvMax()) {
            int huitieme = Math.max(1, defenseur.getPvMax() / 8);
            int[] fixe = new int[16];
            for (int i = 0; i < 16; i++) fixe[i] = huitieme;
            return Resultat.depuis(fixe, defenseur, efficacite);
        }

        appliquerModificateursConditionnels(ctx, efficacite, attaquant, defenseur);

        Stat statOffensive = capacite.getCategorie() == Move.Categorie.PHYSIQUE ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
        // Body Press utilise la Défense de l'attaquant
        if ("bodypress".equals(capacite.getNom())) {
            statOffensive = Stat.DEFENSE;
        }
        Stat statDefensive = capacite.getCategorie() == Move.Categorie.PHYSIQUE ? Stat.DEFENSE : Stat.DEFENSE_SPE;

        int statA = calculerStatOffensiveEffective(attaquant, capacite, ctx, statOffensive, critique);
        int statD = calculerStatDefensiveEffective(defenseur, terrain, ctx, statDefensive, critique);

        int puissance = capacite.getPuissanceDeBase();
        Integer puissanceTotale = PUISSANCE_TOTALE.get(capacite.getNom());
        if (puissanceTotale != null) {
            puissance = puissanceTotale;
        }
        // Knock Off : x1.5 puissance si le défenseur a un objet
        if ("knockoff".equals(capacite.getNom()) && defenseur.getObjet() != null) {
            puissance = (int)(puissance * 1.5);
        }

        int niveauTerme = (2 * attaquant.getNiveau()) / 5 + 2;
        long base = ((long) niveauTerme * puissance * statA) / Math.max(1, statD);
        base = base / 50 + 2;

        double stab = calculerSTAB(attaquant, capacite, ctx);
        double meteo = terrain.multiplicateurMeteo(capacite.getType());
        double champTerrain = estAuSol(attaquant) ? terrain.multiplicateurTerrain(capacite.getType()) : 1.0;
        double ecrans = (!critique && ecransDefenseur != null)
            ? ecransDefenseur.multiplicateur(capacite.getCategorie())
            : 1.0;
        // Torrent de Coups : coups toujours critiques
        double critMult = (critique || "surgingstrikes".equals(capacite.getNom())) ? 1.5 : 1.0;

        int[] degatsUnCoup = new int[16];
        for (int i = 0; i < 16; i++) {
            double alea = (85 + i) / 100.0;
            long d = base;
            d = appliquerEtFloor(d, meteo);
            d = appliquerEtFloor(d, critMult);
            d = appliquerEtFloor(d, alea);
            d = appliquerEtFloor(d, stab);
            d = appliquerEtFloor(d, efficacite);
            d = appliquerEtFloor(d, ecrans);
            d = appliquerEtFloor(d, champTerrain);
            d = appliquerEtFloor(d, ctx.multiplicateurDegatsFinal);
            degatsUnCoup[i] = (int) Math.max(1, d);
        }

        // Multi-coups : multiplier par le nombre de coups
        int[] hits = MULTI_HIT.get(capacite.getNom());
        if (hits != null && hits[1] > 1) {
            int minHits = hits[0];
            int maxHits = hits[1];
            // Multi-Coups (Skill Link) : toujours le max
            if ("Multi-Coups".equals(attaquant.getTalent())) {
                minHits = maxHits;
            }
            int[] degatsMulti = new int[16];
            for (int i = 0; i < 16; i++) {
                int nbHits = minHits + (i * (maxHits - minHits)) / 15;
                degatsMulti[i] = degatsUnCoup[i] * nbHits;
            }
            return Resultat.depuis(degatsMulti, defenseur, efficacite);
        }

        return Resultat.depuis(degatsUnCoup, defenseur, efficacite);
    }

    private static long appliquerEtFloor(long valeur, double multiplicateur) {
        return (long) Math.floor(valeur * multiplicateur);
    }

    private static double calculerEfficaciteType(Move capacite, Pokemon defenseur) {
        if (capacite.getType() == PokemonType.STELLAIRE) {
            return defenseur.isTeracristallise() ? 2.0 : 1.0;
        }
        PokemonType t1 = defenseur.getTypeDefenseurEffectif1();
        PokemonType t2 = defenseur.getTypeDefenseurEffectif2();
        return capacite.getType().efficaciteContre(t1, t2);
    }

    private static void appliquerModificateursConditionnels(ModifierContext ctx, double efficacite,
                                                              Pokemon attaquant, Pokemon defenseur) {
        if (efficacite > 1.0) {
            if ("Ceinture Pro".equals(attaquant.getObjet())) {
                ctx.multiplicateurDegatsFinal *= 1.2;
            }
            String talentDef = defenseur.getTalent();
            if ("Filtre".equals(talentDef) || "Solide Roc".equals(talentDef)) {
                ctx.multiplicateurDegatsFinal *= 0.75;
            }
        } else if (efficacite > 0.0 && efficacite < 1.0) {
            if ("Verres Teintés".equals(attaquant.getTalent())) {
                ctx.multiplicateurDegatsFinal *= 2.0;
            }
        }
    }

    private static int calculerStatOffensiveEffective(Pokemon attaquant, Move capacite, ModifierContext ctx,
                                                        Stat stat, boolean critique) {
        int base = attaquant.getStatCalculee(stat);
        int stage = attaquant.getStage(stat);

        if (ctx.ignorerStagesAttaquant) {
            stage = 0;
        } else if (critique && stage < 0) {
            stage = 0;
        }

        double valeur = appliquerStage(base, stage);
        valeur *= ctx.multiplicateurAttaque;

        if (stat == Stat.ATTAQUE && attaquant.getStatut() == Pokemon.Statut.BRULURE && !ctx.ignorerPenaliteBrulure) {
            valeur *= 0.5;
        }

        return (int) Math.floor(valeur);
    }

    private static int calculerStatDefensiveEffective(Pokemon defenseur, Field terrain, ModifierContext ctx,
                                                        Stat stat, boolean critique) {
        int base = defenseur.getStatCalculee(stat);
        int stage = defenseur.getStage(stat);

        if (ctx.ignorerStagesDefenseur) {
            stage = 0;
        } else if (critique && stage > 0) {
            stage = 0;
        }

        double valeur = appliquerStage(base, stage);
        valeur *= ctx.multiplicateurDefense;

        if (terrain.getMeteo() == Field.Meteo.SABLE && stat == Stat.DEFENSE_SPE
            && defenseur.possedeType(PokemonType.ROCHE)) {
            valeur *= 1.5;
        }
        if (terrain.getMeteo() == Field.Meteo.NEIGE && stat == Stat.DEFENSE
            && defenseur.possedeType(PokemonType.GLACE)) {
            valeur *= 1.5;
        }

        return (int) Math.floor(valeur);
    }

    private static double appliquerStage(int statBase, int stage) {
        if (stage >= 0) return statBase * (2.0 + stage) / 2.0;
        return statBase * 2.0 / (2.0 - stage);
    }

    private static double calculerSTAB(Pokemon attaquant, Move capacite, ModifierContext ctx) {
        PokemonType typeCapacite = capacite.getType();
        boolean typeOriginal = attaquant.possedeType(typeCapacite);

        if (attaquant.isTeracristallise()) {
            PokemonType tera = attaquant.getTeraType();
            if (tera != null && typeCapacite == tera) {
                if (typeOriginal) return ctx.stabAugmente ? 2.25 : 2.0;
                return ctx.stabAugmente ? 2.0 : 1.5;
            }
            if (typeOriginal) return ctx.stabAugmente ? 2.0 : 1.5;
            return 1.0;
        }

        if (typeOriginal) return ctx.stabAugmente ? 2.0 : 1.5;
        return 1.0;
    }

    private static boolean estAuSol(Pokemon p) {
        if (p.possedeType(PokemonType.VOL)) return false;
        return !"Lévitation".equals(p.getTalent());
    }
}
