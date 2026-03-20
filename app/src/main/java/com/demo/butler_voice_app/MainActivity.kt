package com.demo.butler_voice_app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.demo.butler_voice_app.ui.theme.ButlervoiceappTheme
import com.demo.butler_voice_app.api.ApiClient
import com.demo.butler_voice_app.ai.OrderParser
import com.demo.butler_voice_app.voice.SarvamSTTManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.demo.butler_voice_app.api.AuthManager



class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TTSManager
    private lateinit var sarvamSTT: SarvamSTTManager
    private lateinit var porcupine: WakeWordManager

    private val apiClient = ApiClient()
    private val parser = OrderParser()
    private val recordRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ButlervoiceappTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎤 Butler Voice Assistant\nSay 'Hey Butler'",
                        fontSize = 22.sp
                    )
                }
            }
        }


        lifecycleScope.launch {
            try {
                // Only login (signup should be done once manually)
                AuthManager.login("butler@risig.com", "Risiga")
                Log.d("Butler", "✅ LOGIN SUCCESS")

            } catch (e: Exception) {
                Log.e("Butler", "❌ LOGIN ERROR: ${e.message}")
            }
        }


        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true

        sarvamSTT = SarvamSTTManager(BuildConfig.SARVAM_API_KEY)
        porcupine = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) {
            runOnUiThread { onWakeWordDetected() }
        }
        ttsManager = TTSManager(this, BuildConfig.ELEVENLABS_API_KEY, BuildConfig.ELEVENLABS_VOICE_ID)
        ttsManager.init { checkMicPermission() }
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), recordRequestCode
            )
        } else {
            startWakeWordListening()
        }
    }

    private fun startWakeWordListening() {
        Log.d("Butler", "Waiting for wake word...")
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        Log.d("Butler", "Wake word detected!")
        porcupine.stop()
        ttsManager.speak("Yes?") { startCommandListening() }
    }

    private fun startCommandListening() {
        Log.d("Butler", "Listening for command...")
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    if (text.isBlank()) {
                        Log.d("Butler", "No command heard, returning to wake word")
                        ttsManager.speak("Sorry, I couldn't hear that") { startWakeWordListening() }
                    } else {
                        Log.d("Butler", "Command received: \"$text\"")
                        handleCommand(text.lowercase())
                    }
                }
            },
            onError = {
                runOnUiThread {
                    Log.d("Butler", "Command listen error, returning to wake word")
                    ttsManager.speak("Sorry, I couldn't hear that") { startWakeWordListening() }
                }
            }
        )
    }

    private fun handleCommand(spokenText: String) {
        val order = parser.parse(spokenText)

        if (order != null) {
            Log.d("Butler", "Order parsed: ${order.product} x${order.quantity}")
            ttsManager.speak("Searching for ${order.product}")

            lifecycleScope.launch {

                try {
                    val product = apiClient.searchProduct(order.product)

                    if (product != null) {

                        val items = listOf(
                            mapOf(
                                "product_id" to product.id,
                                "product_name" to product.name,
                                "quantity" to order.quantity,
                                "price" to product.price
                            )
                        )

                        val orderId = apiClient.createOrder(items)

                        ttsManager.speak("Order placed successfully. ID is $orderId") {
                            startWakeWordListening()
                        }

                    } else {
                        ttsManager.speak("Sorry, product not found") {
                            startWakeWordListening()
                        }
                    }

                } catch (e: Exception) {
                    Log.e("Butler", "Order error: ${e.message}")
                    ttsManager.speak("Something went wrong") {
                        startWakeWordListening()
                    }
                }
            }

        } else {
            ttsManager.speak("I didn't understand") {
                startWakeWordListening()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        porcupine.stop()
        sarvamSTT.stop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWordListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupine.stop()
        sarvamSTT.stop()
        ttsManager.shutdown()
    }
}
