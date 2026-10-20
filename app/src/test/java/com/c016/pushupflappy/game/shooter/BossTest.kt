package com.c016.pushupflappy.game.shooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossTest {

    @Test
    fun platesShieldTheCoreOutermostFirst() {
        val boss = Boss(1f, 0.5f, coreHp = 2)
        val shotY = boss.y
        // A shot flying towards the core reaches the outermost plate first.
        assertEquals(Boss.Hit.PLATE, boss.hit(boss.plateX(0), shotY, 0.01f))
        repeat(Boss.PLATE_HP - 1) { boss.hit(boss.plateX(0), shotY, 0.01f) }
        assertEquals(0, boss.plates[0])
        assertTrue(boss.plates[1] > 0)
        assertFalse(boss.coreExposed)
    }

    @Test
    fun coreTakesDamageOnceExposed() {
        val boss = Boss(1f, 0.5f, coreHp = 2)
        assertEquals(Boss.Hit.ABSORBED, boss.hit(boss.coreX(), boss.y + Boss.PLATE_HALF_HEIGHT + 0.03f, 0.005f))
        for (i in boss.plates.indices) repeat(Boss.PLATE_HP) { boss.hit(boss.plateX(i), boss.y, 0.005f) }
        assertTrue(boss.coreExposed)
        assertEquals(Boss.Hit.CORE, boss.hit(boss.coreX(), boss.y, 0.005f))
        assertEquals(Boss.Hit.CORE, boss.hit(boss.coreX(), boss.y, 0.005f))
        assertTrue(boss.defeated)
    }

    @Test
    fun missesOutsideTheHull() {
        val boss = Boss(1f, 0.5f, coreHp = 2)
        assertEquals(Boss.Hit.NONE, boss.hit(boss.x, 0.05f, 0.01f))
    }
}
