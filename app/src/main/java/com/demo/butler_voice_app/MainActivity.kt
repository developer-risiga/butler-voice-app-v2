package com.demo.butler_voice_app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
import com.demo.butler_voice_app.api.SessionStore
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.ui.ButlerScreen
import com.demo.butler_voice_app.ui.ButlerUiState
import com.demo.butler_voice_app.ui.CartDisplayItem
import com.demo.butler_voice_app.voice.SarvamSTTManager
import kotlinx.coroutines.launch

enum class AssistantState {
    IDLE, CHECKING_AUTH,
    ASKING_IS_NEW_USER, ASKING_NAME, ASKING_EMAIL, ASKING_PASSWORD,
    LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING
}

class MainActivity : ComponentActivity() {

    private val uiState      = mutableStateOf<ButlerUiState>(ButlerUiState.Idle)
    private var tempName     = ""
    private var tempEmail    = ""

    private lateinit var ttsManager : TTSManager
    private lateinit var sarvamSTT  : SarvamSTTManager
    private lateinit var porcupine  : WakeWordManager

    private val apiClient     = ApiClient()
    private val cart          = mutableListOf<CartItem>()
    private var tempProduct   : ApiClient.Product? = null
    private var currentState  = AssistantState.IDLE
    private var sttRetryCount = 0
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
        audioManager.mode             = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true

        SessionStore.init(this)

        sarvamSTT = SarvamSTTManager(this, BuildConfig.SARVAM_API_KEY)

