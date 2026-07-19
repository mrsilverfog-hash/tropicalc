// Adapté de TropiHunterBoard (https://github.com/PiikaPops/TropiHunterBoard)
// Copyright (c) PiikaPops — Licence MIT. Intégré dans TropiCalc avec attribution.
package com.tropimon.tropicalc.pvp

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier

/**
 * Represents one Pokémon in the opponent's team.
 * @param speciesId  Cobblemon internal species identifier (e.g. "dragapult", "samurott")
 * @param aspects    form tags (e.g. "hisuian", "galarian", "female", "fished")
 */
data class PvpPokemon(val speciesId: String, val aspects: Set<String> = emptySet())

/**
 * Detects when the /challenge PvP screen opens (server plugin inventory).
 *
 * Screen title : "Select your Lead Pokemon"
 * Item ID      : cobblemon:pokemon_model
 * Layout       : 9-wide grid, opponent team in last column (slot.id % 9 == 8)
 *
 * Each item carries a [cobblemon:pokemon_item] component whose toString() is:
 *   PokemonItemComponent(species=cobblemon:dragapult, aspects=[female], tint=null)
 * This component is read via the DATA_COMPONENT_TYPE registry so no fragile
 * Cobblemon import is needed. It contains the real species even for nicknamed
 * Pokémon, and the aspects for regional forms.
 */
object PvpDetector {

    val playerTeam  : MutableList<PvpPokemon> = mutableListOf()
    val opponentTeam: MutableList<PvpPokemon> = mutableListOf()

    /** True once a PvP team-selection screen was detected; cleared when the battle ends. */
    var pvpSessionActive = false
        private set

    // Regional aspect → readable label shown in overlay
    private val REGION_LABELS = mapOf(
        "hisuian"  to "Hisui",
        "galarian" to "Galar",
        "alolan"   to "Alola",
        "paldean"  to "Paldea"
    )

    // cobblemon:pokemon_item component type, looked up once at first use
    private var pokemonItemComponentType: net.minecraft.component.ComponentType<*>? = null
    private var componentTypeLookupDone = false

    // Parses "PokemonItemComponent(species=cobblemon:dragapult, aspects=[female], ...)"
    private val SPECIES_REGEX  = Regex("""species=[a-z_]+:([a-z_0-9]+)""")
    private val ASPECTS_REGEX  = Regex("""aspects=\[([^\]]*)\]""")

    // Fallback: "Luniika_'s Dragapult (lvl100)" → "Dragapult"
    private val DISPLAY_REGEX  = Regex("""'s (.+?) \(lvl\d+\)""")

    private var tickCounter  = 0
    private var alreadyScanned = false

    private val CHALLENGE_TITLE        = "Select your Lead Pokemon"
    private val CHALLENGE_DOUBLE_TITLE = "Select 2 Pokemon for Doubles"
    private val RANKED_TITLE           = "Sélection de l'équipe"

    private fun isChallenge(title: String) = title.contains(CHALLENGE_TITLE, ignoreCase = true)
                                          || title.contains(CHALLENGE_DOUBLE_TITLE, ignoreCase = true)
    private fun isRanked(title: String)    = title.contains(RANKED_TITLE, ignoreCase = true)

    // Ticks de rescan : les serveurs remplissent parfois les slots en retard
    private val TICKS_SCAN = setOf(3, 10, 20, 40)

    fun register() {
        ScreenEvents.AFTER_INIT.register afterInit@{ _, screen, _, _ ->
            if (screen !is HandledScreen<*>) return@afterInit
            val title = screen.title.string

            tickCounter = 0
            var opposantTrouve = false

            ScreenEvents.afterTick(screen).register afterTick@{ scr ->
                if (scr !is HandledScreen<*> || opposantTrouve) return@afterTick
                tickCounter++
                if (tickCounter in TICKS_SCAN) {
                    scanScreen(scr, isRanked(title), title)
                    if (opponentTeam.isNotEmpty()) opposantTrouve = true
                }
            }
        }
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info("[PvP] Detector registered")
    }

