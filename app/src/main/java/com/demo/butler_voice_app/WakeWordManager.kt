package com.demo.butler_voice_app

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException
import java.io.File

class WakeWordManager(
    context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit
) {
    private val appContext = context.applicationContext
    private var porcupineManager: PorcupineManager? = null
    private var isRunning = false

    @Synchronized
    fun start() {
        if (isRunning) {
            Log.d("Porcupine", "⚠️ Already running")
            return
        }
        if (accessKey.isBlank()) {
            Log.e("Porcupine", "❌ AccessKey is missing!")
            return
        }
        try {
            // Always clean up before creating new instance
            safeDelete()

            val keywordPath = copyAssetToFile("Hey-Butler_en_android_v4_0_0.ppn")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath)
                .setSensitivity(0.7f)
                .build(appContext, PorcupineManagerCallback {
                    Log.d("Porcupine", "✅ Hey Butler detected!")
                    onWakeWordDetected()
                })

            porcupineManager!!.start()
            isRunning = true
            Log.d("Porcupine", "🚀 Wake word engine started")

        } catch (e: PorcupineException) {
            Log.e("Porcupine", "❌ Failed to start: ${e.message}")
            safeDelete()
        } catch (e: Exception) {
            Log.e("Porcupine", "❌ Unexpected error: ${e.message}")
            safeDelete()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning && porcupineManager == null) {
            Log.d("Porcupine", "⚠️ Already stopped")
            return
        }
        try {
            porcupineManager?.stop()
            Log.d("Porcupine", "🛑 Wake word engine stopped")
        } catch (e: Exception) {
            Log.e("Porcupine", "❌ Error stopping: ${e.message}")
        } finally {
            safeDelete()
            isRunning = false
        }
    }

    private fun safeDelete() {
        try {
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.w("Porcupine", "delete() warning: ${e.message}")
        } finally {
            porcupineManager = null
        }
    }

    private fun copyAssetToFile(assetName: String): String {
        val file = File(appContext.filesDir, assetName)
        if (!file.exists()) {
            appContext.assets.open(assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d("Porcupine", "📁 Copied: ${file.absolutePath}")
        } else {
            Log.d("Porcupine", "📁 Using cached file")
        }
        return file.absolutePath
    }
}
