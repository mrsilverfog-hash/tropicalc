package com.tropimon.tropicalc.calc;

import java.util.Set;

/**
 * Capacités portant le flag "poing" (Poing de Fer +20%) ou "morsure"
 * (Mâchoire Brute +50%). Liste curatée aux capacités compétitives
 * courantes, pas exhaustive.
 */
public final class MoveFlags {

    private MoveFlags() {
    }

    private static final Set<String> POING = Set.of(
        "machpunch", "bulletpunch", "drainpunch", "dynamicpunch",
        "firepunch", "focuspunch", "icepunch", "thunderpunch",
        "megapunch", "meteormash", "poweruppunch", "shadowpunch",
        "skyuppercut", "jetpunch", "doubleironbash", "plasmafists",
        "cometpunch");

    private static final Set<String> MORSURE = Set.of(
        "bite", "crunch", "firefang", "thunderfang", "icefang",
        "poisonfang", "hyperfang", "jawlock", "psychicfangs");

    public static boolean estPoing(String showdownId) {
        return showdownId != null && POING.contains(showdownId);
    }

    public static boolean estMorsure(String showdownId) {
        return showdownId != null && MORSURE.contains(showdownId);
    }
}
