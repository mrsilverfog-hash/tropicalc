package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Species;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Nature;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ProfilAdversaire;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.SmogonDataLoader;
import com.tropimon.tropicalc.calc.Stat;
import com.tropimon.tropicalc.calc.StatHypothesis;
import net.minecraft.client.MinecraftClient;

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

    // PP consommés par l'adversaire : espèce -> (id capacité -> PP utilisés)
    private static final Map<String, Map<String, Integer>> PP_UTILISES = new HashMap<>();
    private static final Set<String> OBJETS_RETIRES = new HashSet<>();

    // Objets confirmés par observation (ex: soin de fin de tour ~1/16 => Restes)
    private static final Map<String, String> OBJETS_CONFIRMES = new HashMap<>();
    private static String coupAdversaireTourPrecedent = null;

    // Capacités qui soignent leur utilisateur : excluent la confirmation de Restes
    private static final Set<String> COUPS_SOIN_OU_DRAIN = Set.of(
        "recover", "roost", "softboiled", "slackoff", "milkdrink", "moonlight",
        "morningsun", "synthesis", "shoreup", "rest", "wish", "healorder",
        "strengthsap", "junglehealing", "lifedew", "floralhealing",
        "absorb", "megadrain", "gigadrain", "leechlife", "drainpunch",
        "hornleech", "drainingkiss", "paraboliccharge", "oblivionwing",
        "dreameater", "bitterblade", "leechseed", "painsplit");
    // Vitesse minimale observée par espèce (déduite de l'ordre d'action)
    private static final Map<String, Integer> VITESSES_MIN_OBSERVEES = new HashMap<>();
    private static final double TOLERANCE_POURCENT = 3.0;

    // Coups à priorité augmentée : l'ordre d'action ne reflète pas la vitesse
    private static final Set<String> COUPS_PRIORITAIRES = Set.of(
        "quickattack", "extremespeed", "aquajet", "bulletpunch", "machpunch",
        "iceshard", "shadowsneak", "suckerpunch", "accelerock", "vacuumwave",
        "jetpunch", "grassyglide", "firstimpression", "fakeout", "feint",
        "raging bolt", "thunderclap", "upperhand", "protect", "detect",
        "banefulbunker", "silktrap", "burningbulwark", "spikyshield", "kingsshield",
        "obstruct", "endure", "trickroom"
    );

    private static double pvJoueurDebutTour = -1;
    private static double pvAdversaireDebutTour = -1;
    private static MoveUseTracker.CoupDetecte coupJoueurDuTour = null;
    private static MoveUseTracker.CoupDetecte coupAdversaireDuTour = null;
    private static Boolean adversaireAAgiEnPremier = null;
    private static String espaceAdversaireDuTour = null;

    public static synchronized void signalerNouveauTour() {
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

        // Abris consécutifs de l'adversaire (le 2e n'a que ~33% de réussite)
        if (coupAdversaireDuTour != null) {
            if (COUPS_PROTECTION.contains(coupAdversaireDuTour.showdownId())) {
                compteurAbrisAdversaire++;
            } else {
                compteurAbrisAdversaire = 0;
            }
        }

        FieldTracker.nouveauTour();

        // Compteurs Toxik : +1 par tour passé empoisonné gravement (reset au switch/soin)
        if (joueur.getStatut() == Pokemon.Statut.POISON_GRAVE) compteurToxikJoueur++;
        else compteurToxikJoueur = 0;
        if (adversaire.getStatut() == Pokemon.Statut.POISON_GRAVE) compteurToxikAdversaire++;
        else compteurToxikAdversaire = 0;

        if (pvJoueurDebutTour >= 0 && pvAdversaireDebutTour >= 0) {
            double perteJoueur = pvJoueurDebutTour - pvJoueurMaintenant;
            double perteAdversaire = pvAdversaireDebutTour - pvAdversaireMaintenant;

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
                adversaireAAgiEnPremier = null;
                return;
            }

            if (coupJoueurDuTour != null && "knockoff".equals(coupJoueurDuTour.showdownId())
                    && perteAdversaire >= 0.5) {
                OBJETS_RETIRES.add(adversaire.getEspece());
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

            // Inférence de vitesse : l'adversaire a agi en premier avec des coups non prioritaires
            if (Boolean.TRUE.equals(adversaireAAgiEnPremier)
                    && coupJoueurDuTour != null && coupAdversaireDuTour != null
                    && !COUPS_PRIORITAIRES.contains(coupJoueurDuTour.showdownId())
                    && !COUPS_PRIORITAIRES.contains(coupAdversaireDuTour.showdownId())
                    && !FieldTracker.isDistorsion()) {
                int vitesseJoueur = vitesseEffectiveJoueur(joueur);
                VITESSES_MIN_OBSERVEES.merge(adversaire.getEspece(), vitesseJoueur + 1, Math::max);
            }

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
        adversaireAAgiEnPremier = null;
    }

    public static synchronized void signalerCoupUtilise(MoveUseTracker.CoupDetecte coup) {
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
                COUPS_ADVERSAIRE
                    .computeIfAbsent(adversaire.getEspece(), k -> new LinkedHashSet<>())
                    .add(coup.showdownId());

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
        }
    }

    private static int vitesseEffectiveJoueur(Pokemon joueur) {
        double v = joueur.getStatCalculee(Stat.VITESSE);
        int stage = BoostTracker.getStageJoueur(Stat.VITESSE);
        if (stage >= 0) v = v * (2.0 + stage) / 2.0;
        else v = v * 2.0 / (2.0 - stage);
        if ("Écharpe Choix".equals(joueur.getObjet())) v *= 1.5;
        if (joueur.getStatut() == Pokemon.Statut.PARALYSIE) v *= 0.5;
        return (int) Math.floor(v);
    }

    /** Vitesse minimale observée pour une espèce adverse (0 si aucune observation). */
    public static int getVitesseMinObservee(String espece) {
        return VITESSES_MIN_OBSERVEES.getOrDefault(espece, 0);
    }

    private static void enregistrerObservation(boolean adversaireEtaitAttaquant, double perte,
                                                Pokemon adversaire, Pokemon joueur,
                                                MoveUseTracker.CoupDetecte coup) {
        MoveTemplate template = Moves.INSTANCE.getByName(coup.showdownId());
        if (template == null) return;
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.estCapaciteDeStatut()) return;

        ProfilAdversaire profil = PROFILS.computeIfAbsent(adversaire.getEspece(), k -> {
            Set<String> talentsReels = getTalentsReelsEspece(adversaire);
            SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(adversaire.getEspece());
            return new ProfilAdversaire(talentsReels, smogon);
        });

        Field terrainNeutre = new Field();
        double observeMin = Math.max(0, perte - TOLERANCE_POURCENT);
        double observeMax = perte + TOLERANCE_POURCENT;
        profil.enregistrerObservation(adversaireEtaitAttaquant, adversaire, joueur, capacite, terrainNeutre,
            observeMin, observeMax);
    }

    public static Pokemon construireAdversaireEstime(Pokemon adversaireBase) {
        String espece = adversaireBase.getEspece();
        ProfilAdversaire profil = PROFILS.get(espece);
        SmogonDataLoader.SmogonPokemonData smogon = SmogonDataLoader.getDonnees(espece);
        boolean objetRetire = OBJETS_RETIRES.contains(espece);

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
                COUPS_ADVERSAIRE
                    .computeIfAbsent(espece, k -> new LinkedHashSet<>())
                    .addAll(scout.capacites);
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

        if (profil != null && profil.getNbObservations() >= 3) {
            appliquerHypothese(b, Stat.ATTAQUE, profil.attaque);
            appliquerHypothese(b, Stat.ATTAQUE_SPE, profil.attaqueSpe);
            appliquerHypothese(b, Stat.DEFENSE, profil.defense);
            appliquerHypothese(b, Stat.DEFENSE_SPE, profil.defenseSpe);

            if (!objetRetire && objetConfirme == null) {
                String objetEstime = extraireObjetUnique(profil.attaque);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.attaqueSpe);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defense);
                if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defenseSpe);
                if (objetEstime != null) b.objet(objetEstime);
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

        Pokemon p = b.build();

        double fractionPv = adversaireBase.getPvMax() > 0
            ? (double) adversaireBase.getPvActuels() / adversaireBase.getPvMax() : 1.0;
        p.setPvActuels((int) Math.round(fractionPv * p.getPvMax()));
        p.setStatut(adversaireBase.getStatut());

        for (Stat s : Stat.values()) {
            if (s != Stat.PV) {
                p.setStage(s, adversaireBase.getStage(s));
            }
        }

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
        } else if (remontee > 8.0) {
            // Gros soin (Vœu, Soin, drain...) : repartir de ce niveau
            pvPlancherAdv = pvNow;
        }
    }

    private static Boolean determinerAttaquant(String proprietaire) {
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

    /** Vrai si l'objet de cette espèce est un fait observé (soin vu, ou retiré par Sabotage). */
    public static boolean estObjetConfirme(String espece) {
        return OBJETS_CONFIRMES.containsKey(espece) || OBJETS_RETIRES.contains(espece);
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

    public static boolean isJoueurSalaison() { return joueurSalaison; }
    public static boolean isJoueurVampigraine() { return joueurVampigraine; }
    public static boolean isAdversaireSalaison() { return adversaireSalaison; }
    public static boolean isAdversaireVampigraine() { return adversaireVampigraine; }
    /** Multiplicateur Toxik du PROCHAIN tour pour le joueur (1 si pas encore subi). */
    public static int getCompteurToxikProchainJoueur() { return compteurToxikJoueur + 1; }
    public static int getCompteurToxikProchainAdversaire() { return compteurToxikAdversaire + 1; }

    /** L'adversaire n'a pas attaqué ce tour (aucun coup, ou un coup de statut). */
    private static boolean adversaireNAPasAttaque() {
        if (coupAdversaireDuTour == null) return true;
        MoveTemplate t = Moves.INSTANCE.getByName(coupAdversaireDuTour.showdownId());
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
        especeJoueurSuivie = null;
        OBJETS_RETIRES.clear();
        VITESSES_MIN_OBSERVEES.clear();
        BoostTracker.reinitialiser();
        FieldTracker.reinitialiser();
        pvJoueurDebutTour = -1;
        pvAdversaireDebutTour = -1;
        coupJoueurDuTour = null;
        coupAdversaireDuTour = null;
        adversaireAAgiEnPremier = null;
        espaceAdversaireDuTour = null;
    }

    private static Set<String> getTalentsReelsEspece(Pokemon adversaire) {
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
            .build();
    }
}
