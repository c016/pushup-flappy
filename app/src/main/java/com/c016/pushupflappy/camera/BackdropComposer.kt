package com.c016.pushupflappy.camera

import android.graphics.Bitmap
import com.c016.pushupflappy.pose.CameraBackdrop
import com.c016.pushupflappy.pose.PoseFrame

/**
 * Turns a camera frame into a [CameraBackdrop]: the frame itself, shown as is, plus a heavily
 * downscaled copy that turns into a soft blur when stretched back up with filtering, used to fill
 * the screen around it. Runs on the pose estimator's thread so the game loop only has to draw.
 */
class BackdropComposer {

    fun compose(frame: PoseFrame): PoseFrame {
        val image = frame.image
        // Two downscaling steps give a smoother blur than one.
        val tiny = Bitmap.createScaledBitmap(image, (image.width / BLUR_FACTOR).coerceAtLeast(1), (image.height / BLUR_FACTOR).coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, (image.width / 4).coerceAtLeast(1), (image.height / 4).coerceAtLeast(1), true)
        return frame.withBackdrop(CameraBackdrop(blurred, image))
    }

    private companion object {
        const val BLUR_FACTOR = 24
    }
}
