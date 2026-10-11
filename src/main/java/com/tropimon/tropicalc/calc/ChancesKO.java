package com.tropimon.tropicalc.calc;

/**
 * Chances de K.O. d'une capacité ("OHKO 37,5 %", "2HKO garanti"...), selon le
 * modèle du calculateur Showdown (getKOChance) : les 16 jets de dégâts, les
 * PV actuels du défenseur et ce qu'il gagne ou perd à chaque fin de tour
 * (Restes, Boue Noire, brûlure, poison, Toxik, tempête de sable, Champ
 * Herbu, talents météo). Vérifié par les tests contre Showdown.
 *
 * Non pris en compte, comme dans le HUD : pièges à l'entrée (le défenseur est
 * déjà sur le terrain), Vampigraine, Salaison, Cauchemar.
 */
public final class ChancesKO {

    private ChancesKO() {
    }

    /**
     * n : nombre de coups (0 = pas de K.O. en 9 coups) ; chance : probabilité
     * (0 à 1) ; possible : K.O. possible en n coups mais chance non calculée
     * (au-delà de 4 coups, comme Showdown).
     */
    public record Resultat(int n, double chance, boolean possible) {

        public static final Resultat AUCUN = new Resultat(0, 0, false);

        /** Texte court : "OHKO garanti", "2HKO 78 %", "5HKO possible", "" si aucun. */
        public String texte() {
            if (n <= 0) return "";
            String coups = n == 1 ? "OHKO" : n + "HKO";
            if (possible) return coups + " possible";
            if (chance >= 1.0) return coups + " garanti";
            double pct = Math.max(Math.min(Math.round(chance * 1000), 999), 1) / 10.0;
            return coups + " " + (pct == Math.floor(pct) ? String.valueOf((int) pct) : String.valueOf(pct)).replace('.', ',') + " %";
        }
    }

    /**
     * @param degats        les 16 jets de dégâts en PV (DamageCalculator.Resultat.degatsParRoll)
     * @param compteurToxik tours de Toxik déjà subis + 1 pour le prochain (0 si pas Toxik)
     */
    public static Resultat calculer(int[] degats, Pokemon attaquant, Pokemon defenseur, Move capacite,
                                    Field terrain, int compteurToxik) {
        return calculer(degats, attaquant, defenseur, capacite, terrain, compteurToxik, false, false);
    }

    /**
     * Avec Salaison et Vampigraine sur le défenseur, que le calculateur
     * Showdown ignore (1/8 par tour chacune, Salaison 1/4 sur Eau/Acier).
     */
    public static Resultat calculer(int[] degats, Pokemon attaquant, Pokemon defenseur, Move capacite,
                                    Field terrain, int compteurToxik, boolean salaison, boolean vampigraine) {
        if (degats == null || degats.length == 0 || degats[degats.length - 1] <= 0) return Resultat.AUCUN;
        int pvMax = defenseur.getPvMax();
        int pv = defenseur.getPvActuels();
        if (degats[0] >= pvMax) return new Resultat(1, 1.0, false);

        int fdt = finDeTour(attaquant, defenseur, capacite, terrain);
        if (!"Garde Magik".equals(defenseur.getTalent())) {
            if (salaison) {
                boolean vulnerable = defenseur.possedeType(PokemonType.EAU) || defenseur.possedeType(PokemonType.ACIER);
                fdt -= pvMax / (vulnerable ? 4 : 8);
            }
            if (vampigraine) fdt -= pvMax / 8;
        }
        int toxik = defenseur.getStatut() == Pokemon.Statut.POISON_GRAVE
            && !"Garde Magik".equals(defenseur.getTalent()) && !"Soin Poison".equals(defenseur.getTalent())
            ? compteurToxik : 0;

        double sans = chance(degats, pv, 0, 1, pvMax, 0);
        double avec = chance(degats, pv, fdt, 1, pvMax, toxik);
        if (sans + avec > 0) {
            return new Resultat(1, sans >= 1.0 ? 1.0 : avec, false);
        }
        for (int i = 2; i <= 4; i++) {
            double c = chance(degats, pv, fdt, i, pvMax, toxik);
            if (c > 0) return new Resultat(i, c, false);
        }
        for (int i = 5; i <= 9; i++) {
            if (totalPrevu(degats[0], fdt, i, toxik, pvMax) >= pv) return new Resultat(i, 1.0, false);
            if (totalPrevu(degats[degats.length - 1], fdt, i, toxik, pvMax) >= pv) return new Resultat(i, 0, true);
        }
        return Resultat.AUCUN;
    }

