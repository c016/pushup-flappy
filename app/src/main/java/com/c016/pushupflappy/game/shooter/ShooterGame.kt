package com.c016.pushupflappy.game.shooter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.game.PoseHud
import com.c016.pushupflappy.game.SoundFx
import com.c016.pushupflappy.game.SoundFx.Sfx
import com.c016.pushupflappy.motion.PushUpDetector
import com.c016.pushupflappy.pose.Pose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * A horizontal shooter in the style of 80s arcade games. The ship follows the height of the body,
 * fires automatically, and a push-up buys the power-up highlighted on the gauge.
 *
 * Each stage runs through open space, a cavern with turrets on the ceiling and floor, and a boss.
 * Clearing a stage starts the next one at a faster pace.
 *
 * World coordinates are in units of the screen height: y runs from 0 (top) to 1 (bottom) and x
 * from 0 to the screen aspect ratio.
 */
class ShooterGame(context: Context, private val options: GameOptions) : MiniGame {

    private enum class State { READY, PLAYING, GAME_OVER }

    private enum class Phase { SPACE, CAVERN, WARNING, BOSS, CLEAR }

    private enum class Kind(val radius: Float, val hp: Int, val points: Int) {
        FAN(0.036f, 1, 100),
        WAVER(0.042f, 1, 150),
        DART(0.032f, 1, 100),
        GUNNER(0.06f, 4, 500),
        TURRET(0.032f, 2, 200),
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
        /** Turrets only: hanging from the ceiling instead of standing on the floor. */
        val onCeiling: Boolean = false,
    ) {
        var hp = kind.hp
        var age = 0f
        var fireTimer = 1f
        var flash = 0f
    }

    private enum class ShotType { BULLET, LASER, MISSILE }

