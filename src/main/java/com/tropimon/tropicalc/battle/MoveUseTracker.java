package com.tropimon.tropicalc.battle;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Détecte quel coup vient d'être utilisé en combat, en lisant directement
 * la clé de traduction du nom de capacité ("cobblemon.move.scaleshot")
 * plutôt que le texte français déjà traduit. Alimenté par
 * BattleMessageHandlerMixin, qui intercepte les messages de combat de
 * Cobblemon en lecture seule avant qu'ils ne soient affichés.
 *
 * Limitation connue : ne distingue pas encore précisément quel Pokémon a
 * utilisé le coup (juste "un coup vient d'être utilisé très récemment").
 * En format Simple 1v1, l'ambiguïté reste faible mais pas nulle (dégâts de
 * fin de tour, brûlure, sable, etc. peuvent aussi faire varier les PV).
 */
public final class MoveUseTracker {

    private MoveUseTracker() {
    }

    private static final String CLE_PREFIXE_COUP = "cobblemon.move.";
    private static final String CLE_UTILISE_COUP = "cobblemon.battle.used_move";
    private static final String CLE_UTILISE_COUP_SUR = "cobblemon.battle.used_move_on";

    private static volatile String dernierCoupShowdownId = null;
    private static volatile long dernierCoupTimestampMs = 0L;

    /** Durée pendant laquelle un coup détecté reste "récent" et exploitable pour une observation. */
    private static final long FRAICHEUR_MS = 3000L;

    /**
     * Appelé par le Mixin pour chaque message de combat reçu. Ne fait rien
     * si le message n'est pas un message "coup utilisé".
     */
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
        for (Object arg : contenu.getArgs()) {
            if (arg instanceof Text texteArg && texteArg.getContent() instanceof TranslatableTextContent sousContenu) {
                String sousCle = sousContenu.getKey();
                if (sousCle != null && sousCle.startsWith(CLE_PREFIXE_COUP)) {
                    dernierCoupShowdownId = sousCle.substring(CLE_PREFIXE_COUP.length());
                    dernierCoupTimestampMs = System.currentTimeMillis();
                    return;
                }
            }
        }
    }

    /** Renvoie l'identifiant Showdown du dernier coup utilisé récemment, ou null si trop ancien/inconnu. */
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
