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
import com.c016.pushupflappy.camera.BackdropComposer
import com.c016.pushupflappy.camera.PoseCamera
import com.c016.pushupflappy.game.ControlMode
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameCatalog
import com.c016.pushupflappy.game.GameOptions
import com.c016.pushupflappy.game.GameView
import com.c016.pushupflappy.game.MiniGame
import com.c016.pushupflappy.game.VisualTheme
import com.c016.pushupflappy.pose.MediaPipePoseEstimator
import com.c016.pushupflappy.pose.PoseEstimator
import com.c016.pushupflappy.voice.VoiceStarter

/** Hosts one mini game full screen and feeds it camera pose results. */
class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var options: GameOptions
    private lateinit var game: MiniGame
    private var estimator: PoseEstimator? = null
    private var camera: PoseCamera? = null
    private var voice: VoiceStarter? = null

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (Manifest.permission.CAMERA in results) {
                if (results[Manifest.permission.CAMERA] == true) {
                    startPoseTracking()
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
                    finish()
                    return@registerForActivityResult
                }
            }
            if (Manifest.permission.RECORD_AUDIO in results) {
                if (results[Manifest.permission.RECORD_AUDIO] == true) {
                    startVoice()
                } else {
                    Toast.makeText(this, R.string.voice_permission_denied, Toast.LENGTH_LONG).show()
                }
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
        val tapMode = intent.getBooleanExtra(EXTRA_TAP_MODE, false)
        val options = GameOptions(
            difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)
                ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() } ?: Difficulty.EASY,
            tapMode = tapMode,
            controlMode = intent.getStringExtra(EXTRA_CONTROL_MODE)
                ?.let { runCatching { ControlMode.valueOf(it) }.getOrNull() } ?: ControlMode.FLAP,
            theme = intent.getStringExtra(EXTRA_THEME)
                ?.let { runCatching { VisualTheme.valueOf(it) }.getOrNull() } ?: VisualTheme.CLASSIC,
            // Both need the camera, which tap mode does not use.
            cameraBackdrop = !tapMode && intent.getBooleanExtra(EXTRA_CAMERA_BACKDROP, false),
            dimBackdrop = intent.getBooleanExtra(EXTRA_DIM_BACKDROP, false),
            avatar = !tapMode && intent.getBooleanExtra(EXTRA_AVATAR, false),
            sound = intent.getBooleanExtra(EXTRA_SOUND, true),
            voiceStart = intent.getBooleanExtra(EXTRA_VOICE_START, true),
        )
        this.options = options
        game = info.create(this, options)
        gameView = GameView(this, game, options)
        setContentView(gameView)

        // Start what is already allowed and ask for the rest together.
        val missing = mutableListOf<String>()
        if (!options.tapMode) {
            if (isGranted(Manifest.permission.CAMERA)) startPoseTracking() else missing.add(Manifest.permission.CAMERA)
        }
        if (options.voiceStart) {
            if (isGranted(Manifest.permission.RECORD_AUDIO)) startVoice() else missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startVoice() {
        val starter = VoiceStarter(this, shouldListen = { game.acceptsStartCommand }, onStart = gameView::requestStart)
        if (starter.start()) {
            voice = starter
        } else {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        voice?.start()
    }

    override fun onStop() {
        voice?.stop()
        super.onStop()
    }

    private fun startPoseTracking() {
        val poseEstimator = try {
            val composer = if (options.cameraBackdrop) BackdropComposer() else null
            MediaPipePoseEstimator(
                context = this,
                segmentation = options.cameraBackdrop,
                listener = { frame -> gameView.submitFrame(composer?.compose(frame) ?: frame) },
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
        voice?.destroy()
        camera?.close()
        estimator?.close()
        // The game loop has stopped with the surface by now.
        game.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GameActivity"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_TAP_MODE = "tap_mode"
        const val EXTRA_CONTROL_MODE = "control_mode"
        const val EXTRA_THEME = "theme"
        const val EXTRA_CAMERA_BACKDROP = "camera_backdrop"
        const val EXTRA_AVATAR = "avatar"
        const val EXTRA_DIM_BACKDROP = "dim_backdrop"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_VOICE_START = "voice_start"
    }
}
