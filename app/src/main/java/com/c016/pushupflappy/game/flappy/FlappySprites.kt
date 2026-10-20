package com.c016.pushupflappy.game.flappy

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.c016.pushupflappy.R

/** Kenney "Platformer" sprites (CC0, see licenses/kenney-CC0.txt). All are 128x128 tiles. */
class FlappySprites(private val resources: Resources) {
    private fun load(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id)

    /** Faces left in the source image; drawn mirrored. */
    val bee = load(R.drawable.kenney_bee)
    val columnCap = load(R.drawable.kenney_stone)
    val columnBody = load(R.drawable.kenney_stone_center)
    val groundTop = load(R.drawable.kenney_grass_mid)

    /** Ground props; each sits on the bottom edge of its tile. */
    val props = listOf(
        load(R.drawable.kenney_bush),
        load(R.drawable.kenney_mushroom_red),
        load(R.drawable.kenney_grass_sprout),
        load(R.drawable.kenney_rock),
        load(R.drawable.kenney_plant_purple),
    )
}
