package com.c016.pushupflappy.game.zone

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopingWorldTest {
    private val world = LoopingWorld(8f)

    @Test
    fun wrapsIntoRange() {
        assertEquals(1f, world.wrap(9f), 1e-5f)
        assertEquals(7f, world.wrap(-1f), 1e-5f)
        assertEquals(0f, world.wrap(8f), 1e-5f)
    }

    @Test
    fun deltaTakesTheShortWayAround() {
        assertEquals(2f, world.delta(1f, 3f), 1e-5f)
        assertEquals(-2f, world.delta(3f, 1f), 1e-5f)
        assertEquals(1f, world.delta(7.5f, 0.5f), 1e-5f)
        assertEquals(-1f, world.delta(0.5f, 7.5f), 1e-5f)
    }
}
