package com.demo.butler_voice_app.voice

import com.demo.butler_voice_app.TTSManager
import android.content.Context

object VoiceInitializer {

    fun createTTS(context: Context): TTSManager {
        return TTSManager(
            context = context,
            elevenLabsApiKey = VoiceConfig.ELEVEN_API_KEY,
            voiceId = VoiceConfig.VOICE_ID
        )
    }

    fun createSTT(): ElevenLabsSTTManager {
        return ElevenLabsSTTManager(
            apiKey = VoiceConfig.ELEVEN_API_KEY
        )
    }
}