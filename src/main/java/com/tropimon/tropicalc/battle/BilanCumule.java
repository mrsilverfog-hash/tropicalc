package com.tropimon.tropicalc.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Précision du calcul cumulée sur tous tes combats (pas seulement le combat
 * en cours) : coups mesurés, coups dans la fourchette prévue, et par espèce
 * adverse, pour voir si les corrections font vraiment monter le chiffre et
 * quelles espèces ratent le plus. Fichier : config/tropicalc-bilan-cumule.json.
 * Une ligne « BILAN CUMULÉ » est écrite dans le journal à chaque fin de combat.
 */
public final class BilanCumule {

    private BilanCumule() {
    }

    static final class Compte {
        int coups = 0;
        int dedans = 0;
    }

    static final class Donnees {
        int combats = 0;
        int coups = 0;
        int dedans = 0;
        // Les 10 derniers combats : {coups, dedans}, pour suivre la tendance
        List<int[]> derniers = new ArrayList<>();
        Map<String, Compte> especes = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Donnees donnees = null;

    private static Path fichier() {
        return FabricLoader.getInstance().getConfigDir().resolve("tropicalc-bilan-cumule.json");
    }

    private static Donnees donnees() {
        if (donnees != null) return donnees;
        donnees = new Donnees();
        try {
            Path f = fichier();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Donnees lu = GSON.fromJson(r, Donnees.class);
                    if (lu != null) donnees = lu;
                }
            }
        } catch (Exception ignored) {
        }
        if (donnees.derniers == null) donnees.derniers = new ArrayList<>();
        if (donnees.especes == null) donnees.especes = new HashMap<>();
        return donnees;
    }

    /** Un coup mesuré dans le combat qui se termine (espèce adverse concernée). */
    static void noter(Map<String, int[]> parEspece, int coups, int dedans) {
        if (coups <= 0) return;
        try {
            Donnees d = donnees();
            d.combats++;
            d.coups += coups;
            d.dedans += dedans;
            d.derniers.add(new int[]{coups, dedans});
            while (d.derniers.size() > 10) d.derniers.remove(0);
            for (Map.Entry<String, int[]> e : parEspece.entrySet()) {
                Compte c = d.especes.computeIfAbsent(e.getKey(), k -> new Compte());
                c.coups += e.getValue()[0];
                c.dedans += e.getValue()[1];
            }
            sauvegarder();
            journaliser(d);
        } catch (Exception ignored) {
        }
    }

    private static void journaliser(Donnees d) {
        int c10 = 0, d10 = 0;
        for (int[] x : d.derniers) {
            c10 += x[0];
            d10 += x[1];
        }
        MessageDebugLogger.analyse(String.format(java.util.Locale.ROOT,
            "BILAN CUMULÉ : %d/%d coups dans la fourchette (%.0f %%) sur %d combats ; %d derniers combats : %.0f %%",
            d.dedans, d.coups, 100.0 * d.dedans / Math.max(1, d.coups), d.combats,
            d.derniers.size(), 100.0 * d10 / Math.max(1, c10)));
        // Espèces qui ratent le plus (au moins 3 coups mesurés)
        List<Map.Entry<String, Compte>> l = new ArrayList<>();
        for (Map.Entry<String, Compte> e : d.especes.entrySet()) if (e.getValue().coups >= 3) l.add(e);
        l.sort((a, b) -> Double.compare(taux(a.getValue()), taux(b.getValue())));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, l.size()); i++) {
            Compte c = l.get(i).getValue();
            if (taux(c) >= 0.9) break;
            if (sb.length() > 0) sb.append(", ");
            sb.append(l.get(i).getKey()).append(' ').append(c.dedans).append('/').append(c.coups);
        }
        if (sb.length() > 0) MessageDebugLogger.analyse("BILAN CUMULÉ espèces les moins bien prévues : " + sb);
    }

    private static double taux(Compte c) {
        return c.coups == 0 ? 1.0 : (double) c.dedans / c.coups;
    }

    private static void sauvegarder() {
        try {
            Path f = fichier();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(donnees, w);
            }
        } catch (Exception ignored) {
        }
    }
}
