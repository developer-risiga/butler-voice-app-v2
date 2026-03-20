package com.demo.butler_voice_app

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null

    fun start() {
        if (porcupineManager != null) return
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath("Hey-Butler_en_android_v4_0_0.ppn")
                .setSensitivity(0.7f)
                .build(context, PorcupineManagerCallback {
                    Log.d("Porcupine", "Hey Butler detected!")
                    onWakeWordDetected()
                })
            porcupineManager?.start()
            Log.d("Porcupine", "Wake word engine started")
        } catch (e: PorcupineException) {
            Log.e("Porcupine", "Failed to start: ${e.message}")
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.e("Porcupine", "Error stopping: ${e.message}")
        } finally {
            porcupineManager = null
            Log.d("Porcupine", "Wake word engine stopped")
        }
    }
}
