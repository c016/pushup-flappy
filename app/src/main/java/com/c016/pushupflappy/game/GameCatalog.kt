package com.c016.pushupflappy.game

import android.content.Context
import androidx.annotation.StringRes
import com.c016.pushupflappy.R
import com.c016.pushupflappy.game.flappy.FlappyGame

class GameInfo(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val create: (Context, GameOptions) -> MiniGame,
)

/** Every game shown in the menu. Add new pose games here. */
object GameCatalog {
    val games = listOf(
        GameInfo("flappy", R.string.game_flappy_title, R.string.game_flappy_desc) { context, options ->
            FlappyGame(context, options)
        },
    )

    fun find(id: String?): GameInfo? = games.firstOrNull { it.id == id }
}
