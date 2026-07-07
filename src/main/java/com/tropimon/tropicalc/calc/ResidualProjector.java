package com.tropimon.tropicalc.calc;

/**
 * Projette les dégâts résiduels par tour (statut, météo, objet, talent)
 * et estime le nombre de tours avant KO sans attaque directe.
 * Cœur de la stratégie stall : gagner par Toxik + résidus.
 */
public final class ResidualProjector {

    private ResidualProjector() {
    }

    /**
     * @param netPremierTourPct dégâts nets du prochain tour en % des PV max (négatif = régénère)
     * @param toursAvantKO      nombre de tours avant KO par résidus seuls (-1 si jamais)
     * @param detail            sources actives, ex. "Toxik+sable-Restes"
     */
    public record Projection(double netPremierTourPct, int toursAvantKO, String detail) {
    }

    /** Retourne null si aucun effet résiduel n'est actif. */
    public static Projection projeter(Pokemon p, Field.Meteo meteo) {
        String talent = p.getTalent();
        String objet = p.getObjet();
        boolean gardeMagik = "Garde Magik".equals(talent);
        boolean soinPoison = "Soin Poison".equals(talent);

        StringBuilder detail = new StringBuilder();

        // --- Sources constantes (en 16èmes de PV max par tour) ---
        int constant = 0;

        Pokemon.Statut statut = p.getStatut();
        boolean toxik = statut == Pokemon.Statut.POISON_GRAVE;

        if (statut == Pokemon.Statut.BRULURE && !gardeMagik) {
            constant += 1;
            ajouter(detail, "brûlure");
        } else if (statut == Pokemon.Statut.POISON) {
            if (soinPoison) {
                constant -= 2;
                ajouter(detail, "Soin Poison");
            } else if (!gardeMagik) {
                constant += 2;
                ajouter(detail, "poison");
            }
        } else if (toxik) {
            if (soinPoison) {
                constant -= 2;
                toxik = false;
                ajouter(detail, "Soin Poison");
            } else if (gardeMagik) {
                toxik = false;
            } else {
                ajouter(detail, "Toxik");
            }
        }

        if (meteo == Field.Meteo.SABLE && !gardeMagik && !immuniseSable(p)) {
            constant += 1;
            ajouter(detail, "sable");
        }

        if ("Restes".equals(objet)) {
            constant -= 1;
            ajouter(detail, "-Restes");
        } else if ("Boue Noire".equals(objet)) {
            boolean typePoison = p.getType1() == PokemonType.POISON || p.getType2() == PokemonType.POISON;
            if (typePoison) {
                constant -= 1;
                ajouter(detail, "-Boue Noire");
            } else if (!gardeMagik) {
                constant += 2;
                ajouter(detail, "Boue Noire");
            }
        }

        if ("Cuvette".equals(talent)
            && (meteo == Field.Meteo.PLUIE || meteo == Field.Meteo.PLUIE_INTENSE)) {
            constant -= 1;
            ajouter(detail, "-Cuvette");
        }
        if ("Corps Gel".equals(talent) && meteo == Field.Meteo.NEIGE) {
            constant -= 1;
            ajouter(detail, "-Corps Gel");
        }

        if (constant == 0 && !toxik) return null;

        // --- Simulation tour par tour (compteur Toxik supposé repartir à 1) ---
        int pvMax = Math.max(1, p.getPvMax());
        double pv = p.getPvActuels();
        int toursKO = -1;
        double netPremierTour = 0;

        for (int tour = 1; tour <= 30; tour++) {
            int seiziemes = constant + (toxik ? tour : 0);
            double delta = (pvMax * seiziemes) / 16.0;
            if (tour == 1) netPremierTour = (100.0 * seiziemes) / 16.0;
            pv -= delta;
            if (pv > pvMax) pv = pvMax;
            if (pv <= 0) {
                toursKO = tour;
                break;
            }
        }

        return new Projection(netPremierTour, toursKO, detail.toString());
    }

    private static boolean immuniseSable(Pokemon p) {
        PokemonType t1 = p.getType1();
        PokemonType t2 = p.getType2();
        if (t1 == PokemonType.ROCHE || t1 == PokemonType.SOL || t1 == PokemonType.ACIER) return true;
        if (t2 == PokemonType.ROCHE || t2 == PokemonType.SOL || t2 == PokemonType.ACIER) return true;
        String talent = p.getTalent();
        if ("Voile Sable".equals(talent) || "Baigne Sable".equals(talent)
            || "Force Sable".equals(talent) || "Envelocape".equals(talent)) return true;
        return "Lunettes Filtre".equals(p.getObjet());
    }

    private static void ajouter(StringBuilder sb, String source) {
        if (sb.length() > 0 && !source.startsWith("-")) sb.append("+");
        sb.append(source);
    }
}
