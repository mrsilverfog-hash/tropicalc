// Adapté de TropiHunterBoard (https://github.com/PiikaPops/TropiHunterBoard)
// Copyright (c) PiikaPops — Licence MIT. Intégré dans TropiCalc avec attribution.
package com.tropimon.tropicalc.pvp

import java.text.Normalizer

/**
 * Shared name normalization for matching Pokémon/move/ability names across
 * data sources that disagree on special characters:
 *  - accents: "Flabébé" (lang) vs "Flabebe" (species JSON)
 *  - typographic apostrophe: "Farfetch’d" (species JSON, U+2019) vs "farfetch'd" (typed)
 *  - gender symbols: "Nidoran♀"/"Nidoran♂" vs "nidoranf"/"nidoranm"
 *  - punctuation/spacing: "Mr. Mime" vs "mrmime", "Type: Null" vs "typenull"
 */
object NameUtil {

    /**
     * Canonical key: gender symbols mapped to f/m, diacritics stripped
     * (é→e, à→a…), then everything non a-z0-9 removed.
     */
    fun normalize(name: String): String {
        if (name.isEmpty()) return name
        val genderMapped = name
            .replace("♀", "f") // ♀
            .replace("♂", "m") // ♂
        val decomposed = Normalizer.normalize(genderMapped, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "") // strip combining diacritical marks
        return decomposed.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Accent/punctuation-insensitive substring match for search fields.
     * "flabebe" matches "Flabébé", "farfetch'd" matches "Farfetch’d".
     */
    fun matchesQuery(candidate: String, query: String): Boolean {
        if (query.isEmpty()) return true
        return normalize(candidate).contains(normalize(query))
    }
}
