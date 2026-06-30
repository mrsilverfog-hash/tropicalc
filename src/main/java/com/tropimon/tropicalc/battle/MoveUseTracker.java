package com.tropimon.tropicalc.battle;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Détecte quel coup vient d'être utilisé en combat, et par qui (via le nom
 * du dresseur propriétaire), en lisant directement les clés de traduction
 * structurées plutôt que le texte français déjà traduit. Alimenté par
 * BattleMessagePacketMixin, qui intercepte le décodage réseau du paquet,
 * en amont de tout gestionnaire d'affichage (donc indépendant des mods
 * qui remplacent l'UI de combat).
 */
public final class MoveUseTracker {

    private MoveUseTracker() {
    }

    private static final String CLE_PREFIXE_COUP = "cobblemon.move.";
    private static final String CLE_UTILISE_COUP = "cobblemon.battle.used_move";
    private static final String CLE_UTILISE_COUP_SUR = "cobblemon.battle.used_move_on";
    private static final String CLE_PROPRIETAIRE = "cobblemon.battle.owned_pokemon";

    /** Représente un coup détecté : son identifiant Showdown et le nom du dresseur propriétaire (peut être null si Pokémon sauvage). */
    public record CoupDetecte(String showdownId, String proprietaire, long timestampMs) {
    }

    private static volatile CoupDetecte dernierCoup = null;

    private static final long FRAICHEUR_MS = 5000L;

    public static void traiterMessage(Text message) {
        if (message == null) {
            return;
        }
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) {
            return;
        }
        String cle = contenu.getKey();
        if (!CLE_UTILISE_COUP.equals(cle) && !CLE_UTILISE_COUP_SUR.equals(cle)) {
            return;
        }

        String proprietaire = null;
        String coupId = null;

        for (Object arg : contenu.getArgs()) {
            if (!(arg instanceof Text texteArg) || !(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) {
                continue;
            }
            String sousCle = sousContenu.getKey();
            if (sousCle == null) {
                continue;
            }
            if (sousCle.startsWith(CLE_PREFIXE_COUP)) {
                coupId = sousCle.substring(CLE_PREFIXE_COUP.length());
            } else if (CLE_PROPRIETAIRE.equals(sousCle)) {
                Object[] sousArgs = sousContenu.getArgs();
                if (sousArgs.length > 0 && sousArgs[0] instanceof String nom) {
                    proprietaire = nom;
                }
            }
        }

        if (coupId != null) {
            dernierCoup = new CoupDetecte(coupId, proprietaire, System.currentTimeMillis());
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[TropiCalc-diag] Coup détecté : {} (propriétaire={})", coupId, proprietaire);
        }
    }

    /** Renvoie le dernier coup détecté s'il est encore "frais", sinon null. Ne le consomme pas. */
    public static CoupDetecte getDernierCoupRecent() {
        CoupDetecte c = dernierCoup;
        if (c == null) {
            return null;
        }
        if (System.currentTimeMillis() - c.timestampMs() > FRAICHEUR_MS) {
            return null;
        }
        return c;
    }

    public static void consommer() {
        dernierCoup = null;
    }
}
