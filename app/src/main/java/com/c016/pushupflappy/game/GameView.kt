package com.c016.pushupflappy.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.c016.pushupflappy.pose.PoseFrame
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Runs a [MiniGame] on its own thread and draws it together with the camera overlay and, if
 * [GameOptions.cameraBackdrop] is set, the camera image behind the game.
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val game: MiniGame,
    private val options: GameOptions,
) : SurfaceView(context), SurfaceHolder.Callback {

    private val avatar = if (options.avatar) AvatarPainter(resources) else null
    private val overlay = PoseOverlay(avatar)
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val backdropRect = RectF()
    private val dimPaint = Paint().apply { color = Color.argb(DIM_ALPHA, 0, 0, 0) }
    private val pendingTaps = AtomicInteger()
    private val pendingStarts = AtomicInteger()

    /** Latest touch position as a fraction of the view height, or NaN when there is none to deliver. */
    @Volatile
    private var pendingDragY = Float.NaN

    @Volatile
    private var latestFrame: PoseFrame? = null
    private var lastDeliveredTimestamp = Long.MIN_VALUE

    @Volatile
    private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    /** Called from any thread when the player says "start". */
    fun requestStart() {
        pendingStarts.incrementAndGet()
    }

    /** Called from the pose estimator thread with every new result. */
    fun submitFrame(frame: PoseFrame) {
        latestFrame = frame
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "GameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
        thread = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingTaps.incrementAndGet()
                pendingDragY = event.y / height
            }
            MotionEvent.ACTION_MOVE -> pendingDragY = event.y / height
        }
        return true
    }

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = min((now - last) / 1_000_000_000f, MAX_STEP_SEC)
            last = now

            repeat(pendingTaps.getAndSet(0)) { game.onTap() }
            repeat(pendingStarts.getAndSet(0)) { game.onStartCommand() }
            val dragY = pendingDragY
            if (!dragY.isNaN()) {
                pendingDragY = Float.NaN
                game.onDrag(dragY)
            }
            val frame = latestFrame
            if (frame != null && frame.timestampMs != lastDeliveredTimestamp) {
                lastDeliveredTimestamp = frame.timestampMs
                game.onPose(frame.pose, frame.timestampMs)
            }
            game.update(dt)

            val canvas = lockCanvas() ?: continue
            try {
                if (options.cameraBackdrop) drawBackdrop(canvas, frame)
                game.draw(canvas, width, height)
                if (frame != null) overlay.draw(canvas, frame, width, height)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            // Hardware canvases are paced by vsync; software ones need a manual cap.
            val elapsedMs = (System.nanoTime() - now) / 1_000_000
            if (elapsedMs < FRAME_MS) Thread.sleep(FRAME_MS - elapsedMs)
        }
    }

    /**
     * The blurred image fills the screen; the sharp person layer is fitted to the screen height so
     * the whole body stays visible.
     */
    private fun drawBackdrop(canvas: Canvas, frame: PoseFrame?) {
        canvas.drawColor(Color.BLACK)
        val backdrop = frame?.backdrop ?: return
        val image = frame.image
        val w = width.toFloat()
        val h = height.toFloat()

        val coverScale = maxOf(w / image.width, h / image.height)
        val coverW = image.width * coverScale
        val coverH = image.height * coverScale
        backdropRect.set((w - coverW) / 2, (h - coverH) / 2, (w + coverW) / 2, (h + coverH) / 2)
        canvas.drawBitmap(backdrop.blurred, null, backdropRect, backdropPaint)

        val fitW = image.width * h / image.height
        backdropRect.set((w - fitW) / 2, 0f, (w + fitW) / 2, h)
        canvas.drawBitmap(backdrop.person, null, backdropRect, backdropPaint)
        frame.pose?.let { avatar?.draw(canvas, it, backdropRect) }
        if (options.dimBackdrop) canvas.drawRect(0f, 0f, w, h, dimPaint)
    }

    private fun lockCanvas(): Canvas? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()

    private companion object {
        const val MAX_STEP_SEC = 0.05f
        const val FRAME_MS = 15L
        const val DIM_ALPHA = 110
    }
}
