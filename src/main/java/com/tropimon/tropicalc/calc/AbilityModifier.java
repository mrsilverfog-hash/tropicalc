package com.tropimon.tropicalc.calc;

import java.util.HashMap;
import java.util.Map;

public interface AbilityModifier {

    default void appliquerCoteAttaquant(ModifierContext ctx) {
    }

    default void appliquerCoteDefenseur(ModifierContext ctx) {
    }

    // ATTENTION - ordre de declaration significatif.
    // Dans une interface, les champs sont initialises dans l'ordre du texte.
    // Ces deux Set doivent etre declares AVANT REGISTRE : construireRegistre()
    // les passe a immuniteContreCapacites(), et s'ils sont declares plus bas ils
    // valent encore null a ce moment-la. Le modificateur capture alors un Set
    // null et lance une NullPointerException des qu'un defenseur porte
    // Pare-Balles ou Anti-Bruit - en plein rendu du HUD, donc crash du client.
    // Capacités à flag "ball/bomb" les plus jouées en compétitif (Pare-Balles)
    static final java.util.Set<String> CAPACITES_BALLE = java.util.Set.of(
        "shadowball", "sludgebomb", "aurasphere", "focusblast", "energyball",
        "electroball", "gyroball", "weatherball", "mudbomb", "octazooka",
        "eggbomb", "rockwrecker", "acidspray", "pyroball", "mistball",
        "pollenpuff", "beakblast", "barrage");

    // Capacités à flag "son" les plus jouées en compétitif (Anti-Bruit)
    static final java.util.Set<String> CAPACITES_SON = java.util.Set.of(
        "boomburst", "hypervoice", "bugbuzz", "roar", "screech",
        "sing", "supersonic", "growl", "snarl", "uproar",
        "eeriespell", "clangoroussoul", "disarmingvoice", "sparklingaria",
        "relicsong", "round", "chatter", "grasswhistle", "metalsound");

    Map<String, AbilityModifier> REGISTRE = construireRegistre();

    static AbilityModifier pour(String nomTalent) {
        if (nomTalent == null) {
            return null;
        }
        return REGISTRE.get(nomTalent);
    }

