package com.c016.pushupflappy.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [PoseEstimator] backed by the MediaPipe Pose Landmarker (lite model) in live-stream mode.
 * With [segmentation] each frame also carries a [PersonMask].
 */
class MediaPipePoseEstimator(
    context: Context,
    private val segmentation: Boolean,
    private val listener: (PoseFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) : PoseEstimator {

    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var pendingImage: Bitmap? = null
    private var lastTimestampMs = -1L

    private val landmarker: PoseLandmarker = try {
        create(context, Delegate.GPU)
    } catch (e: Exception) {
        Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
        create(context, Delegate.CPU)
    }

    override val isBusy: Boolean get() = busy.get()

    override fun detectAsync(image: Bitmap, timestampMs: Long) {
        if (closed.get() || !busy.compareAndSet(false, true)) return
        // MediaPipe rejects timestamps that do not strictly increase.
        val ts = if (timestampMs <= lastTimestampMs) lastTimestampMs + 1 else timestampMs
        lastTimestampMs = ts
        pendingImage = image
        try {
            landmarker.detectAsync(BitmapImageBuilder(image).build(), ts)
        } catch (e: Exception) {
            busy.set(false)
            if (!closed.get()) onError(e)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) landmarker.close()
    }

    private fun create(context: Context, delegate: Delegate): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputSegmentationMasks(segmentation)
            .setResultListener(this::onResult)
            .setErrorListener { e ->
                busy.set(false)
                if (!closed.get()) onError(e)
            }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        val image = pendingImage
        busy.set(false)
        if (closed.get() || image == null) return
        val pose = result.landmarks().firstOrNull()?.let { points ->
            Pose(points.map { Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f)) })
        }
        val mask = if (segmentation) result.segmentationMasks().orElse(null)?.firstOrNull()?.let(::toMask) else null
        listener(PoseFrame(image, pose, result.timestampMs(), mask))
    }

    private fun toMask(image: MPImage): PersonMask {
        val values = FloatArray(image.width * image.height)
        ByteBufferExtractor.extract(image).order(ByteOrder.nativeOrder()).asFloatBuffer().get(values)
        return PersonMask(image.width, image.height, values)
    }

    companion object {
        private const val TAG = "MediaPipePose"
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}
