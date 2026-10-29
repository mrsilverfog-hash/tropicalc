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
    private static final int COULEUR_BORDURE = 0xFFE8B84B;   // doré, cohérent avec le panneau PvP et le HUD principal

    private SwitchOverlayRenderer() {
    }

    public static void render(BattleSwitchPokemonSelection ecran, DrawContext context, int mouseX, int mouseY) {
        if (!com.tropimon.tropicalc.ModToggle.estActif()) return;
        if (Boolean.TRUE.equals(BattleStateTracker.estFormatRandom())) return;
        if (ObservationCollector.estCombatSauvage()) return;
        // Combat double : ce survol suppose un seul adversaire en face
        if (BattleStateTracker.estCombatDouble()) return;
        BattleSwitchPokemonSelection.SwitchTile survolee = null;
        for (BattleSwitchPokemonSelection.SwitchTile tile : ecran.getTiles()) {
            if (tile.isHovered(mouseX, mouseY)) {
                survolee = tile;
                break;
            }
        }
        if (survolee == null) return;

        com.cobblemon.mod.common.pokemon.Pokemon membre = survolee.getPokemon();
        if (membre == null) return;
        // Métamorph sorti transformé : ses PV d'équipe sont figés depuis la
        // transformation, on prend les derniers PV réels lus en combat
        int pvReels = ObservationCollector.getPvReelsApresTransformation(membre.getUuid());
        // Hors Métamorph : getCurrentHealth() n'est pas tenu à jour en combat
        if (pvReels < 0) pvReels = com.tropimon.tropicalc.battle.PvEquipe.pv(membre.getUuid());
        if ((pvReels >= 0 ? pvReels : membre.getCurrentHealth()) <= 0) return;

        Pokemon adversaireBase = BattleStateTracker.getAdversaireActif();
        if (adversaireBase == null) return;

        Pokemon candidat = BattleStateTracker.convertirMembre(membre);
        if (candidat == null) return;
        if (pvReels >= 0) candidat.setPvActuels(Math.min(pvReels, candidat.getPvMax()));
        // Même sécurité que dans CalcOverlay : sans ça, un candidat pouvait
        // encore montrer un objet déjà détruit (Ballon, Sabotage) ou pas
        // encore mis à jour après un vol via Pickpocket.
        ObservationCollector.appliquerObjetReelJoueur(candidat);
        // Départ Lent : actif dès son entrée
        if ("Départ Lent".equals(candidat.getTalent())) candidat.setDepartLentActif(true);
        if ("Télécharge".equals(candidat.getTalent())) candidat.setTelechargeAVenir(true);
        // Ceriflor qui entre au soleil prend sa forme Ensoleillée
        if ("Don Floral".equals(candidat.getTalent())) candidat.setDonFloralActif(true);

        Pokemon adversaire = ObservationCollector.adversaireAffiche(adversaireBase);

        // Métamorph avec Imposteur : il entre déjà transformé en l'adversaire
        // en face (stats, types, talent et boosts copiés ; ses PV, son objet).
        boolean imposteur = "Imposteur".equals(candidat.getTalent());
        if (imposteur) {
            candidat = ObservationCollector.construireMorphing(candidat, adversaireBase);
            for (Stat s : Stat.values()) {
                if (s != Stat.PV) candidat.setStage(s, BoostTracker.getStageAdversaire(s));
            }
        }

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

        // Pièges d'entrée : PV à l'arrivée
        double degatsPieges = degatsEntreePct(candidat);
        boolean toile = FieldTracker.isStickyWebJoueur() && DamageCalculator.estAuSol(candidat);
        boolean picsToxik = picsToxikActifs(candidat);
        if (degatsPieges > 0 || toile || picsToxik) {
            double pvActuelsPct = 100.0 * candidat.getPvActuels() / Math.max(1, candidat.getPvMax());
            double pvApres = Math.max(0, pvActuelsPct - degatsPieges);
            StringBuilder lignePieges = new StringBuilder();
            if (degatsPieges > 0) {
                lignePieges.append(String.format("Pièges : -%.0f%% → entre à %.0f%% PV", degatsPieges, pvApres));
            }
            if (toile) {
                if (lignePieges.length() > 0) lignePieges.append(" | ");
                lignePieges.append("Toile : -1 Vit");
            }
            if (picsToxik) {
                if (lignePieges.length() > 0) lignePieges.append(" | ");
                int couches = FieldTracker.getToxicSpikesJoueur();
                lignePieges.append(couches >= 2 ? "Pics Toxik : Toxik" : "Pics Toxik : Poison");
            }
            lignes.add(lignePieges.toString());
            couleurs.add(pvApres <= 0 ? COULEUR_KO : (degatsPieges >= 25 ? 0xFFAA00 : COULEUR_TEXTE));
        }
        // Il entre avec les PV et le statut d'après les pièges : ses chances
        // de K.O. (et Multiécaille, Fermeté...) en tiennent compte, comme
        // Showdown ("after Stealth Rock").
        boolean apresPieges = false;
        if (degatsPieges > 0) {
            int perdus = (int) Math.floor(candidat.getPvMax() * degatsPieges / 100.0);
            candidat.setPvActuels(Math.max(0, candidat.getPvActuels() - perdus));
            apresPieges = true;
        }
        if (picsToxik && (candidat.getStatut() == null || candidat.getStatut() == Pokemon.Statut.AUCUN)) {
            candidat.setStatut(FieldTracker.getToxicSpikesJoueur() >= 2 ? Pokemon.Statut.POISON_GRAVE : Pokemon.Statut.POISON);
            apresPieges = true;
        }

        // Ses attaques (Imposteur : celles de l'adversaire, connues ou Smogon, "?")
        lignes.add(imposteur ? "Ses attaques (copiées, probables) :" : "Ses attaques :");
        couleurs.add(COULEUR_TITRE);
        List<Object[]> attaques = new ArrayList<>();
        if (imposteur) {
            for (String id : CalcOverlay.capacitesProbables(adversaireBase.getEspece())) {
                MoveTemplate t = Moves.INSTANCE.getByName(id);
                if (t != null) attaques.add(new Object[]{t.getDisplayName().getString() + " ?", convertirTemplate(t)});
            }
        } else {
            for (Move coup : membre.getMoveSet()) {
                if (coup != null) attaques.add(new Object[]{coup.getDisplayName().getString(), convertirCapacite(coup)});
            }
        }
        for (Object[] attaque : attaques) {
            com.tropimon.tropicalc.calc.Move capacite = (com.tropimon.tropicalc.calc.Move) attaque[1];
            if (capacite == null || capacite.estCapaciteDeStatut()) continue;

            DamageCalculator.Resultat r = DamageCalculator.calculer(candidat, adversaire, capacite, field, field.getEcransAdversaire(), false);
            String nom = (String) attaque[0];
            if (r.immunise) {
                lignes.add(nom + " : immunisé");
                couleurs.add(COULEUR_TEXTE);
            } else {
                lignes.add(String.format("%s : %.0f%% - %.0f%%%s", nom, r.pourcentageMin, r.pourcentageMax,
                    CalcOverlay.texteKO(r, candidat, adversaire, capacite, field, true)));
                couleurs.add(r.koGaranti ? COULEUR_KO : (r.koPossible ? 0xFFAA00 : COULEUR_TEXTE));
            }
        }

        // Ce qu'il subit
        String especeAdv = adversaireBase.getEspece();
        SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(especeAdv);
        List<MoveTemplate> coupsReveles = ObservationCollector.getCoupsAdversaireReveles(especeAdv);
        List<String> copiees = ObservationCollector.getCapacitesMorphingAdversaire();
        if (!copiees.isEmpty()) {
            coupsReveles = new ArrayList<>();
            for (String id : copiees) {
                MoveTemplate t = Moves.INSTANCE.getByName(id);
                if (t != null) coupsReveles.add(t);
            }
            smogon = null;
        }

        LinkedHashSet<String> reveleIds = new LinkedHashSet<>();
        for (MoveTemplate t : coupsReveles) reveleIds.add(t.getName());

        List<MoveTemplate> coupsAdv = new ArrayList<>(coupsReveles);
        // Jeu complet copié par mon Métamorph : rien à supposer
        if (smogon != null && !ObservationCollector.coupsAdversaireComplets(especeAdv)) {
            for (String moveId : smogon.topMovesShowdownId()) {
                if (!reveleIds.contains(moveId)) {
                    MoveTemplate t = Moves.INSTANCE.getByName(moveId);
                    if (t != null) coupsAdv.add(t);
                }
            }
        }

        if (!coupsAdv.isEmpty()) {
            lignes.add(apresPieges ? "Il subit (après les pièges) :" : "Il subit :");
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
                        lignes.add(String.format("%s%s : %.0f%% - %.0f%%%s", prefixe, nom, r.pourcentageMin, r.pourcentageMax,
                            CalcOverlay.texteKO(r, adversaire, candidat, capaciteAdv, field, false, true)));
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

    /** Dégâts de pièges d'entrée en % des PV max pour ce candidat. */
    private static double degatsEntreePct(Pokemon candidat) {
        if ("Grosses Bottes".equals(candidat.getObjet())) return 0;
        if ("Garde Magik".equals(candidat.getTalent())) return 0;

        double total = 0;

        if (FieldTracker.isStealthRockJoueur()) {
            double eff = com.tropimon.tropicalc.calc.PokemonType.ROCHE
                .efficaciteContre(candidat.getType1(), candidat.getType2());
            total += 12.5 * eff;
        }

        int couches = FieldTracker.getSpikesJoueur();
        if (couches > 0 && DamageCalculator.estAuSol(candidat)) {
            total += switch (couches) {
                case 1 -> 12.5;
                case 2 -> 100.0 / 6.0;
                default -> 25.0;
            };
        }

        return total;
    }

    /** Vrai si le candidat serait empoisonné en entrant (Pics Toxik posés, au sol, non Poison/Acier). */
    private static boolean picsToxikActifs(Pokemon candidat) {
        if (FieldTracker.getToxicSpikesJoueur() <= 0) return false;
        if (!DamageCalculator.estAuSol(candidat)) return false;
        if ("Garde Magik".equals(candidat.getTalent())) return false;
        com.tropimon.tropicalc.calc.PokemonType t1 = candidat.getType1();
        com.tropimon.tropicalc.calc.PokemonType t2 = candidat.getType2();
        // Poison : absorbe les Pics Toxik au lieu d'être empoisonné (les retire même)
        if (t1 == com.tropimon.tropicalc.calc.PokemonType.POISON
            || t2 == com.tropimon.tropicalc.calc.PokemonType.POISON) return false;
        if (t1 == com.tropimon.tropicalc.calc.PokemonType.ACIER
            || t2 == com.tropimon.tropicalc.calc.PokemonType.ACIER) return false;
        return true;
    }

    private static int vitesseEffective(Pokemon p, boolean appliquerStages) {
        if (appliquerStages) {
            Field f = FieldTracker.construireField();
            // Cette branche sert à l'ADVERSAIRE (vitesseEffective(adversaire, true)) :
            // c'est donc SON Vent Arrière qui s'applique, pas le mien.
            return (int) DamageCalculator.vitesseEnCombat(p, f.getMeteo(), f.getTerrain(),
                FieldTracker.isTailwindAdversaire());
        }
        // Candidat de mon équipe qui entre : aucun stage, sauf Toile Gluante
        // (-1 Vitesse à l'entrée s'il touche le sol). Même calcul que partout
        // ailleurs (formule du jeu, testée contre Showdown) : objet, talents
        // météo, Proto-Synthèse, paralysie, et mon Vent Arrière.
        Field f = FieldTracker.construireField();
        int stageAvant = p.getStage(Stat.VITESSE);
        try {
            boolean toile = FieldTracker.isStickyWebJoueur() && DamageCalculator.estAuSol(p);
            p.setStage(Stat.VITESSE, toile ? -1 : 0);
            return (int) DamageCalculator.vitesseEnCombat(p, f.getMeteo(), f.getTerrain(),
                FieldTracker.isTailwindJoueur());
        } finally {
            p.setStage(Stat.VITESSE, stageAvant);
        }
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
            
            .poing(com.tropimon.tropicalc.calc.MoveFlags.estPoing(coup.getName()))
            .morsure(com.tropimon.tropicalc.calc.MoveFlags.estMorsure(coup.getName()))
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
            
            .poing(com.tropimon.tropicalc.calc.MoveFlags.estPoing(template.getName()))
            .morsure(com.tropimon.tropicalc.calc.MoveFlags.estMorsure(template.getName()))
            .build();
    }
}
