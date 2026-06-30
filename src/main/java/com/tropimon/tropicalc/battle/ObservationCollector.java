package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Species;
import com.tropimon.tropicalc.calc.Field;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ProfilAdversaire;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Détecte automatiquement les coups utilisés en combat (via MoveUseTracker)
 * et les dégâts qu'ils causent, pour resserrer le ProfilAdversaire courant.
 *
 * Architecture "pilotée par le coup" plutôt que par les PV : dès qu'un
 * nouveau coup est détecté, on photographie les %PV des deux côtés, puis on
 * revérifie un court instant après pour calculer la perte causée par CE
 * coup précis. Le nom du dresseur propriétaire permet de savoir avec
 * certitude si c'est le joueur ou l'adversaire qui a attaqué.
 *
 * À appeler une fois par frame (depuis l'overlay) via {@link #tick()}.
 */
public final class ObservationCollector {

    private ObservationCollector() {
    }

    private static final Map<String, ProfilAdversaire> PROFILS = new HashMap<>();

    private static final long DELAI_APRES_COUP_MS = 900L;
    private static final double TOLERANCE_POURCENT = 1.5;

    private static MoveUseTracker.CoupDetecte coupEnAttente = null;
    private static double pvJoueurAvant;
    private static double pvAdversaireAvant;

    public static void tick() {
        if (!BattleStateTracker.estEnCombat()) {
            reinitialiser();
            return;
        }

        Pokemon joueur = BattleStateTracker.getJoueurActif();
        Pokemon adversaire = BattleStateTracker.getAdversaireActif();
        if (joueur == null || adversaire == null) {
            return;
        }

        double pvJoueur = joueur.getPourcentagePv();
        double pvAdversaire = adversaire.getPourcentagePv();
        long maintenant = System.currentTimeMillis();

        MoveUseTracker.CoupDetecte coupActuel = MoveUseTracker.getDernierCoupRecent();

        boolean nouveauCoup = coupActuel != null
            && (coupEnAttente == null || coupActuel.timestampMs() != coupEnAttente.timestampMs());

        if (nouveauCoup) {
            if (coupEnAttente != null) {
                finaliser(coupEnAttente, adversaire, joueur, pvJoueur, pvAdversaire);
            }
            coupEnAttente = coupActuel;
            pvJoueurAvant = pvJoueur;
            pvAdversaireAvant = pvAdversaire;
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] Nouveau coup capturé : {} pvJoueurAvant={} pvAdversaireAvant={}",
                coupActuel.showdownId(), pvJoueurAvant, pvAdversaireAvant);
            return;
        }

        if (coupEnAttente != null && maintenant - coupEnAttente.timestampMs() >= DELAI_APRES_COUP_MS) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] Délai écoulé, finalisation de : {}", coupEnAttente.showdownId());
            finaliser(coupEnAttente, adversaire, joueur, pvJoueur, pvAdversaire);
            coupEnAttente = null;
        }
    }

    private static void finaliser(MoveUseTracker.CoupDetecte coup, Pokemon adversaire, Pokemon joueur,
                                   double pvJoueurApres, double pvAdversaireApres) {
        MoveTemplate template = Moves.INSTANCE.getByName(coup.showdownId());
        if (template == null) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] finaliser : template introuvable pour {}", coup.showdownId());
            return;
        }
        com.tropimon.tropicalc.calc.Move capacite = convertirCapacite(template);
        if (capacite == null || capacite.estCapaciteDeStatut()) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] finaliser : capacite null ou statut pour {}", coup.showdownId());
            return;
        }

        double perteJoueur = pvJoueurAvant - pvJoueurApres;
        double perteAdversaire = pvAdversaireAvant - pvAdversaireApres;

        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] finaliser : pvJoueurAvant={} pvJoueurApres={} pvAdvAvant={} pvAdvApres={} proprietaire={}",
            pvJoueurAvant, pvJoueurApres, pvAdversaireAvant, pvAdversaireApres, coup.proprietaire());

        Boolean adversaireEtaitAttaquant = determinerAttaquant(coup.proprietaire());

        if (adversaireEtaitAttaquant == null) {
            if (perteJoueur <= 0.5 && perteAdversaire <= 0.5) {
                com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                    "[TropiCalc-diag] finaliser : aucune perte significative, abandon");
                return;
            }
            adversaireEtaitAttaquant = perteJoueur > perteAdversaire;
        }

        double perte = adversaireEtaitAttaquant ? perteJoueur : perteAdversaire;
        if (perte < 0.5) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] finaliser : perte trop faible ({}), abandon", perte);
            return;
        }

        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] Observation finalisée : coup={} adversaireAttaquant={} perte={}",
            coup.showdownId(), adversaireEtaitAttaquant, perte);

        ProfilAdversaire profil = PROFILS.computeIfAbsent(adversaire.getEspece(), k -> {
            Set<String> talentsReels = getTalentsReelsEspece(adversaire);
            if (talentsReels == null) {
                Set<String> tous = new HashSet<>();
                tous.addAll(com.tropimon.tropicalc.calc.SetInferenceEngine.TALENTS_OFFENSIFS);
                tous.addAll(com.tropimon.tropicalc.calc.SetInferenceEngine.TALENTS_DEFENSIFS);
                talentsReels = tous;
            }
            return new ProfilAdversaire(talentsReels);
        });

        Field terrainNeutre = new Field();
        double observeMin = Math.max(0, perte - TOLERANCE_POURCENT);
        double observeMax = perte + TOLERANCE_POURCENT;

        profil.enregistrerObservation(adversaireEtaitAttaquant, adversaire, joueur, capacite, terrainNeutre,
            observeMin, observeMax);
    }

    private static Boolean determinerAttaquant(String proprietaire) {
        if (proprietaire == null) {
            return null;
        }
        var joueurMc = MinecraftClient.getInstance().player;
        if (joueurMc == null) {
            return null;
        }
        String nomJoueur = joueurMc.getGameProfile().getName();
        boolean estLeJoueur = proprietaire.equalsIgnoreCase(nomJoueur);
        return !estLeJoueur;
    }

    public static ProfilAdversaire getProfil(String espece) {
        return PROFILS.get(espece);
    }

    public static void reinitialiser() {
        PROFILS.clear();
        coupEnAttente = null;
    }

    private static Set<String> getTalentsReelsEspece(Pokemon adversaire) {
        Species espece = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(adversaire.getEspece());
        if (espece == null) {
            return null;
        }
        Set<String> resultat = new HashSet<>();
        for (var potentielle : espece.getAbilities()) {
            String nomShowdown = potentielle.getTemplate().getName();
            String nomFrancais = ShowdownIdMapper.talent(nomShowdown);
            if (nomFrancais != null) {
                resultat.add(nomFrancais);
            }
        }
        return resultat;
    }

    private static com.tropimon.tropicalc.calc.Move convertirCapacite(MoveTemplate template) {
        PokemonType type = ShowdownIdMapper.type(template.getElementalType().getName());
        if (type == null) {
            return null;
        }
        String categorieNom = template.getDamageCategory().getName();
        com.tropimon.tropicalc.calc.Move.Categorie categorie;
        if ("physical".equalsIgnoreCase(categorieNom)) {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.PHYSIQUE;
        } else if ("special".equalsIgnoreCase(categorieNom)) {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.SPECIALE;
        } else {
            categorie = com.tropimon.tropicalc.calc.Move.Categorie.STATUT;
        }

        return com.tropimon.tropicalc.calc.Move.builder(template.getName(), type, categorie)
            .puissance((int) template.getPower())
            .build();
    }
}
