package com.c016.pushupflappy

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.c016.pushupflappy.game.Difficulty
import com.c016.pushupflappy.game.GameCatalog

/** Game menu: lists every game in [GameCatalog] with shared difficulty and input options. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<LinearLayout>(R.id.game_list)
        val difficulty = findViewById<RadioGroup>(R.id.difficulty)
        val tapMode = findViewById<CheckBox>(R.id.tap_mode)
        val density = resources.displayMetrics.density

        for (game in GameCatalog.games) {
            val button = Button(this).apply {
                setText(game.title)
                textSize = 20f
                setOnClickListener {
                    val selected = when (difficulty.checkedRadioButtonId) {
                        R.id.difficulty_normal -> Difficulty.NORMAL
                        R.id.difficulty_hard -> Difficulty.HARD
                        else -> Difficulty.EASY
                    }
                    startActivity(
                        Intent(this@MainActivity, GameActivity::class.java)
                            .putExtra(GameActivity.EXTRA_GAME_ID, game.id)
                            .putExtra(GameActivity.EXTRA_DIFFICULTY, selected.name)
                            .putExtra(GameActivity.EXTRA_TAP_MODE, tapMode.isChecked)
                    )
                }
            }
            val description = TextView(this).apply {
                setText(game.description)
                alpha = 0.8f
                setPadding(0, 0, 0, (12 * density).toInt())
            }
            list.addView(button, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            list.addView(description)
        }
    }
}
