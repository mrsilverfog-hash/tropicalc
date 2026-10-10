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
        "pollenpuff", "beakblast", "barrage", "bulletseed", "rockblast");

    // Capacités à flag "son" les plus jouées en compétitif (Anti-Bruit)
    static final java.util.Set<String> CAPACITES_SON = java.util.Set.of(
        "boomburst", "hypervoice", "bugbuzz", "roar", "screech",
        "sing", "supersonic", "growl", "snarl", "uproar",
        "eeriespell", "clangoroussoul", "disarmingvoice", "sparklingaria",
        "relicsong", "round", "chatter", "grasswhistle", "metalsound",
        "perishsong", "partingshot", "echoedvoice", "torchsong", "overdrive",
        "alluringvoice", "psychicnoise", "clangingscales", "snore", "nobleroar", "howl", "healbell");

    // Capacités "pulsation / aura" (Méga Blaster)
    static final java.util.Set<String> CAPACITES_PULSATION = java.util.Set.of(
        "aurasphere", "darkpulse", "dragonpulse", "waterpulse", "originpulse", "terrainpulse", "healpulse");

    // Capacités à contrecoup ou à chute (Téméraire)
    static final java.util.Set<String> CAPACITES_RECUL = java.util.Set.of(
        "bravebird", "doubleedge", "flareblitz", "headsmash", "volttackle", "woodhammer", "wildcharge",
        "headlongrush", "wavecrash", "takedown", "submission", "headcharge", "lightofruin",
        "highjumpkick", "jumpkick", "axekick", "supercellslam", "chloroblast");

    Map<String, AbilityModifier> REGISTRE = construireRegistre();

    static AbilityModifier pour(String nomTalent) {
        if (nomTalent == null) {
            return null;
        }
        return REGISTRE.get(nomTalent);
    }

    /** x1.5 sur la stat offensive pour les capacités du type, à 1/3 des PV ou moins. */
    private static AbilityModifier boostSousUnTiers(PokemonType type) {
        return new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getType() == type
                        && ctx.attaquant.getPvActuels() * 3 <= ctx.attaquant.getPvMax()) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        };
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
        m.put("Multiécaille", demiDegatsPleinePv);
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

        // --- Talents ajoutés (effets génération 9, valeurs de Showdown) ---

        // Engrais / Brasier / Torrent / Essaim : x1.5 sur l'Attaque ou l'Attaque
        // Spé pour les capacités du type, à 1/3 des PV ou moins.
        m.put("Engrais", boostSousUnTiers(PokemonType.PLANTE));
        m.put("Brasier", boostSousUnTiers(PokemonType.FEU));
        m.put("Torrent", boostSousUnTiers(PokemonType.EAU));
        m.put("Essaim", boostSousUnTiers(PokemonType.INSECTE));

        // Téméraire : x1.2 sur les capacités à contrecoup ou à chute.
        m.put("Téméraire", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (CAPACITES_RECUL.contains(ctx.capacite.getNom())) ctx.multiplicateurDegatsFinal *= 4915.0 / 4096.0;
            }
        });

        // Méga Blaster : x1.5 sur les capacités pulsation / aura.
        m.put("Méga Blaster", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (CAPACITES_PULSATION.contains(ctx.capacite.getNom())) ctx.multiplicateurDegatsFinal *= 1.5;
            }
        });

        // Entêtement : Attaque x1.5 (capacités physiques).
        m.put("Entêtement", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE) ctx.multiplicateurAttaque *= 1.5;
            }
        });

        // Force Soleil : Attaque Spé x1.5 sous le soleil.
        m.put("Force Soleil", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                Field.Meteo me = ctx.terrain.getMeteo();
                if (ctx.capacite.getCategorie() == Move.Categorie.SPECIALE
                        && (me == Field.Meteo.SOLEIL || me == Field.Meteo.SOLEIL_INTENSE)) {
                    ctx.multiplicateurAttaque *= 1.5;
                }
            }
        });

        // Sniper : un coup critique fait x2.25 au lieu de x1.5.
        m.put("Sniper", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.critique) ctx.multiplicateurDegatsFinal *= 1.5;
            }
        });

        // Expert Acier : x1.5 sur les capacités Acier. Boost Acier : idem.
        AbilityModifier acier = new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.ACIER) ctx.multiplicateurAttaque *= 1.5;
            }
        };
        m.put("Expert Acier", acier);
        m.put("Boost Acier", acier);

        // Punk Rock : capacités sonores x1.3 en attaque, x0.5 en défense.
        m.put("Punk Rock", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                if (CAPACITES_SON.contains(ctx.capacite.getNom())) ctx.multiplicateurDegatsFinal *= 5325.0 / 4096.0;
            }

            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (CAPACITES_SON.contains(ctx.capacite.getNom())) ctx.multiplicateurDegatsFinal *= 0.5;
            }
        });

        // Ignifugé : dégâts Feu divisés par 2.
        m.put("Ignifugé", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getType() == PokemonType.FEU) ctx.multiplicateurDegatsFinal *= 0.5;
            }
        });

        // Écaille Spéciale : Défense x1.5 sous un statut.
        m.put("Écaille Spéciale", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE
                        && ctx.defenseur.getStatut() != Pokemon.Statut.AUCUN) {
                    ctx.multiplicateurDefense *= 1.5;
                }
            }
        });

        // Toison Herbue : Défense x1.5 sous Champ Herbu.
        m.put("Toison Herbue", new AbilityModifier() {
            @Override
            public void appliquerCoteDefenseur(ModifierContext ctx) {
                if (ctx.capacite.getCategorie() == Move.Categorie.PHYSIQUE
                        && ctx.terrain.getTerrain() == Field.TypeTerrain.HERBU) {
                    ctx.multiplicateurDefense *= 1.5;
                }
            }
        });

        // Peau Céleste/Féérique/Gelée/Électrique, Normalise, Hydrata-Son : le
        // changement de type et le x1.2 sont appliqués dans DamageCalculator
        // (capaciteApresTalent), avant le calcul du type et du STAB.
        // Cérébro-Force : x1.25 sur un coup super efficace, dans
        // DamageCalculator.appliquerModificateursConditionnels.

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

        m.put("Prognathe", new AbilityModifier() {
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

        m.put("Lentiteintée", new AbilityModifier() {
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

        m.put("Général Suprême", new AbilityModifier() {
            @Override
            public void appliquerCoteAttaquant(ModifierContext ctx) {
                // Puissance augmentée selon les coéquipiers déjà K.O. dans
                // l'équipe du PORTEUR, plafonnée à 5. Ratios exacts du jeu
                // (Showdown) : 4096 / 4506 / 4915 / 5325 / 5734 / 6144 sur 4096.
                //
                // Camp lu sur la marque posée à la construction, plus deviné
                // par l'espèce du Pokémon actif : cette devinette comptait mes
                // K.O. pour un Scalpereur adverse en miroir, et ceux de
                // l'adversaire pour un Scalpereur de mon banc (écran de switch).
                //
                // Adversaire : K.O. comptés depuis cobblemon.battle.fainted
                // (Cobblemon ne fournit pas son équipe complète côté client,
                // l'ancienne lecture renvoyait toujours 0). Joueur : le plus
                // grand des deux comptes (équipe complète, messages).
                try {
                    int koCount;
                    if (ctx.attaquant.isCampAdverse()) {
                        koCount = com.tropimon.tropicalc.battle.ObservationCollector.getNombreKoAdversaire();
                    } else {
                        koCount = com.tropimon.tropicalc.battle.ObservationCollector.getNombreKoJoueur();
                        java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> equipe =
                            com.tropimon.tropicalc.battle.BattleStateTracker.getEquipeJoueur();
                        if (equipe != null) {
                            int koEquipe = 0;
                            for (com.cobblemon.mod.common.pokemon.Pokemon p : equipe) {
                                if (p != null && p.isFainted()) koEquipe++;
                            }
                            koCount = Math.max(koCount, koEquipe);
                        }
                    }
                    final double[] ratios = {4096, 4506, 4915, 5325, 5734, 6144};
                    ctx.multiplicateurDegatsFinal *= ratios[Math.max(0, Math.min(5, koCount))] / 4096.0;
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
