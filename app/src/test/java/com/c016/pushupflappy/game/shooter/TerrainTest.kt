package com.c016.pushupflappy.game.shooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TerrainTest {

    @Test
    fun growsTowardsTheTargetAndRecedes() {
        val terrain = Terrain(Random(3))
        terrain.scroll(0f, 2f)
        assertFalse(terrain.isActive)

        terrain.targetDepth = 0.1f
        repeat(200) { terrain.scroll(0.02f, 2f) }
        assertTrue(terrain.isActive)
        val depths = terrain.columns.map { it.ceiling } + terrain.columns.map { it.floor }
        assertTrue(depths.all { it in 0f..0.15f })
        assertEquals(0.1f, depths.average().toFloat(), 0.05f)

        terrain.targetDepth = 0f
        repeat(400) { terrain.scroll(0.02f, 2f) }
        assertFalse(terrain.isActive)
    }

    @Test
    fun interpolatesBetweenColumnsAndDetectsHits() {
        val terrain = Terrain(Random(1))
        terrain.targetDepth = 0.1f
        repeat(200) { terrain.scroll(0.02f, 2f) }
        val a = terrain.columns[5]
        val b = terrain.columns[6]
        val mid = (a.x + b.x) / 2f
        assertEquals((a.floor + b.floor) / 2f, terrain.floorAt(mid), 1e-4f)
        assertTrue(terrain.hits(mid, 1f - terrain.floorAt(mid) + 0.001f, 0.01f))
        assertFalse(terrain.hits(mid, 0.5f, 0.01f))
    }
}
