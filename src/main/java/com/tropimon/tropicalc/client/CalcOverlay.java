package com.tropimon.tropicalc.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.tropimon.tropicalc.battle.BattleStateTracker;
import com.tropimon.tropicalc.battle.ObservationCollector;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.StatHypothesis;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public final class CalcOverlay implements HudRenderCallback {

    private static final int COULEUR_TEXTE = 0xFFFFFF;
    private static final int COULEUR_KO = 0xFF5555;
    private static final int COULEUR_TITRE = 0xFFD700;
    private static final int COULEUR_DANGER = 0xFF8800;

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

        MinecraftClient client = MinecraftClient.getInstance();
        int x = 8;
        int y = 170;
        int hauteurLigne = client.textRenderer.fontHeight + 2;
        Field field = new Field();

        context.drawText(client.textRenderer, Text.literal("TropiCalc"), x, y, COULEUR_TITRE, true);
        y += hauteurLigne + 2;

        for (Move coup : monComplet.getMoveSet()) {
            if (coup == null) continue;
            com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(coup);
            if (capacite == null || capacite.estCapaciteDeStatut()) continue;

            DamageCalculator.Resultat r = DamageCalculator.calculer(joueur, adversaire, capacite, field, null, false);
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

        String especeAdv = ObservationCollector.getEspaceAdversaireCourant();
        if (especeAdv == null) especeAdv = adversaireBase.getEspece();

        List<MoveTemplate> coupsAdv = ObservationCollector.getCoupsAdversaireReveles(especeAdv);
        if (!coupsAdv.isEmpty()) {
            y += 4;
            context.drawText(client.textRenderer, Text.literal("Attaques adverses :"), x, y, COULEUR_DANGER, true);
            y += hauteurLigne;

            for (MoveTemplate template : coupsAdv) {
                com.tropimon.tropicalc.calc.Move capaciteAdv = convertirTemplate(template);
                if (capaciteAdv == null || capaciteAdv.estCapaciteDeStatut()) continue;

                DamageCalculator.Resultat r = DamageCalculator.calculer(adversaire, joueur, capaciteAdv, field, null, false);
                String nom = template.getDisplayName().getString();

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
        }

        com.tropimon.tropicalc.calc.ProfilAdversaire profil = ObservationCollector.getProfil(especeAdv);
        if (profil != null) {
            y += 4;
            context.drawText(client.textRenderer, Text.literal("Inférence :"), x, y, COULEUR_TITRE, true);
            y += hauteurLigne;

            StatHypothesis hypAtk = profil.attaque.nombreObservations >= profil.attaqueSpe.nombreObservations
                ? profil.attaque : profil.attaqueSpe;
            StatHypothesis hypDef = profil.defense.nombreObservations >= profil.defenseSpe.nombreObservations
                ? profil.defense : profil.defenseSpe;

            context.drawText(client.textRenderer,
                Text.literal(String.format("Atk EV %d-%d | Def EV %d-%d",
                    hypAtk.evMin, hypAtk.evMax, hypDef.evMin, hypDef.evMax)),
                x, y, COULEUR_TEXTE, true);
            y += hauteurLigne;
            context.drawText(client.textRenderer,
                Text.literal("Objets : " + hypAtk.objetsPossibles),
                x, y, COULEUR_TEXTE, true);
        }
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
