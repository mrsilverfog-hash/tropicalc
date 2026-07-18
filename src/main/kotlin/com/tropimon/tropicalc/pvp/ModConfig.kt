// Configuration du panneau PvP : positions persistées dans
// config/tropicalc-panels.properties (chargées au premier accès, écrites au drop).
package com.tropimon.tropicalc.pvp

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.util.Properties

object ModConfig {
    private var _enabled = true
    private var _playerX = -1
    private var _playerY = -1
    private var _opponentX = -1
    private var _opponentY = -1
    private var charge = false

    val pvpOverlayEnabled: Boolean get() { charger(); return _enabled }
    val pvpPlayerX: Int get() { charger(); return _playerX }
    val pvpPlayerY: Int get() { charger(); return _playerY }
    val pvpOpponentX: Int get() { charger(); return _opponentX }
    val pvpOpponentY: Int get() { charger(); return _opponentY }

    private fun fichier() =
        FabricLoader.getInstance().configDir.resolve("tropicalc-panels.properties")

    private fun charger() {
        if (charge) return
        charge = true
        try {
            val f = fichier()
            if (Files.exists(f)) {
                val p = Properties()
                Files.newInputStream(f).use { p.load(it) }
                _playerX = p.getProperty("playerX", "-1").toInt()
                _playerY = p.getProperty("playerY", "-1").toInt()
                _opponentX = p.getProperty("opponentX", "-1").toInt()
                _opponentY = p.getProperty("opponentY", "-1").toInt()
                _enabled = p.getProperty("enabled", "true").toBoolean()
            }
        } catch (_: Exception) {
        }
    }

    private fun sauvegarder() {
        try {
            val p = Properties()
            p.setProperty("playerX", _playerX.toString())
            p.setProperty("playerY", _playerY.toString())
            p.setProperty("opponentX", _opponentX.toString())
            p.setProperty("opponentY", _opponentY.toString())
            p.setProperty("enabled", _enabled.toString())
            Files.newOutputStream(fichier()).use { p.store(it, "TropiCalc - positions des panneaux PvP") }
        } catch (_: Exception) {
        }
    }

    fun setPvpPlayerPosition(x: Int, y: Int) {
        charger()
        _playerX = x; _playerY = y
        sauvegarder()
    }

    fun setPvpOpponentPosition(x: Int, y: Int) {
        charger()
        _opponentX = x; _opponentY = y
        sauvegarder()
    }

    fun accentColor(): Int = 0xFFE8B84B.toInt()  // or TropiCalc
    fun bgColor(): Int = 0xC0101010.toInt()
}
