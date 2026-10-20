package com.c016.pushupflappy.game.flappy

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.c016.pushupflappy.R

/** Kenney "Tappy Plane" sprites (CC0, see licenses/kenney-tappy-plane-CC0.txt). */
class FlappySprites(private val resources: Resources) {
    private fun load(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id)

    val background = load(R.drawable.tappy_background)
    val ground = load(R.drawable.tappy_ground)
    val rockUp = load(R.drawable.tappy_rock_up)
    val rockDown = load(R.drawable.tappy_rock_down)

    /** Propeller animation frames, facing right. */
    val plane = listOf(
        load(R.drawable.tappy_plane_1),
        load(R.drawable.tappy_plane_2),
        load(R.drawable.tappy_plane_3),
    )
    val puffLarge = load(R.drawable.tappy_puff_large)
    val puffSmall = load(R.drawable.tappy_puff_small)

    val digits = listOf(
        R.drawable.tappy_number_0, R.drawable.tappy_number_1, R.drawable.tappy_number_2,
        R.drawable.tappy_number_3, R.drawable.tappy_number_4, R.drawable.tappy_number_5,
        R.drawable.tappy_number_6, R.drawable.tappy_number_7, R.drawable.tappy_number_8,
        R.drawable.tappy_number_9,
    ).map(::load)

    val textGetReady = load(R.drawable.tappy_text_get_ready)
    val textGameOver = load(R.drawable.tappy_text_game_over)
    val panel = load(R.drawable.tappy_panel)
    val medalBronze = load(R.drawable.tappy_medal_bronze)
    val medalSilver = load(R.drawable.tappy_medal_silver)
    val medalGold = load(R.drawable.tappy_medal_gold)
}
