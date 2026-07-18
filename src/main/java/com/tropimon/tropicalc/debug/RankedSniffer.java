package com.tropimon.tropicalc.debug;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sniffer de debug : logge le JSON brut des messages "Ranked" envoyés par le serveur
 * afin de vérifier si le click_event / hover_event contient le pseudo ou l'UUID
 * du joueur qui a lancé la recherche d'adversaire.
 *
 * Le résultat apparaît dans logs/latest.log avec le préfixe [RankedSniffer].
 */
public class RankedSniffer {
    private static final Logger LOGGER = LoggerFactory.getLogger("RankedSniffer");

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                String plain = message.getString();
                if (plain == null) return;
                String lower = plain.toLowerCase();
                if (lower.contains("recherche un adversaire")
                        || lower.contains("rejoindre le combat")
                        || lower.contains("ranked")) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.world != null) {
                        String json = Text.Serialization.toJsonString(
                                message, client.world.getRegistryManager());
                        LOGGER.info("[RankedSniffer] RAW JSON: {}", json);
                    } else {
                        LOGGER.info("[RankedSniffer] (monde indisponible) TEXTE: {}", plain);
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("[RankedSniffer] Erreur lors du log du message", t);
            }
        });
    }
}
