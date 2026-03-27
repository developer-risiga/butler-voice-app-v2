package com.demo.butler_voice_app

import android.content.Intent
import com.demo.butler_voice_app.api.PaymentManager
import com.demo.butler_voice_app.api.SmartReorderManager
import com.demo.butler_voice_app.api.ReorderSuggestion
import com.demo.butler_voice_app.ui.AuthActivity
import com.demo.butler_voice_app.ui.DeliveryTrackingActivity
import com.demo.butler_voice_app.ui.RESULT_MANUAL_LOGIN
import com.demo.butler_voice_app.ui.RESULT_USE_VOICE
import com.demo.butler_voice_app.ui.RESULT_GOOGLE_AUTH
import com.demo.butler_voice_app.ui.EXTRA_EMAIL
import com.demo.butler_voice_app.ui.EXTRA_PASSWORD
import com.demo.butler_voice_app.ui.EXTRA_NAME
import com.demo.butler_voice_app.ui.EXTRA_IS_NEW_USER
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

// ── All assistant states including new payment + phone states ──────────────────
enum class AssistantState {
    IDLE, CHECKING_AUTH,

    // Voice signup states
    ASKING_IS_NEW_USER,
    ASKING_NAME,
    ASKING_EMAIL,
    ASKING_PHONE,
    ASKING_PASSWORD,

    // Ordering states
    LISTENING,
    ASKING_QUANTITY,
    ASKING_MORE,
    CONFIRMING,
    REORDER_CONFIRM,
    EDITING_CART,

    // Payment states — all voice driven
    ASKING_PAYMENT_MODE,
    WAITING_CARD_PAYMENT,
    WAITING_UPI_PAYMENT,
    WAITING_QR_PAYMENT
}

class MainActivity : ComponentActivity() {

    // ─── Fields ───────────────────────────────────────────────────────────────
    private var pendingOrderTotal: Double  = 0.0
    private var pendingOrderSummary: String = ""
    private var emailRetryCount = 0
    private var phoneRetryCount = 0
    private var userLocation: android.location.Location? = null
    private lateinit var locationManager: android.location.LocationManager
    private val productRepo = SmartProductRepository(SupabaseClient)
    private val uiState     = mutableStateOf<ButlerUiState>(ButlerUiState.Idle)
    private var tempName    = ""
    private var tempEmail   = ""
    private var tempPhone   = ""
    private var sttRetryCount = 0

    private var pendingReorderSuggestions: List<ReorderSuggestion> = emptyList()
    private var lastOrderId    = ""
    private var lastPublicId   = ""
    private var lastOrderTotal = 0.0

    private lateinit var ttsManager : TTSManager
    private lateinit var sarvamSTT  : SarvamSTTManager
    private lateinit var porcupine  : WakeWordManager

    private val apiClient   = ApiClient()
    private val cart        = mutableListOf<CartItem>()
    private var tempProduct : ApiClient.Product? = null
    private var currentState = AssistantState.IDLE
    private val recordRequestCode = 101

