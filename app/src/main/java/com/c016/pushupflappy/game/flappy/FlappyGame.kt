package com.c016.pushupflappy.game.flappy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.c016.pushupflappy.game.ControlMode
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.motion.PushUpDetector
import com.c016.pushupflappy.pose.Pose
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flappy Bird where each push-up is one flap, drawn with Kenney's Tappy Plane sprites.
 * In [ControlMode.LINKED] the plane instead follows the height of the player's body.
 *
 * World coordinates are in units of the screen height: y runs from 0 (top) to 1 (bottom) and x
 * from 0 to the screen aspect ratio. Physics are slowed down so that one flap per push-up keeps
 * the plane in the air.
 */
class FlappyGame(context: Context, private val options: GameOptions) : MiniGame {

    private enum class State { READY, PLAYING, GAME_OVER }

    /** A pair of rocks, one hanging from the top and one rising from the ground, whose tips line up at [x]. */
    private class Obstacle(var x: Float, val gapY: Float, var passed: Boolean = false)

    private class Puff(var x: Float, var y: Float, var age: Float, val large: Boolean)

    private class Tuning(flapIntervalSec: Float, val gap: Float) {
        // Choose gravity and flap speed so that flapping every flapIntervalSec keeps the plane
        // level, swinging up and down by AMPLITUDE: g = 8A/T^2, v = gT/2.
        val gravity = 8f * AMPLITUDE / (flapIntervalSec * flapIntervalSec)
        val flapSpeed = gravity * flapIntervalSec / 2f
        val maxFallSpeed = flapSpeed * 1.3f
        val obstacleSpacing = SCROLL_SPEED * flapIntervalSec * 2.5f
    }

    private val tuning = when (options.difficulty) {
        Difficulty.EASY -> Tuning(flapIntervalSec = 2.2f, gap = 0.46f)
        Difficulty.NORMAL -> Tuning(flapIntervalSec = 1.7f, gap = 0.38f)
        Difficulty.HARD -> Tuning(flapIntervalSec = 1.3f, gap = 0.32f)
    }

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    private val bestKey = "flappy_best_${options.difficulty.name.lowercase()}"
    private var best = prefs.getInt(bestKey, 0)

    private val sprites = FlappySprites(context.resources)
    private val linked = options.controlMode == ControlMode.LINKED

    // In linked mode the player may hold a position for a while, so the learned range must not fade quickly.
    private val detector = PushUpDetector(
        rangeMemorySec = if (linked) LINKED_RANGE_MEMORY_SEC else PushUpDetector.DEFAULT_RANGE_MEMORY_SEC
    )

    /** Linked mode: height the plane is steering towards, or null until the body's range is known. */
    private var targetY: Float? = null
    private var state = State.READY
    private var stateTime = 0f
    private var score = 0
    private var aspect = 16f / 9f

    private var planeY = 0.45f
    private var planeVy = 0f
    private var sinceFlap = 10f
    private val obstacles = mutableListOf<Obstacle>()
    private val puffs = mutableListOf<Puff>()
    private var groundOffset = 0f
    private var backgroundOffset = 0f

    /** Seconds since the body was last seen; the game pauses while it is out of view. */
    private var unseenSec = 0f

