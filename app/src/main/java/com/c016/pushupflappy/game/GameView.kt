package com.c016.pushupflappy.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.c016.pushupflappy.pose.PoseFrame
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/** Runs a [MiniGame] on its own thread and draws it together with the camera overlay. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val game: MiniGame) : SurfaceView(context), SurfaceHolder.Callback {

    private val overlay = PoseOverlay()
    private val pendingTaps = AtomicInteger()

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

    private fun lockCanvas(): Canvas? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()

    private companion object {
        const val MAX_STEP_SEC = 0.05f
        const val FRAME_MS = 15L
    }
}
