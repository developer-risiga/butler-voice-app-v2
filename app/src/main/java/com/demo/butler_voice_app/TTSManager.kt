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

class TTSManager(
    private val context: Context,
    private val elevenLabsApiKey: String,
    private val voiceId: String
) {
    private val client = OkHttpClient()
    private var fallbackTts: TextToSpeech? = null
    private var fallbackReady = false
    private var mediaPlayer: MediaPlayer? = null

    fun init(onReady: () -> Unit) {
        fallbackTts = TextToSpeech(
            context,
            { status ->
                fallbackReady = status == TextToSpeech.SUCCESS
                if (fallbackReady) fallbackTts?.language = Locale.US
                onReady()
            },
            "com.reecedunn.espeak"
        )
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        Log.d("TTS", "ElevenLabs → \"$text\"")

        val body = JSONObject().apply {
            put("text", text.replace(",", ". ").replace("  ", " "))
            put("model_id", "eleven_turbo_v2_5")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            .addHeader("xi-api-key", elevenLabsApiKey.trim())
            .addHeader("Accept", "audio/mpeg")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {F
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TTS", "ElevenLabs failed: ${e.message} → fallback to eSpeak")
                speakFallback(text, onDone)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "no body"
                    Log.e("TTS", "ElevenLabs error ${response.code}: $errorBody → fallback to eSpeak")
                    speakFallback(text, onDone)
                    return
                }
                val bytes = response.body?.bytes()
                if (bytes == null) {
                    Log.e("TTS", "ElevenLabs empty response → fallback to eSpeak")
                    speakFallback(text, onDone)
                    return
                }
                Log.d("TTS", "ElevenLabs success, playing audio")
                playAudio(text, bytes, onDone)
            }
        })
    }

    private fun playAudio(text: String, bytes: ByteArray, onDone: (() -> Unit)?) {
        try {
            val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir)
            tempFile.writeBytes(bytes)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    tempFile.delete()
                    onDone?.invoke()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("TTS", "Playback error: ${e.message} → fallback to eSpeak")
            speakFallback(text, onDone)
        }
    }

    private fun speakFallback(text: String, onDone: (() -> Unit)?) {
        Log.d("TTS", "eSpeak → \"$text\"")
        if (!fallbackReady) {
            onDone?.invoke()
            return
        }
        val id = "ESPEAK_TTS"
        if (onDone != null) {
            fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) onDone()
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) onDone()
                }
            })
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        } else {
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        fallbackTts?.shutdown()
        fallbackTts = null
    }
}
