package com.tropimon.tropicalc.battle;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public final class MoveUseTracker {

    private MoveUseTracker() {
    }

    private static final String CLE_PREFIXE_COUP = "cobblemon.move.";
    private static final String CLE_UTILISE_COUP = "cobblemon.battle.used_move";
    private static final String CLE_UTILISE_COUP_SUR = "cobblemon.battle.used_move_on";
    private static final String CLE_PROPRIETAIRE = "cobblemon.battle.owned_pokemon";
    private static final String CLE_NOUVEAU_TOUR = "cobblemon.battle.turn";

    public record CoupDetecte(String showdownId, String proprietaire) {
    }

    public static void traiterMessage(Text message) {
        if (message == null) return;

        BoostTracker.traiterMessage(message);
        FieldTracker.traiterMessage(message);
        TypeTracker.traiterMessage(message);

        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();

        if (CLE_NOUVEAU_TOUR.equals(cle)) {
            ObservationCollector.signalerNouveauTour();
            return;
        }

        if (!CLE_UTILISE_COUP.equals(cle) && !CLE_UTILISE_COUP_SUR.equals(cle)) return;

        String proprietaire = null;
        String coupId = null;

        for (Object arg : contenu.getArgs()) {
            if (!(arg instanceof Text texteArg) || !(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) continue;
            String sousCle = sousContenu.getKey();
            if (sousCle == null) continue;
            if (sousCle.startsWith(CLE_PREFIXE_COUP)) {
                if (coupId == null) {
                    coupId = sousCle.substring(CLE_PREFIXE_COUP.length());
                }
            } else if (CLE_PROPRIETAIRE.equals(sousCle)) {
                if (proprietaire == null) {
                    Object[] sousArgs = sousContenu.getArgs();
                    if (sousArgs.length > 0 && sousArgs[0] instanceof String nom) {
                        proprietaire = nom;
                    }
                }
            }
        }

        if (coupId != null) {
            ObservationCollector.signalerCoupUtilise(new CoupDetecte(coupId, proprietaire));
        }
    }
}
