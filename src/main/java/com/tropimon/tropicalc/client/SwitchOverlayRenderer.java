package com.tropimon.tropicalc.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.tropimon.tropicalc.battle.BattleStateTracker;
import com.tropimon.tropicalc.battle.BoostTracker;
import com.tropimon.tropicalc.battle.FieldTracker;
import com.tropimon.tropicalc.battle.ObservationCollector;
import com.tropimon.tropicalc.battle.TypeTracker;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.SmogonDataLoader;
import com.tropimon.tropicalc.calc.Stat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class SwitchOverlayRenderer {

    private static final int COULEUR_TEXTE = 0xFFFFFF;
    private static final int COULEUR_KO = 0xFF5555;
    private static final int COULEUR_TITRE = 0xFFD700;
    private static final int COULEUR_DANGER = 0xFF8800;
    private static final int COULEUR_REVELE = 0x55FF55;
    private static final int COULEUR_FOND = 0xE0100010;
    private static final int COULEUR_BORDURE = 0xFF5000FF;

    private SwitchOverlayRenderer() {
    }

    public static void render(BattleSwitchPokemonSelection ecran, DrawContext context, int mouseX, int mouseY) {
        BattleSwitchPokemonSelection.SwitchTile survolee = null;
        for (BattleSwitchPokemonSelection.SwitchTile tile : ecran.getTiles()) {
            if (tile.isHovered(mouseX, mouseY)) {
                survolee = tile;
                break;
            }
        }
        if (survolee == null) return;

        com.cobblemon.mod.common.pokemon.Pokemon membre = survolee.getPokemon();
        if (membre == null || membre.getCurrentHealth() <= 0) return;

        Pokemon adversaireBase = BattleStateTracker.getAdversaireActif();
        if (adversaireBase == null) return;

        Pokemon candidat = BattleStateTracker.convertirMembre(membre);
        if (candidat == null) return;

        Pokemon adversaire = ObservationCollector.construireAdversaireEstime(adversaireBase);

        // Seuls les boosts adverses restent actifs après un switch
        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                int stageAdv = BoostTracker.getStageAdversaire(s);
                if (stageAdv != 0) adversaire.setStage(s, stageAdv);
            }
        }

        // Types adverses modifiés en combat (le candidat entre avec ses types normaux)
        TypeTracker.appliquer(candidat, adversaire);

        Field field = FieldTracker.construireField();
        MinecraftClient client = MinecraftClient.getInstance();
        int hauteurLigne = client.textRenderer.fontHeight + 2;

        List<String> lignes = new ArrayList<>();
        List<Integer> couleurs = new ArrayList<>();

        // Vitesses (Distorsion inverse la priorité)
        int vitCandidat = vitesseEffective(candidat, false);
        int vitAdversaire = Math.max(vitesseEffective(adversaire, true),
            ObservationCollector.getVitesseMinObservee(adversaireBase.getEspece()));
        boolean distorsion = FieldTracker.isDistorsion();
        boolean candidatPremier = distorsion ? vitCandidat < vitAdversaire : vitCandidat > vitAdversaire;
        boolean egalite = vitCandidat == vitAdversaire;
        String fleche = egalite ? "=" : (candidatPremier ? ">" : "<");
        String suffixe = distorsion ? " [Distorsion]" : "";
        lignes.add(String.format("Vitesse : %d %s %d%s", vitCandidat, fleche, vitAdversaire, suffixe));
        couleurs.add(egalite ? COULEUR_TEXTE : (candidatPremier ? COULEUR_REVELE : COULEUR_KO));

        // Ses attaques
        lignes.add("Ses attaques :");
        couleurs.add(COULEUR_TITRE);
        for (Move coup : membre.getMoveSet()) {
            if (coup == null) continue;
            com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(coup);
            if (capacite == null || capacite.estCapaciteDeStatut()) continue;

            DamageCalculator.Resultat r = DamageCalculator.calculer(candidat, adversaire, capacite, field, field.getEcransAdversaire(), false);
            String nom = coup.getDisplayName().getString();
            if (r.immunise) {
                lignes.add(nom + " : immunisé");
                couleurs.add(COULEUR_TEXTE);
            } else {
                lignes.add(String.format("%s : %.0f%% - %.0f%%", nom, r.pourcentageMin, r.pourcentageMax));
                couleurs.add(r.koGaranti ? COULEUR_KO : (r.koPossible ? 0xFFAA00 : COULEUR_TEXTE));
            }
        }

        // Ce qu'il subit
        String especeAdv = adversaireBase.getEspece();
        SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(especeAdv);
        List<MoveTemplate> coupsReveles = ObservationCollector.getCoupsAdversaireReveles(especeAdv);

        LinkedHashSet<String> reveleIds = new LinkedHashSet<>();
        for (MoveTemplate t : coupsReveles) reveleIds.add(t.getName());

        List<MoveTemplate> coupsAdv = new ArrayList<>(coupsReveles);
        if (smogon != null) {
            for (String moveId : smogon.topMovesShowdownId()) {
                if (!reveleIds.contains(moveId)) {
                    MoveTemplate t = Moves.INSTANCE.getByName(moveId);
                    if (t != null) coupsAdv.add(t);
                }
            }
        }

        if (!coupsAdv.isEmpty()) {
            lignes.add("Il subit :");
            couleurs.add(COULEUR_DANGER);
            for (MoveTemplate template : coupsAdv) {
                boolean estRevele = reveleIds.contains(template.getName());
                com.tropimon.tropicalc.calc.Move capaciteAdv = convertirTemplate(template);
                String nom = template.getDisplayName().getString();
                String prefixe = estRevele ? "✓ " : "";

                if (capaciteAdv == null || capaciteAdv.estCapaciteDeStatut()) {
                    lignes.add(prefixe + nom + " : statut");
                    couleurs.add(estRevele ? COULEUR_REVELE : COULEUR_TEXTE);
                } else {
                    DamageCalculator.Resultat r = DamageCalculator.calculer(adversaire, candidat, capaciteAdv, field, field.getEcransJoueur(), false);
                    if (r.immunise) {
                        lignes.add(prefixe + nom + " : immunisé");
                        couleurs.add(estRevele ? COULEUR_REVELE : COULEUR_TEXTE);
                    } else {
                        lignes.add(String.format("%s%s : %.0f%% - %.0f%%", prefixe, nom, r.pourcentageMin, r.pourcentageMax));
                        couleurs.add(r.koGaranti ? COULEUR_KO
                            : (estRevele ? COULEUR_REVELE : (r.koPossible ? 0xFFAA00 : COULEUR_TEXTE)));
                    }
                }
            }
        }

        // Dimensions / position
        int largeurMax = 0;
        for (String l : lignes) {
            largeurMax = Math.max(largeurMax, client.textRenderer.getWidth(l));
        }
        int padding = 5;
        int largeur = largeurMax + padding * 2;
        int hauteur = lignes.size() * hauteurLigne + padding * 2;

        int px = mouseX + 12;
        int py = mouseY - 12;
        int largeurEcran = client.getWindow().getScaledWidth();
        int hauteurEcran = client.getWindow().getScaledHeight();
        if (px + largeur > largeurEcran) px = mouseX - largeur - 12;
        if (py + hauteur > hauteurEcran) py = hauteurEcran - hauteur - 4;
        if (py < 4) py = 4;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);

        context.fill(px - 1, py - 1, px + largeur + 1, py + hauteur + 1, COULEUR_BORDURE);
        context.fill(px, py, px + largeur, py + hauteur, COULEUR_FOND);

        int ty = py + padding;
        for (int i = 0; i < lignes.size(); i++) {
            context.drawText(client.textRenderer, Text.literal(lignes.get(i)),
                px + padding, ty, couleurs.get(i), true);
            ty += hauteurLigne;
        }

        context.getMatrices().pop();
    }

    private static int vitesseEffective(Pokemon p, boolean appliquerStages) {
        double v = p.getStatCalculee(Stat.VITESSE);
        if (appliquerStages) {
            int stage = p.getStage(Stat.VITESSE);
            if (stage >= 0) v = v * (2.0 + stage) / 2.0;
            else v = v * 2.0 / (2.0 - stage);
        }
        if ("Écharpe Choix".equals(p.getObjet())) v *= 1.5;
        if (p.getStatut() == Pokemon.Statut.PARALYSIE) v *= 0.5;
        return (int) Math.floor(v);
    }

    private static com.tropimon.tropicalc.calc.Move convertirCapacite(Move coup) {
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

    private static com.tropimon.tropicalc.calc.Move convertirTemplate(MoveTemplate template) {
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
