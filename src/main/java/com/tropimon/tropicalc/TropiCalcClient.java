package com.tropimon.tropicalc;

import com.tropimon.tropicalc.calc.SmogonDataLoader;
import com.tropimon.tropicalc.client.CalcOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TropiCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "tropicalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        SmogonDataLoader.charger();
        HudRenderCallback.EVENT.register(new CalcOverlay());
        com.tropimon.tropicalc.pvp.PvpOverlay.INSTANCE.register();
        com.tropimon.tropicalc.pvp.PvpDetector.INSTANCE.register();
        LOGGER.info("TropiCalc chargé - moteur de calcul de dégâts (format Simple uniquement)");
    }
}
