// Copie fidèle de TropiHunterBoard 1.3.8 (commit 3bd121b)
// https://github.com/PiikaPops/TropiHunterBoard — Copyright (c) PiikaPops, Licence MIT.
// Seules modifications : package, logger, enregistrement dans TropiCalcClient.
package com.tropimon.tropicalc.pvp

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.Species

object BattleHelper {

    fun isInBattle(): Boolean {
        return try {
            CobblemonClient.battle != null
        } catch (_: Exception) { false }
    }

    fun getOpponentSpecies(): Species? {
        return try {
            val battle = CobblemonClient.battle ?: return null
            val side2 = battle.side2
            for (actor in side2.actors) {
                for (activePokemon in actor.activePokemon) {
                    val bp = activePokemon.battlePokemon ?: continue
                    val speciesName = bp.properties.species ?: continue
                    return PokemonSpecies.getByName(speciesName)
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
