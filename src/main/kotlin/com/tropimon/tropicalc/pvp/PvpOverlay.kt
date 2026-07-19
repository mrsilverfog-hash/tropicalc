// Adapté de TropiHunterBoard (https://github.com/PiikaPops/TropiHunterBoard)
// Copyright (c) PiikaPops — Licence MIT. Intégré dans TropiCalc avec attribution.
package com.tropimon.tropicalc.pvp

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.item.ItemStack
import org.lwjgl.glfw.GLFW

/**
 * Renders two draggable overlays during a PvP battle:
 *   - Player overlay   (accent-colour border) — full data from BattleTracker
 *   - Opponent overlay (red border)           — species + HP/status revealed during battle
 *
 * Features:
 *   • 32 px sprite icons in a 3×2 grid
 *   • Held-item icon in the bottom-right corner of each sprite cell
 *   • Fainted Pokémon: greyed out + semi-transparent overlay
 *   • Mouse-hover tooltip: types, HP bar, status, moves/PP, ability, item
 *   • Drag-and-drop repositioning persisted to ModConfig
 */
object PvpOverlay {

    // ── Grid geometry ──────────────────────────────────────────────────────────
    private const val COLS   = 3
    private const val ROWS   = 2
    private const val ICON   = 32     // sprite size px
    private const val GAP    = 4     // gap between icons px
    private const val PAD    = 5     // panel padding px
    private val PANEL_W = COLS * ICON + (COLS - 1) * GAP + PAD * 2   // 114 px
    private val PANEL_H = ROWS * ICON + (ROWS - 1) * GAP + PAD * 2   // 82 px

    // ── Battle lifecycle ───────────────────────────────────────────────────────
    private var wasInBattle = false

    // ── Drag state ─────────────────────────────────────────────────────────────
    private enum class Panel { NONE, PLAYER, OPPONENT }
    private var dragging      = Panel.NONE
    private var dragOffsetX   = 0
    private var dragOffsetY   = 0
    private var prevMouseDown = false

    // ── Runtime positions (populated on first render or from config) ───────────
    private var playerX   = -1;  private var playerY   = -1
    private var opponentX = -1;  private var opponentY = -1

    // ── Opponent border colour ─────────────────────────────────────────────────
    private val RED = 0xFFFF4444.toInt()

    // ── Hover / tooltip state (reset each frame before panel rendering) ────────
    private var tooltipMon : BattleTracker.TrackedMon? = null
    private var tooltipIsOwn : Boolean = true
    private var tooltipAnchorX = 0
    private var tooltipAnchorY = 0

