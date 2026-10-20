package com.c016.pushupflappy.game.shooter

import kotlin.random.Random

/**
 * A scrolling ceiling and floor built from evenly spaced columns. Depths are measured inwards from
 * the top and bottom screen edges in world units (screen heights); 0 means open space.
 */
class Terrain(private val random: Random = Random.Default) {

    class Column(var x: Float, val ceiling: Float, val floor: Float)

    val columns = ArrayDeque<Column>()

    /** Depth the terrain grows towards; set it to 0 to let the terrain recede. */
    var targetDepth = 0f

    private var ceilingDepth = 0f
    private var floorDepth = 0f

    /** Whether any column on screen still has terrain. */
    val isActive: Boolean get() = columns.any { it.ceiling > 0f || it.floor > 0f }

    /** Scrolls by [distance] and keeps columns generated up to [rightEdge]. Returns the new columns. */
    fun scroll(distance: Float, rightEdge: Float): List<Column> {
        for (c in columns) c.x -= distance
        while (columns.isNotEmpty() && columns.first().x < -COLUMN_WIDTH * 2) columns.removeFirst()
        val added = mutableListOf<Column>()
        while ((columns.lastOrNull()?.x ?: -COLUMN_WIDTH) < rightEdge + COLUMN_WIDTH) {
            val x = (columns.lastOrNull()?.x ?: -COLUMN_WIDTH) + COLUMN_WIDTH
            ceilingDepth = nextDepth(ceilingDepth)
            floorDepth = nextDepth(floorDepth)
            val column = Column(x, ceilingDepth, floorDepth)
            columns.addLast(column)
            added.add(column)
        }
        return added
    }

    private fun nextDepth(current: Float): Float {
        if (targetDepth <= 0f) return (current - STEP).coerceAtLeast(0f)
        val wander = (random.nextFloat() * 2f - 1f) * STEP
        val pull = (targetDepth - current) * 0.25f
        return (current + wander + pull).coerceIn(0f, targetDepth * 1.5f)
    }

    fun ceilingAt(x: Float): Float = depthAt(x) { it.ceiling }

    fun floorAt(x: Float): Float = depthAt(x) { it.floor }

    /** Whether a circle at ([x], [y]) with radius [r] touches the ceiling or floor. */
    fun hits(x: Float, y: Float, r: Float): Boolean = y - r < ceilingAt(x) || y + r > 1f - floorAt(x)

    private inline fun depthAt(x: Float, depth: (Column) -> Float): Float {
        val i = columns.indexOfFirst { it.x > x }
        if (i <= 0) return 0f
        val a = columns[i - 1]
        val b = columns[i]
        val t = (x - a.x) / (b.x - a.x)
        return depth(a) + (depth(b) - depth(a)) * t
    }

    companion object {
        const val COLUMN_WIDTH = 0.06f
        private const val STEP = 0.02f
    }
}
