package com.c016.pushupflappy.motion

import com.c016.pushupflappy.pose.Landmark
import com.c016.pushupflappy.pose.Pose
import com.c016.pushupflappy.pose.PoseLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PushUpDetectorTest {

    private var time = 0L

    /** A pose whose shoulders are at image height [shoulderY] (0 = top, 1 = bottom). */
    private fun pose(shoulderY: Float, visibility: Float = 0.9f) = Pose(
        List(33) { index ->
            val isShoulder = index == PoseLandmarks.LEFT_SHOULDER || index == PoseLandmarks.RIGHT_SHOULDER
            Landmark(0.5f, if (isShoulder) shoulderY else 0.5f, 0f, if (isShoulder) visibility else 0.9f)
        }
    )

    /** Feeds [frames] samples moving linearly between two shoulder heights; returns completed reps. */
    private fun PushUpDetector.move(from: Float, to: Float, frames: Int = 10): Int =
        (1..frames).count { i ->
            time += 33
            update(pose(from + (to - from) * i / frames), time)
        }

    @Test
    fun countsEachFullPushUp() {
        val detector = PushUpDetector()
        detector.move(0.3f, 0.3f)
        var reps = 0
        repeat(3) {
            reps += detector.move(0.3f, 0.6f)
            reps += detector.move(0.6f, 0.3f)
        }
        assertEquals(3, reps)
        assertEquals(3, detector.reps)
    }

    @Test
    fun ignoresSmallJitter() {
        val detector = PushUpDetector()
        var reps = 0
        repeat(10) {
            reps += detector.move(0.30f, 0.33f, frames = 3)
            reps += detector.move(0.33f, 0.30f, frames = 3)
        }
        assertEquals(0, reps)
    }

    @Test
    fun halfwayDipDoesNotCount() {
        val detector = PushUpDetector()
        detector.move(0.3f, 0.6f)
        detector.move(0.6f, 0.3f)
        val before = detector.reps
        detector.move(0.3f, 0.4f)
        detector.move(0.4f, 0.3f)
        assertEquals(before, detector.reps)
    }

    @Test
    fun longMemoryKeepsProgressWhileHoldingStill() {
        val longMemory = PushUpDetector(rangeMemorySec = 60f)
        val shortMemory = PushUpDetector()
        for (detector in listOf(longMemory, shortMemory)) {
            detector.move(0.3f, 0.6f)
            detector.move(0.6f, 0.4f)
            detector.move(0.4f, 0.4f) // let the smoothing settle
        }
        val held = longMemory.progress!!
        // Hold the same position for 20 seconds.
        for (detector in listOf(longMemory, shortMemory)) detector.move(0.4f, 0.4f, frames = 600)
        assertEquals(held, longMemory.progress!!, 0.05f)
        assertNull(shortMemory.progress)
    }

    @Test
    fun missingShouldersAreReported() {
        val detector = PushUpDetector()
        time += 33
        assertFalse(detector.update(pose(0.3f, visibility = 0.1f), time))
        assertFalse(detector.bodyVisible)
        time += 33
        assertFalse(detector.update(null, time))
        assertFalse(detector.bodyVisible)
    }
}
