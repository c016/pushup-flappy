package com.c016.pushupflappy.game

import android.graphics.Canvas
import com.c016.pushupflappy.pose.Pose

/**
 * A pose-controlled mini game. All methods are called on the game loop thread, so
 * implementations need no synchronization.
 */
interface MiniGame {
    /** A new pose estimation result; [pose] is null when nobody is detected. */
    fun onPose(pose: Pose?, timestampMs: Long)

    /** The screen was tapped. */
    fun onTap()

    /** A finger is touching the screen at [yFraction] of its height (0 = top). */
    fun onDrag(yFraction: Float) = Unit

    fun update(dtSec: Float)

    fun draw(canvas: Canvas, width: Int, height: Int)

    /** Releases resources such as sounds when the game screen closes. */
    fun close() = Unit
}

enum class Difficulty { EASY, NORMAL, HARD }

/** How body movement steers the player in games that support more than one scheme. */
enum class ControlMode {
    /** Each push-up is one discrete action (e.g. a flap). */
    FLAP,

    /** The player's position follows the height of the body continuously. */
    LINKED,
}

/** Visual theme for games that offer more than one look. */
enum class VisualTheme {
    /** Pixel art in the style of the classic game. */
    CLASSIC,

    /** Kenney's CC0 sprite packs. */
    KENNEY,
}

data class GameOptions(
    val difficulty: Difficulty,
    val tapMode: Boolean,
    val controlMode: ControlMode,
    val theme: VisualTheme,
    /** Show the (blurred around the player) camera image instead of the game's background. */
    val cameraBackdrop: Boolean,
    /** Darken the camera backdrop so the game stands out against it. */
    val dimBackdrop: Boolean,
    /** Cover the player's face with an avatar wherever the camera image is shown. */
    val avatar: Boolean,
    val sound: Boolean,
)
