package com.demo.butler_voice_app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.demo.butler_voice_app.BuildConfig
import com.demo.butler_voice_app.api.*
import com.demo.butler_voice_app.ai.AIOrderParser
import com.demo.butler_voice_app.voice.SarvamSTTManager
import kotlinx.coroutines.launch

enum class AssistantState {
    IDLE, LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING
}

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TTSManager
    private lateinit var sarvamSTT: SarvamSTTManager
    private lateinit var porcupine: WakeWordManager

    private val apiClient = ApiClient()
    private val aiParser = AIOrderParser()
    private val cart = mutableListOf<CartItem>()
    private var tempProduct: ApiClient.Product? = null
    private var currentState = AssistantState.IDLE
    private val recordRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Butler Voice Assistant\nSay 'Hey Butler'", fontSize = 22.sp)
            }
        }

        lifecycleScope.launch {
            try {
                AuthManager.login("testuser@gmail.com", "123456")
                Log.d("Butler", "Login Success")
            } catch (e: Exception) {
                Log.e("Butler", "Login Failed: ${e.message}")
            }
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true

        sarvamSTT = SarvamSTTManager(
            context = this,
            apiKey = BuildConfig.SARVAM_API_KEY
        )

        porcupine = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) {
            runOnUiThread { onWakeWordDetected() }
        }

        ttsManager = TTSManager(
            context = this,
            elevenLabsApiKey = BuildConfig.ELEVENLABS_API_KEY,
            voiceId = "1Z7Y8o9cvUeWq8oLKgMY"
        )

        ttsManager.init { checkMicPermission() }
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                recordRequestCode
            )
        } else {
            startWakeWordListening()
        }
    }

    private fun startWakeWordListening() {
        currentState = AssistantState.IDLE
        cart.clear()
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        currentState = AssistantState.LISTENING
        speak("Hey! What would you like today?") { startListening() }
    }

    private fun startListening() {
        Log.d("Butler", "Starting STT...")
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    Log.d("Butler", "Transcript: $text")
                    if (text.isBlank()) {
                        speak("Sorry, I didn't catch that") { startListening() }
                    } else {
                        handleCommand(text.lowercase())
                    }
                }
            },
            onError = {
                runOnUiThread {
                    speak("Something went wrong") { startWakeWordListening() }
                }
            }
        )
    }

    private fun handleCommand(text: String) {
        when (currentState) {

            AssistantState.LISTENING -> {
                aiParser.parse(text) { order ->
                    runOnUiThread {
                        val fallbackItem = when {
                            text.contains("rice")  -> "rice"
                            text.contains("oil")   -> "oil"
                            text.contains("sugar") -> "sugar"
                            text.contains("wheat") -> "wheat"
                            text.contains("dal")   -> "dal"
                            text.contains("salt")  -> "salt"
                            else -> null
                        }
                        val itemName = order?.items?.firstOrNull()?.name ?: fallbackItem
                        if (itemName != null) {
                            lifecycleScope.launch {
                                val product = apiClient.searchProduct(itemName)
                                if (product != null) {
                                    tempProduct = product
                                    currentState = AssistantState.ASKING_QUANTITY
                                    speak("How much ${product.name}?") { startListening() }
                                } else {
                                    speak("I couldn't find $itemName. What else?") { startListening() }
                                }
                            }
                        } else {
                            speak("Tell me what you want to order") { startListening() }
                        }
                    }
                }
            }

            AssistantState.ASKING_QUANTITY -> {
                val qty = extractQuantity(text)
                if (tempProduct != null) {
                    cart.add(CartItem(tempProduct!!, qty))
                    speak("Added $qty ${tempProduct!!.name}. Anything else?") {
                        currentState = AssistantState.ASKING_MORE
                        startListening()
                    }
                } else {
                    speak("Let's try again") {
                        currentState = AssistantState.LISTENING
                        startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE -> {
                when {
                    text.contains("yes") || text.contains("add") || text.contains("more") -> {
                        currentState = AssistantState.LISTENING
                        speak("What else would you like?") { startListening() }
                    }
                    text.contains("no") || text.contains("done") || text.contains("that") -> {
                        readCartAndConfirm()
                    }
                    else -> {
                        speak("Should I add more or place the order?") { startListening() }
                    }
                }
            }

            AssistantState.CONFIRMING -> {
                when {
                    text.contains("yes") || text.contains("place") || text.contains("confirm") -> {
                        placeOrder()
                    }
                    text.contains("no") || text.contains("cancel") -> {
                        speak("Order cancelled") {
                            cart.clear()
                            startWakeWordListening()
                        }
                    }
                    else -> {
                        speak("Say yes to confirm or no to cancel.") { startListening() }
                    }
                }
            }

            else -> {}
        }
    }

    private fun extractQuantity(text: String): Int {
        val wordNumbers = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        )
        return Regex("\\d+").find(text)?.value?.toIntOrNull()
            ?: wordNumbers.entries.firstOrNull { text.contains(it.key) }?.value
            ?: 1
    }

    private fun readCartAndConfirm() {
        val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.CONFIRMING
        speak("You ordered $summary. Shall I place the order?") { startListening() }
    }

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = AuthManager.currentUserId()
                if (userId == null) {
                    speak("Please log in to place an order") { startWakeWordListening() }
                    return@launch
                }
                val orderResult = apiClient.createOrder(cart, userId)
                val shortId = orderResult.id.takeLast(6).uppercase()
                Log.d("Butler", "Order placed: ${orderResult.id}")
                speak("Order placed! Your ID is $shortId") {
                    cart.clear()
                    startWakeWordListening()
                }
            } catch (e: Exception) {
                Log.e("Butler", "Order failed: ${e.message}")
                speak("Sorry, couldn't place your order. Try again.") {
                    startWakeWordListening()
                }
            }
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        ttsManager.speak(text) { onDone?.invoke() }
    }

    override fun onPause() {
        super.onPause()
        porcupine.stop()
        sarvamSTT.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupine.stop()
        sarvamSTT.stop()
        ttsManager.shutdown()
    }

    override fun onResume() {
        super.onResume()
        try { startLockTask() } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWordListening()
        } else {
            Log.e("Permission", "Mic denied")
        }
    }
}
