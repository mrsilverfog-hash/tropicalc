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

    private record LigneTexte(String texte, int x, int y, int couleur) {}
    private record LigneIcone(int type, int x, int y) {}   // voir TEXTURES_ICONES pour la liste des types

    private final List<LigneTexte> lignesAffichage = new ArrayList<>();
    private final List<LigneIcone> iconesAffichage = new ArrayList<>();

    /** Bufférise une ligne de texte au lieu de la dessiner immédiatement : permet de
     *  connaître la taille réelle du contenu AVANT de dessiner le cadre qui l'entoure. */
    private void dessinerTexte(String texte, int x, int y, int couleur) {
        lignesAffichage.add(new LigneTexte(texte, x, y, couleur));
    }

    @Override
    public void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        // Doit tourner AUSSI hors combat : c'est là que le reset entre combats s'exécute
        ObservationCollector.tick();

        if (!com.tropimon.tropicalc.ModToggle.estActif()) return;
        if (!BattleStateTracker.estEnCombat()) return;
        if (ObservationCollector.estCombatSauvage()) return;

        Pokemon adversaireBase = BattleStateTracker.getAdversaireActif();
        com.cobblemon.mod.common.pokemon.Pokemon monComplet = BattleStateTracker.getPokemonCompletJoueurAffichage();
        Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (joueur == null) joueur = BattleStateTracker.getJoueurActif();

        if (adversaireBase == null || joueur == null || monComplet == null) return;

        lignesAffichage.clear();
        iconesAffichage.clear();

        joueur.setCoupsRageFistSubis(ObservationCollector.getCoupsRageFistJoueur(joueur.getEspece()));

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

        // Position de départ du cadre : le fond et la bordure sont dessinés à la
        // TOUTE FIN de la fonction, une fois la taille réelle du contenu connue
        // (tout le texte/icônes est bufferisé au lieu d'être dessiné immédiatement).
        int yDebutCadre = y - 4;
        int xDebutCadre = x - 4;

        Field field = FieldTracker.construireField();

        // --- Section 1 : mes capacités ---
        String titre = BattleStateTracker.joueurEstTransforme()
            ? "TropiCalc [transformé]" : "TropiCalc";
        dessinerTexte(titre, x, y, COULEUR_TITRE);
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
        dessinerTexte(texteVitesse, x, y, couleurVitesse);

        // Hypothèse Mouchoir Choix : sa vitesse x1.5 s'il en tenait un.
        // Affiché en bleu entre parenthèses tant que ce n'est pas déjà son objet
        // connu, pour anticiper le pire cas de priorité.
        String objetAdversaire = adversaire.getObjet();
        boolean mouchoirDejaPris = "Mouchoir Choix".equals(objetAdversaire);
        if (!mouchoirDejaPris) {
            int vitMouchoir = (int) Math.floor(vitAdversaire * 1.5);
            String texteMouchoir = String.format(" (%d)", vitMouchoir);
            int largeur = client.textRenderer.getWidth(texteVitesse);
            dessinerTexte(texteMouchoir, x + largeur, y, COULEUR_MOUCHOIR);
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
            dessinerTexte(ligne, x, y, couleur);
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
            dessinerTexte("Capacités adverses :", x, y, COULEUR_DANGER);
            y += hauteurLigne;

            // Verrou Choix : objet Choix + un coup déjà utilisé depuis son entrée
            String objetAdv = adversaire.getObjet();
            String verrou = ObservationCollector.getCoupVerrouAdversaire();
            if (objetAdv != null && objetAdv.contains("Choix") && verrou != null) {
                MoveTemplate tv = Moves.INSTANCE.getByName(verrou);
                String nomVerrou = tv != null ? tv.getDisplayName().getString() : verrou;
                boolean sur = ObservationCollector.estObjetConfirme(adversaireBase.getEspece());
                dessinerTexte(String.format("Verrou Choix%s : %s", sur ? "" : "?", nomVerrou), x, y, 0xFFAA00);
                y += hauteurLigne;
            }

            // Abris consécutifs : le suivant a 1/3^n de chances de réussir
            int abris = ObservationCollector.getCompteurAbrisAdversaire();
            if (false && abris >= 1) {
                double chance = 100.0 / Math.pow(3, abris);
                dessinerTexte(String.format("Abri x%d → prochain ~%.0f%%", abris, chance), x, y, COULEUR_TEXTE);
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

                        // Prise de Bec / Branchicrok : double puissance si
                        // l'adversaire attaque avant moi. Affiche les DEUX
                        // scénarios plutôt que de deviner l'ordre d'action,
                        // qui dépend aussi de la capacité que JE choisirai.
                        if ("boltbeak".equals(template.getName()) || "fishiousrend".equals(template.getName())) {
                            DamageCalculator.Resultat avant = calculerAvecPuissanceForcee(
                                adversaire, joueur, capaciteAdv, 170, field, field.getEcransJoueur());
                            DamageCalculator.Resultat apres = calculerAvecPuissanceForcee(
                                adversaire, joueur, capaciteAdv, 85, field, field.getEcransJoueur());
                            ligne = String.format("%s%s (avt) : %.0f%%-%.0f%% | (après) : %.0f%%-%.0f%%%s",
                                estRevele ? "✓ " : "", nom,
                                avant.pourcentageMin, avant.pourcentageMax,
                                apres.pourcentageMin, apres.pourcentageMax, suffixePp);
                        }

                        // Hypothèse objet offensif quasi-certain (> 50% d'usage Smogon) :
                        // fourchette de dégâts SI l'adversaire tenait cet objet, en bleu.
                        // Uniquement si l'objet n'est pas déjà un fait confirmé (sinon
                        // le calcul principal l'inclut déjà).
                        String ligneHypo = ligneHypotheseObjet(adversaire, joueur, capaciteAdv, field,
                            especeAdv, smogon, statAtk == Stat.ATTAQUE);
                        if (ligneHypo != null) {
                            dessinerTexte(ligne, x, y, couleur);
                            int largeurLigne = client.textRenderer.getWidth(ligne);
                            dessinerTexte(ligneHypo, x + largeurLigne, y, COULEUR_MOUCHOIR);
                            y += hauteurLigne;
                            continue;
                        }
                    }
                }
                dessinerTexte(ligne, x, y, couleur);
                y += hauteurLigne;
            }
        }

        // --- Projection des dégâts résiduels adverses (cœur du stall) ---
        boolean objetSur = ObservationCollector.estObjetConfirme(adversaireBase.getEspece());
        boolean talentConfirmeAdv = ObservationCollector.getTalentConfirme(adversaireBase.getEspece()) != null;
        java.util.Set<String> talentsPossiblesAdv = ObservationCollector.getTalentsReelsEspece(adversaireBase);
        boolean soinPoisonIncertainAdv = !talentConfirmeAdv
            && talentsPossiblesAdv != null && talentsPossiblesAdv.contains("Soin Poison");
        ResidualProjector.Projection proj = ResidualProjector.projeter(adversaire, field.getMeteo(), objetSur,
            ObservationCollector.getCompteurToxikProchainAdversaire(),
            ObservationCollector.isAdversaireSalaison(),
            ObservationCollector.isAdversaireVampigraine(),
            talentConfirmeAdv, soinPoisonIncertainAdv);
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
                boolean sourceConfirmee = objetSur || talentConfirmeAdv;
                ligneProj = String.format("Résiduel : +%.0f%%/t (%s)%s",
                    -proj.netPremierTourPct(), proj.detail(),
                    sourceConfirmee ? " : régénère" : "");
                couleurProj = sourceConfirmee ? 0xFFAA00 : COULEUR_TEXTE;
            }
            dessinerTexte(ligneProj, x, y, couleurProj);
            y += hauteurLigne;
        }

        // --- Projection résiduelle du joueur : anticiper sa propre mort ---
        // L'objet et le talent du joueur sont réels, jamais estimés
        ResidualProjector.Projection projJoueur = ResidualProjector.projeter(joueur, field.getMeteo(), true,
            ObservationCollector.getCompteurToxikProchainJoueur(),
            ObservationCollector.isJoueurSalaison(),
            ObservationCollector.isJoueurVampigraine(), true, false);
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
            dessinerTexte(ligneToi, x, y, couleurToi);
            y += hauteurLigne;
        }

        // --- Météo : une ligne avec icône selon le type actif ---
        if (field.getMeteo() != com.tropimon.tropicalc.calc.Field.Meteo.AUCUNE
                && FieldTracker.getToursMeteoRestants() > 0) {
            int typeIconeMeteo = switch (field.getMeteo()) {
                case SOLEIL, SOLEIL_INTENSE -> 2;
                case PLUIE, PLUIE_INTENSE -> 3;
                case SABLE -> 4;
                case NEIGE -> 5;
                default -> -1;
            };
            if (typeIconeMeteo >= 0) dessinerIcone(typeIconeMeteo, x, y);
            dessinerTexte(String.format("Météo : ~%dt", FieldTracker.getToursMeteoRestants()),
                x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }

        // --- Prescience : une ligne dédiée par camp, icône œil à gauche ---
        if (FieldTracker.getFutureSightJoueurTours() > 0) {
            dessinerIcone(6, x, y);
            dessinerTexte(String.format("Prescience sur toi : %dt", FieldTracker.getFutureSightJoueurTours()),
                x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }
        if (FieldTracker.getFutureSightAdversaireTours() > 0) {
            dessinerIcone(6, x, y);
            dessinerTexte(String.format("Prescience sur adv : %dt", FieldTracker.getFutureSightAdversaireTours()),
                x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }

        // --- Murs : une ligne dédiée par camp, petite icône à gauche ---
        if (FieldTracker.adversaireAUnEcran() && FieldTracker.getToursEcransAdversaireRestants() > 0) {
            StringBuilder noms = new StringBuilder();
            if (FieldTracker.adversaireAReflet()) noms.append(noms.length() > 0 ? "+Protection" : "Protection");
            if (FieldTracker.adversaireAMurLumiere()) noms.append(noms.length() > 0 ? "+Mur Lumière" : "Mur Lumière");
            if (FieldTracker.adversaireAVoileAurore()) noms.append(noms.length() > 0 ? "+Voile Aurore" : "Voile Aurore");
            dessinerIconeMur(x, y);
            dessinerTexte(String.format("%s adv : ~%dt", noms, FieldTracker.getToursEcransAdversaireRestants()), x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }
        if (FieldTracker.joueurAUnEcran() && FieldTracker.getToursEcransJoueurRestants() > 0) {
            StringBuilder noms = new StringBuilder();
            if (FieldTracker.joueurAReflet()) noms.append(noms.length() > 0 ? "+Protection" : "Protection");
            if (FieldTracker.joueurAMurLumiere()) noms.append(noms.length() > 0 ? "+Mur Lumière" : "Mur Lumière");
            if (FieldTracker.joueurAVoileAurore()) noms.append(noms.length() > 0 ? "+Voile Aurore" : "Voile Aurore");
            dessinerIconeMur(x, y);
            dessinerTexte(String.format("%s toi : ~%dt", noms, FieldTracker.getToursEcransJoueurRestants()), x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }

        // --- Clone : une ligne dédiée par camp, petite icône à gauche ---
        if (FieldTracker.joueurAUnClone()) {
            dessinerIconeClone(x, y);
            dessinerTexte("Clone toi", x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }
        if (FieldTracker.adversaireAUnClone()) {
            dessinerIconeClone(x, y);
            dessinerTexte("Clone adv", x + 13, y, COULEUR_TEXTE);
            y += hauteurLigne;
        }

        // --- Section 3 : set estimé ---
        if (smogon != null && !smogon.topSpreads().isEmpty()) {
            y += 4;
            com.tropimon.tropicalc.calc.ProfilAdversaire profil = ObservationCollector.getProfil(especeAdv);

            // Parmi les 3 spreads Smogon les plus populaires, affiche celui
            // qui reste cohérent avec les dégâts déjà observés (au lieu de
            // toujours montrer le #1 même après une observation qui le
            // contredit). Repli sur le #1 si aucune observation ou si aucun
            // des 3 ne reste cohérent (set hors du top 3, ou EV pas encore
            // assez resserrés pour trancher).
            SmogonDataLoader.ParsedSpread top = smogon.topSpreads().get(0);
            boolean deduitParObservation = false;
            boolean aUneObservation = profil != null && profil.getNbObservations() > 0;
            boolean aUneVitesseObservee = ObservationCollector.getVitesseMinObservee(especeAdv) > 0;
            if (profil != null && (aUneObservation || aUneVitesseObservee)) {
                SmogonDataLoader.ParsedSpread choisi = profil.spreadPlusProbable(smogon.topSpreads(), 3, adversaire);
                if (choisi != null) {
                    deduitParObservation = choisi != smogon.topSpreads().get(0);
                    top = choisi;
                }
            }

            dessinerTexte("Set estimé :", x, y, COULEUR_TITRE);
            y += hauteurLigne;
            dessinerTexte(String.format("HP %d | Def %d | DéfSpé %d | %s",
                    top.hpEv(), top.defEv(), top.spdEv(),
                    ShowdownIdMapper.nature(top.natureShowdownId())), x, y, deduitParObservation ? COULEUR_REVELE : COULEUR_TEXTE);
            y += hauteurLigne;

            if (profil != null && profil.getNbObservations() >= 3) {
                StatHypothesis hypDef = profil.defense.nombreObservations >= profil.defenseSpe.nombreObservations
                    ? profil.defense : profil.defenseSpe;
                dessinerTexte(String.format("Inférence Def EV %d-%d", hypDef.evMin, hypDef.evMax), x, y, COULEUR_TEXTE);
                y += hauteurLigne;
            }

            // Objet : uniquement affiché quand on SAIT (jamais une supposition).
            // Casque Brut / Restes : détectés par le motif de chip/soin.
            // Mouchoir Choix : il agit avant alors que sa vitesse max sans objet
            // ne le permettrait pas. Bandeau/Lunettes Choix : ratio net x1.5.
            // Orbe Vie : ratio net x1.3 + son propre recul de ~10% le même tour.
            String objetConfirme = ObservationCollector.getObjetConfirme(especeAdv);
            boolean objetRetire = ObservationCollector.estObjetConfirme(especeAdv) && objetConfirme == null;
            if (objetConfirme != null) {
                dessinerTexte("Objet confirmé : " + objetConfirme, x, y, COULEUR_REVELE);
                y += hauteurLigne;
            } else if (objetRetire) {
                dessinerTexte("Objet confirmé : aucun (Sabotage)", x, y, COULEUR_REVELE);
                y += hauteurLigne;
            }
        }

        // --- Cadre adaptatif : taille calculée sur le contenu réellement bufferisé ---
        int largeurContenu = 0;
        for (LigneTexte l : lignesAffichage) {
            int droite = (l.x() - x) + client.textRenderer.getWidth(l.texte());
            largeurContenu = Math.max(largeurContenu, droite);
        }
        for (LigneIcone ic : iconesAffichage) {
            int droite = (ic.x() - x) + 10;   // icônes affichées à 10px
            largeurContenu = Math.max(largeurContenu, droite);
        }
        int largeurCadre = largeurContenu + 8;
        int hauteurCadre = (y + 4) - yDebutCadre;

        context.fill(xDebutCadre, yDebutCadre, xDebutCadre + largeurCadre, yDebutCadre + hauteurCadre, 0xC0101010);
        drawBorder(context, xDebutCadre, yDebutCadre, largeurCadre, hauteurCadre, 0xFFE8B84B);

        // Dessin réel, par-dessus le cadre qui vient d'être posé
        for (LigneTexte l : lignesAffichage) {
            context.drawText(client.textRenderer, Text.literal(l.texte()), l.x(), l.y(), l.couleur(), true);
        }
        for (LigneIcone ic : iconesAffichage) {
            context.drawTexture(TEXTURES_ICONES[ic.type()], ic.x(), ic.y(), 0, 0, 10, 10, 16, 16);
        }
    }

    /** Contour rectangulaire simple, 1px, style cohérent avec le panneau PvP. */
    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int couleur) {
        context.fill(x, y, x + w, y + 1, couleur);
        context.fill(x, y + h - 1, x + w, y + h, couleur);
        context.fill(x, y, x + 1, y + h, couleur);
        context.fill(x + w - 1, y, x + w, y + h, couleur);
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

    /**
     * Recalcule les dégâts d'une capacité en forçant sa puissance de base,
     * sans passer par puissanceEffective (DamageCalculator) - utile pour
     * Prise de Bec/Branchicrok, où on veut voir explicitement les DEUX
     * scénarios (avant/après) plutôt qu'une seule valeur déduite d'une
     * comparaison de vitesse qui pourrait ne pas se vérifier selon la
     * capacité que l'adversaire choisira réellement.
     */
    private DamageCalculator.Resultat calculerAvecPuissanceForcee(Pokemon attaquant, Pokemon defenseur,
            com.tropimon.tropicalc.calc.Move original, int puissanceForcee, Field terrain, Field.Ecrans ecrans) {
        com.tropimon.tropicalc.calc.Move copie = com.tropimon.tropicalc.calc.Move
            .builder("_forced_" + original.getNom(), original.getType(), original.getCategorie())
            .puissance(puissanceForcee)
            .precision(original.getPrecision())
            .prioritee(original.getPrioritee())
            .ratioCritique(original.getRatioCritique())
            .multiCoups(original.getCoupsMin(), original.getCoupsMax())
            .poing(original.isPoing())
            .morsure(original.isMorsure())
            .contact()
            .build();
        return DamageCalculator.calculer(attaquant, defenseur, copie, terrain, ecrans, false);
    }

    // Index : 0=mur, 1=clone, 2=soleil, 3=pluie, 4=sable, 5=neige, 6=prescience
    private static final net.minecraft.util.Identifier[] TEXTURES_ICONES = {
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/mur.png"),
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/clone.png"),
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/soleil.png"),
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/pluie.png"),
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/sable.png"),
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/neige.png"),
        net.minecraft.util.Identifier.of("tropicalc", "textures/gui/prescience.png"),
    };

    /** Bufférise une icône (par index dans TEXTURES_ICONES) au lieu de la dessiner immédiatement. */
    private void dessinerIcone(int type, int x, int y) {
        iconesAffichage.add(new LigneIcone(type, x, y));
    }

    private void dessinerIconeMur(int x, int y) { dessinerIcone(0, x, y); }
    private void dessinerIconeClone(int x, int y) { dessinerIcone(1, x, y); }

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