    /**
     * Précision réelle en % (100 = touche toujours) : précision de base,
     * Annule Garde, Œil Composé, Agitation, Victorieux, Loupe, Poudre Claire,
     * Voile Sable / Rideau Neige, Fatal-Foudre / Vent Violent / Blizzard
     * selon la météo. Les boosts de précision et d'esquive ne sont pas suivis.
     *
     * @param base précision de la capacité (0 ou moins = ne rate jamais)
     */
    public static double precision(double base, String idCoup, Pokemon attaquant, Pokemon defenseur,
                                   Field terrain, boolean physique) {
        if ("Annule Garde".equals(attaquant.getTalent()) || "Annule Garde".equals(defenseur.getTalent())) return 100;
        Field.Meteo meteo = terrain.getMeteo();
        boolean pluie = meteo == Field.Meteo.PLUIE || meteo == Field.Meteo.PLUIE_INTENSE;
        boolean soleil = meteo == Field.Meteo.SOLEIL || meteo == Field.Meteo.SOLEIL_INTENSE;
        if (("thunder".equals(idCoup) || "hurricane".equals(idCoup) || "bleakwindstorm".equals(idCoup)
                || "wildboltstorm".equals(idCoup) || "sandsearstorm".equals(idCoup)) && pluie) return 100;
        if ("blizzard".equals(idCoup) && meteo == Field.Meteo.NEIGE) return 100;
        if (base <= 0 || base > 100) return 100;
        double p = base;
        if (("thunder".equals(idCoup) || "hurricane".equals(idCoup)) && soleil) p = 50;
        if ("Œil Composé".equals(attaquant.getTalent())) p *= 1.3;
        if ("Victorieux".equals(attaquant.getTalent())) p *= 1.1;
        if ("Agitation".equals(attaquant.getTalent()) && physique) p *= 0.8;
        if ("Loupe".equals(attaquant.getObjet())) p *= 1.1;
        if ("Poudre Claire".equals(defenseur.getObjet())) p *= 0.9;
        if (("Voile Sable".equals(defenseur.getTalent()) && meteo == Field.Meteo.SABLE)
                || ("Rideau Neige".equals(defenseur.getTalent()) && meteo == Field.Meteo.NEIGE)) p *= 0.8;
        return Math.min(100, p);
    }

    /** "37,5 %" : même arrondi que les chances de K.O. */
    public static String pourcent(double chance) {
        double pct = Math.max(Math.min(Math.round(chance * 1000), 999), 1) / 10.0;
        return (pct == Math.floor(pct) ? String.valueOf((int) pct) : String.valueOf(pct)).replace('.', ',') + " %";
    }

