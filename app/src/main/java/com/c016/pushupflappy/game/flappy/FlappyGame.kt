package com.c016.pushupflappy.game.flappy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.c016.pushupflappy.game.ControlMode
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.game.VisualTheme
import com.c016.pushupflappy.game.flappy.FlappyWorld.GROUND_Y
import com.c016.pushupflappy.game.flappy.FlappyWorld.PLAYER_RADIUS
import com.c016.pushupflappy.game.flappy.FlappyWorld.PLAYER_X
import com.c016.pushupflappy.motion.PushUpDetector
import com.c016.pushupflappy.pose.Pose
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flappy Bird where each push-up is one flap. In [ControlMode.LINKED] the player instead follows
 * the height of the body. How it looks, including obstacle shapes, comes from a [FlappySkin].
 *
 * World coordinates are in units of the screen height: y runs from 0 (top) to 1 (bottom) and x
 * from 0 to the screen aspect ratio. Physics are slowed down so that one flap per push-up keeps
 * the player in the air.
 */
class FlappyGame(context: Context, private val options: GameOptions) : MiniGame {

    private enum class State { READY, PLAYING, GAME_OVER }

    /** An obstacle above and below a gap centred at [gapY]; [x] is its centre line. */
    private class Obstacle(var x: Float, val gapY: Float, var passed: Boolean = false)

    private class Puff(var x: Float, var y: Float, var age: Float, val large: Boolean)

    private class Tuning(flapIntervalSec: Float, val gap: Float) {
        // Choose gravity and flap speed so that flapping every flapIntervalSec keeps the player
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

    private val skin: FlappySkin = when (options.theme) {
        VisualTheme.CLASSIC -> ClassicSkin(context.resources)
        VisualTheme.KENNEY -> TappySkin(context.resources)
    }
    private val linked = options.controlMode == ControlMode.LINKED

    // In linked mode the player may hold a position for a while, so the learned range must not fade quickly.
    private val detector = PushUpDetector(
        rangeMemorySec = if (linked) LINKED_RANGE_MEMORY_SEC else PushUpDetector.DEFAULT_RANGE_MEMORY_SEC
    )

    /** Linked mode: height the player is steering towards, or null until the body's range is known. */
    private var targetY: Float? = null
    private var state = State.READY
    private var stateTime = 0f
    private var score = 0
    private var aspect = 16f / 9f

    private var playerY = 0.45f
    private var playerVy = 0f
    private var sinceFlap = 10f

    /** Drives the player animation; runs faster for a moment after each flap. */
    private var animPhase = 0f
    private val obstacles = mutableListOf<Obstacle>()
    private val puffs = mutableListOf<Puff>()
    private var groundOffset = 0f
    private var backgroundOffset = 0f

    /** Seconds since the body was last seen; the game pauses while it is out of view. */
    private var unseenSec = 0f

    // Paint objects are reused every frame.
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
        color = skin.panelTextColor
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
        playerY = targetY ?: 0.45f
        playerVy = 0f
        obstacles.clear()
        puffs.clear()
        detector.resetCount()
        // The push-up that started the round has already happened.
        flap()
    }

    /** In flap mode this lifts the player; in linked mode a push-up only shows the effect. */
    private fun flap() {
        if (!linked) playerVy = -tuning.flapSpeed
        sinceFlap = 0f
        if (skin.hasPuffs) {
            puffs.add(Puff(PLAYER_X - 0.06f, playerY + 0.01f, 0f, large = true))
            puffs.add(Puff(PLAYER_X - 0.09f, playerY - 0.01f, -0.08f, large = false))
        }
    }

