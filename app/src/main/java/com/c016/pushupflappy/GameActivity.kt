package com.c016.pushupflappy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.c016.pushupflappy.camera.PoseCamera
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameCatalog
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.GameView
import com.c016.pushupflappy.pose.MediaPipePoseEstimator
import com.c016.pushupflappy.pose.PoseEstimator

/** Hosts one mini game full screen and feeds it camera pose results. */
class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private var estimator: PoseEstimator? = null
    private var camera: PoseCamera? = null

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startPoseTracking()
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val info = GameCatalog.find(intent.getStringExtra(EXTRA_GAME_ID)) ?: GameCatalog.games.first()
        val options = GameOptions(
            difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)
                ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() } ?: Difficulty.EASY,
            tapMode = intent.getBooleanExtra(EXTRA_TAP_MODE, false),
        )
        gameView = GameView(this, info.create(this, options))
        setContentView(gameView)

        if (options.tapMode) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startPoseTracking()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startPoseTracking() {
        val poseEstimator = try {
            MediaPipePoseEstimator(
                context = this,
                listener = gameView::submitFrame,
                onError = { e -> Log.e(TAG, "Pose estimation failed", e) },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not create pose estimator", e)
            Toast.makeText(this, getString(R.string.pose_init_failed, e.message), Toast.LENGTH_LONG).show()
            return
        }
        estimator = poseEstimator
        camera = PoseCamera(this, this, poseEstimator).also { it.start() }
    }

    override fun onDestroy() {
        // Stop feeding frames before closing the model.
        camera?.close()
        estimator?.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GameActivity"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_TAP_MODE = "tap_mode"
    }
}
