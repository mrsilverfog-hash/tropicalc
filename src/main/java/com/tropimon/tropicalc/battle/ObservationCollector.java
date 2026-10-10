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
    private static final Map<String, String> OBJETS_CONFIRMES = new HashMap<>();

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

    public static synchronized void signalerNouveauTour() {
        mettreAJourRepos();
        try {
            analyserCoupRecu();
        } catch (Exception ignored) {
        }
        coupRecu = null;
        stageAtkAdvTourEcoule = stageAtkAdvDebutTour;
        stageVitAdvTourEcoule = stageVitAdvDebutTour;
        stageVitJoueurTourEcoule = stageVitJoueurDebutTour;
        tailwindJoueurTourEcoule = tailwindJoueurDebutTour;
        tailwindAdvTourEcoule = tailwindAdvDebutTour;
        distorsionTourEcoule = distorsionDebutTour;
        statutJoueurTourEcoule = statutJoueurDebutTour;
        statutAdvTourEcoule = statutAdvDebutTour;
        especeJoueurTourEcoule = especeJoueurDebutTour;
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
        } else if (espaceAdversaireDuTour != null && coupAdversaireDuTour != null) {
            // Pas de switch : une capacité différente de celle du tour
            // précédent est une preuve certaine que l'objet Choix
            // proactif était une erreur (Choix verrouille sur un seul coup).
            tenterRevoquerObjetChoix(adversaire.getEspece(), coupAdversaireDuTour.showdownId());
        }

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
                if (perteAdversaire <= -5.0 && perteAdversaire >= -8.0
                        && adversaire.getEspece().equals(espaceAdversaireDuTour)
                        && !OBJETS_RETIRES.contains(adversaire.getEspece())
                        && (coupAdversaireDuTour == null
                            || !COUPS_SOIN_OU_DRAIN.contains(coupAdversaireDuTour.showdownId()))
                        && !"wish".equals(coupAdversaireTourPrecedent)
                        && FieldTracker.construireField().getTerrain() != Field.TypeTerrain.HERBU) {
                    OBJETS_CONFIRMES.put(adversaire.getEspece(), "Restes");
                }
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
                return;
            }

            if (coupJoueurDuTour != null && "knockoff".equals(coupJoueurDuTour.showdownId())
                    && perteAdversaire >= 0.5
                    && !"Glu".equals(adversaire.getTalent())) {
                OBJETS_RETIRES.add(adversaire.getEspece());
            }

            // Ballon : explose dès qu'une attaque touche RÉELLEMENT le porteur
            // (jamais sur les dégâts indirects - confusion, brûlure, poison,
            // sable, Piège de Roc - vérifié sur Poképédia). perteAdversaire > 0
            // exclut déjà naturellement le cas d'immunité Sol non consommée
            // (une capacité Sol contre un Ballon intact inflige 0 dégât - le
            // Ballon n'éclate d'ailleurs pas dans ce cas précis, confirmé).
            // IMPORTANT : adversaire ici est l'objet BRUT (BattleStateTracker),
            // qui ne connaît pas l'objet estimé/confirmé - il faut reconstruire
            // l'estimation pour savoir si "Ballon" est vraiment ce qu'on pense
            // qu'il tient, sinon cette condition ne se déclenche quasiment
            // jamais en pratique.
            if ("Ballon".equals(construireAdversaireEstime(adversaire).getObjet())
                    && coupJoueurDuTour != null
                    && !joueurNAPasAttaque() && perteAdversaire > 0) {
                OBJETS_RETIRES.add(adversaire.getEspece());
            }

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

            // Détection Casque Brut : tour "propre" où le joueur attaque au contact,
            // l'adversaire ne l'attaque pas, et le joueur perd des PV quand même.
            // 12.5% = Épine de Fer/Peau Dure seule | ~17% = Casque Brut | ~29% = les deux
            if (coupJoueurDuTour != null
                    && com.tropimon.tropicalc.calc.ContactMoves.estContact(coupJoueurDuTour.showdownId())
                    && perteAdversaire >= 0.5
                    && perteJoueur >= 14.0 && perteJoueur <= 33.0
                    && !(perteJoueur > 20.0 && perteJoueur < 25.0)
                    && adversaireNAPasAttaque()
                    && joueur.getStatut() == Pokemon.Statut.AUCUN
                    && !joueurVampigraine
                    && !"Orbe Vie".equals(joueur.getObjet())
                    && !OBJETS_RETIRES.contains(adversaire.getEspece())
                    && (FieldTracker.construireField().getMeteo() != Field.Meteo.SABLE
                        || immuniseSableSimple(joueur))) {
                OBJETS_CONFIRMES.put(adversaire.getEspece(), "Casque Brut");
                // 25-33% = Casque Brut + Épine de Fer/Peau Dure : le talent aussi est un fait
                if (perteJoueur >= 25.0) {
                    TALENTS_CHIP_CONFIRMES.add(adversaire.getEspece());
                }
            }

            // Détection Orbe Vie : tour "propre" où l'adversaire attaque
            // (dégâts, pas statut, pas de recul propre à la capacité) et
            // perd ~10% de ses PV max le même tour, sans autre source
            // possible (statut, Salaison, Vampigraine, sable non-immunisé,
            // dégâts du joueur qui viendraient troubler la mesure).
            if (coupAdversaireDuTour != null
                    && !adversaireNAPasAttaque()
                    && !com.tropimon.tropicalc.calc.MoveFlags.aRecul(coupAdversaireDuTour.showdownId())
                    && reculOrbeVieIsole(joueur, adversaire, perteAdversaire)
                    && adversaire.getStatut() == Pokemon.Statut.AUCUN
                    && !adversaireVampigraine
                    && !adversaireSalaison
                    && !OBJETS_CONFIRMES.containsKey(adversaire.getEspece())
                    && !OBJETS_RETIRES.contains(adversaire.getEspece())
                    && (FieldTracker.construireField().getMeteo() != Field.Meteo.SABLE
                        || immuniseSableSimple(adversaire))) {
                OBJETS_CONFIRMES.put(adversaire.getEspece(), "Orbe Vie");
            }

            // Vitesse : ordre d'action du tour écoulé, évalué avec l'état du
            // DÉBUT de ce tour (stages, météo, champ, Vent Arrière, statut).
            observerVitesse(adversaire, joueur);

            if (coupAdversaireDuTour != null && perteJoueur >= 0.5) {
                enregistrerObservation(true, perteJoueur, adversaire, joueur, coupAdversaireDuTour);
            }
            if (coupJoueurDuTour != null && perteAdversaire >= 0.5) {
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

        tenterAppliquerHerbeBlanche(joueur, adversaire);
    }

    public static synchronized void signalerCoupUtilise(MoveUseTracker.CoupDetecte coup) {
        // Change-Côté : effet symétrique, peu importe qui la joue - un seul
        // appel suffit, peu importe le camp.
        if ("courtchange".equals(coup.showdownId())) {
            FieldTracker.echangerCotes();
        }

        Boolean estAdversaire = determinerAttaquant(coup.proprietaire());

        // Premier coup du tour = camp qui agit en premier
        if (adversaireAAgiEnPremier == null && estAdversaire != null) {
            adversaireAAgiEnPremier = estAdversaire;
        }

        if (Boolean.TRUE.equals(estAdversaire)) {
            coupAdversaireDuTour = coup;
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

    /**
     * Vrai si la perte de PV de l'adversaire sur ce tour s'explique par le
     * recul de l'Orbe Vie (10% des PV max). Porté depuis randompvp (aeadf47).
     *
     * Cas simple : le joueur n'a pas attaqué, la perte EST le recul.
     *
     * Cas général : le joueur a aussi attaqué, donc la perte observée mélange
     * ses dégâts et le recul. On soustrait les dégâts prévus pour isoler le
     * résidu. L'ancienne condition exigeait que le joueur n'attaque pas, ce qui
     * ne se produit presque jamais : la détection ne se déclenchait quasiment
     * pas. L'Orbe Vie adverse n'influe pas sur les dégâts que le JOUEUR
     * inflige, donc aucune circularité.
     *
     * Précision propre au ranked : ici le set défensif adverse est ESTIMÉ (pas
     * fixe comme en random battle), donc cette voie est moins sûre que le
     * message direct cobblemon.battle.damage.lifeorb, traité plus haut, qui
     * reste la source principale. Celle-ci sert de secours.
     */
    private static boolean reculOrbeVieIsole(Pokemon joueur, Pokemon adversaire, double perteAdversaire) {
        if (coupJoueurDuTour == null || joueurNAPasAttaque()) {
            return perteAdversaire >= 8.0 && perteAdversaire <= 12.0;
        }
        try {
            MoveTemplate template = Moves.INSTANCE.getByName(coupJoueurDuTour.showdownId());
            if (template == null) return false;
            com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
            if (capacite == null) return false;

            Pokemon defenseur = construireAdversaireEstime(adversaire);
            Field terrain = FieldTracker.construireField();
            DamageCalculator.Resultat prevu = DamageCalculator.calculer(
                joueur, defenseur, capacite, terrain, terrain.getEcransAdversaire(), false);
            if (prevu.immunise) {
                return perteAdversaire >= 8.0 && perteAdversaire <= 12.0;
            }

            // Résidu possible une fois les dégâts du joueur retirés. Tolérance
            // un peu plus large que la fenêtre 8-12% du cas simple, le résidu
            // cumulant l'imprécision des deux mesures.
            double residuMin = perteAdversaire - prevu.pourcentageMax;
            double residuMax = perteAdversaire - prevu.pourcentageMin;
            return residuMin >= 7.0 && residuMax <= 13.5;
        } catch (Throwable e) {
            return false;
        }
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
        if (coupJoueurDuTour == null || coupAdversaireDuTour == null || adversaireAAgiEnPremier == null) return;
        if (prioriteObjetCeTour || distorsionTourEcoule) return;
        // Le même Pokémon de chaque côté du début à la fin du tour, sinon
        // l'ordre observé ne concerne pas celui qu'on regarde (Demi-Tour...).
        String espece = adversaire.getEspece();
        if (!espece.equals(espaceAdversaireDuTour)) return;
        if (joueurAChangeCeTour()) return;

        String idJ = coupJoueurDuTour.showdownId();
        String idA = coupAdversaireDuTour.showdownId();
        if (!prioritesEgales(idJ, idA)) return;
        if (prioriteModifiee(idA, talentsBrutsPossibles(adversaire))) return;
        if (prioriteModifiee(idJ, talentBrutJoueur(joueur))) return;

        double vJoueur = vitesseJoueurTourEcoule(joueur);
        if (vJoueur <= 0) return;

        if (Boolean.TRUE.equals(adversaireAAgiEnPremier)) {
            // Plancher affiché : seulement si sa vitesse n'était pas gonflée.
            if (stageVitAdvTourEcoule <= 0 && !tailwindAdvTourEcoule) {
                VITESSES_MIN_OBSERVEES.merge(espece, (int) vJoueur, Math::max);
            }
            if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)
                    || OBJETS_CHOIX_EXCLUS.contains(espece)) return;
            double plafond = plafondVitesseSansObjet(adversaire, 252, Nature.TIMIDE);
            // 2% de marge pour les arrondis de mon modèle ; un Mouchoir fait +50%.
            if (plafond > 0 && vJoueur > plafond * 1.02) {
                OBJETS_CONFIRMES.put(espece, "Mouchoir Choix");
                return;
            }
            // Plus tôt, avec Smogon : si presque aucun réglage de vitesse joué par
            // cette espèce ne lui permet d'aller aussi vite que moi sans objet,
            // et que le Mouchoir est courant chez elle, c'est un Mouchoir.
            if (proportionReglagesAssezRapides(adversaire, vJoueur) < 0.05
                    && SmogonDataLoader.fractionObjet(espece, "choicescarf") >= 0.10) {
                OBJETS_CONFIRMES.put(espece, "Mouchoir Choix");
            }
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
        try {
            Pokemon base = construireAdversaireEstime(adversaire);
            Field etat = FieldTracker.construireField();
            Field.Meteo meteo = meteoTourEcoule != null ? meteoTourEcoule : etat.getMeteo();
            Field.TypeTerrain terrain = terrainTourEcoule != null ? terrainTourEcoule : etat.getTerrain();

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
                p.setStage(Stat.VITESSE, stageVitAdvTourEcoule);
                p.setStatut(statutAdvTourEcoule != null ? statutAdvTourEcoule : adversaire.getStatut());
                meilleur = Math.max(meilleur,
                    DamageCalculator.vitesseEnCombat(p, meteo, terrain, tailwindAdvTourEcoule));
            }
            // Effets de vitesse que le calcul ne modélise pas : on élargit.
            Set<String> bruts = talentsBrutsPossibles(adversaire);
            if (bruts.contains("unburden")) meilleur *= 2.0;            // Allège après objet consommé
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
            Species espece = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(adversaire.getEspece());
            if (espece == null) return r;
            String confirme = TALENTS_CONFIRMES.get(adversaire.getEspece());
            for (var a : espece.getAbilities()) {
                String id = a.getTemplate().getName().toLowerCase().replaceAll("[^a-z0-9]", "");
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
        "avalanche", "revenge", "payback", "brine", "venoshock", "assurance",
        "stompingtantrum", "temperflare", "lashout", "retaliate", "expandingforce",
        "risingvoltage", "psyblade", "collisioncourse", "electrodrift", "terrainpulse",
        "barbbarrage", "infernalparade", "smellingsalts", "wakeupslap", "round",
        "echoedvoice", "furycutter", "rollout", "iceball", "trumpcard", "punishment",
        "beatup", "fling", "naturalgift", "present", "magnitude", "spitup", "foulplay",
        "photongeyser", "terablast", "shellsidearm", "ragingbull", "futuresight",
        "doomdesire", "bodypress", "electroball", "lastrespects", "pursuit", "hiddenpower",
        "tripleaxel", "triplekick", "populationbomb", "ragefist");

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
        double pvAttaquantPct;
        Pokemon.Statut statutAttaquant;
        int stageAtk, stageAtkSpe;
        String especeDefenseur;     // côté combat (pour le suivi des messages)
        Pokemon defenseur;          // stats complètes, objet, stages au moment du coup
        double pvAvant;
        Field etat;
        boolean clone;
        boolean crit, multiCoups, ko, invalide;
        double correctionPv = 0;    // PV rendus (+) ou perdus (-) après le coup
    }
    private static CoupRecu coupRecu = null;
    private static boolean dernierCoupEstAdverse = false;

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

    private static void suivreCoupRecu(String cle, Object[] args) {
        boolean coup = cle.equals("cobblemon.battle.used_move_on") || cle.equals("cobblemon.battle.used_move");
        if (coup) {
            Boolean adverse = args.length > 0
                ? determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0])) : null;
            dernierCoupEstAdverse = Boolean.TRUE.equals(adverse);
            String idCoup = null;
            if (args.length > 1) {
                String brut = String.valueOf(args[1]);
                int i = brut.lastIndexOf('.');
                idCoup = (i >= 0 ? brut.substring(i + 1) : brut).toLowerCase().replaceAll("[^a-z0-9]", "");
            }
            if (Boolean.FALSE.equals(adverse) && coupRecu != null && idCoup != null
                    && COUPS_COUT_PV.contains(idCoup)) {
                coupRecu.invalide = true;
            }
            if (dernierCoupEstAdverse && cle.equals("cobblemon.battle.used_move_on") && args.length > 2
                    && Boolean.FALSE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[2])))) {
                photographierCoupRecu(idCoup, especeDepuisArgument(args[0]), especeDepuisArgument(args[2]));
            }
            return;
        }
        CoupRecu cr = coupRecu;
        if (cr == null) return;
        Boolean surMoi = args.length > 0
            ? Boolean.FALSE.equals(determinerAttaquant(MoveUseTracker.extraireProprietaire(args[0]))) : false;
        boolean surDefenseur = surMoi && memeEspece(especeDepuisArgument(args[0]), cr.especeDefenseur);

        if (cle.equals("cobblemon.battle.crit")) {
            if (dernierCoupEstAdverse) cr.crit = true;
        } else if (cle.equals("cobblemon.battle.hit_count")) {
            if (dernierCoupEstAdverse) cr.multiCoups = true;
        } else if (cle.equals("cobblemon.battle.fainted")) {
            if (surDefenseur) cr.ko = true;
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

        CoupRecu cr = new CoupRecu();
        cr.idCoup = idCoup;
        cr.especeAttaquant = attaquant.getEspece();
        cr.attaquant = attaquant;
        cr.pvAttaquantPct = attaquant.getPourcentagePv();
        cr.statutAttaquant = attaquant.getStatut();
        cr.stageAtk = BoostTracker.getStageAdversaire(Stat.ATTAQUE);
        cr.stageAtkSpe = BoostTracker.getStageAdversaire(Stat.ATTAQUE_SPE);
        cr.especeDefenseur = defCombat.getEspece();
        cr.defenseur = defenseur;
        cr.pvAvant = defCombat.getPourcentagePv();
        cr.etat = FieldTracker.construireField();
        cr.clone = FieldTracker.joueurAUnClone();
        coupRecu = cr;
    }

    private static void analyserCoupRecu() {
        CoupRecu cr = coupRecu;
        if (cr == null || cr.invalide || cr.crit || cr.multiCoups || cr.clone) return;
        String espece = cr.especeAttaquant;
        if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)
                || OBJETS_CHOIX_EXCLUS.contains(espece)) return;
        if (PUISSANCE_NON_MODELISEE.contains(cr.idCoup)) return;

        MoveTemplate template = Moves.INSTANCE.getByName(cr.idCoup);
        if (template == null) return;
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.isMultiCoups()) return;
        boolean physique = capacite.getCategorie() == com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        if (!physique && capacite.getCategorie() != com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE) return;

        // Perte due au coup seul (ou borne basse si K.O.).
        double perte;
        if (cr.ko) {
            perte = cr.pvAvant;
        } else {
            Pokemon actif = BattleStateTracker.getJoueurActif();
            if (actif == null || !memeEspece(actif.getEspece(), cr.especeDefenseur)) return;
            perte = cr.pvAvant - actif.getPourcentagePv() + cr.correctionPv;
        }
        if (perte < 3.0) return;

        // Talents absents du calcul : abstention ou seuil relevé.
        Set<String> bruts = talentsBrutsPossibles(cr.attaquant);
        double seuil = seuilAutreQueChoix(bruts, capacite, physique, cr.pvAttaquantPct);
        if (seuil < 0) return;
        // Les x1.2 (objet du type de l'attaque, Ceinture Pro si super efficace)
        // sont-ils plausibles chez cette espèce ? Si Smogon les donne quasi
        // absents et le Choix courant, tout dépassement du maximum sans objet
        // suffit : on tranche dès le premier coup au lieu d'exiger x1.2 de marge.
        // L'Orbe Vie est déjà exclue : le jeu annonce son recul (damage.lifeorb).
        seuil = Math.max(seuilTalents(bruts), seuilObjets(espece, capacite, physique, cr.defenseur));

        // Maximum SANS objet : 252 EV, nature favorable, meilleur talent possible,
        // dans l'état exact du moment du coup.
        Stat statAtk = physique ? Stat.ATTAQUE : Stat.ATTAQUE_SPE;
        Pokemon base = construireAdversaireEstime(cr.attaquant);
        Set<String> talents = new HashSet<>();
        String talentConfirme = TALENTS_CONFIRMES.get(espece);
        if (talentConfirme != null) {
            talents.add(talentConfirme);
        } else {
            Set<String> reels = getTalentsReelsEspece(cr.attaquant);
            if (reels != null) talents.addAll(reels);
            if (base.getTalent() != null) talents.add(base.getTalent());
        }
        if (talents.isEmpty()) talents.add(StatHypothesis.AUCUN);

        double max = 0;
        for (String talent : talents) {
            Pokemon h = com.tropimon.tropicalc.calc.SetInferenceEngine.construirePokemonHypothetique(
                base, statAtk, 252, com.tropimon.tropicalc.calc.SetInferenceEngine.NatureBoost.BOOSTEE,
                StatHypothesis.AUCUN, talent);
            for (Stat st : Stat.values()) {
                if (st != Stat.PV) h.setStage(st, 0);
            }
            h.setStage(Stat.ATTAQUE, cr.stageAtk);
            h.setStage(Stat.ATTAQUE_SPE, cr.stageAtkSpe);
            if (cr.statutAttaquant != null) h.setStatut(cr.statutAttaquant);
            DamageCalculator.Resultat r = DamageCalculator.calculer(
                h, cr.defenseur, capacite, cr.etat, cr.etat.getEcransJoueur(), false);
            if (r.immunise) return;
            max = Math.max(max, r.pourcentageMax);
        }
        if (max < 3.0) return;

        double plancher = max * seuil * 1.03 + 1.0;   // au-delà : ni sans objet, ni objet x1.2
        double plafond = max * 1.5 * 1.03 + 1.0;      // au-delà : autre chose qu'un Choix
        // Sur un K.O., la perte est une borne basse (il a fait AU MOINS ça) :
        // la même règle reste valable, elle ne peut que rater un Choix.
        if (perte > plancher && perte <= plafond) {
            OBJETS_CONFIRMES.put(espece, physique ? "Bandeau Choix" : "Lunettes Choix");
        }
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
        if (bruts.contains("punkrock")) s = Math.max(s, 1.3);
        if (bruts.contains("analytic")) s = Math.max(s, 1.3);
        if (bruts.contains("rivalry") || bruts.contains("neuroforce")) s = Math.max(s, 1.25);
        if (bruts.contains("orichalcumpulse") || bruts.contains("hadronengine")) s = Math.max(s, 1.34);
        return s;
    }

    private static double seuilAutreQueChoix(Set<String> bruts, com.tropimon.tropicalc.calc.Move capacite,
                                              boolean physique, double pvAttaquantPct) {
        PokemonType type = capacite.getType();
        if (physique && bruts.contains("gorillatactics")) return -1;
        if (!physique && bruts.contains("solarpower")) return -1;
        if (bruts.contains("megalauncher") || bruts.contains("steelworker") || bruts.contains("steelyspirit")
                || bruts.contains("flashfire") || bruts.contains("electromorphosis") || bruts.contains("windpower")
                || bruts.contains("liquidvoice")) return -1;
        if (type == PokemonType.NORMAL && (bruts.contains("aerilate") || bruts.contains("pixilate")
                || bruts.contains("refrigerate") || bruts.contains("galvanize") || bruts.contains("normalize"))) return -1;
        // Engrais / Brasier / Torrent / Essaim : x1.5 sous 1/3 des PV (non modélisés).
        if (pvAttaquantPct <= 34.0 && ((type == PokemonType.PLANTE && bruts.contains("overgrow"))
                || (type == PokemonType.FEU && bruts.contains("blaze"))
                || (type == PokemonType.EAU && bruts.contains("torrent"))
                || (type == PokemonType.INSECTE && bruts.contains("swarm")))) return -1;
        double s = 1.2;
        if (bruts.contains("magicguard") || bruts.contains("sheerforce")) s = Math.max(s, 1.3);
        if (bruts.contains("protosynthesis") || bruts.contains("quarkdrive")) s = Math.max(s, 1.3);
        if (bruts.contains("punkrock")) s = Math.max(s, 1.3);
        if (bruts.contains("analytic")) s = Math.max(s, 1.3);
        if (bruts.contains("rivalry") || bruts.contains("neuroforce")) s = Math.max(s, 1.25);
        if (bruts.contains("orichalcumpulse") || bruts.contains("hadronengine")) s = Math.max(s, 1.34);
        return s;
    }

    private static int vitesseEffectiveJoueur(Pokemon joueur) {
        double v = joueur.getStatCalculee(Stat.VITESSE);
        int stage = BoostTracker.getStageJoueur(Stat.VITESSE);
        if (stage >= 0) v = v * (2.0 + stage) / 2.0;
        else v = v * 2.0 / (2.0 - stage);
        if ("Mouchoir Choix".equals(joueur.getObjet())) v *= 1.5;
        if (joueur.getStatut() == Pokemon.Statut.PARALYSIE) v *= 0.5;
        if (FieldTracker.isTailwindJoueur()) v *= 2.0;
        return (int) Math.floor(v);
    }

    /** Vitesse minimale observée pour une espèce adverse (0 si aucune observation). */
    // K.O. par camp, comptés depuis cobblemon.battle.fainted (une espèce = un
    // K.O. : la clause d'espèce interdit les doublons dans une équipe).
    private static final Set<String> KO_ADVERSAIRE = new HashSet<>();
    private static final Set<String> KO_JOUEUR = new HashSet<>();
    public static int getNombreKoAdversaire() { return KO_ADVERSAIRE.size(); }
    public static int getNombreKoJoueur() { return KO_JOUEUR.size(); }

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

    public static Pokemon construireAdversaireEstime(Pokemon adversaireBase) {
        String espece = adversaireBase.getEspece();
        ProfilAdversaire profil = PROFILS.get(espece);
        SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(espece);
        boolean objetRetire = OBJETS_RETIRES.contains(espece);
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
            if (!objetRetire && objetConfirme == null && !smogon.topItemsShowdownId().isEmpty()) {
                String fr = ShowdownIdMapper.objet(smogon.topItemsShowdownId().get(0));
                if (fr != null) b.objet(fr);
            }
            if (!smogon.topAbilitiesShowdownId().isEmpty()) {
                String fr = ShowdownIdMapper.talent(smogon.topAbilitiesShowdownId().get(0));
                if (fr != null) b.talent(fr);
            }
        }

        // Une seule observation suffit à corriger : mieux vaut une estimation
        // calibrée sur le réel qu'un set Smogon démenti par les faits
        if (profil != null && profil.getNbObservations() >= 3) {
            // Seuil remonté à 3 : le moteur de correction rapide est en pause,
            // on revient à la prudence d'origine pour les hypothèses d'EV
            appliquerHypothese(b, Stat.ATTAQUE, profil.attaque);
            appliquerHypothese(b, Stat.ATTAQUE_SPE, profil.attaqueSpe);
            appliquerHypothese(b, Stat.DEFENSE, profil.defense);
            appliquerHypothese(b, Stat.DEFENSE_SPE, profil.defenseSpe);

            if (!objetRetire && objetConfirme == null) {
                String objetEstime = extraireObjetUnique(profil.attaque);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.attaqueSpe);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defense);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defenseSpe);
                if (objetEstime != null) {
                    b.objet(objetEstime);
                    // Rendu visible ("Objet confirmé"), pas juste appliqué
                    // silencieusement au calcul : le narrowing a déjà éliminé
                    // tous les autres candidats testés, même niveau de
                    // confiance que le calcul qui s'en sert depuis toujours.
                    OBJETS_CONFIRMES.put(espece, objetEstime);
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
            if (scout.objet != null && objetConfirme == null && !objetRetire) b.objet(scout.objet);
            if (scout.talent != null) b.talent(scout.talent);
        }

        if (objetConfirme != null && !objetRetire) {
            b.objet(objetConfirme);
        }

        String talentConfirme = TALENTS_CONFIRMES.get(espece);
        if (talentConfirme != null) {
            b.talent(talentConfirme);
        }

        if (objetRetire) {
            b.objet(null);
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
            String baie = BAIES_RESISTANCE.get(capacite.getType());
            if (baie != null) OBJETS_RETIRES.add(adversaire.getEspece());
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
     * Révoque TOUTE confirmation d'objet Choix contredite par les faits.
     *
     * Deux capacités différentes du même Pokémon sans switch entre-temps est
     * une preuve certaine qu'il ne tient pas d'objet Choix : le verrou impose
     * la première capacité utilisée depuis l'entrée sur le terrain.
     *
     * Ne se limitait au départ qu'à OBJETS_CONFIRMES_PROACTIVEMENT (le taux
     * d'usage Smogon), laissant intacte une confirmation de
     * tenterConfirmerEcharpeChoix (fondée sur la vitesse) - or c'est
     * exactement celle qui peut se tromper si une hypothèse de vitesse manque
     * au modèle (terrain de course, boost adverse non pris en compte, etc.).
     * Une preuve certaine doit l'emporter sur n'importe quelle estimation,
     * quelle qu'en soit la source. Trouvé en portant un fix similaire depuis
     * randompvp (faux positif réel sur un Pelipper ayant enchaîné Vent Violent
     * puis Balle Météo).
     */
    private static void tenterRevoquerObjetChoix(String espece, String coupActuelId) {
        String objet = OBJETS_CONFIRMES.get(espece);
        boolean estObjetChoix = "Mouchoir Choix".equals(objet)
            || "Bandeau Choix".equals(objet) || "Lunettes Choix".equals(objet);
        if (!estObjetChoix) return;
        if (coupAdversaireTourPrecedent == null || coupActuelId == null) return;
        if (!coupAdversaireTourPrecedent.equals(coupActuelId)) {
            OBJETS_CONFIRMES.remove(espece);
            OBJETS_CONFIRMES_PROACTIVEMENT.remove(espece);
            OBJETS_CHOIX_EXCLUS.add(espece);
        }
    }

    private static void tenterConfirmerEvoluroc(String espece, SmogonDataLoader.SmogonPokemonData smogon) {
        if (smogon == null || smogon.topItemsShowdownId().isEmpty()) return;
        if (OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece)) return;
        String topObjet = ShowdownIdMapper.objet(smogon.topItemsShowdownId().get(0));
        if ("Évoluroc".equals(topObjet) && smogon.topItemUsageFraction() >= 0.50) {
            OBJETS_CONFIRMES.put(espece, "Évoluroc");
        }
    }

    private static String extraireObjetUnique(StatHypothesis hyp) {
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
        LinkedHashSet<String> ensemble = COUPS_ADVERSAIRE.computeIfAbsent(espece, k -> new LinkedHashSet<>());
        if (ensemble.contains(capaciteId)) return;
        if (ensemble.size() >= 4) {
            if (!estObservationReelle) return;
            ensemble.remove(ensemble.iterator().next());
        }
        ensemble.add(capaciteId);
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

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) {
            reinitialiser();
            return;
        }

        // Détection Restes : les PV adverses remontent de ~1/16 depuis leur point bas.
        // Contrairement au delta net par tour, ceci voit le soin même si le joueur
        // a infligé des dégâts le même tour.
        Pokemon adv = BattleStateTracker.getAdversaireActif();
        if (adv == null) return;
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
        if (remontee >= 4.5 && remontee <= 8.0
                && adv.getStatut() != Pokemon.Statut.POISON
                && adv.getStatut() != Pokemon.Statut.POISON_GRAVE
                && !OBJETS_RETIRES.contains(adv.getEspece())
                && (coupAdversaireDuTour == null
                    || !COUPS_SOIN_OU_DRAIN.contains(coupAdversaireDuTour.showdownId()))
                && !"wish".equals(coupAdversaireTourPrecedent)
                && FieldTracker.construireField().getTerrain() != Field.TypeTerrain.HERBU) {
            OBJETS_CONFIRMES.put(adv.getEspece(), "Restes");
            pvPlancherAdv = pvNow;
        } else if (remontee >= 1.5 && pvNow >= 99.5
                && adv.getStatut() == Pokemon.Statut.AUCUN
                && !OBJETS_RETIRES.contains(adv.getEspece())
                && (coupAdversaireDuTour == null
                    || !COUPS_SOIN_OU_DRAIN.contains(coupAdversaireDuTour.showdownId()))
                && !"wish".equals(coupAdversaireTourPrecedent)
                && FieldTracker.construireField().getTerrain() != Field.TypeTerrain.HERBU) {
            // Soin plafonné par les PV max (ex: Restes à 97% ne rendent que 3%) :
            // une petite remontée qui termine pile à 100% sans capacité de soin
            // ne s'explique que par un objet de soin passif
            OBJETS_CONFIRMES.put(adv.getEspece(), "Restes");
            pvPlancherAdv = pvNow;
        } else if (remontee >= 10.5 && remontee <= 14.0
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
            // Baie Sitrus (ou équivalente) : restaure 1/4 des PV max, se
            // déclenche sous 50% PV. Contrairement à Restes, l'objet est
            // CONSOMMÉ - important pour Sabotage (Knock Off), qui ne doit
            // plus appliquer son bonus x1.5 une fois la baie mangée.
            OBJETS_RETIRES.add(adv.getEspece());
            pvPlancherAdv = pvNow;
        } else if (remontee > 8.0) {
            // Gros soin (Vœu, Soin, drain...) : repartir de ce niveau
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
        if (espece == null || objetFr == null) return;
        OBJETS_CONFIRMES.put(espece, objetFr);
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

        suivreCoupRecu(cle, contenu.getArgs());

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
            Pokemon adv = BattleStateTracker.getAdversaireActif();
            if (adv == null) return;
            OBJETS_CONFIRMES.put(adv.getEspece(), "Orbe Vie");
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
                        OBJETS_CONFIRMES.put(adv.getEspece(), "Ballon");
                    }
                }
            } catch (Exception ignored) {
            }
            return;
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
            String texteObjet = String.valueOf(args[1]);
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
            }
            if (Boolean.TRUE.equals(voleurEstAdversaire) && objetFr != null) {
                OBJETS_CONFIRMES.put(adv.getEspece(), objetFr);
                OBJETS_CHOIX_EXCLUS.remove(adv.getEspece());
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
        }
    }

    /** Vrai si l'objet de cette espèce est un fait observé (soin vu, ou retiré par Sabotage). */
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
        try {
            Boolean estAdversaire = determinerAttaquant(proprietaire);
            if (!Boolean.TRUE.equals(estAdversaire)) return;
            Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            if (adversaire == null) return;
            String talentFr = ShowdownIdMapper.talent(talentAnglais);
            if (talentFr != null) {
                TALENTS_CONFIRMES.put(adversaire.getEspece(), talentFr);
                if ("Épine de Fer".equals(talentFr) || "Peau Dure".equals(talentFr)) {
                    TALENTS_CHIP_CONFIRMES.add(adversaire.getEspece());
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
            if (objetFr != null) OBJETS_CONFIRMES.put(adversaire.getEspece(), objetFr);
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
    }

    /**
     * À appliquer sur tout Pokemon du joueur avant affichage (CalcOverlay ET
     * SwitchOverlayRenderer) : si un changement d'objet a été détecté pour
     * cette espèce et que Cobblemon n'a pas encore corrigé lui-même son
     * propre objet rapporté, force la vraie valeur connue à la place.
     */
    public static void appliquerObjetReelJoueur(Pokemon p) {
        if (p == null) return;
        String connu = OBJET_REEL_JOUEUR_CONNU.get(p.getEspece());
        if (connu == null) return;
        p.setObjet(AUCUN_OBJET.equals(connu) ? null : connu);
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

    public static void reinitialiser() {
        combatSauvageDetecte = false;
        OBJET_REEL_JOUEUR_CONNU.clear();
        OBJET_STACK_JOUEUR.clear();
        COMPTEUR_REPOS.clear();
        KO_ADVERSAIRE.clear();
        KO_JOUEUR.clear();
        coupRecu = null;
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
            especes.addAll(TALENTS_CONFIRMES.keySet());
            especes.addAll(TALENTS_CHIP_CONFIRMES);
            especes.addAll(COUPS_ADVERSAIRE.keySet());
            for (String esp : especes) {
                ScoutingStore.enregistrer(nomAdversaireCourant, esp,
                    OBJETS_CONFIRMES.get(esp),
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
        PROFILS.clear();
        COUPS_ADVERSAIRE.clear();
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
        espaceAdversaireDuTour = null;
    }

    public static Set<String> getTalentsReelsEspece(Pokemon adversaire) {
        Species espece = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(adversaire.getEspece());
        if (espece == null) return null;
        Set<String> r = new HashSet<>();
        for (var p : espece.getAbilities()) {
            String fr = ShowdownIdMapper.talent(p.getTemplate().getName());
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
