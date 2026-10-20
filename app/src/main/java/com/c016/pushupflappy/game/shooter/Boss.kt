package com.c016.pushupflappy.game.shooter

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The end-of-stage battleship. Its core can only be damaged once the shield plates in front of it
 * are destroyed, outermost first. Positions are the ship's centre in world units; the offsets match
 * the art in tools/shooter_art.py at [PIXEL] screen heights per art pixel.
 */
class Boss(var x: Float, var y: Float, coreHp: Int) {

    enum class Hit { NONE, ABSORBED, PLATE, CORE }

    /** Remaining hit points per plate, outermost (leftmost) first. */
    val plates = IntArray(PLATE_OFFSETS.size) { PLATE_HP }
    var coreHp = coreHp
        private set
    var age = 0f

    /** Time since the boss reached its fighting position; drives its up-and-down sweep. */
    var settledTime = 0f
    var fireTimer = 2f
    var attack = 0
    var flash = 0f

    val defeated: Boolean get() = coreHp <= 0
    val coreExposed: Boolean get() = plates.all { it <= 0 }

    fun coreX() = x + CORE_OFFSET
    fun plateX(index: Int) = x + PLATE_OFFSETS[index]

    /** Applies a shot at ([px], [py]); returns what it struck. */
    fun hit(px: Float, py: Float, r: Float): Hit {
        for (i in plates.indices) {
            if (plates[i] <= 0) continue
            if (abs(px - plateX(i)) < PLATE_HALF_WIDTH + r && abs(py - y) < PLATE_HALF_HEIGHT + r) {
                plates[i]--
                flash = 0.06f
                return Hit.PLATE
            }
        }
        if (coreExposed && hypot(px - coreX(), py - y) < CORE_RADIUS + r) {
            coreHp--
            flash = 0.06f
            return Hit.CORE
        }
        return if (touchesHull(px, py, r)) Hit.ABSORBED else Hit.NONE
    }

    /** Whether something at ([px], [py]) with radius [r] collides with the hull. */
    fun touchesHull(px: Float, py: Float, r: Float): Boolean =
        px + r > x + HULL_LEFT && px - r < x + HULL_RIGHT && abs(py - y) < HULL_HALF_HEIGHT + r

    companion object {
        const val PIXEL = 0.005f
        const val PLATE_HP = 6
        const val CORE_RADIUS = 6 * PIXEL
        const val CORE_OFFSET = -16 * PIXEL
        val PLATE_OFFSETS = floatArrayOf(-38 * PIXEL, -33 * PIXEL, -28 * PIXEL)
        const val PLATE_HALF_WIDTH = 3 * PIXEL
        const val PLATE_HALF_HEIGHT = 10 * PIXEL
        const val HULL_LEFT = -26 * PIXEL
        const val HULL_RIGHT = 28 * PIXEL
        const val HULL_HALF_HEIGHT = 18 * PIXEL
    }
}
