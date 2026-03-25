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
import com.demo.butler_voice_app.ai.LanguageDetector
import com.demo.butler_voice_app.ai.LanguageManager
import com.demo.butler_voice_app.ai.TranslationManager
import com.demo.butler_voice_app.api.ApiClient
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.ui.ButlerScreen
import com.demo.butler_voice_app.ui.ButlerUiState
import com.demo.butler_voice_app.voice.SarvamSTTManager
import kotlinx.coroutines.launch

enum class AssistantState {
    IDLE, CHECKING_AUTH,
    ASKING_IS_NEW_USER, ASKING_NAME, ASKING_EMAIL, ASKING_PASSWORD,
    LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING
}

class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf<ButlerUiState>(ButlerUiState.Idle)

    private var tempName  = ""
    private var tempEmail = ""

    private lateinit var ttsManager  : TTSManager
    private lateinit var sarvamSTT   : SarvamSTTManager
    private lateinit var porcupine   : WakeWordManager

    private val apiClient    = ApiClient()
    private val cart         = mutableListOf<CartItem>()
    private var tempProduct  : ApiClient.Product? = null
    private var currentState = AssistantState.IDLE

    private val recordRequestCode = 101

    // ─── LIFECYCLE ────────────────────────────────────────────

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
        audioManager.mode          = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true

        sarvamSTT = SarvamSTTManager(this, BuildConfig.SARVAM_API_KEY)

        porcupine = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) {
            runOnUiThread { onWakeWordDetected() }
        }

        ttsManager = TTSManager(
            context          = this,
            elevenLabsApiKey = BuildConfig.ELEVENLABS_API_KEY,
            voiceId          = "CpLFIATEbkaZdJr01erZ"
        )

        ttsManager.init { checkMicPermission() }

        // Pre-warm cache
        lifecycleScope.launch {
            try { apiClient.searchProduct("rice"); Log.d("Butler", "Cache warmed") }
            catch (_: Exception) {}
        }
    }

    override fun onPause()   { super.onPause();   porcupine.stop(); sarvamSTT.stop() }
    override fun onDestroy() { super.onDestroy(); porcupine.stop(); sarvamSTT.stop(); ttsManager.shutdown() }
    override fun onResume()  { super.onResume();  try { startLockTask() } catch (_: Exception) {} }

    // ─── PERMISSIONS ──────────────────────────────────────────

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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startWakeWordListening()
    }

    // ─── WAKE WORD ────────────────────────────────────────────

    private fun startWakeWordListening() {
        currentState = AssistantState.IDLE
        cart.clear()
        tempName  = ""
        tempEmail = ""
        LanguageManager.reset()
        apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle)
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        currentState = AssistantState.CHECKING_AUTH
        setUiState(ButlerUiState.Thinking("Checking..."))

        if (UserSessionManager.isLoggedIn() && UserSessionManager.currentProfile != null) {
            val name    = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
            val history = UserSessionManager.purchaseHistory
            currentState = AssistantState.LISTENING

            val greeting = if (history.isNotEmpty()) {
                val last = history.first().product_name
                "Welcome back $name! Last time you ordered $last. What would you like today?"
            } else {
                "Welcome back $name! What would you like to order?"
            }
            speak(greeting) { startListening() }

        } else {
            currentState = AssistantState.ASKING_IS_NEW_USER
            val msg = "Welcome! Are you a new customer or have you ordered before?"
            speak(msg) { startListening() }
        }
    }

    // ─── STT ──────────────────────────────────────────────────

    private fun startListening() {
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")

        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")

                    // Detect language — only update if clearly non-English script
                    val detectedLang = LanguageDetector.detect(transcript)
                    LanguageManager.setLanguage(detectedLang)
                    Log.d("LANG_DEBUG", "Detected=$detectedLang | Session=${LanguageManager.getLanguage()}")

                    if (transcript.isBlank()) {
                        speak("Sorry, I didn't catch that") { startListening() }
                        return@runOnUiThread
                    }

                    setUiState(ButlerUiState.Thinking(transcript))
                    handleCommand(transcript)
                }
            },
            onError = {
                runOnUiThread {
                    speak("Something went wrong") { startWakeWordListening() }
                }
            }
        )
    }

    // ─── COMMAND HANDLER ──────────────────────────────────────

    private fun handleCommand(text: String) {
        val lower = text.lowercase().trim()

        when (currentState) {

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    lower.contains("new")      || lower.contains("first") ||
                    lower.contains("register") || lower.contains("नया")   ||
                    lower.contains("नई") -> {
                        currentState = AssistantState.ASKING_NAME
                        speak("Great! What's your name?") { startListening() }
                    }
                    lower.contains("returning") || lower.contains("before") ||
                    lower.contains("yes")       || lower.contains("have")   ||
                    lower.contains("पहले")      || lower.contains("login")  -> {
                        currentState = AssistantState.ASKING_EMAIL
                        speak("Welcome back! Please say your email.") { startListening() }
                    }
                    else -> speak("Say new customer or returning customer.") { startListening() }
                }
            }

            AssistantState.ASKING_NAME -> {
                val cleaned = text.trim()
                    .replace(Regex("my name is\\s*",   RegexOption.IGNORE_CASE), "")
                    .replace(Regex("i am\\s*",         RegexOption.IGNORE_CASE), "")
                    .replace(Regex("mera naam\\s*",    RegexOption.IGNORE_CASE), "")
                    .replace("मेरा नाम", "")
                    .replace("है", "")
                    .replace("నా పేరు", "")
                    .replace(".", "")
                    .trim()

                if (cleaned.isBlank() || cleaned.contains("@")) {
                    speak("Please say just your first name.") { startListening() }
                    return
                }

                tempName = cleaned.split(" ")
                    .firstOrNull { it.length > 1 }
                    ?.replaceFirstChar { it.uppercase() }
                    ?: cleaned.replaceFirstChar { it.uppercase() }

                currentState = AssistantState.ASKING_EMAIL
                speak("Nice to meet you $tempName! What's your email?") { startListening() }
            }

            AssistantState.ASKING_EMAIL -> {
                val cleaned = text.trim()
                    .replace(Regex("at the rate", RegexOption.IGNORE_CASE), "@")
                    .replace(Regex("\\bat\\b",    RegexOption.IGNORE_CASE), "@")
                    .replace(Regex("dot com",     RegexOption.IGNORE_CASE), ".com")
                    .replace(Regex("dot in",      RegexOption.IGNORE_CASE), ".in")
                    .replace(Regex("dot net",     RegexOption.IGNORE_CASE), ".net")
                    .replace(Regex("\\bdot\\b",   RegexOption.IGNORE_CASE), ".")
                    .replace(" ", "")
                    .lowercase()
                    .trimEnd('.', ',', '!')

                if (!cleaned.contains("@") || !cleaned.contains(".") || cleaned.length < 6) {
                    speak("I didn't catch a valid email. Please say it again, like john at gmail dot com.") {
                        startListening()
                    }
                    return
                }

                tempEmail = cleaned
                Log.d("Butler", "Email captured: $tempEmail")
                currentState = AssistantState.ASKING_PASSWORD
                speak("Got it. Now say your password.") { startListening() }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = text.trim()
                    .replace(" ", "")
                    .trimEnd('.', ',', '!')

                Log.d("Butler", "Password length: ${password.length}")

                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        // SIGNUP
                        setUiState(ButlerUiState.Thinking("Creating account..."))
                        UserSessionManager.signup(tempEmail, password, tempName, "")
                            .fold(
                                onSuccess = { profile ->
                                    currentState = AssistantState.LISTENING
                                    val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                    speak("Account created! Welcome $firstName! What would you like to order?") {
                                        startListening()
                                    }
                                },
                                onFailure = { error ->
                                    if (error.message?.contains("user_already_exists") == true ||
                                        error.message?.contains("already registered") == true) {
                                        speak("Account already exists. Logging you in.") {
                                            lifecycleScope.launch {
                                                doLogin(tempEmail, password)
                                            }
                                        }
                                    } else {
                                        speak("Sorry, couldn't create account. Try again.") {
                                            startWakeWordListening()
                                        }
                                    }
                                }
                            )
                    } else {
                        // LOGIN
                        setUiState(ButlerUiState.Thinking("Logging in..."))
                        doLogin(tempEmail, password)
                    }
                }
            }

            AssistantState.LISTENING -> handleOrderIntent(text, lower)

            AssistantState.ASKING_QUANTITY -> {
                val qty = extractQuantity(text)
                val product = tempProduct
                if (product != null) {
                    cart.add(CartItem(product, qty))
                    val msg = "Added $qty ${product.name}. Anything else?"
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

            AssistantState.ASKING_MORE -> handleAskingMore(lower, text)

            AssistantState.CONFIRMING -> {
                when {
                    lower.contains("yes")     || lower.contains("place")   ||
                    lower.contains("confirm") || lower.contains("ok")      ||
                    lower.contains("haan")    || lower.contains("हाँ")     ||
                    lower.contains("हां")     || lower.contains("theek")   -> placeOrder()

                    lower.contains("no")      || lower.contains("cancel")  ||
                    lower.contains("nahi")    || lower.contains("नहीं")    -> {
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

    // ─── ORDER INTENT (LISTENING state) ───────────────────────

    private fun handleOrderIntent(text: String, lower: String) {

        // Shortcut keywords BEFORE AI call
        if (lower.contains("repeat") || lower.contains("same as last") ||
            lower.contains("previous order")) {
            repeatLastOrder(); return
        }
        if (lower.contains("my orders") || lower.contains("history") ||
            lower.contains("what did i order")) {
            readOrderHistory(); return
        }

        // NO-MORE shortcuts (catches Hindi without AI call)
        if (isNoMoreIntent(lower)) {
            readCartAndConfirm(); return
        }

        lifecycleScope.launch {
            val parsed = AIOrderParser.parse(text)

            // Update language from AI parse result
            LanguageManager.setLanguage(parsed.detectedLanguage)

            runOnUiThread {
                when (parsed.intent) {
                    "no_more", "cancel" -> {
                        if (cart.isEmpty()) {
                            speak("Your cart is empty. What would you like to order?") { startListening() }
                        } else {
                            readCartAndConfirm()
                        }
                    }
                    "confirm" -> {
                        if (currentState == AssistantState.CONFIRMING) {
                            placeOrder()
                        } else {
                            readCartAndConfirm()
                        }
                    }
                    "history" -> readOrderHistory()
                    else -> {
                        // "order" intent
                        if (parsed.items.isEmpty()) {
                            val fallback = keywordFallback(lower)
                            if (fallback != null) {
                                searchAndAskQuantity(fallback)
                            } else {
                                speak("Tell me what you want to order.") { startListening() }
                            }
                        } else if (parsed.items.size == 1) {
                            val item = parsed.items.first()
                            searchAndAskQuantity(item.name, item.quantity, item.unit)
                        } else {
                            lifecycleScope.launch { addMultipleItemsToCart(parsed.items) }
                        }
                    }
                }
            }
        }
    }

    // ─── ASKING_MORE HANDLER ──────────────────────────────────

    private fun handleAskingMore(lower: String, originalText: String) {
        when {
            // User wants to add more items
            lower.contains("yes")  || lower.contains("add")    ||
            lower.contains("more") || lower.contains("also")   ||
            lower.contains("और")   || lower.contains("aur")    -> {
                currentState = AssistantState.LISTENING
                speak("What else would you like?") { startListening() }
            }

            // User is done
            isNoMoreIntent(lower) -> readCartAndConfirm()

            // Could be a new item order directly (e.g., user says "sugar" instead of "yes")
            else -> {
                // Try to parse as a new order item
                lifecycleScope.launch {
                    val parsed = AIOrderParser.parse(originalText)
                    runOnUiThread {
                        when {
                            parsed.intent == "no_more" -> readCartAndConfirm()
                            parsed.items.isNotEmpty()  -> {
                                // They gave us a new item directly
                                currentState = AssistantState.LISTENING
                                handleOrderIntent(originalText, lower)
                            }
                            else -> {
                                speak("Should I add more items or place the order?") { startListening() }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── NO-MORE INTENT DETECTION ─────────────────────────────

    private fun isNoMoreIntent(lower: String): Boolean {
        val noMorePhrases = listOf(
            "no", "done", "nothing", "that's all", "thats all", "finish",
            "place order", "checkout", "order karo", "place it",
            // Hindi
            "नहीं", "नही", "बस", "हो गया", "इतना ही", "kuch nahi",
            "और कुछ नहीं", "और नहीं", "नहीं चाहिए", "bas", "nahi",
            "aur nahi", "khatam", "theek hai", "order kar do"
        )
        return noMorePhrases.any { lower.contains(it) }
    }

    // ─── KEYWORD FALLBACK ─────────────────────────────────────

    private fun keywordFallback(lower: String): String? = when {
        lower.contains("rice")    || lower.contains("चावल")   -> "rice"
        lower.contains("oil")     || lower.contains("तेल")    -> "oil"
        lower.contains("sugar")   || lower.contains("चीनी")   -> "sugar"
        lower.contains("wheat")   || lower.contains("गेहूं")  -> "wheat"
        lower.contains("dal")     || lower.contains("दाल")    -> "dal"
        lower.contains("salt")    || lower.contains("नमक")    -> "salt"
        lower.contains("milk")    || lower.contains("दूध")    -> "milk"
        lower.contains("flour")   || lower.contains("atta")   -> "wheat flour"
        lower.contains("tea")     || lower.contains("चाय")    -> "tea"
        lower.contains("coffee")                               -> "coffee"
        lower.contains("ghee")                                 -> "ghee"
        lower.contains("butter")                               -> "butter"
        lower.contains("paneer")                               -> "paneer"
        lower.contains("eggs")    || lower.contains("egg")    -> "eggs"
        lower.contains("bread")                                -> "bread"
        lower.contains("sooji")   || lower.contains("semolina") -> "semolina"
        lower.contains("maida")                                -> "maida"
        lower.contains("poha")                                 -> "poha"
        lower.contains("chana")                                -> "chickpeas"
        lower.contains("rajma")                                -> "kidney beans"
        lower.contains("turmeric") || lower.contains("haldi") -> "turmeric"
        lower.contains("jeera")   || lower.contains("cumin")  -> "cumin"
        else -> null
    }

    // ─── LOGIN HELPER ─────────────────────────────────────────

    private suspend fun doLogin(email: String, password: String) {
        UserSessionManager.login(email, password)
            .fold(
                onSuccess = { profile ->
                    runOnUiThread {
                        currentState = AssistantState.LISTENING
                        val firstName = profile.full_name?.split(" ")?.first() ?: "there"
                        val history   = UserSessionManager.purchaseHistory
                        val greeting  = if (history.isNotEmpty()) {
                            "Welcome back $firstName! Last time you ordered ${history.first().product_name}. What would you like?"
                        } else {
                            "Welcome back $firstName! What would you like to order?"
                        }
                        speak(greeting) { startListening() }
                    }
                },
                onFailure = {
                    runOnUiThread {
                        speak("Login failed. Please check your email and password.") {
                            startWakeWordListening()
                        }
                    }
                }
            )
    }

    // ─── PRODUCT SEARCH ───────────────────────────────────────

    private fun searchAndAskQuantity(
        itemName: String,
        qty: Int    = 0,
        unit: String? = null
    ) {
        lifecycleScope.launch {
            val product = apiClient.searchProduct(itemName)
            runOnUiThread {
                if (product != null) {
                    tempProduct = product
                    if (qty > 0) {
                        val displayUnit = unit ?: product.unit ?: ""
                        val displayQty  = if (displayUnit.isNotBlank()) "$qty $displayUnit" else "$qty"
                        cart.add(CartItem(product, qty))
                        speak("Added $displayQty ${product.name}. Anything else?") {
                            currentState = AssistantState.ASKING_MORE
                            startListening()
                        }
                    } else {
                        currentState = AssistantState.ASKING_QUANTITY
                        speak("How much ${product.name}?") { startListening() }
                    }
                } else {
                    speak("I couldn't find $itemName. What else would you like?") {
                        startListening()
                    }
                }
            }
        }
    }

    private suspend fun addMultipleItemsToCart(
        items: List<com.demo.butler_voice_app.ai.ParsedItem>
    ) {
        val found    = mutableListOf<String>()
        val notFound = mutableListOf<String>()

        for (item in items) {
            val product = apiClient.searchProduct(item.name)
            if (product != null) {
                cart.add(CartItem(product, item.quantity))
                val unit = item.unit ?: product.unit ?: ""
                val qty  = if (unit.isNotBlank()) "${item.quantity} $unit" else "${item.quantity}"
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
            val items       = apiClient.getOrderItems(lastOrderId)

            if (items.isEmpty()) {
                runOnUiThread {
                    speak("Couldn't load your last order. What would you like?") { startListening() }
                }
                return@launch
            }

            for (item in items) {
                val product = apiClient.searchProduct(item.product_name)
                if (product != null) cart.add(CartItem(product, item.quantity))
            }

            val summary = items.joinToString(", ") { "${it.quantity} ${it.product_name}" }
            runOnUiThread {
                currentState = AssistantState.CONFIRMING
                speak("Repeating your last order: $summary. Shall I place it?") { startListening() }
            }
        }
    }

    // ─── CART ─────────────────────────────────────────────────

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) {
            speak("Your cart is empty. What would you like to order?") {
                currentState = AssistantState.LISTENING
                startListening()
            }
            return
        }
        val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.CONFIRMING
        speak("You ordered $summary. Shall I place the order?") { startListening() }
    }

    // ─── PLACE ORDER ──────────────────────────────────────────

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId()
                if (userId == null) {
                    speak("Session expired. Say Hey Butler to start.") { startWakeWordListening() }
                    return@launch
                }

                val orderResult = apiClient.createOrder(cart, userId)
                val shortId     = orderResult.id.takeLast(6).uppercase()
                val firstName   = UserSessionManager.currentProfile?.full_name
                    ?.split(" ")?.first() ?: ""

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
                runOnUiThread {
                    speak("Sorry, couldn't place your order. Try again.") { startWakeWordListening() }
                }
            }
        }
    }

    // ─── UTILITIES ────────────────────────────────────────────

    private fun extractQuantity(text: String): Int {
        val wordNumbers = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5,
            "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5
        )
        return Regex("\\d+").find(text)?.value?.toIntOrNull()
            ?: wordNumbers.entries.firstOrNull { text.lowercase().contains(it.key) }?.value
            ?: 1
    }

    private fun setUiState(s: ButlerUiState) = runOnUiThread { uiState.value = s }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()

        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)

            Log.d("Butler", "Original: $text")
            Log.d("Butler", "Translated ($lang): $finalText")

            runOnUiThread {
                setUiState(ButlerUiState.Speaking(finalText))
                ttsManager.speak(
                    text     = finalText,
                    language = lang,
                    onDone   = { onDone?.invoke() }
                )
            }
        }
    }
}