    // ─────────────────────────────────────────────────────────────────────────
    fun register() {
        HudRenderCallback.EVENT.register { context, _ ->
            try { render(context) } catch (_: Exception) {}
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            try { handleDrag(client) } catch (_: Exception) {}
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────────

    private fun render(context: DrawContext) {
        if (!ModConfig.pvpOverlayEnabled) return

        val inBattle = BattleHelper.isInBattle()
        if (wasInBattle && !inBattle) {
            PvpDetector.clearTeam()
            BattleTracker.clearState()
        }
        wasInBattle = inBattle
        if (!inBattle) return
        // Repli : même sans preview détectée, activer si l'adversaire est un joueur
        BattleTracker.sync()
        if (!PvpDetector.pvpSessionActive && !BattleTracker.opponentIsPlayer()) return

        // Sync battle state every frame
        BattleTracker.sync()

        val client = MinecraftClient.getInstance()
        val sw = client.window.scaledWidth
        val sh = client.window.scaledHeight

        // Resolve auto-positions on first render
        if (playerX   == -1) playerX   = ModConfig.pvpPlayerX.takeIf   { it >= 0 } ?: 4
        if (playerY   == -1) playerY   = ModConfig.pvpPlayerY.takeIf   { it >= 0 } ?: (sh / 2 - PANEL_H / 2)
        if (opponentX == -1) opponentX = ModConfig.pvpOpponentX.takeIf { it >= 0 } ?: (sw - PANEL_W - 4)
        if (opponentY == -1) opponentY = ModConfig.pvpOpponentY.takeIf { it >= 0 } ?: (sh / 2 - PANEL_H / 2)

        // Mouse position in scaled coordinates
        val window = client.window
        val mx = (client.mouse.x * window.scaledWidth  / window.width.toDouble()).toInt()
        val my = (client.mouse.y * window.scaledHeight / window.height.toDouble()).toInt()

        // Reset tooltip
        tooltipMon = null

        // Resolve display teams: prefer BattleTracker data, fall back to PvpDetector
        val playerTeam   = resolvePlayerTeam()
        val opponentTeam = resolveOpponentTeam()

        renderPanel(context, playerX,   playerY,   playerTeam,   ModConfig.accentColor(), mx, my)
        renderPanel(context, opponentX, opponentY, opponentTeam, RED,                    mx, my)

        // Draw tooltip on top of everything
        tooltipMon?.let { renderTooltip(context, it, tooltipAnchorX, tooltipAnchorY, sw, sh) }
    }

    // ── Team resolution ────────────────────────────────────────────────────────

    private fun resolvePlayerTeam(): List<BattleTracker.TrackedMon> {
        val bt = BattleTracker.playerTeam
        if (bt.isNotEmpty()) return bt
        // Fallback: convert PvpDetector entries to TrackedMon with no battle data
        return PvpDetector.playerTeam.map { pvp ->
            BattleTracker.TrackedMon(
                speciesId   = pvp.speciesId,
                aspects     = pvp.aspects,
                hpPercent   = 1f,
                isFainted   = false,
                statusKey   = null,
                types       = resolveTypesStatic(pvp.speciesId, pvp.aspects),
                moves       = emptyList(),
                abilityName = null,
                abilityDesc = null,
                heldItem    = ItemStack.EMPTY,
                isOwn       = true
            )
        }
    }

    private fun resolveOpponentTeam(): List<BattleTracker.TrackedMon> {
        val bt = BattleTracker.opponentTeam
        if (bt.isNotEmpty()) return bt
        return PvpDetector.opponentTeam.map { pvp ->
            BattleTracker.TrackedMon(
                speciesId   = pvp.speciesId,
                aspects     = pvp.aspects,
                hpPercent   = 1f,
                isFainted   = false,
                statusKey   = null,
                types       = resolveTypesStatic(pvp.speciesId, pvp.aspects),
                moves       = emptyList(),
                abilityName = null,
                abilityDesc = null,
                heldItem    = ItemStack.EMPTY,
                isOwn       = false
            )
        }
    }

    private fun resolveTypesStatic(speciesId: String, aspects: Set<String>): List<String> {
        return try {
            val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(speciesId)
                ?: return emptyList()
            val form = species.getForm(aspects)
            form.types.map { it.displayName.string }
        } catch (_: Exception) { emptyList() }
    }

    // ── Panel rendering ────────────────────────────────────────────────────────

    private fun renderPanel(
        context: DrawContext,
        panelX: Int, panelY: Int,
        team: List<BattleTracker.TrackedMon>,
        borderColor: Int,
        mx: Int, my: Int
    ) {
        if (team.isEmpty()) return

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, ModConfig.bgColor())
        drawBorder(context, panelX, panelY, PANEL_W, PANEL_H, borderColor)

        team.forEachIndexed { index, mon ->
            val col = index % COLS
            val row = index / COLS
            val ix  = panelX + PAD + col * (ICON + GAP)
            val iy  = panelY + PAD + row * (ICON + GAP)

            // ── Sprite (tinted grey+transparent when fainted, respects transparency) ──
            val spriteId = try {
                SpriteHelper.getSpriteIdentifier(mon.speciesId, mon.aspects)
            } catch (_: Exception) { null }

            if (mon.isFainted) RenderSystem.setShaderColor(0.35f, 0.35f, 0.35f, 0.6f)

            if (mon.speciesId.isEmpty()) {
                // Slot adverse pas encore vu : point d'interrogation centré
                val tr = MinecraftClient.getInstance().textRenderer
                context.drawText(tr, "?", ix + ICON / 2 - tr.getWidth("?") / 2,
                    iy + ICON / 2 - tr.fontHeight / 2, 0xFF666666.toInt(), false)
            } else if (spriteId != null) {
                context.drawTexture(spriteId, ix, iy, 0f, 0f, ICON, ICON, ICON, ICON)
            } else {
                val alpha = if (mon.isFainted) 0x22000000.toInt() else 0x33000000.toInt()
                context.fill(ix, iy, ix + ICON, iy + ICON,
                    borderColor and 0x00FFFFFF or alpha)
                drawBorder(context, ix, iy, ICON, ICON,
                    borderColor and 0x00FFFFFF or if (mon.isFainted) 0x44000000.toInt() else 0x88000000.toInt())
            }

            if (mon.isFainted) RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            // ── HP bar at bottom of sprite (3 px tall, 20 px wide, centred) ──
            if (!mon.isFainted) {
                val barW  = 20
                val barH  = 3
                val barX  = ix + (ICON - barW) / 2
                val barY  = iy + ICON - barH - 1
                val hpW   = (barW * mon.hpPercent).toInt().coerceIn(0, barW)
                val hpCol = when {
                    mon.hpPercent > 0.5f -> 0xFF44CC44.toInt()
                    mon.hpPercent > 0.2f -> 0xFFDDAA00.toInt()
                    else                 -> 0xFFCC3333.toInt()
                }
                context.fill(barX,        barY, barX + hpW,  barY + barH, hpCol)
                context.fill(barX + hpW,  barY, barX + barW, barY + barH, 0xFF222222.toInt())
            }

            // ── Status dot (top-left corner, 5×5) ──
            mon.statusKey?.let { key ->
                val dotColor = BattleTracker.STATUS_COLORS[key] ?: 0xFFAAAAAA.toInt()
                context.fill(ix, iy, ix + 5, iy + 5, dotColor)
                // Marqueur résiduel : contour de cellule pour les statuts qui rongent (adverse)
                if (!mon.isOwn && (key == "psn" || key == "tox" || key == "brn")) {
                    context.fill(ix, iy, ix + ICON, iy + 1, dotColor)
                    context.fill(ix, iy + ICON - 1, ix + ICON, iy + ICON, dotColor)
                    context.fill(ix, iy, ix + 1, iy + ICON, dotColor)
                    context.fill(ix + ICON - 1, iy, ix + ICON, iy + ICON, dotColor)
                }
            }

            // ── Held item icon (bottom-right corner, scaled to ~10×10) ──
            if (!mon.heldItem.isEmpty) {
                val scale  = 0.625f   // 16 * 0.625 = 10 px
                val itemSx = ix + ICON - 10   // screen target X
                val itemSy = iy + ICON - 10   // screen target Y
                try {
                    context.matrices.push()
                    context.matrices.scale(scale, scale, 1f)
                    context.drawItem(mon.heldItem,
                        (itemSx / scale).toInt(),
                        (itemSy / scale).toInt())
                    context.matrices.pop()
                } catch (_: Exception) {}
            }

            // ── Hover detection ──
            if (mon.speciesId.isNotEmpty()
                && mx >= ix && mx <= ix + ICON && my >= iy && my <= iy + ICON) {
                tooltipMon      = mon
                tooltipIsOwn    = mon.isOwn
                // Anchor tooltip to the right of the panel; if it's the last column go left
                tooltipAnchorX  = panelX + PANEL_W + 4
                tooltipAnchorY  = iy
            }
        }
    }

    // ── Tooltip ────────────────────────────────────────────────────────────────

    private fun renderTooltip(
        context: DrawContext,
        mon: BattleTracker.TrackedMon,
        anchorX: Int, anchorY: Int,
        sw: Int, sh: Int
    ) {
        val tr    = MinecraftClient.getInstance().textRenderer
        val fh    = tr.fontHeight   // typically 9
        val pad   = 5

        // Build lines
        data class Line(val text: String, val color: Int, val indent: Int = 0)
        val lines = mutableListOf<Line>()

        // ── Species ──
        val speciesName = try {
            com.cobblemon.mod.common.api.pokemon.PokemonSpecies
                .getByName(mon.speciesId)?.translatedName?.string
                ?: mon.speciesId.replaceFirstChar { it.uppercaseChar() }
        } catch (_: Exception) { mon.speciesId.replaceFirstChar { it.uppercaseChar() } }

        val hpPct = (mon.hpPercent * 100).toInt()
        lines += Line("$speciesName  ${hpPct}%",
            if (mon.isFainted) 0xFF666666.toInt() else 0xFFFFFFFF.toInt())

        // ── Types ──
        if (mon.types.isNotEmpty()) {
            val typeStr = mon.types.joinToString(" / ")
            lines += Line(typeStr, 0xFFCCCCCC.toInt())
        }

        // ── Status ──
        mon.statusKey?.let { key ->
            val label = BattleTracker.STATUS_LABELS[key] ?: key.uppercase()
            val color = BattleTracker.STATUS_COLORS[key] ?: 0xFFAAAAAA.toInt()
            lines += Line("▸ $label", color)
        }

        val speedTier = getSpeedTier(mon.speciesId, mon.aspects)

        // ── Separator ──
        if (speedTier != null || mon.moves.isNotEmpty() || mon.abilityName != null || !mon.heldItem.isEmpty) {
            lines += Line("──────────────────", 0xFF444444.toInt())
        }

        if (mon.isOwn) {
            // ── Speed tier (own Pokémon) ──
            if (speedTier != null) {
                lines += Line("Vitesse: ${speedTier.min}–${speedTier.max}", 0xFF99FFCC.toInt())
                speedTier.slowStartRange?.let { (sMin, sMax) ->
                    lines += Line("Slow Start: $sMin–$sMax", 0xFFCCAA44.toInt())
                }
            }

            // ── Moves (own Pokémon) ──
            for (move in mon.moves) {
                val typeColor = BattleTracker.TYPE_TEXT_COLORS[move.typeKey] ?: 0xFFCCCCCC.toInt()
                val ppRatio   = if (move.maxPp == 0) 0f else move.currentPp.toFloat() / move.maxPp
                val ppColor   = when {
                    move.maxPp == 0  -> 0xFF888888.toInt()
                    ppRatio > 0.5f   -> 0xFF88DD88.toInt()
                    ppRatio > 0.25f  -> 0xFFDDDD44.toInt()
                    else             -> 0xFFDD4444.toInt()
                }
                lines += Line("▸ ${move.displayName}  (${move.typeName})", typeColor)
                lines += Line("PP: ${move.currentPp}/${move.maxPp}", ppColor, indent = 4)
            }

            // ── Ability (own Pokémon) — no long description ──
            if (mon.abilityName != null) {
                if (mon.moves.isNotEmpty() || speedTier != null) lines += Line("──────────────────", 0xFF444444.toInt())
                lines += Line("Talent: ${mon.abilityName}", 0xFFAADDFF.toInt())
            }

            // ── Held item (own Pokémon) ──
            if (!mon.heldItem.isEmpty) {
                lines += Line("Objet: ${mon.heldItem.name.string}", 0xFFFFDD88.toInt())
            }

        } else {
            // ── Speed tier (opponent) ──
            if (speedTier != null) {
                lines += Line("Vitesse: ${speedTier.min}–${speedTier.max}", 0xFF99FFCC.toInt())
                speedTier.slowStartRange?.let { (sMin, sMax) ->
                    lines += Line("Slow Start: $sMin–$sMax", 0xFFCCAA44.toInt())
                }
            }

            // ── All possible abilities (opponent) ──
            val allAbilities = getPossibleAbilities(mon.speciesId, mon.aspects)
            if (allAbilities.isNotEmpty()) {
                lines += Line(allAbilities.joinToString(" / "), 0xFFAADDFF.toInt())
            } else {
                lines += Line("Talent: ?", 0xFF666666.toInt())
            }

            // ── Données TropiCalc (observation + scouting) ──
            try {
                val obs = com.tropimon.tropicalc.battle.ObservationCollector::class.java
                val espece = mon.speciesId

                val objetConfirme = com.tropimon.tropicalc.battle.ObservationCollector.getObjetConfirme(espece)
                val talentConfirme = com.tropimon.tropicalc.battle.ObservationCollector.getTalentConfirme(espece)
                val chip = com.tropimon.tropicalc.battle.ObservationCollector.aChipTalentConfirme(espece)
                val scout = com.tropimon.tropicalc.battle.ScoutingStore.get(
                    com.tropimon.tropicalc.battle.ObservationCollector.getNomAdversaireCourant(), espece)
                val reveles = com.tropimon.tropicalc.battle.ObservationCollector.getCoupsAdversaireReveles(espece)

                if (objetConfirme != null || talentConfirme != null || chip
                    || (scout != null && (scout.objet != null || scout.talent != null))
                    || reveles.isNotEmpty()) {
                    lines += Line("──── TropiCalc ────", 0xFF444444.toInt())
                    when {
                        objetConfirme != null -> lines += Line("Objet: $objetConfirme ✓", 0xFFFFDD88.toInt())
                        scout?.objet != null  -> lines += Line("Objet: ${scout.objet}?", 0xFFBBA866.toInt())
                    }
                    when {
                        talentConfirme != null -> lines += Line("Talent: $talentConfirme ✓", 0xFFAADDFF.toInt())
                        chip                   -> lines += Line("Talent: Épine de Fer/Peau Dure ✓", 0xFFAADDFF.toInt())
                        scout?.talent != null  -> lines += Line("Talent: ${scout.talent}?", 0xFF7899AA.toInt())
                    }
                    for (t in reveles) {
                        val maxPp = (t.pp * 8) / 5
                        val restants = (maxPp - com.tropimon.tropicalc.battle.ObservationCollector
                            .getPpUtilises(espece, t.name)).coerceAtLeast(0)
                        val ppColor = when {
                            restants > maxPp / 2 -> 0xFF88DD88.toInt()
                            restants > maxPp / 4 -> 0xFFDDDD44.toInt()
                            else                 -> 0xFFDD4444.toInt()
                        }
                        lines += Line("▸ ${t.displayName.string}  PP $restants/$maxPp", ppColor)
                    }
                }
            } catch (_: Exception) {}

        }

        // ── Measure tooltip dimensions ──
        val contentW = lines.maxOfOrNull { tr.getWidth(it.text) + it.indent } ?: 80
        val tooltipW = contentW + pad * 2
        val tooltipH = lines.size * (fh + 1) + pad * 2

        // HP bar extra height
        val hpBarH = 5
        val totalH = tooltipH + hpBarH + 3

        // ── Position (stay on screen) ──
        var tx = anchorX
        var ty = anchorY
        if (tx + tooltipW > sw - 2) tx = anchorX - tooltipW - PANEL_W - 8
        if (tx < 2) tx = 2
        if (ty + totalH > sh - 2) ty = sh - totalH - 2
        if (ty < 2) ty = 2

        // ── Background & border ──
        context.fill(tx - 1, ty - 1, tx + tooltipW + 1, ty + totalH + 1, 0xDD000000.toInt())
        drawBorder(context, tx - 1, ty - 1, tooltipW + 2, totalH + 2, 0xFF333333.toInt())

        // ── HP bar ──
        val barX  = tx + pad
        val barY  = ty + pad
        val barW  = tooltipW - pad * 2
        val hpW   = (barW * mon.hpPercent).toInt().coerceIn(0, barW)
        val hpCol = when {
            mon.hpPercent > 0.5f -> 0xFF44CC44.toInt()
            mon.hpPercent > 0.2f -> 0xFFDDAA00.toInt()
            else                 -> 0xFFCC3333.toInt()
        }
        context.fill(barX,       barY, barX + hpW,  barY + hpBarH, hpCol)
        context.fill(barX + hpW, barY, barX + barW, barY + hpBarH, 0xFF333333.toInt())

        // ── Text lines ──
        var lineY = ty + pad + hpBarH + 3
        for (line in lines) {
            context.drawText(tr, line.text, tx + pad + line.indent, lineY, line.color, false)
            lineY += fh + 1
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drag logic (GLFW-polled, works inside Cobblemon battle screen)
    // ──────────────────────────────────────────────────────────────────────────

    private fun handleDrag(client: MinecraftClient) {
        if (!ModConfig.pvpOverlayEnabled) return
        if (!BattleHelper.isInBattle()) return
        if (playerX == -1 || opponentX == -1) return

        val window    = client.window
        val mouseDown = GLFW.glfwGetMouseButton(window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val mx = (client.mouse.x * window.scaledWidth  / window.width.toDouble()).toInt()
        val my = (client.mouse.y * window.scaledHeight / window.height.toDouble()).toInt()

        if (mouseDown && !prevMouseDown) {
            dragging = when {
                isInside(mx, my, playerX,   playerY)   -> Panel.PLAYER
                isInside(mx, my, opponentX, opponentY) -> Panel.OPPONENT
                else -> Panel.NONE
            }
            if (dragging == Panel.PLAYER)   { dragOffsetX = mx - playerX;   dragOffsetY = my - playerY }
            if (dragging == Panel.OPPONENT) { dragOffsetX = mx - opponentX; dragOffsetY = my - opponentY }

        } else if (mouseDown && dragging != Panel.NONE) {
            val sw = window.scaledWidth
            val sh = window.scaledHeight
            val nx = (mx - dragOffsetX).coerceIn(0, sw - PANEL_W)
            val ny = (my - dragOffsetY).coerceIn(0, sh - PANEL_H)
            if (dragging == Panel.PLAYER)   { playerX   = nx; playerY   = ny }
            if (dragging == Panel.OPPONENT) { opponentX = nx; opponentY = ny }

        } else if (!mouseDown && prevMouseDown && dragging != Panel.NONE) {
            ModConfig.setPvpPlayerPosition(playerX, playerY)
            ModConfig.setPvpOpponentPosition(opponentX, opponentY)
            dragging = Panel.NONE
        }

        prevMouseDown = mouseDown
    }

    private fun isInside(mx: Int, my: Int, px: Int, py: Int) =
        mx >= px && mx <= px + PANEL_W && my >= py && my <= py + PANEL_H

    // ── Species helpers ───────────────────────────────────────────────────────

    private data class SpeedRange(val min: Int, val max: Int, val slowStartRange: Pair<Int, Int>? = null)

    /**
     * Speed stat range at level 100 (0 IV / 0 EV / -nature → 31 IV / 252 EV / +nature).
     * Formula: floor((2*base + iv + floor(ev/4) + 5) * nature)
     *   min: floor((2*base + 5)  * 0.9)   [IV=0, EV=0, -nature]
     *   max: floor((2*base + 99) * 1.1)   [IV=31, EV=252, +nature]
     * If the form has Slow Start, also provides the halved in-battle range.
     */
    private fun getSpeedTier(speciesId: String, aspects: Set<String>): SpeedRange? {
        return try {
            val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies
                .getByName(speciesId) ?: return null
            val form  = species.getForm(aspects)
            val base  = form.baseStats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED]
                ?: return null
            val min = Math.floor((2.0 * base + 5)  * 0.9).toInt()
            val max = Math.floor((2.0 * base + 99) * 1.1).toInt()
            // Slow Start halves Speed in battle for the first 5 turns
            val hasSlowStart = form.abilities.any { pa ->
                try { pa.template.name.replace("_", "").replace(" ", "").lowercase() == "slowstart" }
                catch (_: Exception) { false }
            }
            val slowRange = if (hasSlowStart)
                Pair(Math.floor(min / 2.0).toInt(), Math.floor(max / 2.0).toInt())
            else null
            SpeedRange(min, max, slowRange)
        } catch (_: Exception) { null }
    }

    /**
     * Returns the localized display names of all abilities in the species/form's ability pool
     * (slot 1, slot 2, hidden ability), deduplicated.
     */
    private fun getPossibleAbilities(speciesId: String, aspects: Set<String>): List<String> {
        return try {
            val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies
                .getByName(speciesId) ?: return emptyList()
            val form = species.getForm(aspects)
            buildList {
                for (pa in form.abilities) {
                    val key = try { pa.template.displayName } catch (_: Exception) { continue }
                    val name = try {
                        net.minecraft.text.Text.translatable(key).string
                            .takeIf { it.isNotBlank() && it != key }
                            ?: key
                    } catch (_: Exception) { key }
                    if (name.isNotBlank() && !contains(name)) add(name)
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.fill(x,         y,         x + w,     y + 1,     color)
        context.fill(x,         y + h - 1, x + w,     y + h,     color)
        context.fill(x,         y,         x + 1,     y + h,     color)
        context.fill(x + w - 1, y,         x + w,     y + h,     color)
    }

    fun resetPositions() {
        playerX = -1; playerY = -1
        opponentX = -1; opponentY = -1
    }
}
