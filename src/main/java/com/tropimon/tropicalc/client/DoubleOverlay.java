package com.tropimon.tropicalc.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.tropimon.tropicalc.ModToggle;
import com.tropimon.tropicalc.battle.BattleStateTracker;
import com.tropimon.tropicalc.battle.BattleStateTracker.ActifDouble;
import com.tropimon.tropicalc.battle.BoostsDouble;
import com.tropimon.tropicalc.battle.FieldTracker;
import com.tropimon.tropicalc.battle.ObservationCollector;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.Stat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TropiCalc double (touche 2 du pavé numérique).
 *
 * Un cadre par Pokémon de mon camp : ses attaques, et pour chacune les
 * dégâts sur CHACUN des deux adversaires (une colonne par adversaire). Le
 * cadre du Pokémon dont le jeu attend l'action est doré et marqué ▶ : après
 * avoir choisi l'attaque du premier, le second s'allume à son tour.
 *
 * En tête, l'ordre d'action des quatre Pokémon (vitesses, Distorsion).
 * Capacités de zone : x0,75 quand elles touchent plusieurs cibles, et pour
 * celles qui touchent aussi l'allié (Séisme...), une colonne « allié ».
 *
 * Adversaires : set estimé (Smogon) comme en simple ; les déductions
 * d'objet / talent / EV sont coupées en double (elles supposent un seul
 * adversaire en face).
 */
public final class DoubleOverlay implements HudRenderCallback {

    private static final int COULEUR_TEXTE = 0xFFFFFF;
    private static final int COULEUR_GRIS = 0xAAAAAA;
    private static final int COULEUR_TITRE = 0xFFD700;
    private static final int COULEUR_KO = 0xFF5555;
    private static final int COULEUR_KO_POSSIBLE = 0xFFAA00;
    private static final int COULEUR_MOI = 0x55FF55;
    private static final int COULEUR_ADV = 0xFF8888;
    private static final int COULEUR_ALLIE = 0x88CCFF;
    private static final int BORDURE_ACTIVE = 0xFFE8B84B;
    private static final int BORDURE_INACTIVE = 0xFF444444;
    private static final int FOND = 0xC0101010;

    /** id Showdown -> "foes" (tous les adversaires) ou "all" (allié compris). */
    private static Map<String, String> zone = null;

    private static Map<String, String> zone() {
        if (zone != null) return zone;
        zone = new HashMap<>();
        try (InputStream in = DoubleOverlay.class.getResourceAsStream("/assets/tropicalc/capacites-zone.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String ligne;
                while ((ligne = r.readLine()) != null) {
                    ligne = ligne.trim();
                    if (ligne.isEmpty() || ligne.startsWith("#")) continue;
                    String[] p = ligne.split(" ");
                    if (p.length == 2) zone.put(p[0], p[1]);
                }
            }
        } catch (Exception ignored) {
        }
        return zone;
    }

    /** Un morceau de texte coloré. */
    private record Morceau(String texte, int couleur) {
    }

    /** Une ligne du tableau : colonnes (nom, adv 1, adv 2, allié). */
    private record Rangee(Morceau[] cellules) {
    }

