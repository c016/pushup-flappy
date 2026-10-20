package com.c016.pushupflappy.game.shooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerUpGaugeTest {

    @Test
    fun capsulesMoveTheCursorAndWrap() {
        val gauge = PowerUpGauge()
        assertNull(gauge.selected)
        gauge.collectCapsule()
        assertEquals(PowerUp.SPEED, gauge.selected)
        repeat(PowerUp.entries.size) { gauge.collectCapsule() }
        assertEquals(PowerUp.SPEED, gauge.selected)
    }

    @Test
    fun activatingBuysTheSelectionAndClearsTheCursor() {
        val gauge = PowerUpGauge()
        repeat(2) { gauge.collectCapsule() }
        assertEquals(PowerUp.MISSILE, gauge.activate())
        assertEquals(1, gauge.level(PowerUp.MISSILE))
        assertNull(gauge.selected)
        assertNull(gauge.activate())
    }

    @Test
    fun maxedPowerUpsCannotBeBoughtAndKeepTheCursor() {
        val gauge = PowerUpGauge()
        repeat(2) { gauge.collectCapsule() }
        gauge.activate()
        repeat(2) { gauge.collectCapsule() }
        // MISSILE has a single level, so the second purchase fails.
        assertNull(gauge.activate())
        assertEquals(1, gauge.level(PowerUp.MISSILE))
        assertEquals(PowerUp.MISSILE, gauge.selected)
    }

    @Test
    fun resetLosesEverything() {
        val gauge = PowerUpGauge()
        gauge.collectCapsule()
        gauge.activate()
        gauge.collectCapsule()
        gauge.reset()
        assertEquals(0, gauge.level(PowerUp.SPEED))
        assertNull(gauge.selected)
    }
}
