package com.c016.pushupflappy.game.shooter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.game.PoseHud
import com.c016.pushupflappy.motion.PushUpDetector
import com.c016.pushupflappy.pose.Pose
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * A horizontal shooter in the style of 80s arcade games. The ship follows the height of the body,
 * fires automatically, and a push-up buys the power-up highlighted on the gauge.
 *
 * World coordinates are in units of the screen height: y runs from 0 (top) to 1 (bottom) and x
 * from 0 to the screen aspect ratio.
 */
class ShooterGame(context: Context, private val options: GameOptions) : MiniGame {

    private enum class State { READY, PLAYING, GAME_OVER }

    private enum class Kind(val radius: Float, val hp: Int, val points: Int) {
        FAN(0.036f, 1, 100),
        WAVER(0.042f, 1, 150),
        DART(0.032f, 1, 100),
        GUNNER(0.06f, 4, 500),
    }

    /** Enemies spawned together; destroying all of them drops a capsule. */
    private class Squad(var alive: Int, var escaped: Boolean = false)

    private class Enemy(
        val kind: Kind,
        var x: Float,
        var y: Float,
        val baseY: Float,
        val vx: Float,
        val vy: Float,
        val phase: Float,
        val squad: Squad?,
    ) {
        var hp = kind.hp
        var age = 0f
        var fireTimer = 1f
        var flash = 0f
    }