    @Override
    public void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!ModToggle.estDoubleActif()) return;
        if (!BattleStateTracker.estEnCombat() || !BattleStateTracker.estCombatDouble()) return;
        try {
            dessiner(context);
        } catch (Exception ignored) {
            // Jamais de crash d'affichage
        }
    }

    private void dessiner(DrawContext context) {
        List<ActifDouble> miens = BattleStateTracker.getActifsJoueurDouble();
        List<ActifDouble> adversesBase = BattleStateTracker.getActifsAdversaireDouble();
        if (miens.isEmpty()) return;

        Field field = FieldTracker.construireField();
        field.setCombatDouble(true);

        // Adversaires : set estimé + boosts de CE Pokémon
        List<Pokemon> adverses = new ArrayList<>();
        List<String> nomsAdverses = new ArrayList<>();
        for (ActifDouble a : adversesBase) {
            if (a == null || a.pokemon().getPvActuels() <= 0) {
                adverses.add(null);
                nomsAdverses.add(null);
                continue;
            }
            Pokemon estime;
            try {
                estime = ObservationCollector.construireAdversaireEstime(a.pokemon());
            } catch (Exception e) {
                estime = a.pokemon();
            }
            appliquerStages(estime, a.cle());
            adverses.add(estime);
            nomsAdverses.add(CalcOverlay.nomEspece(a.pokemon().getEspece()));
        }
        while (adverses.size() < 2) {
            adverses.add(null);
            nomsAdverses.add(null);
        }
        int adversesVivants = 0;
        for (Pokemon p : adverses) if (p != null) adversesVivants++;

        List<Pokemon> mesPokemon = new ArrayList<>();
        for (ActifDouble a : miens) {
            if (a == null || a.pokemon().getPvActuels() <= 0) {
                mesPokemon.add(null);
                continue;
            }
            appliquerStages(a.pokemon(), a.cle());
            mesPokemon.add(a.pokemon());
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int hauteurLigne = tr.fontHeight + 2;
        int x = 8;

        // --- Ordre d'action ---
        List<Morceau> ordre = ligneOrdre(miens, mesPokemon, adversesBase, adverses, field);

        // --- Un tableau par Pokémon de mon camp ---
        UUID quiChoisit = BattleStateTracker.getUuidQuiChoisit();
        List<List<Rangee>> tableaux = new ArrayList<>();
        List<Morceau> titres = new ArrayList<>();
        List<Boolean> actifs = new ArrayList<>();
        for (int i = 0; i < miens.size(); i++) {
            ActifDouble moi = miens.get(i);
            Pokemon attaquant = mesPokemon.get(i);
            if (moi == null || attaquant == null || moi.complet() == null) continue;
            Pokemon allie = null;
            for (int j = 0; j < mesPokemon.size(); j++) if (j != i && mesPokemon.get(j) != null) allie = mesPokemon.get(j);

            boolean choisit = moi.uuid().equals(quiChoisit);
            String boosts = formatBoosts(attaquant);
            titres.add(new Morceau((choisit ? "▶ " : "") + CalcOverlay.nomEspece(attaquant.getEspece())
                + String.format("  %.0f%%", attaquant.getPourcentagePv())
                + (boosts.isEmpty() ? "" : "  " + boosts), choisit ? COULEUR_TITRE : COULEUR_GRIS));
            actifs.add(choisit);

            List<Rangee> rangees = new ArrayList<>();
            // En-tête : noms des adversaires
            Morceau[] entete = new Morceau[4];
            entete[0] = new Morceau("", COULEUR_TEXTE);
            for (int k = 0; k < 2; k++) {
                Pokemon adv = adverses.get(k);
                entete[k + 1] = adv == null ? new Morceau("—", COULEUR_GRIS)
                    : new Morceau(nomsAdverses.get(k) + String.format(" %.0f%%", adv.getPourcentagePv())
                        + texteBoostsCourt(adv), COULEUR_ADV);
            }
            entete[3] = new Morceau("", COULEUR_TEXTE);
            rangees.add(new Rangee(entete));

            for (Move coup : moi.complet().getMoveSet()) {
                if (coup == null) continue;
                com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(coup);
                if (capacite == null || capacite.estCapaciteDeStatut()) continue;
                String id = coup.getName();
                String typeZone = zone().get(id);
                boolean allieTouche = "all".equals(typeZone) && allie != null;
                int cibles = typeZone == null ? 1 : adversesVivants + (allieTouche ? 1 : 0);
                boolean multiple = cibles >= 2;

                Morceau[] c = new Morceau[4];
                c[0] = new Morceau(coup.getDisplayName().getString() + (typeZone != null ? " (zone)" : ""), COULEUR_TEXTE);
                for (int k = 0; k < 2; k++) {
                    Pokemon adv = adverses.get(k);
                    c[k + 1] = adv == null ? new Morceau("", COULEUR_GRIS)
                        : cellule(attaquant, adv, capacite, field, field.getEcransAdversaire(), multiple, true);
                }
                if (allieTouche) {
                    Morceau m = cellule(attaquant, allie, capacite, field, field.getEcransJoueur(), multiple, false);
                    c[3] = new Morceau("allié " + m.texte(), m.couleur() == COULEUR_GRIS ? COULEUR_GRIS : COULEUR_ALLIE);
                } else {
                    c[3] = new Morceau("", COULEUR_TEXTE);
                }
                rangees.add(new Rangee(c));
            }
            tableaux.add(rangees);
        }
        if (tableaux.isEmpty()) return;

        // --- Mise en page : largeur des colonnes commune aux deux cadres ---
        int[] largeurs = new int[4];
        for (List<Rangee> t : tableaux) {
            for (Rangee r : t) {
                for (int k = 0; k < 4; k++) largeurs[k] = Math.max(largeurs[k], tr.getWidth(r.cellules()[k].texte()));
            }
        }
        int ecart = 10;
        int largeurTableau = 0;
        for (int k = 0; k < 4; k++) if (largeurs[k] > 0) largeurTableau += largeurs[k] + ecart;
        int largeurTitres = 0;
        for (Morceau m : titres) largeurTitres = Math.max(largeurTitres, tr.getWidth(m.texte()));
        int largeurOrdre = 0;
        for (Morceau m : ordre) largeurOrdre += tr.getWidth(m.texte());
        int largeur = Math.max(Math.max(largeurTableau, largeurTitres), Math.max(largeurOrdre, tr.getWidth("TropiCalc Double")));

        int hauteurTotale = 2 * hauteurLigne + 4;
        for (List<Rangee> t : tableaux) hauteurTotale += (t.size() + 1) * hauteurLigne + 12;
        int y = Math.max(4, Math.min(120, client.getWindow().getScaledHeight() - hauteurTotale - 4));

        // En-tête général
        int hEntete = 2 * hauteurLigne + 4;
        context.fill(x - 4, y - 4, x + largeur + 4, y - 4 + hEntete, FOND);
        bordure(context, x - 4, y - 4, largeur + 8, hEntete, BORDURE_INACTIVE);
        context.drawText(tr, "TropiCalc Double", x, y, COULEUR_TITRE, true);
        y += hauteurLigne;
        int xo = x;
        for (Morceau m : ordre) {
            context.drawText(tr, m.texte(), xo, y, m.couleur(), true);
            xo += tr.getWidth(m.texte());
        }
        y += hauteurLigne + 6;

        for (int i = 0; i < tableaux.size(); i++) {
            List<Rangee> t = tableaux.get(i);
            int h = (t.size() + 1) * hauteurLigne + 6;
            boolean actif = actifs.get(i);
            context.fill(x - 4, y - 4, x + largeur + 4, y - 4 + h, FOND);
            bordure(context, x - 4, y - 4, largeur + 8, h, actif ? BORDURE_ACTIVE : BORDURE_INACTIVE);
            context.drawText(tr, titres.get(i).texte(), x, y, titres.get(i).couleur(), true);
            y += hauteurLigne;
            for (Rangee r : t) {
                int xc = x;
                for (int k = 0; k < 4; k++) {
                    if (largeurs[k] == 0) continue;
                    Morceau m = r.cellules()[k];
                    int couleur = actif ? m.couleur() : attenuer(m.couleur());
                    if (!m.texte().isEmpty()) context.drawText(tr, m.texte(), xc, y, couleur, true);
                    xc += largeurs[k] + ecart;
                }
                y += hauteurLigne;
            }
            y += 6;
        }
    }

    /** Dégâts d'une capacité sur une cible : "45-53% 2HKO", coloré selon le K.O. */
    private static Morceau cellule(Pokemon attaquant, Pokemon cible, com.tropimon.tropicalc.calc.Move capacite,
                                   Field field, Field.Ecrans ecrans, boolean multiple, boolean cibleAdverse) {
        field.setCibleMultiple(multiple);
        DamageCalculator.Resultat r;
        try {
            r = DamageCalculator.calculer(attaquant, cible, capacite, field, ecrans, false);
        } finally {
            field.setCibleMultiple(false);
        }
        if (r.immunise) return new Morceau("immunisé", COULEUR_GRIS);
        String ko = "";
        try {
            ko = CalcOverlay.texteKO(r, attaquant, cible, capacite, field, cibleAdverse).replace(" | ", " ");
        } catch (Exception ignored) {
        }
        String texte = String.format("%.0f-%.0f%%%s", r.pourcentageMin, r.pourcentageMax, ko);
        int couleur = r.koGaranti ? COULEUR_KO : r.koPossible ? COULEUR_KO_POSSIBLE : COULEUR_TEXTE;
        return new Morceau(texte, couleur);
    }

    /** "Ordre : Pyrax ~168 › Gorythmic 120 › ..." (les vitesses adverses sont estimées). */
    private static List<Morceau> ligneOrdre(List<ActifDouble> miens, List<Pokemon> mesPokemon,
                                            List<ActifDouble> adversesBase, List<Pokemon> adverses, Field field) {
        record Vit(String nom, int vitesse, boolean adverse) {
        }
        List<Vit> v = new ArrayList<>();
        for (int i = 0; i < mesPokemon.size(); i++) {
            Pokemon p = mesPokemon.get(i);
            if (p == null) continue;
            v.add(new Vit(CalcOverlay.nomEspece(p.getEspece()),
                (int) DamageCalculator.vitesseEnCombat(p, field.getMeteo(), field.getTerrain(), FieldTracker.isTailwindJoueur()),
                false));
        }
        for (Pokemon p : adverses) {
            if (p == null) continue;
            v.add(new Vit(CalcOverlay.nomEspece(p.getEspece()),
                (int) DamageCalculator.vitesseEnCombat(p, field.getMeteo(), field.getTerrain(), FieldTracker.isTailwindAdversaire()),
                true));
        }
        boolean distorsion = FieldTracker.isDistorsion();
        v.sort((a, b) -> distorsion ? Integer.compare(a.vitesse(), b.vitesse()) : Integer.compare(b.vitesse(), a.vitesse()));
        List<Morceau> r = new ArrayList<>();
        r.add(new Morceau("Ordre : ", COULEUR_TEXTE));
        for (int i = 0; i < v.size(); i++) {
            Vit x = v.get(i);
            if (i > 0) r.add(new Morceau(" › ", COULEUR_GRIS));
            r.add(new Morceau(x.nom() + " " + (x.adverse() ? "~" : "") + x.vitesse(), x.adverse() ? COULEUR_ADV : COULEUR_MOI));
        }
        if (distorsion) r.add(new Morceau("  [Distorsion]", COULEUR_GRIS));
        return r;
    }

    private static void appliquerStages(Pokemon p, String cle) {
        for (Stat s : Stat.values()) {
            if (s == Stat.PV) continue;
            p.setStage(s, BoostsDouble.stage(cle, s));
        }
    }

    private static String formatBoosts(Pokemon p) {
        String[] noms = {"Atq", "Déf", "AtqSpé", "DéfSpé", "Vit"};
        Stat[] stats = {Stat.ATTAQUE, Stat.DEFENSE, Stat.ATTAQUE_SPE, Stat.DEFENSE_SPE, Stat.VITESSE};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stats.length; i++) {
            int st = p.getStage(stats[i]);
            if (st == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(noms[i]).append(st > 0 ? "+" : "").append(st);
        }
        return sb.toString();
    }

    /** Boosts défensifs de l'adversaire, courts, dans l'en-tête de sa colonne. */
    private static String texteBoostsCourt(Pokemon p) {
        StringBuilder sb = new StringBuilder();
        int d = p.getStage(Stat.DEFENSE), ds = p.getStage(Stat.DEFENSE_SPE);
        if (d != 0) sb.append(" Déf").append(d > 0 ? "+" : "").append(d);
        if (ds != 0) sb.append(" DéfSpé").append(ds > 0 ? "+" : "").append(ds);
        return sb.toString();
    }

    private static int attenuer(int couleur) {
        int r = (couleur >> 16) & 0xFF, g = (couleur >> 8) & 0xFF, b = couleur & 0xFF;
        return ((r * 2 / 3) << 16) | ((g * 2 / 3) << 8) | (b * 2 / 3);
    }

    private static void bordure(DrawContext context, int x, int y, int w, int h, int couleur) {
        context.fill(x, y, x + w, y + 1, couleur);
        context.fill(x, y + h - 1, x + w, y + h, couleur);
        context.fill(x, y, x + 1, y + h, couleur);
        context.fill(x + w - 1, y, x + w, y + h, couleur);
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
}
