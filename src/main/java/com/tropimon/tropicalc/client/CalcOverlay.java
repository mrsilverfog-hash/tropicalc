package com.tropimon.tropicalc.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.tropimon.tropicalc.battle.BattleStateTracker;
import com.tropimon.tropicalc.battle.BoostTracker;
import com.tropimon.tropicalc.battle.FieldTracker;
import com.tropimon.tropicalc.battle.ObservationCollector;
import com.tropimon.tropicalc.battle.TypeTracker;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ResidualProjector;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.SmogonDataLoader;
import com.tropimon.tropicalc.calc.Stat;
import com.tropimon.tropicalc.calc.StatHypothesis;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class CalcOverlay implements HudRenderCallback {

    private static final int COULEUR_TEXTE = 0xFFFFFF;
    private static final int COULEUR_KO = 0xFF5555;
    private static final int COULEUR_MOUCHOIR = 0xFF5599FF;   // bleu : hypothèse Mouchoir Choix
    private static final int COULEUR_TITRE = 0xFFD700;
    private static final int COULEUR_DANGER = 0xFF8800;
    private static final int COULEUR_REVELE = 0x55FF55;

    @Override
    public void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        // Doit tourner AUSSI hors combat : c'est là que le reset entre combats s'exécute
        ObservationCollector.tick();

        if (!BattleStateTracker.estEnCombat()) return;

        Pokemon adversaireBase = BattleStateTracker.getAdversaireActif();
        com.cobblemon.mod.common.pokemon.Pokemon monComplet = BattleStateTracker.getPokemonCompletJoueurAffichage();
        Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (joueur == null) joueur = BattleStateTracker.getJoueurActif();

        if (adversaireBase == null || joueur == null || monComplet == null) return;

        // Espèce stable pour la détection de switch : celle de l'équipe (ex: "Métamorph"),
        // PAS l'espèce copiée si transformé — sinon chaque transformation/changement de
        // cible copiée déclenche à tort une purge des boosts comme un vrai switch.
        String especeJoueurStable = joueur.getEspece();

        Pokemon adversaire = ObservationCollector.construireAdversaireEstime(adversaireBase);

        // Imposteur : les stats du joueur sont celles de la cible copiée,
        // mais les PV restent ceux de Métamorph
        if (BattleStateTracker.joueurEstTransforme()) {
            Pokemon statsDitto = joueur;
            Pokemon copie = ObservationCollector.construireAdversaireEstime(adversaireBase);
            copie.setPvMaxOverride(statsDitto.getPvMax());
            copie.setPvActuels(statsDitto.getPvActuels());
            copie.setStatut(statsDitto.getStatut());
            joueur = copie;
        }

        // Purge les stages si le Pokémon actif d'un camp a changé (switch)
        BoostTracker.verifierActifs(especeJoueurStable, adversaireBase.getEspece());

        // Boosts live des deux camps
        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                int stageAdv = BoostTracker.getStageAdversaire(s);
                if (stageAdv != 0) adversaire.setStage(s, stageAdv);
                int stageJoueur = BoostTracker.getStageJoueur(s);
                if (stageJoueur != 0) joueur.setStage(s, stageJoueur);
            }
        }

        // Types modifiés en combat (Détrempage, Protéen, Libéro)
        TypeTracker.appliquer(joueur, adversaire);

        MinecraftClient client = MinecraftClient.getInstance();
        int x = 8;
        int hauteurLigne = client.textRenderer.fontHeight + 2;

        // Le panneau a beaucoup grandi (Résiduel, Verrou Choix, durées, jusqu'à
        // 6 capacités adverses...) : estimation haute du nombre de lignes pour
        // garantir qu'il reste visible même sur petite résolution / GUI Scale élevée.
        int nbCapacitesJoueur = 0;
        for (Move coup : monComplet.getMoveSet()) {
            if (coup != null) nbCapacitesJoueur++;
        }
        int lignesEstimees = 2 + nbCapacitesJoueur + 1 + 6 + 1 + 2 + 1 + 2 + 3;
        int hauteurEstimee = lignesEstimees * hauteurLigne + 12;
        int scaledHeight = client.getWindow().getScaledHeight();
        int y = Math.min(170, Math.max(4, scaledHeight - hauteurEstimee));

        Field field = FieldTracker.construireField();

        // --- Section 1 : mes capacités ---
        String titre = BattleStateTracker.joueurEstTransforme()
            ? "TropiCalc [transformé]" : "TropiCalc";
        context.drawText(client.textRenderer, Text.literal(titre), x, y, COULEUR_TITRE, true);
        y += hauteurLigne + 2;

        // Vitesses effectives (Distorsion inverse la priorité)
        int vitJoueur = vitesseEffective(joueur);
        int vitAdversaire = Math.max(vitesseEffective(adversaire),
            ObservationCollector.getVitesseMinObservee(adversaireBase.getEspece()));
        boolean distorsion = FieldTracker.isDistorsion();
        boolean joueurPremier = distorsion ? vitJoueur < vitAdversaire : vitJoueur > vitAdversaire;
        boolean egalite = vitJoueur == vitAdversaire;
        String fleche = egalite ? "=" : (joueurPremier ? ">" : "<");
        int couleurVitesse = egalite ? COULEUR_TEXTE : (joueurPremier ? COULEUR_REVELE : COULEUR_KO);
        String suffixe = distorsion ? " [Distorsion]" : "";
        String texteVitesse = String.format("Vitesse : %d %s %d%s", vitJoueur, fleche, vitAdversaire, suffixe);
        context.drawText(client.textRenderer, Text.literal(texteVitesse), x, y, couleurVitesse, true);

        // Hypothèse Mouchoir Choix : sa vitesse x1.5 s'il en tenait un.
        // Affiché en bleu entre parenthèses tant que ce n'est pas déjà son objet
        // connu, pour anticiper le pire cas de priorité.
        String objetAdversaire = adversaire.getObjet();
        boolean mouchoirDejaPris = "Mouchoir Choix".equals(objetAdversaire)
            || "Écharpe Choix".equals(objetAdversaire);
        if (!mouchoirDejaPris) {
            int vitMouchoir = (int) Math.floor(vitAdversaire * 1.5);
            String texteMouchoir = String.format(" (%d)", vitMouchoir);
            int largeur = client.textRenderer.getWidth(texteVitesse);
            context.drawText(client.textRenderer, Text.literal(texteMouchoir),
                x + largeur, y, COULEUR_MOUCHOIR, true);
        }
        y += hauteurLigne;

        // Recul par contact : Casque Brut (~17%) + Épine de Fer / Peau Dure (12.5%)
        boolean objetAdvSur = ObservationCollector.estObjetConfirme(adversaireBase.getEspece());
        String talentAdv = adversaire.getTalent();
        boolean epines = "Épine de Fer".equals(talentAdv) || "Pic Acier".equals(talentAdv)
            || "Peau Dure".equals(talentAdv)
            || ObservationCollector.aChipTalentConfirme(adversaireBase.getEspece());
        boolean casqueBrut = "Casque Brut".equals(adversaire.getObjet());

        for (Move coup : monComplet.getMoveSet()) {
            if (coup == null) continue;
            com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(coup);
            if (capacite == null || capacite.estCapaciteDeStatut()) continue;

            DamageCalculator.Resultat r = DamageCalculator.calculer(joueur, adversaire, capacite, field, field.getEcransAdversaire(), false);
            String nom = coup.getDisplayName().getString();
            String ligne;
            int couleur = COULEUR_TEXTE;
            if (r.immunise) {
                ligne = nom + " : immunisé";
            } else {
                String nomCap = capacite.getNom();
                boolean frappePhysiqueDef = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE
                    || "psyshock".equals(nomCap) || "psystrike".equals(nomCap) || "secretsword".equals(nomCap);
                Stat statDef = frappePhysiqueDef ? Stat.DEFENSE : Stat.DEFENSE_SPE;
                String marqueur = adversaire.estCorrigee(statDef) ? "~" : "";
                ligne = String.format("%s : %s%.0f%% - %.0f%%", nom, marqueur, r.pourcentageMin, r.pourcentageMax);
                if ((casqueBrut || epines)
                        && com.tropimon.tropicalc.calc.ContactMoves.estContact(capacite.getNom())) {
                    int coups = DamageCalculator.nombreDeCoupsMax(capacite, joueur);
                    double recul = ((epines ? 100.0 / 8 : 0) + (casqueBrut ? 100.0 / 6 : 0)) * coups;
                    ligne += String.format(" | -%.0f%% toi%s", recul,
                        casqueBrut && !objetAdvSur ? "?" : "");
                }
                if (r.koGaranti) couleur = COULEUR_KO;
                else if (r.koPossible) couleur = 0xFFAA00;
            }
            context.drawText(client.textRenderer, Text.literal(ligne), x, y, couleur, true);
            y += hauteurLigne;
        }

        // --- Section 2 : capacités adverses (révélées + top Smogon) ---
        String especeAdv = ObservationCollector.getEspaceAdversaireCourant();
        if (especeAdv == null) especeAdv = adversaireBase.getEspece();

        SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(especeAdv);
        List<MoveTemplate> coupsReveles = ObservationCollector.getCoupsAdversaireReveles(especeAdv);

        LinkedHashSet<String> reveleIds = new LinkedHashSet<>();
        for (MoveTemplate t : coupsReveles) reveleIds.add(t.getName());

        List<MoveTemplate> aAfficher = new ArrayList<>(coupsReveles);
        if (smogon != null) {
            for (String moveId : smogon.topMovesShowdownId()) {
                if (!reveleIds.contains(moveId)) {
                    MoveTemplate t = Moves.INSTANCE.getByName(moveId);
                    if (t != null) aAfficher.add(t);
                }
            }
        }

        if (!aAfficher.isEmpty()) {
            y += 4;
            context.drawText(client.textRenderer, Text.literal("Capacités adverses :"), x, y, COULEUR_DANGER, true);
            y += hauteurLigne;

            // Verrou Choix : objet Choix + un coup déjà utilisé depuis son entrée
            String objetAdv = adversaire.getObjet();
            String verrou = ObservationCollector.getCoupVerrouAdversaire();
            if (objetAdv != null && objetAdv.contains("Choix") && verrou != null) {
                MoveTemplate tv = Moves.INSTANCE.getByName(verrou);
                String nomVerrou = tv != null ? tv.getDisplayName().getString() : verrou;
                boolean sur = ObservationCollector.estObjetConfirme(adversaireBase.getEspece());
                context.drawText(client.textRenderer,
                    Text.literal(String.format("Verrou Choix%s : %s", sur ? "" : "?", nomVerrou)),
                    x, y, 0xFFAA00, true);
                y += hauteurLigne;
            }

            // Abris consécutifs : le suivant a 1/3^n de chances de réussir
            int abris = ObservationCollector.getCompteurAbrisAdversaire();
            if (false && abris >= 1) {
                double chance = 100.0 / Math.pow(3, abris);
                context.drawText(client.textRenderer,
                    Text.literal(String.format("Abri x%d → prochain ~%.0f%%", abris, chance)),
                    x, y, COULEUR_TEXTE, true);
                y += hauteurLigne;
            }
            y -= hauteurLigne;
            y += hauteurLigne;

            for (MoveTemplate template : aAfficher) {
                boolean estRevele = reveleIds.contains(template.getName());
                com.tropimon.tropicalc.calc.Move capaciteAdv = convertirTemplate(template);
                String nom = template.getDisplayName().getString();
                String ligne;
                int couleur = estRevele ? COULEUR_REVELE : COULEUR_TEXTE;

                // PP restants (max compétitif = base x1.6 avec PP Max)
                String suffixePp = "";
                if (estRevele) {
                    int max = (int) Math.floor(template.getPp() * 1.6);
                    int restants = Math.max(0, max - ObservationCollector.getPpUtilises(especeAdv, template.getName()));
                    suffixePp = String.format(" | PP %d/%d", restants, max);
                }

                if (capaciteAdv == null || capaciteAdv.estCapaciteDeStatut()) {
                    ligne = (estRevele ? "✓ " : "") + nom + " : statut" + suffixePp;
                } else {
                    DamageCalculator.Resultat r = DamageCalculator.calculer(adversaire, joueur, capaciteAdv, field, field.getEcransJoueur(), false);
                    if (r.immunise) {
                        ligne = (estRevele ? "✓ " : "") + nom + " : immunisé" + suffixePp;
                    } else {
                        Stat statAtk = capaciteAdv.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE
                            ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
                        String marq = adversaire.estCorrigee(statAtk) ? "~" : "";
                        ligne = String.format("%s%s : %s%.0f%% - %.0f%%%s",
                            estRevele ? "✓ " : "", nom, marq, r.pourcentageMin, r.pourcentageMax, suffixePp);
                        if (r.koGaranti) couleur = COULEUR_KO;
                        else if (r.koPossible && !estRevele) couleur = 0xFFAA00;

                        // Hypothèse objet offensif quasi-certain (> 50% d'usage Smogon) :
                        // fourchette de dégâts SI l'adversaire tenait cet objet, en bleu.
                        // Uniquement si l'objet n'est pas déjà un fait confirmé (sinon
                        // le calcul principal l'inclut déjà).
                        String ligneHypo = ligneHypotheseObjet(adversaire, joueur, capaciteAdv, field,
                            especeAdv, smogon, statAtk == Stat.ATTAQUE);
                        if (ligneHypo != null) {
                            context.drawText(client.textRenderer, Text.literal(ligne), x, y, couleur, true);
                            int largeurLigne = client.textRenderer.getWidth(ligne);
                            context.drawText(client.textRenderer, Text.literal(ligneHypo),
                                x + largeurLigne, y, COULEUR_MOUCHOIR, true);
                            y += hauteurLigne;
                            continue;
                        }
                    }
                }
                context.drawText(client.textRenderer, Text.literal(ligne), x, y, couleur, true);
                y += hauteurLigne;
            }
        }

        // --- Projection des dégâts résiduels adverses (cœur du stall) ---
        boolean objetSur = ObservationCollector.estObjetConfirme(adversaireBase.getEspece());
        ResidualProjector.Projection proj = ResidualProjector.projeter(adversaire, field.getMeteo(), objetSur,
            ObservationCollector.getCompteurToxikProchainAdversaire(),
            ObservationCollector.isAdversaireSalaison(),
            ObservationCollector.isAdversaireVampigraine());
        if (proj != null) {
            y += 4;
            String ligneProj;
            int couleurProj;
            if (proj.netPremierTourPct() > 0) {
                ligneProj = proj.toursAvantKO() > 0
                    ? String.format("Résiduel : -%.0f%%/t (%s) → KO ~%d tours",
                        proj.netPremierTourPct(), proj.detail(), proj.toursAvantKO())
                    : String.format("Résiduel : -%.0f%%/t (%s)",
                        proj.netPremierTourPct(), proj.detail());
                couleurProj = COULEUR_REVELE;
            } else {
                ligneProj = String.format("Résiduel : +%.0f%%/t (%s)%s",
                    -proj.netPremierTourPct(), proj.detail(),
                    objetSur ? " : régénère" : "");
                couleurProj = objetSur ? 0xFFAA00 : COULEUR_TEXTE;
            }
            context.drawText(client.textRenderer, Text.literal(ligneProj), x, y, couleurProj, true);
            y += hauteurLigne;
        }

        // --- Projection résiduelle du joueur : anticiper sa propre mort ---
        // L'objet et le statut du joueur sont réels, jamais estimés
        ResidualProjector.Projection projJoueur = ResidualProjector.projeter(joueur, field.getMeteo(), true,
            ObservationCollector.getCompteurToxikProchainJoueur(),
            ObservationCollector.isJoueurSalaison(),
            ObservationCollector.isJoueurVampigraine());
        if (projJoueur != null) {
            if (proj == null) y += 4;
            String ligneToi;
            int couleurToi;
            if (projJoueur.netPremierTourPct() > 0) {
                ligneToi = projJoueur.toursAvantKO() > 0
                    ? String.format("Résiduel toi : -%.0f%%/t (%s) → KO ~%d tours",
                        projJoueur.netPremierTourPct(), projJoueur.detail(), projJoueur.toursAvantKO())
                    : String.format("Résiduel toi : -%.0f%%/t (%s)",
                        projJoueur.netPremierTourPct(), projJoueur.detail());
                couleurToi = projJoueur.toursAvantKO() > 0 && projJoueur.toursAvantKO() <= 2
                    ? COULEUR_KO : 0xFFAA00;
            } else {
                ligneToi = String.format("Résiduel toi : +%.0f%%/t (%s)",
                    -projJoueur.netPremierTourPct(), projJoueur.detail());
                couleurToi = COULEUR_REVELE;
            }
            context.drawText(client.textRenderer, Text.literal(ligneToi), x, y, couleurToi, true);
            y += hauteurLigne;
        }

        // --- Durées : météo et écrans adverses (hypothèse basse 5 tours) ---
        StringBuilder durees = new StringBuilder();
        if (field.getMeteo() != com.tropimon.tropicalc.calc.Field.Meteo.AUCUNE
                && FieldTracker.getToursMeteoRestants() > 0) {
            durees.append(String.format("Météo : ~%dt", FieldTracker.getToursMeteoRestants()));
        }
        if (FieldTracker.adversaireAUnEcran() && FieldTracker.getToursEcransAdversaireRestants() > 0) {
            if (durees.length() > 0) durees.append(" | ");
            durees.append(String.format("Écrans adv : ~%dt", FieldTracker.getToursEcransAdversaireRestants()));
        }
        if (durees.length() > 0) {
            context.drawText(client.textRenderer, Text.literal(durees.toString()), x, y, COULEUR_TEXTE, true);
            y += hauteurLigne;
        }

        // --- Section 3 : set estimé ---
        if (smogon != null && !smogon.topSpreads().isEmpty()) {
            y += 4;
            SmogonDataLoader.ParsedSpread top = smogon.topSpreads().get(0);
            context.drawText(client.textRenderer, Text.literal("Set estimé :"), x, y, COULEUR_TITRE, true);
            y += hauteurLigne;
            context.drawText(client.textRenderer,
                Text.literal(String.format("HP %d | Def %d | DéfSpé %d | %s",
                    top.hpEv(), top.defEv(), top.spdEv(), top.natureShowdownId())),
                x, y, COULEUR_TEXTE, true);
            y += hauteurLigne;

            com.tropimon.tropicalc.calc.ProfilAdversaire profil = ObservationCollector.getProfil(especeAdv);
            if (profil != null && profil.getNbObservations() >= 3) {
                StatHypothesis hypDef = profil.defense.nombreObservations >= profil.defenseSpe.nombreObservations
                    ? profil.defense : profil.defenseSpe;
                context.drawText(client.textRenderer,
                    Text.literal(String.format("Inférence Def EV %d-%d", hypDef.evMin, hypDef.evMax)),
                    x, y, COULEUR_TEXTE, true);
                y += hauteurLigne;
            }

            // Objet : uniquement affiché quand on SAIT (jamais une supposition).
            // Casque Brut / Restes : détectés par le motif de chip/soin.
            // Écharpe Choix : il agit avant alors que sa vitesse max sans objet
            // ne le permettrait pas. Bandeau/Lunettes Choix : ratio net x1.5.
            // Orbe Vie : ratio net x1.3 + son propre recul de ~10% le même tour.
            String objetConfirme = ObservationCollector.getObjetConfirme(especeAdv);
            boolean objetRetire = ObservationCollector.estObjetConfirme(especeAdv) && objetConfirme == null;
            if (objetConfirme != null) {
                context.drawText(client.textRenderer,
                    Text.literal("Objet confirmé : " + objetConfirme), x, y, COULEUR_REVELE, true);
                y += hauteurLigne;
            } else if (objetRetire) {
                context.drawText(client.textRenderer,
                    Text.literal("Objet confirmé : aucun (Sabotage)"), x, y, COULEUR_REVELE, true);
                y += hauteurLigne;
            }
        }
    }

    /**
     * Si le set Smogon de cette espèce joue un objet offensif (Orbe Vie,
     * Bandeau/Lunettes Choix) plus de 50% du temps, et que ce n'est pas déjà
     * un fait confirmé, retourne la fourchette de dégâts hypothétique avec
     * cet objet — sinon null. Ne mute pas durablement le Pokémon (objet
     * restauré après le calcul).
     */
    private static String ligneHypotheseObjet(Pokemon adversaire, Pokemon joueur,
                                               com.tropimon.tropicalc.calc.Move capacite, Field field,
                                               String espece, SmogonDataLoader.SmogonPokemonData smogon,
                                               boolean estPhysique) {
        if (smogon == null || smogon.topItemsShowdownId().isEmpty()) return null;
        if (smogon.topItemUsageFraction() < 0.5) return null;
        if (ObservationCollector.estObjetConfirme(espece)) return null;   // déjà un fait connu

        String objetProbable = com.tropimon.tropicalc.calc.ShowdownIdMapper.objet(smogon.topItemsShowdownId().get(0));
        if (objetProbable == null) return null;
        boolean estOrbeVie = "Orbe Vie".equals(objetProbable);
        boolean estBandeau = "Bandeau Choix".equals(objetProbable);
        boolean estLunettes = "Lunettes Choix".equals(objetProbable);
        if (!estOrbeVie && !estBandeau && !estLunettes) return null;
        if (estBandeau && !estPhysique) return null;
        if (estLunettes && estPhysique) return null;

        // Déjà l'objet actif : le range normal l'inclut déjà, pas la peine de redire
        if (objetProbable.equals(adversaire.getObjet())) return null;

        String objetOriginal = adversaire.getObjet();
        adversaire.setObjet(objetProbable);
        DamageCalculator.Resultat hypo = DamageCalculator.calculer(adversaire, joueur, capacite, field, field.getEcransJoueur(), false);
        adversaire.setObjet(objetOriginal);

        if (hypo.immunise) return null;
        return String.format(" (%.0f%% - %.0f%%)", hypo.pourcentageMin, hypo.pourcentageMax);
    }

    private static int vitesseEffective(Pokemon p) {
        Field f = FieldTracker.construireField();
        return (int) DamageCalculator.vitesseEnCombat(p, f.getMeteo(), f.getTerrain());
    }

    private com.tropimon.tropicalc.calc.Move convertirCapacite(Move coup) {
        PokemonType type = ShowdownIdMapper.type(coup.getType().getName());
        if (type == null) return null;
        String cat = coup.getDamageCategory().getName();
        com.tropimon.tropicalc.calc.Move.Categorie categorie;
        if ("physical".equalsIgnoreCase(cat)) categorie = com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        else if ("special".equalsIgnoreCase(cat)) categorie = com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE;
        else categorie = com.tropimon.tropicalc.calc.Move.Categorie.STATUT;
        return com.tropimon.tropicalc.calc.Move.builder(coup.getName(), type, categorie)
            .puissance((int) coup.getPower())
            
            .poing(com.tropimon.tropicalc.calc.MoveFlags.estPoing(coup.getName()))
            .morsure(com.tropimon.tropicalc.calc.MoveFlags.estMorsure(coup.getName()))
            .build();
    }

    private com.tropimon.tropicalc.calc.Move convertirTemplate(MoveTemplate template) {
        PokemonType type = ShowdownIdMapper.type(template.getElementalType().getName());
        if (type == null) return null;
        String cat = template.getDamageCategory().getName();
        com.tropimon.tropicalc.calc.Move.Categorie categorie;
        if ("physical".equalsIgnoreCase(cat)) categorie = com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        else if ("special".equalsIgnoreCase(cat)) categorie = com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE;
        else categorie = com.tropimon.tropicalc.calc.Move.Categorie.STATUT;
        return com.tropimon.tropicalc.calc.Move.builder(template.getName(), type, categorie)
            .puissance((int) template.getPower())
            
            .poing(com.tropimon.tropicalc.calc.MoveFlags.estPoing(template.getName()))
            .morsure(com.tropimon.tropicalc.calc.MoveFlags.estMorsure(template.getName()))
            .build();
    }
}
