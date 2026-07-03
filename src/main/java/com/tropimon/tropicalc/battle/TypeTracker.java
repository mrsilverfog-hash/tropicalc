package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Suit les changements de type en combat (Détrempage, Protéen, Libéro, Halloween...)
 * via "cobblemon.battle.start.typechange" et "cobblemon.battle.start.typeadd".
 * L'override est annulé au switch du camp concerné.
 */
public final class TypeTracker {

    private TypeTracker() {
    }

    private static PokemonType joueurType1 = null;
    private static PokemonType joueurType2 = null;
    private static boolean joueurModifie = false;

    private static PokemonType adversaireType1 = null;
    private static PokemonType adversaireType2 = null;
    private static boolean adversaireModifie = false;

    // Identité des Pokémon au moment du changement (pour reset au switch)
    private static String especeJoueurAuChangement = null;
    private static String especeAdversaireAuChangement = null;

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

        boolean typechange = "cobblemon.battle.start.typechange".equals(cle);
        boolean typeadd = "cobblemon.battle.start.typeadd".equals(cle);
        if (!typechange && !typeadd) return;

        Boolean estJoueur = null;
        PokemonType type = null;

        for (Object arg : contenu.getArgs()) {
            if (!(arg instanceof Text texteArg)) continue;
            if (!(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) continue;
            String sousCle = sousContenu.getKey();
            if (sousCle == null) continue;

            if ("cobblemon.battle.owned_pokemon".equals(sousCle)) {
                Object[] sousArgs = sousContenu.getArgs();
                if (sousArgs.length > 0 && sousArgs[0] instanceof String nom) {
                    var joueurMc = MinecraftClient.getInstance().player;
                    estJoueur = joueurMc != null && nom.equalsIgnoreCase(joueurMc.getGameProfile().getName());
                }
            } else if (sousCle.startsWith("cobblemon.type.")) {
                String typeId = sousCle.substring("cobblemon.type.".length());
                type = ShowdownIdMapper.type(typeId);
            }
        }

        if (type == null) return;
        boolean joueur = Boolean.TRUE.equals(estJoueur);

        if (typechange) {
            // Type entièrement remplacé (Détrempage, Protéen, Libéro)
            if (joueur) {
                joueurType1 = type;
                joueurType2 = null;
                joueurModifie = true;
                especeJoueurAuChangement = especeActiveJoueur();
            } else {
                adversaireType1 = type;
                adversaireType2 = null;
                adversaireModifie = true;
                especeAdversaireAuChangement = especeActiveAdversaire();
            }
        } else {
            // typeadd : type ajouté (Halloween, Malédiction Sylvestre)
            if (joueur) {
                if (!joueurModifie) {
                    com.tropimon.tropicalc.calc.Pokemon actif = BattleStateTracker.getJoueurActifDepuisEquipe();
                    if (actif != null) {
                        joueurType1 = actif.getType1();
                        joueurType2 = type;
                        joueurModifie = true;
                        especeJoueurAuChangement = especeActiveJoueur();
                    }
                } else {
                    joueurType2 = type;
                }
            } else {
                if (!adversaireModifie) {
                    com.tropimon.tropicalc.calc.Pokemon actif = BattleStateTracker.getAdversaireActif();
                    if (actif != null) {
                        adversaireType1 = actif.getType1();
                        adversaireType2 = type;
                        adversaireModifie = true;
                        especeAdversaireAuChangement = especeActiveAdversaire();
                    }
                } else {
                    adversaireType2 = type;
                }
            }
        }
    }

    /** Applique les overrides si toujours valides (mêmes Pokémon actifs), sinon reset. */
    public static void appliquer(com.tropimon.tropicalc.calc.Pokemon joueur,
                                  com.tropimon.tropicalc.calc.Pokemon adversaire) {
        if (joueurModifie) {
            if (especeJoueurAuChangement != null && especeJoueurAuChangement.equals(joueur.getEspece())) {
                joueur.setTypesOverride(joueurType1, joueurType2);
            } else {
                reinitialiserJoueur();
            }
        }
        if (adversaireModifie) {
            if (especeAdversaireAuChangement != null && especeAdversaireAuChangement.equals(adversaire.getEspece())) {
                adversaire.setTypesOverride(adversaireType1, adversaireType2);
            } else {
                reinitialiserAdversaire();
            }
        }
    }

    private static String especeActiveJoueur() {
        com.tropimon.tropicalc.calc.Pokemon p = BattleStateTracker.getJoueurActifDepuisEquipe();
        return p != null ? p.getEspece() : null;
    }

    private static String especeActiveAdversaire() {
        com.tropimon.tropicalc.calc.Pokemon p = BattleStateTracker.getAdversaireActif();
        return p != null ? p.getEspece() : null;
    }

    public static void reinitialiserJoueur() {
        joueurType1 = null;
        joueurType2 = null;
        joueurModifie = false;
        especeJoueurAuChangement = null;
    }

    public static void reinitialiserAdversaire() {
        adversaireType1 = null;
        adversaireType2 = null;
        adversaireModifie = false;
        especeAdversaireAuChangement = null;
    }

    public static void reinitialiser() {
        reinitialiserJoueur();
        reinitialiserAdversaire();
    }
}