    /** PV gagnés (+) ou perdus (-) par le défenseur à chaque fin de tour (hors Toxik). */
    static int finDeTour(Pokemon attaquant, Pokemon defenseur, Move capacite, Field terrain) {
        int pvMax = defenseur.getPvMax();
        int fdt = 0;
        String talent = defenseur.getTalent();
        String objet = defenseur.getObjet();
        boolean gardeMagik = "Garde Magik".equals(talent);
        boolean perdObjet = "knockoff".equals(capacite.getNom()) && !"Glu".equals(talent);
        boolean antiSoin = "psychicnoise".equals(capacite.getNom())
            && !"Sans Limite".equals(attaquant.getTalent());
        Field.Meteo meteo = terrain.getMeteo();

        if (meteo == Field.Meteo.SOLEIL || meteo == Field.Meteo.SOLEIL_INTENSE) {
            if ("Peau Sèche".equals(talent) || "Force Soleil".equals(talent)) fdt -= pvMax / 8;
        } else if ((meteo == Field.Meteo.PLUIE || meteo == Field.Meteo.PLUIE_INTENSE) && !antiSoin) {
            if ("Peau Sèche".equals(talent)) fdt += pvMax / 8;
            else if ("Cuvette".equals(talent)) fdt += pvMax / 16;
        } else if (meteo == Field.Meteo.SABLE) {
            boolean immunise = defenseur.possedeType(PokemonType.ROCHE) || defenseur.possedeType(PokemonType.SOL)
                || defenseur.possedeType(PokemonType.ACIER) || gardeMagik || "Envelocape".equals(talent)
                || "Force Sable".equals(talent) || "Baigne Sable".equals(talent) || "Voile Sable".equals(talent)
                || "Lunettes Filtre".equals(objet);
            if (!immunise) fdt -= pvMax / 16;
        } else if (meteo == Field.Meteo.NEIGE) {
            if ("Corps Gel".equals(talent) && !antiSoin) fdt += pvMax / 16;
        }

        if ("Restes".equals(objet) && !perdObjet && !antiSoin) {
            fdt += pvMax / 16;
        } else if ("Boue Noire".equals(objet) && !perdObjet) {
            if (defenseur.possedeType(PokemonType.POISON)) {
                if (!antiSoin) fdt += pvMax / 16;
            } else if (!gardeMagik) {
                fdt -= pvMax / 8;
            }
        }

        if (terrain.getTerrain() == Field.TypeTerrain.HERBU && DamageCalculator.estAuSol(defenseur) && !antiSoin) {
            fdt += pvMax / 16;
        }

        Pokemon.Statut statut = defenseur.getStatut();
        if (statut == Pokemon.Statut.POISON || statut == Pokemon.Statut.POISON_GRAVE) {
            if ("Soin Poison".equals(talent)) {
                if (!antiSoin) fdt += pvMax / 8;
            } else if (statut == Pokemon.Statut.POISON && !gardeMagik) {
                fdt -= pvMax / 8;
            }
        } else if (statut == Pokemon.Statut.BRULURE) {
            if ("Ignifugé".equals(talent)) fdt -= pvMax / 32;
            else if (!gardeMagik) fdt -= pvMax / 16;
        }
        return fdt;
    }

    /** computeKOChance de Showdown : probabilité de K.O. en "coups" coups. */
    private static double chance(int[] degats, int pv, int fdt, int coups, int pvMax, int toxik) {
        int degatsToxik = 0;
        if (toxik > 0) {
            degatsToxik = (toxik * pvMax) / 16;
            toxik++;
        }
        int n = degats.length;
        if (coups == 1) {
            if (fdt - degatsToxik > 0) {
                fdt = 0;
                degatsToxik = 0;
            }
            for (int i = 0; i < n; i++) {
                if (degats[n - 1] - fdt + degatsToxik < pv) return 0;
                if (degats[i] - fdt + degatsToxik >= pv) return (double) (n - i) / n;
            }
        }
        double somme = 0;
        double derniere = 0;
        for (int i = 0; i < n; i++) {
            double c;
            if (i == 0 || degats[i] != degats[i - 1]) {
                c = chance(degats, pv - degats[i] + fdt - degatsToxik, fdt, coups - 1, pvMax, toxik);
            } else {
                c = derniere;
            }
            if (c == 1) {
                somme += n - i;
                break;
            }
            somme += c;
            derniere = c;
        }
        return somme / n;
    }

    /** predictTotal de Showdown (au-delà de 4 coups). */
    private static int totalPrevu(int degats, int fdt, int coups, int toxik, int pvMax) {
        int degatsToxik = 0;
        int fdtDernierTour = fdt;
        if (toxik > 0) {
            for (int i = 0; i < coups - 1; i++) degatsToxik += ((toxik + i) * pvMax) / 16;
            fdtDernierTour -= ((toxik + (coups - 1)) * pvMax) / 16;
        }
        int total = coups > 1 ? degats * coups - fdt * (coups - 1) + degatsToxik : degats;
        if (fdtDernierTour < 0) total -= fdtDernierTour;
        return total;
    }
}
