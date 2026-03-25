package com.demo.butler_voice_app

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordManager(
    context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit
) {
    private val appContext = context.applicationContext
    private var porcupineManager: PorcupineManager? = null
    private val isRunning = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(true)

    @Synchronized
    fun start() {
        if (isRunning.get()) {
            Log.d("Porcupine", "⚠️ Already running")
            return
        }
        if (accessKey.isBlank()) {
            Log.e("Porcupine", "❌ AccessKey missing")
            return
        }
        try {
            safeDelete()
            isStopped.set(false)

            val keywordPath = copyAssetToFile("Hey-Butler_en_android_v4_0_0.ppn")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath)
                .setSensitivity(0.7f)
                .build(appContext, PorcupineManagerCallback {
                    if (!isStopped.get()) {
                        Log.d("Porcupine", "✅ Hey Butler detected!")
                        onWakeWordDetected()
                    }
                })

            porcupineManager!!.start()
            isRunning.set(true)
            Log.d("Porcupine", "🚀 Wake word engine started")

        } catch (e: PorcupineException) {
            Log.e("Porcupine", "❌ Start failed: ${e.message}")
            safeDelete()
        } catch (e: Exception) {
            Log.e("Porcupine", "❌ Unexpected: ${e.message}")
            safeDelete()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.get() && porcupineManager == null) {
            Log.d("Porcupine", "⚠️ Already stopped")
            return
        }
        // Set flag FIRST — prevents any in-flight callbacks from firing
        isStopped.set(true)
        try {
            porcupineManager?.stop()
            Log.d("Porcupine", "🛑 Wake word engine stopped")
        } catch (e: Exception) {
            Log.w("Porcupine", "stop() warning: ${e.message}")
        } finally {
            safeDelete()
            isRunning.set(false)
        }
    }

    private fun safeDelete() {
        try { porcupineManager?.delete() }
        catch (e: Exception) { Log.w("Porcupine", "delete() warning: ${e.message}") }
        finally { porcupineManager = null }
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
