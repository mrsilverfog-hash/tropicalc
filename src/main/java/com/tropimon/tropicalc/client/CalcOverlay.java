package com.tropimon.tropicalc.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.tropimon.tropicalc.battle.BattleStateTracker;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Overlay affiché automatiquement pendant un combat Cobblemon (format Simple
 * uniquement), listant les dégâts estimés min/max de chaque capacité du
 * Pokémon actif du joueur contre le Pokémon actif adverse.
 *
 * Limitation actuelle : ne prend pas encore en compte la météo/terrain réels
 * du combat (Field neutre utilisé), ni les écrans (Protection/Mur Lumière).
 * Ce sera ajouté une fois la synchronisation de l'état du terrain branchée.
 */
public final class CalcOverlay implements HudRenderCallback {

    private static final int COULEUR_TEXTE = 0xFFFFFF;
    private static final int COULEUR_KO = 0xFF5555;
    private static final int COULEUR_TITRE = 0xFFD700;

    @Override
    public void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!BattleStateTracker.estEnCombat()) {
            return;
        }

        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        com.cobblemon.mod.common.pokemon.Pokemon monComplet = BattleStateTracker.getPokemonCompletJoueur();
        Pokemon joueur = BattleStateTracker.getJoueurActif();

        if (adversaire == null || joueur == null || monComplet == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int x = 8;
        int y = 170;
        int hauteurLigne = client.textRenderer.fontHeight + 2;

        context.drawText(client.textRenderer, Text.literal("TropiCalc"), x, y, COULEUR_TITRE, true);
        y += hauteurLigne + 2;

        Field field = new Field();

        for (Move coup : monComplet.getMoveSet()) {
            if (coup == null) {
                continue;
            }
            com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(coup);
            if (capacite == null || capacite.estCapaciteDeStatut()) {
                continue;
            }

            DamageCalculator.Resultat resultat = DamageCalculator.calculer(joueur, adversaire, capacite, field, null, false);
            String nomAffiche = coup.getDisplayName().getString();

            String ligne;
            int couleur = COULEUR_TEXTE;
            if (resultat.immunise) {
                ligne = nomAffiche + " : immunisé";
            } else {
                ligne = String.format("%s : %.0f%% - %.0f%%", nomAffiche, resultat.pourcentageMin, resultat.pourcentageMax);
                if (resultat.koPossible) {
                    couleur = COULEUR_KO;
                }
            }

            context.drawText(client.textRenderer, Text.literal(ligne), x, y, couleur, true);
            y += hauteurLigne;
        }
    }

    private com.tropimon.tropicalc.calc.Move convertirCapacite(Move coup) {
        PokemonType type = ShowdownIdMapper.type(coup.getType().getName());
        if (type == null) {
            return null;
        }
        String categorieNom = coup.getDamageCategory().getName();
        com.tropimon.tropicalc.calc.Move.Categorie categorie;
        if ("physical".equalsIgnoreCase(categorieNom)) {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        } else if ("special".equalsIgnoreCase(categorieNom)) {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE;
        } else {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.STATUT;
        }

        return com.tropimon.tropicalc.calc.Move.builder(coup.getName(), type, categorie)
            .puissance((int) coup.getPower())
            .build();
    }
}
