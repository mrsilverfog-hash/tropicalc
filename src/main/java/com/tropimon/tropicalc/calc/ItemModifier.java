package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente l'effet d'un objet tenu sur le calcul de dégâts.
 */
public interface ItemModifier {

    default void appliquerCoteAttaquant(ModifierContext ctx) {
    }

    default void appliquerCoteDefenseur(ModifierContext ctx) {
    }

    Map<String, ItemModifier> REGISTRE = construireRegistre();

    static ItemModifier pour(String nomObjet) {
        if (nomObjet == null) {
            return null;
        }
        return REGISTRE.get(nomObjet);
    }

    private static Map<String, ItemModifier> construireRegistre() {
        Map<String, ItemModifier> m = new HashMap<>();

        // Bandeau Choix (Choice Band) : Attaque x1.5 sur capacités physiques uniquement
        m.put("Bandeau Choix", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        });

        // Lunettes Choix (Choice Specs) : Attaque Spéciale x1.5
        m.put("Lunettes Choix", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        });

        // Orbe Vie (Life Orb) : dégâts finaux x1.3 (le recul de 10% PV n'est pas géré ici)
        m.put("Orbe Vie", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                ctx.multiplicateurDegatsFinal *= 1.3;
            }
        });

        // Ceinture Pro (Expert Belt) : dégâts x1.2 si le coup est super efficace.
        // Le multiplicateur réel est déclenché par DamageCalculator
        // (voir appliquerModificateursConditionnels), car il a besoin de
        // connaître le résultat du type chart.
        m.put("Ceinture Pro", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                // Rien ici volontairement, voir DamageCalculator.
            }
        });

        // Veste de Combat (Assault Vest) : Défense Spéciale x1.5 pour le porteur
        m.put("Veste de Combat", new ItemModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE) {
                    ctx.multiplicateurDefense *= 1.5;
                }
            }
        });

        // Évoluroc (Eviolite) : Défense et Défense Spéciale x1.5 pour le porteur.
        // Limitation actuelle : s'applique sans vérifier si le Pokémon est
        // réellement non-totalement-évolué.
        m.put("Évoluroc", new ItemModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                ctx.multiplicateurDefense *= 1.5;
            }
        });

        // Bandeau Muscle (Muscle Band) : +10% dégâts sur les capacités
        // physiques uniquement. Confirmé (noms vérifiés avant correction du
        // mauvais "Bandeau Muscles") : présent dans les candidats du
        // narrowing depuis le début mais sans aucun effet réel jusqu'ici.
        m.put("Bandeau Muscle", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) {
                    ctx.multiplicateurDegatsFinal *= 1.1;
                }
            }
        });

        // Lunettes Sages (Wise Glasses) : +10% dégâts sur les capacités
        // spéciales uniquement. Même situation que Bandeau Muscle.
        m.put("Lunettes Sages", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE) {
                    ctx.multiplicateurDegatsFinal *= 1.1;
                }
            }
        });

        // Objets améliorant un type : x1.2 sur la puissance des capacités de
        // ce type (ratio exact du jeu, 4915/4096). Absents jusqu'ici : un
        // Scalpereur aux Lunettes Noires tapait 20% plus fort que prévu.
        String[][] objetsDeType = {
            {"Aimant", "ELECTRIK"},
            {"Bec Pointu", "VOL"},
            {"Ceinture Noire", "COMBAT"},
            {"Charbon", "FEU"},
            {"Croc Dragon", "DRAGON"},
            {"Cuillère Tordue", "PSY"},
            {"Eau Mystique", "EAU"},
            {"Glace Éternelle", "GLACE"},
            {"Graine Miracle", "PLANTE"},
            {"Lunettes Noires", "TENEBRES"},
            {"Peau Métal", "ACIER"},
            {"Pic Venin", "POISON"},
            {"Pierre Dure", "ROCHE"},
            {"Poudre Argentée", "INSECTE"},
            {"Rune Sort", "SPECTRE"},
            {"Sable Doux", "SOL"},
            {"Mouchoir Soie", "NORMAL"},
            {"Plume Enchantée", "FEE"},
        };
        for (String[] o : objetsDeType) {
            final PokemonType typeBooste = PokemonType.valueOf(o[1]);
            m.put(o[0], new ItemModifier() {
                @Override
                public void appliquerCoteAttaquant(ModifierContext ctx) {
                    if (ctx.capacite.getType() == typeBooste) {
                        ctx.multiplicateurDegatsFinal *= 4915.0 / 4096.0;
                    }
                }
            });
        }

        // Gant de Boxe (Punching Glove) : +10% dégâts sur les capacités "poing",
        // cumulable avec Poing de Fer (confirmé Bulbapedia). Rend aussi la
        // capacité non-contact en vrai jeu (recul par contact/Casque Brut
        // évité) - non modélisé ici, limite mineure.
        m.put("Gant de Boxe", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (com.tropimon.tropicalc.calc.MoveFlags.estPoing(ctx.capacite.getNom())) {
                    ctx.multiplicateurDegatsFinal *= 1.1;
                }
            }
        });

        return m;
    }
}
