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

        return m;
    }
}
