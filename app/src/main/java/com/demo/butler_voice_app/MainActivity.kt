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
import com.demo.butler_voice_app.ai.AIOrderParser
import com.demo.butler_voice_app.api.ApiClient
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.ui.ButlerScreen
import com.demo.butler_voice_app.ui.ButlerUiState
import com.demo.butler_voice_app.voice.SarvamSTTManager
import kotlinx.coroutines.launch

enum class AssistantState {
    IDLE, CHECKING_AUTH, ASKING_IS_NEW_USER, ASKING_NAME,
    ASKING_EMAIL, ASKING_PASSWORD, LISTENING,
    ASKING_QUANTITY, ASKING_MORE, CONFIRMING
}

class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf<ButlerUiState>(ButlerUiState.Idle)
    private var tempName = ""
    private var tempEmail = ""
    private var detectedLanguage = "en"

    private lateinit var ttsManager: TTSManager
    private lateinit var sarvamSTT: SarvamSTTManager
    private lateinit var porcupine: WakeWordManager

    private val apiClient = ApiClient()
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

        sarvamSTT = SarvamSTTManager(context = this, apiKey = BuildConfig.SARVAM_API_KEY)

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

    private fun setUiState(s: ButlerUiState) = runOnUiThread { uiState.value = s }

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
        currentState = AssistantState.IDLE
        cart.clear()
        tempName = ""
        tempEmail = ""
        detectedLanguage = "en"
        setUiState(ButlerUiState.Idle)
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        currentState = AssistantState.CHECKING_AUTH
        setUiState(ButlerUiState.Thinking("Checking..."))

        if (UserSessionManager.isLoggedIn() && UserSessionManager.currentProfile != null) {
            val name = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
            val history = UserSessionManager.purchaseHistory
            currentState = AssistantState.LISTENING

            val greeting = if (history.isNotEmpty()) {
                val last = history.first().product_name
                "Welcome back $name! Last time you ordered $last. What would you like today?"
            } else {
                "Welcome back $name! What would you like to order?"
            }
            setUiState(ButlerUiState.Speaking(greeting))
            speak(greeting) { startListening() }
        } else {
            currentState = AssistantState.ASKING_IS_NEW_USER
            val msg = "Welcome! Are you a new customer or have you ordered before?"
            setUiState(ButlerUiState.Speaking(msg))
            speak(msg) { startListening() }
        }
    }

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

    private fun handleCommand(text: String) {
        when (currentState) {

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    text.contains("new") || text.contains("first") ||
                    text.contains("register") || text.contains("नया") -> {
                        currentState = AssistantState.ASKING_NAME
                        speak("Great! What's your name?") { startListening() }
                    }
                    text.contains("returning") || text.contains("before") ||
                    text.contains("yes") || text.contains("have") ||
                    text.contains("पहले") -> {
                        currentState = AssistantState.ASKING_EMAIL
                        speak("Welcome back! Please say your email.") { startListening() }
                    }
                    else -> speak("Say new customer or returning customer.") { startListening() }
                }
            }

            AssistantState.ASKING_NAME -> {
                val cleaned = text.trim()
                    .replace(Regex("my name is ", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("i am ", RegexOption.IGNORE_CASE), "")
                    .replace(".", "").trim()
                tempName = cleaned.split(" ")
                    .firstOrNull { it.length > 1 }
                    ?.replaceFirstChar { it.uppercase() } ?: cleaned
                currentState = AssistantState.ASKING_EMAIL
                speak("Nice to meet you $tempName! What's your email?") { startListening() }
            }

            AssistantState.ASKING_EMAIL -> {
                tempEmail = text.trim()
                    .replace(" at ", "@")
                    .replace(" dot ", ".")
                    .replace(" ", "")
                    .lowercase()
                    .trimEnd('.', ',', '!')
                
                Log.d("Butler", "Email captured: $tempEmail")
                currentState = AssistantState.ASKING_PASSWORD
                speak("Got it. Now say your password.") { startListening() }
            }

            AssistantState.ASKING_PASSWORD -> {
               // Clean password — remove spaces and trailing punctuation
                val password = text.trim()
                    .replace(" ", "")
                    .trimEnd('.', ',', '!')
                
                Log.d("Butler", "Password length: ${password.length}")
                
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.Thinking("Creating account..."))
                        UserSessionManager.signup(tempEmail, password, tempName, "")
                            .fold(
                                onSuccess = { profile ->
                                    currentState = AssistantState.LISTENING
                                    val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                    val msg = "Account created! Welcome $firstName! What would you like to order?"
                                    setUiState(ButlerUiState.Speaking(msg))
                                    speak(msg) { startListening() }
                                },
                                onFailure = {
                                    speak("Sorry, couldn't create account. Try again.") { startWakeWordListening() }
                                }
                            )
                    } else {
                        setUiState(ButlerUiState.Thinking("Logging in..."))
                        UserSessionManager.login(tempEmail, password)
                            .fold(
                                onSuccess = { profile ->
                                    currentState = AssistantState.LISTENING
                                    val firstName = profile.full_name?.split(" ")?.first() ?: "there"
                                    val history = UserSessionManager.purchaseHistory
                                    val greeting = if (history.isNotEmpty()) {
                                        "Welcome back $firstName! Last time you ordered ${history.first().product_name}. What would you like?"
                                    } else {
                                        "Welcome back $firstName! What would you like to order?"
                                    }
                                    setUiState(ButlerUiState.Speaking(greeting))
                                    speak(greeting) { startListening() }
                                },
                                onFailure = {
                                    speak("Login failed. Please check your email and password.") { startWakeWordListening() }
                                }
                            )
                    }
                }
            }

            AssistantState.LISTENING -> {
                // Check for repeat order command
                if (text.contains("repeat") || text.contains("same as last") ||
                    text.contains("previous order") || text.contains("last order")) {
                    repeatLastOrder()
                    return
                }

                // Check for order history command
                if (text.contains("what did i order") || text.contains("my orders") ||
                    text.contains("history") || text.contains("last time")) {
                    readOrderHistory()
                    return
                }

                // Parse with AI — supports multi-item
                lifecycleScope.launch {
                    val parsed = AIOrderParser.parse(text)
                    detectedLanguage = parsed.detectedLanguage

                    runOnUiThread {
                        if (parsed.items.isEmpty()) {
                            // Fallback keyword detection
                            val fallback = when {
                                text.contains("rice") || text.contains("चावल") -> "rice"
                                text.contains("oil") || text.contains("तेल") -> "oil"
                                text.contains("sugar") || text.contains("चीनी") -> "sugar"
                                text.contains("wheat") || text.contains("गेहूं") -> "wheat"
                                text.contains("dal") || text.contains("दाल") -> "dal"
                                text.contains("salt") || text.contains("नमक") -> "salt"
                                text.contains("milk") || text.contains("दूध") -> "milk"
                                else -> null
                            }
                            if (fallback != null) {
                                searchAndAskQuantity(fallback)
                            } else {
                                speak("Tell me what you want to order") { startListening() }
                            }
                        } else if (parsed.items.size == 1) {
                            // Single item — ask quantity if needed
                            val item = parsed.items.first()
                            searchAndAskQuantity(item.name, item.quantity, item.unit)
                        } else {
                            // Multi-item — add all to cart directly
                            lifecycleScope.launch {
                                addMultipleItemsToCart(parsed.items)
                            }
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
                    speak("Let's try again. What would you like?") {
                        currentState = AssistantState.LISTENING
                        startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE -> {
                when {
                    text.contains("yes") || text.contains("add") ||
                    text.contains("more") || text.contains("also") ||
                    text.contains("और") -> {
                        currentState = AssistantState.LISTENING
                        speak("What else would you like?") { startListening() }
                    }
                    text.contains("no") || text.contains("done") ||
                    text.contains("nothing") || text.contains("बस") ||
                    text.contains("that's all") -> {
                        readCartAndConfirm()
                    }
                    else -> {
                        speak("Should I add more or place the order?") { startListening() }
                    }
                }
            }

            AssistantState.CONFIRMING -> {
                when {
                    text.contains("yes") || text.contains("place") ||
                    text.contains("confirm") || text.contains("ok") ||
                    text.contains("हाँ") || text.contains("हां") -> {
                        placeOrder()
                    }
                    text.contains("no") || text.contains("cancel") ||
                    text.contains("नहीं") -> {
                        speak("Order cancelled. Goodbye!") {
                            cart.clear()
                            UserSessionManager.logout()
                            startWakeWordListening()
                        }
                    }
                    else -> speak("Say yes to confirm or no to cancel.") { startListening() }
                }
            }

            else -> {}
        }
    }

    // ─── SEARCH + QUANTITY ────────────────────────────────────

    private fun searchAndAskQuantity(
        itemName: String,
        qty: Int = 0,
        unit: String? = null
    ) {
        lifecycleScope.launch {
            val product = apiClient.searchProduct(itemName)
            runOnUiThread {
                if (product != null) {
                    tempProduct = product
                    if (qty > 0) {
                        // Quantity already known — add directly
                        val displayUnit = unit ?: product.unit ?: ""
                        val displayQty = if (displayUnit.isNotBlank()) "$qty $displayUnit" else "$qty"
                        cart.add(CartItem(product, qty))
                        val msg = "Added $displayQty ${product.name}. Anything else?"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) {
                            currentState = AssistantState.ASKING_MORE
                            startListening()
                        }
                    } else {
                        currentState = AssistantState.ASKING_QUANTITY
                        val msg = "How much ${product.name}?"
                        setUiState(ButlerUiState.Speaking(msg))
                        speak(msg) { startListening() }
                    }
                } else {
                    val msg = "I couldn't find $itemName. What else would you like?"
                    setUiState(ButlerUiState.Speaking(msg))
                    speak(msg) { startListening() }
                }
            }
        }
    }

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>()
        val notFound = mutableListOf<String>()

        for (item in items) {
            val product = apiClient.searchProduct(item.name)
            if (product != null) {
                cart.add(CartItem(product, item.quantity))
                val unit = item.unit ?: product.unit ?: ""
                val qty = if (unit.isNotBlank()) "${item.quantity} $unit" else "${item.quantity}"
                found.add("$qty ${product.name}")
            } else {
                notFound.add(item.name)
            }
        }

        runOnUiThread {
            val summary = found.joinToString(", ")
            val msg = if (notFound.isEmpty()) {
                "Added $summary. Anything else?"
            } else {
                "Added $summary. Couldn't find ${notFound.joinToString(", ")}. Anything else?"
            }
            setUiState(ButlerUiState.Speaking(msg))
            speak(msg) {
                currentState = AssistantState.ASKING_MORE
                startListening()
            }
        }
    }

    // ─── ORDER HISTORY ────────────────────────────────────────

    private fun readOrderHistory() {
        val history = UserSessionManager.purchaseHistory
        if (history.isEmpty()) {
            speak("You haven't ordered anything yet. What would you like?") { startListening() }
        } else {
            val items = history.take(3).joinToString(", ") { it.product_name }
            speak("Your recent orders include $items. Would you like to order any of these?") {
                startListening()
            }
        }
    }

    private fun repeatLastOrder() {
        lifecycleScope.launch {
            val userId = UserSessionManager.currentUserId() ?: return@launch
            val orders = apiClient.getOrderHistory(userId)
            if (orders.isEmpty()) {
                runOnUiThread {
                    speak("You have no previous orders. What would you like?") { startListening() }
                }
                return@launch
            }

            val lastOrderId = orders.first().id
            val items = apiClient.getOrderItems(lastOrderId)

            if (items.isEmpty()) {
                runOnUiThread {
                    speak("Couldn't load your last order. What would you like?") { startListening() }
                }
                return@launch
            }

            // Add all items to cart
            for (item in items) {
                val product = apiClient.searchProduct(item.product_name)
                if (product != null) {
                    cart.add(CartItem(product, item.quantity))
                }
            }

            val summary = items.joinToString(", ") { "${it.quantity} ${it.product_name}" }
            runOnUiThread {
                currentState = AssistantState.CONFIRMING
                val msg = "Repeating your last order: $summary. Shall I place it?"
                setUiState(ButlerUiState.Speaking(msg))
                speak(msg) { startListening() }
            }
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────

    private fun extractQuantity(text: String): Int {
        val wordNumbers = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5
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
                    speak("Session expired. Say Hey Butler to start.") { startWakeWordListening() }
                    return@launch
                }

                val orderResult = apiClient.createOrder(cart, userId)
                val shortId = orderResult.id.takeLast(6).uppercase()
                val firstName = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""

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

    override fun onPause() { super.onPause(); porcupine.stop(); sarvamSTT.stop() }
    override fun onDestroy() { super.onDestroy(); porcupine.stop(); sarvamSTT.stop(); ttsManager.shutdown() }
    override fun onResume() { super.onResume(); try { startLockTask() } catch (_: Exception) {} }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startWakeWordListening()
        else Log.e("Permission", "Mic denied")
    }
}
