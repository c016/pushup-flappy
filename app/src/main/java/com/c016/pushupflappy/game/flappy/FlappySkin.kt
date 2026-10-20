package com.c016.pushupflappy.game.flappy

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Layout shared by [FlappyGame] and every skin, in units of the screen height. */
object FlappyWorld {
    /** Where the player dies on the ground. */
    const val GROUND_Y = 0.87f
    const val PLAYER_X = 0.55f
    const val PLAYER_RADIUS = 0.04f
}

enum class FlappyTitle { GET_READY, GAME_OVER }

enum class FlappyMedal { BRONZE, SILVER, GOLD }

/**
 * A visual theme for [FlappyGame]. Besides drawing, a skin owns the obstacle shape so that
 * collisions always match what the player sees. Coordinates are world units (see [FlappyWorld])
 * unless a parameter says otherwise; [u] converts them to pixels.
 */
abstract class FlappySkin(pixelated: Boolean) {

    protected val paint = Paint(if (pixelated) 0 else Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    protected abstract val background: Bitmap

    /** Bottom edge of the background strip; the ground covers whatever lies below. */
    protected abstract val backgroundBottom: Float
    protected abstract val ground: Bitmap
    protected abstract val groundTop: Float
    protected abstract val playerFrames: List<Bitmap>

    /** Order in which [playerFrames] are shown while animating. */
    protected abstract val playerAnimation: IntArray
    protected abstract val playerWidth: Float
    protected abstract val digits: List<Bitmap>
    protected abstract val titles: Map<FlappyTitle, Bitmap>
    protected abstract val panel: Bitmap
    protected abstract val medals: Map<FlappyMedal, Bitmap>

    abstract val panelTextColor: Int

    /** How far an obstacle extends left or right of its x, for spawning and despawning. */
    abstract val obstacleReach: Float

    /** Whether flaps leave smoke puffs behind (drawn with [drawPuff]). */
    open val hasPuffs: Boolean = false

    abstract fun drawObstacle(canvas: Canvas, x: Float, gapTop: Float, gapBottom: Float, u: Float)

    /** Whether a circle at ([px], [py]) with radius [r] touches the obstacle. */
    abstract fun hitsObstacle(x: Float, gapTop: Float, gapBottom: Float, px: Float, py: Float, r: Float): Boolean

    /** [t] runs from 0 (new) to 1 (gone). */
    open fun drawPuff(canvas: Canvas, x: Float, y: Float, t: Float, large: Boolean, u: Float) = Unit

    fun drawBackground(canvas: Canvas, width: Int, u: Float, scroll: Float) =
        drawStrip(canvas, background, width, 0f, backgroundBottom * u, scroll * u)

    fun drawGround(canvas: Canvas, width: Int, u: Float, scroll: Float) =
        drawStrip(canvas, ground, width, groundTop * u, u, scroll * u)

    /** [animPhase] advances with time; [crashed] freezes the animation. */
    fun drawPlayer(canvas: Canvas, cy: Float, tiltDeg: Float, animPhase: Float, crashed: Boolean, u: Float) {
        val index = if (crashed) 0 else playerAnimation[(animPhase * PLAYER_FPS).toInt() % playerAnimation.size]
        val frame = playerFrames[index]
        val cx = FlappyWorld.PLAYER_X * u
        val halfWidth = playerWidth * u / 2f
        val halfHeight = halfWidth * frame.height / frame.width
        canvas.save()
        canvas.rotate(tiltDeg, cx, cy * u)
        drawBitmap(canvas, frame, cx - halfWidth, cy * u - halfHeight, cx + halfWidth, cy * u + halfHeight)
        canvas.restore()
    }

    /** Draws [value] with the digit sprites, centred on [cx]; all arguments in pixels. */
    fun drawNumber(canvas: Canvas, value: Int, cx: Float, top: Float, height: Float) {
        val glyphs = value.toString().map { digits[it - '0'] }
        val gap = height * 0.08f
        val total = glyphs.sumOf { (height * it.width / it.height).toDouble() }.toFloat() + gap * (glyphs.size - 1)
        var x = cx - total / 2
        for (glyph in glyphs) {
            val w = height * glyph.width / glyph.height
            drawBitmap(canvas, glyph, x, top, x + w, top + height)
            x += w + gap
        }
    }

    /** Pixels; keeps the title's aspect ratio. */
    fun drawTitle(canvas: Canvas, title: FlappyTitle, cx: Float, cy: Float, height: Float) {
        val bitmap = titles.getValue(title)
        val w = height * bitmap.width / bitmap.height
        drawBitmap(canvas, bitmap, cx - w / 2, cy - height / 2, cx + w / 2, cy + height / 2)
    }

    /** Pixels. */
    fun drawPanel(canvas: Canvas, bounds: RectF) =
        drawBitmap(canvas, panel, bounds.left, bounds.top, bounds.right, bounds.bottom)

    /** Pixels. */
    fun drawMedal(canvas: Canvas, medal: FlappyMedal, bounds: RectF) =
        drawBitmap(canvas, medals.getValue(medal), bounds.left, bounds.top, bounds.right, bounds.bottom)

    protected fun drawBitmap(canvas: Canvas, bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float, p: Paint = paint) {
        rect.set(left, top, right, bottom)
        canvas.drawBitmap(bitmap, null, rect, p)
    }

    /** Tiles [bitmap] horizontally across the screen between [top] and [bottom] pixels, scrolled by [offset] pixels. */
    private fun drawStrip(canvas: Canvas, bitmap: Bitmap, width: Int, top: Float, bottom: Float, offset: Float) {
        val tileWidth = (bottom - top) * bitmap.width / bitmap.height
        var x = -(offset % tileWidth)
        while (x < width) {
            drawBitmap(canvas, bitmap, x, top, x + tileWidth + 1f, bottom)
            x += tileWidth
        }
    }

    protected companion object {
        const val PLAYER_FPS = 10f

        fun Resources.bitmap(id: Int): Bitmap = BitmapFactory.decodeResource(this, id)
    }
}
