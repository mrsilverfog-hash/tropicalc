package com.tropimon.tropicalc.mixin;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.tropimon.tropicalc.client.SwitchOverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleSwitchPokemonSelection.class)
public abstract class BattleSwitchSelectionMixin {

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void tropicalc$renderSwitchOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        SwitchOverlayRenderer.render((BattleSwitchPokemonSelection) (Object) this, context, mouseX, mouseY);
    }
}
