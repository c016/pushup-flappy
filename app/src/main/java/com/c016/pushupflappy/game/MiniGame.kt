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

    fun update(dtSec: Float)

    fun draw(canvas: Canvas, width: Int, height: Int)
}

enum class Difficulty { EASY, NORMAL, HARD }

data class GameOptions(val difficulty: Difficulty, val tapMode: Boolean)
