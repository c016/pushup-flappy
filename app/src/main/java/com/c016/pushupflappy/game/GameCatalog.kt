package com.c016.pushupflappy.game

import android.content.Context
import androidx.annotation.StringRes
import com.c016.pushupflappy.R
import com.c016.pushupflappy.game.flappy.FlappyGame
import com.c016.pushupflappy.game.shooter.ShooterGame
import com.c016.pushupflappy.game.zone.ZoneGame

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
        GameInfo("shooter", R.string.game_shooter_title, R.string.game_shooter_desc) { context, options ->
            ShooterGame(context, options)
        },
        GameInfo("zone", R.string.game_zone_title, R.string.game_zone_desc) { context, options ->
            ZoneGame(context, options)
        },
    )

    fun find(id: String?): GameInfo? = games.firstOrNull { it.id == id }
}
