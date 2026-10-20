package com.c016.pushupflappy.game.flappy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.motion.PushUpDetector
import com.c016.pushupflappy.pose.Pose
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flappy Bird where each push-up is one flap.
 *
 * World coordinates are in units of the screen height: y runs from 0 (top) to 1 (bottom) and x
 * from 0 to the screen aspect ratio. Physics are slowed down so that one flap per push-up keeps
 * the bird in the air.
 */
class FlappyGame(context: Context, private val options: GameOptions) : MiniGame {

    private enum class State { READY, PLAYING, GAME_OVER }

    private class Pipe(var x: Float, val gapY: Float, var passed: Boolean = false)

    private class Tuning(flapIntervalSec: Float, val gap: Float) {
        // Choose gravity and flap speed so that flapping every flapIntervalSec keeps the bird
        // level, swinging up and down by AMPLITUDE: g = 8A/T^2, v = gT/2.
        val gravity = 8f * AMPLITUDE / (flapIntervalSec * flapIntervalSec)
        val flapSpeed = gravity * flapIntervalSec / 2f
        val maxFallSpeed = flapSpeed * 1.3f
        val pipeSpacing = SCROLL_SPEED * flapIntervalSec * 2.5f
    }

    private val tuning = when (options.difficulty) {
        Difficulty.EASY -> Tuning(flapIntervalSec = 2.2f, gap = 0.46f)
        Difficulty.NORMAL -> Tuning(flapIntervalSec = 1.7f, gap = 0.38f)
        Difficulty.HARD -> Tuning(flapIntervalSec = 1.3f, gap = 0.32f)
    }

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    private val bestKey = "flappy_best_${options.difficulty.name.lowercase()}"
    private var best = prefs.getInt(bestKey, 0)

    private val detector = PushUpDetector()
    private var state = State.READY
    private var stateTime = 0f
    private var score = 0
    private var aspect = 16f / 9f

    private var birdY = 0.45f
    private var birdVy = 0f
    private var sinceFlap = 10f
    private val pipes = mutableListOf<Pipe>()
    private var groundOffset = 0f

    /** Seconds since the body was last seen; the game pauses while it is out of view. */
    private var unseenSec = 0f

