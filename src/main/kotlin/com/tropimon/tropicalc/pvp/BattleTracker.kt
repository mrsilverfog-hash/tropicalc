// Adapté de TropiHunterBoard (https://github.com/PiikaPops/TropiHunterBoard)
// Copyright (c) PiikaPops — Licence MIT. Intégré dans TropiCalc avec attribution.
package com.tropimon.tropicalc.pvp

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleActor
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack

/**
 * Reads real-time battle state from CobblemonClient.battle each frame.
 *
 * Player's own team : full Pokemon objects → all data (moves, ability, item, HP, status).
 *                     Active-slot HP/status is overridden from ClientBattlePokemon so it
 *                     stays accurate turn-by-turn (Pokemon.currentHealth lags behind).
 * Opponent's team   : PvpDetector base + HP/status from active ClientBattlePokemon.
 *                     No move tracking — speed tier & possible abilities are shown instead.
 */
object BattleTracker {

    // ── Data model ────────────────────────────────────────────────────────────

    data class BattleMove(
        val displayName: String,
        val typeName:    String,   // localized display name (e.g. "Ténèbres")
        val typeKey:     String,   // internal lowercase name for colour lookup (e.g. "dark")
        val currentPp:   Int,
        val maxPp:       Int
    )

    data class TrackedMon(
        val speciesId:    String,
        val aspects:      Set<String>,
        val hpPercent:    Float,       // 0.0 – 1.0
        val isFainted:    Boolean,
        val statusKey:    String?,     // showdown name: "psn","brn","slp","par","frz","tox"
        val types:        List<String>,
        val moves:        List<BattleMove>,
        val abilityName:  String?,
        val abilityDesc:  String?,
        val heldItem:     ItemStack,
        val isOwn:        Boolean
    )

    // ── Public state ──────────────────────────────────────────────────────────

    var playerTeam  : List<TrackedMon> = emptyList(); private set
    var opponentTeam: List<TrackedMon> = emptyList(); private set

    // ── Persistent opponent HP / status ───────────────────────────────────────
    private val opponentHpMap   = mutableMapOf<String, Float>()
    private val opponentFainted = mutableMapOf<String, Boolean>()
    private val opponentStatus  = mutableMapOf<String, String?>()
    private val opponentAspects = mutableMapOf<String, Set<String>>()

    // ── Colour tables ─────────────────────────────────────────────────────────

    // Badge/icon colours (used for the 5×5 status dot and type chips in overlay)
    val TYPE_COLORS = mapOf(
        "normal"   to 0xFFA8A77A.toInt(), "fire"     to 0xFFEE8130.toInt(),
        "water"    to 0xFF6390F0.toInt(), "electric" to 0xFFF7D02C.toInt(),
        "grass"    to 0xFF7AC74C.toInt(), "ice"      to 0xFF96D9D6.toInt(),
        "fighting" to 0xFFC22E28.toInt(), "poison"   to 0xFFA33EA1.toInt(),
        "ground"   to 0xFFE2BF65.toInt(), "flying"   to 0xFFA98FF3.toInt(),
        "psychic"  to 0xFFF95587.toInt(), "bug"      to 0xFFA6B91A.toInt(),
        "rock"     to 0xFFB6A136.toInt(), "ghost"    to 0xFF735797.toInt(),
        "dragon"   to 0xFF6F35FC.toInt(), "dark"     to 0xFF705746.toInt(),
        "steel"    to 0xFFB7B7CE.toInt(), "fairy"    to 0xFFD685AD.toInt()
    )