    private fun scanScreen(screen: HandledScreen<*>, ranked: Boolean, title: String) {
        if (!ModConfig.pvpOverlayEnabled) return
        try {
            val handler       = screen.screenHandler
            val slots         = handler.slots
            val containerSize = (slots.size - 36).coerceAtLeast(0)
            if (containerSize < 27) return  // pas un grand conteneur : ignorer

            // Scan de TOUS les slots du conteneur (layout-agnostique)
            data class Entree(val slotId: Int, val mon: PvpPokemon)
            val entrees = mutableListOf<Entree>()
            for (slot in slots) {
                if (slot.id >= containerSize) continue
                val stack = slot.stack
                if (stack.isEmpty) continue
                val entry = extractPvpPokemon(stack) ?: continue
                entrees.add(Entree(slot.id, entry))
            }

            // Heuristique preview : au moins 4 Pokémon identifiés dans le conteneur
            // (évite de se déclencher sur un coffre Lootr ou un PC)
            if (entrees.size < 4) {
                dumpDebug(title, screen, containerSize, entrees.map { it.slotId to it.mon.speciesId })
                return
            }

            // Classification : soustraire mon équipe connue (multiset d'espèces) ;
            // le reste est l'équipe adverse. Plus robuste que des colonnes fixes.
            val maPartie = try {
                CobblemonClient.storage.myParty
                    .mapNotNull { it?.species?.resourceIdentifier?.path }
                    .toMutableList()
            } catch (_: Exception) { mutableListOf() }

            val moi = mutableListOf<PvpPokemon>()
            val eux = mutableListOf<PvpPokemon>()
            if (maPartie.isNotEmpty()) {
                for (e in entrees) {
                    if (maPartie.remove(e.mon.speciesId)) moi.add(e.mon) else eux.add(e.mon)
                }
            } else {
                // Repli sans partie connue : ancienne heuristique de colonnes
                for (e in entrees) {
                    val col = e.slotId % 9
                    if (col <= 3) moi.add(e.mon) else eux.add(e.mon)
                }
            }

            playerTeam.clear();   playerTeam.addAll(moi.take(6))
            opponentTeam.clear(); opponentTeam.addAll(eux.take(6))

            if (opponentTeam.isNotEmpty()) pvpSessionActive = true

            dumpDebug(title, screen, containerSize, entrees.map { it.slotId to it.mon.speciesId })
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
                "[PvP] \"$title\" -> moi(${moi.size})=${moi.map { it.speciesId }} | adverse(${eux.size})=${eux.map { it.speciesId }}")
        } catch (e: Exception) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.warn("[PvP] Scan error: ${e.message}", e)
        }
    }

    /** Dump de diagnostic : contenu du conteneur dans config/tropicalc-preview-debug.txt */
    private fun dumpDebug(title: String, screen: HandledScreen<*>, containerSize: Int,
                          identifies: List<Pair<Int, String>>) {
        try {
            val f = net.fabricmc.loader.api.FabricLoader.getInstance()
                .configDir.resolve("tropicalc-preview-debug.txt")
            val sb = StringBuilder()
            sb.append("=== ").append(java.time.LocalDateTime.now()).append(" ===\n")
            sb.append("Titre: \"").append(title).append("\" | taille: ").append(containerSize).append("\n")
            for (slot in screen.screenHandler.slots) {
                if (slot.id >= containerSize) continue
                val st = slot.stack
                if (st.isEmpty) continue
                val itemId = net.minecraft.registry.Registries.ITEM.getId(st.item)
                sb.append("slot ").append(slot.id).append(" | ").append(itemId)
                    .append(" | \"").append(st.name.string).append("\"\n")
            }
            sb.append("Identifiés: ").append(identifies).append("\n\n")
            java.nio.file.Files.writeString(f, sb.toString(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Column scanner
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Extraction
    // -------------------------------------------------------------------------

    private fun extractPvpPokemon(stack: ItemStack): PvpPokemon? {
        // Primary: cobblemon:pokemon_item component — works for nicknames + regional forms
        extractFromCobblemonComponent(stack)?.let { return it }

        // Fallback: parse display name "Name's Species (lvlN)" (non-nicknamed Pokémon)
        val rawName     = stack.name.string.trim()
        val pokemonName = DISPLAY_REGEX.find(rawName)?.groupValues?.get(1)?.trim()
            ?: rawName.ifEmpty { return null }
        val speciesId   = resolveSpeciesId(pokemonName) ?: run {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.warn("[PvP] Unresolved species from name: \"$pokemonName\"")
            return null
        }
        return PvpPokemon(speciesId)
    }

    /**
     * Read the [cobblemon:pokemon_item] component from the ItemStack.
     *
     * The component type is obtained via the DATA_COMPONENT_TYPE registry so we
     * don't depend on any specific Cobblemon import path. The value is parsed from
     * its toString() representation:
     *   PokemonItemComponent(species=cobblemon:dragapult, aspects=[female], tint=null)
     */
    private fun extractFromCobblemonComponent(stack: ItemStack): PvpPokemon? {
        try {
            val componentType = getPokemonItemComponentType() ?: return null

            @Suppress("UNCHECKED_CAST")
            val value = stack.get(componentType as net.minecraft.component.ComponentType<Any>) ?: return null

            val str = value.toString()

            val speciesPath = SPECIES_REGEX.find(str)?.groupValues?.get(1) ?: return null
            val species     = PokemonSpecies.getByName(speciesPath) ?: return null

            val aspectsStr  = ASPECTS_REGEX.find(str)?.groupValues?.get(1) ?: ""
            val aspects     = aspectsStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            return PvpPokemon(species.resourceIdentifier.path, aspects)
        } catch (e: Exception) {
            com.tropimon.tropicalc.TropiCalcClient.LOGGER.debug("[PvP] Component parse error: ${e.message}")
            return null
        }
    }

    /** Lazy-lookup for the cobblemon:pokemon_item DataComponentType. */
    private fun getPokemonItemComponentType(): net.minecraft.component.ComponentType<*>? {
        if (!componentTypeLookupDone) {
            componentTypeLookupDone = true
            pokemonItemComponentType = net.minecraft.registry.Registries.DATA_COMPONENT_TYPE
                .get(Identifier.of("cobblemon", "pokemon_item"))
            if (pokemonItemComponentType == null) {
                com.tropimon.tropicalc.TropiCalcClient.LOGGER.warn("[PvP] cobblemon:pokemon_item component type not found in registry")
            }
        }
        return pokemonItemComponentType
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun resolveSpeciesId(name: String): String? {
        PokemonSpecies.getByName(
            name.lowercase().replace(" ", "_").replace("-", "_")
        )?.let { return it.resourceIdentifier.path }

        return PokemonSpecies.implemented.find { species ->
            species.translatedName.string.equals(name, ignoreCase = true)
        }?.resourceIdentifier?.path
    }

    /** Returns the region label for this Pokémon's aspects, or null. */
    fun getRegionLabel(pokemon: PvpPokemon): String? =
        pokemon.aspects.firstNotNullOfOrNull { REGION_LABELS[it] }

    fun clearTeam() {
        playerTeam.clear()
        opponentTeam.clear()
        pvpSessionActive = false
    }
}