        porcupine = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) {
            runOnUiThread { onWakeWordDetected() }
        }

        ttsManager = TTSManager(
            context          = this,
            elevenLabsApiKey = BuildConfig.ELEVENLABS_API_KEY,
            voiceId          = "RwXLkVKnRloV1UPh3Ccx"
        )

        ttsManager.init { checkMicPermission() }

        lifecycleScope.launch {
            try { apiClient.searchProduct("rice"); Log.d("Butler", "Cache warmed") }
            catch (_: Exception) {}
        }
    }

    override fun onPause()   { super.onPause();   porcupine.stop(); sarvamSTT.stop() }
    override fun onDestroy() { super.onDestroy(); porcupine.stop(); sarvamSTT.stop(); ttsManager.shutdown() }
    override fun onResume()  { super.onResume();  try { startLockTask() } catch (_: Exception) {} }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), recordRequestCode)
        } else {
            startWakeWordListening()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startWakeWordListening()
    }

    private fun startWakeWordListening() {
        currentState  = AssistantState.IDLE
        sttRetryCount = 0
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

        lifecycleScope.launch {
            val restored = UserSessionManager.tryRestoreSession()
            runOnUiThread {
                if (restored && UserSessionManager.currentProfile != null) {
                    val name    = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
                    val history = UserSessionManager.purchaseHistory
                    currentState = AssistantState.LISTENING
                    AnalyticsManager.logSessionStart(UserSessionManager.currentUserId(), LanguageManager.getLanguage())
                    val greeting = if (history.isNotEmpty()) {
                        "Welcome back $name! Last time you ordered ${history.first().product_name}. What would you like today?"
                    } else {
                        "Welcome back $name! What would you like to order?"
                    }
                    speak(greeting) { startListening() }
                } else {
                    currentState = AssistantState.ASKING_IS_NEW_USER
                    speak("Welcome! Are you a new customer or have you ordered before?") { startListening() }
                }
            }
        }
    }

    private fun startListening() {
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")

        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")
                    val detectedLang = LanguageDetector.detect(transcript)
                    LanguageManager.setLanguage(detectedLang)
                    Log.d("LANG_DEBUG", "Detected=$detectedLang | Session=${LanguageManager.getLanguage()}")

                    if (transcript.isBlank()) {
                        sttRetryCount++
                        if (sttRetryCount < 2) {
                            Log.d("Butler", "STT empty, silent retry $sttRetryCount")
                            AnalyticsManager.logSttEmpty(currentState.name)
                            startListening()
                        } else {
                            sttRetryCount = 0
                            speak("Sorry, I didn't catch that") { startListening() }
                        }
                        return@runOnUiThread
                    }
                    sttRetryCount = 0
                    setUiState(ButlerUiState.Thinking(transcript))
                    handleCommand(transcript)
                }
            },
            onError = {
                runOnUiThread { speak("Something went wrong") { startWakeWordListening() } }
            }
        )
    }

    private fun handleCommand(text: String) {
        val lower   = text.lowercase().trim()
        val cleaned = lower.replace(Regex("[,।.!?]"), "").trim()

        when (currentState) {

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    cleaned.contains("new")      || cleaned.contains("first")   ||
                    cleaned.contains("register") || cleaned.contains("नया")     ||
                    cleaned.contains("नई") -> {
                        currentState = AssistantState.ASKING_NAME
                        speak("Great! What's your name?") { startListening() }
                    }
                    cleaned.contains("returning") || cleaned.contains("before") ||
                    cleaned.contains("yes")       || cleaned.contains("have")   ||
                    cleaned.contains("पहले")      || cleaned.contains("login")  -> {
                        currentState = AssistantState.ASKING_EMAIL
                        speak("Welcome back! Please say your email.") { startListening() }
                    }
                    else -> speak("Say new customer or returning customer.") { startListening() }
                }
            }

            AssistantState.ASKING_NAME -> {
                lifecycleScope.launch {
                    val translatedForParsing = if (LanguageDetector.detect(text) != "en") translateToEnglish(text) else text
                    val nameText = translatedForParsing.trim()
                        .replace(Regex("my name is\\s*",  RegexOption.IGNORE_CASE), "")
                        .replace(Regex("i am\\s*",        RegexOption.IGNORE_CASE), "")
                        .replace(Regex("mera naam\\s*",   RegexOption.IGNORE_CASE), "")
                        .replace(Regex("my name's\\s*",   RegexOption.IGNORE_CASE), "")
                        .replace(".", "").trim()
                    if (nameText.isBlank() || nameText.contains("@")) {
                        runOnUiThread { speak("Please say just your first name.") { startListening() } }
                        return@launch
                    }
                    tempName = nameText.split(" ").firstOrNull { it.length > 1 }
                        ?.replaceFirstChar { it.uppercase() } ?: nameText.replaceFirstChar { it.uppercase() }
                    runOnUiThread {
                        currentState = AssistantState.ASKING_EMAIL
                        speak("Nice to meet you $tempName! What's your email?") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_EMAIL -> {
                val emailText = text.trim()
                    .replace(Regex("at the rate", RegexOption.IGNORE_CASE), "@")
                    .replace(Regex("\\bat\\b",    RegexOption.IGNORE_CASE), "@")
                    .replace(Regex("dot com",     RegexOption.IGNORE_CASE), ".com")
                    .replace(Regex("dot in",      RegexOption.IGNORE_CASE), ".in")
                    .replace(Regex("dot net",     RegexOption.IGNORE_CASE), ".net")
                    .replace(Regex("\\bdot\\b",   RegexOption.IGNORE_CASE), ".")
                    .replace(" ", "").lowercase().trimEnd('.', ',', '!')
                if (!emailText.contains("@") || !emailText.contains(".") || emailText.length < 6) {
                    speak("I didn't catch a valid email. Please say it again, like john at gmail dot com.") { startListening() }
                    return
                }
                tempEmail = emailText
                Log.d("Butler", "Email captured: $tempEmail")
                currentState = AssistantState.ASKING_PASSWORD
                speak("Got it. Now say your password.") { startListening() }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = text.trim().replace(" ", "").trimEnd('.', ',', '!')
                Log.d("Butler", "Password length: ${password.length}")
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.Thinking("Creating account..."))
                        UserSessionManager.signup(tempEmail, password, tempName, "")
                            .fold(
                                onSuccess = { profile ->
                                    currentState = AssistantState.LISTENING
                                    val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                    AnalyticsManager.logUserAuth("signup", LanguageManager.getLanguage())
                                    speak("Account created! Welcome $firstName! What would you like to order?") { startListening() }
                                },
                                onFailure = { error ->
                                    if (error.message?.contains("user_already_exists") == true ||
                                        error.message?.contains("already registered") == true) {
                                        speak("Account already exists. Logging you in.") {
                                            lifecycleScope.launch { doLogin(tempEmail, password) }
                                        }
                                    } else {
                                        speak("Sorry, couldn't create account. Try again.") { startWakeWordListening() }
                                    }
                                }
                            )
                    } else {
                        setUiState(ButlerUiState.Thinking("Logging in..."))
                        doLogin(tempEmail, password)
                    }
                }
            }

            AssistantState.LISTENING -> handleOrderIntent(text, lower)

            AssistantState.ASKING_QUANTITY -> {
                val qty     = extractQuantity(text)
                val product = tempProduct
                if (product != null) {
                    cart.add(CartItem(product, qty))
                    val suggestion = suggestRelatedItem(product.name)
                    val msg = if (suggestion != null && cart.size == 1) {
                        "Added $qty ${product.name}. Would you also like $suggestion?"
                    } else {
                        "Added $qty ${product.name}. Anything else?"
                    }
                    speakWithCart(msg) { currentState = AssistantState.ASKING_MORE; startListening() }
                } else {
                    speak("Let's try again. What would you like?") { currentState = AssistantState.LISTENING; startListening() }
                }
            }

            AssistantState.ASKING_MORE -> handleAskingMore(cleaned, text)

            AssistantState.CONFIRMING -> {
                when {
                    cleaned.contains("yes")       || cleaned.contains("place")      ||
                    cleaned.contains("confirm")   || cleaned.contains("ok")         ||
                    cleaned.contains("haan")      || cleaned.contains("हाँ")        ||
                    cleaned.contains("हां")       || cleaned.contains("theek")      ||
                    cleaned.contains("kar do")    || cleaned.contains("karo")       ||
                    cleaned.contains("order kar") || cleaned.contains("ऑर्डर कर")  ||
                    cleaned.contains("bilkul")    || cleaned.contains("zaroor")     ||
                    cleaned.contains("done")      || cleaned.contains("proceed")    ||
                    cleaned.contains("चलो")       || cleaned.contains("हा")         -> placeOrder()

                    cleaned.contains("no")        || cleaned.contains("cancel")     ||
                    cleaned.contains("nahi")      || cleaned.contains("नहीं")       ||
                    cleaned.contains("mat")       || cleaned.contains("band kar")   ||
                    cleaned.contains("ruk")       -> {
                        speak("Order cancelled. Goodbye!") {
                            cart.clear(); UserSessionManager.logout(); startWakeWordListening()
                        }
                    }
                    else -> {
                        lifecycleScope.launch {
                            val parsed = AIOrderParser.parse(text)
                            LanguageManager.setLanguage(parsed.detectedLanguage)
                            runOnUiThread {
                                when (parsed.intent) {
                                    "confirm_order" -> placeOrder()
                                    "cancel_order"  -> {
                                        speak("Order cancelled. Goodbye!") {
                                            cart.clear(); UserSessionManager.logout(); startWakeWordListening()
                                        }
                                    }
                                    else -> speak("Say yes to confirm or no to cancel.") { startListening() }
                                }
                            }
                        }
                    }
                }
            }

            else -> {}
        }
    }

    private fun handleOrderIntent(text: String, lower: String) {
        val cleaned = lower.replace(Regex("[,।.!?]"), "").trim()
        if (cleaned.contains("repeat") || cleaned.contains("same as last") || cleaned.contains("previous order")) { repeatLastOrder(); return }
        if (cleaned.contains("my orders") || cleaned.contains("history") || cleaned.contains("what did i order")) { readOrderHistory(); return }
        if (isNoMoreIntent(cleaned)) { readCartAndConfirm(); return }

        lifecycleScope.launch {
            val parsed = AIOrderParser.parse(text)
            LanguageManager.setLanguage(parsed.detectedLanguage)
            runOnUiThread {
                when (parsed.intent) {
                    "finish_order" -> { if (cart.isEmpty()) speak("Your cart is empty. What would you like to order?") { startListening() } else readCartAndConfirm() }
                    "confirm_order" -> { if (currentState == AssistantState.CONFIRMING) placeOrder() else readCartAndConfirm() }
                    "cancel_order"  -> speak("Okay, cancelled.") { startWakeWordListening() }
                    "history"       -> readOrderHistory()
                    "order", "add_more" -> {
                        if (parsed.items.isEmpty()) {
                            val fallback = keywordFallback(cleaned)
                            if (fallback != null) searchAndAskQuantity(fallback)
                            else speak("Tell me what you want to order.") { startListening() }
                        } else if (parsed.items.size == 1) {
                            val item = parsed.items.first()
                            searchAndAskQuantity(item.name, item.quantity, item.unit)
                        } else {
                            lifecycleScope.launch { addMultipleItemsToCart(parsed.items) }
                        }
                    }
                    else -> speak("Tell me what you want to order.") { startListening() }
                }
            }
        }
    }

    private fun handleAskingMore(cleaned: String, originalText: String) {
        when {
            cleaned.contains("yes")  || cleaned.contains("add")   ||
            cleaned.contains("more") || cleaned.contains("also")  ||
            cleaned.contains("और")   || cleaned.contains("aur")   ||
            cleaned.contains("हाँ")  || cleaned.contains("haan")  -> {
                currentState = AssistantState.LISTENING
                speakWithCart("What else would you like?") { startListening() }
            }
            isNoMoreIntent(cleaned) -> readCartAndConfirm()
            else -> {
                lifecycleScope.launch {
                    val parsed = AIOrderParser.parse(originalText)
                    LanguageManager.setLanguage(parsed.detectedLanguage)
                    runOnUiThread {
                        when (parsed.intent) {
                            "finish_order", "cancel_order" -> readCartAndConfirm()
                            "order", "add_more" -> {
                                if (parsed.items.isNotEmpty()) { currentState = AssistantState.LISTENING; handleOrderIntent(originalText, originalText.lowercase()) }
                                else speakWithCart("Should I add more or place the order?") { startListening() }
                            }
                            else -> speakWithCart("Should I add more or place the order?") { startListening() }
                        }
                    }
                }
            }
        }
    }

    private fun isNoMoreIntent(s: String): Boolean {
        val phrases = listOf(
            "no","nope","done","nothing","finish","stop","place order","checkout","place it",
            "that is all","that's all","thats all","i am done","im done",
            "नहीं","नही","बस","हो गया","इतना ही","बस इतना","और नहीं","और कुछ नहीं",
            "कुछ नहीं","नहीं चाहिए","नहीं चाहूंगा","नहीं चाहूँगा","कुछ और नहीं",
            "और कुछ नहीं चाहिए","कुछ और नहीं चाहिए","नहीं बस",
            "bas","nahi","nahi chahiye","kuch nahi","aur nahi","khatam",
            "order kar do","order karo","kar do","theek hai bas","bas ho gaya","nahi chahhunga"
        )
        return phrases.any { s.contains(it) }
    }

    private fun suggestRelatedItem(productName: String): String? {
        val name = productName.lowercase()
        return when {
            name.contains("rice")  -> "dal"
            name.contains("atta")  || name.contains("flour") -> "oil"
            name.contains("dal")   -> "rice"
            name.contains("milk")  -> "sugar"
            name.contains("bread") -> "butter"
            name.contains("oil")   -> "salt"
            else                   -> null
        }
    }

    private fun keywordFallback(s: String): String? = when {
        s.contains("rice")     || s.contains("चावल")     -> "rice"
        s.contains("oil")      || s.contains("तेल")      -> "oil"
        s.contains("sugar")    || s.contains("चीनी")     -> "sugar"
        s.contains("wheat")    || s.contains("गेहूं")    -> "wheat"
        s.contains("dal")      || s.contains("दाल")      -> "dal"
        s.contains("salt")     || s.contains("नमक")      -> "salt"
        s.contains("milk")     || s.contains("दूध")      -> "milk"
        s.contains("flour")    || s.contains("atta")     -> "wheat flour"
        s.contains("tea")      || s.contains("चाय")      -> "tea"
        s.contains("coffee")                              -> "coffee"
        s.contains("ghee")                               -> "ghee"
        s.contains("butter")                             -> "butter"
        s.contains("paneer")                             -> "paneer"
        s.contains("eggs")     || s.contains("egg")      -> "eggs"
        s.contains("bread")                              -> "bread"
        s.contains("sooji")    || s.contains("semolina") -> "semolina"
        s.contains("maida")                              -> "maida"
        s.contains("poha")                               -> "poha"
        s.contains("chana")                              -> "chickpeas"
        s.contains("rajma")                              -> "kidney beans"
        s.contains("turmeric") || s.contains("haldi")   -> "turmeric"
        s.contains("jeera")    || s.contains("cumin")    -> "cumin"
        else -> null
    }

    private suspend fun doLogin(email: String, password: String) {
        UserSessionManager.login(email, password).fold(
            onSuccess = { profile ->
                runOnUiThread {
                    currentState = AssistantState.LISTENING
                    val firstName = profile.full_name?.split(" ")?.first() ?: "there"
                    val history   = UserSessionManager.purchaseHistory
                    AnalyticsManager.logUserAuth("login", LanguageManager.getLanguage())
                    val greeting  = if (history.isNotEmpty()) {
                        "Welcome back $firstName! Last time you ordered ${history.first().product_name}. What would you like?"
                    } else { "Welcome back $firstName! What would you like to order?" }
                    speak(greeting) { startListening() }
                }
            },
            onFailure = {
                runOnUiThread { speak("Login failed. Please check your email and password.") { startWakeWordListening() } }
            }
        )
    }

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        lifecycleScope.launch {
            val product = apiClient.searchProduct(itemName)
            runOnUiThread {
                if (product != null) {
                    tempProduct = product
                    if (qty > 0) {
                        val displayUnit = unit ?: product.unit ?: ""
                        val displayQty  = if (displayUnit.isNotBlank()) "$qty $displayUnit" else "$qty"
                        cart.add(CartItem(product, qty))
                        val suggestion = suggestRelatedItem(product.name)
                        val msg = if (suggestion != null && cart.size == 1) {
                            "Added $displayQty ${product.name}. Would you also like $suggestion?"
                        } else { "Added $displayQty ${product.name}. Anything else?" }
                        speakWithCart(msg) { currentState = AssistantState.ASKING_MORE; startListening() }
                    } else {
                        currentState = AssistantState.ASKING_QUANTITY
                        speak("How much ${product.name}?") { startListening() }
                    }
                } else {
                    AnalyticsManager.logItemNotFound(itemName)
                    speak("I couldn't find $itemName. What else would you like?") { startListening() }
                }
            }
        }
    }

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>(); val notFound = mutableListOf<String>()
        for (item in items) {
            val product = apiClient.searchProduct(item.name)
            if (product != null) {
                cart.add(CartItem(product, item.quantity))
                val u = item.unit ?: product.unit ?: ""
                found.add("${if (u.isNotBlank()) "${item.quantity} $u" else "${item.quantity}"} ${product.name}")
            } else { notFound.add(item.name); AnalyticsManager.logItemNotFound(item.name) }
        }
        runOnUiThread {
            val msg = if (notFound.isEmpty()) "Added ${found.joinToString(", ")}. Anything else?"
                      else "Added ${found.joinToString(", ")}. Couldn't find ${notFound.joinToString(", ")}. Anything else?"
            speakWithCart(msg) { currentState = AssistantState.ASKING_MORE; startListening() }
        }
    }

    private fun readOrderHistory() {
        val history = UserSessionManager.purchaseHistory
        if (history.isEmpty()) speak("You haven't ordered anything yet. What would you like?") { startListening() }
        else speak("Your recent orders include ${history.take(3).joinToString(", ") { it.product_name }}. Would you like to order any?") { startListening() }
    }

    private fun repeatLastOrder() {
        lifecycleScope.launch {
            val userId = UserSessionManager.currentUserId() ?: return@launch
            val orders = apiClient.getOrderHistory(userId)
            if (orders.isEmpty()) { runOnUiThread { speak("No previous orders. What would you like?") { startListening() } }; return@launch }
            val items = apiClient.getOrderItems(orders.first().id)
            if (items.isEmpty()) { runOnUiThread { speak("Couldn't load your last order. What would you like?") { startListening() } }; return@launch }
            for (item in items) { apiClient.searchProduct(item.product_name)?.let { cart.add(CartItem(it, item.quantity)) } }
            val summary = items.joinToString(", ") { "${it.quantity} ${it.product_name}" }
            runOnUiThread { currentState = AssistantState.CONFIRMING; speakWithCart("Repeating your last order: $summary. Shall I place it?") { startListening() } }
        }
    }

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) { speak("Your cart is empty. What would you like to order?") { currentState = AssistantState.LISTENING; startListening() }; return }
        val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.CONFIRMING
        speakWithCart("You ordered $summary. Shall I place the order?") { startListening() }
    }

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId()
                if (userId == null) { speak("Session expired. Say Hey Butler to start.") { startWakeWordListening() }; return@launch }
                val orderResult = apiClient.createOrder(cart, userId)
                val shortId     = if (orderResult.public_id.isNotBlank()) orderResult.public_id else orderResult.id.takeLast(6).uppercase()
                val firstName   = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
                Log.d("Butler", "Order placed: ${orderResult.id}")
                AnalyticsManager.logOrderPlaced(orderResult.id, orderResult.total_amount, cart.size, LanguageManager.getLanguage())
                setUiState(ButlerUiState.OrderDone(shortId, orderResult.total_amount, "placed"))
                val farewell = if (firstName.isNotBlank()) "Order placed $firstName! Your ID is $shortId. Thank you, goodbye!"
                              else "Order placed! Your ID is $shortId. Thank you!"
                speak(farewell) {
                    cart.clear()
                    UserSessionManager.logout()  // soft logout — session stays on disk
                    Handler(Looper.getMainLooper()).postDelayed({ startWakeWordListening() }, 2500)
                }
            } catch (e: Exception) {
                Log.e("Butler", "Order failed: ${e.message}")
                runOnUiThread {
                    currentState = AssistantState.CONFIRMING
                    speakWithCart("Sorry, there was a network issue. Say yes to try again or no to cancel.") { startListening() }
                }
            }
        }
    }

    private fun extractQuantity(text: String): Int {
        val w = mapOf("one" to 1,"two" to 2,"three" to 3,"four" to 4,"five" to 5,
            "six" to 6,"seven" to 7,"eight" to 8,"nine" to 9,"ten" to 10,
            "एक" to 1,"दो" to 2,"तीन" to 3,"चार" to 4,"पाँच" to 5,
            "ek" to 1,"do" to 2,"teen" to 3,"char" to 4,"paanch" to 5)
        return Regex("\\d+").find(text)?.value?.toIntOrNull()
            ?: w.entries.firstOrNull { text.lowercase().contains(it.key) }?.value ?: 1
    }

    private fun setUiState(s: ButlerUiState) = runOnUiThread { uiState.value = s }

    private fun speak(text: String, onDone: (() -> Unit)? = null) = speakWithCart(text, showCart = false, onDone = onDone)

    private fun speakWithCart(text: String, showCart: Boolean = true, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("Butler", "Original: $text")
            Log.d("Butler", "Translated ($lang): $finalText")
            val cartItems = if (showCart && cart.isNotEmpty()) {
                cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
            } else emptyList()
            runOnUiThread {
                setUiState(ButlerUiState.Speaking(finalText, cart = cartItems))
                ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() })
            }
        }
    }

    private suspend fun translateToEnglish(text: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = org.json.JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("role", "user")
                            put("content", "Translate to English. Return ONLY the translated text, nothing else: $text")
                        })
                    })
                    put("max_tokens", 50); put("temperature", 0.1)
                }.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body).build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                val resBody  = response.body?.string() ?: return@withContext text
                org.json.JSONObject(resBody).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
            } catch (e: Exception) { Log.e("Butler", "translateToEnglish failed: ${e.message}"); text }
        }
    }
}
