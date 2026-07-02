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
    private static final double TOLERANCE_POURCENT = 1.5;

    private static double pvJoueurDebutTour = -1;
    private static double pvAdversaireDebutTour = -1;
    private static MoveUseTracker.CoupDetecte coupDuTour = null;
    private static String espaceAdversaireDuTour = null;

    public static synchronized void signalerNouveauTour() {
        Pokemon joueur = BattleStateTracker.getJoueurActifDepuisEquipe();
        if (joueur == null) joueur = BattleStateTracker.getJoueurActif();
        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        if (joueur == null || adversaire == null) return;

        double pvJoueurMaintenant = joueur.getPourcentagePv();
        double pvAdversaireMaintenant = adversaire.getPourcentagePv();

        if (coupDuTour != null && pvJoueurDebutTour >= 0 && pvAdversaireDebutTour >= 0) {
            double perteJoueur = pvJoueurDebutTour - pvJoueurMaintenant;
            double perteAdversaire = pvAdversaireDebutTour - pvAdversaireMaintenant;

            if (perteAdversaire < -5.0 || perteJoueur < -5.0) {
                pvJoueurDebutTour = pvJoueurMaintenant;
                pvAdversaireDebutTour = pvAdversaireMaintenant;
                espaceAdversaireDuTour = adversaire.getEspece();
                coupDuTour = null;
                return;
            }

            Boolean adversaireEtaitAttaquant = determinerAttaquant(coupDuTour.proprietaire());
            if (adversaireEtaitAttaquant == null) {
                adversaireEtaitAttaquant = perteJoueur > perteAdversaire;
            }

            // Ajouter aux coups adverses à la fin du tour avec confirmation par dégâts
            if (adversaireEtaitAttaquant && perteJoueur >= 0.5) {
                MoveTemplate tmpl = Moves.INSTANCE.getByName(coupDuTour.showdownId());
                if (tmpl != null && tmpl.getPower() > 0) {
                    COUPS_ADVERSAIRE
                        .computeIfAbsent(adversaire.getEspece(), k -> new LinkedHashSet<>())
                        .add(coupDuTour.showdownId());
                }
            }

            double perte = adversaireEtaitAttaquant ? perteJoueur : perteAdversaire;
            if (perte >= 0.5) {
                enregistrerObservation(adversaireEtaitAttaquant, perte, adversaire, joueur);
            }
        }

        pvJoueurDebutTour = pvJoueurMaintenant;
        pvAdversaireDebutTour = pvAdversaireMaintenant;
        espaceAdversaireDuTour = adversaire.getEspece();
        coupDuTour = null;
    }

    public static synchronized void signalerCoupUtilise(MoveUseTracker.CoupDetecte coup) {
        coupDuTour = coup;

        // Ajouter immédiatement uniquement si le propriétaire est confirmé non-joueur
        Boolean estAdversaire = determinerAttaquant(coup.proprietaire());
        if (Boolean.TRUE.equals(estAdversaire)) {
            Pokemon adversaire = BattleStateTracker.getAdversaireActif();
            if (adversaire != null) {
                MoveTemplate tmpl = Moves.INSTANCE.getByName(coup.showdownId());
                if (tmpl != null && tmpl.getPower() > 0) {
                    COUPS_ADVERSAIRE
                        .computeIfAbsent(adversaire.getEspece(), k -> new LinkedHashSet<>())
                        .add(coup.showdownId());
                }
            }
        }
    }

    private static void enregistrerObservation(boolean adversaireEtaitAttaquant, double perte,
                                                Pokemon adversaire, Pokemon joueur) {
        if (coupDuTour == null) return;
        MoveTemplate template = Moves.INSTANCE.getByName(coupDuTour.showdownId());
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

        Pokemon.Builder b = Pokemon.builder(espece, adversaireBase.getNiveau(),
            adversaireBase.getType1(), adversaireBase.getType2());
        for (Stat s : Stat.values()) {
            b.statBase(s, adversaireBase.getStatBase(s));
        }

        if (smogon != null && !smogon.topSpreads().isEmpty()) {
            SmogonDataLoader.ParsedSpread top = smogon.topSpreads().get(0);
            b.ev(Stat.PV, top.hpEv());
            b.ev(Stat.ATTAQUE, top.atkEv());
            b.ev(Stat.DEFENSE, top.defEv());
            b.ev(Stat.ATTAQUE_SPE, top.spaEv());
            b.ev(Stat.DEFENSE_SPE, top.spdEv());
            b.ev(Stat.VITESSE, top.speEv());
            b.nature(ShowdownIdMapper.nature(top.natureShowdownId()));
            if (!smogon.topItemsShowdownId().isEmpty()) {
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

            String objetEstime = extraireObjetUnique(profil.attaque);
            if (objetEstime == null) objetEstime = extraireObjetUnique(profil.attaqueSpe);
            if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defense);
            if (objetEstime == null) objetEstime = extraireObjetUnique(profil.defenseSpe);
            if (objetEstime != null) b.objet(objetEstime);

            String talentEstime = extraireTalentUnique(profil.attaque);
            if (talentEstime == null) talentEstime = extraireTalentUnique(profil.attaqueSpe);
            if (talentEstime != null) b.talent(talentEstime);
        }

        Pokemon p = b.build();
        p.setPvActuels(p.getPvMax());
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

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) reinitialiser();
    }

    private static Boolean determinerAttaquant(String proprietaire) {
        if (proprietaire == null) return null;
        var joueurMc = MinecraftClient.getInstance().player;
        if (joueurMc == null) return null;
        return !proprietaire.equalsIgnoreCase(joueurMc.getGameProfile().getName());
    }

    public static ProfilAdversaire getProfil(String espece) { return PROFILS.get(espece); }
    public static String getEspaceAdversaireCourant() { return espaceAdversaireDuTour; }

    public static void reinitialiser() {
        PROFILS.clear();
        COUPS_ADVERSAIRE.clear();
        pvJoueurDebutTour = -1;
        pvAdversaireDebutTour = -1;
        coupDuTour = null;
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
