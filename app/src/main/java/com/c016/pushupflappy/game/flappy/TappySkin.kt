package com.c016.pushupflappy.game.flappy

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.c016.pushupflappy.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kenney "Tappy Plane" sprites (CC0, see licenses/kenney-tappy-plane-CC0.txt): a plane flying
 * between rocks. Each rock is a triangle whose tip points into the gap.
 */
class TappySkin(resources: Resources) : FlappySkin(pixelated = false) {

    override val background = resources.bitmap(R.drawable.tappy_background)
    override val backgroundBottom = 1f
    override val ground = resources.bitmap(R.drawable.tappy_ground)

    // The wavy grass line of the ground sprite sits around GROUND_Y.
    override val groundTop = 0.8f
    override val playerFrames = listOf(
        resources.bitmap(R.drawable.tappy_plane_1),
        resources.bitmap(R.drawable.tappy_plane_2),
        resources.bitmap(R.drawable.tappy_plane_3),
    )
    override val playerAnimation = intArrayOf(0, 1, 2)
    override val playerWidth = 0.13f
    override val digits = listOf(
        R.drawable.tappy_number_0, R.drawable.tappy_number_1, R.drawable.tappy_number_2,
        R.drawable.tappy_number_3, R.drawable.tappy_number_4, R.drawable.tappy_number_5,
        R.drawable.tappy_number_6, R.drawable.tappy_number_7, R.drawable.tappy_number_8,
        R.drawable.tappy_number_9,
    ).map { resources.bitmap(it) }
    override val titles = mapOf(
        FlappyTitle.GET_READY to resources.bitmap(R.drawable.tappy_text_get_ready),
        FlappyTitle.GAME_OVER to resources.bitmap(R.drawable.tappy_text_game_over),
    )
    override val panel = resources.bitmap(R.drawable.tappy_panel)
    override val medals = mapOf(
        FlappyMedal.BRONZE to resources.bitmap(R.drawable.tappy_medal_bronze),
        FlappyMedal.SILVER to resources.bitmap(R.drawable.tappy_medal_silver),
        FlappyMedal.GOLD to resources.bitmap(R.drawable.tappy_medal_gold),
    )
    override val panelTextColor = Color.rgb(90, 70, 40)
    override val obstacleReach = MAX_ROCK_WIDTH
    override val hasPuffs = true

    private val rockUp = resources.bitmap(R.drawable.tappy_rock_up)
    private val rockDown = resources.bitmap(R.drawable.tappy_rock_down)
    private val puffLarge = resources.bitmap(R.drawable.tappy_puff_large)
    private val puffSmall = resources.bitmap(R.drawable.tappy_puff_small)
    private val puffPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun rockWidth(height: Float) = (height * ROCK_ASPECT).coerceIn(MIN_ROCK_WIDTH, MAX_ROCK_WIDTH)

    // The lower rock's base sits at the bottom of the screen, hidden behind the ground strip.
    private fun lowerLeft(x: Float, gapBottom: Float) = x - rockWidth(1f - gapBottom) * ROCK_UP_TIP
    private fun lowerRight(x: Float, gapBottom: Float) = x + rockWidth(1f - gapBottom) * (1f - ROCK_UP_TIP)
    private fun upperLeft(x: Float, gapTop: Float) = x - rockWidth(gapTop) * ROCK_DOWN_TIP
    private fun upperRight(x: Float, gapTop: Float) = x + rockWidth(gapTop) * (1f - ROCK_DOWN_TIP)

    override fun drawObstacle(canvas: Canvas, x: Float, gapTop: Float, gapBottom: Float, u: Float) {
        drawBitmap(canvas, rockUp, lowerLeft(x, gapBottom) * u, gapBottom * u, lowerRight(x, gapBottom) * u, u)
        drawBitmap(canvas, rockDown, upperLeft(x, gapTop) * u, 0f, upperRight(x, gapTop) * u, gapTop * u)
    }

    override fun hitsObstacle(x: Float, gapTop: Float, gapBottom: Float, px: Float, py: Float, r: Float): Boolean {
        val ll = lowerLeft(x, gapBottom)
        val lr = lowerRight(x, gapBottom)
        val ul = upperLeft(x, gapTop)
        val ur = upperRight(x, gapTop)
        // Test the centre and points around the circle against both triangles.
        for (i in 0..HIT_SAMPLES) {
            val angle = i * (2.0 * Math.PI / HIT_SAMPLES)
            val sx = if (i == HIT_SAMPLES) px else px + r * cos(angle).toFloat()
            val sy = if (i == HIT_SAMPLES) py else py + r * sin(angle).toFloat()
            if (inTriangle(sx, sy, ll, 1f, lr, 1f, x, gapBottom)) return true
            if (inTriangle(sx, sy, ul, 0f, ur, 0f, x, gapTop)) return true
        }
        return false
    }

    private fun inTriangle(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        val d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by)
        val d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy)
        val d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay)
        val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
        val hasPos = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNeg && hasPos)
    }

    override fun drawPuff(canvas: Canvas, x: Float, y: Float, t: Float, large: Boolean, u: Float) {
        val bitmap = if (large) puffLarge else puffSmall
        val size = (if (large) 0.05f else 0.035f) * (0.7f + 0.8f * t) * u
        puffPaint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
        val h = size * bitmap.height / bitmap.width
        drawBitmap(canvas, bitmap, x * u - size / 2, y * u - h / 2, x * u + size / 2, y * u + h / 2, puffPaint)
    }

    private companion object {
        /** Width/height of the rock sprites (108x239) and where their tips sit across the width. */
        const val ROCK_ASPECT = 108f / 239f
        const val ROCK_UP_TIP = 0.61f
        const val ROCK_DOWN_TIP = 0.6f
        const val MIN_ROCK_WIDTH = 0.12f
        const val MAX_ROCK_WIDTH = 0.3f
        const val HIT_SAMPLES = 8
    }
}
