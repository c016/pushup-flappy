package com.c016.pushupflappy.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    @Test
    fun recognisesStartWords() {
        assertTrue(VoiceCommands.isStart("スタート"))
        assertTrue(VoiceCommands.isStart("ゲームスタート"))
        assertTrue(VoiceCommands.isStart("Start"))
        assertTrue(VoiceCommands.isStart("もう 一回"))
        assertTrue(VoiceCommands.isStart("はじめ"))
    }

    @Test
    fun ignoresOtherSpeech() {
        assertFalse(VoiceCommands.isStart("こんにちは"))
        assertFalse(VoiceCommands.isStart(""))
    }
}
