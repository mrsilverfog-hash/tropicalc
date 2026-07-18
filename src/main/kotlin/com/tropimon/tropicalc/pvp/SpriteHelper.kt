// Adapté de TropiHunterBoard (https://github.com/PiikaPops/TropiHunterBoard)
// Copyright (c) PiikaPops — Licence MIT. Intégré dans TropiCalc avec attribution.
package com.tropimon.tropicalc.pvp

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.io.File
import java.io.FileInputStream
import java.net.URI

/**
 * Downloads and caches 2D pixel art sprites from PokeAPI.
 * Sprites are cached on disk in config/hunterboard/sprites/.
 * Falls back to null if download fails (caller should use 3D ModelWidget).
 *
 * Regional forms (hisuian, galarian, alolan, paldean) are fetched via the
 * PokeAPI form endpoint (e.g. /pokemon/growlithe-hisui/) which returns the
 * correct form-specific sprite URL.
 */
object SpriteHelper {

    // Memory cache: cacheKey -> texture Identifier (null = failed/unavailable)
    private val spriteCache = mutableMapOf<String, Identifier?>()
    // Species currently being downloaded (thread-safe set)
    private val downloading = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    // Counter incremented each time a new sprite is loaded (for cache invalidation)
    var loadedCount = 0
        private set

    private val CACHE_DIR: File by lazy {
        File(MinecraftClient.getInstance().runDirectory, "config/hunterboard/sprites")
    }

    /** Maps Cobblemon aspect tags to PokeAPI form name suffixes. */
    private val FORM_SUFFIXES = mapOf(
        "hisuian"  to "hisui",
        "galarian" to "galar",
        "alolan"   to "alola",
        "paldean"  to "paldea"
    )

    /** Normalize a name to a safe file/cache key (accents/gender symbols folded, non-alphanumeric stripped). */
    private fun normalizeKey(name: String): String = NameUtil.normalize(name)

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Get a sprite texture identifier for the given species.
     * If [aspects] contains a regional form tag (e.g. "hisuian"), the
     * regional form sprite is fetched from PokeAPI instead of the base sprite.
     *
     * Returns null while the download is in progress (non-blocking).
     */
    fun getSpriteIdentifier(speciesName: String, aspects: Set<String> = emptySet()): Identifier? {
        val formSuffix = aspects.firstNotNullOfOrNull { FORM_SUFFIXES[it] }
        val key = if (formSuffix != null) normalizeKey("${speciesName}_$formSuffix")
                  else normalizeKey(speciesName)

        if (spriteCache.containsKey(key)) return spriteCache[key]

        val file = File(CACHE_DIR, "$key.png")
        if (file.exists() && file.length() > 0) return loadAndRegister(key, file)

        if (downloading.add(key)) {
            Thread {
                try {
                    if (formSuffix != null) downloadFormSprite(key, speciesName, formSuffix)
                    else                    downloadBaseSprite(key, speciesName)
                } catch (_: Exception) {
                    spriteCache[key] = null
                } finally {
                    downloading.remove(key)
                }
            }.also { it.isDaemon = true }.start()
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Download helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Download the base-form sprite using the national Pokédex number.
     * URL: https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{dexNum}.png
     */
    private fun downloadBaseSprite(key: String, originalName: String) {
        val species = PokemonSpecies.getByName(key)
            ?: PokemonSpecies.getByName(originalName.lowercase())
        if (species == null) { spriteCache[key] = null; return }

        val dexNum = species.nationalPokedexNumber
        if (dexNum <= 0) { spriteCache[key] = null; return }

        val url = URI("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$dexNum.png").toURL()
        downloadToFile(key, url.toString())
    }

    /**
     * Download a regional-form sprite by first calling the PokeAPI form endpoint
     * (e.g. /pokemon/growlithe-hisui/) to get the form-specific sprite URL,
     * then downloading that image.
     * Falls back to the base-form sprite if the form is not found.
     */
    private fun downloadFormSprite(key: String, speciesName: String, formSuffix: String) {
        val formName = "$speciesName-$formSuffix"
        try {
            // Ask PokeAPI for this form's data
            val apiUrl = URI("https://pokeapi.co/api/v2/pokemon/$formName/").toURL()
            val json   = apiUrl.openStream().reader().readText()

            // Extract front_default URL from JSON without a full parser
            // e.g. "front_default": "https://.../{id}.png"
            val spriteUrl = Regex(""""front_default"\s*:\s*"([^"]+)"""").find(json)
                ?.groupValues?.get(1)

            if (spriteUrl != null) {
                downloadToFile(key, spriteUrl)
                return
            }
        } catch (_: Exception) {
            // Form not found on PokeAPI — fall through to base sprite
        }

        // Fallback: base form sprite
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.debug("[SpriteHelper] Form '$formName' not found, using base sprite")
        downloadBaseSprite(key, speciesName)
    }

    /** Download an image from [url] and save it to the cache file, then register it. */
    private fun downloadToFile(key: String, url: String) {
        CACHE_DIR.mkdirs()
        val file = File(CACHE_DIR, "$key.png")
        URI(url).toURL().openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        MinecraftClient.getInstance().execute { loadAndRegister(key, file) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Texture management
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadAndRegister(key: String, file: File): Identifier? {
        return try {
            val image   = FileInputStream(file).use { NativeImage.read(it) }
            val texture = NativeImageBackedTexture(image)
            val id      = Identifier.of("hunterboard", "sprites/$key")
            MinecraftClient.getInstance().textureManager.registerTexture(id, texture)
            spriteCache[key] = id
            loadedCount++
            id
        } catch (_: Exception) {
            spriteCache[key] = null
            null
        }
    }

    fun clearCache() {
        val tm = MinecraftClient.getInstance().textureManager
        for ((_, id) in spriteCache) {
            if (id != null) try { tm.destroyTexture(id) } catch (_: Exception) {}
        }
        spriteCache.clear()
        loadedCount++
    }
}
