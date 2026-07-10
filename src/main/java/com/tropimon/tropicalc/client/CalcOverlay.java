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
    private static final int COULEUR_TITRE = 0xFFD700;
    private static final int COULEUR_DANGER = 0xFF8800;
    private static final int COULEUR_REVELE = 0x55FF55;

    @Override
    public void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        // Doit tourner AUSSI hors combat : c'est là que le reset entre combats s'exécute
        ObservationCollector.tick();

        if (!BattleStateTracker.estEnCombat()) return;

        Pokemon adversaireBase = BattleStateTracker.getAdversaireActif();
        com.cobblemon.mod.common.pokemon.Pokemon monComplet = BattleStateTracker.getPokemonCompletJoueur();
        Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (joueur == null) joueur = BattleStateTracker.getJoueurActif();

        if (adversaireBase == null || joueur == null || monComplet == null) return;

        Pokemon adversaire = ObservationCollector.construireAdversaireEstime(adversaireBase);

        // Purge les stages si le Pokémon actif d'un camp a changé (switch)
        BoostTracker.verifierActifs(joueur.getEspece(), adversaireBase.getEspece());

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
        int y = 170;
        int hauteurLigne = client.textRenderer.fontHeight + 2;
        Field field = FieldTracker.construireField();

        // --- Section 1 : mes capacités ---
        context.drawText(client.textRenderer, Text.literal("TropiCalc"), x, y, COULEUR_TITRE, true);
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
        context.drawText(client.textRenderer,
            Text.literal(String.format("Vitesse : %d %s %d%s", vitJoueur, fleche, vitAdversaire, suffixe)),
            x, y, couleurVitesse, true);
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
                ligne = String.format("%s : %.0f%% - %.0f%%", nom, r.pourcentageMin, r.pourcentageMax);
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
                        ligne = String.format("%s%s : %.0f%% - %.0f%%%s",
                            estRevele ? "✓ " : "", nom, r.pourcentageMin, r.pourcentageMax, suffixePp);
                        if (r.koGaranti) couleur = COULEUR_KO;
                        else if (r.koPossible && !estRevele) couleur = 0xFFAA00;
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
                    Text.literal(String.format("Inférence Def EV %d-%d | Objets : %s",
                        hypDef.evMin, hypDef.evMax, hypDef.objetsPossibles)),
                    x, y, COULEUR_TEXTE, true);
                y += hauteurLigne;
            }
        }
    }

    private static int vitesseEffective(Pokemon p) {
        double v = p.getStatCalculee(Stat.VITESSE);
        int stage = p.getStage(Stat.VITESSE);
        if (stage >= 0) v = v * (2.0 + stage) / 2.0;
        else v = v * 2.0 / (2.0 - stage);
        if ("Écharpe Choix".equals(p.getObjet())) v *= 1.5;
        if (p.getStatut() == Pokemon.Statut.PARALYSIE) v *= 0.5;
        return (int) Math.floor(v);
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
            .build();
    }
}
