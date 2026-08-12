package com.tropimon.tropicalc;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Bascule manuelle du mod (F6 par défaut) : l'utilisateur active/désactive
 * lui-même l'affichage plutôt qu'une détection automatique - plus simple et
 * fiable pour des modes de jeu (Random Battle...) qu'on ne peut pas
 * distinguer avec certitude depuis les messages de combat.
 */
public final class ModToggle {

    private ModToggle() {
    }

    private static boolean actif = true;
    private static KeyBinding touche;

    public static boolean estActif() {
        return actif;
    }

    public static void enregistrer() {
        touche = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tropicalc.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "key.categories.tropicalc"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (touche.wasPressed()) {
                actif = !actif;
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal(actif ? "§a[TropiCalc] Activé" : "§c[TropiCalc] Désactivé"),
                        true
                    );
                }
            }
        });
    }
}
