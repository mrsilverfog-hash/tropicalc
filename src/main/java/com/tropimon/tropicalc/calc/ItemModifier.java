package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente l'effet d'un objet tenu sur le calcul de dégâts. Chaque objet
 * peut modifier le contexte lorsqu'il est tenu par l'attaquant et/ou par le
 * défenseur. Couvre les objets les plus courants en stall compétitif ;
 * facilement extensible en ajoutant une entrée au registre ci-dessous.
 */
public interface ItemModifier {

    /** Appelé quand cet objet est tenu par le Pokémon qui attaque. */
    default void appliquerCoteAttaquant(ModifierContext ctx) {
    }

    /** Appelé quand cet objet est tenu par le Pokémon qui défend. */
    default void appliquerCoteDefenseur(ModifierContext ctx) {
    }

    Map<String, ItemModifier> REGISTRE = construireRegistre();

    /**
     * Renvoie le modificateur correspondant au nom français de l'objet,
     * ou null si l'objet n'a aucun effet sur le calcul de dégâts (ex: Reste,
     * Bottes Increvables) ou n'est pas encore implémenté.
     */
    static ItemModifier pour(String nomObjet) {
        if (nomObjet == null) {
            return null;
        }
        return REGISTRE.get(nomObjet);
    }

    private static Map<String, ItemModifier> construireRegistre() {
        Map<String, ItemModifier> m = new HashMap<>();

        // Bâton Choix (Choice Band) : Attaque x1.5 sur capacités physiques uniquement
        m.put("Bâton Choix", new ItemModifier() {
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

        // Ceinture Brutale (Expert Belt) : dégâts x1.2 si le coup est super efficace.
        // L'efficacité de type doit déjà avoir été calculée avant ce point ;
        // DamageCalculator est responsable de ne déclencher cet objet que si
        // l'efficacité totale est strictement supérieure à 1.0.
        m.put("Ceinture Brutale", new ItemModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                // Le multiplicateur réel (x1.2) est appliqué directement par
                // DamageCalculator après calcul de l'efficacité de type,
                // car cet objet a besoin de connaître le résultat du type chart.
                // On ne fait rien ici : voir DamageCalculator.appliquerObjetsConditionnels().
            }
        });

        // Gilet Tactique (Assault Vest) : Défense Spéciale x1.5 pour le porteur
        m.put("Gilet Tactique", new ItemModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE) {
                    ctx.multiplicateurDefense *= 1.5;
                }
            }
        });

        // Évoluroc (Eviolite) : Défense et Défense Spéciale x1.5 pour le porteur.
        // Limitation actuelle : s'applique sans vérifier si le Pokémon est
        // réellement non-totalement-évolué (l'information n'est pas encore
        // modélisée dans Pokemon.java). À affiner plus tard si besoin.
        m.put("Évoluroc", new ItemModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                ctx.multiplicateurDefense *= 1.5;
            }
        });

        return m;
    }
}
