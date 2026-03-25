package com.demo.butler_voice_app

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String          // default / English voice
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private var fallbackTts : TextToSpeech? = null
    private var fallbackReady = false
    private var mediaPlayer  : MediaPlayer? = null

    /**
     * Language-specific ElevenLabs voice IDs.
     * Replace the Hindi/Telugu/Tamil values with real voice IDs from your ElevenLabs account.
     * You can clone a voice in Hindi at: https://elevenlabs.io/voice-lab
     */
    private val voiceMap = mapOf(
        "en" to voiceId,                        // your existing English voice
        "hi" to "pqHfZKP75CvOlQylNhV4",         // ElevenLabs Hindi voice (Meera)
        "te" to voiceId,                        // replace with Telugu voice when available
        "ta" to voiceId,                        // replace with Tamil voice when available
        "pa" to voiceId                         // replace with Punjabi voice when available
    )

    fun init(onReady: () -> Unit) {
        fallbackTts = TextToSpeech(context) { status ->
            fallbackReady = status == TextToSpeech.SUCCESS
            if (fallbackReady) fallbackTts?.language = Locale.US
            onReady()
        }
    }

    fun speak(text: String, language: String = "en", onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        // Stop any existing playback
        mediaPlayer?.release()
        mediaPlayer = null

        val selectedVoiceId = voiceMap[language] ?: voiceId
        Log.d("TTS", "ElevenLabs [$language] voice=$selectedVoiceId → \"$text\"")

        val body = JSONObject().apply {
            put("text",     text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability",        0.5)
                put("similarity_boost", 0.8)
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$selectedVoiceId")
            .addHeader("xi-api-key", elevenLabsApiKey.trim())
            .addHeader("Accept", "audio/mpeg")
            .post(body)
            .build()

        callWithRetry(request, text, language, onDone, attempt = 1)
    }

    private fun callWithRetry(
        request : Request,
        text    : String,
        language: String,
        onDone  : (() -> Unit)?,
        attempt : Int
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TTS", "ElevenLabs failed (attempt $attempt): ${e.message}")
                if (attempt < 2) {
                    callWithRetry(request, text, language, onDone, attempt + 1)
                } else {
                    Log.w("TTS", "Falling back to system TTS")
                    speakFallback(text, language, onDone)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TTS", "ElevenLabs error ${response.code}")
                    if (attempt < 2) {
                        callWithRetry(request, text, language, onDone, attempt + 1)
                    } else {
                        speakFallback(text, language, onDone)
                    }
                    return
                }
                val bytes = response.body?.bytes()
                if (bytes == null) {
                    speakFallback(text, language, onDone)
                    return
                }
                playAudio(bytes, onDone)
            }
        })
    }

    private fun playAudio(bytes: ByteArray, onDone: (() -> Unit)?) {
        try {
            val file = File.createTempFile("tts_", ".mp3", context.cacheDir)
            file.writeBytes(bytes)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    file.delete()
                    onDone?.invoke()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("TTS", "Playback error: ${e.message}")
            onDone?.invoke()
        }
    }

    private fun speakFallback(text: String, language: String, onDone: (() -> Unit)?) {
        if (!fallbackReady) { onDone?.invoke(); return }

        fallbackTts?.language = when (language) {
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "ta" -> Locale("ta", "IN")
            "pa" -> Locale("pa", "IN")
            else -> Locale.US
        }

        val id = "FALLBACK_TTS_${System.currentTimeMillis()}"
        fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?)  {}
            override fun onDone(utteranceId: String?)   { if (utteranceId == id) onDone?.invoke() }
            override fun onError(utteranceId: String?)  { if (utteranceId == id) onDone?.invoke() }
        })
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        fallbackTts?.shutdown()
        fallbackTts = null
    }
}