    // ─── Auth launcher (Google / Manual from AuthActivity UI) ─────────────────
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_MANUAL_LOGIN -> {
                val email = result.data?.getStringExtra(EXTRA_EMAIL) ?: return@registerForActivityResult
                val pass  = result.data?.getStringExtra(EXTRA_PASSWORD) ?: return@registerForActivityResult
                val name  = result.data?.getStringExtra(EXTRA_NAME) ?: ""
                val isNew = result.data?.getBooleanExtra(EXTRA_IS_NEW_USER, true) ?: true
                try { startLockTask() } catch (_: Exception) {}
                lifecycleScope.launch {
                    if (isNew) {
                        val displayName = name.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.CREATING_ACCOUNT, displayName, email))
                        UserSessionManager.signup(email, pass, displayName, "").fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: displayName
                                speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)) { startListening() }
                            },
                            onFailure = { err ->
                                if (err.message?.contains("already") == true) {
                                    speak("Account already exists. Logging you in.") { lifecycleScope.launch { doLogin(email, pass) } }
                                } else {
                                    speak("Account creation failed. Please try again.") { startWakeWordListening() }
                                }
                            }
                        )
                    } else {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.LOGGING_IN))
                        doLogin(email, pass)
                    }
                }
            }
            RESULT_GOOGLE_AUTH -> {
                val gEmail = result.data?.getStringExtra(EXTRA_GOOGLE_EMAIL) ?: ""
                val gName  = result.data?.getStringExtra(EXTRA_GOOGLE_NAME) ?: ""
                if (gEmail.isBlank()) return@registerForActivityResult
                try { startLockTask() } catch (_: Exception) {}
                lifecycleScope.launch {
                    val googlePass  = "Butler_G_${gEmail.hashCode().toString().takeLast(8)}"
                    val displayName = gName.ifBlank { gEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.CREATING_ACCOUNT, displayName, gEmail))
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
                try { startLockTask() } catch (_: Exception) {}
                // User chose voice signup — start voice flow
                Handler(Looper.getMainLooper()).postDelayed({
                    startVoiceSignupFlow()
                }, 300)
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

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
        porcupine  = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) {
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
    override fun onResume()  {
        super.onResume()
        if (currentState == AssistantState.IDLE) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    // ─── Location ─────────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 30000L, 50f) { location -> userLocation = location }
                userLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) { Log.e("Butler", "Location error: ${e.message}") }
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION), recordRequestCode)
        } else { startWakeWordListening() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates(); startWakeWordListening()
        }
    }

    // ─── Wake word ────────────────────────────────────────────────────────────

    private fun startWakeWordListening() {
        currentState    = AssistantState.IDLE
        sttRetryCount   = 0
        emailRetryCount = 0
        phoneRetryCount = 0
        cart.clear()
        tempName  = ""; tempEmail = ""; tempPhone = ""
        pendingReorderSuggestions = emptyList()
        LanguageManager.reset()
        apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle)
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        // ✅ Stop Porcupine immediately — no re-trigger mid-session
        try { porcupine.stop() } catch (_: Exception) {}

        // ✅ Ignore if already in an active session
        if (currentState != AssistantState.IDLE) {
            Log.d("Butler", "⚠️ Wake word ignored — state: $currentState")
            return
        }

        currentState = AssistantState.CHECKING_AUTH
        setUiState(ButlerUiState.Thinking("Checking session…"))
        lifecycleScope.launch {
            val restored = UserSessionManager.tryRestoreSession()
            runOnUiThread {
                if (restored && UserSessionManager.currentProfile != null) {
                    val name    = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
                    val history = UserSessionManager.purchaseHistory
                    AnalyticsManager.logSessionStart(UserSessionManager.currentUserId(), LanguageManager.getLanguage())
                    if (history.isNotEmpty()) {
                        lifecycleScope.launch {
                            val suggestions = SmartReorderManager.getSuggestions(UserSessionManager.currentUserId() ?: "")
                            val smartMsg    = SmartReorderManager.buildReorderGreeting(suggestions, name)
                            runOnUiThread {
                                if (smartMsg != null && suggestions.isNotEmpty()) {
                                    pendingReorderSuggestions = suggestions
                                    currentState = AssistantState.REORDER_CONFIRM
                                    speak(smartMsg) { startListening() }
                                } else {
                                    currentState = AssistantState.LISTENING
                                    // ✅ Safe null check — never say "null" aloud
                                    val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() }
                                    val greeting = if (lastProduct != null)
                                        "Welcome back $name! Last time you ordered $lastProduct. What would you like today?"
                                    else
                                        IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), name)
                                    speak(greeting) { startListening() }
                                }
                            }
                        }
                    } else {
                        currentState = AssistantState.LISTENING
                        speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), name)) { startListening() }
                    }
                } else {
                    // No session — show auth choice and launch AuthActivity
                    setUiState(ButlerUiState.AuthChoice)
                    try { stopLockTask() } catch (_: Exception) {}
                    authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                }
            }
        }
    }

    // ─── Voice signup flow ─────────────────────────────────────────────────────

    private fun startVoiceSignupFlow() {
        currentState = AssistantState.ASKING_IS_NEW_USER
        setUiState(ButlerUiState.VoiceSignupStep(
            step   = ButlerUiState.SignupStep.ASK_NEW_OR_RETURNING,
            prompt = "Are you a new customer, or have you ordered before?"
        ))
        speak("Welcome to Butler! Are you a new customer, or have you ordered before?") { startListening() }
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
                    if (transcript.isNotBlank()) {
                        val detectedLang = LanguageDetector.detect(transcript)
                        if (transcript.length > 3) {
                            val scriptLang = MultilingualMatcher.detectScript(transcript)
                            LanguageManager.setLanguage(if (scriptLang != "en") scriptLang else detectedLang)
                        }
                    }
                    if (transcript.isBlank()) {
                        sttRetryCount++
                        if (sttRetryCount < 2) { startListening() }
                        else { sttRetryCount = 0; speak("Sorry, I didn't catch that") { startListening() } }
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

    // ─── Main command handler ──────────────────────────────────────────────────

    private fun handleCommand(text: String) {
        val lower   = text.lowercase().trim()
        val cleaned = lower.replace(Regex("[,।.!?؟]"), "").trim()
        val lang    = LanguageManager.getLanguage()

        when (currentState) {

            // ── Voice signup ───────────────────────────────────────────────────

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    cleaned.contains("new") || cleaned.contains("first") ||
                            cleaned.contains("register") || cleaned.contains("नया") ||
                            cleaned.contains("నొత్త") || cleaned.contains("புதிய") -> {
                        currentState = AssistantState.ASKING_NAME
                        setUiState(ButlerUiState.VoiceSignupStep(
                            step   = ButlerUiState.SignupStep.ASK_NAME,
                            prompt = "What is your full name?"
                        ))
                        speak("Great! What is your full name?") { startListening() }
                    }
                    cleaned.contains("returning") || cleaned.contains("before") ||
                            cleaned.contains("existing") || cleaned.contains("login") ||
                            cleaned.contains("yes") || cleaned.contains("पहले") -> {
                        currentState = AssistantState.ASKING_EMAIL
                        setUiState(ButlerUiState.VoiceSignupStep(
                            step   = ButlerUiState.SignupStep.ASK_EMAIL,
                            prompt = EmailPasswordParser.getEmailPrompt(lang)
                        ))
                        speak(EmailPasswordParser.getEmailPrompt(lang)) { startListening() }
                    }
                    else -> {
                        speak("Please say new customer, or returning customer.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_NAME -> {
                lifecycleScope.launch {
                    val translatedText = if (LanguageDetector.detect(text) != "en") translateToEnglish(text) else text
                    val nameText = translatedText.trim()
                        .replace(Regex("my name is\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("i am\\s*",        RegexOption.IGNORE_CASE), "")
                        .replace(Regex("mera naam\\s*",   RegexOption.IGNORE_CASE), "")
                        .replace(".", "").trim()
                    if (nameText.isBlank() || nameText.contains("@")) {
                        runOnUiThread { speak("Please say just your name clearly.") { startListening() } }
                        return@launch
                    }
                    tempName = nameText.split(" ").firstOrNull { it.length > 1 }
                        ?.replaceFirstChar { it.uppercase() }
                        ?: nameText.replaceFirstChar { it.uppercase() }
                    runOnUiThread {
                        currentState = AssistantState.ASKING_EMAIL
                        setUiState(ButlerUiState.VoiceSignupStep(
                            step          = ButlerUiState.SignupStep.ASK_EMAIL,
                            collectedName = tempName,
                            prompt        = "Nice to meet you $tempName! Please spell your email address, letter by letter."
                        ))
                        speak("Nice to meet you $tempName! Please spell your email address, letter by letter. For example: r-a-v-i at gmail dot com") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_EMAIL -> {
                val parsed = EmailPasswordParser.parseEmail(text)
                if (parsed != null) {
                    tempEmail = parsed
                    emailRetryCount = 0
                    Log.d("Butler", "Email: $tempEmail")
                    currentState = AssistantState.ASKING_PHONE
                    setUiState(ButlerUiState.VoiceSignupStep(
                        step          = ButlerUiState.SignupStep.ASK_PHONE,
                        collectedName = tempName,
                        collectedEmail = tempEmail,
                        prompt        = "What is your 10-digit mobile number?"
                    ))
                    speak("Got it — $tempEmail. Now, what is your 10-digit mobile number?") { startListening() }
                } else {
                    emailRetryCount++
                    if (emailRetryCount <= 2) {
                        speak(EmailPasswordParser.getEmailRetryPrompt(lang)) { startListening() }
                    } else {
                        val fallback = EmailPasswordParser.parseEmailBestEffort(text)
                        if (fallback.contains("@")) {
                            tempEmail = fallback
                            emailRetryCount = 0
                            currentState = AssistantState.ASKING_PHONE
                            setUiState(ButlerUiState.VoiceSignupStep(
                                step           = ButlerUiState.SignupStep.ASK_PHONE,
                                collectedName  = tempName,
                                collectedEmail = tempEmail
                            ))
                            speak("OK, I got $tempEmail. What is your mobile number?") { startListening() }
                        } else {
                            emailRetryCount = 0
                            speak("I'm having trouble understanding the email. Let me show you the sign-in screen.") {
                                try { stopLockTask() } catch (_: Exception) {}
                                authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                            }
                        }
                    }
                }
            }

            AssistantState.ASKING_PHONE -> {
                // Extract digits from speech
                val digits = text.replace(Regex("[^0-9]"), "")
                val phone  = when {
                    digits.length == 10 -> digits
                    digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
                    digits.length > 10 -> digits.takeLast(10)
                    else -> ""
                }
                if (phone.length == 10) {
                    tempPhone = phone
                    phoneRetryCount = 0
                    currentState = AssistantState.ASKING_PASSWORD
                    setUiState(ButlerUiState.VoiceSignupStep(
                        step           = ButlerUiState.SignupStep.ASK_PASSWORD,
                        collectedName  = tempName,
                        collectedEmail = tempEmail,
                        collectedPhone = tempPhone,
                        prompt         = "Now choose a password. Spell each character clearly."
                    ))
                    speak("Got it — $tempPhone. Now choose a password. Spell each character clearly, for example: capital A, lowercase b, 1, 2, 3") { startListening() }
                } else {
                    phoneRetryCount++
                    if (phoneRetryCount <= 2) {
                        speak("Please say your 10-digit mobile number again clearly, digit by digit.") { startListening() }
                    } else {
                        phoneRetryCount = 0
                        // Skip phone, move to password
                        currentState = AssistantState.ASKING_PASSWORD
                        setUiState(ButlerUiState.VoiceSignupStep(
                            step           = ButlerUiState.SignupStep.ASK_PASSWORD,
                            collectedName  = tempName,
                            collectedEmail = tempEmail,
                            prompt         = "Let's set your password. Spell each character clearly."
                        ))
                        speak("No problem, let's skip phone for now. Please choose a password and spell each character.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = EmailPasswordParser.parsePassword(text)
                    .ifBlank { text.trim().replace(" ", "").trimEnd('.', ',', '!') }
                Log.d("Butler", "Password length: ${password.length}")
                if (password.length < 6) {
                    speak("Password must be at least 6 characters. Please try again and spell clearly.") { startListening() }
                    return
                }
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.VoiceSignupStep(
                            step           = ButlerUiState.SignupStep.CREATING_ACCOUNT,
                            collectedName  = tempName,
                            collectedEmail = tempEmail
                        ))
                        speak("Creating your account, please wait.") {}
                        UserSessionManager.signup(tempEmail, password, tempName, tempPhone).fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                AnalyticsManager.logUserAuth("voice_signup", LanguageManager.getLanguage())
                                speak(IndianLanguageProcessor.getWelcomeGreeting(lang, firstName)) { startListening() }
                            },
                            onFailure = { error ->
                                if (error.message?.contains("user_already_exists") == true ||
                                    error.message?.contains("already registered") == true) {
                                    speak("An account already exists with this email. Logging you in.") {
                                        lifecycleScope.launch { doLogin(tempEmail, password) }
                                    }
                                } else {
                                    speak("Sorry, couldn't create account. ${error.message?.take(60) ?: "Please try again."}") { startWakeWordListening() }
                                }
                            }
                        )
                    } else {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.LOGGING_IN))
                        doLogin(tempEmail, password)
                    }
                }
            }

            // ── Smart reorder ──────────────────────────────────────────────────

            AssistantState.REORDER_CONFIRM -> {
                when {
                    MultilingualMatcher.isYes(cleaned) ||
                            IndianLanguageProcessor.detectIntent(cleaned) == "confirm" -> {
                        lifecycleScope.launch {
                            for (suggestion in pendingReorderSuggestions) {
                                apiClient.searchProduct(suggestion.productName)?.let { cart.add(CartItem(it, suggestion.avgQty)) }
                            }
                            runOnUiThread {
                                if (cart.isNotEmpty()) {
                                    currentState = AssistantState.CONFIRMING
                                    val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
                                    showCartAndSpeak("Perfect! I've added $summary to your cart. Shall I place the order?") { startListening() }
                                } else {
                                    currentState = AssistantState.LISTENING
                                    speak("Couldn't find those products. What would you like to order?") { startListening() }
                                }
                            }
                        }
                    }
                    MultilingualMatcher.isNo(cleaned) -> {
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

            // ── Ordering ───────────────────────────────────────────────────────

            AssistantState.LISTENING -> handleOrderIntent(text, lower)

            AssistantState.ASKING_QUANTITY -> {
                val qty     = extractQuantity(text)
                val product = tempProduct
                if (product != null) {
                    cart.add(CartItem(product, qty))
                    val suggestion = suggestRelatedItem(product.name)
                    currentState   = AssistantState.ASKING_MORE
                    val msg = if (suggestion != null && cart.size == 1)
                        "Added $qty ${product.name}. Would you also like $suggestion? Or say done to checkout."
                    else
                        "Added $qty ${product.name}. Would you like to add more items, or shall I go to checkout?"
                    showCartAndSpeak(msg) { startListening() }
                } else {
                    speak("Let's try again. What would you like to order?") {
                        currentState = AssistantState.LISTENING; startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE -> handleAskingMore(cleaned, text)

            AssistantState.EDITING_CART -> handleCartEdit(cleaned, text)

            AssistantState.CONFIRMING -> {
                val intentFromLang = IndianLanguageProcessor.detectIntent(cleaned)
                when {
                    intentFromLang == "confirm" || MultilingualMatcher.isYes(cleaned) -> askPaymentMode()
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
                                    "confirm_order" -> askPaymentMode()
                                    "cancel_order"  -> {
                                        speak("Order cancelled.") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                                    }
                                    else -> speak("Say yes to place the order, no to cancel, or tell me what to change.") { startListening() }
                                }
                            }
                        }
                    }
                }
            }

            // ── Payment voice states ───────────────────────────────────────────

            AssistantState.ASKING_PAYMENT_MODE -> handlePaymentModeChoice(cleaned)

            AssistantState.WAITING_CARD_PAYMENT -> handlePaymentConfirmation(cleaned, "card")

            AssistantState.WAITING_UPI_PAYMENT  -> handlePaymentConfirmation(cleaned, "upi")

            AssistantState.WAITING_QR_PAYMENT   -> handlePaymentConfirmation(cleaned, "qr")

            else -> {}
        }
    }

    // ─── Payment voice flow ────────────────────────────────────────────────────

    private fun askPaymentMode() {
        pendingOrderTotal   = cart.sumOf { it.product.price * it.quantity }
        pendingOrderSummary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.ASKING_PAYMENT_MODE

        val card    = PaymentManager.getSavedCard(this)
        val hasSaved = card != null
        val cardInfo = if (card != null) "${card.network} card ending ${card.last4}" else ""

        setUiState(ButlerUiState.PaymentChoice(
            totalAmount  = pendingOrderTotal,
            orderSummary = pendingOrderSummary,
            hasSavedCard = hasSaved,
            savedCardInfo = cardInfo,
            listeningForChoice = true
        ))

        val cardPart = if (hasSaved) "say card to pay with your saved $cardInfo," else "say card to add a new card,"
        speak("Your total is ₹%.0f. How would you like to pay? $cardPart say UPI to pay with UPI, or say QR to scan a QR code.".format(pendingOrderTotal)) {
            startListening()
        }
    }

    private fun handlePaymentModeChoice(cleaned: String) {
        when {
            // CARD
            cleaned.contains("card") || cleaned.contains("debit") || cleaned.contains("credit") ||
                    cleaned.contains("saved") || cleaned.contains("कार्ड") || cleaned.contains("కార్డ్") -> {
                val card = PaymentManager.getSavedCard(this)
                if (card != null) {
                    currentState = AssistantState.WAITING_CARD_PAYMENT
                    setUiState(ButlerUiState.WaitingPaymentConfirm("card", pendingOrderTotal))
                    speak("I'll charge your ${card.network} card ending ${card.last4} for ₹%.0f. Please tap your card or enter OTP, then say payment done.".format(pendingOrderTotal)) {
                        startListening()
                    }
                } else {
                    currentState = AssistantState.WAITING_CARD_PAYMENT
                    setUiState(ButlerUiState.WaitingPaymentConfirm("card", pendingOrderTotal))
                    speak("Please enter your card details on the screen, then say payment done when complete.") {
                        startListening()
                    }
                }
            }
            // UPI
            cleaned.contains("upi") || cleaned.contains("google pay") || cleaned.contains("phonepe") ||
                    cleaned.contains("paytm") || cleaned.contains("bhim") || cleaned.contains("यूपीआई") -> {
                currentState = AssistantState.WAITING_UPI_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("upi", pendingOrderTotal))
                speak("Open your UPI app and pay ₹%.0f to Butler@upi. Once done, say payment done or I paid.".format(pendingOrderTotal)) {
                    startListening()
                }
            }
            // QR
            cleaned.contains("qr") || cleaned.contains("scan") || cleaned.contains("scanner") ||
                    cleaned.contains("क्यूआर") || cleaned.contains("స్కాన్") -> {
                currentState = AssistantState.WAITING_QR_PAYMENT
                setUiState(ButlerUiState.ShowQRCode(pendingOrderTotal, pendingOrderSummary))
                speak("I've generated a QR code on screen. Scan it with any UPI app to pay ₹%.0f. Then say payment done.".format(pendingOrderTotal)) {
                    startListening()
                }
            }
            else -> {
                speak("I didn't understand. Say card, UPI, or QR to choose payment method.") { startListening() }
            }
        }
    }

    private fun handlePaymentConfirmation(cleaned: String, mode: String) {
        val paymentDoneKeywords = listOf(
            "done", "paid", "payment done", "made the payment", "i paid", "completed",
            "successful", "success", "finished", "transferred", "sent",
            "हो गया", "भुगतान हो गया", "पेमेंट हो गया",
            "చేశాను", "అయింది", "pay చేశాను",
            "நடந்தது", "பணம் செலுத்தினேன்"
        )
        val cancelKeywords = listOf("cancel", "back", "stop", "different", "change", "रद्द", "బ్యాక్")

        when {
            paymentDoneKeywords.any { cleaned.contains(it) } -> {
                // ✅ Payment confirmed by voice — place the order
                speak("Great! Payment confirmed. Placing your order now.") { placeOrder() }
            }
            cancelKeywords.any { cleaned.contains(it) } -> {
                // Go back to payment mode selection
                askPaymentMode()
            }
            else -> {
                val reminder = when (mode) {
                    "card" -> "Once you've completed the card payment, say payment done."
                    "upi"  -> "After paying on your UPI app, say payment done or I paid."
                    "qr"   -> "After scanning and paying, say payment done."
                    else   -> "Say payment done once you've completed the payment."
                }
                speak(reminder) { startListening() }
            }
        }
    }

    // ─── Cart editing ──────────────────────────────────────────────────────────

    private fun isCartEditIntent(s: String): Boolean {
        val phrases = listOf(
            "remove","delete","हटाओ","హటావో","ತೆಗೆ","change","update","बदलो","మార్చు","add more","increase","more"
        )
        return phrases.any { s.contains(it) }
    }

    private fun handleCartEdit(cleaned: String, originalText: String) {
        if (cart.isEmpty()) {
            speak("Your cart is empty. What would you like to add?") { currentState = AssistantState.LISTENING; startListening() }
            return
        }
        val isRemove = cleaned.contains("remove") || cleaned.contains("delete") || cleaned.contains("हटाओ") || cleaned.contains("हटा")
        val isChange = cleaned.contains("change") || cleaned.contains("update") || cleaned.contains("बदलो") || cleaned.contains("make it")

        if (isRemove) {
            val removedItem = cart.firstOrNull { item -> item.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (removedItem != null) {
                cart.remove(removedItem)
                if (cart.isEmpty()) {
                    speak("Removed ${removedItem.product.name}. Cart is now empty. What would you like to add?") { currentState = AssistantState.LISTENING; startListening() }
                } else {
                    currentState = AssistantState.CONFIRMING
                    showCartAndSpeak("Removed ${removedItem.product.name}. Shall I place the order with the remaining items?") { startListening() }
                }
            } else {
                speak("I couldn't find that in your cart. Which item would you like to remove?") { startListening() }
            }
            return
        }
        if (isChange) {
            val newQty      = extractQuantity(cleaned)
            val changedItem = cart.firstOrNull { item -> item.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (changedItem != null && newQty > 0) {
                cart[cart.indexOf(changedItem)] = CartItem(changedItem.product, newQty)
                currentState = AssistantState.CONFIRMING
                showCartAndSpeak("Updated ${changedItem.product.name} to $newQty. Shall I place the order?") { startListening() }
            } else {
                speak("Tell me which item to change and the new quantity.") { startListening() }
            }
            return
        }
        currentState = AssistantState.CONFIRMING
        readCartAndConfirm()
    }

    // ─── Order intent ──────────────────────────────────────────────────────────

    private fun handleOrderIntent(text: String, lower: String) {
        val cleaned = lower.replace(Regex("[,।.!?]"), "").trim()
        if (cleaned.contains("repeat") || cleaned.contains("same as last")) { repeatLastOrder(); return }
        if (cleaned.contains("my orders") || cleaned.contains("history"))   { readOrderHistory(); return }
        if (isNoMoreIntent(cleaned)) { readCartAndConfirm(); return }
        if (cart.isNotEmpty() && isCartEditIntent(cleaned)) { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, text); return }

        val regionalProduct = IndianLanguageProcessor.normalizeProduct(cleaned)
        if (regionalProduct != cleaned && regionalProduct.isNotBlank()) { searchAndAskQuantity(regionalProduct); return }

        lifecycleScope.launch {
            val parsed = AIOrderParser.parse(text)
            LanguageManager.setLanguage(parsed.detectedLanguage)
            runOnUiThread {
                when (parsed.intent) {
                    "finish_order"  -> { if (cart.isEmpty()) speak("Cart is empty. What would you like?") { startListening() } else readCartAndConfirm() }
                    "confirm_order" -> { if (currentState == AssistantState.CONFIRMING) askPaymentMode() else readCartAndConfirm() }
                    "cancel_order"  -> speak("Cancelled.") { startWakeWordListening() }
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

    // ─── Asking more ───────────────────────────────────────────────────────────

    private fun handleAskingMore(cleaned: String, originalText: String) {
        when {
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("add") || cleaned.contains("more") ||
                    cleaned.contains("और") || cleaned.contains("aur") -> {
                currentState = AssistantState.LISTENING
                showCartAndSpeak("What else would you like to add?") { startListening() }
            }
            isNoMoreIntent(cleaned) -> readCartAndConfirm()
            isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, originalText) }
            else -> {
                lifecycleScope.launch {
                    val parsed = AIOrderParser.parse(originalText)
                    LanguageManager.setLanguage(parsed.detectedLanguage)
                    runOnUiThread {
                        when (parsed.intent) {
                            "finish_order", "cancel_order" -> readCartAndConfirm()
                            "order", "add_more" -> {
                                if (parsed.items.isNotEmpty()) { currentState = AssistantState.LISTENING; handleOrderIntent(originalText, originalText.lowercase()) }
                                else showCartAndSpeak("Should I add more or go to checkout?") { startListening() }
                            }
                            else -> showCartAndSpeak("Should I add more, or shall I go to checkout?") { startListening() }
                        }
                    }
                }
            }
        }
    }

    // ─── Product search ────────────────────────────────────────────────────────

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        lifecycleScope.launch {
            val recs = productRepo.getTopRecommendations(itemName, userLocation)
            if (recs.isNotEmpty()) {
                runOnUiThread { setUiState(ButlerUiState.ShowingRecommendations(itemName, recs)) }
                val readout = "I found ${recs.size} options for $itemName. " +
                        recs.mapIndexed { i, r -> "${i + 1}: ${r.productName} at ${r.priceLabel}" }.joinToString(". ") +
                        ". Say 1, 2, or 3 to pick."
                speakKeepingRecsVisible(readout) {
                    sarvamSTT.startListening(
                        onResult = { spoken ->
                            runOnUiThread {
                                if (spoken.lowercase().trim().isBlank()) {
                                    speakKeepingRecsVisible("Please say 1, 2, or 3.") {
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
                            currentState = AssistantState.ASKING_MORE
                            showCartAndSpeak("Added $qty ${product.name}. Would you like to add more items, or shall I go to checkout?") { startListening() }
                        } else {
                            currentState = AssistantState.ASKING_QUANTITY
                            speak("How many ${product.name} would you like?") { startListening() }
                        }
                    } else {
                        speak("I couldn't find $itemName. What else would you like?") { startListening() }
                    }
                }
            }
        }
    }

    private fun handleRecSelection(spoken: String, recs: List<com.demo.butler_voice_app.api.ProductRecommendation>, qty: Int, itemName: String) {
        Log.d("Butler", "handleRecSelection: spoken='$spoken'")
        val numberIndex = MultilingualMatcher.matchNumber(spoken)
        val pick = when {
            numberIndex >= 0                  -> recs.getOrNull(numberIndex)
            MultilingualMatcher.isYes(spoken) -> recs.first()
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
            currentState   = AssistantState.ASKING_MORE
            val msg = if (suggestion != null && cart.size == 1)
                "Added ${pick.productName} from ${pick.storeName}. Would you also like $suggestion? Or say done to checkout."
            else
                "Added ${pick.productName} from ${pick.storeName}. Would you like to add more items, or shall I go to checkout?"
            showCartAndSpeak(msg) { startListening() }
        } else {
            speakKeepingRecsVisible("Please say 1, 2, or 3 to pick a product.") {
                sarvamSTT.startListening(
                    onResult = { s2 -> runOnUiThread { handleRecSelection(s2, recs, qty, itemName) } },
                    onError  = { runOnUiThread { speak("Sorry.") { startListening() } } }
                )
            }
        }
    }

    // ─── Place order ───────────────────────────────────────────────────────────

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId()
                if (userId == null) { speak("Session expired. Say Hey Butler to start.") { startWakeWordListening() }; return@launch }

                val orderResult = apiClient.createOrder(cart, userId)
                val shortId     = if (orderResult.public_id.isNotBlank()) orderResult.public_id else orderResult.id.takeLast(6).uppercase()
                val firstName   = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
                val lang        = LanguageManager.getLanguage()

                Log.d("Butler", "Order placed: ${orderResult.id}")
                AnalyticsManager.logOrderPlaced(orderResult.id, orderResult.total_amount, cart.size, lang)

                lastOrderId    = orderResult.id
                lastPublicId   = shortId
                lastOrderTotal = orderResult.total_amount

                // ✅ Show rich order placed screen with all details
                val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
                setUiState(ButlerUiState.OrderPlaced(
                    orderId          = shortId,
                    totalAmount      = orderResult.total_amount,
                    items            = cartItems,
                    estimatedMinutes = 30,
                    userName         = firstName
                ))

                val farewell = IndianLanguageProcessor.getOrderConfirmation(lang, firstName, shortId)
                speak(farewell) {
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
                    Handler(Looper.getMainLooper()).postDelayed({ startWakeWordListening() }, 8000)
                }
            } catch (e: Exception) {
                Log.e("Butler", "Order failed: ${e.message}")
                runOnUiThread {
                    currentState = AssistantState.CONFIRMING
                    showCartAndSpeak("Sorry, there was a network issue. Say yes to try again or no to cancel.") { startListening() }
                }
            }
        }
    }

    // ─── Cart display helpers ──────────────────────────────────────────────────

    private fun showCartAndSpeak(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("Butler", "Original: $text | Translated ($lang): $finalText")
            val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
            val total     = cart.sumOf { it.product.price * it.quantity }
            runOnUiThread {
                // Show cart review if confirming, otherwise show speaking with cart
                if (currentState == AssistantState.CONFIRMING || currentState == AssistantState.ASKING_MORE) {
                    setUiState(ButlerUiState.CartReview(
                        items       = cartItems,
                        totalAmount = total,
                        prompt      = text
                    ))
                } else {
                    setUiState(ButlerUiState.Speaking(finalText, cart = cartItems))
                }
                ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() })
            }
        }
    }

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) {
            speak("Your cart is empty. What would you like to order?") { currentState = AssistantState.LISTENING; startListening() }
            return
        }
        currentState = AssistantState.CONFIRMING
        val summary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        showCartAndSpeak("You have $summary. Total is ₹%.0f. Say yes to proceed to payment, or tell me what to change.".format(cart.sumOf { it.product.price * it.quantity })) { startListening() }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>(); val notFound = mutableListOf<String>()
        for (item in items) {
            val searchName = IndianLanguageProcessor.normalizeProduct(item.name).ifBlank { item.name }
            val product    = apiClient.searchProduct(searchName)
            if (product != null) { cart.add(CartItem(product, item.quantity)); found.add("${item.quantity} ${product.name}") }
            else { notFound.add(item.name); AnalyticsManager.logItemNotFound(item.name) }
        }
        runOnUiThread {
            currentState = AssistantState.ASKING_MORE
            val msg = if (notFound.isEmpty()) "Added ${found.joinToString(", ")}. Would you like to add more, or go to checkout?"
            else "Added ${found.joinToString(", ")}. Couldn't find ${notFound.joinToString(", ")}. Anything else?"
            showCartAndSpeak(msg) { startListening() }
        }
    }

    private fun isNoMoreIntent(s: String): Boolean {
        if (MultilingualMatcher.isDone(s)) return true
        if (IndianLanguageProcessor.DONE_PHRASES.any { s.contains(it, ignoreCase = true) }) return true
        return listOf("no","nope","done","nothing","finish","stop","place order","checkout","बस","हो गया","bas","nahi","kar do").any { s.contains(it) }
    }

    private fun suggestRelatedItem(productName: String): String? = when {
        productName.lowercase().contains("rice")  -> "dal"
        productName.lowercase().contains("dal")   -> "rice"
        productName.lowercase().contains("milk")  -> "sugar"
        productName.lowercase().contains("bread") -> "butter"
        productName.lowercase().contains("oil")   -> "salt"
        productName.lowercase().contains("atta")  -> "oil"
        else -> null
    }

    private fun keywordFallback(s: String): String? {
        val regional = IndianLanguageProcessor.normalizeProduct(s)
        if (regional != s) return regional
        return when {
            s.contains("rice")  || s.contains("चावल")  || s.contains("అన్నం")  -> "rice"
            s.contains("oil")   || s.contains("तेल")   || s.contains("నూనె")   -> "oil"
            s.contains("sugar") || s.contains("चीनी")  || s.contains("చక్కెర") -> "sugar"
            s.contains("dal")   || s.contains("दाल")   || s.contains("పప్పు")  -> "dal"
            s.contains("salt")  || s.contains("नमक")   || s.contains("ఉప్పు")  -> "salt"
            s.contains("milk")  || s.contains("दूध")   || s.contains("పాలు")   -> "milk"
            s.contains("wheat") || s.contains("atta")  || s.contains("गेहूं")  -> "wheat flour"
            s.contains("tea")   || s.contains("चाय")   || s.contains("టీ")     -> "tea"
            s.contains("ghee")  || s.contains("घी")                            -> "ghee"
            s.contains("bread") || s.contains("रोटी")                          -> "bread"
            s.contains("eggs")  || s.contains("egg")   || s.contains("अंडा")   -> "eggs"
            else -> null
        }
    }

    private suspend fun doLogin(email: String, password: String) {
        UserSessionManager.login(email, password).fold(
            onSuccess = { profile ->
                runOnUiThread {
                    currentState = AssistantState.LISTENING
                    val firstName   = profile.full_name?.split(" ")?.first() ?: "there"
                    val history     = UserSessionManager.purchaseHistory
                    AnalyticsManager.logUserAuth("login", LanguageManager.getLanguage())
                    val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() }
                    val greeting    = if (lastProduct != null)
                        "Welcome back $firstName! Last time you ordered $lastProduct. What would you like?"
                    else IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)
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
        else speak("Your recent orders include ${history.take(3).joinToString(", ") { it.product_name ?: "unknown" }}. Would you like to order any?") { startListening() }
    }

    private fun repeatLastOrder() {
        lifecycleScope.launch {
            val userId = UserSessionManager.currentUserId() ?: return@launch
            val orders = apiClient.getOrderHistory(userId)
            if (orders.isEmpty()) { runOnUiThread { speak("No previous orders. What would you like?") { startListening() } }; return@launch }
            val items  = apiClient.getOrderItems(orders.first().id)
            if (items.isEmpty())  { runOnUiThread { speak("Couldn't load your last order.") { startListening() } }; return@launch }
            for (item in items) { apiClient.searchProduct(item.product_name)?.let { cart.add(CartItem(it, item.quantity)) } }
            runOnUiThread {
                currentState = AssistantState.CONFIRMING
                showCartAndSpeak("Repeating your last order. Shall I proceed to payment?") { startListening() }
            }
        }
    }

    // ─── Speak helpers ────────────────────────────────────────────────────────

    private fun extractQuantity(text: String): Int {
        val w = mapOf(
            "one" to 1,"two" to 2,"three" to 3,"four" to 4,"five" to 5,
            "six" to 6,"seven" to 7,"eight" to 8,"nine" to 9,"ten" to 10,
            "एक" to 1,"दो" to 2,"तीन" to 3,"चार" to 4,"पाँच" to 5,
            "ek" to 1,"do" to 2,"teen" to 3,"char" to 4,"paanch" to 5,
            "ఒకటి" to 1,"రెండు" to 2,"మూడు" to 3,"నాలుగు" to 4,"ఐదు" to 5
        )
        return Regex("\\d+").find(text)?.value?.toIntOrNull()
            ?: w.entries.firstOrNull { text.lowercase().contains(it.key) }?.value ?: 1
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
                            put("content", "Translate to English. Return ONLY the translated text: $text")
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