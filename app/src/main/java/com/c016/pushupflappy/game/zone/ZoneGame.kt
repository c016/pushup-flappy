package com.c016.pushupflappy.game.zone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.game.PoseHud
import com.c016.pushupflappy.game.SoundFx
import com.c016.pushupflappy.game.SoundFx.Sfx
import com.c016.pushupflappy.motion.PushUpDetector
import com.c016.pushupflappy.pose.Pose
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * A cute side-scroller in a world that loops horizontally. The ship flies on its own in one
 * direction and only turns around when it touches a reverse balloon; its height follows the body.
 * Shots fire automatically, a push-up drops a bomb, and coins buy upgrades at the shop balloon,
 * where the cursor steps through the items on its own and a push-up buys the highlighted one.
 * Destroying every flower base calls the boss; beating it moves on to the next stage.
 *
 * World coordinates are in units of the screen height: y runs from 0 (top) to 1 (bottom) and x
 * runs around the loop from 0 to [WORLD_WIDTH].
 */
class ZoneGame(context: Context, private val options: GameOptions) : MiniGame {

    private enum class State { READY, PLAYING, SHOP, GAME_OVER }

    private enum class Phase { BASES, WARNING, BOSS, CLEAR }

    private enum class Kind(val radius: Float, val hp: Int, val points: Int, val coins: Int) {
        PUFF(0.04f, 1, 100, 50),
        BEE(0.035f, 1, 150, 100),
    }

    private class Enemy(val kind: Kind, var x: Float, var y: Float, val vx: Float, val baseY: Float, val phase: Float) {
        var hp = kind.hp
        var age = 0f
    }

    private class Base(val x: Float, val y: Float, var hp: Int) {
        var spawnTimer = 1f
        var fireTimer = 2f
        var flash = 0f
    }

    private class Shot(var x: Float, var y: Float, val vx: Float, val vy: Float, val laser: Boolean) {
        var traveled = 0f
        val struck = mutableSetOf<Any>()
    }

