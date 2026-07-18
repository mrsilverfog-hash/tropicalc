// Stub de configuration minimal pour le portage TropiHunterBoard -> TropiCalc.
// Positions en mémoire uniquement (non persistées entre les sessions).
package com.tropimon.tropicalc.pvp

object ModConfig {
    var pvpOverlayEnabled = true

    var pvpPlayerX = -1; private set
    var pvpPlayerY = -1; private set
    var pvpOpponentX = -1; private set
    var pvpOpponentY = -1; private set

    fun setPvpPlayerPosition(x: Int, y: Int) {
        pvpPlayerX = x; pvpPlayerY = y
    }

    fun setPvpOpponentPosition(x: Int, y: Int) {
        pvpOpponentX = x; pvpOpponentY = y
    }

    fun accentColor(): Int = 0xFFE8B84B.toInt()  // or TropiCalc
    fun bgColor(): Int = 0xC0101010.toInt()
}
