package com.tropimon.tropicalc.battle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Log de diagnostic dans config/tropicalc-messages-debug.txt.
 *
 * Deux sortes de lignes :
 * - les messages de combat bruts (clé + arguments), en format compact :
 *   owned_pokemon(Joueur, translation{...species.X.name...}) devient "Joueur:X" ;
 * - les lignes ">>" écrites par le mod lui-même : dégâts prévus contre dégâts
 *   réels pour chaque coup, observations de vitesse, estimation de
 *   l'adversaire à chaque tour, et la conclusion tirée (objet confirmé ou
 *   raison de l'abstention). Ce sont elles qui permettent de diagnostiquer un
 *   calcul faux sans deviner.
 *
 * Rotation au lieu de troncature : au-delà de 2 Mo, le fichier devient
 * tropicalc-messages-debug.1.txt (l'ancien .1 devient .2). On garde donc les
 * deux sessions précédentes en entier.
 */
public final class MessageDebugLogger {

    private MessageDebugLogger() {
    }

    private static final long TAILLE_MAX_OCTETS = 2_000_000;

    private static Path fichier(String suffixe) {
        return FabricLoader.getInstance().getConfigDir().resolve("tropicalc-messages-debug" + suffixe + ".txt");
    }

    public static void log(Text message) {
        try {
            if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
            StringBuilder sb = new StringBuilder();
            sb.append(horodatage()).append(' ').append(raccourcirCle(contenu.getKey()));
            for (Object arg : contenu.getArgs()) {
                sb.append(" | ").append(formaterArgument(arg));
            }
            ecrire(sb.toString());
        } catch (Exception ignored) {
        }
    }

    /** Ligne d'analyse écrite par le mod (dégâts prévus/réels, vitesse, conclusions). */
    public static void analyse(String ligne) {
        ecrire(horodatage() + " >> " + ligne);
    }

    private static String horodatage() {
        return LocalTime.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }

    private static String raccourcirCle(String cle) {
        if (cle == null) return "?";
        return cle.startsWith("cobblemon.battle.") ? cle.substring("cobblemon.battle.".length())
            : cle.startsWith("cobblemon.") ? cle.substring("cobblemon.".length()) : cle;
    }

    /** owned_pokemon(Joueur, species) -> "Joueur:espèce", cobblemon.move.x -> "x", etc. */
    private static String formaterArgument(Object arg) {
        if (arg instanceof Text texte && texte.getContent() instanceof TranslatableTextContent sous) {
            String k = sous.getKey();
            Object[] a = sous.getArgs();
            if ("cobblemon.battle.owned_pokemon".equals(k) && a.length >= 2) {
                return a[0] + ":" + formaterArgument(a[1]);
            }
            if (k != null && k.startsWith("cobblemon.species.")) return espece(k);
            if (a.length == 0) return raccourcirCle(k);
            StringBuilder sb = new StringBuilder(raccourcirCle(k)).append('(');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formaterArgument(a[i]));
            }
            return sb.append(')').toString();
        }
        String s = String.valueOf(arg);
        if (s.startsWith("cobblemon.species.")) return espece(s);
        if (s.startsWith("cobblemon.move.")) return s.substring("cobblemon.move.".length());
        if (s.startsWith("cobblemon.stat.")) return s.substring("cobblemon.stat.".length()).replace(".name", "");
        // translation{key='cobblemon.species.x.name', ...} affiché tel quel par certains messages
        int i = s.indexOf("cobblemon.species.");
        if (i >= 0) {
            int fin = s.indexOf(".name", i);
            if (fin > i) return s.substring(i + "cobblemon.species.".length(), fin);
        }
        return s;
    }

    private static String espece(String cle) {
        String s = cle.substring("cobblemon.species.".length());
        return s.endsWith(".name") ? s.substring(0, s.length() - 5) : s;
    }

    private static synchronized void ecrire(String ligne) {
        try {
            Path f = fichier("");
            if (Files.exists(f) && Files.size(f) > TAILLE_MAX_OCTETS) {
                Path un = fichier(".1");
                if (Files.exists(un)) Files.move(un, fichier(".2"), StandardCopyOption.REPLACE_EXISTING);
                Files.move(f, un, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(f, ligne + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