    private class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, val type: ShotType) {
        /** Lasers pass through enemies but hit each one only once. */
        val struck = mutableSetOf<Any>()
    }

    private class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float)
    private class Capsule(var x: Float, var y: Float, var age: Float = 0f)
    private class Explosion(val x: Float, val y: Float, val scale: Float, var age: Float = 0f)
    private class Star(var x: Float, val y: Float, val speed: Float, val size: Float)

    /** Difficulty scales enemy speed, fire rate and how often squads arrive. */
    private val basePace = when (options.difficulty) {
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
    private val sfx = SoundFx(context, options.sound)

    // The ship follows the body continuously, so the learned range must survive holds.
    private val detector = PushUpDetector(rangeMemorySec = 60f)
    private val gauge = PowerUpGauge()
    private val terrain = Terrain()

    private var state = State.READY
    private var stateTime = 0f
    private var phase = Phase.SPACE
    private var phaseTime = 0f
    private var stage = 1
    private var score = 0
    private var lives = START_LIVES
    private var aspect = 16f / 9f

    private var shipY = 0.5f
    private var targetY: Float? = null
    private var invulnerable = 0f
    private var shieldHits = 0
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
    private var boss: Boss? = null
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
    private val laserPaint = Paint().apply { color = Color.rgb(120, 240, 255) }
    private val missilePaint = Paint().apply { color = Color.rgb(255, 160, 60) }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 90, 170) }
    private val bulletCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 60, 110) }
    private val rockEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 150, 230)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 220, 255)
        style = Paint.Style.STROKE
    }
    private val barBackPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val barPaint = Paint().apply { color = Color.rgb(255, 80, 80) }
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
    private val path = Path()

    private val pace: Float get() = basePace * (1f + STAGE_PACE_STEP * (stage - 1))

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

    override fun close() = sfx.close()

    private fun onPushUp() {
        when (state) {
            State.READY -> startGame()
            State.PLAYING -> buyPowerUp()
            State.GAME_OVER -> if (stateTime > RESTART_DELAY_SEC) startGame()
        }
    }

    private fun startGame() {
        state = State.PLAYING
        stateTime = 0f
        stage = 1
        score = 0
        lives = START_LIVES
        shipY = targetY ?: 0.5f
        invulnerable = RESPAWN_INVULNERABLE_SEC
        shieldHits = 0
        enemies.clear()
        shots.clear()
        bullets.clear()
        capsules.clear()
        explosions.clear()
        trail.clear()
        terrain.columns.clear()
        terrain.targetDepth = 0f
        boss = null
        gauge.reset()
        detector.resetCount()
        enterPhase(Phase.SPACE)
    }

    private fun buyPowerUp() {
        val powerUp = gauge.activate() ?: return
        if (powerUp == PowerUp.SHIELD) shieldHits = SHIELD_HITS
        lastPowerUp = powerUp
        powerUpMessageSec = POWER_UP_MESSAGE_SEC
        sfx.play(Sfx.POWER_UP)
    }

    private fun enterPhase(next: Phase) {
        phase = next
        phaseTime = 0f
        when (next) {
            Phase.SPACE -> spawnTimer = 2f
            Phase.CAVERN -> terrain.targetDepth = CAVERN_DEPTH
            Phase.WARNING -> sfx.play(Sfx.WARNING, 0.8f)
            Phase.BOSS -> boss = Boss(aspect + 0.3f, 0.45f, BOSS_CORE_HP + 4 * (stage - 1))
            Phase.CLEAR -> Unit
        }
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
        phaseTime += dt
        invulnerable -= dt
        powerUpMessageSec -= dt

        trailTimer += dt
        while (trailTimer >= TRAIL_STEP_SEC) {
            trailTimer -= TRAIL_STEP_SEC
            trail.addLast(shipY)
            if (trail.size > TRAIL_LENGTH) trail.removeFirst()
        }

        for (column in terrain.scroll(TERRAIN_SPEED * dt, aspect)) maybePlaceTurret(column)
        advancePhase()
        fire(dt)
        spawnSquads(dt)
        moveEverything(dt)
        updateBoss(dt)
        resolveHits()
    }

    private fun advancePhase() {
        when (phase) {
            Phase.SPACE -> if (phaseTime > SPACE_SEC) enterPhase(Phase.CAVERN)
            Phase.CAVERN -> {
                if (phaseTime > CAVERN_SEC) terrain.targetDepth = 0f
                if (phaseTime > CAVERN_SEC && !terrain.isActive) enterPhase(Phase.WARNING)
            }
            Phase.WARNING -> if (phaseTime > WARNING_SEC) enterPhase(Phase.BOSS)
            Phase.BOSS -> if (boss == null) enterPhase(Phase.CLEAR)
            Phase.CLEAR -> if (phaseTime > CLEAR_SEC) {
                stage++
                enterPhase(Phase.SPACE)
            }
        }
    }

    private fun maybePlaceTurret(column: Terrain.Column) {
        if (phase != Phase.CAVERN || terrain.targetDepth <= 0f || Random.nextFloat() > TURRET_CHANCE * pace) return
        val onCeiling = Random.nextBoolean()
        val depth = if (onCeiling) column.ceiling else column.floor
        if (depth < 0.04f) return
        // Sit on the surface, sunk slightly into the rock.
        val y = if (onCeiling) depth + 0.022f else 1f - depth - 0.022f
        enemies.add(Enemy(Kind.TURRET, column.x, y, y, -TERRAIN_SPEED, 0f, 0f, null, onCeiling))
    }

    /** Positions the shots come from: the ship's nose, then each option. */
    private fun gunPositions(): List<Pair<Float, Float>> {
        val guns = mutableListOf(SHIP_X + 0.06f to shipY + 0.005f)
        for (i in 0 until gauge.level(PowerUp.OPTION)) guns.add(optionX(i) to optionY(i))
        return guns
    }

    private fun optionX(index: Int) = SHIP_X - 0.08f * (index + 1)

    private fun optionY(index: Int) = trail.elementAtOrNull(trail.size - 1 - OPTION_DELAY_STEPS * (index + 1)) ?: shipY

    private fun fire(dt: Float) {
        shotTimer -= dt
        missileTimer -= dt
        if (shotTimer <= 0f) {
            val laser = gauge.level(PowerUp.LASER) > 0
            shotTimer = if (laser) LASER_INTERVAL_SEC else SHOT_INTERVAL_SEC
            for ((x, y) in gunPositions()) {
                if (laser) {
                    shots.add(Shot(x + 0.05f, y, LASER_SPEED, 0f, ShotType.LASER))
                } else {
                    shots.add(Shot(x, y, SHOT_SPEED, 0f, ShotType.BULLET))
                    if (gauge.level(PowerUp.DOUBLE) > 0) shots.add(Shot(x, y, SHOT_SPEED * 0.7f, -SHOT_SPEED * 0.7f, ShotType.BULLET))
                }
            }
            sfx.play(Sfx.SHOT, 0.15f)
        }
        if (gauge.level(PowerUp.MISSILE) > 0 && missileTimer <= 0f) {
            missileTimer = MISSILE_INTERVAL_SEC
            for ((x, y) in gunPositions()) shots.add(Shot(x, y, MISSILE_SPEED, MISSILE_SPEED, ShotType.MISSILE))
        }
    }

    private fun spawnSquads(dt: Float) {
        val interval = when (phase) {
            // Squads come faster the longer open space lasts.
            Phase.SPACE -> SPAWN_INTERVAL_SEC / (1f + phaseTime / 60f)
            Phase.CAVERN -> SPAWN_INTERVAL_SEC * 1.6f
            else -> return
        }
        spawnTimer -= dt
        if (spawnTimer > 0f) return
        spawnTimer = interval / pace

        val right = aspect + 0.08f
        val top = terrain.ceilingAt(right) + 0.12f
        val bottom = 1f - terrain.floorAt(right) - 0.12f
        fun randomY() = top + Random.nextFloat() * (bottom - top).coerceAtLeast(0f)

        val roll = Random.nextFloat()
        when {
            roll < 0.35f -> {
                val squad = Squad(5)
                val y = randomY()
                repeat(5) { i -> enemies.add(Enemy(Kind.FAN, right + i * 0.1f, y, y, -0.42f * pace, 0f, i * 0.6f, squad)) }
            }
            roll < 0.6f && phase == Phase.SPACE -> {
                val squad = Squad(3)
                val y = randomY()
                repeat(3) { i -> enemies.add(Enemy(Kind.WAVER, right + i * 0.16f, y, y, -0.3f * pace, 0f, i * 1.1f, squad)) }
            }
            roll < 0.85f || phase != Phase.SPACE || enemies.any { it.kind == Kind.GUNNER } -> {
                repeat(2) { i ->
                    val y = randomY()
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

    private fun aimedBullet(fromX: Float, fromY: Float, speed: Float, angleOffset: Float = 0f) {
        val angle = atan2(shipY - fromY, SHIP_X - fromX) + angleOffset
        bullets.add(Bullet(fromX, fromY, cos(angle) * speed, sin(angle) * speed))
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
                Kind.GUNNER, Kind.TURRET -> {
                    if (e.kind == Kind.GUNNER) e.y = e.baseY + 0.05f * sin(e.age * 1.5f)
                    e.fireTimer -= dt
                    // Only fire while on screen and ahead of the ship.
                    if (e.fireTimer <= 0f && e.x < aspect && e.x > SHIP_X + 0.2f) {
                        e.fireTimer = (if (e.kind == Kind.GUNNER) GUNNER_FIRE_SEC else TURRET_FIRE_SEC) / pace
                        aimedBullet(e.x, e.y, BULLET_SPEED * pace)
                    }
                }
            }
        }
        for (e in enemies) if (e.x < -0.15f) e.squad?.escaped = true
        enemies.removeAll { it.x < -0.15f || it.y < -0.2f || it.y > 1.2f }

        for (s in shots) {
            s.x += s.vx * dt
            s.y += s.vy * dt
            // Missiles that reach the floor crawl along it.
            if (s.type == ShotType.MISSILE) {
                val floor = 1f - terrain.floorAt(s.x)
                if (terrain.floorAt(s.x) > 0f && s.y > floor - 0.012f) {
                    s.y = floor - 0.012f
                    s.vy = 0f
                    s.vx = MISSILE_CRAWL_SPEED
                }
            }
        }
        shots.removeAll {
            it.x > aspect + 0.1f || it.y < -0.05f || it.y > 1.05f ||
                (it.type != ShotType.MISSILE && terrain.hits(it.x, it.y, 0f))
        }
        for (b in bullets) {
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        bullets.removeAll { it.x < -0.05f || it.x > aspect + 0.05f || it.y < -0.05f || it.y > 1.05f || terrain.hits(it.x, it.y, 0f) }
        for (c in capsules) {
            c.age += dt
            c.x -= CAPSULE_DRIFT * dt
        }
        capsules.removeAll { it.x < -0.05f }
    }

    private fun updateBoss(dt: Float) {
        val b = boss ?: return
        b.age += dt
        b.flash -= dt
        val home = aspect - 0.3f
        when {
            b.age > BOSS_TIMEOUT_SEC -> b.x += 0.4f * dt // gives up and leaves
            b.x > home -> b.x -= 0.25f * dt
            else -> {
                b.settledTime += dt
                b.y = 0.45f + 0.25f * sin(b.settledTime * 0.8f)
                b.fireTimer -= dt
                if (b.fireTimer <= 0f) {
                    b.fireTimer = BOSS_FIRE_SEC / pace
                    if (b.attack++ % 2 == 0) {
                        for (offset in floatArrayOf(-0.25f, 0f, 0.25f)) aimedBullet(b.coreX(), b.y, BULLET_SPEED * pace, offset)
                    } else {
                        for (dy in floatArrayOf(-0.1f, 0.1f)) bullets.add(Bullet(b.x - 0.1f, b.y + dy, -0.9f * pace, 0f))
                    }
                }
            }
        }
        if (b.x > aspect + 0.4f) boss = null
    }

    private fun resolveHits() {
        val shotIterator = shots.iterator()
        while (shotIterator.hasNext()) {
            val s = shotIterator.next()
            val b = boss
            if (b != null) {
                val hit = b.hit(s.x, s.y, SHOT_RADIUS)
                if (hit != Boss.Hit.NONE) {
                    shotIterator.remove()
                    if (hit == Boss.Hit.CORE && b.defeated) defeatBoss(b)
                    continue
                }
            }
            val target = enemies.firstOrNull {
                it.hp > 0 && it !in s.struck && hypot(it.x - s.x, it.y - s.y) < it.kind.radius + SHOT_RADIUS
            } ?: continue
            if (s.type == ShotType.LASER) s.struck.add(target) else shotIterator.remove()
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
                sfx.play(Sfx.CAPSULE)
            }
        }

        // The terrain is solid even with a shield.
        if (invulnerable <= 0f && terrain.hits(SHIP_X, shipY, SHIP_RADIUS)) {
            loseLife()
            return
        }
        if (invulnerable > 0f) return
        val bulletHit = bullets.firstOrNull { hypot(it.x - SHIP_X, it.y - shipY) < BULLET_RADIUS + SHIP_RADIUS }
        val hit = bulletHit != null ||
            enemies.any { hypot(it.x - SHIP_X, it.y - shipY) < it.kind.radius + SHIP_RADIUS } ||
            boss?.touchesHull(SHIP_X, shipY, SHIP_RADIUS) == true
        if (!hit) return
        if (bulletHit != null) bullets.remove(bulletHit)
        if (shieldHits > 0) {
            shieldHits--
            if (shieldHits == 0) gauge.drop(PowerUp.SHIELD)
            invulnerable = SHIELD_INVULNERABLE_SEC
            sfx.play(Sfx.EXPLODE, 0.6f)
        } else {
            loseLife()
        }
    }

    private fun destroy(e: Enemy) {
        score += e.kind.points
        explosions.add(Explosion(e.x, e.y, if (e.kind == Kind.GUNNER) 1.6f else 1f))
        sfx.play(Sfx.EXPLODE, 0.7f)
        val squad = e.squad
        val dropsCapsule = if (squad != null) {
            squad.alive--
            squad.alive == 0 && !squad.escaped
        } else {
            e.kind == Kind.GUNNER || (e.kind == Kind.TURRET && Random.nextFloat() < TURRET_CAPSULE_CHANCE)
        }
        if (dropsCapsule) capsules.add(Capsule(e.x, e.y.coerceIn(0.15f, 0.8f)))
    }

    private fun defeatBoss(b: Boss) {
        score += BOSS_POINTS * stage
        repeat(6) {
            explosions.add(Explosion(b.x + (Random.nextFloat() - 0.5f) * 0.3f, b.y + (Random.nextFloat() - 0.5f) * 0.2f, 2f, -it * 0.12f))
        }
        bullets.clear()
        sfx.play(Sfx.BIG_EXPLODE)
        boss = null
    }

    private fun loseLife() {
        explosions.add(Explosion(SHIP_X, shipY, 1.8f))
        sfx.play(Sfx.HIT)
        bullets.clear()
        gauge.reset()
        shieldHits = 0
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
        drawTerrain(canvas, u)

        val frame = (stateTime * 8f).toInt()
        for (c in capsules) drawSprite(canvas, sprites.capsule[(c.age * 6f).toInt() % 2], c.x, c.y, u)
        boss?.let { drawBoss(canvas, it, frame, u) }
        for (e in enemies) {
            val bitmap = when (e.kind) {
                Kind.FAN -> sprites.fan[frame % 2]
                Kind.WAVER -> sprites.waver[frame % 2]
                Kind.DART -> sprites.dart
                Kind.GUNNER -> sprites.gunner
                Kind.TURRET -> sprites.turret
            }
            drawSprite(canvas, bitmap, e.x, e.y, u, if (e.flash > 0f) blinkPaint else spritePaint, flipY = e.onCeiling)
        }
        for (s in shots) {
            val (halfW, halfH, paint) = when (s.type) {
                ShotType.BULLET -> Triple(0.02f, 0.005f, shotPaint)
                ShotType.LASER -> Triple(0.06f, 0.004f, laserPaint)
                ShotType.MISSILE -> Triple(0.012f, 0.006f, missilePaint)
            }
            canvas.drawRect((s.x - halfW) * u, (s.y - halfH) * u, (s.x + halfW) * u, (s.y + halfH) * u, paint)
        }
        for (b in bullets) {
            canvas.drawCircle(b.x * u, b.y * u, BULLET_RADIUS * u, bulletPaint)
            canvas.drawCircle(b.x * u, b.y * u, BULLET_RADIUS * 0.45f * u, bulletCorePaint)
        }

        if (state != State.GAME_OVER) {
            for (i in 0 until gauge.level(PowerUp.OPTION)) drawSprite(canvas, sprites.option[frame % 2], optionX(i), optionY(i), u)
            // Blink while invulnerable after losing a life.
            val visible = invulnerable <= 0f || (stateTime * 10f).toInt() % 2 == 0
            if (visible) drawSprite(canvas, sprites.ship[frame % 2], SHIP_X, shipY, u)
            if (shieldHits > 0) {
                shieldPaint.strokeWidth = 0.004f * u * shieldHits
                shieldPaint.alpha = 120 + 40 * shieldHits
                canvas.drawCircle(SHIP_X * u, shipY * u, 0.07f * u, shieldPaint)
            }
        }
        for (e in explosions) {
            if (e.age < 0f) continue
            val index = (e.age / EXPLOSION_SEC * sprites.explosion.size).toInt().coerceAtMost(sprites.explosion.size - 1)
            drawSprite(canvas, sprites.explosion[index], e.x, e.y, u, scale = e.scale)
        }

        drawHud(canvas, width, u)
    }

    private fun drawTerrain(canvas: Canvas, u: Float) {
        val columns = terrain.columns
        if (columns.isEmpty() || !terrain.isActive) return
        rockEdgePaint.strokeWidth = 0.006f * u
        for (ceiling in booleanArrayOf(true, false)) {
            path.reset()
            val edge = if (ceiling) 0f else u
            path.moveTo(columns.first().x * u, edge)
            for (c in columns) path.lineTo(c.x * u, if (ceiling) c.ceiling * u else (1f - c.floor) * u)
            path.lineTo(columns.last().x * u, edge)
            path.close()
            canvas.drawPath(path, rockPaint)
            canvas.drawPath(path, rockEdgePaint)
        }
    }

    private fun drawBoss(canvas: Canvas, b: Boss, frame: Int, u: Float) {
        val paint = if (b.flash > 0f) blinkPaint else spritePaint
        drawSprite(canvas, sprites.bossBody, b.x, b.y, u, paint)
        drawSprite(canvas, sprites.bossCore[if (b.coreExposed) frame % 2 else 1], b.coreX(), b.y, u)
        for (i in b.plates.indices) if (b.plates[i] > 0) drawSprite(canvas, sprites.bossPlate, b.plateX(i), b.y, u, paint)
    }

    /** Draws [bitmap] centred on a world position, scaled so one art pixel is [PIXEL] screen heights. */
    private fun drawSprite(
        canvas: Canvas,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        u: Float,
        paint: Paint = spritePaint,
        scale: Float = 1f,
        flipY: Boolean = false,
    ) {
        val halfW = bitmap.width * PIXEL * scale * u / 2f
        val halfH = bitmap.height * PIXEL * scale * u / 2f
        rect.set(x * u - halfW, y * u - halfH, x * u + halfW, y * u + halfH)
        if (flipY) {
            canvas.save()
            canvas.scale(1f, -1f, x * u, y * u)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, null, rect, paint)
        }
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
                hud.drawText(canvas, "STAGE $stage   残り $lives", cx, 0.14f * u, 0.04f * u)
                drawPowerUpGauge(canvas, cx, u)
                drawPowerUpMessage(canvas, u)
                drawPhaseBanner(canvas, cx, u)
                boss?.let { drawBossBar(canvas, cx, it, u) }
                if (isPaused()) hud.drawPausedBanner(canvas, width, u)
            }
            State.GAME_OVER -> {
                hud.drawText(canvas, "GAME OVER", cx, 0.35f * u, 0.11f * u)
                hud.drawText(canvas, "SCORE %07d   ベスト %07d".format(score, best), cx, 0.47f * u, 0.05f * u)
                val reps = if (options.tapMode) "" else "   腕立て ${detector.reps}回"
                hud.drawText(canvas, "STAGE $stage$reps", cx, 0.55f * u, 0.05f * u)
                if (stateTime > RESTART_DELAY_SEC) {
                    val retry = if (options.tapMode) "タップでもう一度" else "腕立て伏せ1回でもう一度"
                    hud.drawText(canvas, retry, cx, 0.66f * u, 0.055f * u)
                }
            }
        }
    }

    private fun drawPowerUpMessage(canvas: Canvas, u: Float) {
        val bought = lastPowerUp ?: return
        if (powerUpMessageSec <= 0f) return
        val text = if (bought == PowerUp.SPEED) "SPEED UP!" else "${bought.label}!"
        hud.drawText(canvas, text, SHIP_X * u, (shipY - 0.07f) * u, 0.05f * u)
    }

    private fun drawPhaseBanner(canvas: Canvas, cx: Float, u: Float) {
        val blink = (stateTime * 4f).toInt() % 2 == 0
        when (phase) {
            Phase.SPACE -> if (phaseTime < 2.5f) hud.drawText(canvas, "STAGE $stage", cx, 0.42f * u, 0.09f * u)
            Phase.WARNING -> if (blink) hud.drawText(canvas, "WARNING!!", cx, 0.42f * u, 0.11f * u)
            Phase.CLEAR -> hud.drawText(canvas, "STAGE $stage CLEAR!", cx, 0.42f * u, 0.09f * u)
            else -> Unit
        }
    }

    private fun drawBossBar(canvas: Canvas, cx: Float, b: Boss, u: Float) {
        val total = b.plates.size * Boss.PLATE_HP + BOSS_CORE_HP + 4 * (stage - 1)
        val left = b.plates.sumOf { it.coerceAtLeast(0) } + b.coreHp.coerceAtLeast(0)
        val w = 0.6f * u
        val top = 0.17f * u
        canvas.drawRect(cx - w / 2, top, cx + w / 2, top + 0.018f * u, barBackPaint)
        canvas.drawRect(cx - w / 2, top, cx - w / 2 + w * left / total, top + 0.018f * u, barPaint)
    }

    /** The power-up bar along the bottom, with the highlighted slot in orange. */
    private fun drawPowerUpGauge(canvas: Canvas, cx: Float, u: Float) {
        val slots = PowerUp.entries
        val boxW = 0.2f * u
        val boxH = 0.06f * u
        val gap = 0.012f * u
        val total = slots.size * boxW + (slots.size - 1) * gap
        val top = 0.92f * u
        gaugeBorderPaint.strokeWidth = 0.004f * u
        gaugeTextPaint.textSize = 0.034f * u
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
        const val PIXEL = Boss.PIXEL

        const val SHIP_X = 0.25f
        const val SHIP_RADIUS = 0.02f
        const val SHIP_TOP = 0.08f
        const val SHIP_BOTTOM = 0.86f
        const val SHIP_FOLLOW_RATE = 4f
        const val SPEED_STEP = 2.5f
        const val START_LIVES = 3
        const val RESPAWN_INVULNERABLE_SEC = 2.5f
        const val SHIELD_HITS = 3
        const val SHIELD_INVULNERABLE_SEC = 0.6f

        const val SHOT_INTERVAL_SEC = 0.2f
        const val SHOT_SPEED = 1.5f
        const val SHOT_RADIUS = 0.012f
        const val LASER_INTERVAL_SEC = 0.25f
        const val LASER_SPEED = 2.2f
        const val MISSILE_INTERVAL_SEC = 0.7f
        const val MISSILE_SPEED = 0.55f
        const val MISSILE_CRAWL_SPEED = 0.7f

        const val TRAIL_STEP_SEC = 1f / 30f
        const val OPTION_DELAY_STEPS = 8
        const val TRAIL_LENGTH = 40

        const val STAGE_PACE_STEP = 0.15f
        const val SPACE_SEC = 35f
        const val CAVERN_SEC = 40f
        const val WARNING_SEC = 3f
        const val CLEAR_SEC = 4f
        const val CAVERN_DEPTH = 0.1f
        const val TERRAIN_SPEED = 0.2f
        const val TURRET_CHANCE = 0.12f
        const val TURRET_FIRE_SEC = 2.2f
        const val TURRET_CAPSULE_CHANCE = 0.3f

        const val SPAWN_INTERVAL_SEC = 2.8f
        const val GUNNER_FIRE_SEC = 1.6f
        const val BULLET_SPEED = 0.45f
        const val BULLET_RADIUS = 0.011f

        const val BOSS_CORE_HP = 12
        const val BOSS_FIRE_SEC = 1.5f
        const val BOSS_TIMEOUT_SEC = 45f
        const val BOSS_POINTS = 5000

        const val CAPSULE_DRIFT = 0.12f
        const val CAPSULE_PICKUP_RADIUS = 0.06f
        const val CAPSULE_POINTS = 50

        const val EXPLOSION_SEC = 0.45f
        const val POWER_UP_MESSAGE_SEC = 1.2f
        const val RESTART_DELAY_SEC = 2f
        const val LOST_POSE_GRACE_SEC = 0.8f
    }
}
