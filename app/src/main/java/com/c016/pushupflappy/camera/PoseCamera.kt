package com.c016.pushupflappy.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.c016.pushupflappy.pose.PoseEstimator
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Streams upright camera frames into a [PoseEstimator]. Prefers the front camera. */
class PoseCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val estimator: PoseEstimator,
) : Closeable {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analysis: ImageAnalysis? = null
    private var mirror = true

    fun start() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val useFront = try {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            } catch (e: Exception) {
                false
            }
            mirror = useFront
            val selector =
                if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            val useCase = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            useCase.setAnalyzer(executor, ::analyze)
            analysis = useCase

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, useCase)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(proxy: ImageProxy) {
        proxy.use {
            if (estimator.isBusy) return
            val raw = it.toBitmap()
            val matrix = Matrix().apply {
                postRotate(it.imageInfo.rotationDegrees.toFloat())
                if (mirror) postScale(-1f, 1f)
            }
            val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            estimator.detectAsync(upright, SystemClock.uptimeMillis())
        }
    }

    override fun close() {
        analysis?.clearAnalyzer()
        executor.shutdown()
        executor.awaitTermination(500, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val TAG = "PoseCamera"
    }
}
