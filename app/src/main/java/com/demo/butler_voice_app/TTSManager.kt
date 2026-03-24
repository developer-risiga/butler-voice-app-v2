package com.demo.butler_voice_app

import android.content.Context
import android.media.AudioManager
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
    private val voiceId: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var fallbackTts: TextToSpeech? = null
    private var fallbackReady = false
    private var mediaPlayer: MediaPlayer? = null

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun init(onReady: () -> Unit) {
        fallbackTts = TextToSpeech(context) { status ->
            fallbackReady = status == TextToSpeech.SUCCESS
            if (fallbackReady) fallbackTts?.language = Locale.US
            onReady()
        }
    }

    fun speak(
        text: String,
        language: String = "en",
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            Log.w("TTS", "Empty text, skipping")
            onDone?.invoke()
            return
        }

        Log.d("TTS", "ElevenLabs [$language] → \"$text\"")

        // 🔥 Stop any existing playback
        mediaPlayer?.release()
        mediaPlayer = null

        val body = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            .addHeader("xi-api-key", elevenLabsApiKey.trim())
            .addHeader("Accept", "audio/mpeg")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("TTS", "ElevenLabs failed: ${e.message}")
                speakFallback(text, language, onDone)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TTS", "Error ${response.code}")
                    speakFallback(text, language, onDone)
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

    private fun speakFallback(
        text: String,
        language: String,
        onDone: (() -> Unit)?
    ) {
        if (!fallbackReady) {
            onDone?.invoke()
            return
        }

        fallbackTts?.language = getLocale(language)

        val id = "FALLBACK_TTS"

        fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) onDone?.invoke()
            }
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) onDone?.invoke()
            }
        })

        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    private fun getLocale(language: String): Locale {
        return when (language) {
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "ta" -> Locale("ta", "IN")
            else -> Locale.US
        }
    }

    fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        fallbackTts?.shutdown()
        fallbackTts = null
    }
}
