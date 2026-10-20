package com.c016.pushupflappy.voice

/** Recognises spoken commands in speech-to-text results. */
object VoiceCommands {
    private val START_WORDS = listOf(
        "スタート", "すたーと", "start", "開始", "かいし", "はじめ", "始め", "もう一回", "もういっかい", "もう1回", "リトライ",
    )

    /** Whether [text] contains a request to start or restart the game. */
    fun isStart(text: String): Boolean {
        val normalized = text.lowercase().replace(" ", "").replace("　", "")
        return START_WORDS.any { normalized.contains(it) }
    }
}