    private static Map<String, AbilityModifier> construireRegistre() {
        Map<String, AbilityModifier> m = new HashMap<>();

        m.put("Lévitation", immuniteContre(PokemonType.SOL, true));
        m.put("Absorbe-Terre", immuniteContre(PokemonType.SOL));
        m.put("Pare-Balles", immuniteContreCapacites(CAPACITES_BALLE));
        m.put("Anti-Bruit", immuniteContreCapacites(CAPACITES_SON));
        m.put("Absorb'Eau", immuniteContre(PokemonType.EAU));
        m.put("Absorb'Volt", immuniteContre(PokemonType.ELECTRIK));
        m.put("Lavabo", immuniteContre(PokemonType.EAU));
        m.put("Torche", immuniteContre(PokemonType.FEU));
        m.put("Paratonnerre", immuniteContre(PokemonType.ELECTRIK));
        m.put("Herbivore", immuniteContre(PokemonType.PLANTE));

        // Bien Cuit : immunité Feu totale (le boost Défense +2 associé,
        // effet de stage persistant, n'est pas modélisé ici)
        m.put("Bien Cuit", immuniteContre(PokemonType.FEU));

        // Sel Purificateur : résistance SUPPLÉMENTAIRE (x0.5 en plus de la
        // table de type normale) aux capacités Spectre, au-delà de la
        // résistance de type déjà calculée normalement
        m.put("Sel Purificateur", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.SPECTRE) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
            }
        });

        // Garde Mystik : seules les attaques super efficaces touchent
        // (indépendant du type de la capacité, contrairement aux immunités ci-dessus)
        m.put("Garde Mystik", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                double eff = ctx.capacite.getType().efficaciteContre(
                    ctx.defenseur.getTypeDefenseurEffectif1(), ctx.defenseur.getTypeDefenseurEffectif2());
                if (eff <= 1.0) {
                    ctx.immuniteType = true;
                }
            }
        });

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
        m.put("Prisme-Armure", reductionSuperEfficace);

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

        m.put("Agitation", new AbilityModifier() {
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

        AbilityModifier doubleAttaque = new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) {
                    ctx.multiplicateurAttaque *= 2.0;
                }
            }
        };
        m.put("Coloforce", doubleAttaque);
        m.put("Force Pure", doubleAttaque);

        m.put("Griffe Dure", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (com.tropimon.tropicalc.calc.ContactMoves.estContact(ctx.capacite.getNom())) {
                    ctx.multiplicateurDegatsFinal *= 1.3;
                }
            }
        });

        // Dent de Dragon (Regidrago) : +50% dégâts sur les capacités Dragon
        m.put("Dent de Dragon", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.DRAGON) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        m.put("Tranchant", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (com.tropimon.tropicalc.calc.MoveFlags.estTranchant(ctx.capacite.getNom())) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        m.put("Porte-Roche", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.ROCHE) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        // +30% en génération actuelle (était +50% en Gen 8 uniquement, réduit depuis)
        m.put("Transistor", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.ELECTRIK) {
                    ctx.multiplicateurDegatsFinal *= 1.3;
                }
            }
        });

        m.put("Sans Limite", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (SecondaryEffectMoves.aEffetSecondaire(ctx.capacite.getNom())) {
                    ctx.multiplicateurDegatsFinal *= 1.3;
                }
            }
        });

        m.put("Écailles Glacées", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
            }
        });

        m.put("Toison Épaisse", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                // Vraie mécanique : double la stat de Défense (pas un
                // multiplicateur final) — sans effet sur Choc Pied qui
                // utilise la Défense de l'ATTAQUANT, jamais celle-ci.
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) {
                    ctx.multiplicateurDefense *= 2.0;
                }
            }
        });

        m.put("Boule de Poils", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (com.tropimon.tropicalc.calc.ContactMoves.estContact(ctx.capacite.getNom())) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
                if (ctx.capacite.getType() == PokemonType.FEU) {
                    ctx.multiplicateurDegatsFinal *= 2.0;
                }
            }
        });

        m.put("Peau Sèche", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.EAU) {
                    ctx.immuniteType = true;   // absorbe et soigne (hors calcul de dégâts)
                } else if (ctx.capacite.getType() == PokemonType.FEU) {
                    ctx.multiplicateurDegatsFinal *= 1.25;
                }
            }
        });

        m.put("Aquabulle", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.EAU) {
                    ctx.multiplicateurDegatsFinal *= 2.0;
                }
            }

            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.FEU) {
                    ctx.multiplicateurDegatsFinal *= 0.5;
                }
            }
        });

        m.put("Seigneur Suprême", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                // +10% par coéquipier KO (max +50%, 5 coéquipiers). Le
                // porteur peut être le joueur ou l'adversaire : on compare
                // l'espèce de l'attaquant à celle du joueur actif pour
                // savoir de quelle équipe compter les KO.
                try {
                    Pokemon joueurActif = com.tropimon.tropicalc.battle.BattleStateTracker.getJoueurActifDepuisEquipe();
                    boolean estJoueur = joueurActif != null
                        && joueurActif.getEspece().equalsIgnoreCase(ctx.attaquant.getEspece());
                    java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> equipe = estJoueur
                        ? com.tropimon.tropicalc.battle.BattleStateTracker.getEquipeJoueur()
                        : com.tropimon.tropicalc.battle.BattleStateTracker.getEquipeAdversaire();
                    if (equipe == null) return;
                    int koCount = 0;
                    for (com.cobblemon.mod.common.pokemon.Pokemon p : equipe) {
                        if (p != null && p.isFainted()) koCount++;
                    }
                    ctx.multiplicateurDegatsFinal *= 1.0 + Math.min(5, koCount) * 0.1;
                } catch (Exception ignored) {
                }
            }
        });

        m.put("Rage Poison", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE
                        && (ctx.attaquant.getStatut() == Pokemon.Statut.POISON
                            || ctx.attaquant.getStatut() == Pokemon.Statut.POISON_GRAVE)) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        m.put("Rage Brûlure", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE
                        && ctx.attaquant.getStatut() == Pokemon.Statut.BRULURE) {
                    ctx.multiplicateurDegatsFinal *= 1.5;
                }
            }
        });

        return m;
    }

    private static AbilityModifier immuniteContre(PokemonType typeImmunise) {
        return immuniteContre(typeImmunise, false);
    }

    /**
     * @param percePariMilleFleches vrai uniquement pour Lévitation : Mille
     *                              Flèches ne perce QUE l'immunité liée au vol
     *                              (Vol/Lévitation), pas les immunités
     *                              d'absorption comme Absorb'Eau ou
     *                              Absorbe-Terre qui n'ont rien à voir.
     */
    private static AbilityModifier immuniteContre(PokemonType typeImmunise, boolean percePariMilleFleches) {
        return new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getType() == typeImmunise
                        && !(percePariMilleFleches && "thousandarrows".equals(ctx.capacite.getNom()))) {
                    ctx.immuniteType = true;
                }
            }
        };
    }

    private static AbilityModifier immuniteContreCapacites(java.util.Set<String> capacitesConcernees) {
        return new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (capacitesConcernees.contains(ctx.capacite.getNom())) {
                    ctx.immuniteType = true;
                }
            }
        };
    }
}
