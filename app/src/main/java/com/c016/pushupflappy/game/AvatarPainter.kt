package com.c016.pushupflappy.game

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.c016.pushupflappy.R
import com.c016.pushupflappy.pose.Landmark
import com.c016.pushupflappy.pose.Pose
import com.c016.pushupflappy.pose.PoseLandmarks.LEFT_EAR
import com.c016.pushupflappy.pose.PoseLandmarks.LEFT_EYE
import com.c016.pushupflappy.pose.PoseLandmarks.LEFT_SHOULDER
import com.c016.pushupflappy.pose.PoseLandmarks.NOSE
import com.c016.pushupflappy.pose.PoseLandmarks.RIGHT_EAR
import com.c016.pushupflappy.pose.PoseLandmarks.RIGHT_EYE
import com.c016.pushupflappy.pose.PoseLandmarks.RIGHT_SHOULDER
import kotlin.math.atan2
import kotlin.math.hypot

/** Covers the player's face with a pixel-art avatar, following the head's position, size and tilt. */
class AvatarPainter(resources: Resources) {
    private val avatar = BitmapFactory.decodeResource(resources, R.drawable.avatar_chick)
    private val paint = Paint() // no filtering: keep the pixel art crisp
    private val rect = RectF()

    /** [imageRect] is where the camera image the pose was found in is drawn, in pixels. */
    fun draw(canvas: Canvas, pose: Pose, imageRect: RectF) {
        val nose = pose[NOSE]
        val leftEar = pose[LEFT_EAR]
        val rightEar = pose[RIGHT_EAR]
        val leftEye = pose[LEFT_EYE]
        val rightEye = pose[RIGHT_EYE]
        if (!nose.isVisible(VISIBILITY) && !leftEye.isVisible(VISIBILITY) && !rightEye.isVisible(VISIBILITY)) return

        fun x(l: Landmark) = imageRect.left + l.x * imageRect.width()
        fun y(l: Landmark) = imageRect.top + l.y * imageRect.height()
        fun dist(a: Landmark, b: Landmark) = hypot(x(a) - x(b), y(a) - y(b))

        // Estimate the head size from whatever is visible, most reliable first.
        val shoulders = listOf(pose[LEFT_SHOULDER], pose[RIGHT_SHOULDER])
        val size = when {
            leftEar.isVisible(VISIBILITY) && rightEar.isVisible(VISIBILITY) -> dist(leftEar, rightEar) * 1.9f
            leftEye.isVisible(VISIBILITY) && rightEye.isVisible(VISIBILITY) -> dist(leftEye, rightEye) * 4.2f
            leftEar.isVisible(VISIBILITY) -> dist(leftEar, nose) * 3.2f
            rightEar.isVisible(VISIBILITY) -> dist(rightEar, nose) * 3.2f
            shoulders.all { it.isVisible() } -> dist(shoulders[0], shoulders[1]) * 0.6f
            else -> 0f
        }.coerceAtLeast(imageRect.height() * MIN_SIZE)

        val cx: Float
        val cy: Float
        if (leftEye.isVisible(VISIBILITY) && rightEye.isVisible(VISIBILITY)) {
            cx = (x(leftEye) + x(rightEye)) / 2f
            cy = (y(leftEye) + y(rightEye)) / 2f + size * 0.08f
        } else {
            cx = x(nose)
            cy = y(nose)
        }
        val tilt = if (leftEye.isVisible(VISIBILITY) && rightEye.isVisible(VISIBILITY)) {
            // The image is mirrored, so the player's left eye is on the right.
            Math.toDegrees(atan2((y(leftEye) - y(rightEye)).toDouble(), (x(leftEye) - x(rightEye)).toDouble())).toFloat()
        } else {
            0f
        }

        canvas.save()
        canvas.rotate(tilt, cx, cy)
        rect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        canvas.drawBitmap(avatar, null, rect, paint)
        canvas.restore()
    }

    private companion object {
        const val VISIBILITY = 0.3f
        const val MIN_SIZE = 0.08f
    }
}
