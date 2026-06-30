package com.tropimon.tropicalc;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée client de TropiCalc.
 * Calculateur de dégâts automatique pour les combats Cobblemon en simple
 * (1 contre 1), avec estimation des sets adverses en temps réel.
 */
public class TropiCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "tropicalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("TropiCalc chargé - moteur de calcul de dégâts (format Simple uniquement)");
    }
}
