package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente l'effet d'un talent sur le calcul de dégâts. Couvre les
 * talents les plus pertinents en stall/compétitif. Noms français vérifiés
 * (Poképédia / sources officielles). Liste volontairement non-exhaustive
 * (297 talents existent au total) ; facilement extensible via le registre.
 */
public interface AbilityModifier {

    /** Appelé quand ce talent appartient au Pokémon qui attaque. */
    default void appliquerCoteAttaquant(ModifierContext ctx) {
    }

    /** Appelé quand ce talent appartient au Pokémon qui défend. */
    default void appliquerCoteDefenseur(ModifierContext ctx) {
    }

    Map<String, AbilityModifier> REGISTRE = construireRegistre();

    static AbilityModifier pour(String nomTalent) {
        if (nomTalent == null) {
            return null;
        }
        return REGISTRE.get(nomTalent);
    }

    private static Map<String, AbilityModifier> construireRegistre() {
        Map<String, AbilityModifier> m = new HashMap<>();

        // --- Immunités de type ---

        m.put("Lévitation", immuniteContre(PokemonType.SOL));
        m.put("Absorb'Eau", immuniteContre(PokemonType.EAU));
        m.put("Absorb'Volt", immuniteContre(PokemonType.ELECTRIK));
        m.put("Lavabo", immuniteContre(PokemonType.EAU));

        // Torche (Flash Fire) : immunise au Feu. Le boost de 1.5x sur les
        // capacités Feu suivantes du porteur n'est pas géré ici (nécessite
        // un état persistant entre tours, hors scope du calcul instantané).
        m.put("Torche", immuniteContre(PokemonType.FEU));

        // --- Réduction de dégâts subis ---

        // Isograisse (Thick Fat) : divise par deux les dégâts des capacités Feu et Glace
        m.put("Isograisse", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                PokemonType t = ctx.capacite.getType();
                if (t == PokemonType.FEU || t == PokemonType.GLACE) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
            }
        });

        // Filtre / Solide Roc : réduit de 25% les dégâts d'un coup super efficace.
        // Le déclenchement réel (vérifier efficacité > 1.0) est fait par
        // DamageCalculator après calcul du type chart.
        AbilityModifier reductionSuperEfficace = new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                // Voir DamageCalculator.appliquerTalentsConditionnels()
            }
        };
        m.put("Filtre", reductionSuperEfficace);
        m.put("Solide Roc", reductionSuperEfficace);

        // Multi-écailles / Spectro-Bouclier : dégâts x0.5 si le défenseur est à 100% PV
        AbilityModifier demiDegatsPleinePv = new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.defenseur.getPvActuels() == ctx.defenseur.getPvMax()) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
            }
        };
        m.put("Multi-écailles", demiDegatsPleinePv);
        m.put("Spectro-Bouclier", demiDegatsPleinePv);

        // --- Ignorer les stages de combat ---

        // Lucidité (Unaware) : côté attaquant, ignore les stages de Défense
        // du défenseur ; côté défenseur, ignore les stages d'Attaque de l'attaquant.
        m.put("Lucidité", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                ctx.ignorerStagesDefenseur = true;
            }

            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                ctx.ignorerStagesAttaquant = true;
            }
        });

        // --- Modificateurs offensifs ---

        // Adaptabilité (Adaptability) : le STAB passe de x1.5 à x2.0
        m.put("Adaptabilité", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                ctx.stabAugmente = true;
            }
        });

        // Cran (Guts) : Attaque x1.5 si le porteur est statué, et ignore la
        // pénalité de brûlure sur les capacités physiques
        m.put("Cran", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.attaquant.getStatut() != Pokemon.Statut.AUCUN) {
                    ctx.multiplicateurAttaque *= 1.5;
                    ctx.ignorerPenaliteBrulure = true;
                }
            }
        });

        // Adrénaline (Hustle) : Attaque x1.5 sur capacités physiques
        m.put("Adrénaline", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        });

        // Technicien (Technician) : x1.5 si la puissance de base est <= 60
        m.put("Technicien", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getPuissanceDeBase() > 0 && ctx.capacite.getPuissanceDeBase() <= 60) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        // Poing de Fer (Iron Fist) : x1.2 sur les capacités de poing
        m.put("Poing de Fer", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.isPoing()) {
                    ctx.multiplicateurDegatsFinal *= 1.2;
                }
            }
        });

        // Mâchoire Brute (Strong Jaw) : x1.5 sur les capacités de morsure
        m.put("Mâchoire Brute", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.isMorsure()) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        // Force Sable (Sand Force) : x1.3 sur Roche/Sol/Acier pendant une tempête de sable
        m.put("Force Sable", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.terrain.getMeteo() == Field.Meteo.SABLE) {
                    PokemonType t = ctx.capacite.getType();
                    if (t == PokemonType.ROCHE || t == PokemonType.SOL || t == PokemonType.ACIER) {
                        ctx.multiplicateurDegatsFinal *= 1.3;
                    }
                }
            }
        });

        // Verres Teintés (Tinted Lens) : double les dégâts d'un coup "pas très efficace".
        // Le déclenchement réel est fait par DamageCalculator après le type chart.
        m.put("Verres Teintés", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                // Voir DamageCalculator.appliquerTalentsConditionnels()
            }
        });

        return m;
    }

    private static AbilityModifier immuniteContre(PokemonType typeImmunise) {
        return new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getType() == typeImmunise) {
                    ctx.immuniteType = true;
                }
            }
        };
    }
}
