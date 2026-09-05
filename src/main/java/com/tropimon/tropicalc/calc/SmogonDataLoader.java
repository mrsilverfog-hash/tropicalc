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
                                int spaEv, int spdEv, int speEv, double poids) {
    }

    public record SmogonPokemonData(
        List<String> topItemsShowdownId,
        List<String> topAbilitiesShowdownId,
        List<ParsedSpread> topSpreads,
        List<String> topMovesShowdownId,
        double topItemUsageFraction   // part de l'objet n°1 sur le total (0.0 à 1.0)
    ) {
    }

    private static final Map<String, SmogonPokemonData> DONNEES = new HashMap<>();
    private static volatile boolean charge = false;
    private static volatile boolean erreur = false;

    /**
     * URLs calculées dynamiquement sur les mois RÉCENTS, jamais codées en dur.
     * Une liste de dates fixes périme d'elle-même chaque mois — c'était le cas
     * ici : figée à "2025-12" pendant que Smogon publiait déjà jusqu'en 2026-06+,
     * l'estimation Smogon tournait potentiellement à vide ou sur une méta
     * obsolète depuis des mois sans que rien ne le signale.
     * Smogon publie le mois M vers le 1er du mois M+1 : on essaie d'abord le
     * mois précédent (le plus susceptible d'être déjà publié et stable), puis
     * on remonte jusqu'à 4 mois en arrière, sur gen9nationaldex puis gen9ou.
     */
    private static String[] construireUrlsEssai() {
        java.time.YearMonth maintenant = java.time.YearMonth.now(java.time.ZoneOffset.UTC);
        List<String> urls = new ArrayList<>();
        for (String format : new String[]{"gen9nationaldex", "gen9ou"}) {
            for (int i = 1; i <= 4; i++) {
                java.time.YearMonth mois = maintenant.minusMonths(i);
                urls.add(String.format("https://www.smogon.com/stats/%04d-%02d/chaos/%s-0.json",
                    mois.getYear(), mois.getMonthValue(), format));
            }
        }
        return urls.toArray(new String[0]);
    }

    private static final String[] URLS_ESSAI = construireUrlsEssai();

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
            double topItemFraction = fractionDuTop(pkData.getAsJsonObject("Items"));
            DONNEES.put(nomPokemon, new SmogonPokemonData(topItems, topAbilities, topSpreads, topMoves, topItemFraction));
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

    /** Part (0.0 à 1.0) que représente l'entrée n°1 sur le poids total (hors "aucun objet"). */
    private static double fractionDuTop(JsonObject obj) {
        if (obj == null) return 0.0;
        double total = 0.0;
        double max = 0.0;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String nom = normaliser(e.getKey());
            if (nom.isEmpty() || nom.equals("noitem") || nom.equals("nothing")) continue;
            double poids = e.getValue().getAsDouble();
            total += poids;
            if (poids > max) max = poids;
        }
        return total <= 0 ? 0.0 : max / total;
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
            ParsedSpread s = parserSpread(entrees.get(i).getKey(), entrees.get(i).getValue());
            if (s != null) resultat.add(s);
        }
        return resultat;
    }

    private static ParsedSpread parserSpread(String spread, double poids) {
        try {
            String[] parties = spread.split(":");
            if (parties.length != 2) return null;
            String nature = normaliser(parties[0]);
            String[] evs = parties[1].split("/");
            if (evs.length != 6) return null;
            return new ParsedSpread(nature,
                Integer.parseInt(evs[0].trim()), Integer.parseInt(evs[1].trim()),
                Integer.parseInt(evs[2].trim()), Integer.parseInt(evs[3].trim()),
                Integer.parseInt(evs[4].trim()), Integer.parseInt(evs[5].trim()), poids);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normaliser(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Sets manuels pour les espèces absentes des données Smogon (bannies
     * des formats consultés - gen9nationaldex/gen9ou - mais rencontrables
     * en jeu sur des formats plus permissifs). Vérifié avant recherche
     * dans les données Smogon chargées : à défaut, aucune capacité/objet/
     * talent estimé ne serait jamais affiché pour ces espèces avant
     * qu'elles n'aient réellement agi en combat.
     */
    private static final Map<String, SmogonPokemonData> FALLBACKS_MANUELS = Map.of(
        "dracovish", new SmogonPokemonData(
            List.of("choicescarf"),
            List.of("strongjaw"),
            List.of(new ParsedSpread("adamant", 4, 252, 0, 0, 0, 252, 1.0)),
            List.of("fishiousrend", "crunch", "psychicfangs", "outrage"),
            1.0
        ),
        "dragapult", new SmogonPokemonData(
            List.of("choicespecs"),
            List.of("infiltrator"),
            List.of(new ParsedSpread("timid", 0, 0, 0, 252, 4, 252, 1.0)),
            List.of("dracometeor", "shadowball", "flamethrower", "uturn"),
            1.0
        )
    );

    public static SmogonPokemonData getDonnees(String especeShowdownId) {
        String id = normaliser(especeShowdownId);
        if (charge) {
            SmogonPokemonData d = DONNEES.get(id);
            if (d != null) return d;
        }
        return FALLBACKS_MANUELS.get(id);
    }

    public static boolean estCharge() { return charge; }
    public static boolean aErreur() { return erreur; }
}
