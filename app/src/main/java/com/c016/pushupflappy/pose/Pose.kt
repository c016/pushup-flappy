package com.c016.pushupflappy.pose

import android.graphics.Bitmap

/** A body keypoint. x and y are normalized to [0, 1] of the analyzed image; y grows downwards. */
data class Landmark(val x: Float, val y: Float, val z: Float, val visibility: Float) {
    fun isVisible(threshold: Float = 0.5f) = visibility >= threshold
}

/**
 * One detected body. Landmarks use the 33-point MediaPipe BlazePose topology (see [PoseLandmarks]),
 * so another estimator (e.g. RTMPose with COCO-17 keypoints) must map its output onto these indices.
 */
class Pose(val landmarks: List<Landmark>) {
    operator fun get(index: Int): Landmark = landmarks[index]
}

/** Camera image prepared for display: a small copy to stretch as a blurred fill, and the sharp frame. */
class CameraBackdrop(val blurred: Bitmap, val sharp: Bitmap)

/**
 * A camera frame (already rotated upright and mirrored for the front camera) and the pose found in
 * it. [backdrop] is filled in when the camera image is shown behind the game.
 */
class PoseFrame(
    val image: Bitmap,
    val pose: Pose?,
    val timestampMs: Long,
    val backdrop: CameraBackdrop? = null,
) {
    fun withBackdrop(backdrop: CameraBackdrop) = PoseFrame(image, pose, timestampMs, backdrop)
}

object PoseLandmarks {
    const val NOSE = 0
    const val LEFT_EYE = 2
    const val RIGHT_EYE = 5
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    /** Bones drawn in the skeleton overlay. */
    val CONNECTIONS = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
        LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP, LEFT_HIP to RIGHT_HIP,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE,
    )
}