    private class Shot(var x: Float, var y: Float, val vx: Float, val vy: Float, val missile: Boolean)
    private class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float)
    private class Capsule(var x: Float, var y: Float, var age: Float = 0f)
    private class Explosion(val x: Float, val y: Float, val scale: Float, var age: Float = 0f)
    private class Star(var x: Float, val y: Float, val speed: Float, val size: Float)

    /** Difficulty scales enemy speed, fire rate and how often squads arrive. */
    private val pace = when (options.difficulty) {
        Difficulty.EASY -> 0.7f
        Difficulty.NORMAL -> 1f
        Difficulty.HARD -> 1.3f
    }

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    private val bestKey = listOfNotNull(
        "shooter_best",
        options.difficulty.name.lowercase(),
        "tap".takeIf { options.tapMode },
    ).joinToString("_")
    private var best = prefs.getInt(bestKey, 0)

    private val sprites = ShooterSprites(context.resources)
    private val hud = PoseHud()

    // The ship follows the body continuously, so the learned range must survive holds.
    private val detector = PushUpDetector(rangeMemorySec = 60f)
    private val gauge = PowerUpGauge()

    private var state = State.READY
    private var stateTime = 0f
    private var playTime = 0f
    private var score = 0
    private var lives = START_LIVES
    private var aspect = 16f / 9f

    private var shipY = 0.5f
    private var targetY: Float? = null
    private var invulnerable = 0f
    private var shotTimer = 0f
    private var missileTimer = 0f
    private var spawnTimer = 2f
    private var lastPowerUp: PowerUp? = null
    private var powerUpMessageSec = 0f

    /** Recent ship heights, newest last, sampled every [TRAIL_STEP_SEC] for the options to follow. */
    private val trail = ArrayDeque<Float>()
    private var trailTimer = 0f

    private val enemies = mutableListOf<Enemy>()
    private val shots = mutableListOf<Shot>()
    private val bullets = mutableListOf<Bullet>()
    private val capsules = mutableListOf<Capsule>()
    private val explosions = mutableListOf<Explosion>()
    private val stars = List(70) {
        val layer = Random.nextInt(3)
        Star(Random.nextFloat() * 3f, Random.nextFloat(), 0.05f + 0.08f * layer, 0.003f + 0.002f * layer)
    }

    /** Seconds since the body was last seen; the game pauses while it is out of view. */
    private var unseenSec = 0f

    private val spritePaint = Paint() // no filtering: keep the pixel art crisp
    private val blinkPaint = Paint().apply { alpha = 110 }
    private val spacePaint = Paint().apply { color = Color.rgb(8, 8, 28) }
    private val starPaint = Paint().apply { color = Color.rgb(200, 210, 255) }
    private val shotPaint = Paint().apply { color = Color.rgb(255, 250, 200) }
    private val missilePaint = Paint().apply { color = Color.rgb(255, 160, 60) }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 90, 170) }
    private val bulletCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val gaugeBoxPaint = Paint().apply { color = Color.argb(170, 20, 30, 90) }
    private val gaugeSelectedPaint = Paint().apply { color = Color.rgb(255, 150, 30) }
    private val gaugeBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.rgb(140, 170, 255)
    }
    private val gaugeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun onPose(pose: Pose?, timestampMs: Long) {
        if (options.tapMode) return
        val completedRep = detector.update(pose, timestampMs)
        detector.progress?.let { targetY = SHIP_BOTTOM - it * (SHIP_BOTTOM - SHIP_TOP) }
        if (completedRep) onPushUp()
    }

    override fun onTap() {
        if (options.tapMode) onPushUp()
    }

    override fun onDrag(yFraction: Float) {
        if (options.tapMode) targetY = yFraction.coerceIn(SHIP_TOP, SHIP_BOTTOM)
    }

    private fun onPushUp() {
        when (state) {
            State.READY -> startGame()
            State.PLAYING -> applyPowerUp(gauge.activate())
            State.GAME_OVER -> if (stateTime > RESTART_DELAY_SEC) startGame()
        }
    }

    private fun startGame() {
        state = State.PLAYING
        stateTime = 0f
        playTime = 0f
        score = 0
        lives = START_LIVES
        shipY = targetY ?: 0.5f
        invulnerable = RESPAWN_INVULNERABLE_SEC
        spawnTimer = 2f
        enemies.clear()
        shots.clear()
        bullets.clear()
        capsules.clear()
        explosions.clear()
        trail.clear()
        gauge.reset()
        detector.resetCount()
    }

    /** Levels live in the gauge; this only announces the purchase. */
    private fun applyPowerUp(powerUp: PowerUp?) {
        if (powerUp == null) return
        lastPowerUp = powerUp
        powerUpMessageSec = POWER_UP_MESSAGE_SEC
    }

    override fun update(dtSec: Float) {
        stateTime += dtSec
        if (options.tapMode || detector.bodyVisible) unseenSec = 0f else unseenSec += dtSec
        if (state == State.PLAYING && isPaused()) return

        for (star in stars) {
            star.x -= star.speed * dtSec
            if (star.x < 0f) star.x += aspect + 0.1f
        }
        for (e in explosions) e.age += dtSec
        explosions.removeAll { it.age > EXPLOSION_SEC }

        followTarget(dtSec)
        if (state == State.PLAYING) updatePlaying(dtSec)
    }

    private fun isPaused() = unseenSec > LOST_POSE_GRACE_SEC

    private fun followTarget(dt: Float) {
        val target = targetY ?: return
        val rate = SHIP_FOLLOW_RATE + SPEED_STEP * gauge.level(PowerUp.SPEED)
        shipY += (target - shipY) * (1f - exp(-dt * rate))
    }

    private fun updatePlaying(dt: Float) {
        playTime += dt
        invulnerable -= dt
        powerUpMessageSec -= dt

        trailTimer += dt
        while (trailTimer >= TRAIL_STEP_SEC) {
            trailTimer -= TRAIL_STEP_SEC
            trail.addLast(shipY)
            if (trail.size > TRAIL_LENGTH) trail.removeFirst()
        }

        fire(dt)
        spawn(dt)
        moveEverything(dt)
        resolveHits()
    }

    /** Heights of the ship followed by each option. */
    private fun gunPositions(): List<Pair<Float, Float>> {
        val guns = mutableListOf(SHIP_X + 0.05f to shipY + 0.005f)
        for (i in 0 until gauge.level(PowerUp.OPTION)) {
            val delay = OPTION_DELAY_STEPS * (i + 1)
            val y = trail.elementAtOrNull(trail.size - 1 - delay) ?: shipY
            guns.add(optionX(i) to y)
        }
        return guns
    }

    private fun optionX(index: Int) = SHIP_X - 0.07f * (index + 1)

    private fun fire(dt: Float) {
        shotTimer -= dt
        missileTimer -= dt
        if (shotTimer <= 0f) {
            shotTimer = SHOT_INTERVAL_SEC
            for ((x, y) in gunPositions()) {
                shots.add(Shot(x, y, SHOT_SPEED, 0f, missile = false))
                if (gauge.level(PowerUp.DOUBLE) > 0) shots.add(Shot(x, y, SHOT_SPEED * 0.7f, -SHOT_SPEED * 0.7f, missile = false))
            }
        }
        if (gauge.level(PowerUp.MISSILE) > 0 && missileTimer <= 0f) {
            missileTimer = MISSILE_INTERVAL_SEC
            for ((x, y) in gunPositions()) shots.add(Shot(x, y, MISSILE_SPEED, MISSILE_SPEED, missile = true))
        }
    }

    private fun spawn(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer > 0f) return
        // Squads come faster the longer the game lasts.
        val ramp = 1f + playTime / 90f
        spawnTimer = SPAWN_INTERVAL_SEC / (pace * ramp)

        val right = aspect + 0.08f
        val roll = Random.nextFloat()
        when {
            roll < 0.35f -> {
                val squad = Squad(5)
                val y = 0.2f + Random.nextFloat() * 0.6f
                repeat(5) { i ->
                    enemies.add(Enemy(Kind.FAN, right + i * 0.1f, y, y, -0.42f * pace, 0f, i * 0.6f, squad))
                }
            }
            roll < 0.6f -> {
                val squad = Squad(3)
                val y = 0.25f + Random.nextFloat() * 0.5f
                repeat(3) { i ->
                    enemies.add(Enemy(Kind.WAVER, right + i * 0.16f, y, y, -0.3f * pace, 0f, i * 1.1f, squad))
                }
            }
            roll < 0.85f || enemies.any { it.kind == Kind.GUNNER } -> {
                repeat(2) { i ->
                    val y = 0.1f + Random.nextFloat() * 0.8f
                    val x = right + i * 0.25f
                    // Aim at where the ship is now.
                    val time = (x - SHIP_X) / (0.75f * pace)
                    enemies.add(Enemy(Kind.DART, x, y, y, -0.75f * pace, (shipY - y) / time, 0f, null))
                }
            }
            else -> {
                val y = 0.25f + Random.nextFloat() * 0.5f
                enemies.add(Enemy(Kind.GUNNER, right, y, y, -0.12f * pace, 0f, 0f, null))
            }
        }
    }

    private fun moveEverything(dt: Float) {
        for (e in enemies) {
            e.age += dt
            e.flash -= dt
            e.x += e.vx * dt
            when (e.kind) {
                Kind.FAN -> e.y = e.baseY + 0.12f * sin(e.age * 3f + e.phase)
                Kind.WAVER -> e.y = e.baseY + 0.18f * sin(e.age * 2f + e.phase)
                Kind.DART -> e.y += e.vy * dt
                Kind.GUNNER -> {
                    e.y = e.baseY + 0.05f * sin(e.age * 1.5f)
                    e.fireTimer -= dt
                    if (e.fireTimer <= 0f && e.x < aspect) {
                        e.fireTimer = GUNNER_FIRE_SEC / pace
                        val dx = SHIP_X - e.x
                        val dy = shipY - e.y
                        val len = hypot(dx, dy)
                        val speed = BULLET_SPEED * pace
                        bullets.add(Bullet(e.x, e.y, dx / len * speed, dy / len * speed))
                    }
                }
            }
        }
        for (e in enemies) if (e.x < -0.15f) e.squad?.escaped = true
        enemies.removeAll { it.x < -0.15f || it.y < -0.2f || it.y > 1.2f }

        for (s in shots) {
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
        shots.removeAll { it.x > aspect + 0.05f || it.y < -0.05f || it.y > 1.05f }
        for (b in bullets) {
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        bullets.removeAll { it.x < -0.05f || it.x > aspect + 0.05f || it.y < -0.05f || it.y > 1.05f }
        for (c in capsules) {
            c.age += dt
            c.x -= CAPSULE_DRIFT * dt
        }
        capsules.removeAll { it.x < -0.05f }
    }

    private fun resolveHits() {
        val shotIterator = shots.iterator()
        while (shotIterator.hasNext()) {
            val s = shotIterator.next()
            val target = enemies.firstOrNull { hypot(it.x - s.x, it.y - s.y) < it.kind.radius + SHOT_RADIUS } ?: continue
            shotIterator.remove()
            target.hp--
            target.flash = 0.08f
            if (target.hp <= 0) destroy(target)
        }
        enemies.removeAll { it.hp <= 0 }

        val capsuleIterator = capsules.iterator()
        while (capsuleIterator.hasNext()) {
            val c = capsuleIterator.next()
            if (hypot(c.x - SHIP_X, c.y - shipY) < CAPSULE_PICKUP_RADIUS) {
                capsuleIterator.remove()
                gauge.collectCapsule()
                score += CAPSULE_POINTS
            }
        }

        if (invulnerable > 0f) return
        val hitByEnemy = enemies.any { hypot(it.x - SHIP_X, it.y - shipY) < it.kind.radius + SHIP_RADIUS }
        val hitByBullet = bullets.any { hypot(it.x - SHIP_X, it.y - shipY) < BULLET_RADIUS + SHIP_RADIUS }
        if (hitByEnemy || hitByBullet) loseLife()
    }

    private fun destroy(e: Enemy) {
        score += e.kind.points
        explosions.add(Explosion(e.x, e.y, if (e.kind == Kind.GUNNER) 1.6f else 1f))
        val squad = e.squad
        val dropsCapsule = if (squad != null) {
            squad.alive--
            squad.alive == 0 && !squad.escaped
        } else {
            e.kind == Kind.GUNNER
        }
        if (dropsCapsule) capsules.add(Capsule(e.x, e.y))
    }

    private fun loseLife() {
        explosions.add(Explosion(SHIP_X, shipY, 1.8f))
        bullets.clear()
        gauge.reset()
        lives--
        if (lives <= 0) {
            state = State.GAME_OVER
            stateTime = 0f
            if (score > best) {
                best = score
                prefs.edit().putInt(bestKey, best).apply()
            }
        } else {
            invulnerable = RESPAWN_INVULNERABLE_SEC
        }
    }

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        val u = height.toFloat()
        aspect = width / u

        // With the camera backdrop, GameView has already drawn the camera image underneath.
        if (!options.cameraBackdrop) {
            canvas.drawRect(0f, 0f, width.toFloat(), u, spacePaint)
            for (star in stars) {
                canvas.drawRect(star.x * u, star.y * u, (star.x + star.size) * u, (star.y + star.size) * u, starPaint)
            }
        }

        val frame = (stateTime * 8f).toInt()
        for (c in capsules) drawSprite(canvas, sprites.capsule[(c.age * 6f).toInt() % 2], c.x, c.y, u)
        for (e in enemies) {
            val frames = when (e.kind) {
                Kind.FAN -> sprites.fan
                Kind.WAVER -> sprites.waver
                Kind.DART -> sprites.dart
                Kind.GUNNER -> sprites.gunner
            }
            drawSprite(canvas, frames[frame % frames.size], e.x, e.y, u, if (e.flash > 0f) blinkPaint else spritePaint)
        }
        for (s in shots) {
            if (s.missile) {
                canvas.drawRect((s.x - 0.012f) * u, (s.y - 0.006f) * u, (s.x + 0.012f) * u, (s.y + 0.006f) * u, missilePaint)
            } else {
                canvas.drawRect((s.x - 0.02f) * u, (s.y - 0.005f) * u, (s.x + 0.02f) * u, (s.y + 0.005f) * u, shotPaint)
            }
        }
        for (b in bullets) {
            canvas.drawCircle(b.x * u, b.y * u, BULLET_RADIUS * u, bulletPaint)
            canvas.drawCircle(b.x * u, b.y * u, BULLET_RADIUS * 0.45f * u, bulletCorePaint)
        }

        if (state != State.GAME_OVER) {
            for (i in 0 until gauge.level(PowerUp.OPTION)) {
                val y = trail.elementAtOrNull(trail.size - 1 - OPTION_DELAY_STEPS * (i + 1)) ?: shipY
                drawSprite(canvas, sprites.option[frame % 2], optionX(i), y, u)
            }
            // Blink while invulnerable after losing a life.
            val visible = invulnerable <= 0f || (stateTime * 10f).toInt() % 2 == 0
            if (visible) drawSprite(canvas, sprites.ship[frame % 2], SHIP_X, shipY, u)
        }
        for (e in explosions) {
            val index = (e.age / EXPLOSION_SEC * sprites.explosion.size).toInt().coerceAtMost(sprites.explosion.size - 1)
            drawSprite(canvas, sprites.explosion[index], e.x, e.y, u, scale = e.scale)
        }

        drawHud(canvas, width, u)
    }

    /** Draws [bitmap] centred on a world position, scaled so one art pixel is [PIXEL] screen heights. */
    private fun drawSprite(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, u: Float, paint: Paint = spritePaint, scale: Float = 1f) {
        val halfW = bitmap.width * PIXEL * scale * u / 2f
        val halfH = bitmap.height * PIXEL * scale * u / 2f
        rect.set(x * u - halfW, y * u - halfH, x * u + halfW, y * u + halfH)
        canvas.drawBitmap(bitmap, null, rect, paint)
    }

    private fun drawHud(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        hud.drawCornerStats(canvas, u, options.tapMode, detector.reps, best)
        if (!options.tapMode) hud.drawBodyGauge(canvas, u, detector)

        when (state) {
            State.READY -> {
                hud.drawText(canvas, "腕立てシューター", cx, 0.3f * u, 0.09f * u)
                val lines = listOfNotNull(
                    if (options.tapMode) "ドラッグで自機を上下に動かします" else "体の高さで自機を上下に動かします",
                    "カプセルでゲージを進め、" + (if (options.tapMode) "タップ" else "腕立て") + "でパワーアップ",
                    if (options.tapMode) "タップでスタート" else "腕立て伏せを1回するとスタート！",
                    hud.startHint(detector, options.tapMode),
                )
                lines.forEachIndexed { i, line -> hud.drawText(canvas, line, cx, (0.45f + 0.08f * i) * u, 0.05f * u) }
            }
            State.PLAYING -> {
                hud.drawText(canvas, "SCORE %07d".format(score), cx, 0.08f * u, 0.055f * u)
                hud.drawText(canvas, "残り $lives", cx, 0.14f * u, 0.04f * u)
                drawPowerUpGauge(canvas, cx, u)
                val bought = lastPowerUp
                if (bought != null && powerUpMessageSec > 0f) {
                    val text = if (bought == PowerUp.SPEED) "SPEED UP!" else "${bought.label}!"
                    hud.drawText(canvas, text, SHIP_X * u, (shipY - 0.07f) * u, 0.05f * u)
                }
                if (isPaused()) hud.drawPausedBanner(canvas, width, u)
            }
            State.GAME_OVER -> {
                hud.drawText(canvas, "GAME OVER", cx, 0.35f * u, 0.11f * u)
                hud.drawText(canvas, "SCORE %07d   ベスト %07d".format(score, best), cx, 0.47f * u, 0.05f * u)
                if (!options.tapMode) hud.drawText(canvas, "腕立て ${detector.reps}回", cx, 0.55f * u, 0.05f * u)
                if (stateTime > RESTART_DELAY_SEC) {
                    val retry = if (options.tapMode) "タップでもう一度" else "腕立て伏せ1回でもう一度"
                    hud.drawText(canvas, retry, cx, 0.66f * u, 0.055f * u)
                }
            }
        }
    }

    /** The power-up bar along the bottom, with the highlighted slot in orange. */
    private fun drawPowerUpGauge(canvas: Canvas, cx: Float, u: Float) {
        val slots = PowerUp.entries
        val boxW = 0.24f * u
        val boxH = 0.065f * u
        val gap = 0.015f * u
        val total = slots.size * boxW + (slots.size - 1) * gap
        val top = 0.91f * u
        gaugeBorderPaint.strokeWidth = 0.004f * u
        gaugeTextPaint.textSize = 0.04f * u
        slots.forEachIndexed { i, powerUp ->
            val left = cx - total / 2 + i * (boxW + gap)
            rect.set(left, top, left + boxW, top + boxH)
            val selected = gauge.cursor == i
            canvas.drawRect(rect, if (selected) gaugeSelectedPaint else gaugeBoxPaint)
            canvas.drawRect(rect, gaugeBorderPaint)
            gaugeTextPaint.color = when {
                gauge.isMaxed(powerUp) -> Color.rgb(120, 120, 140)
                selected -> Color.rgb(40, 20, 0)
                else -> Color.WHITE
            }
            val label = if (gauge.isMaxed(powerUp)) "MAX" else powerUp.label
            canvas.drawText(label, left + boxW / 2, top + boxH * 0.7f, gaugeTextPaint)
        }
        val selected = gauge.selected
        if (selected != null && !gauge.isMaxed(selected) && (stateTime * 3f).toInt() % 2 == 0) {
            val action = if (options.tapMode) "タップ" else "腕立て"
            hud.drawText(canvas, "${action}でパワーアップ！", cx, top - 0.02f * u, 0.045f * u)
        }
    }

    private companion object {
        /** One art pixel in screen heights. */
        const val PIXEL = 0.005f

        const val SHIP_X = 0.25f
        const val SHIP_RADIUS = 0.02f
        const val SHIP_TOP = 0.08f
        const val SHIP_BOTTOM = 0.86f
        const val SHIP_FOLLOW_RATE = 4f
        const val SPEED_STEP = 2.5f
        const val START_LIVES = 3
        const val RESPAWN_INVULNERABLE_SEC = 2.5f

        const val SHOT_INTERVAL_SEC = 0.2f
        const val SHOT_SPEED = 1.5f
        const val SHOT_RADIUS = 0.012f
        const val MISSILE_INTERVAL_SEC = 0.7f
        const val MISSILE_SPEED = 0.55f

        const val TRAIL_STEP_SEC = 1f / 30f
        const val OPTION_DELAY_STEPS = 8
        const val TRAIL_LENGTH = 40

        const val SPAWN_INTERVAL_SEC = 2.8f
        const val GUNNER_FIRE_SEC = 1.6f
        const val BULLET_SPEED = 0.45f
        const val BULLET_RADIUS = 0.011f

        const val CAPSULE_DRIFT = 0.12f
        const val CAPSULE_PICKUP_RADIUS = 0.06f
        const val CAPSULE_POINTS = 50

        const val EXPLOSION_SEC = 0.45f
        const val POWER_UP_MESSAGE_SEC = 1.2f
        const val RESTART_DELAY_SEC = 2f
        const val LOST_POSE_GRACE_SEC = 0.8f
    }
}
