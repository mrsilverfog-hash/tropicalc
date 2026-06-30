package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.TropiCalcClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Détecte quel coup vient d'être utilisé en combat, en lisant directement
 * la clé de traduction du nom de capacité ("cobblemon.move.scaleshot")
 * plutôt que le texte français déjà traduit. Alimenté par
 * BattleMessageHandlerMixin, qui intercepte les messages de combat de
 * Cobblemon en lecture seule avant qu'ils ne soient affichés.
 *
 * DIAGNOSTIC TEMPORAIRE : log chaque clé de traduction de niveau racine
 * reçue, pour vérifier nos hypothèses sur les noms de clés Cobblemon.
 * À retirer une fois confirmé.
 */
public final class MoveUseTracker {

    private MoveUseTracker() {
    }

    private static final String CLE_PREFIXE_COUP = "cobblemon.move.";
    private static final String CLE_UTILISE_COUP = "cobblemon.battle.used_move";
    private static final String CLE_UTILISE_COUP_SUR = "cobblemon.battle.used_move_on";

    private static volatile String dernierCoupShowdownId = null;
    private static volatile long dernierCoupTimestampMs = 0L;

    private static final long FRAICHEUR_MS = 3000L;

    public static void traiterMessage(Text message) {
        if (message == null) {
            return;
        }
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) {
            TropiCalcClient.LOGGER.info("[TropiCalc-diag] Message reçu sans TranslatableTextContent : classe={}",
                message.getContent().getClass().getName());
            return;
        }
        String cle = contenu.getKey();
        TropiCalcClient.LOGGER.info("[TropiCalc-diag] Clé racine reçue : {}", cle);

        if (!CLE_UTILISE_COUP.equals(cle) && !CLE_UTILISE_COUP_SUR.equals(cle)) {
            return;
        }
        for (Object arg : contenu.getArgs()) {
            TropiCalcClient.LOGGER.info("[TropiCalc-diag] Argument du message coup : type={} valeur={}",
                arg == null ? "null" : arg.getClass().getName(), arg);
            if (arg instanceof Text texteArg && texteArg.getContent() instanceof TranslatableTextContent sousContenu) {
                String sousCle = sousContenu.getKey();
                TropiCalcClient.LOGGER.info("[TropiCalc-diag] Sous-clé trouvée : {}", sousCle);
                if (sousCle != null && sousCle.startsWith(CLE_PREFIXE_COUP)) {
                    dernierCoupShowdownId = sousCle.substring(CLE_PREFIXE_COUP.length());
                    dernierCoupTimestampMs = System.currentTimeMillis();
                    TropiCalcClient.LOGGER.info("[TropiCalc-diag] Coup détecté avec succès : {}", dernierCoupShowdownId);
                    return;
                }
            }
        }
    }

    public static String getDernierCoupRecent() {
        if (dernierCoupShowdownId == null) {
            return null;
        }
        if (System.currentTimeMillis() - dernierCoupTimestampMs > FRAICHEUR_MS) {
            return null;
        }
        return dernierCoupShowdownId;
    }

    public static void consommer() {
        dernierCoupShowdownId = null;
    }
}
