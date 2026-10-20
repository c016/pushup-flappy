package com.c016.pushupflappy.game.zone

/** Math for a world that wraps around horizontally every [width] units. */
class LoopingWorld(val width: Float) {

    /** [x] brought into [0, width). */
    fun wrap(x: Float): Float {
        val r = x % width
        return if (r < 0f) r + width else r
    }

    /** Shortest signed distance from [from] to [to] around the loop, in [-width/2, width/2). */
    fun delta(from: Float, to: Float): Float {
        val d = wrap(to - from)
        return if (d >= width / 2f) d - width else d
    }
}
