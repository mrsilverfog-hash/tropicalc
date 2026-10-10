package com.tropimon.tropicalc.calc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Contexte passé aux ItemModifier et AbilityModifier lors du calcul de
 * dégâts. Contient les informations du combat en cours ainsi que des
 * accumulateurs que chaque objet/talent remplit avant que DamageCalculator
 * ne les applique.
 *
 * Les modificateurs suivent exactement la formule du jeu (Showdown) : ils
 * sont exprimés en 4096e (6144 = x1,5 ; 5325 = x1,3 ; 4915 = x1,2...) et
 * rangés dans l'étape où le jeu les applique, chacune avec son propre
 * arrondi :
 *  - puissance : puissance de base (objets de type, Technicien, champs...) ;
 *  - attaque : stat offensive (Bandeau Choix, Force Pure, Isograisse...) ;
 *  - défense : stat défensive (Veste de Combat, Toison Épaisse...) ;
 *  - final : dégâts finaux (écrans, Orbe Vie, Multiécaille, Filtre...).
 * Mettre un bonus dans la mauvaise étape fausse les dégâts de 1 à 3 %,
 * assez pour se tromper sur un objet ou un K.O.
 *
 * Dans une étape, le jeu enchaîne les modificateurs dans un ordre fixe, avec
 * un arrondi à chaque maillon : chaque ajout porte donc son rang (constantes
 * ORDRE_*), et les modificateurs sont triés par rang avant d'être enchaînés.
 */
public class ModifierContext {

    public final Pokemon attaquant;
    public final Pokemon defenseur;
    public final Move capacite;
    public final Field terrain;
    public final boolean critique;

    /** Puissance de la capacité avant modificateurs (Technicien en dépend). */
    public int puissanceBase;

    public boolean stabAugmente = false;
    public boolean immuniteType = false;
    public boolean ignorerStagesDefenseur = false;
    public boolean ignorerStagesAttaquant = false;
    public boolean ignorerPenaliteBrulure = false;
    /** Agitation : x1,5 sur l'Attaque, appliqué à part avant les autres. */
    public boolean agitation = false;

    // Rangs dans l'ordre du jeu, étape puissance
    public static final int ORDRE_CAPACITE = 0;       // Façade, Sabotage, Saumure...
    public static final int ORDRE_CHAMP = 1;          // champ du lanceur (x1,3)
    public static final int ORDRE_CHAMP_DEFENSEUR = 2; // Brumeux / Herbu (x0,5)
    public static final int ORDRE_TECHNICIEN = 3;     // Technicien, Prognathe, Tranchant, Méga Blaster...
    public static final int ORDRE_AURA = 4;
    public static final int ORDRE_GRIFFE_DURE = 5;    // Griffe Dure, Force Sable, Sans Limite, Punk Rock
    public static final int ORDRE_PEAU = 6;           // Peau Céleste/Féérique..., Normalise
    public static final int ORDRE_POING_DE_FER = 7;   // Poing de Fer, Téméraire
    public static final int ORDRE_PEAU_SECHE = 8;
    public static final int ORDRE_GENERAL = 9;        // Général Suprême
    public static final int ORDRE_OBJET = 10;

    // Étape attaque
    public static final int ORDRE_TALENT_ATTAQUANT = 0;
    public static final int ORDRE_TALENT_DEFENSEUR = 1; // Isograisse, Aquabulle, Ignifugé...
    public static final int ORDRE_FLEAU = 2;            // Tablettes / Urne du Fléau, Épée / Perles
    public static final int ORDRE_PARADOXE = 3;         // Proto-Synthèse / Moteur Quark
    public static final int ORDRE_ORICHALQUE = 4;       // Pouls Orichalque / Moteur Hadron
    // ORDRE_OBJET réutilisé pour Bandeau / Lunettes Choix, Veste, Évoluroc

    // Étape finale
    public static final int ORDRE_ECRANS = 0;
    public static final int ORDRE_TALENT_FINAL = 1;     // Sniper, Lentiteintée, Cérébro-Force
    public static final int ORDRE_MULTIECAILLE = 2;
    public static final int ORDRE_BOULE_DE_POILS = 3;   // contact, Punk Rock (déf.), Écailles Glacées
    public static final int ORDRE_FILTRE = 4;
    public static final int ORDRE_POILS_FEU = 5;        // Boule de Poils contre le Feu
    // ORDRE_OBJET pour Ceinture Pro / Orbe Vie

    private record Mod(int ordre, int valeur) { }

    private final List<Mod> modsPuissance = new ArrayList<>();
    private final List<Mod> modsAttaque = new ArrayList<>();
    private final List<Mod> modsDefense = new ArrayList<>();
    private final List<Mod> modsFinal = new ArrayList<>();

    public ModifierContext(Pokemon attaquant, Pokemon defenseur, Move capacite, Field terrain, boolean critique) {
        this.attaquant = attaquant;
        this.defenseur = defenseur;
        this.capacite = capacite;
        this.terrain = terrain;
        this.critique = critique;
        this.puissanceBase = capacite.getPuissanceDeBase();
    }

    public void puissance(int ordre, int sur4096) { modsPuissance.add(new Mod(ordre, sur4096)); }
    public void attaque(int ordre, int sur4096) { modsAttaque.add(new Mod(ordre, sur4096)); }
    public void defense(int ordre, int sur4096) { modsDefense.add(new Mod(ordre, sur4096)); }
    public void degatsFinal(int ordre, int sur4096) { modsFinal.add(new Mod(ordre, sur4096)); }

    // Bornes du jeu pour chaque chaîne
    public int chainePuissance() { return chainer(modsPuissance, 41, 2097152); }
    public int chaineAttaque() { return chainer(modsAttaque, 410, 131072); }
    public int chaineDefense() { return chainer(modsDefense, 410, 131072); }
    public int chaineFinale() { return chainer(modsFinal, 41, 131072); }

    /** Enchaînement du jeu : M = (M x mod + 2048) >> 12 à chaque maillon. */
    private static int chainer(List<Mod> mods, int min, int max) {
        List<Mod> tries = new ArrayList<>(mods);
        tries.sort(Comparator.comparingInt(Mod::ordre));
        long m = 4096;
        for (Mod mod : tries) {
            if (mod.valeur() != 4096) m = (m * mod.valeur() + 2048) >> 12;
        }
        return (int) Math.max(min, Math.min(max, m));
    }

    /** Arrondi du jeu : au plus proche, un demi exact arrondi vers le bas. */
    public static long arrondiJeu(double x) {
        double frac = x - Math.floor(x);
        return frac > 0.5 ? (long) Math.ceil(x) : (long) Math.floor(x);
    }
}
