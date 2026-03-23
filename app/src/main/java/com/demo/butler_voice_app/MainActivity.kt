package com.demo.butler_voice_app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.demo.butler_voice_app.BuildConfig
import com.demo.butler_voice_app.api.ApiClient
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.ai.AIOrderParser
import com.demo.butler_voice_app.voice.SarvamSTTManager
import com.demo.butler_voice_app.ui.ButlerScreen
import com.demo.butler_voice_app.ui.ButlerUiState
import kotlinx.coroutines.launch

enum class AssistantState {
    IDLE,
    CHECKING_AUTH,
    ASKING_IS_NEW_USER,
    ASKING_NAME,
    ASKING_EMAIL,
    ASKING_PASSWORD,
    LISTENING,
    ASKING_QUANTITY,
    ASKING_MORE,
    CONFIRMING
}

class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf<ButlerUiState>(ButlerUiState.Idle)

    private var tempName = ""
    private var tempEmail = ""

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
            val state by uiState
            ButlerScreen(state = state)
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

    // ─── UI HELPER ────────────────────────────────────────────

    private fun setUiState(s: ButlerUiState) {
        runOnUiThread { uiState.value = s }
    }

    // ─── PERMISSION ───────────────────────────────────────────

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

    // ─── WAKE WORD ────────────────────────────────────────────

    private fun startWakeWordListening() {
        currentState = AssistantState.IDLE
        cart.clear()
        tempName = ""
        tempEmail = ""
        setUiState(ButlerUiState.Idle)
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        currentState = AssistantState.CHECKING_AUTH
        setUiState(ButlerUiState.Thinking("Checking..."))

        if (UserSessionManager.isLoggedIn() &&
            UserSessionManager.currentProfile != null) {
            // Returning user in same session — greet by name
            val name = UserSessionManager.currentProfile
                ?.full_name?.split(" ")?.first() ?: "there"
            currentState = AssistantState.LISTENING
            val msg = "Welcome back $name! What would you like today?"
            setUiState(ButlerUiState.Speaking(msg))
            speak(msg) { startListening() }
        } else {
            // No active session — ask new or returning
            currentState = AssistantState.ASKING_IS_NEW_USER
            val msg = "Welcome! Are you a new customer, or have you ordered before?"
            setUiState(ButlerUiState.Speaking(msg))
            speak(msg) { startListening() }
        }
    }

    // ─── LISTEN ───────────────────────────────────────────────

    private fun startListening() {
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    Log.d("Butler", "Transcript: $text")
                    if (text.isBlank()) {
                        val msg = "Sorry, I didn't catch that"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    } else {
                        setUiState(ButlerUiState.Thinking(text))
                        handleCommand(text.lowercase())
                    }
                }
            },
            onError = {
                runOnUiThread {
                    setUiState(ButlerUiState.Error("Couldn't hear you"))
                    speak("Something went wrong") { startWakeWordListening() }
                }
            }
        )
    }

    // ─── COMMAND HANDLER ──────────────────────────────────────

    private fun handleCommand(text: String) {
        when (currentState) {

            // ── Auth flow ──────────────────────────────────────

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    text.contains("new") || text.contains("first") ||
                    text.contains("register") || text.contains("sign") -> {
                        currentState = AssistantState.ASKING_NAME
                        val msg = "Great! What's your name?"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                    text.contains("returning") || text.contains("before") ||
                    text.contains("old") || text.contains("yes") ||
                    text.contains("have") -> {
                        currentState = AssistantState.ASKING_EMAIL
                        val msg = "Welcome back! Please say your email address."
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                    else -> {
                        val msg = "Say 'new customer' if first time, or 'returning' if you've ordered before."
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                }
            }

            AssistantState.ASKING_NAME -> {
                tempName = text.trim()
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                currentState = AssistantState.ASKING_EMAIL
                val msg = "Nice to meet you $tempName! What's your email address?"
                setUiState(ButlerUiState.Speaking(msg))
                speak(msg) { startListening() }
            }

            AssistantState.ASKING_EMAIL -> {
                tempEmail = text.trim()
                    .replace(" at ", "@")
                    .replace(" dot ", ".")
                    .replace(" ", "")
                    .lowercase()
                currentState = AssistantState.ASKING_PASSWORD
                val msg = "Got it. Now say your password."
                setUiState(ButlerUiState.Speaking(msg))
                speak(msg) { startListening() }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = text.trim().replace(" ", "")
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        // ── New user signup ──
                        setUiState(ButlerUiState.Thinking("Creating account..."))
                        val result = UserSessionManager.signup(
                            email    = tempEmail,
                            password = password,
                            name     = tempName,
                            phone    = ""
                        )
                        result.fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name
                                    ?.split(" ")?.first() ?: tempName
                                val msg = "Account created! Welcome $firstName! What would you like to order?"
                                setUiState(ButlerUiState.Speaking(msg))
                                speak(msg) { startListening() }
                            },
                            onFailure = { e ->
                                Log.e("Butler", "Signup failed: ${e.message}")
                                val msg = "Sorry, I couldn't create your account. Please try again."
                                setUiState(ButlerUiState.Error(msg))
                                speak(msg) { startWakeWordListening() }
                            }
                        )
                    } else {
                        // ── Returning user login ──
                        setUiState(ButlerUiState.Thinking("Logging in..."))
                        val result = UserSessionManager.login(
                            email    = tempEmail,
                            password = password
                        )
                        result.fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name
                                    ?.split(" ")?.first() ?: "there"
                                val greeting = if (UserSessionManager.purchaseHistory.isNotEmpty()) {
                                    val lastItem = UserSessionManager
                                        .purchaseHistory.first().product_name
                                    "Welcome back $firstName! Last time you ordered $lastItem. What would you like today?"
                                } else {
                                    "Welcome back $firstName! What would you like today?"
                                }
                                setUiState(ButlerUiState.Speaking(greeting))
                                speak(greeting) { startListening() }
                            },
                            onFailure = { e ->
                                Log.e("Butler", "Login failed: ${e.message}")
                                val msg = "Login failed. Please check your email and password."
                                setUiState(ButlerUiState.Error(msg))
                                speak(msg) { startWakeWordListening() }
                            }
                        )
                    }
                }
            }

            // ── Order flow ─────────────────────────────────────

            AssistantState.LISTENING -> {
                aiParser.parse(text) { order ->
                    runOnUiThread {
                        val fallbackItem = when {
                            text.contains("rice")   -> "rice"
                            text.contains("oil")    -> "oil"
                            text.contains("sugar")  -> "sugar"
                            text.contains("wheat")  -> "wheat"
                            text.contains("dal")    -> "dal"
                            text.contains("salt")   -> "salt"
                            text.contains("milk")   -> "milk"
                            text.contains("flour")  -> "flour"
                            text.contains("tea")    -> "tea"
                            text.contains("coffee") -> "coffee"
                            else -> null
                        }
                        val itemName = order?.items?.firstOrNull()?.name ?: fallbackItem
                        if (itemName != null) {
                            lifecycleScope.launch {
                                val product = apiClient.searchProduct(itemName)
                                if (product != null) {
                                    tempProduct = product
                                    currentState = AssistantState.ASKING_QUANTITY
                                    val msg = "How much ${product.name}?"
                                    setUiState(ButlerUiState.Speaking(msg))
                                    speak(msg) { startListening() }
                                } else {
                                    val msg = "I couldn't find $itemName. What else would you like?"
                                    setUiState(ButlerUiState.Speaking(msg))
                                    speak(msg) { startListening() }
                                }
                            }
                        } else {
                            val msg = "Tell me what you want to order"
                            setUiState(ButlerUiState.Speaking(msg))
                            speak(msg) { startListening() }
                        }
                    }
                }
            }

            AssistantState.ASKING_QUANTITY -> {
                val qty = extractQuantity(text)
                if (tempProduct != null) {
                    cart.add(CartItem(tempProduct!!, qty))
                    val msg = "Added $qty ${tempProduct!!.name}. Anything else?"
                    setUiState(ButlerUiState.Speaking(msg))
                    speak(msg) {
                        currentState = AssistantState.ASKING_MORE
                        startListening()
                    }
                } else {
                    val msg = "Let's try again. What would you like?"
                    setUiState(ButlerUiState.Speaking(msg))
                    speak(msg) {
                        currentState = AssistantState.LISTENING
                        startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE -> {
                when {
                    text.contains("yes") || text.contains("add") ||
                    text.contains("more") || text.contains("also") -> {
                        currentState = AssistantState.LISTENING
                        val msg = "What else would you like?"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                    text.contains("no") || text.contains("done") ||
                    text.contains("that") || text.contains("nothing") -> {
                        readCartAndConfirm()
                    }
                    else -> {
                        val msg = "Should I add more items or place the order?"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                }
            }

            AssistantState.CONFIRMING -> {
                when {
                    text.contains("yes") || text.contains("place") ||
                    text.contains("confirm") || text.contains("ok") -> {
                        placeOrder()
                    }
                    text.contains("no") || text.contains("cancel") -> {
                        val msg = "Order cancelled. Goodbye!"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) {
                            cart.clear()
                            UserSessionManager.logout()
                            startWakeWordListening()
                        }
                    }
                    else -> {
                        val msg = "Say yes to confirm or no to cancel."
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                }
            }

            else -> {}
        }
    }

    // ─── ORDER HELPERS ────────────────────────────────────────

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
        val msg = "You ordered $summary. Shall I place the order?"
        setUiState(ButlerUiState.Speaking(msg))
        speak(msg) { startListening() }
    }

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId()
                if (userId == null) {
                    val msg = "Session expired. Please say Hey Butler to start again."
                    setUiState(ButlerUiState.Speaking(msg))
                    speak(msg) { startWakeWordListening() }
                    return@launch
                }
    
                val orderResult = apiClient.createOrder(cart, userId)
                val shortId = orderResult.id.takeLast(6).uppercase()
                val firstName = UserSessionManager.currentProfile
                    ?.full_name?.split(" ")?.first() ?: ""
    
                Log.d("Butler", "Order placed: ${orderResult.id}")
                setUiState(ButlerUiState.OrderDone(shortId, orderResult.total_amount))
    
                val farewell = if (firstName.isNotBlank()) {
                    "Order placed $firstName! Your ID is $shortId. Thank you, goodbye!"
                } else {
                    "Order placed! Your ID is $shortId. Thank you!"
                }
    
                speak(farewell) {
                    cart.clear()
                    UserSessionManager.logout()
                    Handler(Looper.getMainLooper()).postDelayed({
                        startWakeWordListening()
                    }, 3000)
                }
    
            } catch (e: Exception) {
                Log.e("Butler", "Order failed: ${e.message}")
                val msg = "Sorry, couldn't place your order. Try again."
                setUiState(ButlerUiState.Error(msg))
                speak(msg) { startWakeWordListening() }
            }
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        ttsManager.speak(text) { onDone?.invoke() }
    }

    // ─── LIFECYCLE ────────────────────────────────────────────

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
