package com.tropimon.tropicalc.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.tropimon.tropicalc.battle.MoveUseTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepte les messages de combat Cobblemon en lecture seule, juste pour
 * en extraire l'identifiant du coup utilisé (via MoveUseTracker) avant que
 * le texte ne soit affiché. N'altère JAMAIS le comportement d'origine :
 * injection en HEAD, sans annuler ni modifier quoi que ce soit, donc même
 * en cas d'erreur de notre côté l'affichage du combat de Cobblemon continue
 * de fonctionner normalement.
 */
@Mixin(BattleMessageHandler.class)
public class BattleMessageHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void tropicalc$onBattleMessage(BattleMessagePacket packet, MinecraftClient client, CallbackInfo ci) {
        try {
            for (Text message : packet.getMessages()) {
                MoveUseTracker.traiterMessage(message);
            }
        } catch (Throwable erreur) {
            // Volontairement avalé : ne jamais faire planter le vrai
            // gestionnaire de Cobblemon a cause d'un souci de notre cote.
        }
    }
}
