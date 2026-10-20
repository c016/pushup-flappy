package com.c016.pushupflappy.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.c016.pushupflappy.pose.CameraBackdrop
import com.c016.pushupflappy.pose.PoseFrame

/**
 * Turns a camera frame into a [CameraBackdrop]: the person cut out with the segmentation mask, and
 * a heavily downscaled copy that blurs the room when it is stretched back up with filtering.
 * Runs on the pose estimator's thread so the game loop only has to draw.
 */
class BackdropComposer {
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var maskPixels = IntArray(0)

    fun compose(frame: PoseFrame): PoseFrame {
        val image = frame.image
        // Two downscaling steps give a smoother blur than one.
        val tiny = Bitmap.createScaledBitmap(image, (image.width / BLUR_FACTOR).coerceAtLeast(1), (image.height / BLUR_FACTOR).coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, (image.width / 4).coerceAtLeast(1), (image.height / 4).coerceAtLeast(1), true)

        val mask = frame.mask ?: return frame.withBackdrop(CameraBackdrop(blurred, image))
        if (maskPixels.size != mask.values.size) maskPixels = IntArray(mask.values.size)
        for (i in mask.values.indices) {
            val alpha = (mask.values[i] * 255f).toInt().coerceIn(0, 255)
            maskPixels[i] = alpha shl 24
        }
        val maskBitmap = Bitmap.createBitmap(maskPixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)

        val person = image.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(person).drawBitmap(maskBitmap, null, Rect(0, 0, person.width, person.height), maskPaint)
        return frame.withBackdrop(CameraBackdrop(blurred, person))
    }

    private companion object {
        const val BLUR_FACTOR = 24
    }
}