    private class Bomb(var x: Float, var y: Float, val vx: Float, var vy: Float)
    private class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float)
    private class Coin(var x: Float, var y: Float, val value: Int, var age: Float = 0f)
    private class Pop(val x: Float, val y: Float, val scale: Float, var age: Float = 0f)
    private class Balloon(val x: Float, val y: Float, var cooldown: Float = 0f)
    private class ShopBalloon(var x: Float, val baseY: Float, var age: Float = 0f) {
        val y: Float get() = baseY + 0.03f * sin(age * 2f)
    }

    private class BossState(var hp: Int) {
        var screenX = Float.NaN
        var y = 0.42f
        var age = 0f
        var fireTimer = 2.5f
        var spawnTimer = 4f
        var flash = 0f
    }

    /** Colours for one stage: the sky gradient, and the grass and soil of the ground. */
    private class Palette(val sky: Int, val horizon: Int, val grass: Int, val grassLight: Int, val soil: Int, val soilDark: Int)

    private val palettes = listOf(
        // Clear blue day, warm sunset, starry violet.
        Palette(rgb(30, 90, 230), rgb(130, 195, 255), rgb(80, 200, 80), rgb(160, 240, 120), rgb(210, 150, 90), rgb(180, 120, 70)),
        Palette(rgb(255, 110, 90), rgb(255, 215, 150), rgb(230, 140, 200), rgb(255, 200, 235), rgb(170, 110, 180), rgb(140, 85, 150)),
        Palette(rgb(60, 30, 150), rgb(170, 130, 240), rgb(90, 200, 220), rgb(170, 245, 250), rgb(90, 90, 170), rgb(70, 70, 140)),
    )

    /** Scenery placed around the loop or across a parallax band; [variant] picks the sprite. */
    private class Prop(val x: Float, val y: Float, val variant: Int, val scale: Float)

    private val world = LoopingWorld(WORLD_WIDTH)

    private val basePace = when (options.difficulty) {
        Difficulty.EASY -> 0.7f
        Difficulty.NORMAL -> 1f
        Difficulty.HARD -> 1.3f
    }

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    private val bestKey = listOfNotNull(
        "zone_best",
        options.difficulty.name.lowercase(),
        "tap".takeIf { options.tapMode },
    ).joinToString("_")
    private var best = prefs.getInt(bestKey, 0)

    private val sprites = ZoneSprites(context.resources)

    // Clouds live in parallax bands that repeat every CLOUD_SPAN; plants are fixed around the loop.
    private val farClouds = List(5) { Prop(it * CLOUD_SPAN / 5 + Random.nextFloat() * 0.3f, 0.1f + Random.nextFloat() * 0.18f, it % 3, 1.3f) }
    private val nearClouds = List(4) { Prop(it * CLOUD_SPAN / 4 + Random.nextFloat() * 0.4f, 0.42f + Random.nextFloat() * 0.15f, it % 3, 2f) }
    private val plants = List((WORLD_WIDTH / PLANT_SPACING).toInt()) {
        Prop(it * PLANT_SPACING + Random.nextFloat() * 0.15f, GROUND_Y + 0.015f, Random.nextInt(5), 0.9f + Random.nextFloat() * 0.5f)
    }
    private val hud = PoseHud()
    private val sfx = SoundFx(context, options.sound)

    // The ship follows the body continuously, so the learned range must survive holds.
    private val detector = PushUpDetector(rangeMemorySec = 60f)
    private val shop = ZoneShop()

    private var state = State.READY
    private var stateTime = 0f
    private var phase = Phase.BASES
    private var phaseTime = 0f
    private var stage = 1
    private var score = 0
    private var coins = 0
    private var lives = START_LIVES
    private var aspect = 16f / 9f

    private var shipX = 0f
    private var shipY = 0.5f
    private var direction = 1
    private var targetY: Float? = null

    /** Where on screen (in world units from the left edge) the ship sits; eases across after turning. */
    private var shipScreenX = Float.NaN

    /** Distance flown without wrapping, for the parallax background. */
    private var traveled = 0f
    private var invulnerable = 0f
    private var shotTimer = 0f
    private var bombCooldown = 0f
    private var enemyTimer = 3f
    private var shopTimer = FIRST_SHOP_SEC
    private var shopCursor = 0
    private var shopCursorTimer = 0f
    private var shopSec = 0f
    private var shopMessage = ""

    private val bases = mutableListOf<Base>()
    private val enemies = mutableListOf<Enemy>()
    private val shots = mutableListOf<Shot>()
    private val bombs = mutableListOf<Bomb>()
    private val bullets = mutableListOf<Bullet>()
    private val coinsOnField = mutableListOf<Coin>()
    private val pops = mutableListOf<Pop>()
    private val reverseBalloons = mutableListOf<Balloon>()
    private var shopBalloon: ShopBalloon? = null
    private var boss: BossState? = null

    /** Seconds since the body was last seen; the game pauses while it is out of view. */
    private var unseenSec = 0f

    private val spritePaint = Paint() // no filtering: keep the pixel art crisp
    private val blinkPaint = Paint().apply { alpha = 120 }
    private val skyPaint = Paint()
    private var skyKey = -1
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(255, 120, 180) }
    private val laserPaint = Paint().apply { color = rgb(120, 220, 255) }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(150, 90, 230) }
    private val bulletCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val radarBackPaint = Paint().apply { color = Color.argb(150, 70, 50, 90) }
    private val radarBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(255, 140, 190) }
    private val radarReversePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(255, 110, 110) }
    private val radarShopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(200, 160, 255) }
    private val radarShipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadePaint = Paint().apply { color = Color.argb(170, 10, 10, 40) }
    private val panelPaint = Paint().apply { color = rgb(20, 30, 110) }
    private val panelBorderPaint = Paint().apply {
        color = rgb(255, 220, 80)
        style = Paint.Style.STROKE
    }
    private val rowPaint = Paint().apply { color = rgb(40, 60, 170) }
    private val rowSelectedPaint = Paint().apply { color = rgb(255, 200, 60) }
    private val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val rect = RectF()
    private val iconRect = RectF()
    private val path = Path()

    private val pace: Float get() = basePace * (1f + STAGE_PACE_STEP * (stage - 1))
    private val shipSpeed: Float get() = SHIP_SPEED + SPEED_STEP * shop.level(ShopItem.SPEED)
    private val palette: Palette get() = palettes[(stage - 1) % palettes.size]

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
            State.PLAYING -> dropBomb()
            State.SHOP -> buySelected()
            State.GAME_OVER -> if (stateTime > RESTART_DELAY_SEC) startGame()
        }
    }

    private fun startGame() {
        state = State.PLAYING
        stateTime = 0f
        stage = 1
        score = 0
        coins = 0
        lives = START_LIVES
        shop.reset()
        startStage()
    }

    private fun startStage() {
        phase = Phase.BASES
        phaseTime = 0f
        shipX = 0f
        shipY = targetY ?: 0.5f
        direction = 1
        invulnerable = RESPAWN_INVULNERABLE_SEC
        enemyTimer = 3f
        shopTimer = FIRST_SHOP_SEC
        enemies.clear()
        shots.clear()
        bombs.clear()
        bullets.clear()
        coinsOnField.clear()
        shopBalloon = null
        boss = null

        // Bases spread evenly around the loop, each nudged a little, starting ahead of the ship.
        bases.clear()
        for (i in 0 until BASE_COUNT) {
            val x = world.wrap(1.5f + i * WORLD_WIDTH / BASE_COUNT + (Random.nextFloat() - 0.5f) * 0.4f)
            bases.add(Base(x, 0.22f + Random.nextFloat() * 0.45f, BASE_HP + 2 * (stage - 1)))
        }
        // Reverse balloons sit between bases, alternating high and low.
        reverseBalloons.clear()
        for (i in 0 until REVERSE_COUNT) {
            val x = world.wrap(1.5f + (i + 0.5f) * WORLD_WIDTH / REVERSE_COUNT)
            reverseBalloons.add(Balloon(x, if (i % 2 == 0) 0.25f else 0.7f))
        }
    }

    private fun dropBomb() {
        if (bombCooldown > 0f) return
        bombCooldown = BOMB_COOLDOWN_SEC
        bombs.add(Bomb(shipX, shipY + 0.03f, direction * shipSpeed * 0.6f, 0.15f))
        sfx.play(Sfx.FLAP, 0.7f)
    }

    private fun shopRows(): Int = ShopItem.entries.size + 1 // last row leaves the shop

    private fun buySelected() {
        if (shopCursor >= ShopItem.entries.size) {
            leaveShop()
            return
        }
        val item = ShopItem.entries[shopCursor]
        val left = shop.buy(item, coins)
        if (left == null) {
            shopMessage = if (shop.isSoldOut(item)) "売り切れです" else "コインが足りません"
            return
        }
        coins = left
        if (item == ShopItem.EXTRA_LIFE) lives++
        shopMessage = "${item.label}を買いました！"
        sfx.play(Sfx.POWER_UP)
    }

    private fun leaveShop() {
        state = State.PLAYING
        shopBalloon = null
        shopTimer = SHOP_INTERVAL_SEC
        invulnerable = maxOf(invulnerable, 1f)
    }

    override fun update(dtSec: Float) {
        stateTime += dtSec
        if (options.tapMode || detector.bodyVisible) unseenSec = 0f else unseenSec += dtSec
        if (state == State.PLAYING && isPaused()) return

        followTarget(dtSec)
        for (p in pops) p.age += dtSec
        pops.removeAll { it.age > POP_SEC }

        when (state) {
            State.PLAYING -> updatePlaying(dtSec)
            State.SHOP -> updateShop(dtSec)
            else -> Unit
        }
    }

    private fun isPaused() = unseenSec > LOST_POSE_GRACE_SEC

    private fun followTarget(dt: Float) {
        val target = targetY ?: return
        shipY += (target - shipY) * (1f - exp(-dt * SHIP_FOLLOW_RATE))
    }

    private fun updateShop(dt: Float) {
        shopSec += dt
        val rows = shopRows()
        if (options.tapMode) {
            // Dragging picks the row directly.
            val y = ((shipY - SHIP_TOP) / (SHIP_BOTTOM - SHIP_TOP)).coerceIn(0f, 0.999f)
            shopCursor = (y * rows).toInt()
        } else if (detector.phase != PushUpDetector.Phase.DOWN) {
            // A push-up moves the body through every height, so the cursor steps on its own instead,
            // holding still while the body is down so the purchase lands on the highlighted row.
            shopCursorTimer += dt
            if (shopCursorTimer > SHOP_CURSOR_SEC) {
                shopCursorTimer = 0f
                shopCursor = (shopCursor + 1) % rows
            }
        }
        // Never get stuck in the shop if the player walks away.
        if (shopSec > SHOP_TIMEOUT_SEC) leaveShop()
    }

    private fun updatePlaying(dt: Float) {
        phaseTime += dt
        invulnerable -= dt
        bombCooldown -= dt

        val move = direction * shipSpeed * dt
        shipX = world.wrap(shipX + move)
        traveled += move
        val anchor = anchorFor(direction)
        if (shipScreenX.isNaN()) shipScreenX = anchor
        shipScreenX += (anchor - shipScreenX) * (1f - exp(-dt * 1.5f))

        advancePhase()
        fire(dt)
        spawnEnemies(dt)
        updateShopBalloon(dt)
        moveEverything(dt)
        updateBoss(dt)
        resolveHits()
    }

    /** The ship sits towards the back of the screen so more of what lies ahead is visible. */
    private fun anchorFor(dir: Int) = if (dir > 0) aspect * 0.3f else aspect * 0.7f

    /** Screen x (world units from the left edge) of something at world x [x]. */
    private fun screenX(x: Float) = shipScreenX + world.delta(shipX, x)

    private fun onScreen(x: Float, margin: Float = 0.2f) = screenX(x) in -margin..aspect + margin

    private fun advancePhase() {
        when (phase) {
            Phase.BASES -> if (bases.isEmpty()) {
                phase = Phase.WARNING
                phaseTime = 0f
                sfx.play(Sfx.WARNING, 0.8f)
            }
            Phase.WARNING -> if (phaseTime > WARNING_SEC) {
                phase = Phase.BOSS
                phaseTime = 0f
                boss = BossState(BOSS_HP + 10 * (stage - 1))
            }
            Phase.BOSS -> if (boss == null) {
                phase = Phase.CLEAR
                phaseTime = 0f
            }
            Phase.CLEAR -> if (phaseTime > CLEAR_SEC) {
                stage++
                startStage()
            }
        }
    }

    private fun fire(dt: Float) {
        shotTimer -= dt
        if (shotTimer > 0f) return
        val laser = shop.level(ShopItem.LASER) > 0
        shotTimer = if (laser) LASER_INTERVAL_SEC else SHOT_INTERVAL_SEC
        val x = world.wrap(shipX + direction * 0.06f)
        if (laser) {
            shots.add(Shot(x, shipY, direction * LASER_SPEED, 0f, laser = true))
        } else {
            shots.add(Shot(x, shipY, direction * SHOT_SPEED, 0f, laser = false))
            if (shop.level(ShopItem.WIDE) > 0) {
                for (angle in floatArrayOf(-0.3f, 0.3f)) {
                    shots.add(Shot(x, shipY, direction * SHOT_SPEED * cos(angle), SHOT_SPEED * sin(angle), laser = false))
                }
            }
        }
        sfx.play(Sfx.SHOT, 0.12f)
    }

    private fun spawnEnemies(dt: Float) {
        if (phase != Phase.BASES) return
        // Bases near the ship release puffs.
        for (b in bases) {
            b.flash -= dt
            if (abs(world.delta(shipX, b.x)) > BASE_ACTIVE_RANGE) continue
            b.spawnTimer -= dt
            if (b.spawnTimer <= 0f) {
                b.spawnTimer = BASE_SPAWN_SEC / pace
                enemies.add(Enemy(Kind.PUFF, b.x, b.y, 0f, b.y, Random.nextFloat() * 6f))
            }
            b.fireTimer -= dt
            if (b.fireTimer <= 0f && onScreen(b.x, 0f)) {
                b.fireTimer = BASE_FIRE_SEC / pace
                aimedBullet(b.x, b.y, BULLET_SPEED * pace)
            }
        }
        // Bee squads come from ahead.
        enemyTimer -= dt
        if (enemyTimer <= 0f) {
            enemyTimer = BEE_INTERVAL_SEC / pace
            val y = 0.15f + Random.nextFloat() * 0.6f
            repeat(3) { i ->
                val x = world.wrap(shipX + direction * (aspect * 0.8f + i * 0.12f))
                enemies.add(Enemy(Kind.BEE, x, y, -direction * BEE_SPEED * pace, y, i * 0.8f))
            }
        }
    }

    private fun updateShopBalloon(dt: Float) {
        val balloon = shopBalloon
        if (balloon == null) {
            shopTimer -= dt
            if (shopTimer <= 0f && phase == Phase.BASES) {
                shopBalloon = ShopBalloon(world.wrap(shipX + direction * aspect * 0.75f), 0.3f + Random.nextFloat() * 0.3f)
            }
            return
        }
        balloon.age += dt
        if (balloon.age > SHOP_BALLOON_SEC) {
            shopBalloon = null
            shopTimer = SHOP_INTERVAL_SEC
        }
    }

    private fun aimedBullet(fromX: Float, fromY: Float, speed: Float, angleOffset: Float = 0f) {
        val angle = atan2(shipY - fromY, world.delta(fromX, shipX)) + angleOffset
        bullets.add(Bullet(fromX, fromY, cos(angle) * speed, sin(angle) * speed))
    }

    private fun moveEverything(dt: Float) {
        for (e in enemies) {
            e.age += dt
            when (e.kind) {
                Kind.PUFF -> {
                    // Drift slowly towards the ship's height while bobbing.
                    e.x = world.wrap(e.x + sign(world.delta(e.x, shipX)) * 0.05f * pace * dt)
                    e.y += ((shipY - e.y) * 0.25f * pace + 0.15f * cos(e.age * 2f + e.phase)) * dt
                }
                Kind.BEE -> {
                    e.x = world.wrap(e.x + e.vx * dt)
                    e.y = e.baseY + 0.1f * sin(e.age * 4f + e.phase)
                }
            }
        }
        enemies.removeAll { it.age > ENEMY_LIFETIME_SEC || abs(world.delta(shipX, it.x)) > aspect * 1.5f }

        for (s in shots) {
            s.x = world.wrap(s.x + s.vx * dt)
            s.y += s.vy * dt
            s.traveled += abs(s.vx * dt)
        }
        shots.removeAll { it.traveled > aspect || it.y < 0f || it.y > GROUND_Y }

        for (b in bombs) {
            b.vy += BOMB_GRAVITY * dt
            b.x = world.wrap(b.x + b.vx * dt)
            b.y += b.vy * dt
        }
        for (b in bombs.filter { it.y >= GROUND_Y }) explodeBomb(b)
        bombs.removeAll { it.y >= GROUND_Y }

        for (b in bullets) {
            b.x = world.wrap(b.x + b.vx * dt)
            b.y += b.vy * dt
        }
        bullets.removeAll { it.y < -0.05f || it.y > GROUND_Y || !onScreen(it.x, 0.3f) }

        for (c in coinsOnField) {
            c.age += dt
            c.y = (c.y + COIN_FALL_SPEED * dt).coerceAtMost(GROUND_Y - 0.03f)
        }
        coinsOnField.removeAll { it.age > COIN_LIFETIME_SEC }
        for (r in reverseBalloons) r.cooldown -= dt
    }

    private fun updateBoss(dt: Float) {
        val b = boss ?: return
        b.age += dt
        b.flash -= dt
        // The boss stays ahead of the ship on screen, crossing over when the ship turns around.
        val target = if (direction > 0) aspect - 0.35f else 0.35f
        if (b.screenX.isNaN()) b.screenX = if (direction > 0) aspect + 0.4f else -0.4f
        b.screenX += (target - b.screenX) * (1f - exp(-dt * 0.8f))
        b.y = 0.42f + 0.2f * sin(b.age * 0.7f)
        b.fireTimer -= dt
        if (b.fireTimer <= 0f) {
            b.fireTimer = BOSS_FIRE_SEC / pace
            for (offset in floatArrayOf(-0.4f, -0.2f, 0f, 0.2f, 0.4f)) aimedBullet(bossX(b), heartY(b), BULLET_SPEED * pace, offset)
        }
        b.spawnTimer -= dt
        if (b.spawnTimer <= 0f) {
            b.spawnTimer = BOSS_SPAWN_SEC / pace
            repeat(2) { enemies.add(Enemy(Kind.PUFF, bossX(b), b.y + (it - 0.5f) * 0.2f, 0f, b.y, it * 3f)) }
        }
    }

    private fun bossX(b: BossState) = world.wrap(shipX + (b.screenX - shipScreenX))

    private fun heartY(b: BossState) = b.y + BOSS_HEART_OFFSET_Y

    private fun resolveHits() {
        val boss = boss
        val shotIterator = shots.iterator()
        while (shotIterator.hasNext()) {
            val s = shotIterator.next()
            if (boss != null) {
                val bx = bossX(boss)
                if (hypot(world.delta(s.x, bx), s.y - heartY(boss)) < BOSS_HEART_RADIUS + SHOT_RADIUS) {
                    shotIterator.remove()
                    damageBoss(boss, 1)
                    continue
                }
                if (inBossBody(boss, s.x, s.y, SHOT_RADIUS)) {
                    shotIterator.remove()
                    continue
                }
            }
            val base = bases.firstOrNull { it !in s.struck && dist(it.x, it.y, s.x, s.y) < BASE_RADIUS + SHOT_RADIUS }
            if (base != null) {
                if (s.laser) s.struck.add(base) else shotIterator.remove()
                damageBase(base, 1)
                continue
            }
            val enemy = enemies.firstOrNull { it.hp > 0 && it !in s.struck && dist(it.x, it.y, s.x, s.y) < it.kind.radius + SHOT_RADIUS }
            if (enemy != null) {
                if (s.laser) s.struck.add(enemy) else shotIterator.remove()
                damageEnemy(enemy, 1)
            }
        }
        bases.removeAll { it.hp <= 0 }
        enemies.removeAll { it.hp <= 0 }

        // Bombs burst on whatever they touch.
        val touched = bombs.filter { b ->
            bases.any { dist(it.x, it.y, b.x, b.y) < BASE_RADIUS } ||
                enemies.any { dist(it.x, it.y, b.x, b.y) < it.kind.radius + 0.01f } ||
                (boss != null && inBossBody(boss, b.x, b.y, 0.01f))
        }
        for (b in touched) explodeBomb(b)
        bombs.removeAll(touched)

        val coinIterator = coinsOnField.iterator()
        while (coinIterator.hasNext()) {
            val c = coinIterator.next()
            if (dist(c.x, c.y, shipX, shipY) < COIN_PICKUP_RADIUS) {
                coinIterator.remove()
                coins += c.value
                sfx.play(Sfx.CAPSULE, 0.6f)
            }
        }

        for (r in reverseBalloons) {
            if (r.cooldown <= 0f && dist(r.x, r.y, shipX, shipY) < REVERSE_RADIUS) {
                r.cooldown = REVERSE_COOLDOWN_SEC
                direction = -direction
                sfx.play(Sfx.POINT)
            }
        }

        val balloon = shopBalloon
        if (balloon != null && dist(balloon.x, balloon.y, shipX, shipY) < SHOP_RADIUS) {
            state = State.SHOP
            shopSec = 0f
            shopCursor = 0
            shopCursorTimer = 0f
            shopMessage = ""
            sfx.play(Sfx.CAPSULE)
            return
        }

        if (invulnerable > 0f) return
        val hit = bullets.any { dist(it.x, it.y, shipX, shipY) < BULLET_RADIUS + SHIP_RADIUS } ||
            enemies.any { dist(it.x, it.y, shipX, shipY) < it.kind.radius + SHIP_RADIUS } ||
            bases.any { dist(it.x, it.y, shipX, shipY) < BASE_RADIUS + SHIP_RADIUS } ||
            (boss != null && inBossBody(boss, shipX, shipY, SHIP_RADIUS))
        if (hit) loseLife()
    }

    private fun inBossBody(b: BossState, x: Float, y: Float, r: Float): Boolean {
        val dx = world.delta(bossX(b), x) / (BOSS_HALF_WIDTH + r)
        val dy = (y - b.y) / (BOSS_HALF_HEIGHT + r)
        return dx * dx + dy * dy < 1f
    }

    private fun explodeBomb(b: Bomb) {
        val heavy = shop.level(ShopItem.HEAVY_BOMB) > 0
        val radius = if (heavy) HEAVY_BOMB_RADIUS else BOMB_RADIUS
        val damage = if (heavy) HEAVY_BOMB_DAMAGE else BOMB_DAMAGE
        pops.add(Pop(b.x, b.y.coerceAtMost(GROUND_Y - 0.02f), if (heavy) 2.2f else 1.4f))
        sfx.play(Sfx.EXPLODE)
        for (base in bases) if (dist(base.x, base.y, b.x, b.y) < radius + BASE_RADIUS) damageBase(base, damage)
        for (e in enemies) if (dist(e.x, e.y, b.x, b.y) < radius + e.kind.radius) damageEnemy(e, damage)
        boss?.let { if (hypot(world.delta(b.x, bossX(it)), b.y - heartY(it)) < radius + BOSS_HEART_RADIUS) damageBoss(it, damage) }
        bases.removeAll { it.hp <= 0 }
        enemies.removeAll { it.hp <= 0 }
    }

    private fun damageEnemy(e: Enemy, damage: Int) {
        if (e.hp <= 0) return
        e.hp -= damage
        if (e.hp > 0) return
        score += e.kind.points
        pops.add(Pop(e.x, e.y, 1f))
        coinsOnField.add(Coin(e.x, e.y, e.kind.coins))
        sfx.play(Sfx.EXPLODE, 0.6f)
    }

    private fun damageBase(base: Base, damage: Int) {
        if (base.hp <= 0) return
        base.hp -= damage
        base.flash = 0.08f
        if (base.hp > 0) return
        score += BASE_POINTS
        pops.add(Pop(base.x, base.y, 2.5f))
        repeat(BASE_COINS) { i ->
            coinsOnField.add(Coin(world.wrap(base.x + (i - BASE_COINS / 2f) * 0.04f), base.y + Random.nextFloat() * 0.05f, BASE_COIN_VALUE))
        }
        sfx.play(Sfx.BIG_EXPLODE, 0.8f)
    }

    private fun damageBoss(b: BossState, damage: Int) {
        b.hp -= damage
        b.flash = 0.06f
        if (b.hp > 0) return
        val bx = bossX(b)
        score += BOSS_POINTS * stage
        repeat(8) { pops.add(Pop(world.wrap(bx + (Random.nextFloat() - 0.5f) * 0.35f), b.y + (Random.nextFloat() - 0.5f) * 0.25f, 2.5f, -it * 0.1f)) }
        repeat(10) { i -> coinsOnField.add(Coin(world.wrap(bx + (i - 5) * 0.04f), b.y, BASE_COIN_VALUE)) }
        bullets.clear()
        enemies.clear()
        sfx.play(Sfx.BIG_EXPLODE)
        boss = null
    }

    private fun loseLife() {
        pops.add(Pop(shipX, shipY, 2f))
        sfx.play(Sfx.HIT)
        bullets.clear()
        shop.loseWeapons()
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

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float) = hypot(world.delta(ax, bx), ay - by)

    private fun sign(v: Float) = if (v < 0f) -1f else 1f

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        val u = height.toFloat()
        aspect = width / u
        if (shipScreenX.isNaN()) shipScreenX = anchorFor(direction)

        // With the camera backdrop, GameView has already drawn the camera image underneath.
        if (!options.cameraBackdrop) drawScenery(canvas, width, u)
        drawGround(canvas, width, u)
        for (p in plants) if (onScreen(p.x, 0.3f)) drawStanding(canvas, sprites.plants[p.variant], p.x, p.y, u, p.scale)

        val frame = (stateTime * 6f).toInt()
        for (r in reverseBalloons) {
            if (onScreen(r.x)) drawSprite(canvas, sprites.reverse, r.x, r.y, u, if (r.cooldown > 0f) blinkPaint else spritePaint)
        }
        shopBalloon?.let { if (onScreen(it.x)) drawSprite(canvas, sprites.shop, it.x, it.y, u) }
        shopBalloon?.let { if (onScreen(it.x)) hud.drawText(canvas, "SHOP", screenX(it.x) * u, (it.y - 0.01f) * u, 0.035f * u) }
        for (b in bases) {
            if (onScreen(b.x)) drawSprite(canvas, sprites.base[frame % 2], b.x, b.y, u, if (b.flash > 0f) blinkPaint else spritePaint)
        }
        boss?.let { drawBoss(canvas, it, u) }
        for (e in enemies) {
            if (!onScreen(e.x)) continue
            when (e.kind) {
                Kind.PUFF -> drawSprite(canvas, sprites.puff[frame % 2], e.x, e.y, u)
                // Bees face left in the art; mirror them when they fly right.
                Kind.BEE -> drawSprite(canvas, sprites.bee[frame % 2], e.x, e.y, u, flipX = e.vx > 0f)
            }
        }
        for (c in coinsOnField) {
            // Blink before disappearing.
            if (c.age > COIN_LIFETIME_SEC - 2f && frame % 2 == 0) continue
            if (onScreen(c.x)) drawSprite(canvas, sprites.coin[(c.age * 5f).toInt() % 2], c.x, c.y, u)
        }
        for (s in shots) {
            val sx = screenX(s.x) * u
            if (s.laser) {
                canvas.drawRect(sx - 0.06f * u, (s.y - 0.005f) * u, sx + 0.06f * u, (s.y + 0.005f) * u, laserPaint)
            } else {
                canvas.drawCircle(sx, s.y * u, 0.011f * u, shotPaint)
            }
        }
        for (b in bombs) drawSprite(canvas, sprites.bomb, b.x, b.y, u)
        for (b in bullets) {
            val bx = screenX(b.x) * u
            canvas.drawCircle(bx, b.y * u, BULLET_RADIUS * u, bulletPaint)
            canvas.drawCircle(bx, b.y * u, BULLET_RADIUS * 0.45f * u, bulletCorePaint)
        }
        if (state != State.GAME_OVER) {
            val visible = invulnerable <= 0f || (stateTime * 10f).toInt() % 2 == 0
            if (visible) drawSprite(canvas, sprites.ship[frame % 2], shipX, shipY, u, flipX = direction < 0)
        }
        for (p in pops) {
            if (p.age < 0f) continue
            val index = (p.age / POP_SEC * sprites.pop.size).toInt().coerceAtMost(sprites.pop.size - 1)
            drawSprite(canvas, sprites.pop[index], p.x, p.y, u, scale = p.scale)
        }

        drawHud(canvas, width, u)
    }

    private fun drawScenery(canvas: Canvas, width: Int, u: Float) {
        val p = palette
        if (skyKey != stage * 10_000 + width) {
            skyKey = stage * 10_000 + width
            skyPaint.shader = LinearGradient(0f, 0f, 0f, GROUND_Y * u, p.sky, p.horizon, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), GROUND_Y * u, skyPaint)
        // Two layers of fluffy clouds, the far ones drifting slower for depth.
        drawCloudLayer(canvas, farClouds, 0.15f, u)
        drawCloudLayer(canvas, nearClouds, 0.35f, u)
    }

    private fun drawCloudLayer(canvas: Canvas, clouds: List<Prop>, parallax: Float, u: Float) {
        val camera = (traveled - shipScreenX) * parallax
        for (c in clouds) {
            val bitmap = sprites.clouds[c.variant]
            val halfW = bitmap.width * PIXEL * c.scale / 2f
            // Wrap into the band, then repeat it across the screen.
            var x = ((c.x - camera) % CLOUD_SPAN + CLOUD_SPAN) % CLOUD_SPAN - halfW
            while (x - halfW < aspect) {
                drawSpriteAtScreen(canvas, bitmap, x, c.y, u, c.scale)
                x += CLOUD_SPAN
            }
        }
    }

    /** Grass with a bumpy top over striped soil, scrolling with the ship. */
    private fun drawGround(canvas: Canvas, width: Int, u: Float) {
        val p = palette
        val top = GROUND_Y * u
        groundPaint.color = p.soil
        canvas.drawRect(0f, top + 0.03f * u, width.toFloat(), u, groundPaint)
        val stripe = 0.04f * u
        val offset = ((traveled - shipScreenX) * u) % (stripe * 2)
        groundPaint.color = p.soilDark
        var x = -offset - stripe * 2
        while (x < width) {
            canvas.drawRect(x, top + 0.055f * u, x + stripe, u, groundPaint)
            x += stripe * 2
        }
        groundPaint.color = p.grass
        canvas.drawRect(0f, top, width.toFloat(), top + 0.035f * u, groundPaint)
        val bump = 0.03f * u
        val bumpOffset = ((traveled - shipScreenX) * u) % bump
        x = -bumpOffset - bump
        while (x < width + bump) {
            groundPaint.color = p.grass
            canvas.drawCircle(x, top + 0.004f * u, bump * 0.6f, groundPaint)
            groundPaint.color = p.grassLight
            canvas.drawCircle(x - bump * 0.15f, top - 0.002f * u, bump * 0.25f, groundPaint)
            x += bump
        }
    }

    private fun drawBoss(canvas: Canvas, b: BossState, u: Float) {
        val bx = bossX(b)
        val blink = (b.age % 3f) > 2.7f
        drawSprite(canvas, sprites.boss[if (blink) 1 else 0], bx, b.y, u, if (b.flash > 0f) blinkPaint else spritePaint, scale = BOSS_SCALE)
        drawSprite(canvas, sprites.heart[(b.age * 4f).toInt() % 2], bx, heartY(b), u)
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
        flipX: Boolean = false,
    ) {
        val cx = screenX(x) * u
        val halfW = bitmap.width * PIXEL * scale * u / 2f
        val halfH = bitmap.height * PIXEL * scale * u / 2f
        rect.set(cx - halfW, y * u - halfH, cx + halfW, y * u + halfH)
        if (flipX) {
            canvas.save()
            canvas.scale(-1f, 1f, cx, y * u)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, null, rect, paint)
        }
    }

    /** Draws [bitmap] standing on [y] (its bottom edge) at world x [x]. */
    private fun drawStanding(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, u: Float, scale: Float) {
        val h = bitmap.height * PIXEL * scale
        drawSprite(canvas, bitmap, x, y - h / 2f, u, scale = scale)
    }

    /** Draws [bitmap] centred at screen position ([sx], [y]) in world units, for parallax layers. */
    private fun drawSpriteAtScreen(canvas: Canvas, bitmap: Bitmap, sx: Float, y: Float, u: Float, scale: Float) {
        val halfW = bitmap.width * PIXEL * scale * u / 2f
        val halfH = bitmap.height * PIXEL * scale * u / 2f
        rect.set(sx * u - halfW, y * u - halfH, sx * u + halfW, y * u + halfH)
        canvas.drawBitmap(bitmap, null, rect, spritePaint)
    }

    private fun drawHud(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        hud.drawCornerStats(canvas, u, options.tapMode, detector.reps, best)
        if (!options.tapMode) hud.drawBodyGauge(canvas, u, detector)
        val action = if (options.tapMode) "タップ" else "腕立て"

        when (state) {
            State.READY -> {
                hud.drawText(canvas, "腕立てパステルゾーン", cx, 0.28f * u, 0.09f * u)
                val lines = listOfNotNull(
                    if (options.tapMode) "ドラッグで上下に動きます。進むのは自動です" else "体の高さで上下に動きます。進むのは自動です",
                    "赤い風船に触れると向きが反転！ ${action}でボム",
                    "お花の基地を全部こわすとボス登場",
                    if (options.tapMode) "タップでスタート" else "腕立て伏せを1回するとスタート！",
                    hud.startHint(detector, options.tapMode),
                )
                lines.forEachIndexed { i, line -> hud.drawText(canvas, line, cx, (0.42f + 0.075f * i) * u, 0.047f * u) }
            }
            State.PLAYING -> {
                hud.drawText(canvas, "SCORE %07d    TOP %07d".format(score, maxOf(score, best)), cx, 0.08f * u, 0.05f * u)
                drawCoinsAndLives(canvas, cx, 0.14f, u)
                drawRadar(canvas, cx, u)
                when (phase) {
                    Phase.BASES -> if (phaseTime < 2.5f) hud.drawText(canvas, "STAGE $stage", cx, 0.42f * u, 0.09f * u)
                    Phase.WARNING -> if ((stateTime * 4f).toInt() % 2 == 0) hud.drawText(canvas, "WARNING!!", cx, 0.42f * u, 0.11f * u)
                    Phase.CLEAR -> hud.drawText(canvas, "STAGE $stage CLEAR!", cx, 0.42f * u, 0.09f * u)
                    Phase.BOSS -> Unit
                }
                if (isPaused()) hud.drawPausedBanner(canvas, width, u)
            }
            State.SHOP -> drawShop(canvas, width, u)
            State.GAME_OVER -> {
                hud.drawText(canvas, "GAME OVER", cx, 0.35f * u, 0.11f * u)
                hud.drawText(canvas, "SCORE %07d   ベスト %07d".format(score, best), cx, 0.47f * u, 0.05f * u)
                val reps = if (options.tapMode) "" else "   腕立て ${detector.reps}回"
                hud.drawText(canvas, "STAGE $stage$reps", cx, 0.55f * u, 0.05f * u)
                if (stateTime > RESTART_DELAY_SEC) hud.drawText(canvas, "${action}でもう一度", cx, 0.66f * u, 0.055f * u)
            }
        }
    }

    /** Coin count with a coin icon, then one small ship per remaining life. */
    private fun drawCoinsAndLives(canvas: Canvas, cx: Float, y: Float, u: Float) {
        drawSpriteAtScreen(canvas, sprites.coin[0], cx / u - 0.25f, y - 0.012f, u, 1.4f)
        hud.drawText(canvas, "$coins", cx - 0.21f * u, y * u, 0.042f * u, Paint.Align.LEFT)
        for (i in 0 until lives.coerceAtMost(6)) drawSpriteAtScreen(canvas, sprites.ship[0], cx / u + 0.08f + i * 0.085f, y - 0.012f, u, 0.55f)
    }

    /** The whole loop as a bar: bases, reverse balloons, the shop and the ship with its heading. */
    private fun drawRadar(canvas: Canvas, cx: Float, u: Float) {
        val w = 0.9f * u
        val left = cx - w / 2
        val top = 0.925f * u
        val h = 0.045f * u
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, radarBackPaint)
        fun px(x: Float) = left + h / 2 + (x / WORLD_WIDTH) * (w - h)
        val midY = top + h / 2
        for (b in bases) canvas.drawCircle(px(b.x), midY, 0.009f * u, radarBasePaint)
        for (r in reverseBalloons) canvas.drawCircle(px(r.x), midY, 0.006f * u, radarReversePaint)
        shopBalloon?.let { canvas.drawCircle(px(it.x), midY, 0.008f * u, radarShopPaint) }
        val sx = px(shipX)
        val tip = 0.014f * u * direction
        path.reset()
        path.moveTo(sx + tip, midY)
        path.lineTo(sx - tip, midY - 0.012f * u)
        path.lineTo(sx - tip, midY + 0.012f * u)
        path.close()
        canvas.drawPath(path, radarShipPaint)
    }

    private fun drawShop(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        canvas.drawRect(0f, 0f, width.toFloat(), u, shadePaint)
        val w = 1.0f * u
        rect.set(cx - w / 2, 0.04f * u, cx + w / 2, 0.86f * u)
        canvas.drawRoundRect(rect, 0.03f * u, 0.03f * u, panelPaint)
        panelBorderPaint.strokeWidth = 0.008f * u
        canvas.drawRoundRect(rect, 0.03f * u, 0.03f * u, panelBorderPaint)
        hud.drawText(canvas, "SHOP", cx, 0.13f * u, 0.075f * u)
        drawSpriteAtScreen(canvas, sprites.coin[0], cx / u - 0.1f, 0.19f, u, 1.4f)
        hud.drawText(canvas, "$coins", cx - 0.06f * u, 0.205f * u, 0.045f * u, Paint.Align.LEFT)

        val rows = shopRows()
        val top = 0.24f * u
        val rowH = 0.58f * u / rows
        val rowW = 0.9f * u
        rowTextPaint.textSize = rowH * 0.48f
        for (i in 0 until rows) {
            val selected = i == shopCursor
            rect.set(cx - rowW / 2, top + i * rowH + 4f, cx + rowW / 2, top + (i + 1) * rowH - 4f)
            canvas.drawRoundRect(rect, 10f, 10f, if (selected) rowSelectedPaint else rowPaint)
            val textColor = if (selected) rgb(60, 30, 0) else Color.WHITE
            val iconSize = rowH * 0.7f
            val icon = sprites.shopIcons[i]
            val iconTop = rect.centerY() - iconSize / 2
            iconRect.set(rect.left + 16f, iconTop, rect.left + 16f + iconSize, iconTop + iconSize)
            canvas.drawBitmap(icon, null, iconRect, spritePaint)
            val textY = rect.centerY() + rowTextPaint.textSize * 0.35f
            val label = if (i < ShopItem.entries.size) {
                val item = ShopItem.entries[i]
                val price = if (shop.isSoldOut(item)) "SOLD OUT" else "${shop.price(item)}"
                rowTextPaint.color = if (!shop.isSoldOut(item) && shop.price(item) > coins && !selected) rgb(150, 160, 210) else textColor
                rowTextPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(price, rect.right - 20f, textY, rowTextPaint)
                val owned = shop.level(item)
                if (owned > 0 && item != ShopItem.EXTRA_LIFE) "${item.label}（${owned}）" else item.label
            } else {
                "ショップを出る"
            }
            rowTextPaint.color = textColor
            rowTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, iconRect.right + 16f, textY, rowTextPaint)
        }
        val how = if (options.tapMode) "ドラッグで選んでタップで決定" else "光っている項目で腕立てすると決定"
        hud.drawText(canvas, shopMessage.ifEmpty { how }, cx, 0.93f * u, 0.045f * u)
    }

    private companion object {
        fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

        /** One art pixel in screen heights. */
        const val PIXEL = 0.005f

        const val WORLD_WIDTH = 8f
        const val CLOUD_SPAN = 4f
        const val PLANT_SPACING = 0.45f
        const val GROUND_Y = 0.9f
        const val SHIP_RADIUS = 0.025f
        const val SHIP_TOP = 0.08f
        const val SHIP_BOTTOM = 0.84f
        const val SHIP_FOLLOW_RATE = 4f
        const val SHIP_SPEED = 0.35f
        const val SPEED_STEP = 0.1f
        const val START_LIVES = 3
        const val RESPAWN_INVULNERABLE_SEC = 2.5f

        const val SHOT_INTERVAL_SEC = 0.2f
        const val SHOT_SPEED = 1.4f
        const val SHOT_RADIUS = 0.012f
        const val LASER_INTERVAL_SEC = 0.25f
        const val LASER_SPEED = 2.2f
        const val BOMB_COOLDOWN_SEC = 0.4f
        const val BOMB_GRAVITY = 1.4f
        const val BOMB_RADIUS = 0.08f
        const val BOMB_DAMAGE = 3
        const val HEAVY_BOMB_RADIUS = 0.13f
        const val HEAVY_BOMB_DAMAGE = 6

        const val STAGE_PACE_STEP = 0.15f
        const val BASE_COUNT = 6
        const val BASE_HP = 12
        const val BASE_RADIUS = 0.085f
        const val BASE_POINTS = 1000
        const val BASE_COINS = 5
        const val BASE_COIN_VALUE = 200
        const val BASE_ACTIVE_RANGE = 1.3f
        const val BASE_SPAWN_SEC = 2.5f
        const val BASE_FIRE_SEC = 3f
        const val BEE_INTERVAL_SEC = 5f
        const val BEE_SPEED = 0.45f
        const val ENEMY_LIFETIME_SEC = 14f
        const val BULLET_SPEED = 0.4f
        const val BULLET_RADIUS = 0.011f

        const val REVERSE_COUNT = 3
        const val REVERSE_RADIUS = 0.07f
        const val REVERSE_COOLDOWN_SEC = 3f

        const val FIRST_SHOP_SEC = 20f
        const val SHOP_INTERVAL_SEC = 35f
        const val SHOP_BALLOON_SEC = 15f
        const val SHOP_RADIUS = 0.08f
        const val SHOP_TIMEOUT_SEC = 30f
        const val SHOP_CURSOR_SEC = 1.6f

        const val COIN_FALL_SPEED = 0.04f
        const val COIN_LIFETIME_SEC = 8f
        const val COIN_PICKUP_RADIUS = 0.06f

        const val WARNING_SEC = 2.5f
        const val CLEAR_SEC = 4f
        const val BOSS_HP = 30
        const val BOSS_SCALE = 1.2f
        const val BOSS_HALF_WIDTH = 32 * PIXEL * BOSS_SCALE
        const val BOSS_HALF_HEIGHT = 24 * PIXEL * BOSS_SCALE
        const val BOSS_HEART_OFFSET_Y = 0.07f
        const val BOSS_HEART_RADIUS = 0.04f
        const val BOSS_FIRE_SEC = 1.8f
        const val BOSS_SPAWN_SEC = 5f
        const val BOSS_POINTS = 10000

        const val POP_SEC = 0.45f
        const val RESTART_DELAY_SEC = 2f
        const val LOST_POSE_GRACE_SEC = 0.8f
    }
}
