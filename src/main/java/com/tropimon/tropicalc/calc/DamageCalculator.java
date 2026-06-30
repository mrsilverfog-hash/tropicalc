package com.tropimon.tropicalc.calc;

/**
 * Moteur de calcul de dégâts Gen 9, format Simple uniquement.
 * Assemble PokemonType, Nature, Stat, Move, Pokemon, Field, ItemModifier
 * et AbilityModifier pour produire une fourchette de dégâts sur 16 rolls
 * (85% à 100%), comme le fait Pokémon Showdown / Showdex.
 *
 * Limitations connues (à affiner plus tard si besoin) :
 * - Pas de gestion de Sniper (ratio crit modifié) : crit fixé à x1.5.
 * - Pas de gestion des capacités à puissance variable (Lance-Soleil, Catapulte...).
 * - Pas de gestion de Façade qui annule la pénalité de brûlure.
 * - Évoluroc s'applique sans vérifier le stade d'évolution du Pokémon.
 */
public class DamageCalculator {

    /** Résultat complet d'un calcul de dégâts sur les 16 rolls possibles (85%-100%). */
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

        public static Resultat immunise() {
            return new Resultat(new int[0], 0, 0, true, 0.0);
        }

        public static Resultat sansDegats() {
            return new Resultat(new int[0], 0, 0, false, 1.0);
        }

        private static Resultat depuis(int[] degats, Pokemon defenseur, double efficacite) {
            return new Resultat(degats, defenseur.getPvMax(), defenseur.getPvActuels(), false, efficacite);
        }
    }

    private DamageCalculator() {
    }

    /**
     * Calcule les dégâts d'une capacité de attaquant vers defenseur.
     *
     * @param ecransDefenseur les écrans actifs côté défenseur (Protection,
     *                        Mur Lumière, Brume Aurore), ou null si aucun.
     * @param critique        si vrai, calcule un coup critique.
     */
    public static Resultat calculer(Pokemon attaquant, Pokemon defenseur, Move capacite,
                                     Field terrain, Field.Ecrans ecransDefenseur, boolean critique) {

        if (capacite.estCapaciteDeStatut() || capacite.getPuissanceDeBase() <= 0) {
            return Resultat.sansDegats();
        }

        ModifierContext ctx = new ModifierContext(attaquant, defenseur, capacite, terrain, critique);

        AbilityModifier talentAttaquant = AbilityModifier.pour(attaquant.getTalent());
        if (talentAttaquant != null) {
            talentAttaquant.appliquerCoteAttaquant(ctx);
        }
        ItemModifier objetAttaquant = ItemModifier.pour(attaquant.getObjet());
        if (objetAttaquant != null) {
            objetAttaquant.appliquerCoteAttaquant(ctx);
        }

        AbilityModifier talentDefenseur = AbilityModifier.pour(defenseur.getTalent());
        if (talentDefenseur != null) {
            talentDefenseur.appliquerCoteDefenseur(ctx);
        }
        ItemModifier objetDefenseur = ItemModifier.pour(defenseur.getObjet());
        if (objetDefenseur != null) {
            objetDefenseur.appliquerCoteDefenseur(ctx);
        }

        if (ctx.immuniteType) {
            return Resultat.immunise();
        }

        double efficacite = calculerEfficaciteType(capacite, defenseur);
        if (efficacite == 0.0) {
            return Resultat.immunise();
        }

        appliquerModificateursConditionnels(ctx, efficacite, attaquant, defenseur);

        Stat statOffensive = capacite.getCategorie() == Move.Categorie.PHYSIQUE ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
        Stat statDefensive = capacite.getCategorie() == Move.Categorie.PHYSIQUE ? Stat.DEFENSE : Stat.DEFENSE_SPE;

        int statA = calculerStatOffensiveEffective(attaquant, capacite, ctx, statOffensive, critique);
        int statD = calculerStatDefensiveEffective(defenseur, terrain, ctx, statDefensive, critique);

        int niveauTerme = (2 * attaquant.getNiveau()) / 5 + 2;
        long base = ((long) niveauTerme * capacite.getPuissanceDeBase() * statA) / Math.max(1, statD);
        base = base / 50 + 2;

        double stab = calculerSTAB(attaquant, capacite, ctx);
        double meteo = terrain.multiplicateurMeteo(capacite.getType());
        double champTerrain = estAuSol(attaquant) ? terrain.multiplicateurTerrain(capacite.getType()) : 1.0;
        double ecrans = (!critique && ecransDefenseur != null)
            ? ecransDefenseur.multiplicateur(capacite.getCategorie())
            : 1.0;
        double critMult = critique ? 1.5 : 1.0;

        int[] degats = new int[16];
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
            degats[i] = (int) Math.max(1, d);
        }

        return Resultat.depuis(degats, defenseur, efficacite);
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

    /**
     * Applique les effets qui ont besoin de connaître l'efficacité de type
     * avant de se déclencher (Ceinture Brutale, Filtre/Solide Roc, Verres Teintés).
     */
    private static void appliquerModificateursConditionnels(ModifierContext ctx, double efficacite,
                                                              Pokemon attaquant, Pokemon defenseur) {
        if (efficacite > 1.0) {
            if ("Ceinture Brutale".equals(attaquant.getObjet())) {
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
        if (stage >= 0) {
            return statBase * (2.0 + stage) / 2.0;
        }
        return statBase * 2.0 / (2.0 - stage);
    }

    /**
     * Calcule le multiplicateur STAB en tenant compte de la Téracristallisation
     * et de l'Adaptabilité (talent qui augmente le STAB).
     */
    private static double calculerSTAB(Pokemon attaquant, Move capacite, ModifierContext ctx) {
        PokemonType typeCapacite = capacite.getType();
        boolean typeOriginal = attaquant.possedeType(typeCapacite);

        if (attaquant.isTeracristallise()) {
            PokemonType tera = attaquant.getTeraType();
            if (tera != null && typeCapacite == tera) {
                boolean correspondAuTypeOrigine = typeOriginal;
                if (correspondAuTypeOrigine) {
                    return ctx.stabAugmente ? 2.25 : 2.0;
                }
                return ctx.stabAugmente ? 2.0 : 1.5;
            }
            if (typeOriginal) {
                return ctx.stabAugmente ? 2.0 : 1.5;
            }
            return 1.0;
        }

        if (typeOriginal) {
            return ctx.stabAugmente ? 2.0 : 1.5;
        }
        return 1.0;
    }

    private static boolean estAuSol(Pokemon p) {
        if (p.possedeType(PokemonType.VOL)) {
            return false;
        }
        return !"Lévitation".equals(p.getTalent());
    }
}
