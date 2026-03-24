package com.demo.butler_voice_app.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class SarvamSTTManager(
    private val context: Context,
    private val apiKey: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var recordingJob: Job? = null

    private val sampleRate    = 16000
    private val bufferSize    = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    // ─── TUNED FOR SPEED ──────────────────────────────────────
    private val silenceThreshold   = 500   // slightly more sensitive
    private val silenceFramesNeeded = 20   // was 25 → now ~0.8 seconds silence to stop
    private val maxPreSpeechMs     = 4000L // was 4000 → give up faster if no speech
    private val maxTotalMs         = 9000L // was 8000 → max recording time

    fun startListening(onResult: (String) -> Unit, onError: () -> Unit) {

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SarvamSTT", "Mic permission not granted")
            onError()
            return
        }

        recordingJob?.cancel()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("SarvamSTT", "AudioRecord init failed")
                withContext(Dispatchers.Main) { onError() }
                return@launch
            }

            val output  = ByteArrayOutputStream()
            val buffer  = ShortArray(bufferSize)

            var silenceCount  = 0
            var hasSpoken     = false
            var localRecording = true
            val startTime     = System.currentTimeMillis()

            recorder.startRecording()
            Log.d("SarvamSTT", "Recording started")

            while (isActive && localRecording) {
                val elapsed = System.currentTimeMillis() - startTime

                // Give up if no speech detected within maxPreSpeechMs
                if (!hasSpoken && elapsed > maxPreSpeechMs) {
                    Log.d("SarvamSTT", "No speech detected")
                    localRecording = false
                    break
                }

                // Hard cap on total recording time
                if (elapsed > maxTotalMs) {
                    localRecording = false
                }

                val read = recorder.read(buffer, 0, buffer.size)

                if (read > 0) {
                    // Write PCM bytes
                    for (i in 0 until read) {
                        output.write(buffer[i].toInt() and 0xFF)
                        output.write((buffer[i].toInt() shr 8) and 0xFF)
                    }

                    val peak = buffer.take(read).maxOf { kotlin.math.abs(it.toInt()) }

                    if (peak >= silenceThreshold) {
                        hasSpoken     = true
                        silenceCount  = 0
                    } else if (hasSpoken) {
                        silenceCount++
                        // Stop quickly after speech ends
                        if (silenceCount >= silenceFramesNeeded) {
                            Log.d("SarvamSTT", "Silence detected, stopping early")
                            localRecording = false
                        }
                    }
                }
            }

            recorder.stop()
            recorder.release()

            if (!isActive) return@launch

            val audioBytes = output.toByteArray()

            if (audioBytes.size < 500) {
                Log.e("SarvamSTT", "No transcript found")
                withContext(Dispatchers.Main) { onResult("") }
                return@launch
            }

            Log.d("SarvamSTT", "Sending audio to Sarvam (${audioBytes.size} bytes)")
            sendToSarvam(buildWav(audioBytes), onResult, onError)
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
    }

    private fun buildWav(pcm: ByteArray): ByteArray {
        val wav = ByteArray(44 + pcm.size)

        fun i32(o: Int, v: Int) {
            wav[o]     = (v and 0xFF).toByte()
            wav[o + 1] = ((v shr 8) and 0xFF).toByte()
            wav[o + 2] = ((v shr 16) and 0xFF).toByte()
            wav[o + 3] = ((v shr 24) and 0xFF).toByte()
        }

        fun i16(o: Int, v: Int) {
            wav[o]     = (v and 0xFF).toByte()
            wav[o + 1] = ((v shr 8) and 0xFF).toByte()
        }

        "RIFF".toByteArray().copyInto(wav, 0)
        i32(4, 36 + pcm.size)
        "WAVE".toByteArray().copyInto(wav, 8)
        "fmt ".toByteArray().copyInto(wav, 12)
        i32(16, 16)
        i16(20, 1)
        i16(22, 1)
        i32(24, sampleRate)
        i32(28, sampleRate * 2)
        i16(32, 2)
        i16(34, 16)
        "data".toByteArray().copyInto(wav, 36)
        i32(40, pcm.size)
        pcm.copyInto(wav, 44)

        return wav
    }

    private fun sendToSarvam(
        wav: ByteArray,
        onResult: (String) -> Unit,
        onError: () -> Unit
    ) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "audio.wav",
                wav.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "saarika:v2.5")
            .addFormDataPart("language_code", "unknown") // auto-detect language
            .build()

        val request = Request.Builder()
            .url("https://api.sarvam.ai/speech-to-text")
            .addHeader("api-subscription-key", apiKey)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("SarvamSTT", "API failed: ${e.message}")
                onError()
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                Log.d("SarvamSTT", "Response: $raw")

                if (!response.isSuccessful) {
                    Log.e("SarvamSTT", "HTTP ${response.code}: $raw")
                    onResult("")
                    return
                }

                try {
                    val json       = JSONObject(raw)
                    val transcript = json.optString("transcript", "")

                    if (transcript.isNotEmpty()) {
                        Log.d("SarvamSTT", "Transcript: $transcript")
                        onResult(transcript)
                    } else {
                        Log.e("SarvamSTT", "No transcript found")
                        onResult("")
                    }
                } catch (e: Exception) {
                    Log.e("SarvamSTT", "Parse error: ${e.message}")
                    onResult("")
                }
            }
        })
    }
}
