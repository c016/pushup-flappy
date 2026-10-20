package com.c016.pushupflappy.motion

import com.c016.pushupflappy.pose.Pose
import com.c016.pushupflappy.pose.PoseLandmarks
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Counts push-ups from the height of the shoulders in the image.
 *
 * The phone is assumed to lie still on the floor, so the shoulders move down in the image when
 * the body lowers, whether the camera sees the player from the front or from the side. The
 * detector learns the top and bottom of the movement on the fly (no calibration step) and slowly
 * forgets them, so it adapts when the player moves closer or further away.
 */
class PushUpDetector {

    enum class Phase { UNKNOWN, UP, DOWN }

    var phase = Phase.UNKNOWN
        private set
    var reps = 0
        private set

    /** 0 at the bottom of the movement, 1 at the top; null until a full range has been seen. */
    var progress: Float? = null
        private set

    /** Whether the shoulders were found in the latest pose. */
    var bodyVisible = false
        private set

    private var smoothed = Float.NaN
    private var top = Float.NaN
    private var bottom = Float.NaN
    private var lastTimestampMs = -1L

    /** Feeds a new pose result. Returns true when it completes a push-up (the body reaches the top again). */
    fun update(pose: Pose?, timestampMs: Long): Boolean {
        val height = pose?.let(::shoulderHeight)
        bodyVisible = height != null
        if (height == null) {
            progress = null
            return false
        }

        val dt = if (lastTimestampMs < 0) 0f else (timestampMs - lastTimestampMs) / 1000f
        lastTimestampMs = timestampMs

        if (smoothed.isNaN()) {
            smoothed = height
            top = height
            bottom = height
        } else {
            smoothed += (height - smoothed) * SMOOTHING
        }

        // Track the extremes and let them relax towards the current value over time.
        val relax = 1f - exp(-dt / RANGE_MEMORY_SEC)
        top = max(top, smoothed)
        bottom = min(bottom, smoothed)
        top += (smoothed - top) * relax
        bottom += (smoothed - bottom) * relax

        val range = top - bottom
        if (range < MIN_RANGE) {
            progress = null
            return false
        }
        val p = ((smoothed - bottom) / range).coerceIn(0f, 1f)
        progress = p

        return when (phase) {
            Phase.UNKNOWN -> {
                if (p > UP_THRESHOLD) phase = Phase.UP else if (p < DOWN_THRESHOLD) phase = Phase.DOWN
                false
            }
            Phase.UP -> {
                if (p < DOWN_THRESHOLD) phase = Phase.DOWN
                false
            }
            Phase.DOWN -> {
                if (p > UP_THRESHOLD) {
                    phase = Phase.UP
                    reps++
                    true
                } else {
                    false
                }
            }
        }
    }

    fun resetCount() {
        reps = 0
    }

    /** Height of the shoulders above the bottom of the image (bigger = higher), or null if not visible. */
    private fun shoulderHeight(pose: Pose): Float? {
        val shoulders = listOf(pose[PoseLandmarks.LEFT_SHOULDER], pose[PoseLandmarks.RIGHT_SHOULDER])
            .filter { it.isVisible() }
        if (shoulders.isEmpty()) return null
        return 1f - shoulders.map { it.y }.average().toFloat()
    }

    private companion object {
        const val SMOOTHING = 0.5f
        const val RANGE_MEMORY_SEC = 10f
        /** Smallest movement (fraction of image height) that counts as a push-up. */
        const val MIN_RANGE = 0.06f
        const val UP_THRESHOLD = 0.7f
        const val DOWN_THRESHOLD = 0.35f
    }
}
