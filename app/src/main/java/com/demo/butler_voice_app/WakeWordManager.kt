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

    // isStopped = true means any in-flight callback should be ignored
    // isRunning  = true means the engine is active and consuming audio
    private val isRunning = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(true)

    @Synchronized
    fun start() {
        if (isRunning.get()) {
            Log.d("Porcupine", "⚠️ Already running")
            return
        }
        if (accessKey.isBlank()) {
            Log.e("Porcupine", "❌ AccessKey missing or blank")
            return
        }
        try {
            safeDelete()                // ensure clean state before building
            isStopped.set(false)

            val keywordPath = copyAssetToFile("Hey-Butler_en_android_v4_0_0.ppn")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath)
                .setSensitivity(0.7f)
                .build(appContext, PorcupineManagerCallback { _ ->
                    // Guard: only fire if we haven't been stopped yet
                    if (!isStopped.get() && isRunning.get()) {
                        Log.d("Porcupine", "✅ Hey Butler detected!")
                        // Set isStopped FIRST so a second detect can't re-fire
                        isStopped.set(true)
                        isRunning.set(false)
                        onWakeWordDetected()
                    }
                })

            porcupineManager!!.start()
            isRunning.set(true)
            Log.d("Porcupine", "🚀 Wake word engine started")

        } catch (e: PorcupineException) {
            Log.e("Porcupine", "❌ Start failed: ${e.message}")
            isRunning.set(false)
            isStopped.set(true)
            safeDelete()
        } catch (e: Exception) {
            Log.e("Porcupine", "❌ Unexpected error: ${e.message}")
            isRunning.set(false)
            isStopped.set(true)
            safeDelete()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.get() && porcupineManager == null) {
            Log.d("Porcupine", "⚠️ Already stopped")
            return
        }
        // Set isStopped FIRST — blocks any in-flight PorcupineManagerCallback
        isStopped.set(true)
        isRunning.set(false)

        try {
            porcupineManager?.stop()
            Log.d("Porcupine", "🛑 Wake word engine stopped")
        } catch (e: Exception) {
            Log.w("Porcupine", "stop() warning (safe to ignore): ${e.message}")
        } finally {
            safeDelete()
        }
    }

    // Call this if you want a hard restart (e.g. after returning from another Activity)
    @Synchronized
    fun restart() {
        stop()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start() }, 200)
    }

    fun isActive(): Boolean = isRunning.get()

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun safeDelete() {
        try {
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.w("Porcupine", "delete() warning (safe to ignore): ${e.message}")
        } finally {
            porcupineManager = null
        }
    }

    private fun copyAssetToFile(assetName: String): String {
        val file = File(appContext.filesDir, assetName)
        if (!file.exists()) {
            try {
                appContext.assets.open(assetName).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("Porcupine", "📁 Copied asset: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("Porcupine", "❌ Failed to copy asset $assetName: ${e.message}")
                throw e
            }
        } else {
            Log.d("Porcupine", "📁 Using cached file")
        }
        return file.absolutePath
    }
}