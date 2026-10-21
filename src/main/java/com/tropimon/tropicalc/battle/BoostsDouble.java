package com.tropimon.tropicalc.battle;

import com.tropimon.tropicalc.calc.Stat;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Boosts / baisses de stats en combat double, Pokémon par Pokémon.
 *
 * Cobblemon ne synchronise pas les stages côté client, et BoostTracker ne
 * connaît qu'un Pokémon par camp. Ici chaque message boost/unboost est rangé
 * sous la clé du Pokémon qu'il nomme (camp + nom affiché, voir
 * BattleStateTracker.cleDouble). Un Pokémon qui n'est plus sur le terrain
 * perd ses stages (ils ne survivent pas à un changement).
 */
public final class BoostsDouble {

    private BoostsDouble() {
    }

    private static final Map<String, EnumMap<Stat, Integer>> STAGES = new HashMap<>();
    private static final Map<String, Long> MAJ = new HashMap<>();

    public static void vider() {
        STAGES.clear();
        MAJ.clear();
    }

    public static void traiterMessage(Text message) {
        if (message == null) return;
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();
        if (cle == null) return;

        if (cle.equals("cobblemon.battle.clearallboost")) {
            vider();
            return;
        }
        int delta;
        if (cle.startsWith("cobblemon.battle.boost.slight")) delta = 1;
        else if (cle.startsWith("cobblemon.battle.boost.sharp")) delta = 2;
        else if (cle.startsWith("cobblemon.battle.boost.severe")) delta = 3;
        else if (cle.startsWith("cobblemon.battle.unboost.slight")) delta = -1;
        else if (cle.startsWith("cobblemon.battle.unboost.sharp")) delta = -2;
        else if (cle.startsWith("cobblemon.battle.unboost.severe")) delta = -3;
        else return;

        String pokemon = null;
        Stat stat = null;
        for (Object arg : contenu.getArgs()) {
            if (!(arg instanceof Text texteArg)) continue;
            if (!(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) continue;
            String sousCle = sousContenu.getKey();
            if (sousCle == null) continue;
            if ("cobblemon.battle.owned_pokemon".equals(sousCle)) {
                Object[] a = sousContenu.getArgs();
                if (a.length >= 2 && a[0] instanceof String dresseur) {
                    Boolean adverse = ObservationCollector.determinerAttaquant(dresseur);
                    String nom = a[1] instanceof Text t ? t.getString() : String.valueOf(a[1]);
                    if (adverse != null) pokemon = BattleStateTracker.cleDouble(adverse, nom);
                }
            } else if (sousCle.startsWith("cobblemon.stat.")) {
                String statId = sousCle.substring("cobblemon.stat.".length()).replace(".name", "");
                stat = switch (statId) {
                    case "attack" -> Stat.ATTAQUE;
                    case "defence" -> Stat.DEFENSE;
                    case "special_attack" -> Stat.ATTAQUE_SPE;
                    case "special_defence" -> Stat.DEFENSE_SPE;
                    case "speed" -> Stat.VITESSE;
                    default -> null;
                };
            }
        }
        if (pokemon == null || stat == null) return;
        EnumMap<Stat, Integer> s = STAGES.computeIfAbsent(pokemon, k -> new EnumMap<>(Stat.class));
        s.put(stat, Math.max(-6, Math.min(6, s.getOrDefault(stat, 0) + delta)));
        MAJ.put(pokemon, System.currentTimeMillis());
    }

    /**
     * Oublie les Pokémon qui ne sont plus sur le terrain. Délai de 5 s : le
     * message de boost arrive parfois avant que le Pokémon entré soit visible.
     */
    public static void garderSeulement(Set<String> surLeTerrain) {
        long maintenant = System.currentTimeMillis();
        Iterator<Map.Entry<String, EnumMap<Stat, Integer>>> it = STAGES.entrySet().iterator();
        while (it.hasNext()) {
            String cle = it.next().getKey();
            if (surLeTerrain.contains(cle)) continue;
            Long maj = MAJ.get(cle);
            if (maj == null || maintenant - maj > 5000) {
                it.remove();
                MAJ.remove(cle);
            }
        }
    }

    /** À chaque tick en combat double. */
    public static void tick() {
        Set<String> cles = new java.util.HashSet<>();
        for (BattleStateTracker.ActifDouble a : BattleStateTracker.getActifsJoueurDouble()) if (a != null) cles.add(a.cle());
        for (BattleStateTracker.ActifDouble a : BattleStateTracker.getActifsAdversaireDouble()) if (a != null) cles.add(a.cle());
        garderSeulement(cles);
    }

    public static int stage(String cle, Stat stat) {
        EnumMap<Stat, Integer> s = STAGES.get(cle);
        return s == null ? 0 : s.getOrDefault(stat, 0);
    }
}
