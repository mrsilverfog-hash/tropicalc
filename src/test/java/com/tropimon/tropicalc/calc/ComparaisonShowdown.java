package com.tropimon.tropicalc.calc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Rejoue les cas de src/test/resources/cas-reference-showdown.json dans le
 * calcul de TropiCalc et compare les 16 jets de dégâts à ceux du calculateur
 * officiel de Showdown (générés par tools/reference-calc/generer-cas.js).
 *
 * Les objets sont construits exactement comme en jeu : talents et objets
 * traduits par ShowdownIdMapper, capacité construite comme CalcOverlay le
 * fait (nom, type, catégorie, puissance, drapeaux poing/morsure).
 *
 * Séparé du test JUnit pour pouvoir aussi être lancé sans JUnit (main).
 */
public final class ComparaisonShowdown {

    public static final String RESSOURCE = "/cas-reference-showdown.json";

    /** Un cas lu dans le fichier, prêt à être recalculé. */
    public record Cas(String nom, JsonObject donnees) {
        @Override
        public String toString() { return nom; }
    }

    private ComparaisonShowdown() {
    }

    public static List<Cas> chargerCas() {
        try (InputStream in = ComparaisonShowdown.class.getResourceAsStream(RESSOURCE)) {
            if (in == null) throw new IllegalStateException("Ressource introuvable : " + RESSOURCE);
            JsonArray tableau = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            List<Cas> cas = new ArrayList<>();
            for (JsonElement e : tableau) {
                JsonObject o = e.getAsJsonObject();
                cas.add(new Cas(o.get("nom").getAsString(), o));
            }
            return cas;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Renvoie null si le calcul correspond, sinon une explication de l'écart. */
    public static String verifier(Cas cas) {
        JsonObject o = cas.donnees();
        Pokemon attaquant = construirePokemon(o.getAsJsonObject("attaquant"));
        Pokemon defenseur = construirePokemon(o.getAsJsonObject("defenseur"));
        Move capacite = construireCapacite(o.getAsJsonObject("capacite"));

        Field terrain = new Field();
        terrain.setMeteo(Field.Meteo.valueOf(o.get("meteo").getAsString()));
        terrain.setTerrain(Field.TypeTerrain.valueOf(o.get("champ").getAsString()));
        Field.Ecrans ecrans = new Field.Ecrans();
        ecrans.setProtection(o.get("protection").getAsBoolean());
        ecrans.setMurLumiere(o.get("murLumiere").getAsBoolean());
        boolean critique = o.get("critique").getAsBoolean();

        DamageCalculator.Resultat r = DamageCalculator.calculer(attaquant, defenseur, capacite, terrain, ecrans, critique);

        int[] attendus = entiers(o.getAsJsonArray("degatsAttendus"));
        int[] obtenus = r.immunise || r.degatsParRoll.length == 0 ? new int[16] : r.degatsParRoll;
        if (Arrays.equals(attendus, obtenus)) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("\n  Showdown  : ").append(Arrays.toString(attendus));
        sb.append("\n  TropiCalc : ").append(Arrays.toString(obtenus));
        sb.append("\n  ").append(o.get("description").getAsString());
        ecartsDeStats(sb, "attaquant", attaquant, o.getAsJsonObject("attaquant"));
        ecartsDeStats(sb, "défenseur", defenseur, o.getAsJsonObject("defenseur"));
        return sb.toString();
    }

    // --- Construction des objets du calcul ---

    private static final Map<String, Stat> STATS = Map.of(
        "hp", Stat.PV, "atk", Stat.ATTAQUE, "def", Stat.DEFENSE,
        "spa", Stat.ATTAQUE_SPE, "spd", Stat.DEFENSE_SPE, "spe", Stat.VITESSE);

    private static final Map<String, Pokemon.Statut> STATUTS = Map.of(
        "brn", Pokemon.Statut.BRULURE, "psn", Pokemon.Statut.POISON,
        "tox", Pokemon.Statut.POISON_GRAVE, "par", Pokemon.Statut.PARALYSIE,
        "slp", Pokemon.Statut.SOMMEIL, "frz", Pokemon.Statut.GEL);

    static Pokemon construirePokemon(JsonObject p) {
        JsonArray types = p.getAsJsonArray("types");
        PokemonType type1 = ShowdownIdMapper.type(types.get(0).getAsString());
        PokemonType type2 = types.size() > 1 ? ShowdownIdMapper.type(types.get(1).getAsString()) : null;

        Pokemon.Builder b = Pokemon.builder(p.get("espece").getAsString(), p.get("niveau").getAsInt(), type1, type2);
        JsonObject base = p.getAsJsonObject("statsBase");
        JsonObject evs = p.getAsJsonObject("evs");
        JsonObject ivs = p.getAsJsonObject("ivs");
        for (Map.Entry<String, Stat> s : STATS.entrySet()) {
            b.statBase(s.getValue(), base.get(s.getKey()).getAsInt());
            b.ev(s.getValue(), evs.get(s.getKey()).getAsInt());
            b.iv(s.getValue(), ivs.get(s.getKey()).getAsInt());
        }
        b.nature(ShowdownIdMapper.nature(p.get("nature").getAsString()));
        String talent = ShowdownIdMapper.talent(p.get("talent").getAsString());
        if (talent != null) b.talent(talent);
        if (p.has("objet") && !p.get("objet").isJsonNull()) {
            String objet = ShowdownIdMapper.objet(p.get("objet").getAsString());
            if (objet != null) b.objet(objet);
        }
        b.poids(p.get("poidsKg").getAsDouble() * 10);

        Pokemon pokemon = b.build();
        JsonObject boosts = p.getAsJsonObject("boosts");
        for (Map.Entry<String, Stat> s : STATS.entrySet()) {
            if (boosts.has(s.getKey())) pokemon.setStage(s.getValue(), boosts.get(s.getKey()).getAsInt());
        }
        Pokemon.Statut statut = STATUTS.get(p.get("statut").getAsString());
        if (statut != null) pokemon.setStatut(statut);
        pokemon.setPvActuels(p.get("pvActuels").getAsInt());
        return pokemon;
    }

    /** Même construction que CalcOverlay.convertirCapacite. */
    static Move construireCapacite(JsonObject c) {
        String id = c.get("id").getAsString();
        String cat = c.get("categorie").getAsString();
        Move.Categorie categorie = "Physical".equals(cat) ? Move.Categorie.PHYSIQUE
            : "Special".equals(cat) ? Move.Categorie.SPECIALE : Move.Categorie.STATUT;
        return Move.builder(id, ShowdownIdMapper.type(c.get("type").getAsString()), categorie)
            .puissance(c.get("puissance").getAsInt())
            .poing(MoveFlags.estPoing(id))
            .morsure(MoveFlags.estMorsure(id))
            .build();
    }

    private static int[] entiers(JsonArray a) {
        int[] t = new int[a.size()];
        for (int i = 0; i < t.length; i++) t[i] = a.get(i).getAsInt();
        return t;
    }

    private static void ecartsDeStats(StringBuilder sb, String role, Pokemon p, JsonObject json) {
        JsonObject attendues = json.getAsJsonObject("statsShowdown");
        for (Map.Entry<String, Stat> s : STATS.entrySet()) {
            int attendu = attendues.get(s.getKey()).getAsInt();
            int obtenu = p.getStatCalculee(s.getValue());
            if (attendu != obtenu) {
                sb.append("\n  Stat ").append(s.getValue().getNomFrancais()).append(" du ").append(role)
                    .append(" : Showdown ").append(attendu).append(", TropiCalc ").append(obtenu);
            }
        }
        if (ShowdownIdMapper.talent(json.get("talent").getAsString()) == null) {
            sb.append("\n  (talent du ").append(role).append(" non traduit : ")
                .append(json.get("talent").getAsString()).append(")");
        }
    }

    public static final String RESSOURCE_VITESSE = "/cas-vitesse-showdown.json";

    public static List<Cas> chargerCasVitesse() {
        try (InputStream in = ComparaisonShowdown.class.getResourceAsStream(RESSOURCE_VITESSE)) {
            if (in == null) throw new IllegalStateException("Ressource introuvable : " + RESSOURCE_VITESSE);
            JsonArray tableau = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            List<Cas> cas = new ArrayList<>();
            for (JsonElement e : tableau) {
                JsonObject o = e.getAsJsonObject();
                cas.add(new Cas(o.get("nom").getAsString(), o));
            }
            return cas;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vitesse en combat de TropiCalc contre getFinalSpeed de Showdown ; null si identique. */
    public static String verifierVitesse(Cas cas) {
        JsonObject o = cas.donnees();
        Pokemon p = construirePokemon(o.getAsJsonObject("pokemon"));
        int obtenue = (int) DamageCalculator.vitesseEnCombat(p,
            Field.Meteo.valueOf(o.get("meteo").getAsString()),
            Field.TypeTerrain.valueOf(o.get("champ").getAsString()),
            o.get("ventArriere").getAsBoolean());
        int attendue = o.get("vitesseAttendue").getAsInt();
        if (obtenue == attendue) return null;
        StringBuilder sb = new StringBuilder("\n  Showdown : " + attendue + ", TropiCalc : " + obtenue);
        ecartsDeStats(sb, "Pokémon", p, o.getAsJsonObject("pokemon"));
        return sb.toString();
    }

    /** Lancement sans JUnit : affiche les écarts, code de sortie 1 s'il y en a. */
    public static void main(String[] args) {
        int echecs = 0;
        List<Cas> tous = chargerCas();
        for (Cas cas : tous) {
            String ecart;
            try {
                ecart = verifier(cas);
            } catch (Throwable t) {
                ecart = "\n  Exception : " + t;
            }
            if (ecart != null) {
                echecs++;
                System.out.println("ÉCART : " + cas.nom() + ecart);
            }
        }
        System.out.println((tous.size() - echecs) + "/" + tous.size() + " cas identiques à Showdown");
        int echecsVitesse = 0;
        List<Cas> vitesses = chargerCasVitesse();
        for (Cas cas : vitesses) {
            String ecart;
            try {
                ecart = verifierVitesse(cas);
            } catch (Throwable t) {
                ecart = "\n  Exception : " + t;
            }
            if (ecart != null) {
                echecsVitesse++;
                System.out.println("ÉCART VITESSE : " + cas.nom() + ecart);
            }
        }
        System.out.println((vitesses.size() - echecsVitesse) + "/" + vitesses.size() + " vitesses identiques à Showdown");
        echecs += echecsVitesse;
        if (echecs > 0) System.exit(1);
    }
}
