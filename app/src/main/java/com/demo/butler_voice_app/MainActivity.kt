package com.demo.butler_voice_app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.demo.butler_voice_app.api.*
import com.demo.butler_voice_app.ai.OrderParser
import com.demo.butler_voice_app.voice.SarvamSTTManager
import kotlinx.coroutines.launch

// ---------------- STATE ----------------
enum class AssistantState {
    IDLE,
    LISTENING,
    ASKING_QUANTITY,
    ASKING_MORE,
    CONFIRMING
}

// ---------------- CART ----------------
data class CartItem(
    val product: ApiClient.Product,
    var quantity: Int
)

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TTSManager
    private lateinit var sarvamSTT: SarvamSTTManager
    private lateinit var porcupine: WakeWordManager

    private val apiClient = ApiClient()
    private val parser = OrderParser()

    private val cart = mutableListOf<CartItem>()
    private var tempProduct: ApiClient.Product? = null

    private var currentState = AssistantState.IDLE
    private val recordRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("🎤 Butler Voice Assistant\nSay 'Hey Butler'", fontSize = 22.sp)
            }
        }

        lifecycleScope.launch {
            try {
                AuthManager.login("testuser@gmail.com", "123456")
                Log.d("Butler", "✅ Login Success")
            } catch (e: Exception) {
                Log.e("Butler", "❌ Login Failed: ${e.message}")
            }
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true

        sarvamSTT = SarvamSTTManager(BuildConfig.SARVAM_API_KEY)

        porcupine = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) {
            runOnUiThread { onWakeWordDetected() }
        }

        ttsManager = TTSManager(
            context = this,
            elevenLabsApiKey = BuildConfig.ELEVENLABS_API_KEY,
            voiceId = BuildConfig.ELEVENLABS_VOICE_ID
        )

        ttsManager.init {
            checkMicPermission()
        }

    }

    // ---------------- PERMISSION ----------------
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
        Log.d("Butler", "🎧 Waiting for wake word...")
        porcupine.start()
    }

    // ---------------- WAKE ----------------
    private fun onWakeWordDetected() {
        currentState = AssistantState.LISTENING

        speak("Hey! What would you like today?") {
            startListening()
        }
    }

    // ---------------- LISTEN ----------------
    private fun startListening() {
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    if (text.isBlank()) {
                        speak("Sorry, I missed that") { startWakeWordListening() }
                    } else {
                        Log.d("Butler", "🎤 $text")
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

    // ---------------- CORE ----------------
    private fun handleCommand(text: String) {

        when (currentState) {

            AssistantState.LISTENING -> {

                val order = parser.parse(text)

                if (order != null) {
                    lifecycleScope.launch {

                        val product = apiClient.searchProduct(order.product)

                        if (product != null) {
                            tempProduct = product
                            currentState = AssistantState.ASKING_QUANTITY

                            speak("Nice choice. How much ${product.name} should I add?") {
                                startListening()
                            }

                        } else {
                            speak("I couldn't find ${order.product}. Try something else.") {
                                startListening()
                            }
                        }
                    }

                } else {
                    speak("Tell me the item you want to order") {
                        startListening()
                    }
                }
            }

            AssistantState.ASKING_QUANTITY -> {

                val qty = extractQuantity(text)

                if (tempProduct != null) {

                    cart.add(CartItem(tempProduct!!, qty))

                    speak("Alright, I've added $qty ${tempProduct!!.name}. Would you like anything else?") {
                        currentState = AssistantState.ASKING_MORE
                        startListening()
                    }

                } else {
                    speak("Let's try again. What item?") {
                        currentState = AssistantState.LISTENING
                        startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE -> {

                when {
                    text.contains("yes") || text.contains("add") -> {
                        currentState = AssistantState.LISTENING
                        speak("Sure, what else would you like to add?") { startListening() }
                    }

                    text.contains("no") || text.contains("done") || text.contains("that's all") -> {
                        readCartAndConfirm()
                    }

                    else -> {
                        speak("Should I add more items or place the order?") {
                            startListening()
                        }
                    }
                }
            }

            AssistantState.CONFIRMING -> {

                when {
                    text.contains("yes") || text.contains("place") -> {
                        placeOrder()
                    }

                    text.contains("no") || text.contains("cancel") -> {
                        speak("Alright, cancelled your order") {
                            cart.clear()
                            startWakeWordListening()
                        }
                    }

                    else -> {
                        speak("Should I place the order?") {
                            startListening()
                        }
                    }
                }
            }

            else -> {}
        }
    }

    // ---------------- HELPERS ----------------
    private fun extractQuantity(text: String): Int {
        val number = Regex("\\d+").find(text)?.value
        return number?.toIntOrNull() ?: 1
    }

    private fun readCartAndConfirm() {
        val summary = cart.joinToString(", ") {
            "${it.quantity} ${it.product.name}"
        }

        currentState = AssistantState.CONFIRMING

        speak("Here's what I have: $summary. Shall I place the order for you?") {
            startListening()
        }
    }

    private fun placeOrder() {

        lifecycleScope.launch {
            try {

                val items = cart.map {
                    mapOf(
                        "product_id" to it.product.id,
                        "product_name" to it.product.name,
                        "quantity" to it.quantity,
                        "price" to it.product.price
                    )
                }

                val orderId = apiClient.createOrder(items)

                speak("You're all set. I've placed your order successfully. Order ID is $orderId") {
                    cart.clear()
                    startWakeWordListening()
                }

            } catch (e: Exception) {
                speak("Oops, something went wrong") {
                    startWakeWordListening()
                }
            }
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {


        sarvamSTT.stop()
        ttsManager.speak(text) { onDone?.invoke() }
    }

    // ---------------- LIFECYCLE ----------------
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
        }
    }
}