    // Paint objects are reused every frame.
    private val skyPaint = Paint()
    private var skyHeight = -1
    private val pipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(94, 189, 62) }
    private val pipeEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 110, 30)
        style = Paint.Style.STROKE
    }
    private val groundPaint = Paint().apply { color = Color.rgb(222, 216, 149) }
    private val grassPaint = Paint().apply { color = Color.rgb(115, 191, 46) }
    private val stripePaint = Paint().apply { color = Color.rgb(98, 166, 36) }
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 208, 48) }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 245, 220) }
    private val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(240, 112, 32) }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
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
    private val rect = RectF()

    override fun onPose(pose: Pose?, timestampMs: Long) {
        if (options.tapMode) return
        if (detector.update(pose, timestampMs)) onFlapInput()
    }

    override fun onTap() {
        if (options.tapMode) onFlapInput()
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
        birdY = 0.45f
        birdVy = 0f
        pipes.clear()
        detector.resetCount()
        // The push-up that started the round has already happened.
        flap()
    }

    private fun flap() {
        birdVy = -tuning.flapSpeed
        sinceFlap = 0f
    }

    override fun update(dtSec: Float) {
        stateTime += dtSec
        sinceFlap += dtSec

        if (options.tapMode || detector.bodyVisible) unseenSec = 0f else unseenSec += dtSec
        if (state == State.PLAYING && isPaused()) return

        if (state != State.GAME_OVER) groundOffset = (groundOffset + SCROLL_SPEED * dtSec) % STRIPE_WIDTH

        when (state) {
            State.READY -> birdY = 0.45f + 0.03f * sin(stateTime * 3f)
            State.PLAYING -> updatePlaying(dtSec)
            State.GAME_OVER -> if (birdY < GROUND_Y - BIRD_RADIUS) {
                birdVy = min(birdVy + tuning.gravity * 3f * dtSec, tuning.maxFallSpeed * 3f)
                birdY = min(birdY + birdVy * dtSec, GROUND_Y - BIRD_RADIUS)
            }
        }
    }

    private fun isPaused() = unseenSec > LOST_POSE_GRACE_SEC

    private fun updatePlaying(dt: Float) {
        birdVy = min(birdVy + tuning.gravity * dt, tuning.maxFallSpeed)
        birdY += birdVy * dt
        if (birdY < BIRD_RADIUS) {
            birdY = BIRD_RADIUS
            birdVy = 0f
        }

        for (pipe in pipes) pipe.x -= SCROLL_SPEED * dt
        pipes.removeAll { it.x < -PIPE_WIDTH }
        val lastX = pipes.lastOrNull()?.x
        if (lastX == null || lastX < aspect + PIPE_WIDTH - tuning.pipeSpacing) {
            val spawnX = max(lastX?.plus(tuning.pipeSpacing) ?: 0f, aspect + PIPE_WIDTH)
            pipes.add(Pipe(spawnX, nextGapY(pipes.lastOrNull()?.gapY)))
        }

        for (pipe in pipes) {
            if (!pipe.passed && pipe.x + PIPE_WIDTH < BIRD_X) {
                pipe.passed = true
                score++
            }
        }

        if (birdY + BIRD_RADIUS >= GROUND_Y || pipes.any(::hitsBird)) gameOver()
    }

    private fun nextGapY(previous: Float?): Float {
        val minY = 0.08f + tuning.gap / 2f
        val maxY = GROUND_Y - 0.06f - tuning.gap / 2f
        val center = previous ?: ((minY + maxY) / 2f)
        return (center + Random.nextFloat() * 2f * MAX_GAP_SHIFT - MAX_GAP_SHIFT).coerceIn(minY, maxY)
    }

    private fun hitsBird(pipe: Pipe): Boolean {
        val r = BIRD_RADIUS * 0.85f
        if (BIRD_X + r < pipe.x || BIRD_X - r > pipe.x + PIPE_WIDTH) return false
        val gapTop = pipe.gapY - tuning.gap / 2f
        val gapBottom = pipe.gapY + tuning.gap / 2f
        return birdY - r < gapTop || birdY + r > gapBottom
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

        drawBackground(canvas, width, height)
        for (pipe in pipes) drawPipe(canvas, pipe, u)
        drawGround(canvas, width, u)
        drawBird(canvas, u)
        drawHud(canvas, width, u)
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        if (skyHeight != height) {
            skyHeight = height
            skyPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(78, 192, 202), Color.rgb(190, 235, 230), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)
    }

    private fun drawPipe(canvas: Canvas, pipe: Pipe, u: Float) {
        val left = pipe.x * u
        val right = (pipe.x + PIPE_WIDTH) * u
        val gapTop = (pipe.gapY - tuning.gap / 2f) * u
        val gapBottom = (pipe.gapY + tuning.gap / 2f) * u
        val capHeight = 0.05f * u
        val capOverhang = 0.015f * u
        pipeEdgePaint.strokeWidth = 0.006f * u

        rect.set(left, 0f, right, gapTop)
        canvas.drawRect(rect, pipePaint)
        canvas.drawRect(rect, pipeEdgePaint)
        rect.set(left - capOverhang, gapTop - capHeight, right + capOverhang, gapTop)
        canvas.drawRect(rect, pipePaint)
        canvas.drawRect(rect, pipeEdgePaint)

        rect.set(left, gapBottom, right, GROUND_Y * u)
        canvas.drawRect(rect, pipePaint)
        canvas.drawRect(rect, pipeEdgePaint)
        rect.set(left - capOverhang, gapBottom, right + capOverhang, gapBottom + capHeight)
        canvas.drawRect(rect, pipePaint)
        canvas.drawRect(rect, pipeEdgePaint)
    }

    private fun drawGround(canvas: Canvas, width: Int, u: Float) {
        val top = GROUND_Y * u
        canvas.drawRect(0f, top, width.toFloat(), u, groundPaint)
        canvas.drawRect(0f, top, width.toFloat(), top + 0.03f * u, grassPaint)
        var x = -groundOffset * u
        while (x < width) {
            canvas.drawRect(x, top, x + STRIPE_WIDTH * u / 2f, top + 0.03f * u, stripePaint)
            x += STRIPE_WIDTH * u
        }
    }

    private fun drawBird(canvas: Canvas, u: Float) {
        val cx = BIRD_X * u
        val cy = birdY * u
        val r = BIRD_RADIUS * u
        val tilt = when (state) {
            State.READY -> 0f
            else -> (birdVy / tuning.maxFallSpeed * 45f).coerceIn(-25f, 80f)
        }

        canvas.save()
        canvas.rotate(tilt, cx, cy)
        canvas.drawCircle(cx, cy, r, birdPaint)
        // The wing snaps up right after a flap, then settles.
        val wingLift = if (sinceFlap < 0.25f) -0.5f * r else 0.1f * r
        rect.set(cx - r * 0.95f, cy - r * 0.15f + wingLift, cx - r * 0.05f, cy + r * 0.4f + wingLift)
        canvas.drawOval(rect, wingPaint)
        canvas.drawCircle(cx + r * 0.4f, cy - r * 0.35f, r * 0.33f, whitePaint)
        canvas.drawCircle(cx + r * 0.5f, cy - r * 0.35f, r * 0.14f, blackPaint)
        rect.set(cx + r * 0.55f, cy - r * 0.05f, cx + r * 1.35f, cy + r * 0.4f)
        canvas.drawOval(rect, beakPaint)
        canvas.restore()
    }

    private fun drawHud(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        if (state != State.READY) drawText(canvas, score.toString(), cx, 0.16f * u, 0.12f * u)

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
                val message = if (options.tapMode) "タップでスタート" else "腕立て伏せを1回するとスタート！"
                drawBanner(canvas, width, u, message, hintText())
            }
            state == State.PLAYING && isPaused() ->
                drawBanner(canvas, width, u, "一時停止中", "カメラに上半身が映るようにしてください")
            state == State.GAME_OVER -> {
                val retry = when {
                    stateTime <= RESTART_DELAY_SEC -> ""
                    options.tapMode -> "タップでもう一度"
                    else -> "腕立て伏せ1回でもう一度"
                }
                val detail = if (options.tapMode) "スコア $score  ベスト $best" else "スコア $score  腕立て ${detector.reps}回  ベスト $best"
                drawBanner(canvas, width, u, "ゲームオーバー", detail, retry)
            }
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

    private fun drawBanner(canvas: Canvas, width: Int, u: Float, title: String, vararg lines: String) {
        val visible = lines.filter { it.isNotEmpty() }
        val top = 0.3f * u
        val bottom = top + 0.16f * u + visible.size * 0.08f * u
        canvas.drawRect(0f, top, width.toFloat(), bottom, shadePaint)
        drawText(canvas, title, width / 2f, top + 0.12f * u, 0.08f * u)
        visible.forEachIndexed { i, line ->
            drawText(canvas, line, width / 2f, top + 0.2f * u + i * 0.08f * u, 0.05f * u)
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
        const val GROUND_Y = 0.9f
        const val BIRD_X = 0.55f
        const val BIRD_RADIUS = 0.045f
        const val PIPE_WIDTH = 0.17f
        const val MAX_GAP_SHIFT = 0.22f
        const val STRIPE_WIDTH = 0.08f
        const val RESTART_DELAY_SEC = 1.5f
        const val LOST_POSE_GRACE_SEC = 0.8f
    }
}