    // Paint objects are reused every frame.
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val puffPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val gaugeBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
    private val gaugePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 40, 40)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        isFakeBoldText = true
    }
    private val panelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 70, 40)
        isFakeBoldText = true
    }
    private val rect = RectF()

    override fun onPose(pose: Pose?, timestampMs: Long) {
        if (options.tapMode) return
        val completedRep = detector.update(pose, timestampMs)
        if (linked) detector.progress?.let { targetY = LINKED_BOTTOM - it * (LINKED_BOTTOM - LINKED_TOP) }
        if (completedRep) onFlapInput()
    }

    override fun onTap() {
        if (options.tapMode) onFlapInput()
    }

    override fun onDrag(yFraction: Float) {
        if (options.tapMode && linked) targetY = yFraction.coerceIn(LINKED_TOP, LINKED_BOTTOM)
    }

    private fun onFlapInput() {
        when (state) {
            State.READY -> startRound()
            State.PLAYING -> flap()
            State.GAME_OVER -> if (stateTime > RESTART_DELAY_SEC) startRound()
        }
    }

    private fun startRound() {
        state = State.PLAYING
        stateTime = 0f
        score = 0
        planeY = targetY ?: 0.45f
        planeVy = 0f
        obstacles.clear()
        puffs.clear()
        detector.resetCount()
        // The push-up that started the round has already happened.
        flap()
    }

    /** In flap mode this lifts the plane; in linked mode a push-up only shows the effect. */
    private fun flap() {
        if (!linked) planeVy = -tuning.flapSpeed
        sinceFlap = 0f
        puffs.add(Puff(PLANE_X - PLANE_WIDTH * 0.45f, planeY + 0.01f, 0f, large = true))
        puffs.add(Puff(PLANE_X - PLANE_WIDTH * 0.7f, planeY - 0.01f, -0.08f, large = false))
    }

    override fun update(dtSec: Float) {
        stateTime += dtSec
        sinceFlap += dtSec

        if (options.tapMode || detector.bodyVisible) unseenSec = 0f else unseenSec += dtSec
        if (state == State.PLAYING && isPaused()) return

        if (state != State.GAME_OVER) {
            groundOffset += SCROLL_SPEED * dtSec
            backgroundOffset += SCROLL_SPEED * BACKGROUND_PARALLAX * dtSec
        }
        for (puff in puffs) {
            puff.age += dtSec
            puff.x -= SCROLL_SPEED * dtSec
            puff.y -= 0.03f * dtSec
        }
        puffs.removeAll { it.age > PUFF_LIFE_SEC }

        when (state) {
            State.READY -> {
                val target = targetY
                if (linked && target != null) followTarget(target, dtSec) else planeY = 0.45f + 0.03f * sin(stateTime * 3f)
            }
            State.PLAYING -> updatePlaying(dtSec)
            State.GAME_OVER -> if (planeY < GROUND_Y - PLANE_RADIUS) {
                planeVy = min(planeVy + tuning.gravity * 3f * dtSec, tuning.maxFallSpeed * 3f)
                planeY = min(planeY + planeVy * dtSec, GROUND_Y - PLANE_RADIUS)
            }
        }
    }

    private fun isPaused() = unseenSec > LOST_POSE_GRACE_SEC

    /** Eases the plane towards [target]; planeVy is kept up to date for the tilt. */
    private fun followTarget(target: Float, dt: Float) {
        if (dt <= 0f) return
        val previous = planeY
        planeY += (target - planeY) * (1f - exp(-dt * LINKED_FOLLOW_RATE))
        planeVy = (planeY - previous) / dt
    }

    private fun updatePlaying(dt: Float) {
        if (linked) {
            // Keep the last height while the body's range is not known yet.
            targetY?.let { followTarget(it, dt) }
        } else {
            planeVy = min(planeVy + tuning.gravity * dt, tuning.maxFallSpeed)
            planeY += planeVy * dt
        }
        if (planeY < PLANE_RADIUS) {
            planeY = PLANE_RADIUS
            planeVy = 0f
        }

        for (obstacle in obstacles) obstacle.x -= SCROLL_SPEED * dt
        obstacles.removeAll { it.x < -MAX_ROCK_WIDTH }
        val lastX = obstacles.lastOrNull()?.x
        if (lastX == null || lastX < aspect + MAX_ROCK_WIDTH - tuning.obstacleSpacing) {
            val spawnX = maxOf(lastX?.plus(tuning.obstacleSpacing) ?: 0f, aspect + MAX_ROCK_WIDTH)
            obstacles.add(Obstacle(spawnX, nextGapY(obstacles.lastOrNull()?.gapY)))
        }

        for (obstacle in obstacles) {
            if (!obstacle.passed && obstacle.x < PLANE_X) {
                obstacle.passed = true
                score++
            }
        }

        if (planeY + PLANE_RADIUS >= GROUND_Y || obstacles.any(::hitsPlane)) gameOver()
    }

    private fun nextGapY(previous: Float?): Float {
        val minY = MIN_ROCK_HEIGHT + tuning.gap / 2f
        val maxY = GROUND_Y - MIN_ROCK_HEIGHT - tuning.gap / 2f
        val center = previous ?: ((minY + maxY) / 2f)
        return (center + Random.nextFloat() * 2f * MAX_GAP_SHIFT - MAX_GAP_SHIFT).coerceIn(minY, maxY)
    }

    // Rock geometry. Each rock is a triangle whose tip points into the gap; the sprites are drawn
    // over exactly these triangles so that collisions match what the player sees.

    private fun rockWidth(height: Float) = (height * ROCK_ASPECT).coerceIn(MIN_ROCK_WIDTH, MAX_ROCK_WIDTH)

    private fun upperRockBottom(o: Obstacle) = o.gapY - tuning.gap / 2f
    private fun lowerRockTop(o: Obstacle) = o.gapY + tuning.gap / 2f

    /** Rock rising from below the ground; its base is hidden behind the ground strip. */
    private fun lowerRockRect(o: Obstacle, out: RectF) {
        val top = lowerRockTop(o)
        val w = rockWidth(1f - top)
        out.set(o.x - w * ROCK_UP_TIP, top, o.x + w * (1f - ROCK_UP_TIP), 1f)
    }

    private fun upperRockRect(o: Obstacle, out: RectF) {
        val bottom = upperRockBottom(o)
        val w = rockWidth(bottom)
        out.set(o.x - w * ROCK_DOWN_TIP, 0f, o.x + w * (1f - ROCK_DOWN_TIP), bottom)
    }

    private fun hitsPlane(o: Obstacle): Boolean {
        val r = PLANE_RADIUS * 0.85f
        lowerRockRect(o, rect)
        val lowerLeft = rect.left
        val lowerRight = rect.right
        upperRockRect(o, rect)
        val upperLeft = rect.left
        val upperRight = rect.right
        // Test the plane's centre and points around its hitbox circle against both triangles.
        for (i in 0..HIT_SAMPLES) {
            val angle = i * (2.0 * Math.PI / HIT_SAMPLES)
            val px = if (i == HIT_SAMPLES) PLANE_X else PLANE_X + r * cos(angle).toFloat()
            val py = if (i == HIT_SAMPLES) planeY else planeY + r * sin(angle).toFloat()
            if (inTriangle(px, py, lowerLeft, 1f, lowerRight, 1f, o.x, lowerRockTop(o))) return true
            if (inTriangle(px, py, upperLeft, 0f, upperRight, 0f, o.x, upperRockBottom(o))) return true
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

    private fun gameOver() {
        state = State.GAME_OVER
        stateTime = 0f
        if (score > best) {
            best = score
            prefs.edit().putInt(bestKey, best).apply()
        }
    }

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        val u = height.toFloat()
        aspect = width / u

        drawBackground(canvas, width, u)
        for (obstacle in obstacles) drawObstacle(canvas, obstacle, u)
        drawGround(canvas, width, u)
        for (puff in puffs) drawPuff(canvas, puff, u)
        drawPlane(canvas, u)
        drawHud(canvas, width, u)
    }

    /** Tiles [bitmap] horizontally across the screen at the given height, scrolled by [offset]. */
    private fun drawStrip(canvas: Canvas, bitmap: Bitmap, width: Int, top: Float, bottom: Float, offset: Float) {
        val tileWidth = (bottom - top) * bitmap.width / bitmap.height
        var x = -(offset % tileWidth)
        while (x < width) {
            rect.set(x, top, x + tileWidth + 1f, bottom)
            canvas.drawBitmap(bitmap, null, rect, spritePaint)
            x += tileWidth
        }
    }

    private fun drawBackground(canvas: Canvas, width: Int, u: Float) =
        drawStrip(canvas, sprites.background, width, 0f, u, backgroundOffset * u)

    private fun drawGround(canvas: Canvas, width: Int, u: Float) =
        drawStrip(canvas, sprites.ground, width, (1f - GROUND_HEIGHT) * u, u, groundOffset * u)

    private fun drawObstacle(canvas: Canvas, o: Obstacle, u: Float) {
        lowerRockRect(o, rect)
        rect.set(rect.left * u, rect.top * u, rect.right * u, rect.bottom * u)
        canvas.drawBitmap(sprites.rockUp, null, rect, spritePaint)
        upperRockRect(o, rect)
        rect.set(rect.left * u, rect.top * u, rect.right * u, rect.bottom * u)
        canvas.drawBitmap(sprites.rockDown, null, rect, spritePaint)
    }

    private fun drawPuff(canvas: Canvas, puff: Puff, u: Float) {
        if (puff.age < 0f) return
        val t = puff.age / PUFF_LIFE_SEC
        val bitmap = if (puff.large) sprites.puffLarge else sprites.puffSmall
        val size = (if (puff.large) 0.05f else 0.035f) * (0.7f + 0.8f * t) * u
        puffPaint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
        val h = size * bitmap.height / bitmap.width
        rect.set(puff.x * u - size / 2, puff.y * u - h / 2, puff.x * u + size / 2, puff.y * u + h / 2)
        canvas.drawBitmap(bitmap, null, rect, puffPaint)
    }

    private fun drawPlane(canvas: Canvas, u: Float) {
        val cx = PLANE_X * u
        val cy = planeY * u
        val frames = sprites.plane
        // The propeller spins faster right after a flap and stops once the plane has crashed.
        val fps = if (sinceFlap < 0.4f) 24f else 12f
        val frame = if (state == State.GAME_OVER) frames[0] else frames[(stateTime * fps).toInt() % frames.size]
        val halfWidth = PLANE_WIDTH * u / 2f
        val halfHeight = halfWidth * frame.height / frame.width
        val tilt = when (state) {
            State.READY -> if (linked) (planeVy / tuning.maxFallSpeed * 20f).coerceIn(-25f, 25f) else 0f
            else -> (planeVy / tuning.maxFallSpeed * 40f).coerceIn(-25f, 70f)
        }

        canvas.save()
        canvas.rotate(tilt, cx, cy)
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
        canvas.drawBitmap(frame, null, rect, spritePaint)
        canvas.restore()
    }

    private fun drawHud(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        if (state == State.PLAYING) drawNumber(canvas, score, cx, 0.05f * u, 0.11f * u)

        val repsLabel = if (options.tapMode) "タップ操作モード" else "腕立て ${detector.reps}回"
        textPaint.textAlign = Paint.Align.LEFT
        textStrokePaint.textAlign = Paint.Align.LEFT
        drawText(canvas, repsLabel, 0.04f * u, 0.09f * u, 0.055f * u)
        drawText(canvas, "ベスト $best", 0.04f * u, 0.16f * u, 0.045f * u)
        textPaint.textAlign = Paint.Align.CENTER
        textStrokePaint.textAlign = Paint.Align.CENTER

        if (!options.tapMode) drawGauge(canvas, u)

        when {
            state == State.READY -> {
                drawCentered(canvas, sprites.textGetReady, cx, 0.24f * u, 0.11f * u)
                val message = if (options.tapMode) "タップでスタート" else "腕立て伏せを1回するとスタート！"
                drawText(canvas, message, cx, 0.62f * u, 0.06f * u)
                val lines = listOfNotNull(
                    when {
                        !linked -> null
                        options.tapMode -> "連動モード: ドラッグした高さに飛行機が動きます"
                        else -> "連動モード: 体の高さで飛行機を上下させます"
                    },
                    hintText().takeIf { it.isNotEmpty() },
                )
                lines.forEachIndexed { i, line -> drawText(canvas, line, cx, (0.7f + 0.075f * i) * u, 0.045f * u) }
            }
            state == State.PLAYING && isPaused() -> {
                canvas.drawRect(0f, 0.3f * u, width.toFloat(), 0.54f * u, shadePaint)
                drawText(canvas, "一時停止中", cx, 0.42f * u, 0.08f * u)
                drawText(canvas, "カメラに上半身が映るようにしてください", cx, 0.5f * u, 0.05f * u)
            }
            state == State.GAME_OVER -> drawGameOver(canvas, cx, u)
        }
    }

    private fun drawGameOver(canvas: Canvas, cx: Float, u: Float) {
        drawCentered(canvas, sprites.textGameOver, cx, 0.17f * u, 0.11f * u)

        val panelSize = 0.42f * u
        val top = 0.27f * u
        rect.set(cx - panelSize * 0.8f, top, cx + panelSize * 0.8f, top + panelSize)
        canvas.drawBitmap(sprites.panel, null, rect, spritePaint)

        val medal = when {
            score >= GOLD_SCORE -> sprites.medalGold
            score >= SILVER_SCORE -> sprites.medalSilver
            score >= BRONZE_SCORE -> sprites.medalBronze
            else -> null
        }
        val medalSize = panelSize * 0.5f
        val medalCx = cx - panelSize * 0.42f
        val medalCy = top + panelSize / 2f
        if (medal != null) {
            rect.set(medalCx - medalSize / 2, medalCy - medalSize / 2, medalCx + medalSize / 2, medalCy + medalSize / 2)
            canvas.drawBitmap(medal, null, rect, spritePaint)
        }

        val textX = cx - panelSize * 0.1f
        val lines = buildList {
            add("スコア  $score")
            add("ベスト  $best")
            if (!options.tapMode) add("腕立て  ${detector.reps}回")
        }
        panelTextPaint.textSize = 0.055f * u
        val lineHeight = 0.085f * u
        var y = medalCy - lineHeight * (lines.size - 1) / 2f + panelTextPaint.textSize * 0.35f
        for (line in lines) {
            canvas.drawText(line, textX, y, panelTextPaint)
            y += lineHeight
        }

        if (stateTime > RESTART_DELAY_SEC) {
            val retry = if (options.tapMode) "タップでもう一度" else "腕立て伏せ1回でもう一度"
            drawText(canvas, retry, cx, top + panelSize + 0.09f * u, 0.055f * u)
        }
    }

    private fun hintText(): String = when {
        options.tapMode -> ""
        !detector.bodyVisible -> "カメラに上半身が映るようにしてください"
        detector.progress == null -> "体が映りました。腕立て伏せをしてください"
        else -> ""
    }

    /** Vertical bar showing how far down the body currently is, so players can see detection working. */
    private fun drawGauge(canvas: Canvas, u: Float) {
        val left = 0.04f * u
        val top = 0.24f * u
        val w = 0.035f * u
        val h = 0.4f * u
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, w / 2, w / 2, gaugeBackPaint)
        val progress = detector.progress ?: return
        gaugePaint.color = if (detector.phase == PushUpDetector.Phase.DOWN) Color.rgb(255, 170, 40) else Color.rgb(80, 220, 120)
        rect.set(left, top + h * (1f - progress), left + w, top + h)
        canvas.drawRoundRect(rect, w / 2, w / 2, gaugePaint)
    }

    /** Draws [bitmap] centred on (cx, cy) with the given height, keeping its aspect ratio. */
    private fun drawCentered(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, height: Float) {
        val w = height * bitmap.width / bitmap.height
        rect.set(cx - w / 2, cy - height / 2, cx + w / 2, cy + height / 2)
        canvas.drawBitmap(bitmap, null, rect, spritePaint)
    }

    /** Draws [value] with the digit sprites, centred horizontally on [cx]. */
    private fun drawNumber(canvas: Canvas, value: Int, cx: Float, top: Float, height: Float) {
        val digits = value.toString().map { sprites.digits[it - '0'] }
        val gap = height * 0.05f
        val total = digits.sumOf { (height * it.width / it.height).toDouble() }.toFloat() + gap * (digits.size - 1)
        var x = cx - total / 2
        for (digit in digits) {
            val w = height * digit.width / digit.height
            rect.set(x, top, x + w, top + height)
            canvas.drawBitmap(digit, null, rect, spritePaint)
            x += w + gap
        }
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
        textPaint.textSize = size
        textStrokePaint.textSize = size
        textStrokePaint.strokeWidth = size * 0.15f
        canvas.drawText(text, x, y, textStrokePaint)
        canvas.drawText(text, x, y, textPaint)
    }

    private companion object {
        const val AMPLITUDE = 0.12f
        const val SCROLL_SPEED = 0.32f
        const val BACKGROUND_PARALLAX = 0.25f

        /** Height of the ground strip; its wavy grass line sits around GROUND_Y. */
        const val GROUND_HEIGHT = 0.2f
        const val GROUND_Y = 0.87f

        const val PLANE_X = 0.55f
        const val PLANE_WIDTH = 0.13f
        const val PLANE_RADIUS = 0.04f

        /** Width/height of the rock sprites (108x239) and where their tips sit across the width. */
        const val ROCK_ASPECT = 108f / 239f
        const val ROCK_UP_TIP = 0.61f
        const val ROCK_DOWN_TIP = 0.6f
        const val MIN_ROCK_WIDTH = 0.12f
        const val MAX_ROCK_WIDTH = 0.3f
        const val MIN_ROCK_HEIGHT = 0.12f
        const val MAX_GAP_SHIFT = 0.22f
        const val HIT_SAMPLES = 8

        /** Linked mode: the plane's range of heights, from body fully up (top) to fully down (bottom). */
        const val LINKED_TOP = 0.1f
        const val LINKED_BOTTOM = 0.8f
        const val LINKED_FOLLOW_RATE = 6f
        const val LINKED_RANGE_MEMORY_SEC = 60f

        const val PUFF_LIFE_SEC = 0.7f
        const val RESTART_DELAY_SEC = 1.5f
        const val LOST_POSE_GRACE_SEC = 0.8f

        const val BRONZE_SCORE = 5
        const val SILVER_SCORE = 10
        const val GOLD_SCORE = 20
    }
}
