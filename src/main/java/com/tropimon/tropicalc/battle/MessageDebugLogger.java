package com.tropimon.tropicalc.battle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Log de diagnostic : écrit la clé et les arguments de CHAQUE message de
 * combat dans config/tropicalc-messages-debug.txt. Sert à confirmer les
 * vraies clés Cobblemon (ex: changement de type Protéen/Libéro/Détrempage)
 * plutôt que de deviner un nom plausible. À retirer une fois le diagnostic
 * terminé — ce n'est pas fait pour tourner en continu.
 */
public final class MessageDebugLogger {

    private MessageDebugLogger() {
    }

    private static Path fichier() {
        return FabricLoader.getInstance().getConfigDir().resolve("tropicalc-messages-debug.txt");
    }

    public static void log(Text message) {
        try {
            if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
            StringBuilder sb = new StringBuilder();
            sb.append(LocalDateTime.now()).append(" | clé=").append(contenu.getKey());
            for (Object arg : contenu.getArgs()) {
                sb.append(" | arg=");
                if (arg instanceof Text texteArg && texteArg.getContent() instanceof TranslatableTextContent sousContenu) {
                    sb.append(sousContenu.getKey());
                    Object[] sousArgs = sousContenu.getArgs();
                    if (sousArgs.length > 0) {
                        sb.append("(");
                        for (int i = 0; i < sousArgs.length; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(sousArgs[i]);
                        }
                        sb.append(")");
                    }
                } else {
                    sb.append(arg);
                }
            }
            sb.append("\n");
            Files.writeString(fichier(), sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
