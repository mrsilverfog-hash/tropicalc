package com.tropimon.tropicalc.mixin;

import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.tropimon.tropicalc.battle.MoveUseTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepte le DÉCODAGE du paquet de message de combat, directement à sa
 * sortie du réseau, avant que quoi que ce soit (Cobblemon ou un autre mod
 * comme Cobblemon Extended Battle UI) ne décide qui le traite. Contrairement
 * à un Mixin sur BattleMessageHandler, ce point d'interception ne peut pas
 * être contourné par un mod qui remplace l'affichage du combat, car le
 * décodage a lieu une seule fois, systématiquement, pour CHAQUE paquet reçu.
 */
@Mixin(BattleMessagePacket.Companion.class)
public class BattleMessagePacketMixin {

    @Inject(method = "decode", at = @At("RETURN"))
    private void tropicalc$onDecode(RegistryFriendlyByteBuf buffer, CallbackInfoReturnable<BattleMessagePacket> cir) {
        try {
            BattleMessagePacket packet = cir.getReturnValue();
            if (packet == null) {
                return;
            }
            for (Text message : packet.getMessages()) {
                MoveUseTracker.traiterMessage(message);
            }
        } catch (Throwable erreur) {
            // Volontairement avalé : ne jamais perturber le décodage réel.
        }
    }
}
