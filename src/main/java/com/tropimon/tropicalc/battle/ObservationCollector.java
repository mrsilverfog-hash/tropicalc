package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Species;
import com.tropimon.tropicalc.calc.DamageCalculator;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Move;
import com.tropimon.tropicalc.calc.Nature;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ProfilAdversaire;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.SmogonDataLoader;
import com.tropimon.tropicalc.calc.Stat;
import com.tropimon.tropicalc.calc.StatHypothesis;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ObservationCollector {

    private ObservationCollector() {
    }

    private static final Map<String, ProfilAdversaire> PROFILS = new HashMap<>();
    private static final Map<String, LinkedHashSet<String>> COUPS_ADVERSAIRE = new HashMap<>();
    // Jeu de capacités COMPLET connu (copié par mon Métamorph) : plus de
    // capacités Smogon supposées pour cette espèce
    private static final Set<String> COUPS_ADVERSAIRE_COMPLETS = new HashSet<>();

    /**
     * Vrai dès qu'un Pokémon SAUVAGE (sans propriétaire) est détecté dans le
     * combat en cours. Signal confirmé par observation réelle : un Pokémon
     * sauvage apparaît comme "cobblemon.species.XXX.name" nu dans les
     * messages de combat, jamais enveloppé dans "owned_pokemon" comme
     * n'importe quel Pokémon de dresseur (joueur ou adversaire PvP).
     * Réinitialisé à chaque fin de combat.
     */
    private static boolean combatSauvageDetecte = false;

    public static boolean estCombatSauvage() {
        return combatSauvageDetecte;
    }

    public static void signalerPokemonSauvage() {
        combatSauvageDetecte = true;
    }

    // PP consommés par l'adversaire : espèce -> (id capacité -> PP utilisés)
    private static final Map<String, Map<String, Integer>> PP_UTILISES = new HashMap<>();
    private static final Set<String> OBJETS_RETIRES = new HashSet<>();

    // Objets confirmés par observation (ex: soin de fin de tour ~1/16 => Restes)
    private static final Map<String, String> OBJETS_CONFIRMES = new HashMap<>() {
        // Une confirmation annulée à la main (touche) ne peut plus revenir
        // pendant le combat, quel que soit le détecteur qui la reproduirait.
        @Override
        public String put(String espece, String objet) {
            Set<String> annules = OBJETS_ANNULES == null ? null : OBJETS_ANNULES.get(espece);
            if (annules != null && annules.contains(objet)) return get(espece);
            return super.put(espece, objet);
        }
    };
    // Pourquoi chaque objet a été confirmé (affiché dans le HUD).
    private static final Map<String, String> RAISONS_OBJET = new HashMap<>();
    // Confirmations annulées par le joueur pour ce combat : espèce -> objets refusés.
    private static final Map<String, Set<String>> OBJETS_ANNULES = new HashMap<>();

    /** Confirme un objet et retient pourquoi (affiché dans le HUD). */
    private static void confirmerObjet(String espece, String objet, String raison) {
        if (espece == null || objet == null) return;
        Set<String> annules = OBJETS_ANNULES.get(espece);
        if (annules != null && annules.contains(objet)) return;
        OBJETS_CONFIRMES.put(espece, objet);
        if (raison != null) RAISONS_OBJET.put(espece, raison);
        else RAISONS_OBJET.remove(espece);
    }

    public static String getRaisonObjet(String espece) {
        return RAISONS_OBJET.get(espece);
    }

    /**
     * Annule la confirmation d'objet de l'adversaire actif (touche dédiée).
     * Renvoie le message à afficher au joueur.
     */
    public static synchronized String annulerConfirmationAdversaire() {
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        if (adv == null) return "Aucun adversaire en combat";
        String espece = adv.getEspece();
        String objet = OBJETS_CONFIRMES.remove(espece);
        RAISONS_OBJET.remove(espece);
        if (objet != null) {
            OBJETS_ANNULES.computeIfAbsent(espece, k -> new HashSet<>()).add(objet);
            OBJETS_CONFIRMES_PROACTIVEMENT.remove(espece);
            MessageDebugLogger.analyse("ANNULATION par le joueur : " + objet + " sur " + espece);
            return "Confirmation annulée : " + objet + " (" + espece + ")";
        }
        if (OBJETS_RETIRES.remove(espece)) {
            MessageDebugLogger.analyse("ANNULATION par le joueur : objet retiré sur " + espece);
            return "Annulé : " + espece + " n'est plus considéré sans objet";
        }
        return "Aucun objet confirmé sur " + espece;
    }

    // Sous-ensemble de OBJETS_CONFIRMES : confirmé PROACTIVEMENT par simple
    // dominance statistique Smogon (>=80% d'usage), pas par une preuve réelle
    // en combat - donc révocable si une observation le contredit ensuite
    // (changement de capacité sans switch, ou dégâts/vitesse incompatibles).
    // Une confirmation par preuve réelle n'entre jamais dans cet ensemble et
    // n'est donc jamais révoquée.
    private static final Set<String> OBJETS_CONFIRMES_PROACTIVEMENT = new HashSet<>();

    private static String coupAdversaireTourPrecedent = null;

    // Capacités qui soignent leur utilisateur : excluent la confirmation de Restes
    private static final Set<String> COUPS_SOIN_OU_DRAIN = Set.of(
        "recover", "roost", "softboiled", "slackoff", "milkdrink", "moonlight",
        "morningsun", "synthesis", "shoreup", "rest", "wish", "healorder",
        "strengthsap", "junglehealing", "lifedew", "floralhealing",
        "absorb", "megadrain", "gigadrain", "leechlife", "drainpunch",
        "hornleech", "drainingkiss", "paraboliccharge", "oblivionwing",
        "dreameater", "bitterblade", "leechseed", "painsplit");

    // Baie de résistance par type - noms confirmés identiques en français
    // (wiki Cobblemon, Pokémon Trash). Chilan (Normal) fait exception : elle
    // divise par 2 les dégâts des capacités Normal même sans super efficacité.
    private static final Map<PokemonType, String> BAIES_RESISTANCE = Map.ofEntries(
        Map.entry(PokemonType.NORMAL, "Chilan"),
        Map.entry(PokemonType.FEU, "Occa"),
        Map.entry(PokemonType.EAU, "Passho"),
        Map.entry(PokemonType.ELECTRIK, "Wacan"),
        Map.entry(PokemonType.PLANTE, "Rindo"),
        Map.entry(PokemonType.GLACE, "Yache"),
        Map.entry(PokemonType.COMBAT, "Chople"),
        Map.entry(PokemonType.POISON, "Kebia"),
        Map.entry(PokemonType.SOL, "Shuca"),
        Map.entry(PokemonType.VOL, "Coba"),
        Map.entry(PokemonType.PSY, "Payapa"),
        Map.entry(PokemonType.INSECTE, "Tanga"),
        Map.entry(PokemonType.ROCHE, "Charti"),
        Map.entry(PokemonType.SPECTRE, "Kasib"),
        Map.entry(PokemonType.DRAGON, "Haban"),
        Map.entry(PokemonType.TENEBRES, "Colbur"),
        Map.entry(PokemonType.ACIER, "Babiri"),
        Map.entry(PokemonType.FEE, "Roseli")
    );

    // Vitesse minimale observée par espèce (déduite de l'ordre d'action)
    private static final Map<String, Integer> VITESSES_MIN_OBSERVEES = new HashMap<>();
    // Symétrique : si le joueur a prouvé agir avant l'adversaire (mêmes
    // garde-fous), sa vitesse réelle est plafonnée à vitesseJoueur-1. Sans
    // ça, une estimation Smogon par défaut peut rester trop haute même
    // après une preuve inverse claire en combat.
    private static final Map<String, Integer> VITESSES_MAX_OBSERVEES = new HashMap<>();
    private static final double TOLERANCE_POURCENT = 3.0;

    // Coups à priorité augmentée : l'ordre d'action ne reflète pas la vitesse
    private static final Set<String> COUPS_PRIORITAIRES = Set.of(
        "quickattack", "extremespeed", "aquajet", "bulletpunch", "machpunch",
        "iceshard", "shadowsneak", "suckerpunch", "accelerock", "vacuumwave",
        "jetpunch", "grassyglide", "firstimpression", "fakeout", "feint",
        "thunderclap", "upperhand", "protect", "detect",
        "banefulbunker", "silktrap", "burningbulwark", "spikyshield", "kingsshield",
        "obstruct", "endure", "trickroom"
    );

    /**
     * Nombre d'observations de vitesse exploitables par espèce. Une seule ne
     * suffit pas à confirmer : la Vive-Griffe fait passer en premier une fois
     * sur cinq au hasard, indépendamment de la vitesse, et aucun signal client
     * ne permet de la distinguer. Deux observations indépendantes ramènent ce
     * risque à 4%, et un vrai Mouchoir Choix se manifeste de toute façon à
     * chaque tour.
     */
    private static final Map<String, Integer> OBSERVATIONS_VITESSE = new HashMap<>();

    /**
     * Vrai si les deux capacités ont la MEME priorité, donc si l'ordre d'action
     * ne s'explique que par la vitesse.
     *
     * L'ancienne version testait l'appartenance à une liste de capacités
     * prioritaires écrite à la main. Elle ratait deux choses : les capacités
     * prioritaires absentes de la liste, et surtout les priorités NÉGATIVES
     * (Avalanche, Draco-Queue, Hurlement, Contre, Riposte, Vantardise...). Une
     * capacité à priorité négative côté joueur fait passer l'adversaire en
     * premier quelle que soit sa vitesse — et le mod en déduisait un Mouchoir
     * Choix. On lit donc la priorité réelle dans les données Cobblemon, ce qui
     * couvre les deux sens sans liste à maintenir.
     */
    private static boolean prioritesEgales(String coupJoueurId, String coupAdversaireId) {
        try {
            MoveTemplate a = Moves.INSTANCE.getByName(coupJoueurId);
            MoveTemplate b = Moves.INSTANCE.getByName(coupAdversaireId);
            if (a == null || b == null) return false; // inconnue : on s'abstient
            return a.getPriority() == b.getPriority();
        } catch (Throwable e) {
            // Repli sur l'ancienne liste si la donnée n'est pas accessible
            return !COUPS_PRIORITAIRES.contains(coupJoueurId)
                && !COUPS_PRIORITAIRES.contains(coupAdversaireId);
        }
    }


    private static double pvJoueurDebutTour = -1;
    private static double pvAdversaireDebutTour = -1;
    private static MoveUseTracker.CoupDetecte coupJoueurDuTour = null;
    private static MoveUseTracker.CoupDetecte coupAdversaireDuTour = null;
    private static Boolean adversaireAAgiEnPremier = null;
    private static String espaceAdversaireDuTour = null;

    // --- Fin de tour différée ---
    // Le serveur envoie la mise à jour des PV environ 1,5 s APRÈS le message
    // de la capacité, et le message "tour suivant" arrive juste après la
    // dernière capacité : mesurés à ce moment, les dégâts du dernier coup du
    // tour valaient 0 (3 Ébullilave sur Carchacrok "perte 0,0 %", Draco-Griffe
    // 1,8 % pour 20 % prévus). Les mesures de fin de tour attendent donc que
    // les PV soient à jour : 2,5 s, ou le prochain message de combat.
    private static long finDeTourEnAttenteMs = 0;
    private static final long DELAI_PV_MS = 2500;

    public static synchronized void signalerNouveauTour() {
        if (finDeTourEnAttenteMs != 0) traiterFinDeTour();
        finDeTourEnAttenteMs = System.currentTimeMillis();
    }

    /** Traite la fin de tour en attente (à appeler avant tout nouveau message). */
    public static synchronized void viderFinDeTourEnAttente() {
        if (finDeTourEnAttenteMs != 0) traiterFinDeTour();
    }

    private static synchronized void traiterFinDeTour() {
        finDeTourEnAttenteMs = 0;
        mettreAJourRepos();
        try {
            journaliserEstimation();
        } catch (Exception ignored) {
        }
        try {
            analyserCoupRecu();
        } catch (Exception ignored) {
        }
        try {
            CoupDonne cd = coupDonne;
            coupDonne = null;
            journaliserCoupDonne(cd);
        } catch (Exception ignored) {
        }
        try {
            CoupDonne cd = coupPrescience;
            coupPrescience = null;
            journaliserCoupDonne(cd);
        } catch (Exception ignored) {
        }
        try {
            journaliserPrescience();
        } catch (Exception ignored) {
        }
        coupRecuTourEcoule = coupRecu;
        coupRecu = null;
        joueurBloqueCeTour = false;
        stageAtkAdvTourEcoule = stageAtkAdvDebutTour;
        stageVitAdvTourEcoule = stageVitAdvDebutTour;
        stageVitJoueurTourEcoule = stageVitJoueurDebutTour;
        tailwindJoueurTourEcoule = tailwindJoueurDebutTour;
        tailwindAdvTourEcoule = tailwindAdvDebutTour;
        distorsionTourEcoule = distorsionDebutTour;
        statutJoueurTourEcoule = statutJoueurDebutTour;
        statutAdvTourEcoule = statutAdvDebutTour;
        especeJoueurTourEcoule = especeJoueurDebutTour;
        joueurPokemonTourEcoule = joueurPokemonDebutTour;
        adversairePokemonTourEcoule = adversairePokemonDebutTour;
        stageVitJoueurDebutTour = BoostTracker.getStageJoueur(Stat.VITESSE);
        tailwindJoueurDebutTour = FieldTracker.isTailwindJoueur();
        tailwindAdvDebutTour = FieldTracker.isTailwindAdversaire();
        distorsionDebutTour = FieldTracker.isDistorsion();
        try {
            Pokemon jDebut = BattleStateTracker.getJoueurActif();
            Pokemon aDebut = BattleStateTracker.getAdversaireActif();
            statutJoueurDebutTour = jDebut != null ? jDebut.getStatut() : null;
            especeJoueurDebutTour = jDebut != null ? jDebut.getEspece() : null;
            statutAdvDebutTour = aDebut != null ? aDebut.getStatut() : null;
            Pokemon jEquipe = BattleStateTracker.getJoueurActifDepuisEquipe();
            joueurPokemonDebutTour = jEquipe != null ? jEquipe : jDebut;
            adversairePokemonDebutTour = aDebut;
        } catch (Exception ignored) {
        }
        stageAtkSpeAdvTourEcoule = stageAtkSpeAdvDebutTour;
        stageDefJoueurTourEcoule = stageDefJoueurDebutTour;
        stageDefSpeJoueurTourEcoule = stageDefSpeJoueurDebutTour;
        meteoTourEcoule = meteoDebutTour;
        terrainTourEcoule = terrainDebutTour;
        ecransJoueurTourEcoule = ecransJoueurDebutTour;
        try {
            Field etat = FieldTracker.construireField();
            meteoDebutTour = etat.getMeteo();
            terrainDebutTour = etat.getTerrain();
        } catch (Exception ignored) {
        }
        ecransJoueurDebutTour = FieldTracker.signatureEcransJoueur();
        Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (joueur == null) joueur = BattleStateTracker.getJoueurActif();
        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        if (joueur == null || adversaire == null) return;

        double pvJoueurMaintenant = joueur.getPourcentagePv();
        double pvAdversaireMaintenant = adversaire.getPourcentagePv();

        // La Vampigraine et la Salaison ne survivent pas au switch du joueur
        if (especeJoueurSuivie != null && !especeJoueurSuivie.equals(joueur.getEspece())) {
            joueurVampigraine = false;
            joueurSalaison = false;
            compteurToxikJoueur = 0;
        }
        especeJoueurSuivie = joueur.getEspece();

        // Idem côté adverse (espaceAdversaireDuTour contient encore l'espèce du tour passé)
        if (espaceAdversaireDuTour != null && !espaceAdversaireDuTour.equals(adversaire.getEspece())) {
            adversaireVampigraine = false;
            adversaireSalaison = false;
            compteurToxikAdversaire = 0;
            coupVerrouAdversaire = null;   // le verrou Choix tombe au switch
            compteurAbrisAdversaire = 0;
        }
        // (Plus de révocation du Choix par comparaison tour à tour : avec un
        // Demi-Tour, la capacité « du tour précédent » était celle du Pokémon
        // reparti. Log du 12/10 : Lunettes Choix de Dracolosse confirmées puis
        // annulées par le Demi-Tour de Pyrobut. suivreVerrouChoix compare les
        // capacités d'un même séjour, à partir des messages du jeu.)

        // Abris consécutifs de l'adversaire (le 2e n'a que ~33% de réussite)
        if (coupAdversaireDuTour != null) {
            if (COUPS_PROTECTION.contains(coupAdversaireDuTour.showdownId())) {
                compteurAbrisAdversaire++;
            } else {
                compteurAbrisAdversaire = 0;
            }
        }

        FieldTracker.nouveauTour();

        // Vulné-Assurance : +2 Attaque ET +2 Attaque Spé simultanément après avoir
        // subi un coup super efficace - signature quasi unique (seule autre source
        // connue : Croissance sous soleil, explicitement exclue ci-dessous).
        tenterConfirmerVulneAssurance(adversaire, joueur);
        tenterConfirmerDefiantBattant(adversaire);
        stageAtkAdvDebutTour = BoostTracker.getStageAdversaire(Stat.ATTAQUE);
        stageAtkSpeAdvDebutTour = BoostTracker.getStageAdversaire(Stat.ATTAQUE_SPE);
        stageDefAdvDebutTour = BoostTracker.getStageAdversaire(Stat.DEFENSE);
        stageDefSpeAdvDebutTour = BoostTracker.getStageAdversaire(Stat.DEFENSE_SPE);
        stageVitAdvDebutTour = BoostTracker.getStageAdversaire(Stat.VITESSE);
        stageDefJoueurDebutTour = BoostTracker.getStageJoueur(Stat.DEFENSE);
        stageDefSpeJoueurDebutTour = BoostTracker.getStageJoueur(Stat.DEFENSE_SPE);

        // Compteurs Toxik : +1 par tour passé empoisonné gravement (reset au switch/soin)
        if (joueur.getStatut() == Pokemon.Statut.POISON_GRAVE) compteurToxikJoueur++;
        else compteurToxikJoueur = 0;
        if (adversaire.getStatut() == Pokemon.Statut.POISON_GRAVE) compteurToxikAdversaire++;
        else compteurToxikAdversaire = 0;

        if (pvJoueurDebutTour >= 0 && pvAdversaireDebutTour >= 0) {
            double perteJoueur = pvJoueurDebutTour - pvJoueurMaintenant;
            double perteAdversaire = pvAdversaireDebutTour - pvAdversaireMaintenant;

            // Poing de Colère : +1 coup subi (max 6) par capacité offensive
            // qui a réellement touché - persiste toute la durée du combat.
            // Limite acceptée : si le Clone se brise le MÊME tour que ce
            // coup, notre état "Clone actif" reflète déjà l'après-coup (le
            // message end.substitute arrive avant ce traitement), donc ce
            // cas très marginal peut sous-estimer le compteur d'un coup -
            // jamais le surestimer.
            if (perteJoueur > 0 && coupAdversaireDuTour != null && !adversaireNAPasAttaque()
                    && !FieldTracker.joueurAUnClone()) {
                String esp = joueur.getEspece();
                COUPS_RAGE_FIST_JOUEUR.merge(esp, 1, (a, b) -> Math.min(6, a + b));
            }
            if (perteAdversaire > 0 && coupJoueurDuTour != null && !joueurNAPasAttaque()
                    && !FieldTracker.adversaireAUnClone()) {
                String esp = adversaire.getEspece();
                COUPS_RAGE_FIST_ADVERSAIRE.merge(esp, 1, (a, b) -> Math.min(6, a + b));
            }

            if (perteAdversaire < -5.0 || perteJoueur < -5.0) {
                // Soin adverse de ~1/16 sans switch ni capacité de soin : Restes confirmés
                // (Vampigraine/Vœu soignent 1/8+ et le tour d'après pour Vœu : exclus)
                // (Restes : plus déduits des PV, le jeu les annonce)
                if (coupAdversaireDuTour != null) {
                    coupAdversaireTourPrecedent = coupAdversaireDuTour.showdownId();
                }
                pvJoueurDebutTour = pvJoueurMaintenant;
                pvAdversaireDebutTour = pvAdversaireMaintenant;
                espaceAdversaireDuTour = adversaire.getEspece();
                coupJoueurDuTour = null;
                coupAdversaireDuTour = null;
                critCeTour = false;
                prioriteObjetCeTour = false;
                degatsAnnexesJoueurCeTour = false;
                attaqueDiffereeCeTour = false;
                multiCoupsCeTour = false;
                adversaireAAgiEnPremier = null;
        especeAdvActeur = null;
        especeJoueurActeur = null;
        reculOrbeVieAdvCeTour = false;
        coupAdvRateCeTour = false;
                return;
            }

            if (coupJoueurDuTour != null && "knockoff".equals(coupJoueurDuTour.showdownId())
                    && perteAdversaire >= 0.5
                    && !"Glu".equals(adversaire.getTalent())) {
                OBJETS_RETIRES.add(adversaire.getEspece());
            }

            // Ballon adverse : plus de déduction "Ballon estimé + touché =
            // Ballon éclaté". Elle marquait "objet retiré" sur des Pokémon
            // qui n'avaient jamais eu de Ballon (Gromago, estimé Ballon par
            // Smogon, touché par Boutefeu). Le jeu annonce le Ballon à
            // l'entrée et son éclatement (enditem.airballoon) : ce sont les
            // seules sources utilisées, voir traiterMessageObjet.

            // Symétrique, pour MON propre Ballon. Même si joueur.getObjet()
            // devrait en théorie déjà refléter la vraie destruction de
            // l'objet (lu en direct depuis Cobblemon, pas une estimation),
            // un suivi de secours évite tout souci si la mise à jour n'est
            // pas immédiate côté client - appliqué aux DEUX écrans via
            // appliquerObjetReelJoueur.
            if ("Ballon".equals(joueur.getObjet()) && coupAdversaireDuTour != null
                    && !adversaireNAPasAttaque() && perteJoueur > 0) {
                marquerObjetJoueurDetruit(joueur.getEspece());
            }

            // Casque Brut et Orbe Vie : plus déduits des pertes de PV. Le jeu
            // annonce les deux (damage.rockyhelmet, damage.lifeorb), lus dans
            // MoveUseTracker et traiterMessageObjet. La déduction par les PV
            // a confirmé une Orbe Vie sur un Carchacrok qui n'en avait pas
            // (11/10), alors qu'il avait attaqué sans recul annoncé.

            // Vitesse : ordre d'action du tour écoulé, évalué avec l'état du
            // DÉBUT de ce tour (stages, météo, champ, Vent Arrière, statut).
            observerVitesse(adversaire, joueur);

            tenterExclureOrbeVie(adversaire, perteJoueur);

            // Morphing : stats, types et talent copiés, les calculs ne
            // correspondent plus au vrai Pokémon - aucune observation.
            boolean morphing = joueurTransforme() || estTransforme(true, adversaire.getEspece());
            if (coupAdversaireDuTour != null && perteJoueur >= 0.5 && !morphing) {
                enregistrerObservation(true, perteJoueur, adversaire, joueur, coupAdversaireDuTour);
            }
            if (coupJoueurDuTour != null && perteAdversaire >= 0.5 && !morphing) {
                enregistrerObservation(false, perteAdversaire, adversaire, joueur, coupJoueurDuTour);
            }
        }

        pvJoueurDebutTour = pvJoueurMaintenant;
        pvAdversaireDebutTour = pvAdversaireMaintenant;
        espaceAdversaireDuTour = adversaire.getEspece();
        if (coupAdversaireDuTour != null) {
            coupAdversaireTourPrecedent = coupAdversaireDuTour.showdownId();
        }
        coupJoueurDuTour = null;
        coupAdversaireDuTour = null;
        critCeTour = false;
        prioriteObjetCeTour = false;
        degatsAnnexesJoueurCeTour = false;
        attaqueDiffereeCeTour = false;
        multiCoupsCeTour = false;
        adversaireAAgiEnPremier = null;
        especeAdvActeur = null;
        especeJoueurActeur = null;
        reculOrbeVieAdvCeTour = false;
        coupAdvRateCeTour = false;

        tenterAppliquerHerbeBlanche(joueur, adversaire);
    }

    public static synchronized void signalerCoupUtilise(MoveUseTracker.CoupDetecte coup) {
        // Change-Côté : effet symétrique, peu importe qui la joue - un seul
        // appel suffit, peu importe le camp.
        if ("courtchange".equals(coup.showdownId())) {
            FieldTracker.echangerCotesSiReussi();
        }

        Boolean estAdversaire = determinerAttaquant(coup.proprietaire());

        // Premier coup du tour = camp qui agit en premier
        if (adversaireAAgiEnPremier == null && estAdversaire != null) {
            adversaireAAgiEnPremier = estAdversaire;
        }
        // Qui a réellement agi de chaque côté (avant un éventuel Demi-Tour)
        try {
            if (Boolean.TRUE.equals(estAdversaire) && especeAdvActeur == null) {
                Pokemon a = BattleStateTracker.getAdversaireActif();
                if (a != null) especeAdvActeur = a.getEspece();
            } else if (Boolean.FALSE.equals(estAdversaire) && especeJoueurActeur == null) {
                Pokemon j = BattleStateTracker.getJoueurActif();
                if (j != null) especeJoueurActeur = j.getEspece();
            }
        } catch (Exception ignored) {
        }

        if (Boolean.TRUE.equals(estAdversaire)) {
            coupAdversaireDuTour = coup;
            if ("rest".equals(coup.showdownId())) {
                Pokemon a = BattleStateTracker.getAdversaireActif();
                if (a != null) reposEnAttenteAdv = a.getEspece();
            }
            if ("leechseed".equals(coup.showdownId())) {
                joueurVampigraine = true;
            }
            if ("saltcure".equals(coup.showdownId())) {
                joueurSalaison = true;
            }
            if (coup.proprietaire() != null) {
                nomAdversaireCourant = coup.proprietaire();
            }
            coupVerrouAdversaire = coup.showdownId();
            Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            if (adversaire != null) {
                ajouterCapaciteAdversaire(adversaire.getEspece(), coup.showdownId(), true);

                // Comptage des PP : Pression (talent du joueur) ajoute 1 PP,
                // mais seulement si la capacité CIBLE le Pokémon qui a Pression
                // (Abri, Soin, Vœu, Piège de Roc etc. ne sont pas affectés)
                int cout = 1;
                Pokemon joueurActif = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (joueurActif == null) joueurActif = BattleStateTracker.getJoueurActif();
                if (joueurActif != null && "Pression".equals(joueurActif.getTalent())
                    && cibleLAdversaire(coup.showdownId())) {
                    cout = 2;
                }
                PP_UTILISES
                    .computeIfAbsent(adversaire.getEspece(), k -> new HashMap<>())
                    .merge(coup.showdownId(), cout, Integer::sum);
            }
        } else {
            coupJoueurDuTour = coup;
            if ("leechseed".equals(coup.showdownId())) {
                adversaireVampigraine = true;
            }
            if ("saltcure".equals(coup.showdownId())) {
                adversaireSalaison = true;
            }
            // Repos : le compteur ne démarre qu'au tour suivant, et seulement si
            // le Pokémon est vraiment endormi (voir mettreAJourRepos).
            if ("rest".equals(coup.showdownId())) {
                Pokemon actif = BattleStateTracker.getJoueurActif();
                if (actif != null) reposEnAttente = actif.getEspece();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Compteur de Repos (Pokémon du joueur)
    //
    // Mécanique (Bulbapedia/Poképédia, gén. 6+) : après le tour où il lance
    // Repos, le Pokémon dort 2 tours puis se réveille et agit au 3e. Avec
    // Matinal, un seul tour de sommeil. Le compteur avance sur les tours où le
    // Pokémon TENTE d'agir (Blabla Dodo/Ronflement compris), pas sur les tours
    // passés hors du terrain, et il n'est pas remis à zéro quand il sort.
    // Modélisé comme Showdown : compteur 3 au départ, -1 par tentative (-2 avec
    // Matinal), il agit à la tentative qui le fait tomber à 0.
    // ---------------------------------------------------------------------
    private record EtatRepos(int compteur, int pas) {}
    private static final Map<String, EtatRepos> COMPTEUR_REPOS = new HashMap<>();
    private static String reposEnAttente = null;
    // Même compteur côté adversaire. matinalPossible : talent inconnu mais
    // Matinal possible chez l'espèce (un tour de sommeil en moins).
    private static final Map<String, EtatRepos> COMPTEUR_REPOS_ADV = new HashMap<>();
    private static final Set<String> REPOS_ADV_MATINAL_POSSIBLE = new HashSet<>();
    private static String reposEnAttenteAdv = null;
    private static String especeAdvActiveDebutTour = null;
    private static String especeActiveDebutTour = null;
    private static int numeroTour = 0;

    public static void setNumeroTour(int n) { numeroTour = n; }
    public static int getNumeroTour() { return numeroTour; }

    private static void mettreAJourRepos() {
        // Statut lu côté combat (mis à jour en direct), pas depuis l'équipe.
        Pokemon actif = BattleStateTracker.getJoueurActif();
        String espece = actif != null ? actif.getEspece() : null;
        boolean endormi = actif != null && actif.getStatut() == Pokemon.Statut.SOMMEIL;

        // 1. Le tour écoulé compte-t-il comme une tentative d'action ? Oui si le
        //    Pokémon endormi était sur le terrain au début du tour et y est encore
        //    (pas de switch), ou s'il a utilisé une capacité (Blabla Dodo qui
        //    lance Demi-Tour, par exemple).
        if (especeActiveDebutTour != null) {
            EtatRepos etat = COMPTEUR_REPOS.get(especeActiveDebutTour);
            if (etat != null && (especeActiveDebutTour.equals(espece) || coupJoueurDuTour != null)) {
                int restant = etat.compteur() - etat.pas();
                if (restant <= 0) COMPTEUR_REPOS.remove(especeActiveDebutTour);
                else COMPTEUR_REPOS.put(especeActiveDebutTour, new EtatRepos(restant, etat.pas()));
            }
        }

        // 2. Repos lancé au tour écoulé : on ne démarre que s'il a vraiment
        //    endormi (Repos échoue à PV pleins, sous Champ Électrifié/Brumeux,
        //    avec Insomnie...). Jamais de redémarrage si un compteur existe déjà
        //    (Repos tiré par Blabla Dodo échoue).
        if (reposEnAttente != null) {
            if (reposEnAttente.equals(espece) && endormi && !COMPTEUR_REPOS.containsKey(espece)) {
                COMPTEUR_REPOS.put(espece, new EtatRepos(3, "Matinal".equals(actif.getTalent()) ? 2 : 1));
            }
            reposEnAttente = null;
        }

        // 3. Réveil anticipé (Baie Maron, Baie Prine, Hydratation, Mue, Glas de
        //    Soin...) : plus endormi = plus de compteur.
        if (espece != null && !endormi) COMPTEUR_REPOS.remove(espece);

        especeActiveDebutTour = espece;

        // --- Même chose pour l'adversaire ---
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        String especeAdv = adv != null ? adv.getEspece() : null;
        boolean advEndormi = adv != null && adv.getStatut() == Pokemon.Statut.SOMMEIL;
        if (especeAdvActiveDebutTour != null) {
            EtatRepos etat = COMPTEUR_REPOS_ADV.get(especeAdvActiveDebutTour);
            if (etat != null && (especeAdvActiveDebutTour.equals(especeAdv) || coupAdversaireDuTour != null)) {
                int restant = etat.compteur() - etat.pas();
                if (restant <= 0) COMPTEUR_REPOS_ADV.remove(especeAdvActiveDebutTour);
                else COMPTEUR_REPOS_ADV.put(especeAdvActiveDebutTour, new EtatRepos(restant, etat.pas()));
            }
        }
        if (reposEnAttenteAdv != null) {
            if (reposEnAttenteAdv.equals(especeAdv) && advEndormi && !COMPTEUR_REPOS_ADV.containsKey(especeAdv)) {
                String talent = TALENTS_CONFIRMES.get(especeAdv);
                boolean matinal = "Matinal".equals(talent);
                COMPTEUR_REPOS_ADV.put(especeAdv, new EtatRepos(3, matinal ? 2 : 1));
                Set<String> reels = getTalentsReelsEspece(adv);
                if (talent == null && reels != null && reels.contains("Matinal")) REPOS_ADV_MATINAL_POSSIBLE.add(especeAdv);
                else REPOS_ADV_MATINAL_POSSIBLE.remove(especeAdv);
            }
            reposEnAttenteAdv = null;
        }
        if (especeAdv != null && !advEndormi) COMPTEUR_REPOS_ADV.remove(especeAdv);
        especeAdvActiveDebutTour = especeAdv;
    }

    /** Comme getToursAvantReveilRepos, pour l'adversaire. -1 = pas de Repos suivi. */
    public static int getToursAvantReveilReposAdversaire(String espece) {
        EtatRepos e = espece == null ? null : COMPTEUR_REPOS_ADV.get(espece);
        if (e == null) return -1;
        return (e.compteur() + e.pas() - 1) / e.pas();
    }

    public static boolean isReposAdversaireMatinalPossible(String espece) {
        return REPOS_ADV_MATINAL_POSSIBLE.contains(espece);
    }

    /**
     * Nombre de tours, celui qui commence compris, avant que ce Pokémon agisse
     * à nouveau : 1 = il se réveille et agit ce tour-ci. -1 = pas de Repos suivi.
     */
    public static int getToursAvantReveilRepos(String espece) {
        EtatRepos e = espece == null ? null : COMPTEUR_REPOS.get(espece);
        if (e == null) return -1;
        return (e.compteur() + e.pas() - 1) / e.pas();
    }


    // ---------------------------------------------------------------------
    // Vitesse : Mouchoir Choix par impossibilité, plancher/plafond affichés.
    //
    // Preuve uniquement par la vitesse, jamais par le verrou de capacité.
    // Si l'adversaire a agi avant moi à priorité égale, sa vitesse était au
    // moins égale à la mienne (égalité possible : pas de +1). Si même son
    // MAXIMUM sans objet (252 EV, nature +Vit, meilleur talent possible,
    // stage, météo, champ et Vent Arrière du début du tour) reste sous ma
    // vitesse, seul un Mouchoir Choix l'explique. Une seule observation
    // suffit : Vive-Griffe et Tir Vif sont annoncés par le jeu et écartés.
    // ---------------------------------------------------------------------
    private static void observerVitesse(Pokemon adversaire, Pokemon joueur) {
        if (coupJoueurDuTour == null) {
            observerVitesseSansMonAction(adversaire);
            return;
        }
        if (coupAdversaireDuTour == null || adversaireAAgiEnPremier == null) return;
        String debut = "VITESSE " + adversaire.getEspece() + " " + coupAdversaireDuTour.showdownId()
            + " / moi " + joueur.getEspece() + " " + coupJoueurDuTour.showdownId() + " | "
            + (Boolean.TRUE.equals(adversaireAAgiEnPremier) ? "il a agi avant" : "j'ai agi avant") + " | ";
        if (prioriteObjetCeTour) { MessageDebugLogger.analyse(debut + "Vive-Griffe/Tir Vif : ignoré"); return; }
        if (estTransforme(true, adversaire.getEspece()) || joueurTransforme()) {
            MessageDebugLogger.analyse(debut + "Morphing : ignoré");
            return;
        }
        if (distorsionTourEcoule) { MessageDebugLogger.analyse(debut + "Distorsion : ignoré"); return; }
        // Le même Pokémon de chaque côté du début à la fin du tour, sinon
        // l'ordre observé ne concerne pas celui qu'on regarde (Demi-Tour...).
        String espece = adversaire.getEspece();
        if (!espece.equals(espaceAdversaireDuTour) || joueurAChangeCeTour()) {
            // Demi-Tour, Change Éclair, Flip Turn, Demi-Tour forcé... : si les
            // deux Pokémon qui ont agi sont bien ceux du début du tour, le
            // changement est venu APRÈS leurs actions et l'ordre reste valable.
            // On mesure alors avec ces deux Pokémon-là, pas avec les actifs.
            boolean memesActeurs = especeAdvActeur != null && especeAdvActeur.equals(espaceAdversaireDuTour)
                && especeJoueurActeur != null && especeJoueurActeur.equals(especeJoueurTourEcoule)
                && adversairePokemonTourEcoule != null && joueurPokemonTourEcoule != null
                && especeAdvActeur.equals(adversairePokemonTourEcoule.getEspece())
                && especeJoueurActeur.equals(joueurPokemonTourEcoule.getEspece());
            if (!memesActeurs) {
                MessageDebugLogger.analyse(debut + "switch avant d'agir : ignoré");
                return;
            }
            adversaire = adversairePokemonTourEcoule;
            joueur = joueurPokemonTourEcoule;
            espece = adversaire.getEspece();
            debut = "VITESSE " + espece + " " + coupAdversaireDuTour.showdownId() + " / moi " + joueur.getEspece()
                + " " + coupJoueurDuTour.showdownId() + " | "
                + (Boolean.TRUE.equals(adversaireAAgiEnPremier) ? "il a agi avant" : "j'ai agi avant")
                + " (switch après les actions) | ";
        }

        String idJ = coupJoueurDuTour.showdownId();
        String idA = coupAdversaireDuTour.showdownId();
        if (!prioritesEgales(idJ, idA)) { MessageDebugLogger.analyse(debut + "priorités différentes : ignoré"); return; }
        if (prioriteModifiee(idA, talentsBrutsPossibles(adversaire)) || prioriteModifiee(idJ, talentBrutJoueur(joueur))) {
            MessageDebugLogger.analyse(debut + "priorité donnée par un talent ou un champ : ignoré");
            return;
        }

        double vJoueur = vitesseJoueurTourEcoule(joueur);
        if (vJoueur <= 0) return;
        if (Boolean.FALSE.equals(adversaireAAgiEnPremier)) {
            MessageDebugLogger.analyse(debut + "ma vitesse " + (int) vJoueur + " => sa vitesse <= " + (int) vJoueur);
        }

        if (Boolean.TRUE.equals(adversaireAAgiEnPremier)) {
            conclureAdversairePlusRapide(adversaire, vJoueur);
        } else {
            // J'ai agi avant : sa vitesse était au plus égale à la mienne.
            // Plafond affiché valable seulement si elle n'était pas réduite
            // à ce moment (stage négatif, paralysie) ni ralentie par un talent.
            Set<String> bruts = talentsBrutsPossibles(adversaire);
            if (stageVitAdvTourEcoule < 0 || statutAdvTourEcoule == Pokemon.Statut.PARALYSIE
                    || bruts.contains("stall") || bruts.contains("myceliummight")) return;
            VITESSES_MAX_OBSERVEES.merge(espece, (int) vJoueur, Math::min);
        }
    }

    /**
     * L'adversaire a été au moins aussi rapide que moi (vJoueur = ma vitesse) :
     * plancher affiché, puis Mouchoir si aucune version sans objet ne l'explique.
     */
    private static void conclureAdversairePlusRapide(Pokemon adversaire, double vJoueur) {
        String espece = adversaire.getEspece();
        StringBuilder log = new StringBuilder("VITESSE " + espece + " plus rapide que moi (" + (int) vJoueur + ")");
        try {
            conclureAdversairePlusRapide(adversaire, vJoueur, log);
        } finally {
            MessageDebugLogger.analyse(log.toString());
        }
    }

    private static void conclureAdversairePlusRapide(Pokemon adversaire, double vJoueur, StringBuilder log) {
        String espece = adversaire.getEspece();
        // Plancher affiché : seulement si sa vitesse n'était pas gonflée.
        if (stageVitAdvTourEcoule <= 0 && !tailwindAdvTourEcoule) {
            VITESSES_MIN_OBSERVEES.merge(espece, (int) vJoueur, Math::max);
        }
        if (OBJETS_CONFIRMES.containsKey(espece)) {
            log.append(" | objet déjà confirmé : ").append(OBJETS_CONFIRMES.get(espece));
            return;
        }
        if (OBJETS_RETIRES.contains(espece) || OBJETS_CHOIX_EXCLUS.contains(espece)) {
            log.append(" | objet retiré ou Choix exclu");
            return;
        }
        // 1. Même sa version la plus rapide sans objet (252 EV, nature +Vitesse,
        //    meilleur talent) est plus lente que moi : certain.
        double plafond = plafondVitesseSansObjet(adversaire, 252, Nature.TIMIDE);
        log.append(" | son max sans objet ").append((int) plafond).append(" (stage ").append(stageVitAdvTourEcoule)
            .append(tailwindAdvTourEcoule ? ", Vent Arrière" : "").append(')');
        if (plafond > 0 && vJoueur > plafond * 1.02) {   // 2 % pour les arrondis
            confirmerObjet(espece, "Mouchoir Choix", "plus rapide que toi (" + (int) vJoueur
                + ") ; max sans objet " + (int) plafond);
            log.append(" | => Mouchoir Choix CONFIRMÉ (impossible sans objet)");
            return;
        }
        // 2. Presque aucun réglage de vitesse joué par cette espèce (Smogon) ne
        //    m'atteint sans objet, et le Mouchoir y est nettement plus courant
        //    que ces réglages rapides : Mouchoir.
        double rapides = proportionReglagesAssezRapides(adversaire, vJoueur);
        double mouchoir = SmogonDataLoader.fractionObjet(espece, "choicescarf");
        log.append(" | Smogon : ").append(pct(rapides * 100)).append(" des sets m'atteignent sans objet, Mouchoir ")
            .append(mouchoir < 0 ? "inconnu" : pct(mouchoir * 100));
        if (rapides < 0.05 && mouchoir >= 0.05 && mouchoir >= 3 * rapides) {
            confirmerObjet(espece, "Mouchoir Choix", "plus rapide que toi (" + (int) vJoueur + ") ; "
                + Math.round(rapides * 100) + " % des sets Smogon y arrivent sans objet");
            log.append(" | => Mouchoir Choix CONFIRMÉ (Smogon)");
        } else {
            log.append(" | explicable sans Mouchoir");
        }
    }

    /**
     * Tour où je n'ai pas agi parce que son coup m'a mis K.O. ou fait
     * tressaillir : il a forcément agi avant moi. Le cas typique du Mouchoir
     * (revenge kill), ignoré jusqu'ici faute de coup de ma part.
     *
     * Valable seulement si : c'est le Pokémon qui a commencé le tour qui a été
     * touché ; il n'a pas été empêché d'agir avant (sommeil, paralysie,
     * recharge, confusion) ; aucune de mes capacités n'a une priorité plus
     * basse que la sienne (sinon j'ai pu choisir une capacité lente) ; pas de
     * priorité cachée, Vive-Griffe, Distorsion.
     */
    private static void observerVitesseSansMonAction(Pokemon adversaire) {
        CoupRecu cr = coupRecuTourEcoule;
        if (cr == null || !(cr.ko || cr.tressailli) || cr.joueurBloqueAvant) return;
        if (prioriteObjetCeTour || distorsionTourEcoule) return;
        if (!memeEspece(cr.especeAttaquant, adversaire.getEspece())
                || !adversaire.getEspece().equals(espaceAdversaireDuTour)) return;
        if (especeJoueurTourEcoule == null || !memeEspece(cr.especeDefenseur, especeJoueurTourEcoule)) return;
        if (cr.prioriteMinJoueur == Integer.MIN_VALUE) return;
        try {
            MoveTemplate t = Moves.INSTANCE.getByName(cr.idCoup);
            if (t == null || cr.prioriteMinJoueur < t.getPriority()) return;
        } catch (Exception e) {
            return;
        }
        if (prioriteModifiee(cr.idCoup, talentsBrutsPossibles(adversaire))) return;
        Set<String> mesTalents = talentBrutJoueur(cr.defenseur);
        if (mesTalents.contains("stall") || mesTalents.contains("myceliummight")) return;

        double vJoueur = vitesseJoueurTourEcoule(cr.defenseur);
        if (vJoueur > 0) {
            MessageDebugLogger.analyse("VITESSE " + adversaire.getEspece() + " " + cr.idCoup + " m'a "
                + (cr.ko ? "mis K.O." : "fait tressaillir") + " avant que " + cr.especeDefenseur + " agisse");
            conclureAdversairePlusRapide(adversaire, vJoueur);
        }
    }

    /** Mon Pokémon actif n'est plus celui du début du tour (même source de lecture). */
    private static boolean joueurAChangeCeTour() {
        if (especeJoueurTourEcoule == null) return false;
        Pokemon actif = BattleStateTracker.getJoueurActif();
        return actif == null || !especeJoueurTourEcoule.equals(actif.getEspece());
    }

    /** Ma vitesse réelle au début du tour écoulé (stage, statut, météo, champ, Vent Arrière). */
    private static double vitesseJoueurTourEcoule(Pokemon joueur) {
        appliquerObjetReelJoueur(joueur);
        int stageAvant = joueur.getStage(Stat.VITESSE);
        Pokemon.Statut statutAvant = joueur.getStatut();
        try {
            joueur.setStage(Stat.VITESSE, stageVitJoueurTourEcoule);
            if (statutJoueurTourEcoule != null) joueur.setStatut(statutJoueurTourEcoule);
            Field etat = FieldTracker.construireField();
            Field.Meteo meteo = meteoTourEcoule != null ? meteoTourEcoule : etat.getMeteo();
            Field.TypeTerrain terrain = terrainTourEcoule != null ? terrainTourEcoule : etat.getTerrain();
            return DamageCalculator.vitesseEnCombat(joueur, meteo, terrain, tailwindJoueurTourEcoule);
        } catch (Exception e) {
            return -1;
        } finally {
            joueur.setStage(Stat.VITESSE, stageAvant);
            joueur.setStatut(statutAvant);
        }
    }

    /**
     * Part des réglages Smogon (nature + EV Vitesse) qui, SANS objet et dans
     * l'état du début du tour écoulé, permettent d'aller au moins aussi vite
     * que moi. 1.0 (prudence) si l'espèce est inconnue de Smogon.
     */
    private static double proportionReglagesAssezRapides(Pokemon adversaire, double vJoueur) {
        Map<String, Double> reglages = SmogonDataLoader.reglagesVitesse(adversaire.getEspece());
        if (reglages == null || reglages.isEmpty()) return 1.0;
        double part = 0;
        // Plusieurs réglages donnent la même vitesse : un seul calcul par réglage.
        for (Map.Entry<String, Double> r : reglages.entrySet()) {
            String[] k = r.getKey().split("/");
            if (k.length != 2) continue;
            int ev;
            try {
                ev = Integer.parseInt(k[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            double v = plafondVitesseSansObjet(adversaire, ev, ShowdownIdMapper.nature(k[0]));
            if (v < 0) return 1.0;
            // Même marge que pour le maximum absolu.
            if (v * 1.02 >= vJoueur) part += r.getValue();
        }
        return part;
    }

    /** Vitesse maximale de l'adversaire SANS objet, dans l'état du début du tour écoulé. */
    private static double plafondVitesseSansObjet(Pokemon adversaire, int evVitesse, Nature nature) {
        Field etat = FieldTracker.construireField();
        return plafondVitesseSansObjet(adversaire, evVitesse, nature, stageVitAdvTourEcoule,
            statutAdvTourEcoule != null ? statutAdvTourEcoule : adversaire.getStatut(),
            meteoTourEcoule != null ? meteoTourEcoule : etat.getMeteo(),
            terrainTourEcoule != null ? terrainTourEcoule : etat.getTerrain(),
            tailwindAdvTourEcoule);
    }

    /**
     * Vitesse maximale de l'adversaire actif SANS objet, dans l'état actuel
     * (stage, statut, météo, champ, Vent Arrière) : 252 EV, nature +Vitesse,
     * talent le plus rapide possible. Affichée dans le HUD à côté de
     * l'estimation : s'il te dépasse alors que tu es au-dessus, c'est un
     * Mouchoir (hors priorité). -1 si incalculable.
     */
    public static int vitesseMaxSansObjetActuelle(Pokemon adversaireBase) {
        if (adversaireBase == null) return -1;
        Field etat = FieldTracker.construireField();
        return (int) plafondVitesseSansObjet(adversaireBase, 252, Nature.TIMIDE,
            BoostTracker.getStageAdversaire(Stat.VITESSE), adversaireBase.getStatut(),
            etat.getMeteo(), etat.getTerrain(), FieldTracker.isTailwindAdversaire());
    }

    private static double plafondVitesseSansObjet(Pokemon adversaire, int evVitesse, Nature nature,
                                                  int stage, Pokemon.Statut statut, Field.Meteo meteo,
                                                  Field.TypeTerrain terrain, boolean ventArriere) {
        try {
            Pokemon base = construireAdversaireEstime(adversaire);

            Set<String> talents = new HashSet<>();
            String confirme = TALENTS_CONFIRMES.get(adversaire.getEspece());
            if (confirme != null) {
                talents.add(confirme);
            } else {
                Set<String> reels = getTalentsReelsEspece(adversaire);
                if (reels != null) talents.addAll(reels);
                talents.add("");   // aucun talent de vitesse
            }
            double meilleur = 0;
            for (String talent : talents) {
                Pokemon.Builder b = Pokemon.builder(adversaire.getEspece(), base.getNiveau(),
                        base.getType1(), base.getType2())
                    .statBase(Stat.VITESSE, base.getStatBase(Stat.VITESSE))
                    .iv(Stat.VITESSE, 31)
                    .ev(Stat.VITESSE, evVitesse)
                    .nature(nature);
                if (!talent.isEmpty()) b.talent(talent);
                Pokemon p = b.build();
                p.setStage(Stat.VITESSE, stage);
                if (statut != null) p.setStatut(statut);
                meilleur = Math.max(meilleur,
                    DamageCalculator.vitesseEnCombat(p, meteo, terrain, ventArriere));
            }
            // Effets de vitesse que le calcul ne modélise pas : on élargit.
            Set<String> bruts = talentsBrutsPossibles(adversaire);
            // Délestage : x2 seulement s'il a perdu son objet depuis son entrée
            if (bruts.contains("unburden") && delestageActifAdversaire(adversaire.getEspece())) meilleur *= 2.0;
            if (bruts.contains("protosynthesis") || bruts.contains("quarkdrive")) meilleur *= 1.5; // Énergie Booster
            if (bruts.contains("surgesurfer") && terrain == Field.TypeTerrain.ELECTRIQUE) meilleur *= 2.0;
            return meilleur;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Priorité donnée par un talent ou un champ, invisible dans les données de la capacité. */
    private static boolean prioriteModifiee(String idCoup, Set<String> talentsBruts) {
        try {
            if ("grassyglide".equals(idCoup) && terrainTourEcoule == Field.TypeTerrain.HERBU) return true;
            MoveTemplate t = Moves.INSTANCE.getByName(idCoup);
            if (t == null) return true;
            boolean statut = "status".equalsIgnoreCase(t.getDamageCategory().getName());
            String type = t.getElementalType().getName();
            if (talentsBruts.contains("prankster") && statut) return true;
            if (talentsBruts.contains("galewings") && "flying".equalsIgnoreCase(type)) return true;
            if (talentsBruts.contains("triage") && COUPS_SOIN_OU_DRAIN.contains(idCoup)) return true;
            if (talentsBruts.contains("stall") || talentsBruts.contains("myceliummight")) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Talents possibles de l'espèce en identifiants Showdown bruts, y compris
     * ceux que ShowdownIdMapper ne traduit pas (Entêtement, Filature...) : les
     * garde-fous en ont besoin justement parce que le calcul les ignore.
     * Réduit au talent confirmé quand il est connu.
     */
    private static Set<String> talentsBrutsPossibles(Pokemon adversaire) {
        Set<String> r = new HashSet<>();
        try {
            List<String> noms = BattleStateTracker.talentsPossibles(adversaire.getEspece());
            if (noms == null) return r;
            String confirme = TALENTS_CONFIRMES.get(adversaire.getEspece());
            for (String nom : noms) {
                String id = nom.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (confirme != null && !confirme.equals(ShowdownIdMapper.talent(id))) continue;
                r.add(id);
            }
        } catch (Exception ignored) {
        }
        return r;
    }

    private static Set<String> talentBrutJoueur(Pokemon joueur) {
        Set<String> r = new HashSet<>();
        String t = joueur.getTalent();
        if (t == null) return r;
        for (String id : new String[] {"prankster", "galewings", "triage", "stall", "myceliummight"}) {
            if (t.equals(ShowdownIdMapper.talent(id))) r.add(id);
        }
        return r;
    }

    // Capacités à puissance conditionnelle que le calcul ne modélise pas :
    // leur écart avec la prévision ne dit rien de l'objet.
    private static final Set<String> PUISSANCE_NON_MODELISEE = Set.of(
        // Doublées selon l'ordre réel du tour, que le calcul ne fait que supposer
        "avalanche", "revenge", "payback", "assurance", "stompingtantrum", "temperflare",
        "lashout", "retaliate", "pursuit", "furycutter", "rollout", "iceball", "echoedvoice",
        "round", "ragefist",
        // Autres puissances non calculées ou trop variables
        "smellingsalts", "wakeupslap", "trumpcard", "punishment", "beatup", "fling",
        "naturalgift", "present", "magnitude", "spitup", "foulplay", "photongeyser",
        "terablast", "shellsidearm", "ragingbull", "futuresight", "doomdesire", "bodypress",
        "electroball", "hiddenpower", "terrainpulse", "tripleaxel", "triplekick", "populationbomb",
        "ficklebeam");

    // ---------------------------------------------------------------------
    // Bandeau Choix / Lunettes Choix : coup reçu trop fort.
    //
    // L'ancienne mesure comparait mes PV au début et à la fin du tour : elle
    // ne valait rien dès que je changeais de Pokémon (le cas le plus fréquent
    // pour encaisser un coup), sur un K.O., ou si un champ s'arrêtait en fin
    // de tour. Désormais, au message de l'attaque adverse, on photographie le
    // Pokémon touché (PV, stages, objet), l'attaquant, la météo, le champ et
    // mes écrans de CE moment-là ; puis on suit les messages jusqu'à la fin du
    // tour : soins connus (Restes, Champ Herbu, brûlure, poison) corrigés,
    // tout autre changement de PV invalide la mesure. Un K.O. donne une borne
    // basse, suffisante si le Pokémon avait plus de PV qu'un coup sans objet
    // ne peut en retirer.
    // ---------------------------------------------------------------------
    private static final class CoupRecu {
        String idCoup;
        String especeAttaquant;
        Pokemon attaquant;          // adversaire tel que vu au moment du coup
        Pokemon attaquantEstime;    // son estimation (objet...) au moment du coup
        double pvAttaquantPct;
        Pokemon.Statut statutAttaquant;
        int stageAtk, stageAtkSpe;
        String especeDefenseur;     // côté combat (pour le suivi des messages)
        Pokemon defenseur;          // stats complètes, objet, stages au moment du coup
        double pvAvant;
        Field etat;
        boolean clone;
        boolean crit, multiCoups, ko, invalide;
        boolean bloque;             // Abri, raté, immunité ou échec : aucun dégât à mesurer
        boolean tressailli;          // cant.flinch sur le Pokémon touché après le coup
        boolean joueurBloqueAvant;   // je n'ai pas pu agir AVANT son coup (sommeil, paralysie, recharge...)
        int prioriteMinJoueur = Integer.MIN_VALUE;  // priorité la plus basse parmi mes capacités
        double correctionPv = 0;    // PV rendus (+) ou perdus (-) après le coup
        long instantMs;
        boolean pvStableAuCoup, pvAvantDouteux;
        boolean entreeRecente;      // mon Pokémon venait d'entrer : sa barre de PV n'est pas encore à jour
        boolean morphing;           // un des deux transformé AU MOMENT du coup
        boolean torche, charge;     // Torche activée / Chargeur en place au moment du coup
    }
    private static CoupRecu coupRecu = null;

    // Même principe pour MES coups : dégâts prévus contre perte réelle de
    // l'adversaire. Uniquement pour le log (vérifier son set défensif estimé).
    private static final class CoupDonne {
        String idCoup, especeAttaquant, especeDefenseur;
        Pokemon attaquant;          // mon Pokémon au moment du coup
        Pokemon defenseurBase;      // adversaire tel que vu au moment du coup
        Pokemon defenseurEstime;    // son estimation (objet, stats) au moment du coup
        boolean bloque;             // Abri, raté, immunité ou échec : aucun dégât à mesurer
        double pvAvant;
        Field etat;
        boolean crit, multiCoups, ko, invalide;
        double correctionPv = 0;
        boolean impactPrescience;   // impact de ma Prescience (fin de tour), pas un coup du tour
        boolean clone;              // son Clone a pris le coup : ses PV n'ont pas bougé
        long instantMs;             // moment du coup (décodage du message)
        boolean pvStableAuCoup;     // sa barre de PV était immobile à ce moment
        long coupPrecedentMs;       // mon coup du même tour avant l'impact de Prescience
        boolean pvAvantDouteux;     // un effet sur ses PV venait d'arriver, pas encore appliqué
        boolean morphing;           // un des deux transformé AU MOMENT du coup
    }
    private static CoupDonne coupDonne = null;
    private static CoupDonne coupPrescience = null;
    private static CoupRecu coupRecuTourEcoule = null;   // conservé pour observerVitesse
    private static boolean dernierCoupEstAdverse = false;
    private static boolean joueurBloqueCeTour = false;   // avant le coup adverse

    // Capacités du joueur qui changent ses propres PV sans message de dégâts.
    private static final Set<String> COUPS_COUT_PV = Set.of(
        "substitute", "bellydrum", "painsplit", "curse", "clangoroussoul",
        "filletaway", "shedtail", "mindblown", "steelbeam", "chloroblast");

    private static boolean memeEspece(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase().replaceAll("[^a-z0-9]", "");
        String y = b.toLowerCase().replaceAll("[^a-z0-9]", "");
        return x.startsWith(y) || y.startsWith(x);
    }

    // --- Objet reçu en cours de séjour (Pickpocket, Tour de Magie...) ---
    // Le verrou Choix repart de zéro : la capacité utilisée AVANT d'avoir
    // l'objet ne compte pas (le Tinkaton qui vole un Bandeau Choix après un
    // Bluff, puis utilise Sabotage, n'avait pas "deux capacités sous Choix").
    private static void objetAdversaireRecu(String espece) {
        if (espece != null && espece.equals(delestageAdversaire)) delestageAdversaire = null;
        OBJETS_RETIRES.remove(espece);
        OBJETS_CHOIX_EXCLUS.remove(espece);
        PREUVE_PAS_CHOIX.remove(espece);
        OBJETS_PROBABLES.remove(espece);
        ORBE_VIE_EXCLUE.remove(espece);
        if (espece != null && espece.equals(especeSejourAdv)) premierCoupSejourAdv = null;
    }

    // --- Pas d'Orbe Vie : il m'a touché sans que le jeu annonce le recul ---
    private static boolean reculOrbeVieAdvCeTour = false;
    // Son coup a raté, échoué, ou a été bloqué (Abri, immunité)
    private static boolean coupAdvRateCeTour = false;
    private static final Set<String> ORBE_VIE_EXCLUE = new HashSet<>();

    private static void tenterExclureOrbeVie(Pokemon adversaire, double perteJoueur) {
        String espece = adversaire.getEspece();
        if (ORBE_VIE_EXCLUE.contains(espece) || reculOrbeVieAdvCeTour || coupAdvRateCeTour) return;
        if ("Orbe Vie".equals(OBJETS_CONFIRMES.get(espece))) return;
        if (coupAdversaireDuTour == null || adversaireNAPasAttaque() || perteJoueur < 0.5) return;
        if (!espece.equals(espaceAdversaireDuTour) || FieldTracker.joueurAUnClone()) return;
        // Le coup doit être celui qui m'a touché (pas un dégât annexe)
        CoupRecu cr = coupRecuTourEcoule;
        if (cr == null || cr.clone || !coupAdversaireDuTour.showdownId().equals(cr.idCoup)) return;
        MoveTemplate t = Moves.INSTANCE.getByName(cr.idCoup);
        if (t == null || "status".equalsIgnoreCase(t.getDamageCategory().getName())) return;
        // Garde Magik : pas de recul ; Sans Limite : pas de recul sur ses coups à effet
        Set<String> bruts = talentsBrutsPossibles(adversaire);
        if (bruts.contains("magicguard") || bruts.contains("sheerforce")) return;
        if (estTransforme(true, espece)) return;
        ORBE_VIE_EXCLUE.add(espece);
        if ("Orbe Vie".equals(OBJETS_PROBABLES.get(espece))) OBJETS_PROBABLES.remove(espece);
        ProfilAdversaire profil = PROFILS.get(espece);
        if (profil != null) {
            for (StatHypothesis h : new StatHypothesis[]{profil.attaque, profil.attaqueSpe, profil.defense, profil.defenseSpe}) {
                h.objetsPossibles.remove("Orbe Vie");
            }
        }
        MessageDebugLogger.analyse("OBJET " + espece + " : pas d'Orbe Vie (" + cr.idCoup
            + " m'a touché sans recul annoncé)");
    }

    // --- Morphing (Métamorph, Imposteur) : "moi:espèce" / "adv:espèce" ---
    private static final Set<String> TRANSFORMES = new HashSet<>();

    /** Mon Pokémon actif est transformé (Morphing vu, ou espèce de combat différente de sa fiche). */
    private static boolean joueurTransforme() {
        if (TRANSFORMES.stream().anyMatch(k -> k.startsWith("moi:"))) return true;
        try {
            return BattleStateTracker.joueurEstTransforme();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean estTransforme(boolean campAdverse, String espece) {
        if (campAdverse && cibleMorphingAdversaire != null) return true;
        return espece != null && TRANSFORMES.contains((campAdverse ? "adv:" : "moi:") + espece);
    }

    // Mon Métamorph : la cible copiée (instantané au moment du Morphing,
    // valable même si l'adversaire change ensuite de Pokémon) et ses
    // capacités copiées, lues dans le menu d'attaque du jeu.
    private static Pokemon cibleMorphingJoueur = null;
    private static List<String> capacitesMorphing = new ArrayList<>();

    // Métamorph ADVERSE : mon Pokémon qu'il a copié (stats exactes) et mes
    // capacités qu'il a donc aussi.
    private static Pokemon cibleMorphingAdversaire = null;
    private static String especeMorphingAdversaire = null;   // son espèce d'origine (Métamorph...)
    private static List<String> capacitesMorphingAdversaire = new ArrayList<>();

    /** Mes capacités copiées par le Métamorph adverse (id Showdown), vide sinon. */
    public static List<String> getCapacitesMorphingAdversaire() {
        return cibleMorphingAdversaire == null ? List.of() : capacitesMorphingAdversaire;
    }

    /** Espèce de mon Pokémon copié par le Métamorph adverse, ou null. */
    public static String getCibleMorphingAdversaire() {
        return cibleMorphingAdversaire == null ? null : cibleMorphingAdversaire.getEspece();
    }

    /**
     * L'adversaire tel que le HUD doit le calculer : son estimation, ou, s'il
     * s'est transformé en mon Pokémon, une copie exacte de mon Pokémon (stats,
     * types, talent) avec ses PV, son statut et son objet à lui.
     */
    public static Pokemon adversaireAffiche(Pokemon adversaireBase) {
        Pokemon estime = construireAdversaireEstime(adversaireBase);
        Pokemon cible = cibleMorphingAdversaire;
        if (cible == null) return estime;
        // Son espèce d'origine : celle vue au Morphing, sauf si le jeu affichait
        // déjà l'espèce copiée (alors Métamorph, le seul cas courant).
        String origine = especeMorphingAdversaire == null || especeMorphingAdversaire.equals(cible.getEspece())
            ? "ditto" : especeMorphingAdversaire;
        int pvMax;
        String objet;
        if (origine.equals(adversaireBase.getEspece())) {
            pvMax = estime.getPvMax();
            objet = estime.getObjet();
        } else {
            pvMax = pvMaxEstime(origine, adversaireBase.getNiveau());
            objet = OBJETS_RETIRES.contains(origine) ? null
                : OBJETS_CONFIRMES.containsKey(origine) ? OBJETS_CONFIRMES.get(origine)
                : objetSmogonProbable(origine, SmogonDataLoader.getDonnees(origine));
        }
        Pokemon.Builder b = Pokemon.builder(cible.getEspece(), cible.getNiveau(), cible.getType1(), cible.getType2())
            .nature(cible.getNature())
            .poids(cible.getPoidsHg());
        if (cible.getTalent() != null) b.talent(cible.getTalent());
        for (Stat st : Stat.values()) {
            b.statBase(st, cible.getStatBase(st)).ev(st, cible.getEv(st)).iv(st, cible.getIv(st));
        }
        Pokemon copie = b.build();
        copie.setCampAdverse(true);
        if (pvMax > 0) copie.setPvMaxOverride(pvMax);
        copie.setPvActuels((int) Math.round(adversaireBase.getPourcentagePv() / 100.0 * copie.getPvMax()));
        copie.setStatut(adversaireBase.getStatut());
        copie.setObjet(objet);
        copie.setCoupsRageFistSubis(getCoupsRageFistAdversaire(adversaireBase.getEspece()));
        return copie;
    }

    /** PV max d'une espèce à ce niveau avec 252 EV et 31 IV (0 si inconnue). */
    private static int pvMaxEstime(String espece, int niveau) {
        try {
            Species sp = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(espece);
            if (sp == null) return 0;
            Integer base = sp.getBaseStats().get(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP);
            if (base == null) return 0;
            return ((2 * base + 31 + 63) * niveau) / 100 + niveau + 10;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** La cible copiée par mon Métamorph, ou null (pas de Morphing vu). */
    public static Pokemon getCibleMorphingJoueur() {
        return cibleMorphingJoueur;
    }

    /**
     * Mon Pokémon actif est transformé. Le message "transform" fait foi
     * jusqu'à ce qu'il quitte le terrain : quand la cible copiée tombe K.O.,
     * le jeu peut de nouveau présenter mon Métamorph comme un Métamorph
     * (log du 11/10 : Braségali copié puis K.O., plus aucune capacité
     * affichée face au Léviator alors que j'étais toujours transformé).
     */
    public static boolean isJoueurTransforme() {
        if (cibleMorphingJoueur != null) return true;
        try {
            return BattleStateTracker.joueurEstTransforme();
        } catch (Exception e) {
            return false;
        }
    }

    /** Capacités copiées par mon Métamorph (id Showdown), vide si pas encore lues. */
    public static List<String> getCapacitesMorphing() {
        return capacitesMorphing;
    }

    /**
     * Métamorph transformé : stats (EV et nature compris), types et talent de
     * la cible telle qu'estimée (31 IV, set Smogon ou mieux si observé), mais
     * PV, objet et statut de Métamorph - c'est ce que copie Morphing.
     */
    public static Pokemon construireMorphing(Pokemon metamorph, Pokemon cibleBase) {
        Pokemon copie = construireAdversaireEstime(cibleBase);
        copie.setCampAdverse(false);
        copie.setPvMaxOverride(metamorph.getPvMax());
        copie.setPvActuels(metamorph.getPvActuels());
        copie.setStatut(metamorph.getStatut());
        copie.setObjet(metamorph.getObjet());
        // Poing de Colère : Morphing ne copie pas le compteur de coups subis,
        // c'est celui de MON Métamorph qui compte (log du 19/10 : Métamorph
        // en Colossinge, prévu 62-73 % avec le compteur du Colossinge adverse,
        // réel 32 %)
        copie.setCoupsRageFistSubis(getCoupsRageFistJoueur(metamorph.getEspece()));
        return copie;
    }

    private static void suivreMorphingEtRates(String cle, Object[] args) {
        if (cle.equals("cobblemon.battle.transform") && args.length > 0) {
            Boolean adverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            if (adverse == null) return;
            Pokemon p = adverse ? BattleStateTracker.getAdversaireActif() : BattleStateTracker.getJoueurActif();
            if (adverse) {
                // Métamorph adverse transformé en MON Pokémon : copie exacte de
                // ses stats (connues), de ses capacités et de ses boosts.
                especeMorphingAdversaire = p != null ? p.getEspece() : null;
                cibleMorphingAdversaire = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (cibleMorphingAdversaire == null) cibleMorphingAdversaire = BattleStateTracker.getJoueurActif();
                if (cibleMorphingAdversaire != null) appliquerObjetReelJoueur(cibleMorphingAdversaire);
                capacitesMorphingAdversaire = new ArrayList<>();
                try {
                    com.cobblemon.mod.common.pokemon.Pokemon complet = BattleStateTracker.getPokemonCompletJoueur();
                    if (complet != null) {
                        for (com.cobblemon.mod.common.api.moves.Move m : complet.getMoveSet()) {
                            if (m != null) capacitesMorphingAdversaire.add(m.getName());
                        }
                    }
                } catch (Exception ignored) {
                }
                for (Stat st : Stat.values()) {
                    if (st != Stat.PV) BoostTracker.forcerStageAdversaire(st, BoostTracker.getStageJoueur(st));
                }
            }
            if (!adverse) {
                // Morphing copie aussi les boosts de la cible
                cibleMorphingJoueur = BattleStateTracker.getAdversaireActif();
                for (Stat st : Stat.values()) {
                    if (st != Stat.PV) BoostTracker.forcerStageJoueur(st, BoostTracker.getStageAdversaire(st));
                }
                capacitesMorphing = new ArrayList<>();
            }
            if (p != null && TRANSFORMES.add((adverse ? "adv:" : "moi:") + p.getEspece())) {
                MessageDebugLogger.analyse("MORPHING " + p.getEspece() + (adverse ? " (adverse)" : " (moi)")
                    + (!adverse && cibleMorphingJoueur != null ? " en " + cibleMorphingJoueur.getEspece() : "")
                    + " : mesures ignorées tant qu'il reste sur le terrain");
            }
        } else if (cle.startsWith("cobblemon.battle.switch.other") || cle.startsWith("cobblemon.battle.withdraw.other")) {
            TRANSFORMES.removeIf(k -> k.startsWith("adv:"));
            cibleMorphingAdversaire = null;
            especeMorphingAdversaire = null;
            capacitesMorphingAdversaire = new ArrayList<>();
        } else if (cle.startsWith("cobblemon.battle.switch.self") || cle.startsWith("cobblemon.battle.withdraw.self")) {
            if (cle.startsWith("cobblemon.battle.switch.self")) entreeJoueurMs = System.currentTimeMillis();
            TRANSFORMES.removeIf(k -> k.startsWith("moi:"));
            cibleMorphingJoueur = null;
            capacitesMorphing = new ArrayList<>();
        } else if (dernierCoupEstAdverse && (cle.equals("cobblemon.battle.missed") || cle.equals("cobblemon.battle.fail")
                || cle.equals("cobblemon.battle.immune") || cle.startsWith("cobblemon.battle.activate.protect"))) {
            coupAdvRateCeTour = true;
        }
    }

    // --- Torche activée : x1,5 sur l'Attaque des capacités Feu ---
    // Activée en absorbant une capacité Feu, jusqu'à la sortie du terrain.
    // "adv:espèce" ou "moi:espèce".
    private static final Set<String> TORCHE_ACTIVE = new HashSet<>();

    /** Vrai si le jeu a annoncé l'activation de Torche de ce Pokémon (encore sur le terrain). */
    public static boolean isTorcheActive(boolean campAdverse, String espece) {
        return espece != null && TORCHE_ACTIVE.contains((campAdverse ? "adv:" : "moi:") + espece);
    }

    private static void activerTorche(Boolean estAdversaire) {
        if (estAdversaire == null) return;
        Pokemon p = estAdversaire ? BattleStateTracker.getAdversaireActif() : BattleStateTracker.getJoueurActif();
        if (p == null) return;
        if (TORCHE_ACTIVE.add((estAdversaire ? "adv:" : "moi:") + p.getEspece())) {
            MessageDebugLogger.analyse("TALENT " + p.getEspece() + (estAdversaire ? " (adverse)" : " (moi)")
                + " : Torche activée, capacités Feu x1,5");
        }
    }

    // --- Durée de la météo : 8 tours = roche du lanceur ---
    // Une météo dure 5 tours (4 messages "upkeep" puis "end"), 8 avec la
    // roche adaptée. Un 5e upkeep sans relance prouve la roche du lanceur.
    // Vérifié sur le log du 11/10 : Sable Volant de Tyranocif = 7 upkeep,
    // celui de mon Métamorph (Mouchoir Choix) = 4, Neige de Roigada = 4.
    private static final Map<String, String> ROCHES_METEO = Map.of(
        "sandstorm", "Roche Lisse", "raindance", "Roche Humide", "sunnyday", "Roche Chaude",
        "snow", "Roche Glacée", "snowscape", "Roche Glacée", "hail", "Roche Glacée");
    private static Boolean dernierActeurAdverse = null;
    private static String meteoType = null;
    private static String meteoLanceurAdv = null;   // espèce adverse qui l'a lancée, sinon null
    private static int meteoUpkeeps = 0;

    private static void suivreDureeMeteo(String cle, Object[] args) {
        if ((cle.equals("cobblemon.battle.ability.generic") || cle.equals("cobblemon.battle.used_move")
                || cle.equals("cobblemon.battle.used_move_on")) && args.length > 0) {
            dernierActeurAdverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            return;
        }
        if (!cle.startsWith("cobblemon.battle.weather.")) return;
        String[] p = cle.substring("cobblemon.battle.weather.".length()).split("\\.");
        if (p.length != 2) return;
        if (p[1].equals("start")) {
            meteoType = p[0];
            meteoUpkeeps = 0;
            Pokemon adv = Boolean.TRUE.equals(dernierActeurAdverse) ? BattleStateTracker.getAdversaireActif() : null;
            meteoLanceurAdv = adv != null ? adv.getEspece() : null;
        } else if (p[1].equals("upkeep") && p[0].equals(meteoType)) {
            meteoUpkeeps++;
            String roche = ROCHES_METEO.get(meteoType);
            if (meteoUpkeeps == 5 && meteoLanceurAdv != null && roche != null) {
                String espece = meteoLanceurAdv;
                String raison = "météo de 8 tours au lieu de 5";
                if (OBJETS_RETIRES.contains(espece)) {
                    OBJETS_PERDUS_CONNUS.put(espece, roche);
                    MessageDebugLogger.analyse("OBJET " + espece + " : tenait une " + roche + " (" + raison + "), perdue depuis");
                } else {
                    confirmerObjet(espece, roche, raison);
                    MessageDebugLogger.analyse("OBJET " + espece + " : " + roche + " confirmée (" + raison + ")");
                }
            }
        } else if (p[1].equals("end")) {
            meteoType = null;
            meteoLanceurAdv = null;
            meteoUpkeeps = 0;
        }
    }

    private static void suivreTorche(String cle, Object[] args) {
        if (cle.startsWith("cobblemon.battle.switch.other") || cle.startsWith("cobblemon.battle.withdraw.other")) {
            TORCHE_ACTIVE.removeIf(k -> k.startsWith("adv:"));
        } else if (cle.startsWith("cobblemon.battle.switch.self") || cle.startsWith("cobblemon.battle.withdraw.self")) {
            TORCHE_ACTIVE.removeIf(k -> k.startsWith("moi:"));
        } else if (cle.startsWith("cobblemon.battle.dragged_out")) {
            // Camp non lu ici : prudence, on oublie les deux
            TORCHE_ACTIVE.clear();
        } else if (cle.startsWith("cobblemon.battle.start.") && cle.contains("flashfire") && args.length > 0) {
            activerTorche(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])));
        }
    }

    // --- Verrou Choix : deux capacités différentes sur le même séjour ---
    // Un objet Choix bloque son porteur sur la première capacité utilisée
    // depuis son entrée. Deux capacités différentes sans sortie entre les
    // deux prouvent qu'il n'en a pas. Suivi par les messages du jeu (pas par
    // tour : un Pokémon entré en cours de tour puis reparti avec Demi-Tour
    // échappait à l'ancienne comparaison tour par tour).
    private static String especeSejourAdv = null;
    private static String premierCoupSejourAdv = null;
    // Preuve affichée dans le HUD : espèce -> "Pyroball puis Demi-Tour"
    private static final Map<String, String> PREUVE_PAS_CHOIX = new HashMap<>();
    // Capacités qui en lancent une autre (la capacité lancée apparaît aussi)
    private static final Set<String> COUPS_APPELANTS = Set.of(
        "sleeptalk", "metronome", "copycat", "mefirst", "assist", "mirrormove", "naturepower");

    private static void suivreVerrouChoix(String cle, Object[] args) {
        if (cle.startsWith("cobblemon.battle.switch.other") || cle.startsWith("cobblemon.battle.withdraw.other")
                || cle.startsWith("cobblemon.battle.dragged_out")) {
            // Sortie ou entrée : nouveau séjour (prudent : n'importe quel
            // changement côté adverse coupe la comparaison)
            especeSejourAdv = null;
            premierCoupSejourAdv = null;
            return;
        }
        boolean coup = cle.equals("cobblemon.battle.used_move_on") || cle.equals("cobblemon.battle.used_move");
        if (!coup || args.length < 2) return;
        if (!Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) return;
        String espece = especeDepuisArgument(args[0]);
        if (espece == null) {
            // Pokémon surnommé : le message donne le surnom, pas l'espèce
            Pokemon actif = BattleStateTracker.getAdversaireActif();
            if (actif != null) espece = actif.getEspece();
        }
        String idCoup = idCapaciteDepuisArgument(args[1]);
        if (espece == null || idCoup == null || "struggle".equals(idCoup)) return;

        if (!espece.equals(especeSejourAdv)) {
            especeSejourAdv = espece;
            premierCoupSejourAdv = idCoup;
            return;
        }
        if (premierCoupSejourAdv == null) {
            premierCoupSejourAdv = idCoup;
            return;
        }
        if (premierCoupSejourAdv.equals(idCoup)) return;
        // Capacité lancée par une autre (Blabla Dodo...) : pas une preuve
        if (COUPS_APPELANTS.contains(premierCoupSejourAdv) || COUPS_APPELANTS.contains(idCoup)) return;
        // Miroir Magik renvoie une capacité de statut au nom du porteur
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        if (adv != null && espece.equals(adv.getEspece())
                && talentsBrutsPossibles(adv).contains("magicbounce")) {
            MoveTemplate t = Moves.INSTANCE.getByName(idCoup);
            if (t == null || "status".equalsIgnoreCase(t.getDamageCategory().getName())) return;
        }
        exclureChoix(espece, premierCoupSejourAdv, idCoup);
    }

    private static void exclureChoix(String espece, String coup1, String coup2) {
        if (PREUVE_PAS_CHOIX.containsKey(espece)) return;
        String preuve = nomCapacite(coup1) + " puis " + nomCapacite(coup2);
        PREUVE_PAS_CHOIX.put(espece, preuve);
        OBJETS_CHOIX_EXCLUS.add(espece);
        String probable = OBJETS_PROBABLES.get(espece);
        if (probable != null && probable.endsWith(" Choix")) OBJETS_PROBABLES.remove(espece);
        String objet = OBJETS_CONFIRMES.get(espece);
        if ("Mouchoir Choix".equals(objet) || "Bandeau Choix".equals(objet) || "Lunettes Choix".equals(objet)) {
            OBJETS_CONFIRMES.remove(espece);
            OBJETS_CONFIRMES_PROACTIVEMENT.remove(espece);
            RAISONS_OBJET.remove(espece);
        }
        MessageDebugLogger.analyse("OBJET " + espece + " : pas d'objet Choix (" + coup1 + " puis " + coup2
            + " sans sortir)" + (objet != null && objet.contains("Choix") ? ", " + objet + " annulé" : ""));
    }

    private static String nomCapacite(String id) {
        try {
            MoveTemplate t = Moves.INSTANCE.getByName(id);
            if (t != null) return t.getDisplayName().getString();
        } catch (Exception ignored) {
        }
        return id;
    }

    // Objet déduit par élimination sur les dégâts (pas une preuve) : espèce -> objet
    private static final Map<String, String> OBJETS_PROBABLES = new HashMap<>();
    private static final Map<String, String> RAISONS_PROBABLES = new HashMap<>();

    /** Pourquoi cet objet est supposé (affiché dans le HUD), ou null. */
    public static String getRaisonObjetProbable(String espece) {
        return getObjetProbable(espece) == null ? null : RAISONS_PROBABLES.get(espece);
    }

    /** Objet que les dégâts désignent, sans être prouvé (null si aucun ou si confirmé/retiré). */
    public static String getObjetProbable(String espece) {
        if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)) return null;
        return OBJETS_PROBABLES.get(espece);
    }

    /** "Pyroball puis Demi-Tour" si l'adversaire a prouvé ne pas avoir d'objet Choix, sinon null. */
    public static String getPreuvePasChoix(String espece) {
        return PREUVE_PAS_CHOIX.get(espece);
    }

    /**
     * Clé de traduction d'un argument de message ("cobblemon.move.earthquake",
     * "item.cobblemon.choice_band"), qu'il arrive comme texte traduisible ou
     * comme chaîne.
     */
    static String cleDepuisArgument(Object arg) {
        if (arg == null) return null;
        if (arg instanceof Text t && t.getContent() instanceof TranslatableTextContent c) return c.getKey();
        String brut = String.valueOf(arg);
        int k = brut.indexOf("key='");
        if (k >= 0) {
            int fin = brut.indexOf('\'', k + 5);
            if (fin > k) return brut.substring(k + 5, fin);
        }
        return brut;
    }

    /**
     * Id Showdown de la capacité ("earthquake") depuis l'argument du message.
     *
     * L'argument est un texte traduisible (clé "cobblemon.move.earthquake"),
     * pas une chaîne : couper son affichage au dernier point donnait
     * "earthquakeargs" (la fin de "', args=[]}"), une capacité inconnue. Toute
     * l'analyse des coups reçus (détection Bandeau/Lunettes par les dégâts)
     * concluait donc "capacité inconnue".
     */
    static String idCapaciteDepuisArgument(Object arg) {
        String cle = cleDepuisArgument(arg);
        if (cle == null) return null;
        int i = cle.lastIndexOf('.');
        String id = (i >= 0 ? cle.substring(i + 1) : cle).toLowerCase().replaceAll("[^a-z0-9]", "");
        return id.isEmpty() ? null : id;
    }

    private static void suivreCoupRecu(String cle, Object[] args) {
        boolean coup = cle.equals("cobblemon.battle.used_move_on") || cle.equals("cobblemon.battle.used_move");
        if (coup) {
            Boolean adverse = args.length > 0
                ? determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])) : null;
            dernierCoupEstAdverse = Boolean.TRUE.equals(adverse);
            String idCoup = args.length > 1 ? idCapaciteDepuisArgument(args[1]) : null;
            if (Boolean.FALSE.equals(adverse) && coupRecu != null && idCoup != null
                    && COUPS_COUT_PV.contains(idCoup)) {
                coupRecu.invalide = true;
            }
            if (Boolean.FALSE.equals(adverse) && cle.equals("cobblemon.battle.used_move_on") && args.length > 2
                    && Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[2])))) {
                photographierCoupDonne(idCoup, especeOuActif(args[2]));
            }
            if (dernierCoupEstAdverse && cle.equals("cobblemon.battle.used_move_on") && args.length > 2
                    && Boolean.FALSE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[2])))) {
                photographierCoupRecu(idCoup, especeOuActif(args[0]), especeOuActif(args[2]));
            }
            return;
        }
        // Avant le coup adverse : mon Pokémon a-t-il été empêché d'agir à son
        // tour (il était alors PLUS rapide) ? Endormi, gelé, paralysé, recharge,
        // dégâts de confusion... Dans ce cas, ne pas avoir agi ne prouve rien.
        if (coupRecu == null && args.length > 0
                && Boolean.FALSE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))
                && ((cle.startsWith("cobblemon.status.") && cle.endsWith(".is"))
                    || cle.startsWith("cobblemon.battle.cant.") || cle.equals("cobblemon.battle.recharge")
                    || cle.startsWith("cobblemon.battle.damage.") || cle.contains("confusion"))) {
            joueurBloqueCeTour = true;
        }
        suivreCoupDonne(cle, args);
        CoupRecu cr = coupRecu;
        if (cr == null) return;
        Boolean surMoi = args.length > 0
            ? Boolean.FALSE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]))) : false;
        boolean surDefenseur = surMoi && memeEspece(especeOuActif(args[0]), cr.especeDefenseur);
        // Son coup bloqué par mon Abri, raté, sans effet ou échoué : rien à mesurer
        // (log du 11/10 : Cascade sur l'Abri de Braségali lu comme « perte 0 »)
        if (dernierCoupEstAdverse && (cle.equals("cobblemon.battle.missed") || cle.equals("cobblemon.battle.fail")
                || ((cle.startsWith("cobblemon.battle.activate.protect") || cle.equals("cobblemon.battle.immune"))
                    && surDefenseur))) {
            cr.bloque = true;
        }

        if (cle.equals("cobblemon.battle.crit")) {
            if (dernierCoupEstAdverse) cr.crit = true;
        } else if (cle.equals("cobblemon.battle.hit_count")) {
            if (dernierCoupEstAdverse) cr.multiCoups = true;
        } else if (cle.equals("cobblemon.battle.fainted")) {
            if (surDefenseur) cr.ko = true;
        } else if (cle.equals("cobblemon.battle.cant.flinch")) {
            if (surDefenseur) cr.tressailli = true;
        } else if (cle.equals("cobblemon.battle.heal.wish")) {
            // « heal.wish | moi:Mamanbo » soigne le Pokémon EN PLACE (Roigada
            // entré entre-temps) : le message nomme le lanceur du Vœu
            if (surMoi) cr.invalide = true;
        } else if (cle.equals("cobblemon.battle.heal.drain")) {
            // « heal.drain | X » nomme le Pokémon VIDÉ : si c'est moi, c'est
            // l'attaquant qui se soigne (Lame Ointe sur Trioxhydre, mesure jetée
            // à tort le 13/10) ; si c'est lui, c'est moi qui me soigne.
            if (args.length > 0 && Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) {
                cr.invalide = true;
            }
        } else if (cle.startsWith("cobblemon.battle.heal.")) {
            if (surDefenseur) {
                String type = cle.substring("cobblemon.battle.heal.".length());
                if (type.equals("leftovers") || type.equals("grassyterrain")) cr.correctionPv += 6.25;
                else cr.invalide = true;
            }
        } else if (cle.startsWith("cobblemon.status.") && cle.endsWith(".hurt")) {
            if (surDefenseur) {
                if (cle.contains("burn")) cr.correctionPv -= 6.25;
                else if (cle.contains("poison") && cr.defenseur.getStatut() == Pokemon.Statut.POISON) cr.correctionPv -= 12.5;
                else cr.invalide = true;
            }
        } else if (cle.startsWith("cobblemon.battle.damage.") || cle.startsWith("cobblemon.battle.end.futuresight")
                || cle.startsWith("cobblemon.battle.end.doomdesire")) {
            if (surDefenseur) cr.invalide = true;
        } else if (cle.equals("cobblemon.battle.switch.self") || cle.equals("cobblemon.battle.withdraw.self")) {
            // Le Pokémon touché quitte le terrain : on ne pourra pas relire ses PV.
            if (!cr.ko) cr.invalide = true;
        }
    }

    private static void photographierCoupDonne(String idCoup, String especeDef) {
        coupDonne = null;
        if (idCoup == null) return;
        try {
            Pokemon def = BattleStateTracker.getAdversaireActif();
            Pokemon att = BattleStateTracker.getJoueurActifDepuisEquipe();
            if (att == null) att = BattleStateTracker.getJoueurActif();
            if (def == null || att == null) return;
            if (especeDef != null && !memeEspece(especeDef, def.getEspece())) return;
            appliquerObjetReelJoueur(att);
            // Mon Métamorph transformé frappe avec la copie, pas ses propres stats
            if (joueurTransforme() && cibleMorphingJoueur != null) att = construireMorphing(att, cibleMorphingJoueur);
            for (Stat st : Stat.values()) {
                if (st != Stat.PV) att.setStage(st, BoostTracker.getStageJoueur(st));
            }
            try {
                TypeTracker.appliquer(att, def);
            } catch (Exception ignored) {
            }
            CoupDonne cd = new CoupDonne();
            cd.idCoup = idCoup;
            cd.morphing = joueurTransforme() || estTransforme(true, def.getEspece());
            cd.especeAttaquant = att.getEspece();
            cd.especeDefenseur = def.getEspece();
            cd.attaquant = att;
            cd.defenseurBase = def;
            // Estimation AU MOMENT du coup : Sabotage fait tomber l'objet juste
            // après, mais son bonus x1,5 dépendait de l'objet encore tenu.
            cd.defenseurEstime = construireAdversaireEstime(def);
            // Ses boosts du moment (Plénitude, Gonflette...) : sans eux, un
            // Pokémon boosté en Déf. paraissait « plus résistant que prévu »
            // (log du 12/10 : Braségali après Gonflette, Primarina après Plénitude)
            for (Stat st : Stat.values()) {
                if (st != Stat.PV) cd.defenseurEstime.setStage(st, BoostTracker.getStageAdversaire(st));
            }
            cd.pvAvant = def.getPourcentagePv();
            cd.etat = FieldTracker.construireField();
            cd.clone = FieldTracker.adversaireAUnClone();
            noterInstantDuCoup(cd);
            coupDonne = cd;
        } catch (Exception ignored) {
        }
    }

    private static void suivreCoupDonne(String cle, Object[] args) {
        noterEffetPv(cle, args);
        try {
            suivreRestes(cle, args);
        } catch (Exception ignored) {
        }
        suivreUnCoupDonne(coupDonne, cle, args);
        suivreUnCoupDonne(coupPrescience, cle, args);
    }

    /**
     * Effet sur ses PV après mon coup : la mesure par fin de tour devient
     * fausse. Elle reste possible par l'historique des PV stables (voir
     * mesureParPvStables), qui lit ses PV juste après mon coup.
     */
    private static void figerOuInvalider(CoupDonne cd) {
        cd.invalide = true;
    }

    // --- PV adverses stables : historique de la barre de PV ---
    // Les messages arrivent 1 à 2 s AVANT que le jeu applique les PV, et la
    // barre s'anime ensuite. Relire ses PV à l'arrivée d'un message donnait
    // donc une valeur d'avant le coup ou à mi-animation (logs du 15/10 :
    // Meloetta lue à 51,5 % au lieu de 45,8 %, Corboss lu avant le recul de
    // son Orbe Vie). On garde chaque valeur restée immobile 300 ms : la
    // première qui change après mon coup est sa vie juste après mon coup,
    // avant les Restes, poisons et autres effets de fin de tour.
    private static final class PvStable {
        final long t; final String espece; final double v;
        PvStable(long t, String espece, double v) { this.t = t; this.espece = espece; this.v = v; }
    }

    /** Barre de PV d'un camp : valeurs restées immobiles 300 ms, et dernier effet annoncé. */
    private static final class SuiviPv {
        final java.util.ArrayDeque<PvStable> histo = new java.util.ArrayDeque<>();
        String espece = null;
        double valeur = -1;
        long depuisMs = 0;
        boolean stable = false;
        long dernierEffetMs = 0;
        // Arrivée des messages qui changent ces PV (appliqués ~1 s plus tard)
        final java.util.ArrayDeque<Long> effets = new java.util.ArrayDeque<>();

        void noterEffet(long t) {
            dernierEffetMs = t;
            effets.addLast(t);
            while (effets.size() > 60) effets.removeFirst();
        }

        void suivre(Pokemon p) {
            if (p == null) return;
            long t = System.currentTimeMillis();
            double v = p.getPourcentagePv();
            String e = p.getEspece();
            if (!java.util.Objects.equals(e, espece) || Math.abs(v - valeur) > 0.01) {
                espece = e;
                valeur = v;
                depuisMs = t;
                stable = false;
                return;
            }
            if (!stable && t - depuisMs >= 300) {
                stable = true;
                histo.addLast(new PvStable(depuisMs, e, v));
                while (histo.size() > 40) histo.removeFirst();
            }
        }

        boolean stableA(String especeVisee, double pv) {
            return stable && memeEspece(espece, especeVisee) && Math.abs(valeur - pv) < 0.05;
        }

        /**
         * {avant, juste après} le coup lu dans les PV stables, ou null.
         * Un effet annoncé juste avant le coup mais pas encore appliqué
         * (Pièges de Roc à l'entrée, recul...) donne un changement de plus à
         * sauter ; deux, ou un effet collé juste après le coup (les deux
         * changements peuvent alors se fondre), et la mesure est écartée.
         * @param autreCoup instant d'un autre coup sur ce camp juste avant (0 sinon)
         */
        double[] mesure(long instantMs, String especeVisee, long autreCoup) {
            long dernierChangement = 0;
            double v0 = Double.NaN;
            java.util.List<Double> suite = new java.util.ArrayList<>();
            for (PvStable p : histo) {
                if (!memeEspece(p.espece, especeVisee)) continue;
                if (p.t <= instantMs) {
                    dernierChangement = p.t;
                    v0 = p.v;
                } else {
                    suite.add(p.v);
                }
            }
            int enAttente = 0;
            for (long e : effets) {
                if (e > dernierChangement && e < instantMs && e > instantMs - 3000) enAttente++;
            }
            if (autreCoup > dernierChangement && autreCoup < instantMs && autreCoup > instantMs - 10000) enAttente++;
            if (enAttente >= 2) return null;
            for (long e : effets) {
                if (e > instantMs) {
                    if (e - instantMs < 1800) return null;
                    break;
                }
            }
            // Effets annoncés après le coup (Restes, Vampigraine, poison...) :
            // chacun doit avoir laissé sa propre valeur stable, sinon la valeur
            // « juste après le coup » est déjà celle d'après ces effets (Pomme
            // Acide sur Bronzong mesurée 11,6 % au lieu de ~5 %, Restes et
            // Vampigraine compris, logs du 22/10).
            int effetsApres = 0;
            for (long e : effets) if (e > instantMs && e <= instantMs + 6000) effetsApres++;
            if (effetsApres > 0) {
                int changementsApres = 0;
                for (PvStable p : histo) {
                    if (memeEspece(p.espece, especeVisee) && p.t > instantMs && p.t <= instantMs + 8000) changementsApres++;
                }
                if (changementsApres < 1 + effetsApres) return null;
            }
            java.util.List<Double> valeurs = new java.util.ArrayList<>();
            if (!Double.isNaN(v0)) valeurs.add(v0);
            valeurs.addAll(suite);
            if (valeurs.size() < enAttente + 2) return null;
            double avant = valeurs.get(enAttente);
            double apres = valeurs.get(enAttente + 1);
            if (Math.abs(apres - avant) < 0.05) return null;
            return new double[]{avant, apres};
        }

        void vider() {
            histo.clear();
            espece = null;
            valeur = -1;
            stable = false;
            dernierEffetMs = 0;
            effets.clear();
        }
    }
    private static final SuiviPv SUIVI_PV_ADV = new SuiviPv();
    private static final SuiviPv SUIVI_PV_MOI = new SuiviPv();

    private static void suivrePvStables() {
        SUIVI_PV_ADV.suivre(BattleStateTracker.getAdversaireActif());
        SUIVI_PV_MOI.suivre(BattleStateTracker.getJoueurActif());
    }

    /** Message qui change des PV (appliqués plus tard par le jeu) : on note le camp. */
    private static void noterEffetPv(String cle, Object[] args) {
        if (args.length == 0) return;
        boolean effet = cle.startsWith("cobblemon.battle.damage.") || cle.startsWith("cobblemon.battle.heal.")
            || (cle.startsWith("cobblemon.status.") && cle.endsWith(".hurt"))
            || cle.equals("cobblemon.battle.item.eat")
            || cle.startsWith("cobblemon.battle.end.futuresight") || cle.startsWith("cobblemon.battle.end.doomdesire");
        if (!effet) return;
        Boolean adverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
        if (adverse == null) return;
        // heal.drain nomme le Pokémon VIDÉ : c'est l'autre camp qui se soigne
        boolean surAdv = cle.equals("cobblemon.battle.heal.drain") ? !adverse : adverse;
        (surAdv ? SUIVI_PV_ADV : SUIVI_PV_MOI).noterEffet(System.currentTimeMillis());
    }

    // --- Pas de Restes : blessé avant la fin du tour, aucun soin ---
    // Les Restes (et la Boue Noire d'un Poison) soignent à la fin de chaque
    // tour dès qu'il lui manque des PV. S'il était blessé avant cette phase et
    // qu'aucun soin n'est annoncé, il ne les a pas. Sans ça, « Restes
    // (estimé) » restait affiché tout le combat (très courant en random).
    private static long debutTourMs = 0;
    private static long residuelMs = 0;
    private static String especeAuResiduel = null;
    private static final Set<String> SOIGNES_CE_TOUR = new HashSet<>();

    private static boolean estMessageResiduel(String cle) {
        return cle.startsWith("cobblemon.battle.weather.") && (cle.endsWith(".upkeep") || cle.endsWith(".end"))
            || cle.equals("cobblemon.battle.heal.leftovers") || cle.equals("cobblemon.battle.heal.blacksludge")
            || cle.equals("cobblemon.battle.heal.grassyterrain")
            || (cle.startsWith("cobblemon.status.") && cle.endsWith(".hurt"))
            || cle.startsWith("cobblemon.battle.fieldend.") || cle.startsWith("cobblemon.battle.end.futuresight")
            || cle.equals("cobblemon.battle.damage.leechseed") || cle.equals("cobblemon.battle.damage.saltcure");
    }

    private static void suivreRestes(String cle, Object[] args) {
        if (cle.equals("cobblemon.battle.turn")) {
            verifierRestesDuTour();
            debutTourMs = System.currentTimeMillis();
            residuelMs = 0;
            especeAuResiduel = null;
            SOIGNES_CE_TOUR.clear();
            return;
        }
        if (cle.startsWith("cobblemon.battle.heal.") && args.length > 0
                && Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) {
            String e = especeOuActif(args[0]);
            if (e != null) SOIGNES_CE_TOUR.add(e);
        }
        if (residuelMs == 0 && estMessageResiduel(cle)) {
            residuelMs = System.currentTimeMillis();
            Pokemon adv = BattleStateTracker.getAdversaireActif();
            especeAuResiduel = adv != null ? adv.getEspece() : null;
        }
    }

    private static void verifierRestesDuTour() {
        if (debutTourMs == 0 || combatSauvageDetecte) return;
        long finMs = residuelMs != 0 ? residuelMs : System.currentTimeMillis();
        String espece = especeAuResiduel;
        if (espece == null) {
            Pokemon adv = BattleStateTracker.getAdversaireActif();
            espece = adv != null ? adv.getEspece() : null;
        }
        if (espece == null) return;
        for (String e : SOIGNES_CE_TOUR) if (memeEspece(e, espece)) return;
        if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)) return;
        if (objetExclu(espece, "Restes")) return;
        // PV en vigueur pendant le tour, jusqu'à ~1 s après le premier message
        // de fin de tour (le jeu applique les PV avec ce retard)
        double enVigueur = Double.NaN;
        boolean blesse = false;
        for (PvStable p : SUIVI_PV_ADV.histo) {
            if (!memeEspece(p.espece, espece)) continue;
            if (p.t <= debutTourMs) {
                enVigueur = p.v;
                continue;
            }
            if (p.t > finMs + 800) break;
            if (p.v > 0.05 && p.v < 99.5) blesse = true;
        }
        if (!Double.isNaN(enVigueur) && enVigueur > 0.05 && enVigueur < 99.5) blesse = true;
        if (!blesse) return;
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        if (adv == null || !memeEspece(adv.getEspece(), espece) || adv.getPvActuels() <= 0) return;
        OBJETS_EXCLUS.computeIfAbsent(espece, k -> new HashSet<>()).add("Restes");
        OBJETS_EXCLUS.computeIfAbsent(espece, k -> new HashSet<>()).add("Boue Noire");
        OBJETS_PROBABLES.remove(espece, "Restes");
        OBJETS_PROBABLES.remove(espece, "Boue Noire");
        MessageDebugLogger.analyse("OBJET " + espece + " : pas de Restes (blessé, aucun soin en fin de tour)");
    }

    /** Remplit l'état « PV au moment du coup » d'une mesure qui commence. */
    private static void noterInstantDuCoup(CoupDonne cd) {
        cd.instantMs = System.currentTimeMillis();
        cd.pvStableAuCoup = SUIVI_PV_ADV.stableA(cd.especeDefenseur, cd.pvAvant);
        cd.pvAvantDouteux = cd.instantMs - SUIVI_PV_ADV.dernierEffetMs < 2500;
        // L'impact de Prescience suit mon coup du même tour : son changement
        // de PV peut ne pas être encore appliqué
        if (cd.impactPrescience && coupDonne != null && !coupDonne.bloque && !coupDonne.clone
                && memeEspece(coupDonne.especeDefenseur, cd.especeDefenseur)) {
            cd.coupPrecedentMs = coupDonne.instantMs;
        }
    }

    /**
     * {PV avant, PV juste après} mon coup d'après l'historique stable, ou null.
     * Si sa barre bougeait encore au moment du coup, « avant » est la valeur
     * où elle s'est arrêtée.
     */
    private static double[] mesureParPvStables(CoupDonne cd) {
        if (cd.multiCoups || cd.instantMs == 0) return null;
        return SUIVI_PV_ADV.mesure(cd.instantMs, cd.especeDefenseur, cd.coupPrecedentMs);
    }

    /** Même chose pour un coup reçu (ma barre de PV). */
    private static double[] mesureParPvStables(CoupRecu cr) {
        if (cr.multiCoups || cr.instantMs == 0) return null;
        return SUIVI_PV_MOI.mesure(cr.instantMs, cr.especeDefenseur, 0);
    }

    private static void suivreUnCoupDonne(CoupDonne cd, String cle, Object[] args) {
        if (cd == null) return;
        boolean surLui = args.length > 0
            && Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))
            && memeEspece(especeOuActif(args[0]), cd.especeDefenseur);
        if (!cd.impactPrescience && !dernierCoupEstAdverse && (cle.equals("cobblemon.battle.missed") || cle.equals("cobblemon.battle.fail")
                || ((cle.startsWith("cobblemon.battle.activate.protect") || cle.equals("cobblemon.battle.immune"))
                    && surLui))) {
            cd.bloque = true;
        }
        if (cle.equals("cobblemon.battle.crit")) {
            // Prescience : frappe en fin de tour, plus aucune capacité après
            if (cd.impactPrescience || !dernierCoupEstAdverse) cd.crit = true;
        } else if (cle.equals("cobblemon.battle.hit_count")) {
            if (!cd.impactPrescience && !dernierCoupEstAdverse) cd.multiCoups = true;
        } else if (cle.equals("cobblemon.battle.fainted")) {
            if (surLui) cd.ko = true;
        } else if (cle.startsWith("cobblemon.battle.end.futuresight") || cle.startsWith("cobblemon.battle.end.doomdesire")) {
            // Une Prescience lancée deux tours plus tôt frappe le même tour
            if (surLui && !cd.impactPrescience) figerOuInvalider(cd);
        } else if (cle.equals("cobblemon.battle.heal.wish")) {
            if (args.length > 0 && Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) {
                figerOuInvalider(cd);
            }
        } else if (cle.equals("cobblemon.battle.heal.drain")) {
            // « heal.drain | lui » : il a été VIDÉ, c'est moi qui me soigne
            boolean surMoiVide = args.length > 0
                && Boolean.FALSE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])));
            if (surMoiVide) figerOuInvalider(cd);   // lui se soigne en me vidant
        } else if (cle.startsWith("cobblemon.battle.heal.")) {
            if (surLui) {
                String type = cle.substring("cobblemon.battle.heal.".length());
                if (type.equals("leftovers") || type.equals("grassyterrain")) cd.correctionPv += 6.25;
                else figerOuInvalider(cd);
            }
        } else if (cle.startsWith("cobblemon.status.") && cle.endsWith(".hurt")) {
            if (surLui) {
                if (cle.contains("burn")) cd.correctionPv -= 6.25;
                else if (cle.contains("poison") && cd.defenseurBase.getStatut() == Pokemon.Statut.POISON) cd.correctionPv -= 12.5;
                else figerOuInvalider(cd);
            }
        } else if (cle.startsWith("cobblemon.battle.damage.")) {
            if (surLui) {
                // Son Orbe Vie : 10 % de ses PV max, connu exactement.
                if (cle.endsWith(".lifeorb")) cd.correctionPv -= 10.0;
                else figerOuInvalider(cd);
            }
        } else if (cle.equals("cobblemon.battle.switch.other") || cle.equals("cobblemon.battle.withdraw.other")) {
            if (!cd.ko) figerOuInvalider(cd);
        }
    }

    /** Ligne de log pour mon coup du tour écoulé : prévu contre réel. */
    private static void journaliserCoupDonne(CoupDonne cd) {
        if (cd == null) return;
        StringBuilder log = new StringBuilder("COUP DONNÉ ").append(cd.especeAttaquant).append(' ')
            .append(cd.idCoup).append(cd.impactPrescience ? " (impact)" : "").append(" -> ")
            .append(cd.especeDefenseur).append(" | ");
        try {
            if (!cd.impactPrescience && ("futuresight".equals(cd.idCoup) || "doomdesire".equals(cd.idCoup))) {
                log.append("dégâts dans deux tours : pas de mesure");
            } else if (cd.bloque) {
                log.append("coup bloqué (Abri, raté, sans effet ou échoué)");
            } else if (cd.morphing) {
                log.append("Morphing au moment du coup : pas de mesure");
            } else if (cd.clone && !cd.impactPrescience) {
                log.append("touché sur son Clone");
            } else if (mesureParPvStables(cd) == null && cd.pvAvantDouteux) {
                log.append("mesure invalide (effet sur ses PV juste avant le coup, pas encore appliqué)");
            } else if (mesureParPvStables(cd) == null && cd.invalide) {
                log.append("mesure invalide (soin, dégât annexe ou switch après le coup)");
            } else {
                double perte;
                double[] stables = mesureParPvStables(cd);
                boolean parPvStables = stables != null;
                if (parPvStables) {
                    cd.pvAvant = stables[0];
                    cd.correctionPv = 0;
                    if (stables[1] <= 0.05) {
                        cd.ko = true;
                        perte = stables[0];
                    } else {
                        cd.ko = false;
                        perte = stables[0] - stables[1];
                    }
                } else if (cd.ko) {
                    perte = cd.pvAvant;
                } else {
                    Pokemon adv = BattleStateTracker.getAdversaireActif();
                    if (adv == null || !memeEspece(adv.getEspece(), cd.especeDefenseur)) {
                        MessageDebugLogger.analyse(log.append("adversaire touché introuvable").toString());
                        return;
                    }
                    perte = cd.pvAvant - adv.getPourcentagePv() + cd.correctionPv;
                }
                if (perte < -0.5) {
                    // Ses PV ont MONTÉ : un soin s'est glissé dans la mesure (Vœu
                    // reçu en entrant : Eau Bouillante sur Samurott « -70 % », 22/10)
                    MessageDebugLogger.analyse(log.append("PV en hausse (soin pendant la mesure) : ignoré").toString());
                    return;
                }
                MoveTemplate t = Moves.INSTANCE.getByName(cd.idCoup);
                com.tropimon.tropicalc.calc.Move capacite = t == null ? null : convertirCapacite(t);
                log.append("PV avant ").append(pct(cd.pvAvant)).append(" | perte ").append(pct(perte));
                if (cd.correctionPv != 0) log.append(" (corrigée de ").append(pct(cd.correctionPv)).append(")");
                if (parPvStables) log.append(" (PV relus juste après le coup)");
                if (cd.ko) log.append(" K.O. (borne basse)");
                if (cd.crit) log.append(" CRITIQUE");
                if (cd.multiCoups) log.append(" multi-coups");
                if (capacite != null && !capacite.estCapaciteDeStatut()) {
                    Pokemon def = cd.defenseurEstime != null ? cd.defenseurEstime
                        : construireAdversaireEstime(cd.defenseurBase);
                    DamageCalculator.Resultat r = DamageCalculator.calculer(
                        cd.attaquant, def, capacite, cd.etat, cd.etat.getEcransAdversaire(), cd.crit);
                    boolean physique = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
                    log.append(" | prévu ").append(r.immunise ? "immunisé"
                            : pct(r.pourcentageMin) + " - " + pct(r.pourcentageMax))
                        .append(" [son objet ").append(def.getObjet()).append(", talent ").append(def.getTalent())
                        .append(", PV ").append(def.getPvMax())
                        .append(physique ? ", Déf " : ", DéfSpé ")
                        .append(def.getStatCalculee(physique ? Stat.DEFENSE : Stat.DEFENSE_SPE))
                        .append(", mon objet ").append(cd.attaquant.getObjet())
                        .append(physique ? ", mon Atq " : ", mon AtqSpé ")
                        .append(cd.attaquant.getStatCalculee(physique ? Stat.ATTAQUE : Stat.ATTAQUE_SPE)).append(']');
                    boolean coupsFixes = !cd.multiCoups || DamageCalculator.nombreDeCoupsFixe(capacite, cd.attaquant);
                    if (coupsFixes && (cd.impactPrescience || !PUISSANCE_NON_MODELISEE.contains(cd.idCoup))) {
                        noterBilan("donné " + cd.especeAttaquant + " " + cd.idCoup + (cd.impactPrescience ? " (impact)" : "")
                            + " -> " + cd.especeDefenseur + (cd.crit ? " critique" : ""), perte, r, cd.ko);
                    }
                    if (!r.immunise && !cd.ko && coupsFixes) {
                        if (perte > r.pourcentageMax + 2) log.append(" | PLUS que prévu");
                        else if (perte < r.pourcentageMin - 2) log.append(" | MOINS que prévu");
                        if (!cd.crit && !tenterExclureObjetDefensif(cd, def, capacite, perte, log)) {
                            tenterFacteurDefensif(cd, def, capacite, perte, r, log);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.append("erreur : ").append(e);
        }
        MessageDebugLogger.analyse(log.toString());
    }

    // --- Objet défensif supposé démenti par les dégâts ---
    // Veste de Combat / Évoluroc supposés (Smogon, scouting) : si mon coup
    // fait plus que le maximum possible du set le MOINS résistant qui tient
    // cet objet (0 EV en PV et en défense, nature qui baisse la défense,
    // 31 IV), il ne l'a pas. Il faut aussi que le même set SANS l'objet
    // explique la perte, sinon c'est la mesure qui est douteuse.
    private static final Set<String> OBJETS_DEFENSIFS = Set.of("Veste de Combat", "Évoluroc");
    private static final Map<String, Set<String>> OBJETS_EXCLUS = new HashMap<>();
    private static final Map<String, String> PREUVES_EXCLUSION = new HashMap<>();

    private static boolean objetExclu(String espece, String objet) {
        Set<String> s = OBJETS_EXCLUS.get(espece);
        return s != null && objet != null && s.contains(objet);
    }

    /** "Pas de Veste de Combat : Vibrobscur 11,3 % > 10,9 % max", null sinon. */
    public static String getPreuveExclusionObjet(String espece) {
        return PREUVES_EXCLUSION.get(espece);
    }

    /** Vrai si l'objet supposé vient d'être exclu (l'estimation change). */
    private static boolean tenterExclureObjetDefensif(CoupDonne cd, Pokemon def,
            com.tropimon.tropicalc.calc.Move capacite, double perte, StringBuilder log) {
        String objet = def.getObjet();
        if (objet == null || !OBJETS_DEFENSIFS.contains(objet)) return false;
        if (OBJETS_CONFIRMES.containsKey(def.getEspece())) return false;
        boolean physique = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE
            || "psyshock".equals(cd.idCoup) || "psystrike".equals(cd.idCoup) || "secretsword".equals(cd.idCoup);
        Stat statDef = physique ? Stat.DEFENSE : Stat.DEFENSE_SPE;
        if (objet.equals(OBJETS_PROBABLES.get(def.getEspece()))
                && retirerObjetDefensifDeduit(cd, def, capacite, perte, objet, log)) {
            return true;
        }
        Pokemon faible = def.copieAvecEvNature(Map.of(Stat.PV, 0, statDef, 0),
            physique ? com.tropimon.tropicalc.calc.Nature.PRESSE : com.tropimon.tropicalc.calc.Nature.NAIF);
        DamageCalculator.Resultat avec = DamageCalculator.calculer(
            cd.attaquant, faible, capacite, cd.etat, cd.etat.getEcransAdversaire(), false);
        faible.setObjet(null);
        DamageCalculator.Resultat sans = DamageCalculator.calculer(
            cd.attaquant, faible, capacite, cd.etat, cd.etat.getEcransAdversaire(), false);
        if (avec.immunise || sans.immunise) return false;
        double seuil = avec.pourcentageMax * 1.02 + 0.5;
        if (perte <= seuil || perte > sans.pourcentageMax * 1.05 + 1.0) return false;
        String nomCoup = cd.idCoup;
        try {
            MoveTemplate t = Moves.INSTANCE.getByName(cd.idCoup);
            if (t != null) nomCoup = t.getDisplayName().getString();
        } catch (Exception ignored) {
        }
        OBJETS_EXCLUS.computeIfAbsent(def.getEspece(), k -> new HashSet<>()).add(objet);
        OBJETS_PROBABLES.remove(def.getEspece(), objet);
        String preuve = String.format(java.util.Locale.FRANCE, "%s %.1f %% > %.1f %% max avec", nomCoup, perte, avec.pourcentageMax);
        PREUVES_EXCLUSION.put(def.getEspece(), "pas de " + objet + " : " + preuve);
        log.append(" | PAS DE ").append(objet).append(" : ").append(pct(perte)).append(" > ")
            .append(pct(avec.pourcentageMax)).append(" max du set le moins résistant qui la tient");
        return true;
    }

    /**
     * Veste de Combat / Évoluroc seulement déduits (« seul objet qui explique
     * les dégâts », EV supposés Smogon) : si un coup fait nettement plus que
     * prévu avec l'objet et tombe pile dans la fourchette du même set SANS
     * l'objet, la déduction était fausse. Log du 13/10 : Primarina supposé
     * Veste de Combat, Bombe Beurk 48,9 % pour 31-37 % prévu (47-55 % sans) ;
     * le mod gardait la Veste et baissait ses EV à la place.
     */
    private static boolean retirerObjetDefensifDeduit(CoupDonne cd, Pokemon def,
            com.tropimon.tropicalc.calc.Move capacite, double perte, String objet, StringBuilder log) {
        Pokemon estime = def.copieAvecEvNature(Map.of(), null);
        DamageCalculator.Resultat avec = DamageCalculator.calculer(
            cd.attaquant, estime, capacite, cd.etat, cd.etat.getEcransAdversaire(), false);
        estime.setObjet(null);
        DamageCalculator.Resultat sans = DamageCalculator.calculer(
            cd.attaquant, estime, capacite, cd.etat, cd.etat.getEcransAdversaire(), false);
        if (avec.immunise || sans.immunise) return false;
        double marge = Math.max(1.5, avec.pourcentageMax * 0.05);
        if (perte <= avec.pourcentageMax + marge) return false;
        if (perte < sans.pourcentageMin - marge || perte > sans.pourcentageMax + marge) return false;
        String espece = def.getEspece();
        String nomCoup = cd.idCoup;
        try {
            MoveTemplate t = Moves.INSTANCE.getByName(cd.idCoup);
            if (t != null) nomCoup = t.getDisplayName().getString();
        } catch (Exception ignored) {
        }
        OBJETS_EXCLUS.computeIfAbsent(espece, k -> new HashSet<>()).add(objet);
        OBJETS_PROBABLES.remove(espece, objet);
        RAISONS_PROBABLES.remove(espece);
        PREUVES_EXCLUSION.put(espece, String.format(java.util.Locale.FRANCE,
            "pas de %s : %s %.1f %% (%.0f-%.0f %% sans, %.0f %% max avec)", objet, nomCoup, perte,
            sans.pourcentageMin, sans.pourcentageMax, avec.pourcentageMax));
        log.append(" | ").append(objet.toUpperCase(java.util.Locale.FRANCE)).append(" DÉMENTIE : ")
            .append(pct(perte)).append(" colle au même set sans objet (").append(pct(sans.pourcentageMin))
            .append(" - ").append(pct(sans.pourcentageMax)).append(')');
        MessageDebugLogger.analyse("OBJET " + espece + " : " + objet + " retiré (déduction démentie par " + cd.idCoup + ")");
        return true;
    }

    // --- Résistance mesurée sur mes coups (EV défensifs) ---
    // Mon coup fait nettement plus (ou moins) que prévu : on cherche, parmi
    // tous les sets (EV PV 0-252, EV défense 0-252, nature +/neutre/-, même
    // objet et talent supposés), le plus résistant (ou le moins résistant)
    // qui explique encore la perte. Son rapport à l'estimation (PV x défense)
    // devient une borne, appliquée à sa défense dès un écart net (20 %) ou à
    // la deuxième mesure. Plus résistant et moins résistant à la fois :
    // mesures incohérentes, rien n'est appliqué.
    private static final Map<String, Map<Stat, Double>> PLAFONDS_DEFENSIFS = new HashMap<>();
    private static final Map<String, Map<Stat, Double>> PLANCHERS_DEFENSIFS = new HashMap<>();
    private static final Map<String, Integer> MESURES_DEFENSIVES = new HashMap<>();
    private static final Set<String> DEFENSIFS_ACTIFS = new HashSet<>();

    private static void tenterFacteurDefensif(CoupDonne cd, Pokemon def,
            com.tropimon.tropicalc.calc.Move capacite, double perte, DamageCalculator.Resultat prevu,
            StringBuilder log) {
        if (prevu.pourcentageMin < 4.0) return;
        double marge = Math.max(1.5, prevu.pourcentageMax * 0.05);
        boolean plus = perte > prevu.pourcentageMax + marge;
        boolean moins = perte < prevu.pourcentageMin - marge;
        if (!plus && !moins) return;
        if (PUISSANCE_NON_MODELISEE.contains(cd.idCoup) && !cd.impactPrescience) return;
        boolean physique = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE
            || "psyshock".equals(cd.idCoup) || "psystrike".equals(cd.idCoup) || "secretsword".equals(cd.idCoup);
        Stat statDef = physique ? Stat.DEFENSE : Stat.DEFENSE_SPE;
        com.tropimon.tropicalc.calc.Nature[] natures = physique
            ? new com.tropimon.tropicalc.calc.Nature[]{com.tropimon.tropicalc.calc.Nature.ASSURE,
                com.tropimon.tropicalc.calc.Nature.HARDI, com.tropimon.tropicalc.calc.Nature.PRESSE}
            : new com.tropimon.tropicalc.calc.Nature[]{com.tropimon.tropicalc.calc.Nature.CALME,
                com.tropimon.tropicalc.calc.Nature.HARDI, com.tropimon.tropicalc.calc.Nature.NAIF};

        // Estimation SANS facteur déjà appliqué (base commune à toutes les mesures)
        Pokemon brut = def.copieAvecEvNature(Map.of(), null);
        double resistanceEstimee = (double) brut.getPvMax() * brut.getStatCalculee(statDef);
        double meilleure = chercherResistance(cd, def, def.getTalent(), capacite, perte, statDef, natures, plus);
        if (Double.isNaN(meilleure)) {
            // Inexplicable avec le talent supposé : un autre talent possible
            // de l'espèce l'explique-t-il seul ? (log du 13/10 : Milobellus
            // empoisonné avec Écaille Spéciale, estimé Battant ; mes coups
            // faisaient moitié moins que prévu)
            String espece = def.getEspece();
            if (!TALENTS_CONFIRMES.containsKey(espece)) {
                List<String> explications = new ArrayList<>();
                for (String id : talentsBrutsPossibles(def)) {
                    String fr = ShowdownIdMapper.talent(id);
                    if (fr == null || fr.equals(def.getTalent())) continue;
                    if (!Double.isNaN(chercherResistance(cd, def, fr, capacite, perte, statDef, natures, plus))) {
                        explications.add(fr);
                    }
                }
                if (explications.size() == 1) {
                    TALENTS_PROBABLES.put(espece, explications.get(0));
                    log.append(" | talent ").append(explications.get(0))
                        .append(" probable (seul talent possible qui explique la perte)");
                    MessageDebugLogger.analyse("TALENT " + espece + " : " + explications.get(0)
                        + " probable (seul talent qui explique mes dégâts)");
                    return;
                }
            }
            log.append(plus ? " | inexplicable par ses EV (objet ou talent ?)" : " | inexplicable par ses EV");
            return;
        }
        double facteur = meilleure / resistanceEstimee;
        if (plus ? facteur > 0.97 : facteur < 1.03) return;
        String espece = def.getEspece();
        int mesures = MESURES_DEFENSIVES.merge(espece, 1, Integer::sum);
        boolean net = plus ? perte > prevu.pourcentageMax * 1.2 : perte < prevu.pourcentageMin * 0.8;
        if (plus) PLAFONDS_DEFENSIFS.computeIfAbsent(espece, k -> new HashMap<>()).merge(statDef, facteur, Math::min);
        else PLANCHERS_DEFENSIFS.computeIfAbsent(espece, k -> new HashMap<>()).merge(statDef, facteur, Math::max);
        if (net || mesures >= 2) DEFENSIFS_ACTIFS.add(espece);
        log.append(plus ? " | MOINS RÉSISTANT : " : " | PLUS RÉSISTANT : ")
            .append(statDef == Stat.DEFENSE ? "PV x Déf" : "PV x DéfSpé")
            .append(plus ? " au plus x" : " au moins x").append(String.format(java.util.Locale.ROOT, "%.2f", facteur))
            .append(net || mesures >= 2 ? ", appliqué" : ", à confirmer");
        if (!plus) partagerPvEnPlus(cd, brut, capacite, perte, statDef, espece, log);
    }

    /**
     * Plus résistant que prévu : si des EV en PV suffisent à l'expliquer (le
     * set supposé en a moins de 252), ces PV comptent aussi contre l'autre
     * catégorie d'attaque. On applique à l'autre défense la part due aux PV.
     * Log du 13/10 : Dracolosse 25 % plus résistant au Pisto-Poing comme à la
     * Prescience, sans que le mod relie les deux.
     */
    private static void partagerPvEnPlus(CoupDonne cd, Pokemon brut, com.tropimon.tropicalc.calc.Move capacite,
                                         double perte, Stat statDef, String espece, StringBuilder log) {
        int evPv = brut.getEv(Stat.PV);
        if (evPv >= 252) return;
        int pvEstimes = brut.getPvMax();
        for (int ev = evPv + 4; ev <= 252; ev += 4) {
            Pokemon h = brut.copieAvecEvNature(Map.of(Stat.PV, ev), null);
            for (Stat st : Stat.values()) if (st != Stat.PV) h.setStage(st, cd.defenseurEstime.getStage(st));
            DamageCalculator.Resultat r = DamageCalculator.calculer(
                cd.attaquant, h, capacite, cd.etat, cd.etat.getEcransAdversaire(), cd.crit);
            if (r.immunise) return;
            if (perte >= r.pourcentageMin - 1.0 && perte <= r.pourcentageMax + 1.0) {
                double facteurPv = (double) h.getPvMax() / pvEstimes;
                if (facteurPv < 1.03) return;
                Stat autre = statDef == Stat.DEFENSE ? Stat.DEFENSE_SPE : Stat.DEFENSE;
                PLANCHERS_DEFENSIFS.computeIfAbsent(espece, k -> new HashMap<>()).merge(autre, facteurPv, Math::max);
                log.append(" | PV probablement plus hauts (").append(ev).append(" EV) : x")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", facteurPv))
                    .append(autre == Stat.DEFENSE ? " aussi en Déf" : " aussi en DéfSpé");
                return;
            }
        }
    }

    /**
     * Résistance (PV x défense) du set le plus (plus=true : le moins) résistant
     * qui explique la perte avec ce talent, NaN si aucun.
     */
    private static double chercherResistance(CoupDonne cd, Pokemon def, String talent,
            com.tropimon.tropicalc.calc.Move capacite, double perte, Stat statDef,
            com.tropimon.tropicalc.calc.Nature[] natures, boolean plus) {
        double meilleure = plus ? -1 : Double.MAX_VALUE;
        for (com.tropimon.tropicalc.calc.Nature n : natures) {
            for (int evPv = 0; evPv <= 252; evPv += 4) {
                for (int evDef = 0; evDef <= 252; evDef += 4) {
                    Pokemon h = def.copieAvec(Map.of(Stat.PV, evPv, statDef, evDef), n, talent);
                    double resistance = (double) h.getPvMax() * h.getStatCalculee(statDef);
                    if (plus ? resistance <= meilleure : resistance >= meilleure) continue;
                    DamageCalculator.Resultat r = DamageCalculator.calculer(
                        cd.attaquant, h, capacite, cd.etat, cd.etat.getEcransAdversaire(), false);
                    if (r.immunise) return Double.NaN;
                    if (perte >= r.pourcentageMin - 1.0 && perte <= r.pourcentageMax + 1.0) meilleure = resistance;
                }
            }
        }
        return (plus ? meilleure < 0 : meilleure == Double.MAX_VALUE) ? Double.NaN : meilleure;
    }

    // Talent désigné par les dégâts (seul talent possible qui les explique) :
    // utilisé dans les calculs, affiché comme supposé, jamais mémorisé
    private static final Map<String, String> TALENTS_PROBABLES = new HashMap<>();

    public static String getTalentProbable(String espece) {
        return TALENTS_CONFIRMES.containsKey(espece) ? null : TALENTS_PROBABLES.get(espece);
    }

    /** Facteur appliqué à sa défense (1.0 = aucun), une fois établi et cohérent. */
    private static double facteurDefensif(String espece, Stat stat) {
        if (!DEFENSIFS_ACTIFS.contains(espece)) return 1.0;
        Double plafond = PLAFONDS_DEFENSIFS.getOrDefault(espece, Map.of()).get(stat);
        Double plancher = PLANCHERS_DEFENSIFS.getOrDefault(espece, Map.of()).get(stat);
        if (plafond != null && plancher != null) return 1.0;   // incohérent
        if (plafond != null) return plafond;
        if (plancher != null) return plancher;
        return 1.0;
    }

    /** "moins résistant que prévu : DéfSpé x0,85" (ou plus), null si rien d'établi. */
    public static String getFacteurDefensifTexte(String espece) {
        StringBuilder sb = new StringBuilder();
        for (Stat st : new Stat[]{Stat.DEFENSE, Stat.DEFENSE_SPE}) {
            double f = facteurDefensif(espece, st);
            if (f == 1.0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(f < 1 ? "moins" : "plus").append(" résistant que prévu : ")
                .append(st == Stat.DEFENSE ? "Déf" : "DéfSpé").append(" x")
                .append(String.format(java.util.Locale.FRANCE, "%.2f", f));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void photographierCoupRecu(String idCoup, String especeAtt, String especeDef) {
        coupRecu = null;
        if (idCoup == null) return;
        Pokemon defCombat = BattleStateTracker.getJoueurActif();
        Pokemon attaquant = BattleStateTracker.getAdversaireActif();
        if (defCombat == null || attaquant == null) return;
        // Le Pokémon nommé comme cible doit être celui qu'on lit.
        if (especeDef != null && !memeEspece(especeDef, defCombat.getEspece())) return;
        if (especeAtt != null && !memeEspece(especeAtt, attaquant.getEspece())) return;
        Pokemon defenseur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (defenseur == null || !memeEspece(defenseur.getEspece(), defCombat.getEspece())) defenseur = defCombat;
        appliquerObjetReelJoueur(defenseur);
        for (Stat st : Stat.values()) {
            if (st != Stat.PV) defenseur.setStage(st, BoostTracker.getStageJoueur(st));
        }
        defenseur.setStatut(defCombat.getStatut());

        // Types changés en combat (Protéen / Libéro une seule fois par entrée en
        // 9G, Détrempage...) : Amphinobi resté Eau après Surf, Vibrobscur sans
        // bonus de type et Psyko « immunisé » prévu sur lui (logs du 22/10)
        try {
            TypeTracker.appliquer(defenseur, attaquant);
        } catch (Exception ignored) {
        }
        // Tous ses boosts du moment, pas seulement Atq / AtqSpé : Force Ajoutée
        // compte chaque boost (Bronzong +3/+3 après Plénitude, prévu avec
        // +3 AtqSpé seulement : 56-66 % pour 86 % réels, 22/10), Big Splash
        // la Défense.
        for (Stat st : Stat.values()) {
            if (st != Stat.PV) attaquant.setStage(st, BoostTracker.getStageAdversaire(st));
        }
        CoupRecu cr = new CoupRecu();
        cr.idCoup = idCoup;
        cr.morphing = joueurTransforme() || estTransforme(true, attaquant.getEspece());
        cr.torche = TORCHE_ACTIVE.contains("adv:" + attaquant.getEspece());
        cr.charge = chargeAdversaire;
        cr.especeAttaquant = attaquant.getEspece();
        cr.attaquant = attaquant;
        // Estimation AU MOMENT du coup : Dégommage lance l'objet puis le perd
        // (log du 12/10 : Orbe Flamme de Krarmor, prévu 0 % après la perte)
        try {
            cr.attaquantEstime = construireAdversaireEstime(attaquant);
        } catch (Exception ignored) {
        }
        cr.pvAttaquantPct = attaquant.getPourcentagePv();
        cr.statutAttaquant = attaquant.getStatut();
        cr.stageAtk = BoostTracker.getStageAdversaire(Stat.ATTAQUE);
        cr.stageAtkSpe = BoostTracker.getStageAdversaire(Stat.ATTAQUE_SPE);
        cr.especeDefenseur = defCombat.getEspece();
        cr.defenseur = defenseur;
        cr.pvAvant = defCombat.getPourcentagePv();
        cr.instantMs = System.currentTimeMillis();
        cr.pvStableAuCoup = SUIVI_PV_MOI.stableA(cr.especeDefenseur, cr.pvAvant);
        cr.pvAvantDouteux = cr.instantMs - SUIVI_PV_MOI.dernierEffetMs < 2500;
        cr.entreeRecente = cr.instantMs - entreeJoueurMs < 4000;
        cr.etat = FieldTracker.construireField();
        cr.clone = FieldTracker.joueurAUnClone();
        cr.joueurBloqueAvant = joueurBloqueCeTour;
        try {
            com.cobblemon.mod.common.pokemon.Pokemon complet = BattleStateTracker.getPokemonCompletJoueur();
            if (complet != null && memeEspece(complet.getSpecies().showdownId(), defCombat.getEspece())) {
                int min = Integer.MAX_VALUE;
                for (var m : complet.getMoveSet().getMoves()) {
                    if (m != null) min = Math.min(min, m.getTemplate().getPriority());
                }
                if (min != Integer.MAX_VALUE) cr.prioriteMinJoueur = min;
            }
        } catch (Throwable ignored) {
        }
        coupRecu = cr;
    }

    private static void analyserCoupRecu() {
        CoupRecu cr = coupRecu;
        if (cr == null) return;
        String ligne;
        try {
            ligne = analyserCoupRecu(cr);
        } catch (Exception e) {
            ligne = "erreur : " + e;
        }
        MessageDebugLogger.analyse("COUP REÇU " + cr.especeAttaquant + " " + cr.idCoup + " -> "
            + cr.especeDefenseur + " | " + ligne);
    }

    /** Ce que le HUD suppose de l'adversaire et de mon Pokémon au début de ce tour. */
    private static void journaliserEstimation() {
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        Pokemon moi = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (adv == null || moi == null) return;
        Pokemon e = construireAdversaireEstime(adv);
        String espece = adv.getEspece();
        String source = OBJETS_RETIRES.contains(espece) ? "retiré"
            : OBJETS_CONFIRMES.containsKey(espece) ? "confirmé" : "estimé";
        appliquerObjetReelJoueur(moi);
        MessageDebugLogger.analyse("TOUR " + numeroTour + " | adv " + (nomAdversaireCourant != null ? nomAdversaireCourant + ":" : "")
            + espece + " " + pct(adv.getPourcentagePv()) + " objet " + e.getObjet() + " (" + source + ") talent "
            + e.getTalent() + (TALENTS_CONFIRMES.containsKey(espece) ? " (confirmé)" : "")
            + " | PV " + e.getPvMax() + " Atq " + e.getStatCalculee(Stat.ATTAQUE) + " Déf " + e.getStatCalculee(Stat.DEFENSE)
            + " AtqSpé " + e.getStatCalculee(Stat.ATTAQUE_SPE) + " DéfSpé " + e.getStatCalculee(Stat.DEFENSE_SPE)
            + " Vit " + e.getStatCalculee(Stat.VITESSE) + " (obs. min " + getVitesseMinObservee(espece)
            + (getVitesseMaxObservee(espece) < Integer.MAX_VALUE ? ", max " + getVitesseMaxObservee(espece) : "") + ")"
            + " | moi " + moi.getEspece() + " " + pct(moi.getPourcentagePv()) + " objet " + moi.getObjet()
            + " Vit " + moi.getStatCalculee(Stat.VITESSE)
            + " | météo " + FieldTracker.construireField().getMeteo() + " champ " + FieldTracker.construireField().getTerrain());
    }

    // --- Journal des PV ---
    // Une ligne à chaque changement de PV, statut ou boosts des deux Pokémon
    // actifs : permet de rejouer un combat hors du jeu et de vérifier les
    // mesures (le bug des PV max du 11/10 aurait sauté aux yeux).
    private static String dernierePhotoPv = null;
    private static String photoPvEnCours = null;
    private static long photoPvDepuisMs = 0;

    // PV réels de mon Métamorph pendant sa transformation (l'équipe se fige)
    private static final Map<java.util.UUID, Integer> PV_METAMORPH = new HashMap<>();

    private static void suivrePvMetamorph() {
        java.util.UUID id = BattleStateTracker.getUuidJoueurActif();
        if (id == null) return;
        if (cibleMorphingJoueur != null) {
            int pv = BattleStateTracker.getPvBrutJoueurActif();
            if (pv >= 0) PV_METAMORPH.put(id, pv);
        } else if (PV_METAMORPH.containsKey(id) && !BattleStateTracker.joueurEstTransforme()) {
            // De retour sur le terrain, pas (encore) transformé : l'équipe
            // a redonné ses vrais PV
            PV_METAMORPH.remove(id);
        }
    }

    /** PV réels de ce Pokémon de mon équipe s'il a été transformé depuis sa dernière entrée, sinon -1. */
    public static int getPvReelsApresTransformation(java.util.UUID id) {
        Integer pv = id == null ? null : PV_METAMORPH.get(id);
        return pv == null ? -1 : pv;
    }

    private static void journaliserPv() {
        Pokemon moi = BattleStateTracker.getJoueurActif();
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        if (moi == null || adv == null) return;
        String photo = "PV | moi " + moi.getEspece() + " " + moi.getPvActuels() + "/" + moi.getPvMax()
            + " (" + pct(moi.getPourcentagePv()) + ")" + etatCompact(moi, false)
            + " | adv " + adv.getEspece() + " " + pct(adv.getPourcentagePv()) + etatCompact(adv, true);
        // La barre de PV s'anime (une valeur par image pendant ~0,5 s) : on
        // n'écrit que les valeurs restées stables 300 ms.
        long maintenant = System.currentTimeMillis();
        if (!photo.equals(photoPvEnCours)) {
            photoPvEnCours = photo;
            photoPvDepuisMs = maintenant;
            return;
        }
        if (photo.equals(dernierePhotoPv) || maintenant - photoPvDepuisMs < 300) return;
        dernierePhotoPv = photo;
        MessageDebugLogger.analyse(photo);
    }

    private static String etatCompact(Pokemon p, boolean adverse) {
        StringBuilder sb = new StringBuilder();
        if (p.getStatut() != null && p.getStatut() != Pokemon.Statut.AUCUN) sb.append(' ').append(p.getStatut());
        String[] noms = {"Atq", "Déf", "AtqSpé", "DéfSpé", "Vit"};
        Stat[] stats = {Stat.ATTAQUE, Stat.DEFENSE, Stat.ATTAQUE_SPE, Stat.DEFENSE_SPE, Stat.VITESSE};
        for (int i = 0; i < stats.length; i++) {
            int st = adverse ? BoostTracker.getStageAdversaire(stats[i]) : BoostTracker.getStageJoueur(stats[i]);
            if (st != 0) sb.append(' ').append(noms[i]).append(st > 0 ? "+" : "").append(st);
        }
        return sb.toString();
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", v);
    }

    /** Analyse un coup reçu ; renvoie la ligne de log (mesures et conclusion). */
    // --- Set moins offensif que l'estimation (Pyrobut de soutien...) ---
    // Facteur maximal sur sa stat offensive (espèce -> stat -> facteur < 1)
    // et nombre de coups qui l'ont mesuré.
    private static final Map<String, Map<Stat, Double>> PLAFONDS_OFFENSIFS = new HashMap<>();
    private static final Map<String, Integer> MESURES_PLAFOND = new HashMap<>();
    private static final Set<String> PLAFONDS_ACTIFS = new HashSet<>();

    /**
     * Le coup reçu reste nettement sous le minimum prévu : on cherche le set
     * le plus offensif qui l'explique encore (EV 252 -> 0, nature favorable,
     * neutre ou défavorable, même objet et talent supposés). Le rapport à
     * l'estimation devient un plafond, appliqué dès un écart net (< 80 % du
     * minimum) ou à la deuxième mesure. Chaque coup donne une borne haute :
     * on garde la plus basse.
     */
    /** Stat offensive la plus haute (EV 252 -> 0, 3 natures) qui explique la perte, -1 si aucune. */
    private static int statLaPlusOffensive(CoupRecu cr, Pokemon estime, com.tropimon.tropicalc.calc.Move capacite,
                                           Stat statAtk, double perte, String objet, String talent) {
        int meilleure = -1;
        for (com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost n
                : com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.values()) {
            for (int ev = 252; ev >= 0; ev -= 4) {
                Pokemon h = com.tropimon.tropicalc.calc.SetInferenceEngine.construirePokemonHypothetique(
                    estime, statAtk, ev, n, objet, talent);
                int stat = h.getStatCalculee(statAtk);
                if (stat <= meilleure) continue;   // déjà mieux trouvé
                for (Stat st : Stat.values()) {
                    if (st != Stat.PV) h.setStage(st, estime.getStage(st));   // Def (Big Splash), tous les boosts (Force Ajoutée)
                }
                h.setStage(Stat.ATTAQUE, cr.stageAtk);
                h.setStage(Stat.ATTAQUE_SPE, cr.stageAtkSpe);
                if (cr.statutAttaquant != null) h.setStatut(cr.statutAttaquant);
                DamageCalculator.Resultat r = DamageCalculator.calculer(
                    h, cr.defenseur, capacite, cr.etat, cr.etat.getEcransJoueur(), false);
                // Pourcentages arrondis à l'affichage : 1 point de marge
                if (perte >= r.pourcentageMin - 1.0 && perte <= r.pourcentageMax + 1.0) {
                    meilleure = stat;
                    break;   // EV décroissants : le premier qui colle est le plus offensif
                }
            }
        }
        return meilleure;
    }

    private static void tenterPlafondOffensif(CoupRecu cr, Pokemon estime,
            com.tropimon.tropicalc.calc.Move capacite, Stat statAtk, double perte,
            DamageCalculator.Resultat prevu, StringBuilder log) {
        // Mes PV sont exacts : un coup sous le jet minimum de plus de 3 % est
        // un vrai écart. L'ancien seuil (-10 % -1 point) laissait passer les
        // petits coups répétés (Demi-Tour d'Félinferno à 9 % pour 10,1-12 %
        // prévus, quatre fois de suite, logs du 22/10).
        if (prevu.pourcentageMin < 6.0 || perte >= prevu.pourcentageMin * 0.97 - 0.5) return;
        String espece = cr.especeAttaquant;
        // Stat de l'estimation SANS plafond déjà appliqué (base commune à
        // toutes les mesures)
        int statEstimee = (int) Math.round(estime.getStatCalculee(statAtk) / plafondOffensif(espece, statAtk));
        if (statEstimee <= 0) return;
        String objet = estime.getObjet() != null ? estime.getObjet() : StatHypothesis.AUCUN;
        String talent = estime.getTalent() != null ? estime.getTalent() : StatHypothesis.AUCUN;
        // Objet offensif supposé (Bandeau / Lunettes Choix, Orbe Vie) : si le
        // coup colle SANS lui avec son set estimé, c'est l'objet qui est faux,
        // pas ses EV. Avant, on baissait sa stat et on gardait l'objet
        // (Magnézone « Lunettes Choix (estimé) », Tonnerre à 28 % pour 42-50 %
        // prévus : AtqSpé ramenée à 271 au lieu d'écarter les Lunettes, 22/10).
        boolean objetOffensifSuppose = ("Orbe Vie".equals(objet) || "Bandeau Choix".equals(objet)
                || "Lunettes Choix".equals(objet))
            && !OBJETS_CONFIRMES.containsKey(espece) && !OBJETS_RETIRES.contains(espece);
        if (objetOffensifSuppose) {
            int sans = statLaPlusOffensive(cr, estime, capacite, statAtk, perte, StatHypothesis.AUCUN, talent);
            if (sans >= 0 && sans >= estime.getStatCalculee(statAtk) * 0.97) {
                OBJETS_EXCLUS.computeIfAbsent(espece, k -> new HashSet<>()).add(objet);
                if ("Orbe Vie".equals(objet)) ORBE_VIE_EXCLUE.add(espece);
                OBJETS_PROBABLES.remove(espece, objet);
                log.append(" | PAS DE ").append(objet).append(" : colle sans objet avec son set estimé");
                MessageDebugLogger.analyse("OBJET " + espece + " : pas de " + objet
                    + " (" + cr.idCoup + " colle sans objet avec son set estimé)");
                return;
            }
        }
        int meilleure = statLaPlusOffensive(cr, estime, capacite, statAtk, perte, objet, talent);
        if (meilleure < 0) {
            // Même sans EV avec l'objet supposé, ça fait trop : l'objet supposé
            // (Bandeau, Lunettes, Orbe Vie...) est faux s'il n'est pas confirmé.
            // Logs du 19/10 : Gorythmic « Bandeau Choix (estimé) », Sabotage
            // à 31,6 % sur Cizayox pour 51-61 % prévus, plusieurs fois.
            boolean objetSuppose = !StatHypothesis.AUCUN.equals(objet)
                && !OBJETS_CONFIRMES.containsKey(espece) && !OBJETS_RETIRES.contains(espece);
            if (objetSuppose && statLaPlusOffensive(cr, estime, capacite, statAtk, perte, StatHypothesis.AUCUN, talent) >= 0) {
                OBJETS_EXCLUS.computeIfAbsent(espece, k -> new HashSet<>()).add(objet);
                if ("Orbe Vie".equals(objet)) ORBE_VIE_EXCLUE.add(espece);
                OBJETS_PROBABLES.remove(espece, objet);
                log.append(" | PAS DE ").append(objet).append(" : trop peu de dégâts même sans EV avec");
                MessageDebugLogger.analyse("OBJET " + espece + " : pas de " + objet
                    + " (" + cr.idCoup + " trop faible même sans EV avec cet objet)");
                return;
            }
            log.append(" | MOINS que prévu, inexplicable par ses EV");
            return;
        }
        double facteur = (double) meilleure / statEstimee;
        if (facteur > 0.97) return;
        int mesures = MESURES_PLAFOND.merge(espece, 1, Integer::sum);
        boolean net = perte < prevu.pourcentageMin * 0.8;
        PLAFONDS_OFFENSIFS.computeIfAbsent(espece, k -> new HashMap<>()).merge(statAtk, facteur, Math::min);
        if (net || mesures >= 2) PLAFONDS_ACTIFS.add(espece);
        log.append(" | MOINS que prévu : ").append(statAtk == Stat.ATTAQUE ? "Atq" : "AtqSpé")
            .append(" au plus ").append(meilleure).append(" (x").append(String.format("%.2f", facteur)).append(")")
            .append(net || mesures >= 2 ? ", appliqué" : ", à confirmer");
    }

    /**
     * Facteur à appliquer à sa stat offensive (1.0 = aucun), une fois établi :
     * plafond (set moins offensif) ou plancher (set plus offensif) ; les deux
     * à la fois = mesures incohérentes, rien n'est appliqué.
     */
    private static double plafondOffensif(String espece, Stat stat) {
        Double plafond = PLAFONDS_ACTIFS.contains(espece)
            ? PLAFONDS_OFFENSIFS.getOrDefault(espece, Map.of()).get(stat) : null;
        Double plancher = PLANCHERS_ACTIFS.contains(espece)
            ? PLANCHERS_OFFENSIFS.getOrDefault(espece, Map.of()).get(stat) : null;
        if (plafond != null && plancher != null) return 1.0;
        if (plafond != null) return plafond;
        if (plancher != null) return plancher;
        return 1.0;
    }

    /** "moins offensif que prévu : Atq x0,80" (ou plus), null si rien d'établi. */
    public static String getPlafondOffensifTexte(String espece) {
        StringBuilder sb = new StringBuilder();
        for (Stat st : new Stat[]{Stat.ATTAQUE, Stat.ATTAQUE_SPE}) {
            double f = plafondOffensif(espece, st);
            if (f == 1.0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(f < 1 ? "moins" : "plus").append(" offensif que prévu : ")
                .append(st == Stat.ATTAQUE ? "Atq" : "AtqSpé").append(" x")
                .append(String.format(java.util.Locale.FRANCE, "%.2f", f));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // --- Set plus offensif que l'estimation ---
    // Son coup fait nettement plus que le maximum prévu alors que les objets
    // qui l'expliqueraient sont écartés (Choix exclu, Orbe Vie exclue, ou
    // objet connu) : on cherche le set le MOINS offensif qui l'explique
    // (EV 0 -> 252, nature -/neutre/+). Rapport à l'estimation = plancher,
    // appliqué dès un écart net (> 120 % du max) ou à la deuxième mesure.
    // Log du 12/10 : Chartor offensif, Ball'Météo 45 % pour 25-30 % prévus.
    private static final Map<String, Map<Stat, Double>> PLANCHERS_OFFENSIFS = new HashMap<>();
    private static final Map<String, Integer> MESURES_PLANCHER = new HashMap<>();
    private static final Set<String> PLANCHERS_ACTIFS = new HashSet<>();

    private static void tenterPlancherOffensif(CoupRecu cr, Pokemon estime,
            com.tropimon.tropicalc.calc.Move capacite, Stat statAtk, double perte,
            DamageCalculator.Resultat prevu, StringBuilder log) {
        if (prevu.pourcentageMax < 4.0 || perte <= prevu.pourcentageMax * 1.1 + 1.5) return;
        String espece = cr.especeAttaquant;
        boolean objetConnu = OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece);
        boolean objetsBoostEcartes = objetConnu
            || (OBJETS_CHOIX_EXCLUS.contains(espece) && ORBE_VIE_EXCLUE.contains(espece));
        if (!objetsBoostEcartes) return;
        int statEstimee = (int) Math.round(estime.getStatCalculee(statAtk) / plafondOffensif(espece, statAtk));
        if (statEstimee <= 0) return;
        String objet = estime.getObjet() != null ? estime.getObjet() : StatHypothesis.AUCUN;
        String talent = estime.getTalent() != null ? estime.getTalent() : StatHypothesis.AUCUN;
        int moindre = Integer.MAX_VALUE;
        com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost[] natures = {
            com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.BAISSEE,
            com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.NEUTRE,
            com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.BOOSTEE};
        for (com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost n : natures) {
            for (int ev = 0; ev <= 252; ev += 4) {
                Pokemon h = com.tropimon.tropicalc.calc.SetInferenceEngine.construirePokemonHypothetique(
                    estime, statAtk, ev, n, objet, talent);
                int stat = h.getStatCalculee(statAtk);
                if (stat >= moindre) continue;
                for (Stat st : Stat.values()) {
                    if (st != Stat.PV) h.setStage(st, estime.getStage(st));   // Def (Big Splash), tous les boosts (Force Ajoutée)
                }
                h.setStage(Stat.ATTAQUE, cr.stageAtk);
                h.setStage(Stat.ATTAQUE_SPE, cr.stageAtkSpe);
                if (cr.statutAttaquant != null) h.setStatut(cr.statutAttaquant);
                DamageCalculator.Resultat r = DamageCalculator.calculer(
                    h, cr.defenseur, capacite, cr.etat, cr.etat.getEcransJoueur(), false);
                if (perte >= r.pourcentageMin - 1.0 && perte <= r.pourcentageMax + 1.0) {
                    moindre = stat;
                    break;   // EV croissants : le premier qui colle est le moins offensif
                }
            }
        }
        if (moindre == Integer.MAX_VALUE) {
            log.append(" | PLUS que prévu, inexplicable par ses EV");
            return;
        }
        double facteur = (double) moindre / statEstimee;
        if (facteur < 1.03) return;
        int mesures = MESURES_PLANCHER.merge(espece, 1, Integer::sum);
        boolean net = perte > prevu.pourcentageMax * 1.2;
        PLANCHERS_OFFENSIFS.computeIfAbsent(espece, k -> new HashMap<>()).merge(statAtk, facteur, Math::max);
        if (net || mesures >= 2) PLANCHERS_ACTIFS.add(espece);
        log.append(" | PLUS que prévu : ").append(statAtk == Stat.ATTAQUE ? "Atq" : "AtqSpé")
            .append(" au moins ").append(moindre).append(" (x").append(String.format("%.2f", facteur)).append(")")
            .append(net || mesures >= 2 ? ", appliqué" : ", à confirmer");
    }

    /** Instant de mon dernier switch.self (barre de PV pas encore synchronisée). */
    private static long entreeJoueurMs = 0;

    private static String analyserCoupRecu(CoupRecu cr) {
        StringBuilder log = new StringBuilder();
        if (cr.bloque) return "coup bloqué (Abri, raté, sans effet ou échoué)";
        // Coup reçu juste après mon entrée : la barre montre encore l'ancien
        // Pokémon ou 100 %, puis passe aux PV d'entrée, pris pour le coup
        // (Avalanche sur Tarpaud entré à 83,5 % : "perte 10,2 %" au lieu de
        // 34,8 %, et le Séisme suivant mesuré avec les dégâts d'Avalanche)
        if (cr.entreeRecente) return "mesure invalide (je venais d'entrer, PV pas encore à jour)";
        double[] stables = mesureParPvStables(cr);
        if (stables == null && cr.pvAvantDouteux) {
            return "mesure invalide (effet sur mes PV juste avant le coup, pas encore appliqué)";
        }
        if (stables == null && cr.invalide) return "mesure invalide (soin, dégât annexe, switch ou capacité coûtant des PV après le coup)";
        // Morphing au moment du coup (analysé au tour suivant, Métamorph
        // parfois déjà sorti : Pisto-Poing sur Métamorph-Cizayox comparé à
        // un Métamorph normal, logs du 22/10)
        if (cr.morphing || estTransforme(true, cr.especeAttaquant) || joueurTransforme()) {
            return "Morphing en cours : ignoré";
        }
        if (cr.clone) return "touché sur un Clone";

        MoveTemplate template = Moves.INSTANCE.getByName(cr.idCoup);
        if (template == null) return "capacité inconnue";
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null) return "capacité non convertie";
        boolean physique = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        if (!physique && capacite.getCategorie() != com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE) {
            return "capacité de statut";
        }

        // Perte due au coup seul (ou borne basse si K.O.).
        double perte;
        if (stables != null) {
            // Ma barre juste après son coup, avant Restes, recul ou autre effet
            cr.pvAvant = stables[0];
            cr.correctionPv = 0;
            cr.ko = stables[1] <= 0.05;
            perte = cr.ko ? stables[0] : stables[0] - stables[1];
        } else if (cr.ko) {
            perte = cr.pvAvant;
        } else {
            Pokemon actif = BattleStateTracker.getJoueurActif();
            if (actif == null || !memeEspece(actif.getEspece(), cr.especeDefenseur)) return "Pokémon touché introuvable";
            perte = cr.pvAvant - actif.getPourcentagePv() + cr.correctionPv;
        }
        if (perte < -0.5) return "PV en hausse (soin pendant la mesure) : ignoré";
        log.append("PV avant ").append(pct(cr.pvAvant)).append(" | perte ").append(pct(perte));
        if (cr.correctionPv != 0) log.append(" (corrigée de ").append(pct(cr.correctionPv)).append(")");
        if (cr.ko) log.append(" K.O. (borne basse)");

        // Ce que le HUD prévoyait, avec l'adversaire estimé tel qu'affiché.
        Pokemon estime = cr.attaquantEstime != null ? cr.attaquantEstime : construireAdversaireEstime(cr.attaquant);
        // Objet supposé au moment du coup, exclu juste après (Choix exclu par
        // sa 2e capacité, Orbe Vie sans recul) : on mesure avec l'objet estimé
        // maintenant. Log du 13/10 : Hameçon, Demi-Tour mesuré avec un Bandeau
        // exclu une milliseconde plus tard, d'où « Atq au plus 311 » à tort.
        String objetSuppose = estime.getObjet();
        if (objetSuppose != null && !OBJETS_CONFIRMES.containsKey(cr.especeAttaquant)
                && ((objetSuppose.endsWith("Choix") && OBJETS_CHOIX_EXCLUS.contains(cr.especeAttaquant))
                    || ("Orbe Vie".equals(objetSuppose) && ORBE_VIE_EXCLUE.contains(cr.especeAttaquant)))) {
            String remplacant = null;
            try {
                remplacant = construireAdversaireEstime(cr.attaquant).getObjet();
            } catch (Exception ignored) {
            }
            if (remplacant != null && (remplacant.endsWith("Choix") || "Orbe Vie".equals(remplacant))) remplacant = null;
            estime.setObjet(remplacant);
            log.append(" (").append(objetSuppose).append(" exclu depuis, recalculé avec ").append(remplacant).append(')');
        }
        // Objet confirmé juste après le coup (recul d'Orbe Vie annoncé après
        // les dégâts) : c'est lui qui a compté. Log du 19/10 : Darumacho
        // mesuré avec un Mouchoir supposé, Orbe Vie confirmée par son recul,
        // d'où un faux « plus que prévu, inexplicable ».
        String objetConfirmeDepuis = OBJETS_CONFIRMES.get(cr.especeAttaquant);
        if (objetConfirmeDepuis != null && !objetConfirmeDepuis.equals(estime.getObjet())
                && !OBJETS_RETIRES.contains(cr.especeAttaquant)) {
            log.append(" (").append(objetConfirmeDepuis).append(" confirmé depuis, recalculé avec)");
            estime.setObjet(objetConfirmeDepuis);
        }
        estime.setStage(Stat.ATTAQUE, cr.stageAtk);
        estime.setStage(Stat.ATTAQUE_SPE, cr.stageAtkSpe);
        DamageCalculator.Resultat prevu = DamageCalculator.calculer(
            estime, cr.defenseur, capacite, cr.etat, cr.etat.getEcransJoueur(), false);
        Stat statAtk = physique ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
        log.append(" | prévu ").append(prevu.immunise ? "immunisé"
                : pct(prevu.pourcentageMin) + " - " + pct(prevu.pourcentageMax))
            .append(" [objet ").append(estime.getObjet()).append(", talent ").append(estime.getTalent())
            .append(", ").append(physique ? "Atq " : "AtqSpé ").append(estime.getStatCalculee(statAtk))
            .append(" stage ").append(physique ? cr.stageAtk : cr.stageAtkSpe)
            .append(", météo ").append(cr.etat.getMeteo()).append(", champ ").append(cr.etat.getTerrain()).append(']');

        if (!cr.crit && !cr.multiCoups && !capacite.isMultiCoups() && !PUISSANCE_NON_MODELISEE.contains(cr.idCoup)) {
            noterBilan("reçu " + cr.especeAttaquant + " " + cr.idCoup + " -> " + cr.especeDefenseur, perte, prevu, cr.ko);
        }

        // --- Talent : un autre talent possible explique seul le coup ---
        // (le set supposé prend le talent le plus joué ; Multiécaille, Peau
        // Féérique, Adaptabilité... changent les dégâts du tout au tout)
        boolean talentTrouve = false;
        if (!cr.crit && !cr.ko && !cr.multiCoups && !capacite.isMultiCoups() && !prevu.immunise
                && !PUISSANCE_NON_MODELISEE.contains(cr.idCoup) && !TALENTS_CONFIRMES.containsKey(cr.especeAttaquant)) {
            double marge = Math.max(1.5, prevu.pourcentageMax * 0.05);
            if (perte > prevu.pourcentageMax + marge || perte < prevu.pourcentageMin - marge) {
                List<String> explications = new ArrayList<>();
                for (String id : talentsBrutsPossibles(cr.attaquant)) {
                    String fr = ShowdownIdMapper.talent(id);
                    if (fr == null || fr.equals(estime.getTalent())) continue;
                    Pokemon h = estime.copieAvec(Map.of(), null, fr);
                    DamageCalculator.Resultat r = DamageCalculator.calculer(
                        h, cr.defenseur, capacite, cr.etat, cr.etat.getEcransJoueur(), false);
                    if (!r.immunise && perte >= r.pourcentageMin - 1.0 && perte <= r.pourcentageMax + 1.0) explications.add(fr);
                }
                if (explications.size() == 1) {
                    String esp = cr.especeAttaquant;
                    if (!explications.get(0).equals(TALENTS_PROBABLES.put(esp, explications.get(0)))) {
                        MessageDebugLogger.analyse("TALENT " + esp + " : " + explications.get(0)
                            + " probable (seul talent qui explique ses dégâts)");
                    }
                    log.append(" | talent ").append(explications.get(0)).append(" probable (explique seul le coup)");
                    talentTrouve = true;
                }
            }
        }

        // --- Set moins (ou plus) offensif que l'estimation ---
        if (!talentTrouve && !cr.crit && !cr.ko && !cr.multiCoups && !capacite.isMultiCoups() && !prevu.immunise
                && !PUISSANCE_NON_MODELISEE.contains(cr.idCoup)) {
            tenterPlafondOffensif(cr, estime, capacite, statAtk, perte, prevu, log);
            tenterPlancherOffensif(cr, estime, capacite, statAtk, perte, prevu, log);
        }

        // --- Détection Bandeau / Lunettes Choix ---
        if (cr.crit) return log.append(" | coup critique : pas de conclusion").toString();
        if (cr.multiCoups || capacite.isMultiCoups()) return log.append(" | multi-coups : pas de conclusion").toString();
        String espece = cr.especeAttaquant;
        if (OBJETS_CONFIRMES.containsKey(espece)) {
            return log.append(" | objet déjà confirmé : ").append(OBJETS_CONFIRMES.get(espece)).toString();
        }
        if (OBJETS_RETIRES.contains(espece)) return log.append(" | objet retiré").toString();
        if (OBJETS_CHOIX_EXCLUS.contains(espece)) return log.append(" | Choix déjà exclu").toString();
        if (PUISSANCE_NON_MODELISEE.contains(cr.idCoup)) return log.append(" | puissance non modélisée").toString();
        if (perte < 3.0) return log.append(" | perte trop faible").toString();

        // Talents absents du calcul : abstention ou seuil relevé.
        Set<String> bruts = talentsBrutsPossibles(cr.attaquant);
        if (seuilAutreQueChoix(bruts, capacite, physique, cr.pvAttaquantPct, cr.torche, cr.charge) < 0) {
            return log.append(" | talent possible indiscernable d'un Choix ").append(bruts).toString();
        }
        // Les x1.2 (objet du type de l'attaque, Ceinture Pro si super efficace)
        // sont-ils plausibles chez cette espèce ? Si Smogon les donne quasi
        // absents et le Choix courant, tout dépassement du maximum sans objet
        // suffit. L'Orbe Vie est déjà exclue : le jeu annonce son recul.
        double seuil = Math.max(seuilTalents(bruts), seuilObjets(espece, capacite, physique, cr.defenseur));

        // Maximum SANS objet : 252 EV, nature favorable, meilleur talent possible,
        // dans l'état exact du moment du coup.
        Set<String> talents = new HashSet<>();
        String talentConfirme = TALENTS_CONFIRMES.get(espece);
        if (talentConfirme != null) {
            talents.add(talentConfirme);
        } else {
            Set<String> reels = getTalentsReelsEspece(cr.attaquant);
            if (reels != null) talents.addAll(reels);
            if (estime.getTalent() != null) talents.add(estime.getTalent());
        }
        if (talents.isEmpty()) talents.add(StatHypothesis.AUCUN);

        double max = 0;
        for (String talent : talents) {
            Pokemon h = com.tropimon.tropicalc.calc.SetInferenceEngine.construirePokemonHypothetique(
                estime, statAtk, 252, com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.BOOSTEE,
                StatHypothesis.AUCUN, talent);
            for (Stat st : Stat.values()) {
                if (st != Stat.PV) h.setStage(st, estime.getStage(st));   // Def (Big Splash), tous les boosts (Force Ajoutée)
            }
            h.setStage(Stat.ATTAQUE, cr.stageAtk);
            h.setStage(Stat.ATTAQUE_SPE, cr.stageAtkSpe);
            if (cr.statutAttaquant != null) h.setStatut(cr.statutAttaquant);
            DamageCalculator.Resultat r = DamageCalculator.calculer(
                h, cr.defenseur, capacite, cr.etat, cr.etat.getEcransJoueur(), false);
            if (r.immunise) return log.append(" | immunisé selon le calcul").toString();
            max = Math.max(max, r.pourcentageMax);
        }
        double plancher = max * seuil * 1.03 + 1.0;   // au-delà : ni sans objet, ni objet x1.2
        double plafond = max * 1.5 * 1.03 + 1.0;      // au-delà : autre chose qu'un Choix
        log.append(" | max sans objet ").append(pct(max)).append(" seuil x").append(seuil)
            .append(" -> zone Choix ").append(pct(plancher)).append(" - ").append(pct(plafond));
        if (max < 3.0) return log.append(" | coup trop faible").toString();

        // Sur un K.O., la perte est une borne basse (il a fait AU MOINS ça) :
        // la même règle reste valable, elle ne peut que rater un Choix.
        if (perte > plancher && perte <= plafond) {
            String objet = physique ? "Bandeau Choix" : "Lunettes Choix";
            String nomCapacite;
            try {
                nomCapacite = template.getDisplayName().getString();
            } catch (Exception e) {
                nomCapacite = cr.idCoup;
            }
            confirmerObjet(espece, objet, nomCapacite + " : " + (cr.ko ? ">= " : "") + Math.round(perte)
                + " % > " + Math.round(max) + " % max sans objet" + (cr.ko ? " (K.O.)" : ""));
            return log.append(" | => ").append(objet).append(" CONFIRMÉ").toString();
        }
        return log.append(perte > plafond ? " | au-dessus d'un Choix : autre cause" : " | explicable sans Choix").toString();
    }

    /**
     * Multiplicateur maximal qu'autre chose qu'un objet Choix peut expliquer
     * (objets de type et Ceinture Pro x1.2, talents non modélisés), ou -1 si
     * un talent possible donne x1.5 ou plus : indiscernable, on s'abstient.
     */
    private static final Map<PokemonType, String> OBJET_DU_TYPE = Map.ofEntries(
        Map.entry(PokemonType.ELECTRIK, "magnet"), Map.entry(PokemonType.VOL, "sharpbeak"),
        Map.entry(PokemonType.COMBAT, "blackbelt"), Map.entry(PokemonType.FEU, "charcoal"),
        Map.entry(PokemonType.DRAGON, "dragonfang"), Map.entry(PokemonType.PSY, "twistedspoon"),
        Map.entry(PokemonType.EAU, "mysticwater"), Map.entry(PokemonType.GLACE, "nevermeltice"),
        Map.entry(PokemonType.PLANTE, "miracleseed"), Map.entry(PokemonType.TENEBRES, "blackglasses"),
        Map.entry(PokemonType.ACIER, "metalcoat"), Map.entry(PokemonType.POISON, "poisonbarb"),
        Map.entry(PokemonType.ROCHE, "hardstone"), Map.entry(PokemonType.INSECTE, "silverpowder"),
        Map.entry(PokemonType.SPECTRE, "spelltag"), Map.entry(PokemonType.SOL, "softsand"),
        Map.entry(PokemonType.NORMAL, "silkscarf"), Map.entry(PokemonType.FEE, "fairyfeather"));
    private static final Map<PokemonType, String> PLAQUE_DU_TYPE = Map.ofEntries(
        Map.entry(PokemonType.ELECTRIK, "zapplate"), Map.entry(PokemonType.VOL, "skyplate"),
        Map.entry(PokemonType.COMBAT, "fistplate"), Map.entry(PokemonType.FEU, "flameplate"),
        Map.entry(PokemonType.DRAGON, "dracoplate"), Map.entry(PokemonType.PSY, "mindplate"),
        Map.entry(PokemonType.EAU, "splashplate"), Map.entry(PokemonType.GLACE, "icicleplate"),
        Map.entry(PokemonType.PLANTE, "meadowplate"), Map.entry(PokemonType.TENEBRES, "dreadplate"),
        Map.entry(PokemonType.ACIER, "ironplate"), Map.entry(PokemonType.POISON, "toxicplate"),
        Map.entry(PokemonType.ROCHE, "stoneplate"), Map.entry(PokemonType.INSECTE, "insectplate"),
        Map.entry(PokemonType.SPECTRE, "spookyplate"), Map.entry(PokemonType.SOL, "earthplate"),
        Map.entry(PokemonType.FEE, "pixieplate"));

    /** Seuil côté objets : 1.2 par défaut, 1.0 si Smogon rend les x1.2 invraisemblables. */
    private static double seuilObjets(String espece, com.tropimon.tropicalc.calc.Move capacite,
                                      boolean physique, Pokemon defenseur) {
        double choix = SmogonDataLoader.fractionObjet(espece, physique ? "choiceband" : "choicespecs");
        if (choix < 0.10) return 1.2;   // espèce inconnue (-1) ou Choix rare : prudence
        PokemonType type = capacite.getType();
        double x12 = 0;
        String objType = OBJET_DU_TYPE.get(type);
        String plaque = PLAQUE_DU_TYPE.get(type);
        if (objType != null) x12 += SmogonDataLoader.fractionObjet(espece, objType);
        if (plaque != null) x12 += SmogonDataLoader.fractionObjet(espece, plaque);
        try {
            if (type.efficaciteContre(defenseur.getType1(), defenseur.getType2()) > 1.0) {
                x12 += SmogonDataLoader.fractionObjet(espece, "expertbelt");
            }
        } catch (Exception e) {
            return 1.2;
        }
        return x12 < 0.05 ? 1.0 : 1.2;
    }

    /** Seuil côté talents non modélisés (1.0 = aucun). */
    private static double seuilTalents(Set<String> bruts) {
        double s = 1.0;
        if (bruts.contains("magicguard") || bruts.contains("sheerforce")) s = Math.max(s, 1.3);
        if (bruts.contains("protosynthesis") || bruts.contains("quarkdrive")) s = Math.max(s, 1.3);
        if (bruts.contains("analytic")) s = Math.max(s, 1.3);
        if (bruts.contains("rivalry")) s = Math.max(s, 1.25);   // dépend du sexe, inconnu
        if (bruts.contains("orichalcumpulse") || bruts.contains("hadronengine")) s = Math.max(s, 1.34);
        return s;
    }

    private static double seuilAutreQueChoix(Set<String> bruts, com.tropimon.tropicalc.calc.Move capacite,
                                              boolean physique, double pvAttaquantPct,
                                              boolean torcheAuCoup, boolean chargeAuCoup) {
        // Seuls restent ici les talents x1.5 que le calcul NE modélise PAS
        // (Torche activée, Électrogenèse, Énergie Éolienne) : indiscernables
        // d'un Choix. Entêtement, Force Soleil, Méga Blaster, Expert/Boost Acier,
        // Engrais & co, les Peau ... et Hydrata-Son sont désormais calculés,
        // donc inclus dans le maximum sans objet.
        // Seulement quand l'effet est réellement en place : Torche activée et
        // capacité Feu, Chargeur (Électrogenèse / Énergie Éolienne, annoncé par
        // le jeu) et capacité Électrik. Sinon ces talents ne changent rien :
        // Fulgulairo s'abstenait à chaque Tonnerre (Lunettes Choix vues
        // seulement par élimination, logs du 22/10).
        PokemonType type = capacite.getType();
        if (bruts.contains("flashfire") && type == PokemonType.FEU && torcheAuCoup) return -1;
        if ((bruts.contains("electromorphosis") || bruts.contains("windpower"))
                && type == PokemonType.ELECTRIK && chargeAuCoup) return -1;
        return 1.0;
    }

    /** Vitesse minimale observée pour une espèce adverse (0 si aucune observation). */
    // K.O. par camp, comptés depuis cobblemon.battle.fainted (une espèce = un
    // K.O. : la clause d'espèce interdit les doublons dans une équipe).
    private static final Set<String> KO_ADVERSAIRE = new HashSet<>();
    private static final Set<String> KO_JOUEUR = new HashSet<>();
    public static int getNombreKoAdversaire() { return KO_ADVERSAIRE.size(); }
    public static int getNombreKoJoueur() { return KO_JOUEUR.size(); }

    /**
     * Espèce nommée par un argument de message ; pour un Pokémon surnommé
     * (le message donne "Master_Sans:Pichon", sans l'espèce), celle du
     * Pokémon actif de ce camp. Log du 11/10 : le K.O. de Pyrobut surnommé
     * n'était pas reconnu, la mesure finissait en « adversaire introuvable ».
     */
    private static String especeOuActif(Object arg) {
        String espece = especeDepuisArgument(arg);
        if (espece != null) return espece;
        Boolean adverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(arg));
        if (adverse == null) return null;
        // Le dernier Pokémon annoncé à l'entrée : plus sûr que l'état du jeu,
        // pas encore mis à jour quand le message est lu
        String entre = adverse ? derniereEntreeAdv : derniereEntreeMoi;
        if (entre != null) return entre;
        Pokemon actif = adverse ? BattleStateTracker.getAdversaireActif() : BattleStateTracker.getJoueurActif();
        return actif != null ? actif.getEspece() : null;
    }

    // --- Dernier Pokémon entré de chaque camp, lu dans les messages d'entrée ---
    // ("switch.other.nickname | Slycos_ | Kraft | weezing" donne l'espèce même
    // pour un Pokémon surnommé)
    private static String derniereEntreeAdv = null;
    private static String derniereEntreeMoi = null;

    private static void suivreEntrees(String cle, Object[] args) {
        boolean adv = cle.startsWith("cobblemon.battle.switch.other");
        boolean moi = cle.startsWith("cobblemon.battle.switch.self");
        boolean traine = cle.startsWith("cobblemon.battle.dragged_out");
        if (!adv && !moi && !traine) return;
        String espece = null;
        for (Object a : args) {
            espece = especeDeTexte(a);
            if (espece != null) break;
        }
        if (traine) {
            if (args.length == 0) return;
            Boolean adverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            if (adverse == null) return;
            adv = adverse;
        }
        if (espece == null) return;
        if (adv) {
            derniereEntreeAdv = espece;
            especeSejourIllusion = espece;
            faitsAvantSejour = capturerFaits(espece);
        } else {
            derniereEntreeMoi = espece;
        }
    }

    // --- Illusion (Zoroark) ---
    // Le Zoroark entre déguisé : capacités, objet, recul d'Orbe Vie... sont
    // notés sur l'espèce imitée. Au message end.illusion, tout ce qui a été
    // appris depuis son entrée repart sur le Zoroark et l'espèce imitée
    // retrouve ses faits d'avant (log du 12/10 : Orbe Vie et Explonuit
    // attribués à Flingouste, c'était le Zoroark).
    private static final class FaitsEspece {
        LinkedHashSet<String> coups;
        Map<String, Integer> pp;
        boolean retire, ovExclue, choixExclu;
        String objet, raison, preuveChoix, talent, perdu;
    }
    private static String especeSejourIllusion = null;
    private static FaitsEspece faitsAvantSejour = null;

    private static FaitsEspece capturerFaits(String e) {
        FaitsEspece f = new FaitsEspece();
        f.coups = COUPS_ADVERSAIRE.containsKey(e) ? new LinkedHashSet<>(COUPS_ADVERSAIRE.get(e)) : null;
        f.pp = PP_UTILISES.containsKey(e) ? new HashMap<>(PP_UTILISES.get(e)) : null;
        f.retire = OBJETS_RETIRES.contains(e);
        f.ovExclue = ORBE_VIE_EXCLUE.contains(e);
        f.choixExclu = OBJETS_CHOIX_EXCLUS.contains(e);
        f.objet = OBJETS_CONFIRMES.get(e);
        f.raison = RAISONS_OBJET.get(e);
        f.preuveChoix = PREUVE_PAS_CHOIX.get(e);
        f.talent = TALENTS_CONFIRMES.get(e);
        f.perdu = OBJETS_PERDUS_CONNUS.get(e);
        return f;
    }

    private static <K, V> void remettre(Map<K, V> m, K k, V v) {
        if (v == null) m.remove(k);
        else m.put(k, v);
    }

    private static void remettre(Set<String> s, String k, boolean present) {
        if (present) s.add(k);
        else s.remove(k);
    }

    private static void restaurerFaits(String e, FaitsEspece f) {
        remettre(COUPS_ADVERSAIRE, e, f.coups);
        remettre(PP_UTILISES, e, f.pp);
        remettre(OBJETS_RETIRES, e, f.retire);
        remettre(ORBE_VIE_EXCLUE, e, f.ovExclue);
        remettre(OBJETS_CHOIX_EXCLUS, e, f.choixExclu);
        remettre(OBJETS_CONFIRMES, e, f.objet);
        remettre(RAISONS_OBJET, e, f.raison);
        remettre(PREUVE_PAS_CHOIX, e, f.preuveChoix);
        remettre(TALENTS_CONFIRMES, e, f.talent);
        remettre(OBJETS_PERDUS_CONNUS, e, f.perdu);
    }

    private static void suivreIllusion(String cle, Object[] args) {
        if (!cle.startsWith("cobblemon.battle.end.illusion") || args.length == 0) return;
        if (!Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) return;
        // Les coups de ce tour visaient / venaient du Zoroark, pas de l'espèce imitée
        if (coupRecu != null) coupRecu.invalide = true;
        if (coupDonne != null) coupDonne.invalide = true;
        String vrai = especeDepuisArgument(args[0]);
        String faux = especeSejourIllusion;
        if (vrai == null || faux == null || vrai.equals(faux) || faitsAvantSejour == null) return;
        FaitsEspece apres = capturerFaits(faux);
        FaitsEspece avant = faitsAvantSejour;
        restaurerFaits(faux, avant);
        if (apres.coups != null) {
            for (String c : apres.coups) {
                if (avant.coups == null || !avant.coups.contains(c)) {
                    COUPS_ADVERSAIRE.computeIfAbsent(vrai, k -> new LinkedHashSet<>()).add(c);
                }
            }
        }
        if (apres.pp != null) {
            for (Map.Entry<String, Integer> e : apres.pp.entrySet()) {
                int avantPp = avant.pp != null ? avant.pp.getOrDefault(e.getKey(), 0) : 0;
                if (e.getValue() > avantPp) {
                    PP_UTILISES.computeIfAbsent(vrai, k -> new HashMap<>()).merge(e.getKey(), e.getValue() - avantPp, Integer::sum);
                }
            }
        }
        if (apres.objet != null && !apres.objet.equals(avant.objet)) confirmerObjet(vrai, apres.objet, apres.raison);
        if (apres.retire && !avant.retire) OBJETS_RETIRES.add(vrai);
        if (apres.ovExclue && !avant.ovExclue) ORBE_VIE_EXCLUE.add(vrai);
        if (apres.choixExclu && !avant.choixExclu) OBJETS_CHOIX_EXCLUS.add(vrai);
        if (apres.preuveChoix != null && avant.preuveChoix == null) PREUVE_PAS_CHOIX.put(vrai, apres.preuveChoix);
        if (apres.talent != null && !apres.talent.equals(avant.talent)) TALENTS_CONFIRMES.put(vrai, apres.talent);
        if (apres.perdu != null && !apres.perdu.equals(avant.perdu)) OBJETS_PERDUS_CONNUS.put(vrai, apres.perdu);
        MessageDebugLogger.analyse("ILLUSION : c'était " + vrai + " déguisé en " + faux
            + ", ce qui a été vu depuis son entrée lui est réattribué");
        derniereEntreeAdv = vrai;
        especeSejourIllusion = vrai;
        faitsAvantSejour = capturerFaits(vrai);
    }

    /** Espèce contenue dans un argument (clé cobblemon.species.X, directe ou dans owned_pokemon). */
    private static String especeDeTexte(Object arg) {
        if (arg instanceof Text t && t.getContent() instanceof TranslatableTextContent c) {
            String k = c.getKey();
            if (k != null && k.startsWith("cobblemon.species.")) {
                String sp = k.substring("cobblemon.species.".length());
                return sp.endsWith(".name") ? sp.substring(0, sp.length() - 5) : sp;
            }
            for (Object sous : c.getArgs()) {
                String sp = especeDeTexte(sous);
                if (sp != null) return sp;
            }
            return null;
        }
        if (arg instanceof String str) {
            int i = str.indexOf("cobblemon.species.");
            if (i >= 0) {
                int fin = str.indexOf(".name", i);
                if (fin > i) return str.substring(i + "cobblemon.species.".length(), fin);
            }
        }
        return null;
    }

    /** owned_pokemon(dresseur, translation{cobblemon.species.X.name}) -> "X". */
    private static String especeDepuisArgument(Object arg) {
        if (!(arg instanceof Text texte) || !(texte.getContent() instanceof TranslatableTextContent contenuArg)) return null;
        for (Object sous : contenuArg.getArgs()) {
            if (sous instanceof Text t && t.getContent() instanceof TranslatableTextContent c) {
                String k = c.getKey();
                if (k != null && k.startsWith("cobblemon.species.")) {
                    String s = k.substring("cobblemon.species.".length());
                    return s.endsWith(".name") ? s.substring(0, s.length() - 5) : s;
                }
            }
        }
        return null;
    }

    public static int getVitesseMinObservee(String espece) {
        return VITESSES_MIN_OBSERVEES.getOrDefault(espece, 0);
    }

    /** Integer.MAX_VALUE = aucun plafond observé (pas de limite réelle connue). */
    public static int getVitesseMaxObservee(String espece) {
        return VITESSES_MAX_OBSERVEES.getOrDefault(espece, Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------
    // Combat double : conclusions par espèce, appelées par InferenceDouble
    // (qui sait quel Pokémon a fait quoi). Mêmes faits que le simple :
    // l'estimation adverse (construireAdversaireEstime) les utilise telle quelle.
    // ------------------------------------------------------------------

    public static void confirmerObjetDouble(String espece, String objetFr, String raison) {
        if (espece == null || objetFr == null) return;
        OBJETS_RETIRES.remove(espece);
        if (!objetFr.equals(OBJETS_CONFIRMES.get(espece))) {
            confirmerObjet(espece, objetFr, raison);
            MessageDebugLogger.analyse("DOUBLE OBJET " + espece + " : " + objetFr + " (" + raison + ")");
        }
    }

    public static void objetPerduDouble(String espece, String detail) {
        if (espece != null) marquerObjetAdversairePerdu(espece, detail);
    }

    public static void confirmerTalentDouble(String espece, String talentAnglais) {
        if (espece == null || talentAnglais == null) return;
        String talentFr = ShowdownIdMapper.talent(talentAnglais);
        if (talentFr == null) return;
        TALENTS_CONFIRMES.put(espece, talentFr);
        if ("Épine de Fer".equals(talentFr) || "Peau Dure".equals(talentFr)) TALENTS_CHIP_CONFIRMES.add(espece);
    }

    /** Deux capacités différentes sans sortir : pas d'objet Choix. */
    public static void exclureChoixDouble(String espece, String preuve) {
        if (espece == null || OBJETS_CHOIX_EXCLUS.contains(espece)) return;
        OBJETS_CHOIX_EXCLUS.add(espece);
        PREUVE_PAS_CHOIX.put(espece, preuve);
        String objet = OBJETS_CONFIRMES.get(espece);
        MessageDebugLogger.analyse("DOUBLE OBJET " + espece + " : pas d'objet Choix (" + preuve + ")"
            + (objet != null && objet.contains("Choix") ? " ; " + objet + " confirmé auparavant" : ""));
    }

    /**
     * Ordre d'action en double entre un adversaire et un de mes Pokémon, même
     * priorité : il a agi avant (sa vitesse >= la mienne, Mouchoir si aucune
     * version sans objet n'y arrive) ou après (sa vitesse <= la mienne).
     */
    public static void observerVitesseDouble(Pokemon adversaire, String idAdv, int stageVitAdv, boolean tailwindAdv,
                                             Pokemon joueur, String idJoueur, double vJoueur, boolean adversaireAvant) {
        String debut = "DOUBLE VITESSE " + adversaire.getEspece() + " " + idAdv + " / moi " + joueur.getEspece()
            + " " + idJoueur + " (" + (int) vJoueur + ") | " + (adversaireAvant ? "il a agi avant" : "j'ai agi avant") + " | ";
        if (!prioritesEgales(idJoueur, idAdv)) {
            MessageDebugLogger.analyse(debut + "priorités différentes : ignoré");
            return;
        }
        if (prioriteModifiee(idAdv, talentsBrutsPossibles(adversaire)) || prioriteModifiee(idJoueur, talentBrutJoueur(joueur))) {
            MessageDebugLogger.analyse(debut + "priorité donnée par un talent ou un champ : ignoré");
            return;
        }
        if (vJoueur <= 0) return;
        int ancienStage = stageVitAdvTourEcoule;
        boolean ancienVent = tailwindAdvTourEcoule;
        Pokemon.Statut ancienStatut = statutAdvTourEcoule;
        Field.Meteo ancienneMeteo = meteoTourEcoule;
        Field.TypeTerrain ancienTerrain = terrainTourEcoule;
        Field etat = FieldTracker.construireField();
        stageVitAdvTourEcoule = stageVitAdv;
        tailwindAdvTourEcoule = tailwindAdv;
        statutAdvTourEcoule = adversaire.getStatut();
        meteoTourEcoule = etat.getMeteo();
        terrainTourEcoule = etat.getTerrain();
        try {
            if (adversaireAvant) {
                MessageDebugLogger.analyse(debut + "=> sa vitesse >= " + (int) vJoueur);
                conclureAdversairePlusRapide(adversaire, vJoueur);
            } else {
                Set<String> bruts = talentsBrutsPossibles(adversaire);
                if (stageVitAdv < 0 || adversaire.getStatut() == Pokemon.Statut.PARALYSIE
                        || bruts.contains("stall") || bruts.contains("myceliummight")) {
                    MessageDebugLogger.analyse(debut + "vitesse réduite ou talent lent : pas de plafond");
                    return;
                }
                VITESSES_MAX_OBSERVEES.merge(adversaire.getEspece(), (int) vJoueur, Math::min);
                MessageDebugLogger.analyse(debut + "=> sa vitesse <= " + (int) vJoueur);
            }
        } finally {
            stageVitAdvTourEcoule = ancienStage;
            tailwindAdvTourEcoule = ancienVent;
            statutAdvTourEcoule = ancienStatut;
            meteoTourEcoule = ancienneMeteo;
            terrainTourEcoule = ancienTerrain;
        }
    }

    /**
     * Coup reçu en double, perte mesurée sur mes PV (serveur) : prévu contre
     * réel, puis Bandeau / Lunettes Choix si aucune version sans objet
     * n'atteint la perte. etat porte déjà le x0,75 de zone et le double.
     */
    public static String analyserCoupRecuDouble(Pokemon attaquantBase, Pokemon estime, Pokemon defenseur,
                                                String idCoup, Field etat, double perte, boolean ko) {
        StringBuilder log = new StringBuilder();
        MoveTemplate template = Moves.INSTANCE.getByName(idCoup);
        if (template == null) return "capacité inconnue";
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null) return "capacité non convertie";
        boolean physique = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        if (!physique && capacite.getCategorie() != com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE) {
            return "capacité de statut";
        }
        Stat statAtk = physique ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
        int stageAtk = estime.getStage(Stat.ATTAQUE);
        int stageAtkSpe = estime.getStage(Stat.ATTAQUE_SPE);
        DamageCalculator.Resultat prevu = DamageCalculator.calculer(estime, defenseur, capacite, etat, etat.getEcransJoueur(), false);
        log.append("perte ").append(ko ? ">= " : "").append(pct(perte))
            .append(" | prévu ").append(prevu.immunise ? "immunisé" : pct(prevu.pourcentageMin) + " - " + pct(prevu.pourcentageMax))
            .append(" [objet ").append(estime.getObjet()).append(", talent ").append(estime.getTalent())
            .append(", ").append(physique ? "Atq " : "AtqSpé ").append(estime.getStatCalculee(statAtk))
            .append(" stage ").append(physique ? stageAtk : stageAtkSpe)
            .append(etat.isCibleMultiple() ? ", zone x0,75" : "").append(']');

        String espece = attaquantBase.getEspece();
        if (OBJETS_CONFIRMES.containsKey(espece)) return log.append(" | objet déjà confirmé : ").append(OBJETS_CONFIRMES.get(espece)).toString();
        if (OBJETS_RETIRES.contains(espece)) return log.append(" | objet retiré").toString();
        if (OBJETS_CHOIX_EXCLUS.contains(espece)) return log.append(" | Choix déjà exclu").toString();
        if (PUISSANCE_NON_MODELISEE.contains(idCoup)) return log.append(" | puissance non modélisée").toString();
        if (capacite.isMultiCoups()) return log.append(" | multi-coups").toString();
        if (perte < 3.0) return log.append(" | perte trop faible").toString();

        Set<String> bruts = talentsBrutsPossibles(attaquantBase);
        if (seuilAutreQueChoix(bruts, capacite, physique, attaquantBase.getPourcentagePv(), true, true) < 0) {
            return log.append(" | talent possible indiscernable d'un Choix ").append(bruts).toString();
        }
        double seuil = Math.max(seuilTalents(bruts), seuilObjets(espece, capacite, physique, defenseur));

        Set<String> talents = new HashSet<>();
        String talentConfirme = TALENTS_CONFIRMES.get(espece);
        if (talentConfirme != null) {
            talents.add(talentConfirme);
        } else {
            Set<String> reels = getTalentsReelsEspece(attaquantBase);
            if (reels != null) talents.addAll(reels);
            if (estime.getTalent() != null) talents.add(estime.getTalent());
        }
        if (talents.isEmpty()) talents.add(StatHypothesis.AUCUN);

        double max = 0;
        for (String talent : talents) {
            Pokemon h = com.tropimon.tropicalc.calc.SetInferenceEngine.construirePokemonHypothetique(
                estime, statAtk, 252, com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.BOOSTEE,
                StatHypothesis.AUCUN, talent);
            for (Stat st : Stat.values()) {
                if (st != Stat.PV) h.setStage(st, estime.getStage(st));   // Def (Big Splash), tous les boosts (Force Ajoutée)
            }
            h.setStage(Stat.ATTAQUE, stageAtk);
            h.setStage(Stat.ATTAQUE_SPE, stageAtkSpe);
            if (attaquantBase.getStatut() != null) h.setStatut(attaquantBase.getStatut());
            DamageCalculator.Resultat r = DamageCalculator.calculer(h, defenseur, capacite, etat, etat.getEcransJoueur(), false);
            if (r.immunise) return log.append(" | immunisé selon le calcul").toString();
            max = Math.max(max, r.pourcentageMax);
        }
        double plancher = max * seuil * 1.03 + 1.0;
        double plafond = max * 1.5 * 1.03 + 1.0;
        log.append(" | max sans objet ").append(pct(max)).append(" seuil x").append(seuil)
            .append(" -> zone Choix ").append(pct(plancher)).append(" - ").append(pct(plafond));
        if (max < 3.0) return log.append(" | coup trop faible").toString();
        if (perte > plancher && perte <= plafond) {
            String objet = physique ? "Bandeau Choix" : "Lunettes Choix";
            String nomCapacite;
            try {
                nomCapacite = template.getDisplayName().getString();
            } catch (Exception e) {
                nomCapacite = idCoup;
            }
            confirmerObjet(espece, objet, nomCapacite + " : " + (ko ? ">= " : "") + Math.round(perte)
                + " % > " + Math.round(max) + " % max sans objet" + (ko ? " (K.O.)" : "") + " (double)");
            return log.append(" | => ").append(objet).append(" CONFIRMÉ").toString();
        }
        return log.append(perte > plafond ? " | au-dessus d'un Choix : autre cause" : " | explicable sans Choix").toString();
    }

    private static void enregistrerObservation(boolean adversaireEtaitAttaquant, double perte,
                                                Pokemon adversaire, Pokemon joueur,
                                                MoveUseTracker.CoupDetecte coup) {
        MoveTemplate template = Moves.INSTANCE.getByName(coup.showdownId());
        if (template == null) return;
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.estCapaciteDeStatut()) return;

        String objetConfirmeDejaSu = OBJETS_CONFIRMES.get(adversaire.getEspece());
        String talentConfirmeDejaSu = TALENTS_CONFIRMES.get(adversaire.getEspece());
        ProfilAdversaire profil = PROFILS.computeIfAbsent(adversaire.getEspece(), k -> {
            Set<String> talentsReels = getTalentsReelsEspece(adversaire);
            SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(adversaire.getEspece());
            return new ProfilAdversaire(talentsReels, smogon, objetConfirmeDejaSu, talentConfirmeDejaSu);
        });
        // Une confirmation arrivée APRÈS la première construction du profil
        // (le cas le plus fréquent) doit quand même verrouiller les
        // candidats, sans perdre les plages EV déjà resserrées.
        profil.verrouillerSiConfirme(objetConfirmeDejaSu, talentConfirmeDejaSu);

        Field terrainNeutre = FieldTracker.construireField();
        double observeMin = Math.max(0, perte - TOLERANCE_POURCENT);
        double observeMax = perte + TOLERANCE_POURCENT;
        profil.enregistrerObservation(adversaireEtaitAttaquant, adversaire, joueur, capacite, terrainNeutre,
            observeMin, observeMax);

        // Baie de résistance (Occa, Passho, etc.) : le joueur a attaqué
        // l'adversaire, qui encaisse nettement moins que prévu.
        if (!adversaireEtaitAttaquant) {
            // adversaire est ici l'objet BRUT (BattleStateTracker) : pour un
            // calcul de dégâts attendus correct (EV/objet défensif estimés),
            // il faut l'objet reconstruit, même bug que celui trouvé sur le
            // Ballon et Herbe Blanche.
            tenterConfirmerBaieResistance(construireAdversaireEstime(adversaire), joueur, capacite, perte, terrainNeutre);
        }

        // --- Détection d'objet par signal fort, distincte du moteur de correction ---
        // Bandeau/Lunettes Choix : voir analyserCoupRecu (mesure au moment
        // du coup, indépendante des switchs et des K.O.).

        // Correction directe par écart prévu/réel : s'applique immédiatement
        // à toutes les lignes du HUD, y compris pour tes autres Pokémon.
        try {
            if (!capacite.isMultiCoups()) {
                Pokemon attaquant = adversaireEtaitAttaquant ? construireAdversaireEstime(adversaire) : joueur;
                Pokemon defenseur = adversaireEtaitAttaquant ? joueur : construireAdversaireEstime(adversaire);

                // La prédiction de référence doit inclure les STAGES actuels
                // (Mur de Fer etc.), sinon leur effet est compté deux fois :
                // une fois par le boost, une fois par la "correction".
                for (Stat st : Stat.values()) {
                    if (st == Stat.PV) continue;
                    int stJoueur = BoostTracker.getStageJoueur(st);
                    int stAdv = BoostTracker.getStageAdversaire(st);
                    if (adversaireEtaitAttaquant) {
                        if (stAdv != 0) attaquant.setStage(st, stAdv);
                        if (stJoueur != 0) defenseur.setStage(st, stJoueur);
                    } else {
                        if (stJoueur != 0) attaquant.setStage(st, stJoueur);
                        if (stAdv != 0) defenseur.setStage(st, stAdv);
                    }
                }

                DamageCalculator.Resultat prevu = DamageCalculator.calculer(
                    attaquant, defenseur, capacite, terrainNeutre, null, false);
                double milieuPrevu = (prevu.pourcentageMin + prevu.pourcentageMax) / 2.0;

                // Le delta de PV observé est NET des résiduels du défenseur
                // (Restes qui soignent, Toxik/Salaison qui rongent) : on les
                // retire pour isoler les dégâts du coup lui-même.
                double perteCoup = perte;
                try {
                    boolean defEstAdversaire = !adversaireEtaitAttaquant;
                    boolean talentConf = !defEstAdversaire
                        || getTalentConfirme(adversaire.getEspece()) != null;
                    java.util.Set<String> talentsPoss = defEstAdversaire
                        ? getTalentsReelsEspece(adversaire) : null;
                    boolean soinIncertain = defEstAdversaire && !talentConf
                        && talentsPoss != null && talentsPoss.contains("Soin Poison");
                    com.tropimon.tropicalc.calc.ResidualProjector.Projection proj =
                        com.tropimon.tropicalc.calc.ResidualProjector.projeter(
                            defenseur, terrainNeutre.getMeteo(), true,
                            defEstAdversaire ? getCompteurToxikProchainAdversaire() : getCompteurToxikProchainJoueur(),
                            defEstAdversaire ? adversaireSalaison : joueurSalaison,
                            defEstAdversaire ? adversaireVampigraine : joueurVampigraine,
                            talentConf, soinIncertain);
                    if (proj != null) perteCoup = Math.max(0, perte - proj.netPremierTourPct());
                } catch (Exception ignored2) {
                }

                // Coup critique probable (réel >> prévu max) : ne rien conclure
                boolean critProbable = perteCoup > prevu.pourcentageMax * 1.4;
                if (milieuPrevu > 1.0 && !prevu.immunise && !critProbable) {
                    double ratio = perteCoup / milieuPrevu;
                    // Zone morte élargie : un roll bas + un résiduel mal estimé
                    // ne doivent pas déclencher de correction
                    if (ratio < 0.75 || ratio > 1.3) {
                        Stat cible = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE
                            ? (adversaireEtaitAttaquant ? Stat.ATTAQUE : Stat.DEFENSE)
                            : (adversaireEtaitAttaquant ? Stat.ATTAQUE_SPE : Stat.DEFENSE_SPE);
                        majFacteur(adversaire.getEspece(), cible, ratio);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static final boolean PROFIL_ANCIEN_ACTIF = false;

    public static Pokemon construireAdversaireEstime(Pokemon adversaireBase) {
        String espece = adversaireBase.getEspece();
        ProfilAdversaire profil = PROFILS.get(espece);
        SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(espece);
        boolean objetRetire = OBJETS_RETIRES.contains(espece);
        // Pré-évolution : sur Tropimon, l'Évoluroc est le seul objet possible.
        // Tenu pour certain dès son entrée (Colossinge, logs du 19/10).
        if (!objetRetire && !OBJETS_CONFIRMES.containsKey(espece) && estPreEvolution(espece)) {
            confirmerObjet(espece, "Évoluroc", "pré-évolution, seul objet possible");
            if ("Évoluroc".equals(OBJETS_CONFIRMES.get(espece))) {
                MessageDebugLogger.analyse("OBJET " + espece + " : Évoluroc (pré-évolution, seul objet possible)");
            }
        }
        tenterConfirmerEvoluroc(espece, smogon);

        Pokemon.Builder b = Pokemon.builder(espece, adversaireBase.getNiveau(),
            adversaireBase.getType1(), adversaireBase.getType2());
        b.poids(adversaireBase.getPoidsHg());
        for (Stat s : Stat.values()) {
            b.statBase(s, adversaireBase.getStatBase(s));
        }

        // Scouting inter-combats : pré-remplir les faits des combats passés
        ScoutingStore.Faits scout = ScoutingStore.get(nomAdversaireCourant, espece);
        if (scout != null && !ESPECES_SCOUT_FUSIONNEES.contains(espece)) {
            ESPECES_SCOUT_FUSIONNEES.add(espece);
            if (!scout.capacites.isEmpty()) {
                for (String capaciteScoutee : scout.capacites) {
                    ajouterCapaciteAdversaire(espece, capaciteScoutee, false);
                }
            }
            if (scout.chipTalent) TALENTS_CHIP_CONFIRMES.add(espece);
        }

        String objetConfirme = OBJETS_CONFIRMES.get(espece);

        if (smogon != null && !smogon.topSpreads().isEmpty()) {
            SmogonDataLoader.ParsedSpread top = smogon.topSpreads().get(0);
            b.ev(Stat.PV, top.hpEv());
            b.ev(Stat.ATTAQUE, top.atkEv());
            b.ev(Stat.DEFENSE, top.defEv());
            b.ev(Stat.ATTAQUE_SPE, top.spaEv());
            b.ev(Stat.DEFENSE_SPE, top.spdEv());
            b.ev(Stat.VITESSE, top.speEv());
            b.nature(ShowdownIdMapper.nature(top.natureShowdownId()));
            if (!objetRetire && objetConfirme == null) {
                String fr = objetSmogonProbable(espece, smogon);
                if (fr != null) b.objet(fr);
            }
            if (!smogon.topAbilitiesShowdownId().isEmpty()) {
                String fr = ShowdownIdMapper.talent(smogon.topAbilitiesShowdownId().get(0));
                if (fr != null) b.talent(fr);
            }
        }

        // Ancien profil (pertes de PV comparées d'un tour à l'autre) : il ne
        // corrige plus ni les EV ni l'objet. Ses pertes mélangent Torrent,
        // boosts, deux coups par tour et changements de Pokémon ; les mesures
        // coup par coup (plafond/plancher offensif, résistance mesurée,
        // exclusion de Veste) les remplacent. Logs : Primarina passé à
        // AtqSpé 351 et « Veste de Combat probable » à tort, Dragapult
        // « Lunettes Choix probable » alors que le Choix était exclu,
        // Hydrapple « Orbe Vie probable » sans recul annoncé.
        if (PROFIL_ANCIEN_ACTIF && profil != null && profil.getNbObservations() >= 3) {
            appliquerHypothese(b, Stat.ATTAQUE, profil.attaque);
            appliquerHypothese(b, Stat.ATTAQUE_SPE, profil.attaqueSpe);
            appliquerHypothese(b, Stat.DEFENSE, profil.defense);
            appliquerHypothese(b, Stat.DEFENSE_SPE, profil.defenseSpe);

            if (!objetRetire && objetConfirme == null) {
                String objetEstime = extraireObjetUnique(profil.attaque);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.attaqueSpe);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defense);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defenseSpe);
                if (objetEstime != null && objetExclu(espece, objetEstime)) objetEstime = null;
                if (objetEstime != null) {
                    b.objet(objetEstime);
                    // "Probable", jamais "confirmé" : l'élimination suppose
                    // que ses EV sont dans les répartitions Smogon. Un set
                    // inhabituel (plus d'EV défensifs) imite un objet - la
                    // Hatterene "confirmée" Veste de Combat après un seul coup.
                    // Appliqué au calcul, affiché comme probable, jamais
                    // mémorisé entre les combats.
                    // (appelé à chaque image : une ligne de log au changement)
                    RAISONS_PROBABLES.put(espece, "seul objet qui colle aux dégâts mesurés");
                    if (!objetEstime.equals(OBJETS_PROBABLES.put(espece, objetEstime))) {
                        MessageDebugLogger.analyse("OBJET " + espece + " : " + objetEstime
                            + " probable (seul objet qui explique les dégâts, " + profil.getNbObservations()
                            + " coups mesurés, EV supposés Smogon)");
                    }
                }
            }

            String talentEstime = extraireTalentUnique(profil.attaque);
            if (talentEstime == null) talentEstime = extraireTalentUnique(profil.attaqueSpe);
            // N'écrase le talent Smogon que si le talent inféré modifie réellement
            // les dégâts : l'inférence ne peut rien conclure sur les talents neutres
            // (ex: Épine de Fer écrasé par Anticipation = régression pure)
            if (talentEstime != null
                    && com.tropimon.tropicalc.calc.AbilityModifier.pour(talentEstime) != null) {
                b.talent(talentEstime);
            }
        }

        // Objet/talent des combats passés : meilleure estimation que Smogon,
        // mais le "?" reste (le set a pu changer depuis)
        if (scout != null) {
            if (scout.objet != null && objetConfirme == null && !objetRetire && !objetExclu(espece, scout.objet)) {
                b.objet(scout.objet);
            }
            // Imposteur hors Métamorph : erreur d'attribution d'anciens combats
            // (talent du Métamorph entrant donné au Pokémon qu'il remplaçait)
            if (scout.talent != null && !("Imposteur".equals(scout.talent) && !"ditto".equals(espece))) {
                b.talent(scout.talent);
            }
        }

        // Objet probable (élimination par les dégâts, Évoluroc) : plus fiable
        // que le scouting et Smogon, moins qu'une confirmation
        String objetProbable = OBJETS_PROBABLES.get(espece);
        if (objetProbable != null && objetConfirme == null && !objetRetire && !objetExclu(espece, objetProbable)) {
            b.objet(objetProbable);
        }

        if (objetConfirme != null && !objetRetire) {
            b.objet(objetConfirme);
        }

        String talentProbable = TALENTS_PROBABLES.get(espece);
        if (talentProbable != null) b.talent(talentProbable);
        String talentConfirme = TALENTS_CONFIRMES.get(espece);
        if (talentConfirme != null) {
            b.talent(talentConfirme);
        }

        if (objetRetire) {
            b.objet(null);
        }

        // Plafond offensif mesuré sur les coups reçus (set moins offensif que
        // l'estimation) : appliqué comme correction de stat ("~" dans le HUD).
        for (Stat st : new Stat[]{Stat.ATTAQUE, Stat.ATTAQUE_SPE}) {
            double plafond = plafondOffensif(espece, st);
            if (Math.abs(plafond - 1.0) > 0.03) b.multiplicateurStat(st, plafond);
        }
        // Résistance mesurée sur mes coups (PV x défense) : appliquée à sa défense
        for (Stat st : new Stat[]{Stat.DEFENSE, Stat.DEFENSE_SPE}) {
            double f = facteurDefensif(espece, st);
            if (f != 1.0) b.multiplicateurStat(st, f);
        }

        // Correction par observation : DÉSACTIVÉE temporairement — mesure
        // de bord peu fiable, écrasait des dégâts corrects (retour utilisateur).
        // Les facteurs continuent d'être calculés (voir majFacteur) mais ne
        // sont plus appliqués, pour pouvoir diagnostiquer sur des cas concrets
        // sans que le calcul affiché soit lui-même faussé.
        boolean CORRECTION_ACTIVE = false;
        Map<Stat, Double> facteurs = CORRECTION_ACTIVE ? FACTEURS.get(espece) : null;
        if (facteurs != null && !facteurs.isEmpty()) {
            for (Map.Entry<Stat, Double> e : facteurs.entrySet()) {
                double f = e.getValue();
                if (Math.abs(f - 1.0) < 0.12) continue;   // écart dans le bruit : ignorer
                Stat st = e.getKey();
                // Dégâts subis plus forts que prévu => son Attaque est plus haute (x f)
                // Dégâts infligés plus faibles que prévu => sa Défense est plus haute (/ f)
                boolean offensive = (st == Stat.ATTAQUE || st == Stat.ATTAQUE_SPE);
                double mult = offensive ? f : 1.0 / f;
                b.multiplicateurStat(st, mult);
            }
        }

        Pokemon p = b.build();

        double fractionPv = adversaireBase.getPvMax() > 0
            ? (double) adversaireBase.getPvActuels() / adversaireBase.getPvMax() : 1.0;
        p.setPvActuels((int) Math.round(fractionPv * p.getPvMax()));
        p.setStatut(adversaireBase.getStatut());
        p.setCoupsRageFistSubis(getCoupsRageFistAdversaire(espece));
        p.setDepartLentActif(departLentActif(true, espece));
        // Délestage : Vitesse x2 s'il a perdu son objet DEPUIS SON ENTRÉE
        // (l'effet s'arrête quand il sort, même si l'objet reste perdu)
        p.setObjetPerdu(objetRetire && delestageActifAdversaire(espece));

        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                p.setStage(s, adversaireBase.getStage(s));
            }
        }

        p.setCampAdverse(true);
        return p;
    }

    private static void appliquerHypothese(Pokemon.Builder b, Stat stat, StatHypothesis hyp) {
        b.ev(stat, hyp.evMax);
        if (hyp.peutEtreBoostee) {
            Nature n = trouverNatureBoostant(stat);
            if (n != null) b.nature(n);
        }
    }

    private static Nature trouverNatureBoostant(Stat stat) {
        for (Nature n : Nature.values()) {
            if (n.getStatAugmentee() == stat) return n;
        }
        return null;
    }

    /**
     * Confirme Mouchoir Choix si la vitesse minimale garantie par l'ordre
     * d'action observé DÉPASSE ce que ce Pokémon pourrait atteindre au
     * MAXIMUM sans elle (252 EV, nature boostante, + le meilleur talent
     * de vitesse plausible pour cette espèce sous la météo actuelle) -
     * dans ce cas, l'écharpe est la seule explication restante, pas
     * juste la plus probable.
     */

    /**
     * Confirme Évoluroc si Smogon montre une dominance TRÈS forte (≥80%)
     * pour cet objet sur cette espèce - PAS une preuve comportementale
     * comme les autres détections de ce fichier, mais une quasi-certitude
     * statistique (ex: Porygon2 joue Évoluroc depuis plus d'une décennie
     * à quasiment 100% des cas). Un tel niveau de dominance n'existe en
     * pratique que pour les objets quasi-obligatoires sur une espèce
     * précise - le risque de faux positif est donc faible, mais reste
     * d'une nature différente d'une observation directe.
     */
    /**
     * Confirme Évoluroc si Smogon le montre comme objet n°1 pour cette
     * espèce, avec un seuil de dominance plus permissif (50%) que pour un
     * objet générique - Évoluroc n'a STRICTEMENT AUCUN EFFET sur un
     * Pokémon totalement évolué, donc le simple fait qu'il soit l'objet
     * le plus joué sur une espèce est déjà auto-validant : aucun joueur
     * sensé ne le porterait s'il n'apportait rien. Couvre les murs NFE
     * moins extrêmes que Porygon2 (Cerfrousse, Téraclope...) où l'usage
     * peut être dominant sans dépasser 80%.
     */
    /**
     * Confirme Vulné-Assurance si l'adversaire vient de gagner EXACTEMENT +2
     * Attaque ET +2 Attaque Spé simultanément le même tour, juste après avoir
     * subi un coup super efficace du joueur. Ce double-boost précis n'a qu'une
     * seule autre source connue en jeu (Croissance sous soleil), explicitement
     * exclue en vérifiant que l'adversaire n'a pas lui-même joué cette capacité
     * ce tour. Ne couvre pas le cas Contrary (-2/-2 au lieu de +2/+2), plus rare.
     */
    /**
     * Baie de résistance (Occa, Passho, etc.) : divise par 2 les dégâts d'un
     * coup super efficace, consommée immédiatement après. Détectée si les
     * dégâts réellement subis tombent nettement en dessous même du minimum
     * attendu (~35-60% de la fourchette normale, cohérent avec une division
     * par 2 plutôt qu'un simple mauvais roll de dégâts 85-100%).
     * Chilan (Normal) fait exception : s'applique même sans super efficacité.
     */
    private static void tenterConfirmerBaieResistance(Pokemon adversaire, Pokemon joueur,
            com.tropimon.tropicalc.calc.Move capacite, double perte, Field terrain) {
        if (OBJETS_CONFIRMES.containsKey(adversaire.getEspece())
                || OBJETS_RETIRES.contains(adversaire.getEspece())) return;

        boolean estChilan = capacite.getType() == PokemonType.NORMAL;
        double efficacite = DamageCalculator.calculerEfficaciteType(capacite, adversaire, joueur);
        if (efficacite <= 1.0 && !estChilan) return;

        DamageCalculator.Resultat r = DamageCalculator.calculer(joueur, adversaire, capacite,
            terrain, terrain.getEcransAdversaire(), false);
        if (r.koGaranti || r.pourcentageMin <= 0) return;   // Exclure Fermeté/Ceinture Focus

        if (perte >= r.pourcentageMin * 0.35 && perte <= r.pourcentageMax * 0.6) {
            // Supposition seulement (dégâts plus faibles que prévu) : plus de
            // "objet retiré" sur cette base. Une baie mangée est annoncée par
            // le jeu (enditem), traitée dans traiterMessageObjet.
            String baie = BAIES_RESISTANCE.get(capacite.getType());
            if (baie != null) MessageDebugLogger.analyse("OBJET " + adversaire.getEspece()
                + " : dégâts faibles, " + baie + " possible (non confirmé)");
        }
    }

    private static void tenterConfirmerVulneAssurance(Pokemon adversaire, Pokemon joueur) {
        if (OBJETS_CONFIRMES.containsKey(adversaire.getEspece())
                || OBJETS_RETIRES.contains(adversaire.getEspece())) return;
        if (coupJoueurDuTour == null || joueurNAPasAttaque()) return;
        if (coupAdversaireDuTour != null && "growth".equals(coupAdversaireDuTour.showdownId())) return;

        int deltaAtk = BoostTracker.getStageAdversaire(Stat.ATTAQUE) - stageAtkAdvDebutTour;
        int deltaAtkSpe = BoostTracker.getStageAdversaire(Stat.ATTAQUE_SPE) - stageAtkSpeAdvDebutTour;
        if (deltaAtk != 2 || deltaAtkSpe != 2) return;

        try {
            MoveTemplate template = Moves.INSTANCE.getByName(coupJoueurDuTour.showdownId());
            if (template == null) return;
            com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
            if (capacite == null) return;
            double efficacite = DamageCalculator.calculerEfficaciteType(capacite, adversaire, joueur);
            if (efficacite > 1.0) {
                OBJETS_CONFIRMES.put(adversaire.getEspece(), "Vulné-Assurance");
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Confirme Défiant (+2 Attaque) ou Battant (+2 Attaque Spé) si l'adversaire
     * subit une baisse d'au moins une stat ce tour (par l'attaquant, pas
     * auto-infligée) et que son Attaque ou Attaque Spé monte de +2 en réaction,
     * sans qu'il ait lui-même joué de capacité offensive ce tour (ce qui
     * exclurait une explication auto-infligée).
     */
    /**
     * Herbe Blanche : restaure TOUTES les stats du porteur ayant subi une
     * baisse à la fin du tour, quelle qu'en soit la cause (capacité auto-
     * baissante comme Surchauffe/Draco-Météore, ou même un talent adverse
     * comme Intimidation - confirmé sur Poképédia) - usage unique, consommé
     * dès qu'au moins une restauration a eu lieu.
     */
    private static void tenterAppliquerHerbeBlanche(Pokemon joueur, Pokemon adversaire) {
        appliquerHerbeBlancheUnCote(joueur, false);
        // adversaire est l'objet BRUT (BattleStateTracker) : il ne connaît
        // pas l'objet estimé/confirmé, seulement ce qui a été réellement
        // révélé en jeu - même bug que celui trouvé sur le Ballon, corrigé
        // ici en reconstruisant l'estimation avant de vérifier l'objet.
        appliquerHerbeBlancheUnCote(construireAdversaireEstime(adversaire), true);
    }

    private static void appliquerHerbeBlancheUnCote(Pokemon porteur, boolean estAdversaire) {
        if (porteur == null || !"Herbe Blanche".equals(porteur.getObjet())) return;
        boolean auMoinsUneBaisse = false;
        for (Stat s : Stat.values()) {
            if (s == Stat.PV) continue;
            int stage = estAdversaire ? BoostTracker.getStageAdversaire(s) : BoostTracker.getStageJoueur(s);
            if (stage < 0) {
                if (estAdversaire) BoostTracker.forcerStageAdversaire(s, 0);
                else BoostTracker.forcerStageJoueur(s, 0);
                auMoinsUneBaisse = true;
            }
        }
        // Consommé - ne s'applique qu'à l'estimation adverse (l'objet du
        // joueur n'a pas besoin d'être "retiré", il est déjà vu directement).
        if (auMoinsUneBaisse && estAdversaire) {
            OBJETS_RETIRES.add(porteur.getEspece());
        }
    }

    private static void tenterConfirmerDefiantBattant(Pokemon adversaire) {
        boolean talentDejaConnu = TALENTS_CONFIRMES.containsKey(adversaire.getEspece());
        if (talentDejaConnu) return;

        boolean uneAutreStatABaisse =
            (BoostTracker.getStageAdversaire(Stat.DEFENSE) - stageDefAdvDebutTour < 0)
            || (BoostTracker.getStageAdversaire(Stat.DEFENSE_SPE) - stageDefSpeAdvDebutTour < 0)
            || (BoostTracker.getStageAdversaire(Stat.VITESSE) - stageVitAdvDebutTour < 0);
        if (!uneAutreStatABaisse) return;

        int deltaAtk = BoostTracker.getStageAdversaire(Stat.ATTAQUE) - stageAtkAdvDebutTour;
        int deltaAtkSpe = BoostTracker.getStageAdversaire(Stat.ATTAQUE_SPE) - stageAtkSpeAdvDebutTour;

        if (deltaAtk == 2) {
            TALENTS_CONFIRMES.put(adversaire.getEspece(), "Défiant");
        } else if (deltaAtkSpe == 2) {
            TALENTS_CONFIRMES.put(adversaire.getEspece(), "Battant");
        }
    }

    /**
     * Confirme proactivement un objet Choix (Mouchoir/Bandeau/Lunettes) si
     * c'est le top objet Smogon avec au moins 80% d'usage - seuil élevé
     * volontairement (contrairement à Évoluroc à 50%) car ces objets
     * s'appliquent à N'IMPORTE QUEL Pokémon sans condition d'éligibilité,
     * donc un faux positif aurait un vrai impact sur le calcul. Marquée
     * comme révocable (OBJETS_CONFIRMES_PROACTIVEMENT) : si une observation
     * contredit ensuite cette hypothèse (changement de capacité sans switch,
     * ou dégâts/vitesse incompatibles), la confirmation est retirée et le
     * calcul repasse sans l'objet.
     */
    // Espèces pour lesquelles un objet Choix proactif a été révoqué (preuve
    // contraire observée) : empêche de re-confirmer le même objet Choix au
    // prochain appel, SANS bloquer d'autres hypothèses d'objet comme le
    // ferait OBJETS_RETIRES (qui signifie "aucun objet du tout").
    private static final Set<String> OBJETS_CHOIX_EXCLUS = new HashSet<>();

    private static void tenterConfirmerObjetChoixProactivement(String espece, SmogonDataLoader.SmogonPokemonData smogon) {
        if (smogon == null || smogon.topItemsShowdownId().isEmpty()) return;
        if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)) return;
        if (OBJETS_CHOIX_EXCLUS.contains(espece)) return;
        String topObjet = ShowdownIdMapper.objet(smogon.topItemsShowdownId().get(0));
        boolean estObjetChoix = "Mouchoir Choix".equals(topObjet)
            || "Bandeau Choix".equals(topObjet) || "Lunettes Choix".equals(topObjet);
        if (estObjetChoix && smogon.topItemUsageFraction() >= 0.80) {
            OBJETS_CONFIRMES.put(espece, topObjet);
            OBJETS_CONFIRMES_PROACTIVEMENT.add(espece);
        }
    }


    /**
     * Évoluroc supposé pour une sous-évolution (le Pokémon peut encore
     * évoluer) : en compétitif, c'est presque toujours son objet. Écarté
     * seulement si Smogon montre clairement autre chose (Évoluroc absent du
     * top et sous 30 %). Utilisé dans les calculs, affiché comme supposé ;
     * un objet prouvé (message du jeu, Sabotage...) le remplace.
     */
    private static void tenterConfirmerEvoluroc(String espece, SmogonDataLoader.SmogonPokemonData smogon) {
        if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)) return;
        if (OBJETS_PROBABLES.containsKey(espece)) return;
        if (!peutEvoluer(espece)) return;
        boolean enTete = false;
        if (smogon != null && !smogon.topItemsShowdownId().isEmpty()) {
            enTete = "Évoluroc".equals(ShowdownIdMapper.objet(smogon.topItemsShowdownId().get(0)));
        }
        double part = SmogonDataLoader.fractionObjet(espece, "eviolite");
        if (smogon != null && !enTete && part < 0.30) return;
        OBJETS_PROBABLES.put(espece, "Évoluroc");
        RAISONS_PROBABLES.put(espece, "sous-évolution" + (part >= 0 ? ", " + Math.round(part * 100) + " % Smogon" : ""));
        MessageDebugLogger.analyse("OBJET " + espece + " : Évoluroc supposé (" + RAISONS_PROBABLES.get(espece) + ")");
    }

    /** Vrai si cette espèce a encore une évolution (Évoluroc possible). */
    private static boolean peutEvoluer(String espece) {
        try {
            Species sp = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(espece);
            return sp != null && !sp.getEvolutions().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * L'objet que les dégâts imposent, ou null.
     *
     * Tant que "aucun objet" reste possible, les dégâts ne prouvent rien : un
     * objet sans effet sur cette stat (Grosses Bottes, Restes...) donne
     * exactement les mêmes dégâts que pas d'objet, et survivait donc seul à
     * l'élimination. C'est ce qui "confirmait" des Grosses Bottes sur un
     * Pyrobut alors que les dégâts disaient seulement "pas d'objet offensif".
     */
    private static String extraireObjetUnique(StatHypothesis hyp) {
        if (hyp.objetsPossibles.contains(StatHypothesis.AUCUN)) return null;
        Set<String> s = new HashSet<>(hyp.objetsPossibles);
        s.remove(StatHypothesis.AUCUN);
        return s.size() == 1 ? s.iterator().next() : null;
    }

    private static String extraireTalentUnique(StatHypothesis hyp) {
        Set<String> s = new HashSet<>(hyp.talentsPossibles);
        s.remove(StatHypothesis.AUCUN);
        return s.size() == 1 ? s.iterator().next() : null;
    }

    /**
     * Ajoute une capacité connue pour cette espèce, en garantissant de
     * NE JAMAIS dépasser 4 (un vrai Pokémon n'en connaît jamais plus).
     * Une observation RÉELLE de ce combat (certaine) fait toujours de la
     * place en retirant la plus ancienne entrée si nécessaire ; une
     * entrée de scouting ancien (potentiellement obsolète, le set adverse
     * a pu changer entre deux combats) n'est jamais ajoutée si ça
     * dépasserait 4.
     */
    private static void ajouterCapaciteAdversaire(String espece, String capaciteId, boolean estObservationReelle) {
        if (COUPS_ADVERSAIRE_COMPLETS.contains(espece)) return;   // jeu complet déjà connu
        LinkedHashSet<String> ensemble = COUPS_ADVERSAIRE.computeIfAbsent(espece, k -> new LinkedHashSet<>());
        if (ensemble.contains(capaciteId)) return;
        if (ensemble.size() >= 4) {
            if (!estObservationReelle) return;
            ensemble.remove(ensemble.iterator().next());
        }
        ensemble.add(capaciteId);
    }

    /**
     * Morphing copie les 4 capacités de la cible : son jeu complet, connu pour
     * de bon. Gardé quand Métamorph sort (avant : oublié au changement de
     * Pokémon, on revenait aux capacités Smogon supposées).
     */
    private static void retenirCapacitesCopiees(List<String> ids) {
        try {
            Pokemon cible = cibleMorphingJoueur;
            if (cible == null || ids == null || ids.isEmpty()) return;
            String espece = cible.getEspece();
            if (espece == null || estTransforme(true, espece)) return;   // la cible était elle-même transformée
            LinkedHashSet<String> ensemble = new LinkedHashSet<>();
            for (String id : ids) {
                if (id != null && !id.isEmpty() && !"struggle".equals(id)) ensemble.add(id);
            }
            if (ensemble.isEmpty()) return;
            COUPS_ADVERSAIRE.put(espece, ensemble);
            COUPS_ADVERSAIRE_COMPLETS.add(espece);
            MessageDebugLogger.analyse("MORPHING capacités de " + espece + " retenues (jeu complet) : " + ensemble);
        } catch (Exception ignored) {
        }
    }

    /** Vrai si toutes les capacités de cette espèce sont connues (copiées par Morphing). */
    public static boolean coupsAdversaireComplets(String espece) {
        return espece != null && COUPS_ADVERSAIRE_COMPLETS.contains(espece);
    }

    public static List<MoveTemplate> getCoupsAdversaireReveles(String espece) {
        LinkedHashSet<String> ids = COUPS_ADVERSAIRE.get(espece);
        if (ids == null) return List.of();
        List<MoveTemplate> r = new ArrayList<>();
        for (String id : ids) {
            MoveTemplate t = Moves.INSTANCE.getByName(id);
            if (t != null) r.add(t);
        }
        return r;
    }

    // Plancher de PV adverses depuis le dernier point bas (détection de soin intra-tour)
    private static String especePlancherAdv = null;
    private static double pvPlancherAdv = -1;

    // Vrai une fois l'état remis à zéro hors combat : tick() tourne à chaque
    // image, et la remise à zéro (avec sa ligne "NOUVEAU COMBAT") était
    // refaite des dizaines de fois par seconde entre deux combats - 25 000
    // lignes en 25 minutes, le journal débordait et perdait les combats.
    private static boolean etatVide = false;

    // Combat suivi : un nouvel objet combat = un nouveau match. En random, la
    // file enchaîne parfois deux combats sans repasser « hors combat » entre
    // les deux, et l'état du match précédent (Pokémon vus, attaques, objets)
    // restait affiché.
    private static Object combatSuivi = null;

    /** Remet tout à zéro si le combat en cours n'est plus celui qu'on suivait. */
    public static void verifierChangementDeCombat() {
        Object combat;
        try {
            combat = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattle();
        } catch (Throwable t) {
            return;
        }
        if (combat == null || combat == combatSuivi) return;
        if (combatSuivi != null) {
            reinitialiser();
            etatVide = false;
        }
        combatSuivi = combat;
    }

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) {
            combatSuivi = null;
            if (!etatVide) {
                reinitialiser();
                etatVide = true;
            }
            return;
        }
        verifierChangementDeCombat();
        BattleStateTracker.detecterFormat();
        etatVide = false;
        // Sets Smogon de double (VGC / Doubles OU) pendant un combat double
        try {
            com.tropimon.tropicalc.calc.SmogonDataLoader.setModeDouble(BattleStateTracker.estCombatDouble());
        } catch (Exception ignored) {
        }
        // Combat double : pas d'analyse (elle suppose un seul adversaire)
        if (BattleStateTracker.estCombatDouble()) {
            try {
                BoostsDouble.tick();
            } catch (Exception ignored) {
            }
            try {
                PvEquipe.tick();
            } catch (Throwable ignored) {
            }
            try {
                InferenceDouble.tick();
            } catch (Exception ignored) {
            }
            return;
        }
        if (finDeTourEnAttenteMs != 0 && System.currentTimeMillis() - finDeTourEnAttenteMs >= DELAI_PV_MS) {
            viderFinDeTourEnAttente();
        }
        try {
            journaliserPv();
        } catch (Exception ignored) {
        }
        try {
            suivrePvStables();
        } catch (Exception ignored) {
        }
        try {
            suivrePvMetamorph();
        } catch (Exception ignored) {
        }
        try {
            PvEquipe.tick();
        } catch (Throwable ignored) {
        }

        // Capacités copiées par mon Métamorph : lues dans le menu d'attaque
        // dès qu'il est disponible, gardées tant qu'il reste transformé.
        try {
            // cibleMorphingJoueur : effacée au retrait, alors que le combat
            // montre encore le Métamorph transformé quelques instants (les
            // capacités du Pokémon suivant étaient prises pour des copies).
            if (cibleMorphingJoueur != null) {
                List<String> ids = BattleStateTracker.getCapacitesRequeteJoueur();
                if (ids != null && !ids.isEmpty() && !ids.contains("transform") && !ids.equals(capacitesMorphing)) {
                    capacitesMorphing = new ArrayList<>(ids);
                    MessageDebugLogger.analyse("MORPHING capacités copiées : " + ids);
                    retenirCapacitesCopiees(ids);
                }
            }
        } catch (Exception ignored) {
        }

        // Détection Restes : les PV adverses remontent de ~1/16 depuis leur point bas.
        // Contrairement au delta net par tour, ceci voit le soin même si le joueur
        // a infligé des dégâts le même tour.
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        if (adv == null) return;
        ENTREE_ADVERSAIRE_MS.putIfAbsent(adv.getEspece(), System.currentTimeMillis());
        double pvNow = adv.getPourcentagePv();

        if (!adv.getEspece().equals(especePlancherAdv)) {
            especePlancherAdv = adv.getEspece();
            pvPlancherAdv = pvNow;
            return;
        }
        if (pvNow < pvPlancherAdv) {
            pvPlancherAdv = pvNow;
            return;
        }

        ADVERSAIRES_VUS.put(adv.getEspece(),
            new double[]{adv.getPourcentagePv(), adv.getStatut().ordinal()});

        double remontee = pvNow - pvPlancherAdv;
        // Restes, Boue Noire, baies : le jeu annonce ces soins (heal.leftovers,
        // heal.blacksludge, enditem...), lus dans MoveUseTracker. Plus aucune
        // déduction par la remontée des PV : un soin écarté (Vampipoing) laissait
        // l'ancien point bas en place, et au tour suivant la même remontée était
        // prise pour des Restes (Colossinge du 11/10).
        if (remontee >= 10.5 && remontee <= 14.0
                && (adv.getStatut() == Pokemon.Statut.POISON
                    || adv.getStatut() == Pokemon.Statut.POISON_GRAVE)
                && !joueurVampigraine
                && (coupAdversaireDuTour == null
                    || !COUPS_SOIN_OU_DRAIN.contains(coupAdversaireDuTour.showdownId()))
                && !"wish".equals(coupAdversaireTourPrecedent)
                && FieldTracker.construireField().getTerrain() != Field.TypeTerrain.HERBU) {
            // Un Pokémon EMPOISONNÉ qui gagne ~1/8 par tour : signature de Soin Poison
            // (le poison aurait dû lui retirer des PV, il en gagne 12.5%)
            TALENTS_CONFIRMES.put(adv.getEspece(), "Soin Poison");
            pvPlancherAdv = pvNow;
        } else if (remontee >= 22.0 && remontee <= 27.0
                && pvPlancherAdv <= 50.5
                && !OBJETS_RETIRES.contains(adv.getEspece())
                && (coupAdversaireDuTour == null
                    || !COUPS_SOIN_OU_DRAIN.contains(coupAdversaireDuTour.showdownId()))
                && !"wish".equals(coupAdversaireTourPrecedent)) {
            // Baie Sitrus probable : sa consommation est annoncée par le jeu
            // (enditem), qui marque l'objet perdu. Rien de déduit ici.
            pvPlancherAdv = pvNow;
        } else if (remontee > 1.0) {
            // Tout autre soin (Vœu, Soin, drain, Restes annoncés...) : repartir
            // de ce niveau, pour ne pas le recompter plus tard
            pvPlancherAdv = pvNow;
        }
    }

    public static Boolean determinerAttaquant(String proprietaire) {
        if (proprietaire == null) return null;
        var joueurMc = MinecraftClient.getInstance().player;
        if (joueurMc == null) return null;
        return !proprietaire.equalsIgnoreCase(joueurMc.getGameProfile().getName());
    }

    public static ProfilAdversaire getProfil(String espece) { return PROFILS.get(espece); }
    public static String getEspaceAdversaireCourant() { return espaceAdversaireDuTour; }

    /** PP consommés par cette espèce adverse sur cette capacité (0 si jamais vue). */
    public static int getPpUtilises(String espece, String moveId) {
        Map<String, Integer> m = PP_UTILISES.get(espece);
        return m == null ? 0 : m.getOrDefault(moveId, 0);
    }

    /**
     * Vrai si la capacité cible un Pokémon adverse (condition d'application de Pression).
     * Faux pour les cibles soi-même, alliés, côté de terrain, ou terrain entier.
     */
    private static boolean cibleLAdversaire(String moveId) {
        MoveTemplate t = Moves.INSTANCE.getByName(moveId);
        if (t == null) return true; // inconnue : on suppose offensive
        String cible = String.valueOf(t.getTarget()).toLowerCase();
        if (cible.equals("all")) return false;          // météo, Champ Psychique...
        if (cible.contains("self")) return false;       // Abri, Soin, Danse Lames...
        if (cible.contains("ally") || cible.contains("allies")) return false;
        if (cible.contains("side")) return false;       // Piège de Roc, Picots, écrans...
        return true;
    }

    /** Objet confirmé par observation pour cette espèce, ou null. */
    public static String getObjetConfirme(String espece) {
        return OBJETS_CONFIRMES.get(espece);
    }

    // Facteurs de correction observés par espèce et par stat défensive/offensive :
    // ratio dégâts réels / dégâts prévus. > 1 = la cible encaisse moins que prévu.
    private static final Map<String, Map<Stat, Double>> FACTEURS = new HashMap<>();

    /** Vrai si un facteur mesuré significatif est actif pour cette stat. */
    private static boolean facteurActif(String espece, Stat stat) {
        return Math.abs(getFacteur(espece, stat) - 1.0) >= 0.12;
    }

    /** Facteur de correction observé pour cette espèce et cette stat (1.0 = aucun). */
    public static double getFacteur(String espece, Stat stat) {
        Map<Stat, Double> m = FACTEURS.get(espece);
        if (m == null) return 1.0;
        Double f = m.get(stat);
        return f == null ? 1.0 : f;
    }

    /**
     * Corrige le facteur d'une stat à partir d'un écart prévu/réel.
     * Moyenne glissante pondérée : un écart isolé ne bascule pas tout,
     * mais deux observations concordantes convergent vite.
     */
    private static void majFacteur(String espece, Stat stat, double ratio) {
        if (!(ratio > 0.05) || !(ratio < 20)) return;   // aberrant : ignorer
        Map<Stat, Double> m = FACTEURS.computeIfAbsent(espece, k -> new HashMap<>());
        Double actuel = m.get(stat);
        m.put(stat, actuel == null ? ratio : actuel * 0.4 + ratio * 0.6);
    }

    /**
     * Confirmation directe d'un objet par espèce, sans passer par un nom de
     * propriétaire - utilisée quand l'objet est déduit d'un comportement
     * observé (ex: un écran qui dure plus longtemps que possible sans
     * Lumargile) plutôt que d'un message explicite du jeu.
     */
    public static void confirmerObjetDirect(String espece, String objetFr) {
        confirmerObjet(espece, objetFr, "Lumargile".equals(objetFr) ? "écran prolongé au-delà de 5 tours"
            : "Champ'Duit".equals(objetFr) ? "champ prolongé au-delà de 5 tours" : null);
    }

    // Adversaires dont le jeu a annoncé le Ballon à l'entrée
    private static final Set<String> BALLON_ANNONCE = new HashSet<>();
    // Moment (ms) où chaque espèce adverse a été vue active pour la première fois
    private static final Map<String, Long> ENTREE_ADVERSAIRE_MS = new HashMap<>();
    // Délai laissé au jeu pour annoncer le Ballon (les messages défilent
    // environ toutes les 1,5 s)
    private static final long DELAI_ANNONCE_BALLON_MS = 6000;

    private static boolean mentionneBallon(String cle, Object[] args) {
        if (cle.contains("airballoon") || cle.contains("air_balloon")) return true;
        if (!cle.startsWith("cobblemon.battle.item")) return false;
        for (Object a : args) {
            String t = String.valueOf(a).toLowerCase().replaceAll("[^a-z]", "");
            if (t.contains("airballoon")) return true;
        }
        return false;
    }

    /**
     * Vrai si cette espèce adverse est entrée depuis assez longtemps sans que
     * le jeu annonce de Ballon : elle n'en a pas.
     */
    private static boolean ballonExclu(String espece) {
        if (BALLON_ANNONCE.contains(espece)) return false;
        Long entree = ENTREE_ADVERSAIRE_MS.get(espece);
        return entree != null && System.currentTimeMillis() - entree > DELAI_ANNONCE_BALLON_MS;
    }

    /** Objet le plus joué sur Smogon, hors Ballon quand le jeu ne l'a pas annoncé. */
    // Pokémon qui peut encore évoluer : Évoluroc quasi certain (x1,5 Déf et
    // Déf. Spé.). Liste tirée du Pokédex de Showdown (champ « evos »), livrée
    // avec le mod : les données d'évolution de Cobblemon ne sont pas
    // forcément envoyées au client (Colossinge sans Évoluroc le 19/10).
    private static volatile Set<String> PRE_EVOLUTIONS = null;

    static boolean estPreEvolution(String espece) {
        if (espece == null) return false;
        Set<String> liste = PRE_EVOLUTIONS;
        if (liste == null) {
            Set<String> lu = new HashSet<>();
            try (java.io.InputStream in = ObservationCollector.class.getResourceAsStream("/assets/tropicalc/preevolutions.txt")) {
                if (in != null) {
                    for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                        if (!l.isBlank()) lu.add(l.trim());
                    }
                }
            } catch (Exception ignored) {
            }
            PRE_EVOLUTIONS = liste = lu;
        }
        String id = espece.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (liste.contains(id)) return true;
        try {
            Species sp = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(id);
            return sp != null && !sp.getEvolutions().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Évoluroc supposé parce qu'il peut évoluer : affiché comme objet supposé. */
    private static String evolurocSuppose(String espece) {
        if (!estPreEvolution(espece) || objetExclu(espece, "Évoluroc")) return null;
        RAISONS_PROBABLES.put(espece, "peut encore évoluer");
        if (!"Évoluroc".equals(OBJETS_PROBABLES.put(espece, "Évoluroc"))) {
            MessageDebugLogger.analyse("OBJET " + espece + " : Évoluroc supposé (peut encore évoluer)");
        }
        return "Évoluroc";
    }

    private static String objetSmogonProbable(String espece, SmogonDataLoader.SmogonPokemonData smogon) {
        // Pokémon qui peut encore évoluer : Évoluroc, sauf s'il est exclu ou
        // si Smogon donne autre chose de courant pour lui
        String evoluroc = evolurocSuppose(espece);
        if (evoluroc != null) return evoluroc;
        if (smogon == null) return null;
        for (String id : smogon.topItemsShowdownId()) {
            String fr = ShowdownIdMapper.objet(id);
            if (fr == null) continue;
            if ("Ballon".equals(fr) && ballonExclu(espece)) continue;
            if (fr.endsWith(" Choix") && OBJETS_CHOIX_EXCLUS.contains(espece)) continue;
            if ("Orbe Vie".equals(fr) && ORBE_VIE_EXCLUE.contains(espece)) continue;
            if (objetExclu(espece, fr)) continue;
            return fr;
        }
        return null;
    }

    /** Objet tenu au début du combat, connu par le message de sa perte (Sabotage, baie...). */
    private static final Map<String, String> OBJETS_PERDUS_CONNUS = new HashMap<>();

    /** L'adversaire a perdu son objet (annoncé par le jeu). */
    // --- Délestage (Unburden) ---
    // Vitesse x2 dès que le porteur perd son objet sur le terrain (baie,
    // Ceinture Focus, Herbe Blanche, Sabotage, vol...). L'effet s'arrête
    // quand il sort : à son retour, objet toujours perdu mais plus de x2.
    // Il s'arrête aussi s'il reçoit un objet. Espèce concernée par camp,
    // null si aucun Délestage en cours.
    private static String delestageAdversaire = null;
    private static String delestageJoueur = null;

    public static boolean delestageActifAdversaire(String espece) {
        return espece != null && espece.equals(delestageAdversaire);
    }

    public static boolean delestageActifJoueur(String espece) {
        return espece != null && espece.equals(delestageJoueur);
    }

    /** Entrée / sortie d'un Pokémon : fin du Délestage de ce camp. */
    private static void suivreDelestage(String cle, Object[] args) {
        if (cle.startsWith("cobblemon.battle.switch.self") || cle.startsWith("cobblemon.battle.withdraw.self")) {
            delestageJoueur = null;
        } else if (cle.startsWith("cobblemon.battle.switch.other") || cle.startsWith("cobblemon.battle.withdraw.other")) {
            delestageAdversaire = null;
        } else if (cle.startsWith("cobblemon.battle.dragged_out") && args.length > 0) {
            Boolean adv = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            if (Boolean.TRUE.equals(adv)) delestageAdversaire = null;
            else if (Boolean.FALSE.equals(adv)) delestageJoueur = null;
        }
    }

    /** Mon Pokémon actif peut-il refaire pousser sa baie (Récolte, Ruminant) ? */
    private static boolean joueurPeutRecupererBaie() {
        try {
            com.cobblemon.mod.common.pokemon.Pokemon complet = BattleStateTracker.getPokemonCompletJoueur();
            if (complet == null) return false;
            String t = complet.getAbility().getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
            return "harvest".equals(t) || "cudchew".equals(t);
        } catch (Exception e) {
            return false;
        }
    }

    // --- Départ Lent : "moi:espèce" / "adv:espèce" tant que l'effet dure ---
    // Le jeu annonce le début (start.slowstart, à l'entrée) et la fin
    // (end.slowstart, 5 tours plus tard). Sans ça, le Body Slam d'un
    // Regigigas était prévu au double (logs random du 22/10).
    private static final Set<String> DEPART_LENT = new HashSet<>();

    private static void suivreDepartLent(String cle, Object[] args) {
        if (cle.startsWith("cobblemon.battle.switch.self") || cle.startsWith("cobblemon.battle.withdraw.self")) {
            DEPART_LENT.removeIf(k -> k.startsWith("moi:"));
        } else if (cle.startsWith("cobblemon.battle.switch.other") || cle.startsWith("cobblemon.battle.withdraw.other")) {
            DEPART_LENT.removeIf(k -> k.startsWith("adv:"));
        } else if ((cle.equals("cobblemon.battle.start.slowstart") || cle.equals("cobblemon.battle.end.slowstart"))
                && args.length > 0) {
            Boolean adverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            if (adverse == null) return;
            // Espèce nommée par le message (le jeu n'a pas encore fait entrer le Pokémon)
            String espece = especeOuActif(args[0]);
            if (espece == null) return;
            String k = (adverse ? "adv:" : "moi:") + espece;
            if (cle.endsWith("start.slowstart")) DEPART_LENT.add(k);
            else DEPART_LENT.remove(k);
        }
    }

    // --- Chargeur adverse (Électrogenèse, Énergie Éolienne, Chargeur) ---
    // Annoncé par le jeu (start.charge), consommé par sa prochaine capacité
    // Électrik, perdu quand il sort.
    private static boolean chargeAdversaire = false;

    private static void suivreCharge(String cle, Object[] args) {
        if (cle.startsWith("cobblemon.battle.switch.other") || cle.startsWith("cobblemon.battle.withdraw.other")
                || cle.startsWith("cobblemon.battle.dragged_out")) {
            chargeAdversaire = false;
        } else if (cle.equals("cobblemon.battle.start.charge") && args.length > 0) {
            if (Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) chargeAdversaire = true;
        } else if ((cle.equals("cobblemon.battle.used_move_on") || cle.equals("cobblemon.battle.used_move"))
                && args.length > 1 && chargeAdversaire
                && Boolean.TRUE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])))) {
            try {
                MoveTemplate t = Moves.INSTANCE.getByName(idCapaciteDepuisArgument(args[1]));
                if (t != null && "electric".equalsIgnoreCase(t.getElementalType().getName())) chargeAdversaire = false;
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean departLentActif(boolean adverse, String espece) {
        return espece != null && DEPART_LENT.contains((adverse ? "adv:" : "moi:") + espece);
    }

    private static void marquerObjetAdversairePerdu(String espece, String detail) {
        delestageAdversaire = espece;
        OBJETS_RETIRES.add(espece);
        OBJETS_CONFIRMES.remove(espece);
        OBJETS_CONFIRMES_PROACTIVEMENT.remove(espece);
        RAISONS_OBJET.remove(espece);
        MessageDebugLogger.analyse("OBJET " + espece + " : perdu, annoncé par le jeu (enditem." + detail + ")");
    }

    /**
     * Intercepte les messages "cobblemon.battle.enditem.XXX" - confirmé par
     * un vrai log (tropicalc-messages-debug.txt, combat contre Ratdeglingo,
     * clé exacte "cobblemon.battle.enditem.airballoon") : un message dédié
     * existe pour la destruction du Ballon, bien plus fiable que la détection
     * par comportement (perteAdversaire/perteJoueur > 0) utilisée jusqu'ici,
     * qui reste en place en secours mais ne devrait plus jamais être
     * nécessaire pour ce cas précis.
     */
    public static void traiterMessageObjet(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

        suivreEntrees(cle, contenu.getArgs());
        suivreIllusion(cle, contenu.getArgs());
        suivreCoupRecu(cle, contenu.getArgs());
        suivreVerrouChoix(cle, contenu.getArgs());
        suivreTorche(cle, contenu.getArgs());
        suivreDureeMeteo(cle, contenu.getArgs());
        suivreMorphingEtRates(cle, contenu.getArgs());
        suivreDepartLent(cle, contenu.getArgs());
        suivreCharge(cle, contenu.getArgs());
        suivreDelestage(cle, contenu.getArgs());

        if (cle.contains("quickclaw") || cle.contains("quickdraw") || cle.contains("custap")) {
            prioriteObjetCeTour = true;
            return;
        }
        if (cle.contains("futuresight") || cle.contains("doomdesire")) {
            attaqueDiffereeCeTour = true;
        }
        if (cle.startsWith("cobblemon.battle.damage.")
                && DEGATS_ANNEXES.contains(cle.substring("cobblemon.battle.damage.".length()))) {
            // arg0 = le Pokémon qui perd les PV (vu en log pour lifeorb, recoil,
            // rockyhelmet). Pas de return : damage.lifeorb est traité plus bas.
            Object[] argsDeg = contenu.getArgs();
            if (argsDeg.length > 0 && Boolean.FALSE.equals(
                    determinerAttaquant(MoveUseTracker.extraireProprietaire(argsDeg[0])))) {
                degatsAnnexesJoueurCeTour = true;
            }
        }

        if (cle.equals("cobblemon.battle.crit")) {
            critCeTour = true;
            return;
        }
        if (cle.equals("cobblemon.battle.hit_count")) {
            multiCoupsCeTour = true;
            return;
        }

        if (cle.equals("cobblemon.battle.fainted")) {
            // Un K.O., annoncé par le jeu avec dresseur et espèce (vu 61 fois en
            // log, les deux camps). Seule source fiable pour l'adversaire :
            // Cobblemon ne fournit pas son équipe complète côté client, donc
            // Général Suprême adverse comptait toujours 0 K.O.
            Object[] args = contenu.getArgs();
            if (args.length == 0) return;
            Boolean adverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            if (adverse == null) return;
            Set<String> cible = adverse ? KO_ADVERSAIRE : KO_JOUEUR;
            String espece = especeDepuisArgument(args[0]);
            cible.add(espece != null ? espece : "ko#" + cible.size());
            return;
        }

        if (cle.equals("cobblemon.battle.damage.lifeorb")) {
            // Recul de l'Orbe Vie annoncé par le jeu lui-même (confirmé en log
            // réel, les deux camps, arg0 = owned_pokemon(dresseur, espèce)).
            // C'est la preuve directe : plus rapide et plus sûre que la
            // déduction par perte de PV (qui exigeait que le joueur n'ait pas
            // attaqué le même tour, donc ne se déclenchait presque jamais),
            // et sans le risque de faux positif d'un spread mal estimé.
            // Seul le cas adverse nous intéresse : mon propre objet est lu
            // directement. La confirmation écrase toute estimation ou
            // confirmation proactive précédente (un seul objet par Pokémon).
            Object[] args = contenu.getArgs();
            if (args.length == 0) return;
            String proprietaire = MoveUseTracker.extraireProprietaire(args[0]);
            if (!Boolean.TRUE.equals(determinerAttaquant(proprietaire))) return;
            reculOrbeVieAdvCeTour = true;
            Pokemon adv = BattleStateTracker.getAdversaireActif();
            if (adv == null) return;
            confirmerObjet(adv.getEspece(), "Orbe Vie", "recul d'Orbe Vie annoncé par le jeu");
            OBJETS_CONFIRMES_PROACTIVEMENT.remove(adv.getEspece());
            OBJETS_RETIRES.remove(adv.getEspece());
            return;
        }

        if (cle.equals("cobblemon.battle.immune")) {
            // Confirmation DIRECTE du Ballon, pas une déduction par comportement :
            // le jeu annonce explicitement l'immunité. Si le coup qui vient
            // d'être joué est Sol et que la cible n'a aucune autre explication
            // naturelle (type Vol, Lévitation confirmée), la seule explication
            // restante est le Ballon. Signalé par l'utilisateur : plus fiable
            // que d'attendre la preuve indirecte (dégâts nuls, ou l'explosion
            // ultérieure de l'objet).
            Object[] args = contenu.getArgs();
            if (args.length == 0) return;
            String cible = MoveUseTracker.extraireProprietaire(args[0]);
            Boolean cibleEstAdversaire = determinerAttaquant(cible);
            if (cibleEstAdversaire == null) return;

            MoveUseTracker.CoupDetecte coupEnCause = cibleEstAdversaire ? coupJoueurDuTour : coupAdversaireDuTour;
            if (coupEnCause == null) return;

            try {
                MoveTemplate template = Moves.INSTANCE.getByName(coupEnCause.showdownId());
                if (template == null) return;
                com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
                if (capacite == null || capacite.getType() != PokemonType.SOL) return;

                // Seul le cas adverse nous intéresse : si c'est MOI qui suis
                // immunisé, je connais déjà directement mon propre objet réel.
                if (cibleEstAdversaire) {
                    Pokemon adv = BattleStateTracker.getAdversaireActif();
                    if (adv == null) return;
                    if (adv.getType1() == com.tropimon.tropicalc.calc.PokemonType.VOL
                            || adv.getType2() == com.tropimon.tropicalc.calc.PokemonType.VOL) return;
                    if ("Lévitation".equals(getTalentConfirme(adv.getEspece()))) return;
                    if (!OBJETS_CONFIRMES.containsKey(adv.getEspece())) {
                        confirmerObjet(adv.getEspece(), "Ballon", "immunité Sol annoncée par le jeu");
                    }
                }
            } catch (Exception ignored) {
            }
            return;
        }

        // Perte d'objet annoncée par le jeu (enditem.X : Ballon éclaté, baie
        // mangée, Carte Rouge, Herbe Blanche, Sabotage...). arg0 = celui qui
        // perd l'objet. Pas de return : les cas particuliers suivent.
        if (cle.startsWith("cobblemon.battle.enditem.")) {
            Object[] argsFin = contenu.getArgs();
            // Mon Pokémon perd son objet (Ceinture Focus, Herbe Blanche,
            // Bouton Fuite, Sabotage...) : plus d'objet, Délestage actif
            if (argsFin.length > 0 && Boolean.FALSE.equals(
                    determinerAttaquant(MoveUseTracker.extraireProprietaire(argsFin[0])))) {
                Pokemon moiFin = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (moiFin == null) moiFin = BattleStateTracker.getJoueurActif();
                if (moiFin != null) marquerObjetJoueurDetruit(moiFin.getEspece());
            }
            if (argsFin.length > 0 && Boolean.TRUE.equals(
                    determinerAttaquant(MoveUseTracker.extraireProprietaire(argsFin[0])))) {
                Pokemon adv = BattleStateTracker.getAdversaireActif();
                if (adv != null) {
                    marquerObjetAdversairePerdu(adv.getEspece(),
                        cle.substring("cobblemon.battle.enditem.".length()));
                    // Le jeu nomme l'objet perdu ("item.cobblemon.focus_sash") :
                    // gardé pour les prochains combats contre ce joueur.
                    String texteObjet = argsFin.length > 1 ? cleDepuisArgument(argsFin[1]) : null;
                    // Sans objet en argument, la clé le nomme souvent elle-même
                    // (enditem.ejectbutton, enditem.focussash, enditem.whiteherb...)
                    if (texteObjet == null) texteObjet = cle;
                    if (texteObjet.lastIndexOf('.') >= 0) {
                        String fr = ShowdownIdMapper.objet(
                            texteObjet.substring(texteObjet.lastIndexOf('.') + 1).replace("_", ""));
                        if (fr != null) {
                            OBJETS_PERDUS_CONNUS.put(adv.getEspece(), fr);
                            MessageDebugLogger.analyse("OBJET " + adv.getEspece() + " : c'était " + fr
                                + " (retenu pour les prochains combats)");
                        }
                    }
                }
            }
        } else if (mentionneBallon(cle, contenu.getArgs())) {
            // Le jeu annonce le Ballon à l'entrée du porteur ("X flotte grâce
            // à son Ballon") : preuve directe.
            Object[] argsBallon = contenu.getArgs();
            if (argsBallon.length > 0 && Boolean.TRUE.equals(
                    determinerAttaquant(MoveUseTracker.extraireProprietaire(argsBallon[0])))) {
                Pokemon adv = BattleStateTracker.getAdversaireActif();
                if (adv != null) {
                    BALLON_ANNONCE.add(adv.getEspece());
                    OBJETS_RETIRES.remove(adv.getEspece());
                    confirmerObjet(adv.getEspece(), "Ballon", "Ballon annoncé par le jeu à l'entrée");
                    MessageDebugLogger.analyse("OBJET " + adv.getEspece() + " : Ballon annoncé (" + cle + ")");
                }
            }
        }

        if (cle.equals("cobblemon.battle.enditem.airballoon")) {
            Object[] args = contenu.getArgs();
            if (args.length == 0) return;
            String proprietaire = MoveUseTracker.extraireProprietaire(args[0]);
            Boolean estAdversaire = determinerAttaquant(proprietaire);
            if (estAdversaire == null) return;

            if (estAdversaire) {
                Pokemon adv = BattleStateTracker.getAdversaireActif();
                if (adv != null) OBJETS_RETIRES.add(adv.getEspece());
            } else {
                Pokemon joueurActif = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (joueurActif == null) joueurActif = BattleStateTracker.getJoueurActif();
                if (joueurActif != null) marquerObjetJoueurDetruit(joueurActif.getEspece());
            }
            return;
        }

        if (cle.equals("cobblemon.battle.enditem.knockoff")) {
            // Sabotage : arg0 = victime, arg1 = objet, arg2 = attaquant
            // (ordre inverse de item.thief, confirmé par log plus tôt ce
            // soir). Le cas adversaire-victime est déjà géré ailleurs
            // (perteAdversaire dans signalerNouveauTour) ; ce bloc ne
            // couvre QUE le cas où c'est le joueur qui se fait saboter,
            // jamais intercepté jusqu'ici.
            Object[] args = contenu.getArgs();
            if (args.length == 0) return;
            String victime = MoveUseTracker.extraireProprietaire(args[0]);
            if (Boolean.FALSE.equals(determinerAttaquant(victime))) {
                Pokemon joueurActif = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (joueurActif == null) joueurActif = BattleStateTracker.getJoueurActif();
                if (joueurActif != null) marquerObjetJoueurDetruit(joueurActif.getEspece());
            }
            return;
        }

        if (cle.equals("cobblemon.battle.item.thief")) {
            // Pickpocket (le nom reste "Pickpocket" en français, confirmé) :
            // arg0 = voleur (gagne l'objet), arg1 = objet (format brut
            // "item.cobblemon.rocky_helmet"), arg2 = victime (perd l'objet).
            // Confirmé par un vrai log (tropicalc-messages-debug.txt) :
            // l'ordre victime/voleur est l'inverse de enditem.knockoff, où
            // arg0 est la victime - vérifié avec l'utilisateur pour être sûr.
            Object[] args = contenu.getArgs();
            if (args.length < 3) return;
            String voleur = MoveUseTracker.extraireProprietaire(args[0]);
            String victime = MoveUseTracker.extraireProprietaire(args[2]);
            String texteObjet = cleDepuisArgument(args[1]);   // "item.cobblemon.rocky_helmet"
            if (texteObjet == null) return;
            int pointFinal = texteObjet.lastIndexOf('.');
            if (pointFinal < 0) return;
            String showdownId = texteObjet.substring(pointFinal + 1).replace("_", "");
            String objetFr = ShowdownIdMapper.objet(showdownId);
            // L'icône n'a pas besoin du nom français : on garde l'objet volé même
            // s'il est absent de ShowdownIdMapper.
            ItemStack stackVole = stackDepuisCleBrute(texteObjet);
            if (objetFr == null && stackVole.isEmpty()) return;

            Boolean victimeEstAdversaire = determinerAttaquant(victime);
            Boolean voleurEstAdversaire = determinerAttaquant(voleur);
            Pokemon adv = BattleStateTracker.getAdversaireActif();
            if (adv == null) return;

            if (Boolean.TRUE.equals(victimeEstAdversaire)) {
                OBJETS_RETIRES.add(adv.getEspece());
                delestageAdversaire = adv.getEspece();
            }
            if (Boolean.TRUE.equals(voleurEstAdversaire) && objetFr != null) {
                objetAdversaireRecu(adv.getEspece());
                confirmerObjet(adv.getEspece(), objetFr, "volé avec Pickpocket");
            }

            // Côté joueur, pour le même filet de sécurité que pour le Ballon :
            // si JE suis le voleur, mon nouvel objet est connu directement
            // (certitude totale, pas une estimation) ; si je suis la
            // victime, je n'ai plus d'objet du tout.
            Pokemon joueurActif2 = BattleStateTracker.getJoueurActifDepuisEquipe();
            if (joueurActif2 == null) joueurActif2 = BattleStateTracker.getJoueurActif();
            if (joueurActif2 != null) {
                if (Boolean.FALSE.equals(voleurEstAdversaire)) {
                    marquerObjetJoueurConnu(joueurActif2.getEspece(), objetFr);
                    if (!stackVole.isEmpty()) {
                        OBJET_STACK_JOUEUR.put(cleEspece(joueurActif2.getEspece()), stackVole);
                    }
                }
                if (Boolean.FALSE.equals(victimeEstAdversaire)) {
                    marquerObjetJoueurDetruit(joueurActif2.getEspece());
                }
            }
            return;
        }

        // Toute autre révélation d'objet par le jeu (Fouille, Tour de Magie,
        // Passe-Passe, Passe-Cadeau, annonce d'entrée...) : "item.X", arg0 =
        // le porteur, un argument "item.cobblemon.nom_objet". Le Ballon a déjà
        // son traitement (annonce d'entrée) plus haut.
        if (cle.startsWith("cobblemon.battle.item.") && !cle.contains("airballoon")) {
            Object[] args = contenu.getArgs();
            if (args.length < 2) return;
            String suffixe = cle.substring("cobblemon.battle.item.".length());
            String cleObjet = null;
            for (int k = 1; k < args.length; k++) {
                String c = cleDepuisArgument(args[k]);
                if (c != null && c.startsWith("item.")) {
                    cleObjet = c;
                    break;
                }
            }
            if (cleObjet == null) return;
            String objetFr = ShowdownIdMapper.objet(cleObjet.substring(cleObjet.lastIndexOf('.') + 1).replace("_", ""));
            ItemStack stack = stackDepuisCleBrute(cleObjet);
            // Objet RECU en cours de combat (et non simplement révélé)
            boolean recu = suffixe.contains("trick") || suffixe.contains("switcheroo") || suffixe.contains("bestow");
            Boolean porteurAdverse = determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]));
            // Baie mangée (item.eat) : l'objet est CONSOMMÉ. Elle était notée
            // « révélée », donc encore tenue (Baie Sitrus de Tropius), et
            // Délestage ne s'activait jamais. Récolte / Ruminant peuvent la
            // faire repousser : on garde alors l'ancien comportement.
            if ("eat".equals(suffixe)) {
                if (Boolean.TRUE.equals(porteurAdverse)) {
                    Pokemon adv = BattleStateTracker.getAdversaireActif();
                    if (adv != null) {
                        Set<String> bruts = talentsBrutsPossibles(adv);
                        if (!bruts.contains("harvest") && !bruts.contains("cudchew")) {
                            marquerObjetAdversairePerdu(adv.getEspece(), "eat");
                            if (objetFr != null) OBJETS_PERDUS_CONNUS.put(adv.getEspece(), objetFr);
                            return;
                        }
                    }
                } else if (Boolean.FALSE.equals(porteurAdverse)) {
                    if (!joueurPeutRecupererBaie()) {
                        Pokemon moi = BattleStateTracker.getJoueurActifDepuisEquipe();
                        if (moi == null) moi = BattleStateTracker.getJoueurActif();
                        if (moi != null) marquerObjetJoueurDetruit(moi.getEspece());
                    }
                    return;
                }
            }
            if (Boolean.TRUE.equals(porteurAdverse)) {
                Pokemon adv = BattleStateTracker.getAdversaireActif();
                if (adv == null || objetFr == null) return;
                if (recu) objetAdversaireRecu(adv.getEspece());
                OBJETS_RETIRES.remove(adv.getEspece());
                confirmerObjet(adv.getEspece(), objetFr, recu ? "reçu (" + suffixe + ")" : "révélé par le jeu (" + suffixe + ")");
                MessageDebugLogger.analyse("OBJET " + adv.getEspece() + " : " + objetFr
                    + (recu ? " reçu" : " révélé") + " par le jeu (" + cle + ")");
            } else if (Boolean.FALSE.equals(porteurAdverse) && recu) {
                Pokemon moi = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (moi == null) moi = BattleStateTracker.getJoueurActif();
                if (moi == null) return;
                marquerObjetJoueurConnu(moi.getEspece(), objetFr);
                if (!stack.isEmpty()) OBJET_STACK_JOUEUR.put(cleEspece(moi.getEspece()), stack);
            }
        }
    }

    /** Vrai si l'objet de cette espèce est un fait observé (soin vu, ou retiré par Sabotage). */
    /** Objet Choix exclu (deux capacités différentes sans sortir). */
    public static boolean estChoixExclu(String espece) {
        return OBJETS_CHOIX_EXCLUS.contains(espece);
    }

    /** Orbe Vie exclue (il a attaqué sans recul annoncé). */
    public static boolean estOrbeVieExclue(String espece) {
        return ORBE_VIE_EXCLUE.contains(espece);
    }

    public static boolean estObjetConfirme(String espece) {
        return OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece);
    }

    /**
     * Confirmation DIRECTE d'un talent par message explicite du jeu
     * ("cobblemon.battle.ability.generic"), bien plus fiable que les
     * heuristiques par seuils de dégâts. Ne concerne que l'adversaire
     * (le talent du joueur est déjà connu avec certitude).
     */
    public static void confirmerTalentParMessage(String proprietaire, String talentAnglais) {
        confirmerTalentParMessage(proprietaire, talentAnglais, null);
    }

    /**
     * @param argPokemon l'argument du message qui nomme le Pokémon : le talent
     *                   lui est attribué, pas au Pokémon que le jeu montre encore
     *                   (log du 12/10 : l'Imposteur d'un Métamorph entrant attribué
     *                   à l'Ectoplasma qu'il remplaçait).
     */
    public static void confirmerTalentParMessage(String proprietaire, String talentAnglais, Object argPokemon) {
        try {
            Boolean estAdversaire = determinerAttaquant(proprietaire);
            if (talentAnglais != null && "flashfire".equals(talentAnglais.toLowerCase().replaceAll("[^a-z]", ""))) {
                activerTorche(estAdversaire);
            }
            if (!Boolean.TRUE.equals(estAdversaire)) return;
            Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            if (adversaire == null) return;
            String talentFr = ShowdownIdMapper.talent(talentAnglais);
            String espece = argPokemon != null ? especeOuActif(argPokemon) : null;
            if (espece == null) espece = adversaire.getEspece();
            if (talentFr != null) {
                TALENTS_CONFIRMES.put(espece, talentFr);
                if ("Épine de Fer".equals(talentFr) || "Peau Dure".equals(talentFr)) {
                    TALENTS_CHIP_CONFIRMES.add(espece);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Confirmation DIRECTE d'un objet par message explicite du jeu, avec
     * l'id showdown déjà connu (ex: "rockyhelmet" tiré de la clé du message
     * "cobblemon.battle.damage.rockyhelmet"). Ne concerne que l'adversaire.
     */
    public static void confirmerObjetParMessage(String proprietaire, String objetShowdownId) {
        try {
            Boolean estAdversaire = determinerAttaquant(proprietaire);
            if (!Boolean.TRUE.equals(estAdversaire)) return;
            Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            if (adversaire == null) return;
            String objetFr = ShowdownIdMapper.objet(objetShowdownId);
            if (objetFr != null) {
                OBJETS_RETIRES.remove(adversaire.getEspece());
                confirmerObjet(adversaire.getEspece(), objetFr, "annoncé par le jeu");
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Confirmation DIRECTE d'un objet dont le nom arrive en anglais espacé
     * ("Black Sludge", "Leftovers") plutôt qu'en id showdown - la
     * normalisation du mapper gère déjà espaces/majuscules donc le lookup
     * fonctionne directement. Ne concerne que l'adversaire.
     */
    public static void confirmerObjetParMessageNomAnglais(String proprietaire, String objetAnglaisEspace) {
        confirmerObjetParMessage(proprietaire, objetAnglaisEspace);
    }

    // Espèces dont le talent à chip de contact (Épine de Fer / Peau Dure) est observé
    private static final Set<String> TALENTS_CHIP_CONFIRMES = new HashSet<>();

    // Talents confirmés par observation (ex: Soin Poison vu en action)
    private static final Map<String, String> TALENTS_CONFIRMES = new HashMap<>();

    public static String getTalentConfirme(String espece) {
        return TALENTS_CONFIRMES.get(espece);
    }

    /** Vrai si un talent type Épine de Fer / Peau Dure a été observé sur cette espèce. */
    public static boolean aChipTalentConfirme(String espece) {
        return TALENTS_CHIP_CONFIRMES.contains(espece);
    }

    // Vampigraine posée sur le Pokémon actif du joueur (fausse la détection de chip)
    private static boolean joueurVampigraine = false;
    private static String especeJoueurSuivie = null;

    // Adversaires vus ce combat : espèce -> {pv%, ordinal statut}, ordre d'apparition
    private static final Map<String, double[]> ADVERSAIRES_VUS = new LinkedHashMap<>();

    /** Vue ordonnée (espèce -> {pv%, ordinal Statut}) des Pokémon adverses aperçus. */
    public static Map<String, double[]> getAdversairesVus() {
        return ADVERSAIRES_VUS;
    }

    // Scouting inter-combats et états stratégiques
    private static String nomAdversaireCourant = null;
    private static String coupVerrouAdversaire = null;   // dernier coup depuis son entrée
    private static int compteurAbrisAdversaire = 0;      // Abris consécutifs
    private static final Set<String> ESPECES_SCOUT_FUSIONNEES = new HashSet<>();
    private static final Set<String> COUPS_PROTECTION = Set.of(
        "protect", "detect", "banefulbunker", "spikyshield", "silktrap",
        "burningbulwark", "kingsshield", "obstruct", "maxguard");

    public static String getNomAdversaireCourant() { return nomAdversaireCourant; }
    public static String getCoupVerrouAdversaire() { return coupVerrouAdversaire; }
    public static int getCompteurAbrisAdversaire() { return compteurAbrisAdversaire; }

    // Volatils et compteurs pour la projection résiduelle des deux camps
    private static boolean joueurSalaison = false;
    private static boolean adversaireSalaison = false;
    private static boolean adversaireVampigraine = false;
    private static int compteurToxikJoueur = 0;
    private static int compteurToxikAdversaire = 0;

    // Vrai dès que le Ballon du joueur a été touché par une attaque réelle -
    // voir la détection dans signalerNouveauTour.
    //
    // Généralisé (signalé par l'utilisateur) : le filet de sécurité
    // spécifique au Ballon n'était appliqué que dans CalcOverlay, jamais
    // dans SwitchOverlayRenderer - donc l'écran de switch pouvait encore
    // montrer un objet déjà détruit. Remplacé par un suivi général PAR
    // ESPÈCE de l'objet réel connu du joueur, couvrant aussi Sabotage subi
    // et un vol réussi via Pickpocket, appliqué aux DEUX écrans de la même
    // façon plutôt que de dupliquer un flag par cas.
    private static final String AUCUN_OBJET = "\0AUCUN_OBJET";
    private static final Map<String, String> OBJET_REEL_JOUEUR_CONNU = new HashMap<>();

    /** Enregistre que ce membre de l'équipe n'a plus d'objet du tout. */
    private static void marquerObjetJoueurDetruit(String espece) {
        if (espece == null) return;
        delestageJoueur = espece;
        OBJET_REEL_JOUEUR_CONNU.put(espece, AUCUN_OBJET);
        OBJET_STACK_JOUEUR.put(cleEspece(espece), ItemStack.EMPTY);
    }

    // Même suivi, mais sous forme de VRAI ItemStack, pour que les icônes du
    // panneau d'équipe (PvpOverlay) suivent l'objet réel : Cobblemon ne
    // rafraîchit pas immédiatement l'objet rapporté après un Sabotage subi,
    // un Ballon éclaté ou un vol. Clé normalisée (minuscules, sans séparateur)
    // car le panneau utilise le chemin de ressource de l'espèce, pas le
    // showdownId. ItemStack.EMPTY = objet perdu, pas d'entrée = on fait
    // confiance à Cobblemon.
    private static final Map<String, ItemStack> OBJET_STACK_JOUEUR = new HashMap<>();

    private static String cleEspece(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Icône de l'objet d'un adversaire pour le panneau d'équipe : seulement
     * un objet CONFIRMÉ (jamais une estimation ni un objet probable), et rien
     * une fois l'objet perdu.
     */
    public static synchronized ItemStack stackObjetAdverse(String speciesId) {
        String cle = cleEspece(speciesId);
        for (Map.Entry<String, String> e : OBJETS_CONFIRMES.entrySet()) {
            if (cleEspece(e.getKey()).equals(cle) && !OBJETS_RETIRES.contains(e.getKey())) {
                return stackDepuisNomFr(e.getValue());
            }
        }
        return ItemStack.EMPTY;
    }

    // Objets du jeu indexés par id sans "_" ("choiceband" -> Bandeau Choix)
    private static Map<String, Item> ITEMS_PAR_ID = null;

    private static ItemStack stackDepuisNomFr(String objetFr) {
        try {
            String id = ShowdownIdMapper.idObjet(objetFr);
            if (id == null) return ItemStack.EMPTY;
            if (ITEMS_PAR_ID == null) {
                Map<String, Item> m = new HashMap<>();
                for (Identifier ident : Registries.ITEM.getIds()) {
                    String ns = ident.getNamespace();
                    if (!"cobblemon".equals(ns) && !"minecraft".equals(ns)) continue;
                    // cobblemon prioritaire en cas de doublon
                    String k = ident.getPath().replace("_", "");
                    if ("cobblemon".equals(ns) || !m.containsKey(k)) m.put(k, Registries.ITEM.get(ident));
                }
                ITEMS_PAR_ID = m;
            }
            Item item = ITEMS_PAR_ID.get(id);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** "item.cobblemon.rocky_helmet" -> vraie icône de cet objet, EMPTY si inconnu. */
    private static ItemStack stackDepuisCleBrute(String cleBrute) {
        try {
            String[] parties = cleBrute.split("\\.");
            if (parties.length < 3 || !"item".equals(parties[0])) return ItemStack.EMPTY;
            Item item = Registries.ITEM.get(Identifier.of(parties[1], parties[2]));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Throwable e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * À appeler par le panneau d'équipe pour chaque Pokémon du joueur :
     * renvoie l'objet réel connu s'il a changé en combat, sinon celui que
     * Cobblemon rapporte.
     */
    public static ItemStack appliquerStackJoueur(String speciesId, ItemStack parDefaut) {
        ItemStack connu = OBJET_STACK_JOUEUR.get(cleEspece(speciesId));
        return connu != null ? connu : parDefaut;
    }

    /** Enregistre le nouvel objet réel connu de ce membre de l'équipe. */
    private static void marquerObjetJoueurConnu(String espece, String objetFr) {
        if (espece != null && objetFr != null) OBJET_REEL_JOUEUR_CONNU.put(espece, objetFr);
        if (espece != null && espece.equals(delestageJoueur)) delestageJoueur = null;
    }

    /**
     * À appliquer sur tout Pokemon du joueur avant affichage (CalcOverlay ET
     * SwitchOverlayRenderer) : si un changement d'objet a été détecté pour
     * cette espèce et que Cobblemon n'a pas encore corrigé lui-même son
     * propre objet rapporté, force la vraie valeur connue à la place.
     */
    public static void appliquerObjetReelJoueur(Pokemon p) {
        if (p == null) return;
        p.setDepartLentActif(departLentActif(false, p.getEspece()));
        String connu = OBJET_REEL_JOUEUR_CONNU.get(p.getEspece());
        if (connu == null) return;
        p.setObjet(AUCUN_OBJET.equals(connu) ? null : connu);
        // Délestage seulement pour le Pokémon sur le terrain qui l'a perdu
        // depuis son entrée (pas pour un membre qui entrerait)
        p.setObjetPerdu(AUCUN_OBJET.equals(connu) && delestageActifJoueur(p.getEspece()));
    }

    // Snapshot des stages Attaque/Attaque Spé adverses au début du tour précédent,
    // pour détecter un gain de +2/+2 simultané (Vulné-Assurance) précisément CE tour.
    private static int stageAtkAdvDebutTour = 0;
    // Copies prises au tout début de signalerNouveauTour, avant que les
    // instantanés "DebutTour" soient réécrits pour le nouveau tour : ce sont
    // les valeurs du tour qu'on est en train d'analyser.
    private static int stageAtkAdvTourEcoule = 0;
    private static int stageAtkSpeAdvTourEcoule = 0;
    private static int stageDefJoueurDebutTour = 0;
    private static int stageDefSpeJoueurDebutTour = 0;
    private static int stageDefJoueurTourEcoule = 0;
    private static int stageDefSpeJoueurTourEcoule = 0;
    private static Field.Meteo meteoDebutTour = null;
    private static Field.Meteo meteoTourEcoule = null;
    private static Field.TypeTerrain terrainDebutTour = null;
    private static Field.TypeTerrain terrainTourEcoule = null;
    private static int ecransJoueurDebutTour = 0;
    private static int ecransJoueurTourEcoule = 0;
    // Messages du tour (le message crit n'a pas d'argument : on ne sait pas
    // qui l'a subi, donc tout tour avec un critique est ignoré).
    private static boolean critCeTour = false;
    private static boolean multiCoupsCeTour = false;
    // Vive-Griffe, Tir Vif, Baie Chérim : l'ordre du tour ne dit rien de la vitesse.
    private static boolean prioriteObjetCeTour = false;
    // Perte de PV du joueur qui ne vient pas du coup adverse (Orbe Vie, recul,
    // Casque Brut, Peau Dure...) : la perte mesurée ne mesure plus le coup.
    private static boolean degatsAnnexesJoueurCeTour = false;
    // Prescience / Carnareket qui frappe : dégâts sans lien avec le coup du tour.
    private static boolean attaqueDiffereeCeTour = false;
    private static final Set<String> DEGATS_ANNEXES = Set.of(
        "lifeorb", "recoil", "rockyhelmet", "roughskin", "ironbarbs", "spikyshield",
        "confusion", "mindblown", "steelbeam", "struggle", "curse", "chloroblast",
        "bellydrum", "highjumpkick", "jumpkick", "crash", "explosion", "jabocaberry",
        "rowapberry", "powder", "burningbulwark");

    // État au début du tour en cours (DebutTour) et du tour qu'on analyse (TourEcoule).
    private static int stageVitJoueurDebutTour = 0, stageVitJoueurTourEcoule = 0;
    private static int stageVitAdvTourEcoule = 0;
    private static boolean tailwindJoueurDebutTour = false, tailwindJoueurTourEcoule = false;
    private static boolean tailwindAdvDebutTour = false, tailwindAdvTourEcoule = false;
    private static boolean distorsionDebutTour = false, distorsionTourEcoule = false;
    private static Pokemon.Statut statutJoueurDebutTour = null, statutJoueurTourEcoule = null;
    private static Pokemon.Statut statutAdvDebutTour = null, statutAdvTourEcoule = null;
    private static String especeJoueurDebutTour = null, especeJoueurTourEcoule = null;
    // Pokémon actifs au début du tour (instantanés), pour mesurer la vitesse
    // quand un Demi-Tour les a remplacés avant la fin du tour
    private static Pokemon joueurPokemonDebutTour = null, joueurPokemonTourEcoule = null;
    private static Pokemon adversairePokemonDebutTour = null, adversairePokemonTourEcoule = null;
    // Espèce qui a agi en premier de chaque côté pendant le tour
    private static String especeAdvActeur = null, especeJoueurActeur = null;
    private static int stageAtkSpeAdvDebutTour = 0;

    // Snapshots supplémentaires pour Défiant/Battant (une autre stat baisse,
    // en réaction l'Attaque ou l'Attaque Spé monte de +2).
    private static int stageDefAdvDebutTour = 0;
    private static int stageDefSpeAdvDebutTour = 0;
    private static int stageVitAdvDebutTour = 0;

    // Poing de Colère : persiste PAR ESPÈCE pour toute la durée du combat,
    // ne reset jamais au switch (contrairement à tout le reste ci-dessus).
    private static final Map<String, Integer> COUPS_RAGE_FIST_JOUEUR = new HashMap<>();
    private static final Map<String, Integer> COUPS_RAGE_FIST_ADVERSAIRE = new HashMap<>();

    public static int getCoupsRageFistJoueur(String espece) {
        return COUPS_RAGE_FIST_JOUEUR.getOrDefault(espece, 0);
    }

    public static int getCoupsRageFistAdversaire(String espece) {
        return COUPS_RAGE_FIST_ADVERSAIRE.getOrDefault(espece, 0);
    }

    public static boolean isJoueurSalaison() { return joueurSalaison; }
    public static boolean isJoueurVampigraine() { return joueurVampigraine; }
    public static boolean isAdversaireSalaison() { return adversaireSalaison; }
    public static boolean isAdversaireVampigraine() { return adversaireVampigraine; }
    /** Multiplicateur Toxik du PROCHAIN tour pour le joueur (1 si pas encore subi). */
    public static int getCompteurToxikProchainJoueur() { return compteurToxikJoueur + 1; }
    public static int getCompteurToxikProchainAdversaire() { return compteurToxikAdversaire + 1; }

    /** L'adversaire n'a pas attaqué ce tour (aucun coup, ou un coup de statut). */
    /**
     * Confirme Écharpe/Mouchoir Choix, Bandeau/Lunettes Choix ou Orbe Vie par
     * un signal fort et net — indépendant du moteur de correction (désactivé).
     */

    private static boolean adversaireNAPasAttaque() {
        if (coupAdversaireDuTour == null) return true;
        MoveTemplate t = Moves.INSTANCE.getByName(coupAdversaireDuTour.showdownId());
        return t != null && "status".equalsIgnoreCase(String.valueOf(t.getDamageCategory().getName()));
    }

    private static boolean joueurNAPasAttaque() {
        if (coupJoueurDuTour == null) return true;
        MoveTemplate t = Moves.INSTANCE.getByName(coupJoueurDuTour.showdownId());
        return t != null && "status".equalsIgnoreCase(String.valueOf(t.getDamageCategory().getName()));
    }

    private static boolean immuniseSableSimple(Pokemon p) {
        return p.getType1() == com.tropimon.tropicalc.calc.PokemonType.ROCHE
            || p.getType1() == com.tropimon.tropicalc.calc.PokemonType.SOL
            || p.getType1() == com.tropimon.tropicalc.calc.PokemonType.ACIER
            || p.getType2() == com.tropimon.tropicalc.calc.PokemonType.ROCHE
            || p.getType2() == com.tropimon.tropicalc.calc.PokemonType.SOL
            || p.getType2() == com.tropimon.tropicalc.calc.PokemonType.ACIER;
    }

    // --- Prescience : lanceur gardé pour calculer l'impact ---
    // Les dégâts sont calculés à l'impact avec les stats du lanceur, même
    // s'il a quitté le terrain (ses boosts sont alors perdus).
    private static Pokemon lanceurPrescienceJoueur = null;   // mon Pokémon (la copie si transformé)
    private static Pokemon lanceurPrescienceAdvBase = null;  // l'adversaire tel que vu au lancement

    /** @param adverse vrai si c'est l'adversaire qui a lancé Prescience */
    public static void noterLanceurPrescience(boolean adverse) {
        try {
            if (adverse) {
                lanceurPrescienceAdvBase = BattleStateTracker.getAdversaireActif();
            } else {
                Pokemon moi = BattleStateTracker.getJoueurActifDepuisEquipe();
                if (moi == null) return;
                appliquerObjetReelJoueur(moi);
                if (isJoueurTransforme() && cibleMorphingJoueur != null) moi = construireMorphing(moi, cibleMorphingJoueur);
                lanceurPrescienceJoueur = moi;
            }
        } catch (Exception ignored) {
        }
    }

    /** @param surAdversaire vrai si la Prescience vient de frapper l'adversaire */
    public static void oublierPrescience(boolean surAdversaire) {
        try {
            photographierPrescience(surAdversaire);
        } catch (Exception ignored) {
        }
        if (surAdversaire) lanceurPrescienceJoueur = null;
        else lanceurPrescienceAdvBase = null;
    }

    /** Prescience tombée dans le vide (cible K.O.) : rien à mesurer. */
    public static void abandonnerPrescience(boolean surAdversaire) {
        if (surAdversaire) lanceurPrescienceJoueur = null;
        else lanceurPrescienceAdvBase = null;
    }

    // Impact de Prescience mesuré à la fin du tour (vérifie la ligne du HUD)
    private static String prescienceCible = null;
    private static boolean prescienceSurAdv = false;
    private static double prescienceAvant = -1, prescienceMin = 0, prescienceMax = 0;
    private static String prescienceDetail = "";

    private static void photographierPrescience(boolean surAdversaire) {
        prescienceCible = null;
        MoveTemplate t = Moves.INSTANCE.getByName("futuresight");
        com.tropimon.tropicalc.calc.Move fs = t == null ? null : convertirCapacite(t);
        if (fs == null) return;
        Field terrain = FieldTracker.construireField();
        Pokemon lanceur, cible;
        if (surAdversaire) {
            lanceur = lanceurPrescienceJoueur;
            Pokemon base = BattleStateTracker.getAdversaireActif();
            if (lanceur == null || base == null) return;
            cible = construireAdversaireEstime(base);
            for (Stat st : Stat.values()) if (st != Stat.PV) cible.setStage(st, BoostTracker.getStageAdversaire(st));
            prescienceAvant = base.getPourcentagePv();
            // Mesurée comme mes coups (soins après, résistance, Veste...) :
            // ligne COUP DONNÉ « futuresight (impact) » en fin de tour
            CoupDonne cd = new CoupDonne();
            cd.idCoup = "futuresight";
            cd.impactPrescience = true;
            cd.attaquant = lanceur.copieAvecEvNature(Map.of(), null);
            cd.especeAttaquant = lanceur.getEspece();
            cd.defenseurBase = base;
            cd.defenseurEstime = cible;
            cd.especeDefenseur = base.getEspece();
            cd.pvAvant = prescienceAvant;
            cd.etat = terrain;
            noterInstantDuCoup(cd);
            coupPrescience = cd;
            return;
        } else {
            lanceur = getLanceurPrescienceAdversaire();
            cible = BattleStateTracker.getJoueurActifDepuisEquipe();
            if (lanceur == null || cible == null) return;
            appliquerObjetReelJoueur(cible);
            for (Stat st : Stat.values()) if (st != Stat.PV) cible.setStage(st, BoostTracker.getStageJoueur(st));
            Pokemon combat = BattleStateTracker.getJoueurActif();
            prescienceAvant = combat != null ? combat.getPourcentagePv() : cible.getPourcentagePv();
        }
        DamageCalculator.Resultat r = DamageCalculator.calculer(lanceur, cible, fs, terrain,
            surAdversaire ? terrain.getEcransAdversaire() : terrain.getEcransJoueur(), false);
        if (r.immunise) return;
        prescienceCible = cible.getEspece();
        prescienceSurAdv = surAdversaire;
        prescienceMin = r.pourcentageMin;
        prescienceMax = r.pourcentageMax;
        prescienceDetail = " [lanceur " + lanceur.getEspece() + " AtqSpé " + lanceur.getStatCalculee(Stat.ATTAQUE_SPE)
            + " objet " + lanceur.getObjet() + " | cible PV " + cible.getStatCalculee(Stat.PV)
            + " DéfSpé " + cible.getStatCalculee(Stat.DEFENSE_SPE) + " objet " + cible.getObjet() + "]";
    }

    private static void journaliserPrescience() {
        String cible = prescienceCible;
        prescienceCible = null;
        if (cible == null) return;
        Pokemon actuel = prescienceSurAdv ? BattleStateTracker.getAdversaireActif() : BattleStateTracker.getJoueurActif();
        if (actuel == null || !memeEspece(actuel.getEspece(), cible)) return;
        double perte = prescienceAvant - actuel.getPourcentagePv();
        MessageDebugLogger.analyse("PRESCIENCE sur " + cible + " | PV avant " + pct(prescienceAvant) + " | perte "
            + pct(perte) + (actuel.getPvActuels() <= 0 ? " K.O. (borne basse)" : "") + " | prévu " + pct(prescienceMin)
            + " - " + pct(prescienceMax) + prescienceDetail + " (Leftovers et autres soins non retirés)");
    }

    /** Mon Pokémon qui a lancé la Prescience en cours sur l'adversaire, ou null. */
    public static Pokemon getLanceurPrescienceJoueur() {
        return lanceurPrescienceJoueur;
    }

    /** Estimation de l'adversaire qui a lancé la Prescience en cours sur moi, ou null. */
    public static Pokemon getLanceurPrescienceAdversaire() {
        return lanceurPrescienceAdvBase == null ? null : construireAdversaireEstime(lanceurPrescienceAdvBase);
    }

    // --- Bilan de précision du combat (écrit dans le journal à la fin) ---
    // Chaque coup mesuré : dans la fourchette prévue ou non. Sert à voir où le
    // mod se trompe encore et si une correction améliore vraiment les choses.
    private static final class MesureBilan {
        final String quoi; final double perte, min, max; final boolean ko;
        MesureBilan(String quoi, double perte, double min, double max, boolean ko) {
            this.quoi = quoi; this.perte = perte; this.min = min; this.max = max; this.ko = ko;
        }
        boolean dedans() {
            // K.O. : la perte n'est qu'une borne basse, le prévu doit l'atteindre
            return ko ? max >= perte - 1.5 : perte >= min - 1.5 && perte <= max + 1.5;
        }
    }
    private static final List<MesureBilan> BILAN = new ArrayList<>();

    private static void noterBilan(String quoi, double perte, DamageCalculator.Resultat r, boolean ko) {
        if (r == null || r.immunise || Double.isNaN(perte)) return;
        BILAN.add(new MesureBilan(quoi, perte, r.pourcentageMin, r.pourcentageMax, ko));
    }

    private static void ecrireBilan() {
        if (BILAN.isEmpty()) return;
        int dedans = 0;
        List<String> ecarts = new ArrayList<>();
        // Par espèce adverse (celle dont le set est estimé) pour le bilan cumulé
        Map<String, int[]> parEspece = new HashMap<>();
        for (MesureBilan m : BILAN) {
            String esp = especeAdverseDuBilan(m.quoi);
            if (esp != null) {
                int[] c = parEspece.computeIfAbsent(esp, k -> new int[2]);
                c[0]++;
                if (m.dedans()) c[1]++;
            }
            if (m.dedans()) {
                dedans++;
            } else {
                ecarts.add(m.quoi + " : " + pct(m.perte) + (m.ko ? " K.O." : "") + " pour " + pct(m.min) + " - " + pct(m.max)
                    + (m.perte > m.max ? " (plus)" : " (moins)"));
            }
        }
        MessageDebugLogger.analyse(String.format(java.util.Locale.ROOT,
            "BILAN DU COMBAT : %d/%d coups dans la fourchette prévue (%.0f %%)", dedans, BILAN.size(),
            100.0 * dedans / BILAN.size()));
        for (String e : ecarts) MessageDebugLogger.analyse("BILAN écart : " + e);
        // Mauvais mod pour ce format : les hypothèses (EV) ne s'appliquent
        // pas, le cumul serait faussé (combats classés lus par RandomPvp, 28/10)
        if (Boolean.TRUE.equals(BattleStateTracker.estFormatRandom())) MessageDebugLogger.analyse("BILAN : combat random, non compté dans le cumul TropiCalc");
        else BilanCumule.noter(parEspece, BILAN.size(), dedans);
        BILAN.clear();
    }

    /** "reçu X coup -> moi" : X ; "donné moi coup -> X" : X. */
    private static String especeAdverseDuBilan(String quoi) {
        try {
            if (quoi.startsWith("reçu ")) return quoi.split(" ")[1];
            int i = quoi.indexOf("-> ");
            if (quoi.startsWith("donné ") && i >= 0) return quoi.substring(i + 3).split(" ")[0];
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void reinitialiser() {
        try {
            ecrireBilan();
        } catch (Exception ignored) {
        }
        lanceurPrescienceJoueur = null;
        lanceurPrescienceAdvBase = null;
        MessageDebugLogger.analyse("=============== NOUVEAU COMBAT ===============");
        finDeTourEnAttenteMs = 0;
        combatSauvageDetecte = false;
        OBJET_REEL_JOUEUR_CONNU.clear();
        delestageAdversaire = null;
        delestageJoueur = null;
        OBJET_STACK_JOUEUR.clear();
        COMPTEUR_REPOS.clear();
        COMPTEUR_REPOS_ADV.clear();
        REPOS_ADV_MATINAL_POSSIBLE.clear();
        reposEnAttenteAdv = null;
        especeAdvActiveDebutTour = null;
        KO_ADVERSAIRE.clear();
        KO_JOUEUR.clear();
        RAISONS_OBJET.clear();
        OBJETS_ANNULES.clear();
        BALLON_ANNONCE.clear();
        ENTREE_ADVERSAIRE_MS.clear();
        PREUVE_PAS_CHOIX.clear();
        ORBE_VIE_EXCLUE.clear();
        TRANSFORMES.clear();
        cibleMorphingJoueur = null;
        capacitesMorphing = new ArrayList<>();
        cibleMorphingAdversaire = null;
        especeMorphingAdversaire = null;
        capacitesMorphingAdversaire = new ArrayList<>();
        reculOrbeVieAdvCeTour = false;
        coupAdvRateCeTour = false;
        TORCHE_ACTIVE.clear();
        OBJETS_PROBABLES.clear();
        RAISONS_PROBABLES.clear();
        OBJETS_CHOIX_EXCLUS.clear();
        especeSejourAdv = null;
        premierCoupSejourAdv = null;
        coupRecu = null;
        coupDonne = null;
        coupPrescience = null;
        coupRecuTourEcoule = null;
        joueurBloqueCeTour = false;
        dernierCoupEstAdverse = false;
        meteoDebutTour = null;
        terrainDebutTour = null;
        ecransJoueurDebutTour = 0;
        stageDefJoueurDebutTour = 0;
        stageDefSpeJoueurDebutTour = 0;
        reposEnAttente = null;
        especeActiveDebutTour = null;
        numeroTour = 0;

        // Persister les faits du combat avant de tout effacer
        if (nomAdversaireCourant != null) {
            Set<String> especes = new HashSet<>();
            especes.addAll(OBJETS_CONFIRMES.keySet());
            especes.addAll(OBJETS_PERDUS_CONNUS.keySet());
            especes.addAll(TALENTS_CONFIRMES.keySet());
            especes.addAll(TALENTS_CHIP_CONFIRMES);
            especes.addAll(COUPS_ADVERSAIRE.keySet());
            for (String esp : especes) {
                ScoutingStore.enregistrer(nomAdversaireCourant, esp,
                    OBJETS_CONFIRMES.get(esp) != null ? OBJETS_CONFIRMES.get(esp) : OBJETS_PERDUS_CONNUS.get(esp),
                    TALENTS_CONFIRMES.get(esp),
                    TALENTS_CHIP_CONFIRMES.contains(esp),
                    COUPS_ADVERSAIRE.get(esp));
            }
            nomAdversaireCourant = null;
        }
        coupVerrouAdversaire = null;
        compteurAbrisAdversaire = 0;
        ESPECES_SCOUT_FUSIONNEES.clear();
        ADVERSAIRES_VUS.clear();
        FACTEURS.clear();
        OBJETS_PERDUS_CONNUS.clear();
        derniereEntreeAdv = null;
        derniereEntreeMoi = null;
        especeSejourIllusion = null;
        faitsAvantSejour = null;
        OBJETS_EXCLUS.clear();
        PREUVES_EXCLUSION.clear();
        PV_METAMORPH.clear();
        PvEquipe.vider();
        DEPART_LENT.clear();
        chargeAdversaire = false;
        BattleStateTracker.oublierFormes();
        BoostsDouble.vider();
        InferenceDouble.vider();
        TALENTS_PROBABLES.clear();
        PLAFONDS_DEFENSIFS.clear();
        PLANCHERS_DEFENSIFS.clear();
        MESURES_DEFENSIVES.clear();
        DEFENSIFS_ACTIFS.clear();
        dernierePhotoPv = null;
        SUIVI_PV_ADV.vider();
        SUIVI_PV_MOI.vider();
        debutTourMs = 0;
        residuelMs = 0;
        especeAuResiduel = null;
        SOIGNES_CE_TOUR.clear();
        photoPvEnCours = null;
        meteoType = null;
        meteoLanceurAdv = null;
        meteoUpkeeps = 0;
        PLAFONDS_OFFENSIFS.clear();
        MESURES_PLAFOND.clear();
        PLAFONDS_ACTIFS.clear();
        PLANCHERS_OFFENSIFS.clear();
        MESURES_PLANCHER.clear();
        PLANCHERS_ACTIFS.clear();
        prescienceCible = null;
        PROFILS.clear();
        COUPS_ADVERSAIRE.clear();
        COUPS_ADVERSAIRE_COMPLETS.clear();
        PP_UTILISES.clear();
        OBJETS_CONFIRMES.clear();
        TALENTS_CHIP_CONFIRMES.clear();
        TALENTS_CONFIRMES.clear();
        coupAdversaireTourPrecedent = null;
        joueurVampigraine = false;
        joueurSalaison = false;
        adversaireVampigraine = false;
        adversaireSalaison = false;
        compteurToxikJoueur = 0;
        compteurToxikAdversaire = 0;
        stageAtkAdvDebutTour = 0;
        stageAtkSpeAdvDebutTour = 0;
        stageDefAdvDebutTour = 0;
        stageDefSpeAdvDebutTour = 0;
        stageVitAdvDebutTour = 0;
        COUPS_RAGE_FIST_JOUEUR.clear();
        COUPS_RAGE_FIST_ADVERSAIRE.clear();
        especeJoueurSuivie = null;
        OBJETS_RETIRES.clear();
        VITESSES_MIN_OBSERVEES.clear();
        VITESSES_MAX_OBSERVEES.clear();
        OBSERVATIONS_VITESSE.clear();
        BoostTracker.reinitialiser();
        TypeTracker.reinitialiser();
        FieldTracker.reinitialiser();
        pvJoueurDebutTour = -1;
        pvAdversaireDebutTour = -1;
        coupJoueurDuTour = null;
        coupAdversaireDuTour = null;
        critCeTour = false;
        prioriteObjetCeTour = false;
        degatsAnnexesJoueurCeTour = false;
        attaqueDiffereeCeTour = false;
        multiCoupsCeTour = false;
        adversaireAAgiEnPremier = null;
        especeAdvActeur = null;
        especeJoueurActeur = null;
        reculOrbeVieAdvCeTour = false;
        coupAdvRateCeTour = false;
        espaceAdversaireDuTour = null;
    }

    public static Set<String> getTalentsReelsEspece(Pokemon adversaire) {
        // Forme vue en combat comprise (Tranchant du Samurott de Hisui)
        List<String> noms = BattleStateTracker.talentsPossibles(adversaire.getEspece());
        if (noms == null) return null;
        Set<String> r = new HashSet<>();
        for (String n : noms) {
            String fr = ShowdownIdMapper.talent(n);
            if (fr != null) r.add(fr);
        }
        return r;
    }

    private static com.tropimon.tropicalc.calc.Move convertirCapacite(MoveTemplate template) {
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
