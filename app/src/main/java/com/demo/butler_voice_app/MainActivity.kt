package com.demo.butler_voice_app

import android.content.Intent
import com.demo.butler_voice_app.api.PaymentManager
import com.demo.butler_voice_app.api.SmartReorderManager
import com.demo.butler_voice_app.api.ReorderSuggestion
import com.demo.butler_voice_app.ui.PaymentActivity
import com.demo.butler_voice_app.ui.AuthActivity
import com.demo.butler_voice_app.ui.QRPaymentActivity
import com.demo.butler_voice_app.ui.DeliveryTrackingActivity
import com.demo.butler_voice_app.ui.RESULT_MANUAL_LOGIN
import com.demo.butler_voice_app.ui.RESULT_USE_VOICE
import com.demo.butler_voice_app.ui.RESULT_PAY_CARD
import com.demo.butler_voice_app.ui.RESULT_PAY_QR
import com.demo.butler_voice_app.ui.RESULT_PAY_CANCEL
import com.demo.butler_voice_app.ui.RESULT_QR_PAID
import com.demo.butler_voice_app.ui.RESULT_QR_CANCEL
import com.demo.butler_voice_app.ui.RESULT_GOOGLE_AUTH
import com.demo.butler_voice_app.ui.EXTRA_EMAIL
import com.demo.butler_voice_app.ui.EXTRA_PASSWORD
import com.demo.butler_voice_app.ui.EXTRA_NAME
import com.demo.butler_voice_app.ui.EXTRA_IS_NEW_USER
import com.demo.butler_voice_app.ui.EXTRA_ORDER_TOTAL
import com.demo.butler_voice_app.ui.EXTRA_ORDER_SUMMARY
import com.demo.butler_voice_app.ui.EXTRA_GOOGLE_TOKEN
import com.demo.butler_voice_app.ui.EXTRA_GOOGLE_EMAIL
import com.demo.butler_voice_app.ui.EXTRA_GOOGLE_NAME
import androidx.activity.result.contract.ActivityResultContracts
import com.demo.butler_voice_app.ai.MultilingualMatcher
import com.demo.butler_voice_app.ai.IndianLanguageProcessor
import com.demo.butler_voice_app.ai.EmailPasswordParser
import com.demo.butler_voice_app.api.SmartProductRepository
import com.demo.butler_voice_app.api.SupabaseClient
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

// ── States ─────────────────────────────────────────────────────────────────────
enum class AssistantState {
    IDLE, CHECKING_AUTH,
    ASKING_IS_NEW_USER, ASKING_NAME, ASKING_EMAIL, ASKING_PASSWORD,
    LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING,
    REORDER_CONFIRM,
    EDITING_CART
}

class MainActivity : ComponentActivity() {

    // ─── FIELDS ───────────────────────────────────────────────────────────────
    private var pendingOrderTotal: Double = 0.0
    private var pendingOrderSummary: String = ""
    private var emailRetryCount = 0
    private var userLocation: android.location.Location? = null
    private lateinit var locationManager: android.location.LocationManager
    private val productRepo   = SmartProductRepository(SupabaseClient)
    private val uiState       = mutableStateOf<ButlerUiState>(ButlerUiState.Idle)
    private var tempName      = ""
    private var tempEmail     = ""
    private var sttRetryCount = 0

    private var pendingReorderSuggestions: List<ReorderSuggestion> = emptyList()
    private var lastOrderId   = ""
    private var lastPublicId  = ""
    private var lastOrderTotal = 0.0

    private lateinit var ttsManager : TTSManager
    private lateinit var sarvamSTT  : SarvamSTTManager
    private lateinit var porcupine  : WakeWordManager

    private val apiClient    = ApiClient()
    private val cart         = mutableListOf<CartItem>()
    private var tempProduct  : ApiClient.Product? = null
    private var currentState = AssistantState.IDLE
    private val recordRequestCode = 101

