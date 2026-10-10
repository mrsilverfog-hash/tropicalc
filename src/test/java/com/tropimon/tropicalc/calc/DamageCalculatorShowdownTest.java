package com.tropimon.tropicalc.calc;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compare le calcul de dégâts de TropiCalc au calculateur officiel de
 * Showdown, cas par cas, sur les 16 jets de dégâts.
 *
 * Les cas (talents, objets, météo, champs, écrans, critiques, stages,
 * capacités à puissance variable...) sont dans
 * src/test/resources/cas-reference-showdown.json, générés par
 * tools/reference-calc/generer-cas.js. Un écart fait échouer le build
 * GitHub, avec le détail dans le journal : jets attendus, jets obtenus, et
 * les stats qui diffèrent.
 */
class DamageCalculatorShowdownTest {

    @TestFactory
    Stream<DynamicTest> calculIdentiqueAShowdown() {
        return ComparaisonShowdown.chargerCas().stream()
            .map(cas -> DynamicTest.dynamicTest(cas.nom(), () -> {
                String ecart = ComparaisonShowdown.verifier(cas);
                if (ecart != null) fail("Écart avec Showdown pour « " + cas.nom() + " »" + ecart);
            }));
    }
}
