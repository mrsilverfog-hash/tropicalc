package com.tropimon.tropicalc.calc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tropimon.tropicalc.TropiCalcClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public final class SmogonDataLoader {

    private SmogonDataLoader() {
    }

    public record ParsedSpread(String natureShowdownId, int hpEv, int atkEv, int defEv,
                                int spaEv, int spdEv, int speEv) {
    }

    public record SmogonPokemonData(
        List<String> topItemsShowdownId,
        List<String> topAbilitiesShowdownId,
        List<ParsedSpread> topSpreads,
        List<String> topMovesShowdownId
    ) {
    }

    private static final Map<String, SmogonPokemonData> DONNEES = new HashMap<>();
    private static volatile boolean charge = false;
    private static volatile boolean erreur = false;

    private static final String[] URLS_ESSAI = {
        "https://www.smogon.com/stats/2025-12/chaos/gen9nationaldex-0.json",
        "https://www.smogon.com/stats/2025-11/chaos/gen9nationaldex-0.json",
        "https://www.smogon.com/stats/2025-10/chaos/gen9nationaldex-0.json",
        "https://www.smogon.com/stats/2025-09/chaos/gen9nationaldex-0.json",
        "https://www.smogon.com/stats/2025-12/chaos/gen9ou-0.json",
        "https://www.smogon.com/stats/2025-11/chaos/gen9ou-0.json",
    };

    public static void charger() {
        Thread t = new Thread(() -> {
            for (String url : URLS_ESSAI) {
                try {
                    TropiCalcClient.LOGGER.info("[TropiCalc] Chargement sets Smogon : {}", url);
                    HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "TropiCalc/1.0 Cobblemon-Fabric-Mod")
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() != 200) {
                        TropiCalcClient.LOGGER.warn("[TropiCalc] HTTP {} pour {}", resp.statusCode(), url);
                        continue;
                    }
                    parser(resp.body());
                    charge = true;
                    TropiCalcClient.LOGGER.info("[TropiCalc] Sets Smogon chargés : {} Pokémon ({})", DONNEES.size(), url);
                    return;
                } catch (Exception e) {
                    TropiCalcClient.LOGGER.warn("[TropiCalc] Échec chargement {} : {}", url, e.getMessage());
                }
            }
            erreur = true;
            TropiCalcClient.LOGGER.warn("[TropiCalc] Impossible de charger les sets Smogon.");
        }, "TropiCalc-SmogonLoader");
        t.setDaemon(true);
        t.start();
    }

    private static void parser(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject data = root.getAsJsonObject("data");
        if (data == null) return;
        DONNEES.clear();
        for (Map.Entry<String, JsonElement> entree : data.entrySet()) {
            String nomPokemon = normaliser(entree.getKey());
            JsonObject pkData = entree.getValue().getAsJsonObject();
            List<String> topItems = extraireTop(pkData.getAsJsonObject("Items"), 5);
            List<String> topAbilities = extraireTop(pkData.getAsJsonObject("Abilities"), 5);
            List<ParsedSpread> topSpreads = extraireSpreads(pkData.getAsJsonObject("Spreads"), 5);
            List<String> topMoves = extraireTop(pkData.getAsJsonObject("Moves"), 5);
            DONNEES.put(nomPokemon, new SmogonPokemonData(topItems, topAbilities, topSpreads, topMoves));
        }
    }

    private static List<String> extraireTop(JsonObject obj, int n) {
        if (obj == null) return List.of();
        List<Map.Entry<String, Double>> entrees = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String nom = normaliser(e.getKey());
            if (nom.isEmpty() || nom.equals("noitem") || nom.equals("nothing")) continue;
            entrees.add(Map.entry(nom, e.getValue().getAsDouble()));
        }
        entrees.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> resultat = new ArrayList<>();
        for (int i = 0; i < Math.min(n, entrees.size()); i++) resultat.add(entrees.get(i).getKey());
        return resultat;
    }

    private static List<ParsedSpread> extraireSpreads(JsonObject obj, int n) {
        if (obj == null) return List.of();
        List<Map.Entry<String, Double>> entrees = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            entrees.add(Map.entry(e.getKey(), e.getValue().getAsDouble()));
        }
        entrees.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<ParsedSpread> resultat = new ArrayList<>();
        for (int i = 0; i < Math.min(n, entrees.size()); i++) {
            ParsedSpread s = parserSpread(entrees.get(i).getKey());
            if (s != null) resultat.add(s);
        }
        return resultat;
    }

    private static ParsedSpread parserSpread(String spread) {
        try {
            String[] parties = spread.split(":");
            if (parties.length != 2) return null;
            String nature = normaliser(parties[0]);
            String[] evs = parties[1].split("/");
            if (evs.length != 6) return null;
            return new ParsedSpread(nature,
                Integer.parseInt(evs[0].trim()), Integer.parseInt(evs[1].trim()),
                Integer.parseInt(evs[2].trim()), Integer.parseInt(evs[3].trim()),
                Integer.parseInt(evs[4].trim()), Integer.parseInt(evs[5].trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normaliser(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static SmogonPokemonData getDonnees(String especeShowdownId) {
        if (!charge) return null;
        return DONNEES.get(normaliser(especeShowdownId));
    }

    public static boolean estCharge() { return charge; }
    public static boolean aErreur() { return erreur; }
}
