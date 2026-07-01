package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

public interface AbilityModifier {

    default void appliquerCoteAttaquant(ModifierContext ctx) {
    }

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

        m.put("Lévitation", immuniteContre(PokemonType.SOL));
        m.put("Absorb'Eau", immuniteContre(PokemonType.EAU));
        m.put("Absorb'Volt", immuniteContre(PokemonType.ELECTRIK));
        m.put("Lavabo", immuniteContre(PokemonType.EAU));
        m.put("Torche", immuniteContre(PokemonType.FEU));

        m.put("Isograisse", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                PokemonType t = ctx.capacite.getType();
                if (t == PokemonType.FEU || t == PokemonType.GLACE) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
            }
        });

        AbilityModifier reductionSuperEfficace = new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
            }
        };
        m.put("Filtre", reductionSuperEfficace);
        m.put("Solide Roc", reductionSuperEfficace);

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

        m.put("Adaptabilité", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                ctx.stabAugmente = true;
            }
        });

        // Télécharge (Download) : boost Atk si Déf adverse < DéfSpé, sinon boost AtkSpé
        m.put("Télécharge", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                int defAdverse = ctx.defenseur.getStatCalculee(Stat.DEFENSE);
                int defSpeAdverse = ctx.defenseur.getStatCalculee(Stat.DEFENSE_SPE);
                boolean boostAtk = defAdverse < defSpeAdverse;
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE && boostAtk) {
                    ctx.multiplicateurAttaque *= 1.5;
                } else if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE && !boostAtk) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        });

        m.put("Cran", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.attaquant.getStatut() != Pokemon.Statut.AUCUN) {
                    ctx.multiplicateurAttaque *= 1.5;
                    ctx.ignorerPenaliteBrulure = true;
                }
            }
        });

        m.put("Adrénaline", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        });

        m.put("Technicien", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getPuissanceDeBase() > 0 && ctx.capacite.getPuissanceDeBase() <= 60) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        m.put("Poing de Fer", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.isPoing()) {
                    ctx.multiplicateurDegatsFinal *= 1.2;
                }
            }
        });

        m.put("Mâchoire Brute", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.isMorsure()) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

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

        m.put("Verres Teintés", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
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
