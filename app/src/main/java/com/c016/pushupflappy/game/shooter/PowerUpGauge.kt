package com.c016.pushupflappy.game.shooter

enum class PowerUp(val label: String, val maxLevel: Int) {
    SPEED("SPEED", 4),
    MISSILE("MISSILE", 1),
    DOUBLE("DOUBLE", 1),
    OPTION("OPTION", 2),
}

/**
 * The selectable power-up bar: each capsule moves the cursor one slot to the right (wrapping), and
 * a push-up buys the highlighted power-up and clears the cursor.
 */
class PowerUpGauge {
    private val levels = IntArray(PowerUp.entries.size)

    /** Index into [PowerUp.entries], or -1 when nothing is highlighted. */
    var cursor = -1
        private set

    val selected: PowerUp? get() = PowerUp.entries.getOrNull(cursor)

    fun level(powerUp: PowerUp) = levels[powerUp.ordinal]

    fun isMaxed(powerUp: PowerUp) = level(powerUp) >= powerUp.maxLevel

    fun collectCapsule() {
        cursor = (cursor + 1) % PowerUp.entries.size
    }

    /** Buys the highlighted power-up. Returns it, or null when nothing is highlighted or it is maxed out. */
    fun activate(): PowerUp? {
        val powerUp = selected ?: return null
        if (isMaxed(powerUp)) return null
        levels[powerUp.ordinal]++
        cursor = -1
        return powerUp
    }

    /** Losing a life loses every power-up. */
    fun reset() {
        levels.fill(0)
        cursor = -1
    }
}
