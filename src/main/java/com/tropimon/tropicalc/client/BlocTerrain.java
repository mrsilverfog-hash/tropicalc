package com.tropimon.tropicalc.client;

import com.tropimon.tropicalc.battle.FieldTracker;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloc « Terrain / Toi / Adv » du HUD : des pastilles colorées, une par effet,
 * avec les tours restants en petits carrés (pleins = tours restants, tour en
 * cours compris). 5 carrés au départ ; si l'effet dure au-delà, un objet le
 * prolonge (Roche, Lumargile, Champ'Duit) et on passe à 8.
 *
 * - Terrain : météo, champ, Distorsion, avec leur effet écrit en dessous.
 * - Toi / Adv : ce qui est de ton côté / du sien (écrans, Vent Arrière,
 *   Clone, pièges). Vert = bon pour toi, rouge = mauvais pour toi.
 *
 * Le bloc ne dessine rien lui-même : il produit des rectangles, des textes et
 * des icônes que le HUD dessine avec le reste (cadre adaptatif, retours à la
 * ligne).
 */
final class BlocTerrain {

    record Rect(int x, int y, int w, int h, int couleur) {}
    record Texte(String texte, int x, int y, int couleur) {}
    record Icone(Identifier id, int x, int y) {}

    final List<Rect> rects = new ArrayList<>();
    final List<Texte> textes = new ArrayList<>();
    final List<Icone> icones = new ArrayList<>();
    int hauteur = 0;

    private static final int GRIS_LABEL = 0xFFB4B2A9;
    private static final int GRIS_EFFET = 0xFFA3A199;
    private static final int LABEL_TOI = 0xFF9FE1CB;
    private static final int LABEL_ADV = 0xFFF09595;
    // Pastilles bonnes / mauvaises pour toi
    private static final int[] BON = {0xFF085041, 0xFF9FE1CB};
    private static final int[] MAUVAIS = {0xFF791F1F, 0xFFF7C1C1};

    private static Identifier icone(String nom) {
        return Identifier.of("tropicalc", "textures/gui/" + nom + ".png");
    }

    private static final Identifier I_SOLEIL = icone("soleil"), I_PLUIE = icone("pluie"), I_SABLE = icone("sable"),
        I_NEIGE = icone("neige"), I_ELEC = icone("electrique"), I_HERBU = icone("herbu"), I_PSY = icone("psychique"),
        I_BRUME = icone("brumeux"), I_DISTORSION = icone("distorsion"), I_MUR = icone("mur"), I_CLONE = icone("clone"),
        I_VENT = icone("vent"), I_ROC = icone("roc"), I_PICOTS = icone("picots"), I_PICS_TOXIK = icone("picstoxik"),
        I_TOILE = icone("toile");

    /** Une pastille : icône, nom, carrés des tours (si durée), effets écrits dessous. */
    private record Pastille(Identifier icone, String nom, FieldTracker.Duree duree, int fond, int texte,
                            List<String> effets) {}

    private final TextRenderer tr;
    private final int xDebut;
    private final int largeurMax;
    private final int hLigne;
    private int y;

    private BlocTerrain(TextRenderer tr, int x, int y, int largeurMax) {
        this.tr = tr;
        this.xDebut = x;
        this.y = y;
        this.largeurMax = largeurMax;
        this.hLigne = tr.fontHeight + 2;
    }

    /** Construit le bloc ; vide (hauteur 0) s'il n'y a aucun effet sur le terrain. */
    static BlocTerrain construire(TextRenderer tr, Field field, Pokemon joueur, Pokemon adversaire,
                                  int x, int y, int largeurMax) {
        BlocTerrain b = new BlocTerrain(tr, x, y, largeurMax);
        b.ligne("Terrain", GRIS_LABEL, pastillesTerrain(field, joueur, adversaire));
        b.ligne("Toi", LABEL_TOI, pastillesCamp(true));
        b.ligne("Adv", LABEL_ADV, pastillesCamp(false));
        b.hauteur = b.y - y;
        return b;
    }

    // ------------------------------------------------------------------
    // Contenu
    // ------------------------------------------------------------------

    private static List<Pastille> pastillesTerrain(Field field, Pokemon joueur, Pokemon adversaire) {
        List<Pastille> l = new ArrayList<>();
        FieldTracker.Duree dm = FieldTracker.dureeMeteo();
        switch (field.getMeteo()) {
            case SOLEIL -> l.add(new Pastille(I_SOLEIL, "Soleil", dm, 0xFF633806, 0xFFFAC775,
                List.of("Feu ×1,5 · Eau ×0,5")));
            case SOLEIL_INTENSE -> l.add(new Pastille(I_SOLEIL, "Soleil Intense", null, 0xFF633806, 0xFFFAC775,
                List.of("Feu ×1,5 · Eau sans effet")));
            case PLUIE -> l.add(new Pastille(I_PLUIE, "Pluie", dm, 0xFF0C447C, 0xFFB5D4F4,
                List.of("Eau ×1,5 · Feu ×0,5")));
            case PLUIE_INTENSE -> l.add(new Pastille(I_PLUIE, "Pluie Battante", null, 0xFF0C447C, 0xFFB5D4F4,
                List.of("Eau ×1,5 · Feu sans effet")));
            case SABLE -> {
                List<String> e = new ArrayList<>();
                e.add("-6 %/t sauf Roche, Sol, Acier");
                if (joueur.possedeType(PokemonType.ROCHE) || adversaire.possedeType(PokemonType.ROCHE)) {
                    e.add("Déf. Spé Roche ×1,5");
                }
                l.add(new Pastille(I_SABLE, "Sable", dm, 0xFF5A4A2C, 0xFFEAD7A8, e));
            }
            case NEIGE -> l.add(new Pastille(I_NEIGE, "Neige", dm, 0xFF27566A, 0xFFDDF2FA,
                List.of("Déf Glace ×1,5")));
            default -> { }
        }
        FieldTracker.Duree dt = FieldTracker.dureeTerrainActif();
        boolean auSol = DamageCalculator.estAuSol(joueur) || DamageCalculator.estAuSol(adversaire);
        String sol = auSol ? " (au sol)" : " (personne au sol)";
        switch (field.getTerrain()) {
            case ELECTRIQUE -> l.add(new Pastille(I_ELEC, "Électrique", dt, 0xFF5C4806, 0xFFFFE27A,
                List.of("Électrik ×1,3 · pas de sommeil" + sol)));
            case HERBU -> l.add(new Pastille(I_HERBU, "Herbu", dt, 0xFF27500A, 0xFFC0DD97,
                List.of("Plante ×1,3 · +6 %/t" + sol, "Séisme, Ampleur ×0,5")));
            case PSYCHIQUE -> l.add(new Pastille(I_PSY, "Psychique", dt, 0xFF72243E, 0xFFF4C0D1,
                List.of("Psy ×1,3 · pas de priorité" + sol)));
            case BRUMEUX -> l.add(new Pastille(I_BRUME, "Brumeux", dt, 0xFF4E3A66, 0xFFE9D8FA,
                List.of("Dragon ×0,5 · pas de statut" + sol)));
            default -> { }
        }
        if (FieldTracker.isDistorsion()) {
            l.add(new Pastille(I_DISTORSION, "Distorsion", FieldTracker.dureeDistorsion(), 0xFF3C3489, 0xFFCECBF6,
                List.of("les plus lents d'abord")));
        }
        return l;
    }

    /** Ce qui est de ton côté (joueur = true) ou du sien. */
    private static List<Pastille> pastillesCamp(boolean joueur) {
        List<Pastille> l = new ArrayList<>();
        int[] avantage = joueur ? BON : MAUVAIS;   // ses écrans / son vent : mauvais pour toi
        int[] piege = joueur ? MAUVAIS : BON;      // pièges de ton côté : mauvais pour toi
        if (joueur ? FieldTracker.joueurAReflet() : FieldTracker.adversaireAReflet()) {
            l.add(new Pastille(I_MUR, "Protection", FieldTracker.dureeProtection(joueur), avantage[0], avantage[1], List.of()));
        }
        if (joueur ? FieldTracker.joueurAMurLumiere() : FieldTracker.adversaireAMurLumiere()) {
            l.add(new Pastille(I_MUR, "Mur Lumière", FieldTracker.dureeMurLumiere(joueur), avantage[0], avantage[1], List.of()));
        }
        if (joueur ? FieldTracker.joueurAVoileAurore() : FieldTracker.adversaireAVoileAurore()) {
            l.add(new Pastille(I_MUR, "Voile Aurore", FieldTracker.dureeVoileAurore(joueur), avantage[0], avantage[1], List.of()));
        }
        if (joueur ? FieldTracker.isTailwindJoueur() : FieldTracker.isTailwindAdversaire()) {
            l.add(new Pastille(I_VENT, "Vent Arrière", FieldTracker.dureeVentArriere(joueur), avantage[0], avantage[1], List.of()));
        }
        if (joueur ? FieldTracker.joueurAUnClone() : FieldTracker.adversaireAUnClone()) {
            l.add(new Pastille(I_CLONE, "Clone", null, avantage[0], avantage[1], List.of()));
        }
        if (joueur ? FieldTracker.isStealthRockJoueur() : FieldTracker.isStealthRockAdversaire()) {
            l.add(new Pastille(I_ROC, "Pièges de Roc", null, piege[0], piege[1], List.of()));
        }
        int picots = joueur ? FieldTracker.getSpikesJoueur() : FieldTracker.getSpikesAdversaire();
        if (picots > 0) {
            l.add(new Pastille(I_PICOTS, "Picots ×" + picots, null, piege[0], piege[1], List.of()));
        }
        int toxik = joueur ? FieldTracker.getToxicSpikesJoueur() : FieldTracker.getToxicSpikesAdversaire();
        if (toxik > 0) {
            l.add(new Pastille(I_PICS_TOXIK, "Pics Toxik ×" + toxik, null, piege[0], piege[1], List.of()));
        }
        if (joueur ? FieldTracker.isStickyWebJoueur() : FieldTracker.isStickyWebAdversaire()) {
            l.add(new Pastille(I_TOILE, "Toile Gluante", null, piege[0], piege[1], List.of()));
        }
        return l;
    }

    // ------------------------------------------------------------------
    // Mise en page
    // ------------------------------------------------------------------

    private static final int ICONE = 9;
    private static final int CARRE = 3;
    private static final int LARGEUR_LABEL = 38;

    private int largeurPastille(Pastille p) {
        int w = 3 + ICONE + 2 + tr.getWidth(p.nom()) + 3;
        if (p.duree() != null && p.duree().actif()) w += 2 + p.duree().total() * (CARRE + 1);
        return w;
    }

    /** Une ligne étiquetée ; les pastilles passent à la ligne si elles dépassent. */
    private void ligne(String label, int couleurLabel, List<Pastille> pastilles) {
        if (pastilles.isEmpty()) return;
        textes.add(new Texte(label, xDebut, y + 1, couleurLabel));
        int x0 = xDebut + LARGEUR_LABEL;
        int x = x0;
        int hRangee = 0;
        for (Pastille p : pastilles) {
            int wp = largeurPastille(p);
            int wEffets = 0;
            for (String e : p.effets()) wEffets = Math.max(wEffets, tr.getWidth(e));
            int cellule = Math.max(wp, wEffets) + 4;
            if (x > x0 && x + cellule > xDebut + largeurMax) {
                y += hRangee;
                x = x0;
                hRangee = 0;
            }
            dessinerPastille(p, x, y, wp);
            int ye = y + hLigne;
            for (String e : p.effets()) {
                textes.add(new Texte(e, x + 1, ye, GRIS_EFFET));
                ye += hLigne - 1;
            }
            hRangee = Math.max(hRangee, ye - y + (p.effets().isEmpty() ? 1 : 2));
            x += cellule;
        }
        y += hRangee;
    }

    private void dessinerPastille(Pastille p, int x, int y, int w) {
        int h = hLigne;
        rects.add(new Rect(x, y - 1, w, h, p.fond()));
        icones.add(new Icone(p.icone(), x + 3, y));
        int xt = x + 3 + ICONE + 2;
        textes.add(new Texte(p.nom(), xt, y + 1, p.texte()));
        FieldTracker.Duree d = p.duree();
        if (d != null && d.actif()) {
            int xc = xt + tr.getWidth(p.nom()) + 3;
            int yc = y + (tr.fontHeight - CARRE) / 2 + 1;
            int vide = (p.texte() & 0x00FFFFFF) | 0x50000000;
            for (int i = 0; i < d.total(); i++) {
                rects.add(new Rect(xc + i * (CARRE + 1), yc, CARRE, CARRE, i < d.restants() ? p.texte() : vide));
            }
        }
    }

    // ------------------------------------------------------------------
    // Dessin direct (HUD double, sans tampon)
    // ------------------------------------------------------------------

    void dessiner(DrawContext context, int dy) {
        for (Rect r : rects) context.fill(r.x(), r.y() + dy, r.x() + r.w(), r.y() + dy + r.h(), r.couleur());
        for (Icone i : icones) context.drawTexture(i.id(), i.x(), i.y() + dy, ICONE, ICONE, 0, 0, 16, 16, 16, 16);
        for (Texte t : textes) context.drawText(tr, Text.literal(t.texte()), t.x(), t.y() + dy, t.couleur(), true);
    }

    int largeur() {
        int w = 0;
        for (Rect r : rects) w = Math.max(w, r.x() + r.w() - xDebut);
        for (Texte t : textes) w = Math.max(w, t.x() + tr.getWidth(t.texte()) - xDebut);
        return w;
    }
}
