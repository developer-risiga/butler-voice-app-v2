package com.demo.butler_voice_app.utils

import android.content.Context
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Drop-in replacement for WakeWordManager (Porcupine).
 *
 * Listens ONLY for "hey butler" using Vosk grammar mode.
 * Grammar mode restricts the vocabulary to the given phrases only —
 * making it fast, lightweight, and accurate for a 2-word wake phrase.
 *
 * Zero expiry. Zero API key. 100% on-device. Apache 2.0 license.
 *
 * SETUP REQUIRED before this compiles:
 *   1. Add to app/build.gradle:
 *      implementation 'com.alphacephei:vosk-android:0.3.47'
 *   2. Download: alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip
 *   3. Unzip → rename folder to "vosk-model"
 *   4. Place at: app/src/main/assets/vosk-model/
 */
class VoskWakeWordEngine(
    private val context: Context,
    private val onDetected: () -> Unit
) {
    companion object {
        // Keep tag as "Porcupine" — existing logcat filters work unchanged
        private const val TAG = "Porcupine"

        private const val ASSETS_MODEL_NAME  = "vosk-model"  // folder in assets/
        private const val INTERNAL_MODEL_DIR = "vosk-model"  // folder in internal storage
        private const val SAMPLE_RATE        = 16000.0f

        // Grammar restricts recognition to ONLY these words.
        // "[unk]" catches everything else — prevents false positives.
        // Variants catch Indian English mispronunciation of "butler"
        private const val GRAMMAR = """["hey butler", "hey butlar", "hey butter", "[unk]"]"""
    }

    private var model: Model?          = null
    private var speechService: SpeechService? = null

    @Volatile private var isRunning  = false
    @Volatile private var modelReady = false

    // ── Init ──────────────────────────────────────────────────────────────
    // Call once in onCreate() BEFORE ttsManager.init().
    // First launch: copies 36MB model from assets → internal storage (~5-10s)
    // Subsequent launches: loads from internal storage (~200ms)

    fun init(onReady: () -> Unit, onError: (IOException) -> Unit) {
        Log.d(TAG, "📁 Loading Vosk model...")
        StorageService.unpack(
            context,
            ASSETS_MODEL_NAME,
            INTERNAL_MODEL_DIR,
            { m: Model ->
                model      = m
                modelReady = true
                Log.d(TAG, "📁 Using cached file")
                onReady()
            },
            { e: IOException ->
                Log.e(TAG, "❌ Start failed: ${e.message}")
                onError(e)
            }
        )
    }

    // ── Start ─────────────────────────────────────────────────────────────
    // Begins listening for "hey butler".
    // Safe to call multiple times — no-ops if already running.

    fun start() {
        if (isRunning) {
            Log.d(TAG, "⚠️ Already stopped")   // matches Porcupine log pattern
            return
        }
        val m = model
        if (m == null || !modelReady) {
            Log.e(TAG, "❌ Start failed: model not loaded yet")
            return
        }
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE, GRAMMAR)
            speechService  = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(recognitionListener)
            isRunning = true
            Log.d(TAG, "🚀 Wake word engine started")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Start failed: ${e.message}")
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────
    // Frees the microphone. Call before starting Sarvam STT.

    fun stop() {
        if (!isRunning) {
            Log.d(TAG, "⚠️ Already stopped")
            return
        }
        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        } finally {
            speechService = null
            isRunning     = false
            Log.d(TAG, "🛑 Wake word engine stopped")
        }
    }

    // ── Destroy ───────────────────────────────────────────────────────────
    // Release all resources. Call from onDestroy().

    fun destroy() {
        stop()
        try {
            model?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Model close error: ${e.message}")
        } finally {
            model      = null
            modelReady = false
        }
    }

    // ── Recognition listener ──────────────────────────────────────────────

    private val recognitionListener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            // Fires while user is still speaking — catches wake word early
            if (!isRunning || hypothesis == null) return
            if (matchesWakeWord(hypothesis)) triggerDetection()
        }

        override fun onResult(hypothesis: String?) {
            // Final result after silence — catches what partial missed
            if (!isRunning || hypothesis == null) return
            if (matchesWakeWord(hypothesis)) triggerDetection()
        }

        override fun onFinalResult(hypothesis: String?) {
            // Not needed for wake word detection
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Recognition error: ${exception?.message}")
            // Auto-restart so we keep listening after errors
            if (isRunning) {
                speechService?.stop()
                speechService = null
                isRunning     = false
                start()
            }
        }

        override fun onTimeout() {
            // Auto-restart on timeout
            if (isRunning) {
                speechService?.stop()
                speechService = null
                isRunning     = false
                start()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun matchesWakeWord(hypothesis: String): Boolean {
        return try {
            // Vosk returns JSON strings:
            //   partial → {"partial": "hey butler"}
            //   final   → {"text": "hey butler"}
            val json = JSONObject(hypothesis)
            val text = json.optString("partial", "")
                .ifEmpty { json.optString("text", "") }
                .lowercase()
                .trim()

            text.contains("hey butler") ||
                    text.contains("hey butlar") ||
                    text.contains("hey butter")
        } catch (e: JSONException) {
            false
        }
    }

    private fun triggerDetection() {
        Log.d(TAG, "✅ Hey Butler detected!")
        stop()        // free mic BEFORE calling onDetected — Sarvam needs it
        onDetected()  // calls MainActivity.onWakeWordDetected()
    }
}