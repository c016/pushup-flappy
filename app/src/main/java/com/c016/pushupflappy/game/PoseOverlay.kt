package com.c016.pushupflappy.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.c016.pushupflappy.pose.PoseFrame
import com.c016.pushupflappy.pose.PoseLandmarks

/** Draws the camera image with the detected skeleton as a small picture-in-picture. */
class PoseOverlay(private val avatar: AvatarPainter?) {
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 120)
        strokeCap = Paint.Cap.ROUND
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 80, 80) }
    private val rect = RectF()

    fun draw(canvas: Canvas, frame: PoseFrame, width: Int, height: Int) {
        val image = frame.image
        val boxWidth = width * 0.24f
        val boxHeight = boxWidth * image.height / image.width
        val margin = height * 0.03f
        rect.set(width - boxWidth - margin, margin, width - margin, margin + boxHeight)

        canvas.drawBitmap(image, null, rect, imagePaint)
        framePaint.strokeWidth = height * 0.004f
        canvas.drawRect(rect, framePaint)

        val pose = frame.pose ?: return
        avatar?.draw(canvas, pose, rect)
        bonePaint.strokeWidth = height * 0.006f
        for ((a, b) in PoseLandmarks.CONNECTIONS) {
            val p = pose[a]
            val q = pose[b]
            if (!p.isVisible() || !q.isVisible()) continue
            canvas.drawLine(
                rect.left + p.x * rect.width(), rect.top + p.y * rect.height(),
                rect.left + q.x * rect.width(), rect.top + q.y * rect.height(),
                bonePaint,
            )
        }
        val radius = height * 0.006f
        for (index in listOf(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER)) {
            val p = pose[index]
            if (!p.isVisible()) continue
            canvas.drawCircle(rect.left + p.x * rect.width(), rect.top + p.y * rect.height(), radius * 1.6f, jointPaint)
        }
    }
}
