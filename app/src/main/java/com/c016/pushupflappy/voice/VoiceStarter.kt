package com.c016.pushupflappy.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Listens for a spoken "start" while [shouldListen] says the game is waiting for one, and calls
 * [onStart] when heard. Android's recognizer stops after each utterance, so listening is restarted
 * from a periodic check. Must be used from the main thread.
 */
class VoiceStarter(
    private val context: Context,
    private val shouldListen: () -> Boolean,
    private val onStart: () -> Unit,
) : RecognitionListener {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var running = false

    private val check = object : Runnable {
        override fun run() {
            if (!running) return
            val want = shouldListen()
            if (want && !listening) begin()
            if (!want && listening) {
                recognizer?.cancel()
                listening = false
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    /** Returns false when the device has no speech recognizer. */
    fun start(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return false
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
        }
        running = true
        handler.removeCallbacks(check) // start() may be called again from onStart
        handler.post(check)
        return true
    }

    fun stop() {
        running = false
        handler.removeCallbacks(check)
        recognizer?.cancel()
        listening = false
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun begin() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        try {
            recognizer?.startListening(intent)
            listening = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start listening", e)
        }
    }

    private fun handle(results: Bundle?) {
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (texts.any(VoiceCommands::isStart)) {
            recognizer?.cancel()
            listening = false
            onStart()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = handle(partialResults)

    override fun onResults(results: Bundle?) {
        listening = false
        handle(results)
    }

    override fun onError(error: Int) {
        // No match and timeouts are normal; the periodic check starts listening again.
        listening = false
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) stop()
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private companion object {
        const val TAG = "VoiceStarter"
        const val CHECK_INTERVAL_MS = 500L
    }
}