    // ─── LAUNCHERS ────────────────────────────────────────────────────────────

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_MANUAL_LOGIN -> {
                val email = result.data?.getStringExtra(EXTRA_EMAIL) ?: return@registerForActivityResult
                val pass  = result.data?.getStringExtra(EXTRA_PASSWORD) ?: return@registerForActivityResult
                val name  = result.data?.getStringExtra(EXTRA_NAME) ?: ""
                val isNew = result.data?.getBooleanExtra(EXTRA_IS_NEW_USER, true) ?: true
                // ✅ Re-enter kiosk mode BEFORE speaking
                try { startLockTask() } catch (_: Exception) {}
                lifecycleScope.launch {
                    if (isNew) {
                        val displayName = name.ifBlank {
                            email.substringBefore("@").replaceFirstChar { it.uppercase() }
                        }
                        UserSessionManager.signup(email, pass, displayName, "").fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: displayName
                                speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)) { startListening() }
                            },
                            onFailure = { err ->
                                if (err.message?.contains("already") == true) {
                                    speak("Account already exists. Logging you in.") {
                                        lifecycleScope.launch { doLogin(email, pass) }
                                    }
                                } else {
                                    speak("Account creation failed. Please try again.") { startWakeWordListening() }
                                }
                            }
                        )
                    } else { doLogin(email, pass) }
                }
            }
            RESULT_GOOGLE_AUTH -> {
                val gEmail = result.data?.getStringExtra(EXTRA_GOOGLE_EMAIL) ?: ""
                val gName  = result.data?.getStringExtra(EXTRA_GOOGLE_NAME) ?: ""
                if (gEmail.isBlank()) return@registerForActivityResult
                // ✅ Re-enter kiosk mode BEFORE speaking
                try { startLockTask() } catch (_: Exception) {}
                lifecycleScope.launch {
                    val googlePass  = "Butler_G_${gEmail.hashCode().toString().takeLast(8)}"
                    val displayName = gName.ifBlank { gEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }
                    UserSessionManager.signup(gEmail, googlePass, displayName, "").fold(
                        onSuccess = { profile ->
                            currentState = AssistantState.LISTENING
                            val firstName = profile.full_name?.split(" ")?.first() ?: displayName.split(" ").first()
                            AnalyticsManager.logUserAuth("google", LanguageManager.getLanguage())
                            speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)) { startListening() }
                        },
                        onFailure = { doLogin(gEmail, googlePass) }
                    )
                }
            }
            RESULT_USE_VOICE -> {
                // ✅ Re-enter kiosk mode BEFORE speaking
                try { startLockTask() } catch (_: Exception) {}
                currentState = AssistantState.ASKING_IS_NEW_USER
                // ✅ 300ms delay lets lock task settle so TTS audio is not cut off
                Handler(Looper.getMainLooper()).postDelayed({
                    speak("Welcome! Are you a new customer, or have you ordered before?") { startListening() }
                }, 300)
            }
        }
        // ❌ NO startLockTask() here at the bottom — already done per-branch above
    }

    private val paymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // ✅ Re-enter kiosk mode when returning from PaymentActivity
        try { startLockTask() } catch (_: Exception) {}
        // ✅ Small delay so lock task settles before TTS speaks
        Handler(Looper.getMainLooper()).postDelayed({
            when (result.resultCode) {
                RESULT_PAY_CARD -> {
                    val card = PaymentManager.getSavedCard(this)
                    if (card != null) {
                        speak("Paying ₹%.0f with your ${card.network} card ending ${card.last4}. Placing order now.".format(pendingOrderTotal)) {
                            placeOrder()
                        }
                    } else placeOrder()
                }
                RESULT_PAY_QR -> {
                    val intent = Intent(this, QRPaymentActivity::class.java).apply {
                        putExtra(EXTRA_ORDER_TOTAL, pendingOrderTotal)
                        putExtra(EXTRA_ORDER_SUMMARY, pendingOrderSummary)
                    }
                    // ✅ Stop lock task before launching QRPaymentActivity
                    try { stopLockTask() } catch (_: Exception) {}
                    qrLauncher.launch(intent)
                }
                RESULT_PAY_CANCEL -> {
                    currentState = AssistantState.CONFIRMING
                    speak("Payment cancelled. Say yes when ready to pay.") { startListening() }
                }
            }
        }, 300)
    }

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // ✅ Re-enter kiosk mode when returning from QRPaymentActivity
        try { startLockTask() } catch (_: Exception) {}
        // ✅ Small delay so lock task settles before TTS speaks
        Handler(Looper.getMainLooper()).postDelayed({
            when (result.resultCode) {
                RESULT_QR_PAID -> speak("Payment received! Placing your order now.") { placeOrder() }
                RESULT_QR_CANCEL -> {
                    currentState = AssistantState.CONFIRMING
                    speak("Payment cancelled. Say yes when ready to pay.") { startListening() }
                }
            }
        }, 300)
    }

    // ─── LIFECYCLE ────────────────────────────────────────────────────────────

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
        startLocationUpdates()
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

    override fun onResume() {
        super.onResume()
        // ✅ Only lock task when truly idle — NOT when returning from auth/payment/delivery
        if (currentState == AssistantState.IDLE) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    // ─── LOCATION ─────────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER, 30000L, 50f
                ) { location -> userLocation = location }
                userLocation = locationManager.getLastKnownLocation(
                    android.location.LocationManager.GPS_PROVIDER
                ) ?: locationManager.getLastKnownLocation(
                    android.location.LocationManager.NETWORK_PROVIDER
                )
            }
        } catch (e: Exception) { Log.e("Butler", "Location error: ${e.message}") }
    }

    // ─── PERMISSIONS ──────────────────────────────────────────────────────────

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION),
                recordRequestCode
            )
        } else { startWakeWordListening() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) { startLocationUpdates(); startWakeWordListening() }
    }

    // ─── WAKE WORD ────────────────────────────────────────────────────────────

    private fun startWakeWordListening() {
        currentState  = AssistantState.IDLE
        sttRetryCount = 0
        emailRetryCount = 0
        cart.clear()
        tempName = ""; tempEmail = ""
        pendingReorderSuggestions = emptyList()
        LanguageManager.reset()
        apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle)
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    // ─── WAKE WORD DETECTED ───────────────────────────────────────────────────

    private fun onWakeWordDetected() {
        currentState = AssistantState.CHECKING_AUTH
        setUiState(ButlerUiState.Thinking("Checking..."))
        lifecycleScope.launch {
            val restored = UserSessionManager.tryRestoreSession()
            runOnUiThread {
                if (restored && UserSessionManager.currentProfile != null) {
                    val name    = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
                    val history = UserSessionManager.purchaseHistory
                    AnalyticsManager.logSessionStart(UserSessionManager.currentUserId(), LanguageManager.getLanguage())

                    if (history.isNotEmpty()) {
                        lifecycleScope.launch {
                            val userId      = UserSessionManager.currentUserId() ?: ""
                            val suggestions = SmartReorderManager.getSuggestions(userId)
                            val smartMsg    = SmartReorderManager.buildReorderGreeting(suggestions, name)

                            runOnUiThread {
                                if (smartMsg != null && suggestions.isNotEmpty()) {
                                    pendingReorderSuggestions = suggestions
                                    currentState = AssistantState.REORDER_CONFIRM
                                    speak(smartMsg) { startListening() }
                                } else {
                                    currentState = AssistantState.LISTENING
                                    val greeting = "Welcome back $name! Last time you ordered ${history.first().product_name}. What would you like today?"
                                    speak(greeting) { startListening() }
                                }
                            }
                        }
                    } else {
                        currentState = AssistantState.LISTENING
                        speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), name)) { startListening() }
                    }
                } else {
                    // ✅ Stop lock task BEFORE launching AuthActivity
                    try { stopLockTask() } catch (_: Exception) {}
                    authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                }
            }
        }
    }

    // ─── STT ──────────────────────────────────────────────────────────────────

    private fun startListening() {
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")
                    val detectedLang = LanguageDetector.detect(transcript)
                    if (transcript.length > 3) {
                        val scriptLang = MultilingualMatcher.detectScript(transcript)
                        val finalLang  = if (scriptLang != "en") scriptLang else detectedLang
                        LanguageManager.setLanguage(finalLang)
                    }
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

    // ─── COMMAND HANDLER ──────────────────────────────────────────────────────

    private fun handleCommand(text: String) {
        val lower   = text.lowercase().trim()
        val cleaned = lower.replace(Regex("[,।.!?؟]"), "").trim()
        val lang    = LanguageManager.getLanguage()

        when (currentState) {

            AssistantState.REORDER_CONFIRM -> {
                when {
                    MultilingualMatcher.isYes(cleaned) ||
                            IndianLanguageProcessor.detectIntent(cleaned) == "confirm" -> {
                        lifecycleScope.launch {
                            for (suggestion in pendingReorderSuggestions) {
                                val product = apiClient.searchProduct(suggestion.productName)
                                product?.let { cart.add(CartItem(it, suggestion.avgQty)) }
                            }
                            runOnUiThread {
                                if (cart.isNotEmpty()) {
                                    val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
                                    currentState = AssistantState.CONFIRMING
                                    speakWithCart("Perfect! I've added $summary to your cart. Shall I place the order?") { startListening() }
                                } else {
                                    currentState = AssistantState.LISTENING
                                    speak("I couldn't find those products right now. What would you like to order?") { startListening() }
                                }
                            }
                        }
                    }
                    MultilingualMatcher.isNo(cleaned) ||
                            IndianLanguageProcessor.detectIntent(cleaned) == "cancel" -> {
                        pendingReorderSuggestions = emptyList()
                        currentState = AssistantState.LISTENING
                        speak("No problem! What would you like to order today?") { startListening() }
                    }
                    else -> {
                        pendingReorderSuggestions = emptyList()
                        currentState = AssistantState.LISTENING
                        handleOrderIntent(text, lower)
                    }
                }
            }

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    cleaned.contains("new") || cleaned.contains("first") ||
                            cleaned.contains("register") || cleaned.contains("नया") ||
                            cleaned.contains("नई") || cleaned.contains("నవీన") ||
                            cleaned.contains("నొత్త") || cleaned.contains("புதிய") ||
                            cleaned.contains("ਨਵਾ") || cleaned.contains("ନୂଆ") -> {
                        currentState = AssistantState.ASKING_NAME
                        speak("Great! What's your name?") { startListening() }
                    }
                    cleaned.contains("returning") || cleaned.contains("before") ||
                            cleaned.contains("yes") || cleaned.contains("have") ||
                            cleaned.contains("पहले") || cleaned.contains("login") ||
                            cleaned.contains("जुना") || cleaned.contains("ಹಿಂದೆ") ||
                            cleaned.contains("முன்பு") -> {
                        currentState = AssistantState.ASKING_EMAIL
                        speak(EmailPasswordParser.getEmailPrompt(lang)) { startListening() }
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
                        .replace(Regex("मेरा नाम\\s*",    RegexOption.IGNORE_CASE), "")
                        .replace(Regex("నా పేరు\\s*",     RegexOption.IGNORE_CASE), "")
                        .replace(Regex("என் பெயர்\\s*",   RegexOption.IGNORE_CASE), "")
                        .replace(Regex("ನನ್ನ ಹೆಸರು\\s*",  RegexOption.IGNORE_CASE), "")
                        .replace(".", "").trim()
                    if (nameText.isBlank() || nameText.contains("@")) {
                        runOnUiThread { speak("Please say just your first name.") { startListening() } }
                        return@launch
                    }
                    tempName = nameText.split(" ").firstOrNull { it.length > 1 }
                        ?.replaceFirstChar { it.uppercase() }
                        ?: nameText.replaceFirstChar { it.uppercase() }
                    runOnUiThread {
                        currentState = AssistantState.ASKING_EMAIL
                        speak("Nice to meet you $tempName! ${EmailPasswordParser.getEmailPrompt(lang)}") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_EMAIL -> {
                val parsed = EmailPasswordParser.parseEmail(text)
                if (parsed != null) {
                    tempEmail = parsed
                    emailRetryCount = 0
                    Log.d("Butler", "Email captured: $tempEmail")
                    currentState = AssistantState.ASKING_PASSWORD
                    speak("Got it — $tempEmail. ${EmailPasswordParser.getPasswordPrompt(lang)}") { startListening() }
                } else {
                    emailRetryCount++
                    if (emailRetryCount <= 2) {
                        speak(EmailPasswordParser.getEmailRetryPrompt(lang)) { startListening() }
                    } else {
                        val fallback = EmailPasswordParser.parseEmailBestEffort(text)
                        if (fallback.contains("@")) {
                            tempEmail = fallback
                            emailRetryCount = 0
                            currentState = AssistantState.ASKING_PASSWORD
                            speak("OK, I got $tempEmail. ${EmailPasswordParser.getPasswordPrompt(lang)}") { startListening() }
                        } else {
                            emailRetryCount = 0
                            speak("Let's try a different way. Showing sign-in screen.") {
                                // ✅ Stop lock task before launching AuthActivity from voice flow
                                try { stopLockTask() } catch (_: Exception) {}
                                authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                            }
                        }
                    }
                }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = EmailPasswordParser.parsePassword(text)
                    .ifBlank { text.trim().replace(" ", "").trimEnd('.', ',', '!') }
                Log.d("Butler", "Password length: ${password.length}")
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.Thinking("Creating account..."))
                        UserSessionManager.signup(tempEmail, password, tempName, "").fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                AnalyticsManager.logUserAuth("signup", LanguageManager.getLanguage())
                                speak(IndianLanguageProcessor.getWelcomeGreeting(lang, firstName)) { startListening() }
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
                    val msg = if (suggestion != null && cart.size == 1)
                        "Added $qty ${product.name}. Would you also like $suggestion?"
                    else "Added $qty ${product.name}. Anything else?"
                    speakWithCart(msg) { currentState = AssistantState.ASKING_MORE; startListening() }
                } else {
                    speak("Let's try again. What would you like?") {
                        currentState = AssistantState.LISTENING; startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE -> handleAskingMore(cleaned, text)

            AssistantState.EDITING_CART -> handleCartEdit(cleaned, text)

            AssistantState.CONFIRMING -> {
                val intentFromLang = IndianLanguageProcessor.detectIntent(cleaned)
                when {
                    intentFromLang == "confirm" || MultilingualMatcher.isYes(cleaned) -> placeOrderWithPayment()
                    intentFromLang == "cancel"  || MultilingualMatcher.isNo(cleaned)  -> {
                        speak("Order cancelled. Goodbye!") {
                            cart.clear(); UserSessionManager.logout(); startWakeWordListening()
                        }
                    }
                    isCartEditIntent(cleaned) -> {
                        currentState = AssistantState.EDITING_CART
                        handleCartEdit(cleaned, text)
                    }
                    else -> {
                        lifecycleScope.launch {
                            val parsed = AIOrderParser.parse(text)
                            LanguageManager.setLanguage(parsed.detectedLanguage)
                            runOnUiThread {
                                when (parsed.intent) {
                                    "confirm_order" -> placeOrderWithPayment()
                                    "cancel_order"  -> {
                                        speak("Order cancelled. Goodbye!") {
                                            cart.clear(); UserSessionManager.logout(); startWakeWordListening()
                                        }
                                    }
                                    else -> speak("Say yes to confirm or no to cancel. You can also say remove item or change quantity.") { startListening() }
                                }
                            }
                        }
                    }
                }
            }

            else -> {}
        }
    }

    // ─── VOICE CART EDITING ───────────────────────────────────────────────────

    private fun isCartEditIntent(s: String): Boolean {
        val editPhrases = listOf(
            "remove", "delete", "हटाओ", "हटा दो", "निकाल दो", "తొలగించు", "ತೆಗೆ",
            "change", "update", "बदलो", "మార్చు", "ಬದಲಾಯಿಸಿ",
            "add more", "increase", "ज्यादा", "more"
        )
        return editPhrases.any { s.contains(it) }
    }

    private fun handleCartEdit(cleaned: String, originalText: String) {
        if (cart.isEmpty()) {
            speak("Your cart is empty. What would you like to add?") {
                currentState = AssistantState.LISTENING; startListening()
            }
            return
        }

        val isRemove = cleaned.contains("remove") || cleaned.contains("delete") ||
                cleaned.contains("हटाओ") || cleaned.contains("हटा") ||
                cleaned.contains("निकाल") || cleaned.contains("తొలగించు") ||
                cleaned.contains("ತೆಗೆ") || cleaned.contains("वापस")

        val isChange = cleaned.contains("change") || cleaned.contains("update") ||
                cleaned.contains("बदलो") || cleaned.contains("make it") ||
                cleaned.contains("instead") || cleaned.contains("మార్చు")

        if (isRemove) {
            val removedItem = cart.firstOrNull { item ->
                val words = item.product.name.lowercase().split(" ")
                words.any { w -> cleaned.contains(w) && w.length > 3 }
            }
            if (removedItem != null) {
                cart.remove(removedItem)
                if (cart.isEmpty()) {
                    speak("Removed ${removedItem.product.name}. Your cart is now empty. What would you like to add?") {
                        currentState = AssistantState.LISTENING; startListening()
                    }
                } else {
                    currentState = AssistantState.CONFIRMING
                    speakWithCart("Removed ${removedItem.product.name}. Your cart now has ${cart.size} item${if (cart.size > 1) "s" else ""}. Shall I place the order?") { startListening() }
                }
            } else {
                val cartList = cart.joinToString(", ") { it.product.name.split(" ").take(2).joinToString(" ") }
                speak("I couldn't find that in your cart. You have: $cartList. Which one to remove?") { startListening() }
            }
            return
        }

        if (isChange) {
            val newQty = extractQuantity(cleaned)
            val changedItem = cart.firstOrNull { item ->
                val words = item.product.name.lowercase().split(" ")
                words.any { w -> cleaned.contains(w) && w.length > 3 }
            }
            if (changedItem != null && newQty > 0) {
                val idx = cart.indexOf(changedItem)
                cart[idx] = CartItem(changedItem.product, newQty)
                currentState = AssistantState.CONFIRMING
                speakWithCart("Updated ${changedItem.product.name} to $newQty. Shall I place the order?") { startListening() }
            } else {
                speak("Tell me which item to change and the new quantity. For example, say: change rice to 2.") { startListening() }
            }
            return
        }

        currentState = AssistantState.CONFIRMING
        readCartAndConfirm()
    }

    // ─── ORDER INTENT ─────────────────────────────────────────────────────────

    private fun handleOrderIntent(text: String, lower: String) {
        val cleaned = lower.replace(Regex("[,।.!?]"), "").trim()

        if (cleaned.contains("repeat") || cleaned.contains("same as last") ||
            cleaned.contains("previous order")) { repeatLastOrder(); return }
        if (cleaned.contains("my orders") || cleaned.contains("history") ||
            cleaned.contains("what did i order")) { readOrderHistory(); return }
        if (isNoMoreIntent(cleaned)) { readCartAndConfirm(); return }

        if (cart.isNotEmpty() && isCartEditIntent(cleaned)) {
            currentState = AssistantState.EDITING_CART
            handleCartEdit(cleaned, text)
            return
        }

        val regionalProduct = IndianLanguageProcessor.normalizeProduct(cleaned)
        if (regionalProduct != cleaned && regionalProduct.isNotBlank()) {
            Log.d("Butler", "Regional product: '$cleaned' → '$regionalProduct'")
            searchAndAskQuantity(regionalProduct)
            return
        }

        lifecycleScope.launch {
            val parsed = AIOrderParser.parse(text)
            LanguageManager.setLanguage(parsed.detectedLanguage)
            runOnUiThread {
                when (parsed.intent) {
                    "finish_order" -> {
                        if (cart.isEmpty()) speak("Your cart is empty. What would you like to order?") { startListening() }
                        else readCartAndConfirm()
                    }
                    "confirm_order" -> {
                        if (currentState == AssistantState.CONFIRMING) placeOrderWithPayment()
                        else readCartAndConfirm()
                    }
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

    // ─── ASKING MORE ──────────────────────────────────────────────────────────

    private fun handleAskingMore(cleaned: String, originalText: String) {
        when {
            MultilingualMatcher.isYes(cleaned) ||
                    cleaned.contains("add") || cleaned.contains("more") ||
                    cleaned.contains("और") || cleaned.contains("aur") -> {
                currentState = AssistantState.LISTENING
                speakWithCart("What else would you like?") { startListening() }
            }
            isNoMoreIntent(cleaned) -> readCartAndConfirm()
            isCartEditIntent(cleaned) -> {
                currentState = AssistantState.EDITING_CART
                handleCartEdit(cleaned, originalText)
            }
            else -> {
                lifecycleScope.launch {
                    val parsed = AIOrderParser.parse(originalText)
                    LanguageManager.setLanguage(parsed.detectedLanguage)
                    runOnUiThread {
                        when (parsed.intent) {
                            "finish_order", "cancel_order" -> readCartAndConfirm()
                            "order", "add_more" -> {
                                if (parsed.items.isNotEmpty()) {
                                    currentState = AssistantState.LISTENING
                                    handleOrderIntent(originalText, originalText.lowercase())
                                } else speakWithCart("Should I add more or place the order?") { startListening() }
                            }
                            else -> speakWithCart("Should I add more or place the order?") { startListening() }
                        }
                    }
                }
            }
        }
    }

    // ─── PRODUCT SEARCH ───────────────────────────────────────────────────────

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        lifecycleScope.launch {
            val recs = productRepo.getTopRecommendations(itemName, userLocation)
            if (recs.isNotEmpty()) {
                runOnUiThread { setUiState(ButlerUiState.ShowingRecommendations(itemName, recs)) }
                val readout = "I found ${recs.size} options for $itemName. " +
                        recs.mapIndexed { i, r -> "${i + 1}: ${r.productName} at ${r.priceLabel}" }
                            .joinToString(". ") + ". Say 1, 2, or 3 to pick."
                speakKeepingRecsVisible(readout) {
                    sarvamSTT.startListening(
                        onResult = { spoken ->
                            runOnUiThread {
                                if (spoken.lowercase().trim().isBlank()) {
                                    speakKeepingRecsVisible("Please say 1, 2, or 3 to pick a product.") {
                                        sarvamSTT.startListening(
                                            onResult = { s2 -> runOnUiThread { handleRecSelection(s2, recs, qty, itemName) } },
                                            onError  = { runOnUiThread { speak("Sorry.") { startListening() } } }
                                        )
                                    }
                                    return@runOnUiThread
                                }
                                handleRecSelection(spoken, recs, qty, itemName)
                            }
                        },
                        onError = { runOnUiThread { speak("Sorry, didn't catch that.") { startListening() } } }
                    )
                }
            } else {
                val product = apiClient.searchProduct(itemName)
                runOnUiThread {
                    if (product != null) {
                        tempProduct = product
                        if (qty > 0) {
                            cart.add(CartItem(product, qty))
                            speakWithCart("Added $qty ${product.name}. Anything else?") {
                                currentState = AssistantState.ASKING_MORE; startListening()
                            }
                        } else {
                            currentState = AssistantState.ASKING_QUANTITY
                            speak("How much ${product.name}?") { startListening() }
                        }
                    } else {
                        speak("I couldn't find $itemName. What else would you like?") { startListening() }
                    }
                }
            }
        }
    }

    private fun handleRecSelection(
        spoken: String,
        recs: List<com.demo.butler_voice_app.api.ProductRecommendation>,
        qty: Int, itemName: String
    ) {
        Log.d("Butler", "handleRecSelection: spoken='$spoken'")
        val numberIndex = MultilingualMatcher.matchNumber(spoken)
        val pick = when {
            numberIndex >= 0                   -> recs.getOrNull(numberIndex)
            MultilingualMatcher.isYes(spoken)  -> recs.first()
            else -> recs.firstOrNull { rec ->
                val recWords    = rec.productName.lowercase().split(" ")
                val spokenWords = spoken.lowercase().split(" ").filter { it.length > 2 }
                spokenWords.any { sw -> recWords.any { rw -> rw.contains(sw) || sw.contains(rw) } }
            }
        }
        if (pick != null) {
            val finalQty = if (qty > 0) qty else 1
            val product  = ApiClient.Product(id = pick.productId, name = pick.productName, price = pick.priceRs, unit = pick.unit)
            cart.add(CartItem(product, finalQty))
            val suggestion = suggestRelatedItem(pick.productName)
            val msg = if (suggestion != null && cart.size == 1)
                "Added ${pick.productName} from ${pick.storeName}. Would you also like $suggestion?"
            else "Added ${pick.productName} from ${pick.storeName}. Anything else?"
            speakWithCart(msg) { currentState = AssistantState.ASKING_MORE; startListening() }
        } else {
            speakKeepingRecsVisible("Please say 1, 2, or 3 to pick.") {
                sarvamSTT.startListening(
                    onResult = { s2 -> runOnUiThread { handleRecSelection(s2, recs, qty, itemName) } },
                    onError  = { runOnUiThread { speak("Sorry.") { startListening() } } }
                )
            }
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>(); val notFound = mutableListOf<String>()
        for (item in items) {
            val searchName = IndianLanguageProcessor.normalizeProduct(item.name).ifBlank { item.name }
            val product    = apiClient.searchProduct(searchName)
            if (product != null) {
                cart.add(CartItem(product, item.quantity))
                found.add("${item.quantity} ${product.name}")
            } else { notFound.add(item.name); AnalyticsManager.logItemNotFound(item.name) }
        }
        runOnUiThread {
            val msg = if (notFound.isEmpty()) "Added ${found.joinToString(", ")}. Anything else?"
            else "Added ${found.joinToString(", ")}. Couldn't find ${notFound.joinToString(", ")}. Anything else?"
            speakWithCart(msg) { currentState = AssistantState.ASKING_MORE; startListening() }
        }
    }

    private fun isNoMoreIntent(s: String): Boolean {
        if (MultilingualMatcher.isDone(s)) return true
        if (IndianLanguageProcessor.DONE_PHRASES.any { s.contains(it, ignoreCase = true) }) return true
        val phrases = listOf("no","nope","done","nothing","finish","stop","place order","checkout",
            "नहीं","नही","बस","हो गया","इतना ही","bas","nahi","khatam","order kar do","order karo","kar do")
        return phrases.any { s.contains(it) }
    }

    private fun suggestRelatedItem(productName: String): String? = when {
        productName.lowercase().contains("rice")  -> "dal"
        productName.lowercase().contains("atta")  ||
                productName.lowercase().contains("flour") -> "oil"
        productName.lowercase().contains("dal")   -> "rice"
        productName.lowercase().contains("milk")  -> "sugar"
        productName.lowercase().contains("bread") -> "butter"
        productName.lowercase().contains("oil")   -> "salt"
        else -> null
    }

    private fun keywordFallback(s: String): String? {
        val regional = IndianLanguageProcessor.normalizeProduct(s)
        if (regional != s) return regional
        return when {
            s.contains("rice")     || s.contains("चावल")   || s.contains("అన్నం")  -> "rice"
            s.contains("oil")      || s.contains("तेल")    || s.contains("నూనె")   -> "oil"
            s.contains("sugar")    || s.contains("चीनी")   || s.contains("చక్కెర") -> "sugar"
            s.contains("wheat")    || s.contains("गेहूं")                           -> "wheat"
            s.contains("dal")      || s.contains("दाल")    || s.contains("పప్పు")  -> "dal"
            s.contains("salt")     || s.contains("नमक")    || s.contains("ఉప్పు")  -> "salt"
            s.contains("milk")     || s.contains("दूध")    || s.contains("పాలు")   -> "milk"
            s.contains("flour")    || s.contains("atta")                            -> "wheat flour"
            s.contains("tea")      || s.contains("चाय")    || s.contains("టీ")     -> "tea"
            s.contains("coffee")                                                    -> "coffee"
            s.contains("ghee")     || s.contains("घी")                             -> "ghee"
            s.contains("butter")   || s.contains("मक्खन")                          -> "butter"
            s.contains("paneer")   || s.contains("पनीर")                           -> "paneer"
            s.contains("eggs")     || s.contains("egg")    || s.contains("अंडा")   -> "eggs"
            s.contains("bread")    || s.contains("रोटी")                           -> "bread"
            s.contains("poha")     || s.contains("पोहा")                           -> "poha"
            s.contains("turmeric") || s.contains("haldi")  || s.contains("हल्दी")  -> "turmeric"
            s.contains("jeera")    || s.contains("cumin")  || s.contains("जीरा")   -> "cumin"
            else -> null
        }
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
                    } else {
                        IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)
                    }
                    speak(greeting) { startListening() }
                }
            },
            onFailure = {
                runOnUiThread { speak("Login failed. Please check your email and password.") { startWakeWordListening() } }
            }
        )
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
            val items  = apiClient.getOrderItems(orders.first().id)
            if (items.isEmpty())  { runOnUiThread { speak("Couldn't load your last order. What would you like?") { startListening() } }; return@launch }
            for (item in items) { apiClient.searchProduct(item.product_name)?.let { cart.add(CartItem(it, item.quantity)) } }
            val summary = items.joinToString(", ") { "${it.quantity} ${it.product_name}" }
            runOnUiThread {
                currentState = AssistantState.CONFIRMING
                speakWithCart("Repeating your last order: $summary. Shall I place it?") { startListening() }
            }
        }
    }

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) {
            speak("Your cart is empty. What would you like to order?") {
                currentState = AssistantState.LISTENING; startListening()
            }
            return
        }
        val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.CONFIRMING
        speakWithCart("You ordered $summary. Shall I place the order? You can also say remove or change any item.") { startListening() }
    }

    // ─── PAYMENT ──────────────────────────────────────────────────────────────

    private fun placeOrderWithPayment() {
        pendingOrderTotal   = cart.sumOf { it.product.price * it.quantity }
        pendingOrderSummary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        // ✅ Stop lock task before launching PaymentActivity
        try { stopLockTask() } catch (_: Exception) {}
        val intent = Intent(this, PaymentActivity::class.java).apply {
            putExtra(EXTRA_ORDER_TOTAL,   pendingOrderTotal)
            putExtra(EXTRA_ORDER_SUMMARY, pendingOrderSummary)
            putExtra(PaymentActivity.EXTRA_MODE, PaymentActivity.MODE_CONFIRM)
        }
        paymentLauncher.launch(intent)
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
                val shortId     = if (orderResult.public_id.isNotBlank()) orderResult.public_id
                else orderResult.id.takeLast(6).uppercase()
                val firstName   = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
                val lang        = LanguageManager.getLanguage()
                Log.d("Butler", "Order placed: ${orderResult.id}")
                AnalyticsManager.logOrderPlaced(orderResult.id, orderResult.total_amount, cart.size, lang)

                lastOrderId    = orderResult.id
                lastPublicId   = shortId
                lastOrderTotal = orderResult.total_amount

                setUiState(ButlerUiState.OrderDone(shortId, orderResult.total_amount, "placed", firstName))
                val farewell = IndianLanguageProcessor.getOrderConfirmation(lang, firstName, shortId)

                speak(farewell) {
                    // ✅ Stop lock task BEFORE launching DeliveryTrackingActivity
                    try { stopLockTask() } catch (_: Exception) {}
                    val trackIntent = Intent(this@MainActivity, DeliveryTrackingActivity::class.java).apply {
                        putExtra("order_id",  lastOrderId)
                        putExtra("public_id", lastPublicId)
                        putExtra("total",     lastOrderTotal)
                        putExtra("summary",   pendingOrderSummary)
                    }
                    startActivity(trackIntent)
                    cart.clear()
                    UserSessionManager.logout()
                    // ✅ 6 second delay gives delivery screen time to show before resetting
                    Handler(Looper.getMainLooper()).postDelayed({
                        startWakeWordListening()
                    }, 6000)
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

    // ─── SPEAK HELPERS ────────────────────────────────────────────────────────

    private fun extractQuantity(text: String): Int {
        val w = mapOf(
            "one" to 1,"two" to 2,"three" to 3,"four" to 4,"five" to 5,
            "six" to 6,"seven" to 7,"eight" to 8,"nine" to 9,"ten" to 10,
            "एक" to 1,"दो" to 2,"तीन" to 3,"चार" to 4,"पाँच" to 5,
            "ek" to 1,"do" to 2,"teen" to 3,"char" to 4,"paanch" to 5,
            "ఒకటి" to 1,"రెండు" to 2,"మూడు" to 3,"నాలుగు" to 4,"ఐదు" to 5,
            "ஒன்று" to 1,"இரண்டு" to 2,"மூன்று" to 3,"நான்கு" to 4,"ஐந்து" to 5,
            "ಒಂದು" to 1,"ಎರಡು" to 2,"ಮೂರು" to 3,"ನಾಲ್ಕು" to 4,"ಐದು" to 5,
            "ഒന്ന്" to 1,"രണ്ട്" to 2,"മൂന്ന്" to 3,"നാല്" to 4,"അഞ്ച്" to 5,
            "ਇੱਕ" to 1,"ਦੋ" to 2,"ਤਿੰਨ" to 3,"ਚਾਰ" to 4,"ਪੰਜ" to 5,
            "ଏକ" to 1,"ଦୁଇ" to 2,"ତିନି" to 3,"ଚାରି" to 4,"ପାଞ୍ଚ" to 5
        )
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

    private fun speakKeepingRecsVisible(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            runOnUiThread { ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() }) }
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
                org.json.JSONObject(resBody)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            } catch (e: Exception) {
                Log.e("Butler", "translateToEnglish failed: ${e.message}")
                text
            }
        }
    }
}