    // Brighter colours for text on dark tooltip backgrounds
    val TYPE_TEXT_COLORS = mapOf(
        "normal"   to 0xFFD8D8A8.toInt(), "fire"     to 0xFFFF9950.toInt(),
        "water"    to 0xFF90AAFF.toInt(), "electric" to 0xFFFFE840.toInt(),
        "grass"    to 0xFF88EE55.toInt(), "ice"      to 0xFFB8F0EE.toInt(),
        "fighting" to 0xFFFF6655.toInt(), "poison"   to 0xFFD070D0.toInt(),
        "ground"   to 0xFFEEDD70.toInt(), "flying"   to 0xFFCCB8FF.toInt(),
        "psychic"  to 0xFFFF88AA.toInt(), "bug"      to 0xFFCCDD30.toInt(),
        "rock"     to 0xFFDDCC55.toInt(), "ghost"    to 0xFFAA99DD.toInt(),
        "dragon"   to 0xFF9966FF.toInt(), "dark"     to 0xFFBBAA99.toInt(),
        "steel"    to 0xFFCCCCDD.toInt(), "fairy"    to 0xFFFF99CC.toInt()
    )
    val STATUS_COLORS = mapOf(
        "psn" to 0xFFBB66FF.toInt(), "tox" to 0xFF9944CC.toInt(),
        "brn" to 0xFFFF6B35.toInt(), "slp" to 0xFF89B4FA.toInt(),
        "par" to 0xFFFFD93D.toInt(), "frz" to 0xFF74C7EC.toInt()
    )
    val STATUS_LABELS = mapOf(
        "psn" to "PSN", "tox" to "TOX", "brn" to "BRN",
        "slp" to "SLP", "par" to "PAR", "frz" to "FRZ"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Called every render frame from PvpOverlay. */
    fun sync() {
        try {
            val battle = CobblemonClient.battle ?: return
            val myUuid = MinecraftClient.getInstance().player?.uuid

            var playerActor   : ClientBattleActor?     = null
            var opponentActors: List<ClientBattleActor> = emptyList()

            outer@ for (side in arrayOf(battle.side1, battle.side2)) {
                for (actor in side.actors) {
                    if (actor.uuid == myUuid) {
                        playerActor    = actor
                        val other      = if (side === battle.side1) battle.side2 else battle.side1
                        opponentActors = other.actors.toList()
                        break@outer
                    }
                }
            }

            playerTeam = playerActor?.let { buildOwnTeam(it) } ?: emptyList()

            opponentIsPlayerFlag = opponentActors.any { a ->
                try { MinecraftClient.getInstance().world?.getPlayerByUuid(a.uuid) != null }
                catch (_: Exception) { false }
            }

            for (actor in opponentActors) {
                for (active in actor.activePokemon) {
                    val bp  = active.battlePokemon ?: continue
                    val key = canonicalKey(bp.properties.species ?: continue)
                    val hp  = calcHpPercent(bp)
                    opponentHpMap[key]   = hp
                    opponentFainted[key] = hp <= 0f
                    opponentStatus[key]  = try { bp.status?.showdownName } catch (_: Exception) { null }
                    opponentAspects[key] = try { bp.state.currentAspects } catch (_: Exception) { emptySet() }
                }
            }

            opponentTeam = buildOpponentTeam()

        } catch (_: Exception) {}
    }

    // Vrai si l'acteur adverse est un joueur chargé dans le monde (combat PvP)
    var opponentIsPlayerFlag = false; private set
    fun opponentIsPlayer(): Boolean = opponentIsPlayerFlag

    fun clearState() {
        opponentIsPlayerFlag = false
        playerTeam   = emptyList()
        opponentTeam = emptyList()
        opponentHpMap.clear()
        opponentFainted.clear()
        opponentStatus.clear()
        opponentAspects.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Player's own team
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildOwnTeam(actor: ClientBattleActor): List<TrackedMon> {
        return try {
            val list = actor.pokemon
            if (list.isNullOrEmpty()) return emptyList()

            // Live HP/status from currently active ClientBattlePokemon.
            // Pokemon.currentHealth doesn't update mid-battle; hpValue does.
            data class LiveState(val hp: Float, val status: String?)
            val liveMap = mutableMapOf<String, LiveState>()
            for (active in actor.activePokemon) {
                val bp  = active.battlePokemon ?: continue
                val key = bp.properties.species?.lowercase() ?: continue
                liveMap[key] = LiveState(
                    hp     = calcHpPercent(bp),
                    status = try { bp.status?.showdownName } catch (_: Exception) { null }
                )
            }

            list.mapNotNull { p ->
                try {
                    var mon = fromPokemon(p)
                    liveMap[mon.speciesId]?.let { live ->
                        mon = mon.copy(
                            hpPercent = live.hp,
                            isFainted = live.hp <= 0f,
                            statusKey = live.status
                        )
                    }
                    mon
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun fromPokemon(p: Pokemon): TrackedMon {
        val speciesId   = p.species.resourceIdentifier.path
        val aspects     = try { p.aspects } catch (_: Exception) { emptySet() }
        val hpMax       = p.maxHealth
        val hpCurrent   = p.currentHealth
        val hpPercent   = if (hpMax > 0) hpCurrent.toFloat() / hpMax else 0f
        val isFainted   = p.isFainted()
        val statusKey   = try { p.status?.status?.showdownName } catch (_: Exception) { null }
        val types       = try { p.types.map { it.displayName.string } } catch (_: Exception) { emptyList() }
        val moves       = try {
            p.moveSet.getMoves().map { m ->
                BattleMove(
                    displayName = m.displayName.string,
                    typeName    = try { m.type.displayName.string } catch (_: Exception) { "" },
                    typeKey     = try { m.type.name.lowercase()   } catch (_: Exception) { "normal" },
                    currentPp   = m.currentPp,
                    maxPp       = m.maxPp
                )
            }
        } catch (_: Exception) { emptyList() }

        // ability.displayName / description return translation keys ("cobblemon.ability.strong_jaw")
        // → wrap in Text.translatable() to resolve the localized string
        val abilityName = try {
            net.minecraft.text.Text.translatable(p.ability.displayName).string
                .takeIf { it.isNotBlank() && !it.equals(p.ability.displayName, ignoreCase = true) }
                ?: p.ability.displayName
        } catch (_: Exception) { null }
        val abilityDesc = try {
            net.minecraft.text.Text.translatable(p.ability.description).string
                .takeIf { it.isNotBlank() && !it.equals(p.ability.description, ignoreCase = true) }
                ?: p.ability.description
        } catch (_: Exception) { null }

        val heldItem    = try { p.heldItem() } catch (_: Exception) { null } ?: ItemStack.EMPTY

        return TrackedMon(speciesId, aspects, hpPercent, isFainted, statusKey,
                          types, moves, abilityName, abilityDesc, heldItem, isOwn = true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Opponent's team
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildOpponentTeam(): List<TrackedMon> {
        val base = PvpDetector.opponentTeam
        if (base.isEmpty()) {
            // Repli sans preview : construire depuis les espèces vues en combat
            val vus = opponentHpMap.keys.take(6).map { key ->
                val aspectsVus = opponentAspects[key] ?: emptySet()
                TrackedMon(
                    speciesId   = key,
                    aspects     = aspectsVus,
                    hpPercent   = opponentHpMap[key] ?: 1f,
                    isFainted   = opponentFainted[key] ?: false,
                    statusKey   = opponentStatus[key],
                    types       = resolveTypes(key, aspectsVus),
                    moves       = emptyList(),
                    abilityName = null,
                    abilityDesc = null,
                    heldItem    = ItemStack.EMPTY,
                    isOwn       = false
                )
            }
            // Compléter à 6 cases : les slots pas encore vus s'affichent en "?"
            val inconnues = (vus.size until 6).map {
                TrackedMon("", emptySet(), 1f, false, null,
                    emptyList(), emptyList(), null, null, ItemStack.EMPTY, isOwn = false)
            }
            return vus + inconnues
        }

        return base.distinctBy { canonicalKey(it.speciesId) }.map { pvp ->
            val key       = canonicalKey(pvp.speciesId)
            val hpPercent = opponentHpMap[key] ?: 1f
            val isFainted = opponentFainted[key] ?: false
            val statusKey = opponentStatus[key]
            val types     = resolveTypes(pvp.speciesId, pvp.aspects)

            TrackedMon(pvp.speciesId, pvp.aspects, hpPercent, isFainted, statusKey,
                       types, emptyList(), null, null, ItemStack.EMPTY, isOwn = false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Clé d'espèce canonique : deux variantes de nom (majuscules, suffixes de
     *  forme, ids showdown) pointant vers la même espèce fusionnent. */
    private fun canonicalKey(raw: String): String = try {
        PokemonSpecies.getByName(raw.lowercase())?.resourceIdentifier?.path ?: raw.lowercase()
    } catch (_: Exception) { raw.lowercase() }

    private fun resolveTypes(speciesId: String, aspects: Set<String>): List<String> {
        return try {
            val species = PokemonSpecies.getByName(speciesId) ?: return emptyList()
            val form    = species.getForm(aspects)
            form.types.map { it.displayName.string }
        } catch (_: Exception) { emptyList() }
    }

    internal fun calcHpPercent(bp: ClientBattlePokemon): Float = try {
        if (bp.isHpFlat) {
            if (bp.maxHp > 0f) bp.hpValue / bp.maxHp else 1f
        } else {
            bp.hpValue
        }
    } catch (_: Exception) { 1f }
}
