package com.c016.pushupflappy.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.c016.pushupflappy.motion.PushUpDetector

/** HUD pieces shared by the pose games. Sizes and positions are in pixels unless noted. */
class PoseHud {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 40, 40)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        isFakeBoldText = true
    }
    private val shadePaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val gaugeBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
    private val gaugePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** White text with a dark outline, readable over any background. */
    fun drawText(canvas: Canvas, text: String, x: Float, y: Float, size: Float, align: Paint.Align = Paint.Align.CENTER) {
        textPaint.textAlign = align
        textStrokePaint.textAlign = align
        textPaint.textSize = size
        textStrokePaint.textSize = size
        textStrokePaint.strokeWidth = size * 0.15f
        canvas.drawText(text, x, y, textStrokePaint)
        canvas.drawText(text, x, y, textPaint)
    }

    /** Push-up count (or the tap mode label) and best score in the top-left corner; [u] is the screen height. */
    fun drawCornerStats(canvas: Canvas, u: Float, tapMode: Boolean, reps: Int, best: Int) {
        val label = if (tapMode) "タップ操作モード" else "腕立て ${reps}回"
        drawText(canvas, label, 0.04f * u, 0.09f * u, 0.055f * u, Paint.Align.LEFT)
        drawText(canvas, "ベスト $best", 0.04f * u, 0.16f * u, 0.045f * u, Paint.Align.LEFT)
    }

    /** Vertical bar showing how far down the body currently is, so players can see detection working. */
    fun drawBodyGauge(canvas: Canvas, u: Float, detector: PushUpDetector) {
        val left = 0.04f * u
        val top = 0.24f * u
        val w = 0.035f * u
        val h = 0.4f * u
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, w / 2, w / 2, gaugeBackPaint)
        val progress = detector.progress ?: return
        gaugePaint.color =
            if (detector.phase == PushUpDetector.Phase.DOWN) Color.rgb(255, 170, 40) else Color.rgb(80, 220, 120)
        rect.set(left, top + h * (1f - progress), left + w, top + h)
        canvas.drawRoundRect(rect, w / 2, w / 2, gaugePaint)
    }

    fun drawPausedBanner(canvas: Canvas, width: Int, u: Float) {
        val cx = width / 2f
        canvas.drawRect(0f, 0.3f * u, width.toFloat(), 0.54f * u, shadePaint)
        drawText(canvas, "一時停止中", cx, 0.42f * u, 0.08f * u)
        drawText(canvas, "カメラに上半身が映るようにしてください", cx, 0.5f * u, 0.05f * u)
    }

    /** What the player should do next before the game can start, or null when all is well. */
    fun startHint(detector: PushUpDetector, tapMode: Boolean): String? = when {
        tapMode -> null
        !detector.bodyVisible -> "カメラに上半身が映るようにしてください"
        detector.progress == null -> "体が映りました。腕立て伏せをしてください"
        else -> null
    }
}
