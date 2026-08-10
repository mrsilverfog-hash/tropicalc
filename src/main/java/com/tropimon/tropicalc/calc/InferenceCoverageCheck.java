package com.tropimon.tropicalc.calc;

import com.tropimon.tropicalc.TropiCalcClient;

import java.util.HashSet;
import java.util.Set;

/**
 * Garde-fou de démarrage : vérifie que tout talent/objet implémenté dans
 * AbilityModifier/ItemModifier est bien présent dans au moins une des listes
 * candidates de SetInferenceEngine. Sans ça, le moteur d'inférence ne peut
 * jamais converger vers ce talent/objet pour un Pokémon qui en a plusieurs
 * possibles, même si l'observation le confirmerait sans ambiguïté - bug
 * silencieux trouvé et corrigé une fois cette nuit sur 26 entrées à la fois.
 * Ce contrôle empêche que ça se reproduise sans qu'on s'en aperçoive.
 *
 * Ne fait QUE logger un avertissement (jamais de correction automatique,
 * jamais d'impact sur le calcul) - risque nul, juste un signal pour la
 * prochaine session si un futur ajout est de nouveau oublié.
 *
 * Limite connue : ne couvre QUE ce qui passe par le registre standard
 * (AbilityModifier.REGISTRE / ItemModifier.REGISTRE). Les talents/objets
 * testés "en dur" par égalité de string ailleurs dans DamageCalculator
 * (Robuste, Fantômasque, Ceinture Focus, Garde-Talent...) sont invisibles
 * à ce scan - ils ont été ajoutés manuellement aux listes cette nuit, mais
 * un futur ajout du même genre ne serait pas détecté automatiquement ici.
 */
public final class InferenceCoverageCheck {

    private InferenceCoverageCheck() {
    }

    // Talents implémentés mais LÉGITIMEMENT hors du scope de l'inférence de
    // dégâts (vitesse pure, mécaniques de statut/switch, effets hors combat
    // direct) - identifiés lors des audits de cette nuit, pas des oublis.
    private static final Set<String> TALENTS_HORS_SCOPE = Set.of(
        "Chlorophylle", "Glissade", "Baigne Sable", "Chasse-Neige", "Pied Véloce",
        "Farceur", "Soin Poison", "Multi-Coups"
    );

    public static void verifier() {
        Set<String> manquantsTalents = new HashSet<>();
        for (String talent : AbilityModifier.REGISTRE.keySet()) {
            if (TALENTS_HORS_SCOPE.contains(talent)) continue;
            if (!SetInferenceEngine.TALENTS_OFFENSIFS.contains(talent)
                    && !SetInferenceEngine.TALENTS_DEFENSIFS.contains(talent)) {
                manquantsTalents.add(talent);
            }
        }

        Set<String> manquantsObjets = new HashSet<>();
        for (String objet : ItemModifier.REGISTRE.keySet()) {
            if (!SetInferenceEngine.OBJETS_OFFENSIFS.contains(objet)
                    && !SetInferenceEngine.OBJETS_DEFENSIFS.contains(objet)) {
                manquantsObjets.add(objet);
            }
        }

        if (!manquantsTalents.isEmpty()) {
            TropiCalcClient.LOGGER.warn(
                "[TropiCalc] Talents implémentés mais absents des listes candidates "
                    + "d'inférence (SetInferenceEngine) : " + manquantsTalents
                    + " - l'inférence ne convergera jamais dessus pour un Pokémon "
                    + "ayant plusieurs talents possibles. À ajouter ou exclure explicitement.");
        }
        if (!manquantsObjets.isEmpty()) {
            TropiCalcClient.LOGGER.warn(
                "[TropiCalc] Objets implémentés mais absents des listes candidates "
                    + "d'inférence (SetInferenceEngine) : " + manquantsObjets
                    + " - même risque que pour les talents ci-dessus.");
        }
    }
}