    override fun update(dtSec: Float) {
        stateTime += dtSec
        sinceFlap += dtSec
        animPhase += dtSec * (if (sinceFlap < FAST_ANIM_SEC) 2f else 1f)

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
                if (linked && target != null) followTarget(target, dtSec) else playerY = 0.45f + 0.03f * sin(stateTime * 3f)
            }
            State.PLAYING -> updatePlaying(dtSec)
            State.GAME_OVER -> if (playerY < GROUND_Y - PLAYER_RADIUS) {
                playerVy = min(playerVy + tuning.gravity * 3f * dtSec, tuning.maxFallSpeed * 3f)
                playerY = min(playerY + playerVy * dtSec, GROUND_Y - PLAYER_RADIUS)
            }
        }
    }

    private fun isPaused() = unseenSec > LOST_POSE_GRACE_SEC

    /** Eases the player towards [target]; playerVy is kept up to date for the tilt. */
    private fun followTarget(target: Float, dt: Float) {
        if (dt <= 0f) return
        val previous = playerY
        playerY += (target - playerY) * (1f - exp(-dt * LINKED_FOLLOW_RATE))
        playerVy = (playerY - previous) / dt
    }

    private fun updatePlaying(dt: Float) {
        if (linked) {
            // Keep the last height while the body's range is not known yet.
            targetY?.let { followTarget(it, dt) }
        } else {
            playerVy = min(playerVy + tuning.gravity * dt, tuning.maxFallSpeed)
            playerY += playerVy * dt
        }
        if (playerY < PLAYER_RADIUS) {
            playerY = PLAYER_RADIUS
            playerVy = 0f
        }

        for (obstacle in obstacles) obstacle.x -= SCROLL_SPEED * dt
        obstacles.removeAll { it.x < -skin.obstacleReach }
        val lastX = obstacles.lastOrNull()?.x
        if (lastX == null || lastX < aspect + skin.obstacleReach - tuning.obstacleSpacing) {
            val spawnX = maxOf(lastX?.plus(tuning.obstacleSpacing) ?: 0f, aspect + skin.obstacleReach)
            obstacles.add(Obstacle(spawnX, nextGapY(obstacles.lastOrNull()?.gapY)))
        }

        for (obstacle in obstacles) {
            if (!obstacle.passed && obstacle.x < PLAYER_X) {
                obstacle.passed = true
                score++
            }
        }

        if (playerY + PLAYER_RADIUS >= GROUND_Y || obstacles.any(::hitsPlayer)) gameOver()
    }

    private fun nextGapY(previous: Float?): Float {
        val minY = MIN_OBSTACLE_HEIGHT + tuning.gap / 2f
        val maxY = GROUND_Y - MIN_OBSTACLE_HEIGHT - tuning.gap / 2f
        val center = previous ?: ((minY + maxY) / 2f)
        return (center + Random.nextFloat() * 2f * MAX_GAP_SHIFT - MAX_GAP_SHIFT).coerceIn(minY, maxY)
    }

    private fun gapTop(o: Obstacle) = o.gapY - tuning.gap / 2f
    private fun gapBottom(o: Obstacle) = o.gapY + tuning.gap / 2f

    private fun hitsPlayer(o: Obstacle) =
        skin.hitsObstacle(o.x, gapTop(o), gapBottom(o), PLAYER_X, playerY, PLAYER_RADIUS * 0.85f)

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

        skin.drawBackground(canvas, width, u, backgroundOffset)
        for (o in obstacles) skin.drawObstacle(canvas, o.x, gapTop(o), gapBottom(o), u)
        skin.drawGround(canvas, width, u, groundOffset)
        for (puff in puffs) {
            if (puff.age >= 0f) skin.drawPuff(canvas, puff.x, puff.y, puff.age / PUFF_LIFE_SEC, puff.large, u)
        }
        val tilt = when (state) {
            State.READY -> if (linked) (playerVy / tuning.maxFallSpeed * 20f).coerceIn(-25f, 25f) else 0f
            else -> (playerVy / tuning.maxFallSpeed * 40f).coerceIn(-25f, 70f)
        }
        skin.drawPlayer(canvas, playerY, tilt, animPhase, crashed = state == State.GAME_OVER, u)
        drawHud(canvas, width, u)
    }

    private fun drawHud(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        if (state == State.PLAYING) skin.drawNumber(canvas, score, cx, 0.05f * u, 0.11f * u)

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
                skin.drawTitle(canvas, FlappyTitle.GET_READY, cx, 0.24f * u, 0.11f * u)
                val message = if (options.tapMode) "タップでスタート" else "腕立て伏せを1回するとスタート！"
                drawText(canvas, message, cx, 0.62f * u, 0.06f * u)
                val lines = listOfNotNull(
                    when {
                        !linked -> null
                        options.tapMode -> "連動モード: ドラッグした高さに動きます"
                        else -> "連動モード: 体の高さで上下させます"
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
        skin.drawTitle(canvas, FlappyTitle.GAME_OVER, cx, 0.17f * u, 0.11f * u)

        val panelSize = 0.42f * u
        val top = 0.27f * u
        rect.set(cx - panelSize * 0.8f, top, cx + panelSize * 0.8f, top + panelSize)
        skin.drawPanel(canvas, rect)

        val medal = when {
            score >= GOLD_SCORE -> FlappyMedal.GOLD
            score >= SILVER_SCORE -> FlappyMedal.SILVER
            score >= BRONZE_SCORE -> FlappyMedal.BRONZE
            else -> null
        }
        val medalSize = panelSize * 0.5f
        val medalCx = cx - panelSize * 0.42f
        val medalCy = top + panelSize / 2f
        if (medal != null) {
            rect.set(medalCx - medalSize / 2, medalCy - medalSize / 2, medalCx + medalSize / 2, medalCy + medalSize / 2)
            skin.drawMedal(canvas, medal, rect)
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

        const val MIN_OBSTACLE_HEIGHT = 0.12f
        const val MAX_GAP_SHIFT = 0.22f
        const val FAST_ANIM_SEC = 0.4f

        /** Linked mode: the player's range of heights, from body fully up (top) to fully down (bottom). */
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
