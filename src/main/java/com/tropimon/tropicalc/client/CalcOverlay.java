package com.tropimon.tropicalc.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.tropimon.tropicalc.battle.BattleStateTracker;
import com.tropimon.tropicalc.battle.BoostTracker;
import com.tropimon.tropicalc.battle.FieldTracker;
import com.tropimon.tropicalc.battle.ObservationCollector;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
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
        if (!BattleStateTracker.estEnCombat()) return;

        Pokemon adversaireBase = BattleStateTracker.getAdversaireActif();
        com.cobblemon.mod.common.pokemon.Pokemon monComplet = BattleStateTracker.getPokemonCompletJoueur();
        Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (joueur == null) joueur = BattleStateTracker.getJoueurActif();

        if (adversaireBase == null || joueur == null || monComplet == null) return;

        ObservationCollector.tick();

        Pokemon adversaire = ObservationCollector.construireAdversaireEstime(adversaireBase);

        // Boosts live des deux camps
        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                int stageAdv = BoostTracker.getStageAdversaire(s);
                if (stageAdv != 0) adversaire.setStage(s, stageAdv);
                int stageJoueur = BoostTracker.getStageJoueur(s);
                if (stageJoueur != 0) joueur.setStage(s, stageJoueur);
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int x = 8;
        int y = 170;
        int hauteurLigne = client.textRenderer.fontHeight + 2;
        Field field = FieldTracker.construireField();

        // --- Section 1 : mes capacités ---
        context.drawText(client.textRenderer, Text.literal("TropiCalc"), x, y, COULEUR_TITRE, true);
        y += hauteurLigne + 2;

        // Vitesses effectives
        int vitJoueur = vitesseEffective(joueur);
        int vitAdversaire = vitesseEffective(adversaire);
        String fleche = vitJoueur > vitAdversaire ? ">" : (vitJoueur < vitAdversaire ? "<" : "=");
        int couleurVitesse = vitJoueur > vitAdversaire ? COULEUR_REVELE
            : (vitJoueur < vitAdversaire ? COULEUR_KO : COULEUR_TEXTE);
        context.drawText(client.textRenderer,
            Text.literal(String.format("Vitesse : %d %s %d", vitJoueur, fleche, vitAdversaire)),
            x, y, couleurVitesse, true);
        y += hauteurLigne;

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

            for (MoveTemplate template : aAfficher) {
                boolean estRevele = reveleIds.contains(template.getName());
                com.tropimon.tropicalc.calc.Move capaciteAdv = convertirTemplate(template);
                String nom = template.getDisplayName().getString();
                String ligne;
                int couleur = estRevele ? COULEUR_REVELE : COULEUR_TEXTE;

                if (capaciteAdv == null || capaciteAdv.estCapaciteDeStatut()) {
                    ligne = (estRevele ? "✓ " : "") + nom + " : statut";
                } else {
                    DamageCalculator.Resultat r = DamageCalculator.calculer(adversaire, joueur, capaciteAdv, field, field.getEcransJoueur(), false);
                    if (r.immunise) {
                        ligne = (estRevele ? "✓ " : "") + nom + " : immunisé";
                    } else {
                        ligne = String.format("%s%s : %.0f%% - %.0f%%",
                            estRevele ? "✓ " : "", nom, r.pourcentageMin, r.pourcentageMax);
                        if (r.koGaranti) couleur = COULEUR_KO;
                        else if (r.koPossible && !estRevele) couleur = 0xFFAA00;
                    }
                }
                context.drawText(client.textRenderer, Text.literal(ligne), x, y, couleur, true);
                y += hauteurLigne;
            }
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
