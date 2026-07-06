package com.tropimon.tropicalc.calc;

import java.util.Map;

public class DamageCalculator {

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

        if (capacite.estCapaciteDeStatut()) {
            return Resultat.sansDegats();
        }

        // Ball'Météo : change de type et double de puissance selon la météo
        capacite = capaciteEffective(capacite, terrain);

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

        appliquerModificateursConditionnels(ctx, efficacite, attaquant, defenseur);

        // Puissance effective : gère Sabotage, Gyroball, Boule Élek, Châtiment,
        // Façade, Balayage, Nœud Herbe, Tacle Lourd, Tacle Feu.
        // Doit être calculée AVANT les stats : Façade pose ignorerPenaliteBrulure.
        int puissance = puissanceEffective(capacite, attaquant, defenseur, ctx);
        if (puissance <= 0) {
            return Resultat.sansDegats();
        }

        Stat statOffensive = capacite.getCategorie() == Move.Categorie.PHYSIQUE ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
        // Body Press utilise la Défense de l'attaquant
        if ("bodypress".equals(capacite.getNom())) {
            statOffensive = Stat.DEFENSE;
        }
        Stat statDefensive = capacite.getCategorie() == Move.Categorie.PHYSIQUE ? Stat.DEFENSE : Stat.DEFENSE_SPE;

        int statA = calculerStatOffensiveEffective(attaquant, capacite, ctx, statOffensive, critique);
        int statD = calculerStatDefensiveEffective(defenseur, terrain, ctx, statDefensive, critique);

        int niveauTerme = (2 * attaquant.getNiveau()) / 5 + 2;
        long base = ((long) niveauTerme * puissance * statA) / Math.max(1, statD);
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

        // Multi-coups : total = dégâts par coup x nombre de coups
        // (interpolation coupsMin -> coupsMax du roll min au roll max)
        int[] coups = coupsEffectifs(capacite, attaquant);
        if (coups[1] > 1) {
            for (int i = 0; i < 16; i++) {
                double c = coups[0] + (coups[1] - coups[0]) * (i / 15.0);
                degats[i] = (int) Math.round(degats[i] * c);
            }
        }

