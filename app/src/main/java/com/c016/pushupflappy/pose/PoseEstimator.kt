package com.c016.pushupflappy.pose

import android.graphics.Bitmap
import java.io.Closeable

/**
 * Pose estimation backend. Implementations deliver a [PoseFrame] for every accepted image
 * to the listener they were created with, on a background thread.
 */
interface PoseEstimator : Closeable {
    /** True while a previous frame is still being processed; callers should skip frames then. */
    val isBusy: Boolean

    /** Submits an upright image. [timestampMs] must increase monotonically. */
    fun detectAsync(image: Bitmap, timestampMs: Long)
}