        return Resultat.depuis(degats, defenseur, efficacite);
    }

    // Capacités multi-coups (id showdown -> {coups min, coups max})
    private static final Map<String, int[]> MULTI_COUPS = Map.ofEntries(
        // 2 coups fixes
        Map.entry("doublekick", new int[]{2, 2}),
        Map.entry("bonemerang", new int[]{2, 2}),
        Map.entry("doublehit", new int[]{2, 2}),
        Map.entry("dualwingbeat", new int[]{2, 2}),
        Map.entry("dragondarts", new int[]{2, 2}),
        Map.entry("geargrind", new int[]{2, 2}),
        Map.entry("twineedle", new int[]{2, 2}),
        Map.entry("dualchop", new int[]{2, 2}),
        Map.entry("twinbeam", new int[]{2, 2}),
        Map.entry("tachyoncutter", new int[]{2, 2}),
        // 3 coups fixes
        Map.entry("surgingstrikes", new int[]{3, 3}),
        Map.entry("tripledive", new int[]{3, 3}),
        // 2 à 5 coups
        Map.entry("bulletseed", new int[]{2, 5}),
        Map.entry("rockblast", new int[]{2, 5}),
        Map.entry("iciclespear", new int[]{2, 5}),
        Map.entry("pinmissile", new int[]{2, 5}),
        Map.entry("tailslap", new int[]{2, 5}),
        Map.entry("scaleshot", new int[]{2, 5}),
        Map.entry("furyswipes", new int[]{2, 5}),
        Map.entry("doubleslap", new int[]{2, 5}),
        Map.entry("furyattack", new int[]{2, 5}),
        Map.entry("spikecannon", new int[]{2, 5}),
        Map.entry("barrage", new int[]{2, 5}),
        Map.entry("cometpunch", new int[]{2, 5}),
        Map.entry("armthrust", new int[]{2, 5}),
        Map.entry("bonerush", new int[]{2, 5}),
        Map.entry("watershuriken", new int[]{2, 5}),
        // 10 coups
        Map.entry("populationbomb", new int[]{10, 10})
    );

    /**
     * Nombre de coups effectifs {min, max}.
     * Multi-Coups (Skill Link) : toujours le max.
     * Dé Pipé (Loaded Dice) : 4 à 5 coups pour les capacités 2-5.
     */
    private static int[] coupsEffectifs(Move capacite, Pokemon attaquant) {
        int[] base = MULTI_COUPS.get(capacite.getNom());
        if (base == null) return new int[]{1, 1};
        int min = base[0];
        int max = base[1];
        if (min != max) {
            if ("Multi-Coups".equals(attaquant.getTalent())) {
                min = max;
            } else if ("Dé Pipé".equals(attaquant.getObjet())) {
                min = Math.max(min, max - 1);
            }
        }
        return new int[]{min, max};
    }

    private static long appliquerEtFloor(long valeur, double multiplicateur) {
        return (long) Math.floor(valeur * multiplicateur);
    }

    /**
     * Transforme la capacité si son type dépend du contexte.
     * Ball'Météo : Feu/Eau/Roche/Glace et puissance 100 selon la météo.
     */
    private static Move capaciteEffective(Move capacite, Field terrain) {
        if (!"weatherball".equals(capacite.getNom())) return capacite;
        PokemonType nouveauType = switch (terrain.getMeteo()) {
            case SOLEIL, SOLEIL_INTENSE -> PokemonType.FEU;
            case PLUIE, PLUIE_INTENSE -> PokemonType.EAU;
            case SABLE -> PokemonType.ROCHE;
            case NEIGE -> PokemonType.GLACE;
            default -> null;
        };
        if (nouveauType == null) return capacite;
        return Move.builder("weatherball", nouveauType, capacite.getCategorie())
            .puissance(100).build();
    }

    /**
     * Puissance effective des capacités à puissance variable.
     * Les capacités variables ont une puissance de base de 0 dans les données
     * Showdown : sans ce calcul, elles affichaient zéro dégât.
     */
    private static int puissanceEffective(Move capacite, Pokemon attaquant, Pokemon defenseur,
                                           ModifierContext ctx) {
        int puissance = capacite.getPuissanceDeBase();
        String nom = capacite.getNom();
        if (nom == null) return puissance;

        switch (nom) {
            case "knockoff" -> {
                // Sabotage : x1.5 si le défenseur tient un objet
                if (defenseur.getObjet() != null) puissance = (int) (puissance * 1.5);
            }
            case "facade" -> {
                // Façade : x2 si brûlure, poison ou paralysie ; ignore la pénalité de brûlure
                Pokemon.Statut s = attaquant.getStatut();
                if (s == Pokemon.Statut.BRULURE || s == Pokemon.Statut.POISON
                    || s == Pokemon.Statut.POISON_GRAVE || s == Pokemon.Statut.PARALYSIE) {
                    puissance *= 2;
                    ctx.ignorerPenaliteBrulure = true;
                }
            }
            case "hex" -> {
                // Châtiment : x2 si le défenseur a un statut
                if (defenseur.getStatut() != Pokemon.Statut.AUCUN) puissance *= 2;
            }
            case "gyroball" -> {
                // Gyroball : 25 x Vit. défenseur / Vit. attaquant + 1, max 150
                double vitAtt = Math.max(1, vitesseEffective(attaquant));
                double vitDef = vitesseEffective(defenseur);
                puissance = (int) Math.min(150, Math.floor(25.0 * vitDef / vitAtt) + 1);
                puissance = Math.max(1, puissance);
            }
            case "electroball" -> {
                // Boule Élek : paliers selon le ratio Vit. attaquant / Vit. défenseur
                double vitAtt = vitesseEffective(attaquant);
                double vitDef = Math.max(1, vitesseEffective(defenseur));
                double ratio = vitAtt / vitDef;
                if (ratio >= 4) puissance = 150;
                else if (ratio >= 3) puissance = 120;
                else if (ratio >= 2) puissance = 80;
                else if (ratio >= 1) puissance = 60;
                else puissance = 40;
            }
            case "lowkick", "grassknot" -> {
                // Balayage / Nœud Herbe : paliers selon le poids du défenseur (hg)
                double poids = poidsEffectif(defenseur);
                if (poids <= 0) puissance = 60; // poids inconnu : valeur médiane
                else if (poids >= 2000) puissance = 120;
                else if (poids >= 1000) puissance = 100;
                else if (poids >= 500) puissance = 80;
                else if (poids >= 250) puissance = 60;
                else if (poids >= 100) puissance = 40;
                else puissance = 20;
            }
            case "heavyslam", "heatcrash" -> {
                // Tacle Lourd / Tacle Feu : paliers selon le ratio de poids attaquant/défenseur
                double poidsAtt = poidsEffectif(attaquant);
                double poidsDef = poidsEffectif(defenseur);
                if (poidsAtt <= 0 || poidsDef <= 0) {
                    puissance = 80; // poids inconnu : valeur médiane
                } else {
                    double ratio = poidsAtt / poidsDef;
                    if (ratio >= 5) puissance = 120;
                    else if (ratio >= 4) puissance = 100;
                    else if (ratio >= 3) puissance = 80;
                    else if (ratio >= 2) puissance = 60;
                    else puissance = 40;
                }
            }
            case "return", "frustration" -> {
                // Retour / Frustration : bonheur inconnu, on suppose la puissance max
                puissance = 102;
            }
            case "hiddenpower" -> {
                // Puissance Cachée : 60 fixe depuis la 6G (type non déductible des IVs adverses)
                puissance = 60;
            }
            case "acrobatics" -> {
                // Acrobatie : x2 si l'attaquant n'a pas d'objet
                if (attaquant.getObjet() == null) puissance *= 2;
            }
            case "storedpower", "powertrip" -> {
                // Force Ajoutée / Total Contrôle : 20 + 20 par boost positif de l'attaquant
                int boosts = 0;
                for (Stat s : Stat.values()) {
                    if (s != Stat.PV) boosts += Math.max(0, attaquant.getStage(s));
                }
                puissance = 20 + 20 * boosts;
            }
            case "flail", "reversal" -> {
                // Fléau / Contre : paliers selon les PV restants de l'attaquant
                int pvMax = Math.max(1, attaquant.getPvMax());
                int p = (48 * attaquant.getPvActuels()) / pvMax;
                if (p >= 33) puissance = 20;
                else if (p >= 17) puissance = 40;
                else if (p >= 10) puissance = 80;
                else if (p >= 5) puissance = 100;
                else if (p >= 2) puissance = 150;
                else puissance = 200;
            }
            case "triplekick" -> {
                // Triple Pied : 10+20+30, affiché comme total
                puissance = 60;
            }
            case "tripleaxel" -> {
                // Triple Axel : 20+40+60, affiché comme total
                puissance = 120;
            }
            case "eruption", "waterspout", "dragonenergy" -> {
                // Éruption / Giclédo / Draco-Énergie : 150 x PV restants / PV max
                int pvMax = Math.max(1, attaquant.getPvMax());
                puissance = Math.max(1, (150 * attaquant.getPvActuels()) / pvMax);
            }
            default -> { /* puissance de base inchangée */ }
        }
        return puissance;
    }

    /** Vitesse en combat : stages, Écharpe Choix, paralysie. */
    private static double vitesseEffective(Pokemon p) {
        double v = p.getStatCalculee(Stat.VITESSE);
        int stage = p.getStage(Stat.VITESSE);
        if (stage >= 0) v = v * (2.0 + stage) / 2.0;
        else v = v * 2.0 / (2.0 - stage);
        if ("Écharpe Choix".equals(p.getObjet())) v *= 1.5;
        if (p.getStatut() == Pokemon.Statut.PARALYSIE) v *= 0.5;
        return Math.floor(v);
    }

    /** Poids en hectogrammes, modifié par Heavy Metal, Light Metal et Pierre Allégée. */
    private static double poidsEffectif(Pokemon p) {
        double poids = p.getPoidsHg();
        if (poids <= 0) return 0;
        String talent = p.getTalent();
        if ("Heavy Metal".equals(talent)) poids *= 2.0;
        if ("Light Metal".equals(talent)) poids *= 0.5;
        if ("Pierre Allégée".equals(p.getObjet())) poids *= 0.5;
        return Math.max(1, poids);
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

        // Protéen / Libéro : l'attaquant prend le type de son attaque → STAB garanti
        String talent = attaquant.getTalent();
        boolean proteen = "Protéen".equals(talent) || "Libéro".equals(talent);

        boolean typeOriginal = attaquant.possedeType(typeCapacite) || proteen;

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
