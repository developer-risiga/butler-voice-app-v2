package com.demo.butler_voice_app

import android.content.Intent
import com.demo.butler_voice_app.api.PaymentManager
import com.demo.butler_voice_app.api.SmartReorderManager
import com.demo.butler_voice_app.api.ReorderSuggestion
import com.demo.butler_voice_app.ui.AuthActivity
import com.demo.butler_voice_app.ui.DeliveryTrackingActivity
import com.demo.butler_voice_app.ui.ServiceActivity
import com.demo.butler_voice_app.ui.RESULT_MANUAL_LOGIN
import com.demo.butler_voice_app.ui.RESULT_USE_VOICE
import com.demo.butler_voice_app.ui.RESULT_GOOGLE_AUTH
import com.demo.butler_voice_app.ui.EXTRA_EMAIL
import com.demo.butler_voice_app.ui.EXTRA_PASSWORD
import com.demo.butler_voice_app.ui.EXTRA_NAME
import com.demo.butler_voice_app.ui.EXTRA_IS_NEW_USER
import com.demo.butler_voice_app.ui.EXTRA_GOOGLE_EMAIL
import com.demo.butler_voice_app.ui.EXTRA_GOOGLE_NAME
import com.demo.butler_voice_app.services.ServiceManager
import com.demo.butler_voice_app.services.ServiceVoiceHandler
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

// ══════════════════════════════════════════════════════════════════════════════
// ASSISTANT STATES
// ══════════════════════════════════════════════════════════════════════════════

enum class AssistantState {
    IDLE, CHECKING_AUTH,
    ASKING_IS_NEW_USER, ASKING_NAME, ASKING_EMAIL, ASKING_PHONE, ASKING_PASSWORD,
    LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING, REORDER_CONFIRM, EDITING_CART,
    ASKING_PAYMENT_MODE,
    WAITING_CARD_PAYMENT, WAITING_UPI_PAYMENT, WAITING_QR_PAYMENT,
    CONFIRMING_CARD_PAID, CONFIRMING_UPI_PAID, CONFIRMING_QR_PAID,
    IN_SERVICE_FLOW
}

// ══════════════════════════════════════════════════════════════════════════════
// MAIN ACTIVITY
// ══════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    private var pendingOrderTotal: Double   = 0.0
    private var pendingOrderSummary: String = ""
    private var emailRetryCount  = 0
    private var phoneRetryCount  = 0
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

    // ── ₹ FIX: TTS says "48 rupees" not "₹48" ────────────────────────────────
    private fun toSpeakableAmount(amount: Double): String = "${amount.toInt()} rupees"

    // ══════════════════════════════════════════════════════════════════════════
    // ✅ FIX: Extended number detection — all tones, languages, spellings
    // ══════════════════════════════════════════════════════════════════════════

    private fun detectNumberFromSpeech(spoken: String): Int {
        val s = spoken.lowercase().trim()
            .replace(Regex("[,।.!?]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Direct digit
        val digit = Regex("\\b([123])\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()
        if (digit != null) return digit

        return when {
            // ── 1 ─────────────────────────────────────────────────────────────
            s == "1" ||
                    s.contains("one") || s.contains("wan") || s.contains("won") ||
                    s.contains("first") || s.contains("pehla") || s.contains("pehli") ||
                    s.contains("ek") || s.contains("एक") || s.contains("ఒకటి") ||
                    s.contains("ஒன்று") || s.contains("ಒಂದು") || s.contains("ഒന്ന്") ||
                    s.contains("pahila") || s.contains("number one") || s.contains("no one") ||
                    s.contains("option one") || s.contains("choice one") ||
                    s.contains("pehla wala") || s.contains("pehli wali") -> 1

            // ── 2 ─────────────────────────────────────────────────────────────
            s == "2" ||
                    s.contains("two") || s.contains("too") || s.contains("to ") ||
                    s.contains("second") || s.contains("doosra") || s.contains("dusra") ||
                    s.contains("do") || s.contains("दो") || s.contains("రెండు") ||
                    s.contains("இரண்டு") || s.contains("ಎರಡು") || s.contains("രണ്ട്") ||
                    s.contains("number two") || s.contains("no two") ||
                    s.contains("option two") || s.contains("choice two") ||
                    s.contains("doosra wala") || s.contains("dono") -> 2

            // ── 3 ─────────────────────────────────────────────────────────────
            s == "3" ||
                    s.contains("three") || s.contains("tree") || s.contains("thre") ||
                    s.contains("third") || s.contains("teesra") || s.contains("tisra") ||
                    s.contains("teen") || s.contains("तीन") || s.contains("మూడు") ||
                    s.contains("மூன்று") || s.contains("ಮೂರು") || s.contains("മൂന്ന്") ||
                    s.contains("number three") || s.contains("no three") ||
                    s.contains("option three") || s.contains("choice three") ||
                    s.contains("teesra wala") -> 3

            else -> -1
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LAUNCHERS
    // ══════════════════════════════════════════════════════════════════════════

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
                                if (err.message?.contains("already") == true)
                                    speak("Account already exists. Logging you in.") { lifecycleScope.launch { doLogin(email, pass) } }
                                else
                                    speak("Account creation failed. Please try again.") { startWakeWordListening() }
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
                Handler(Looper.getMainLooper()).postDelayed({ startVoiceSignupFlow() }, 300)
            }
        }
    }

    private val serviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try { startLockTask() } catch (_: Exception) {}
        currentState = AssistantState.IDLE
        Handler(Looper.getMainLooper()).postDelayed({
            val bookingId = result.data?.getStringExtra("booking_id")
            if (!bookingId.isNullOrBlank()) {
                speak("Your booking is confirmed. Booking ID $bookingId. Is there anything else you need?") {
                    currentState = AssistantState.LISTENING
                    startListening()
                }
            } else {
                startWakeWordListening()
            }
        }, 500)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent { val state by uiState; ButlerScreen(state = state) }

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
    override fun onResume()  { super.onResume(); if (currentState == AssistantState.IDLE) { try { startLockTask() } catch (_: Exception) {} } }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCATION
    // ══════════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ══════════════════════════════════════════════════════════════════════════

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION), recordRequestCode)
        else startWakeWordListening()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates(); startWakeWordListening()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WAKE WORD
    // ══════════════════════════════════════════════════════════════════════════

    private fun startWakeWordListening() {
        currentState    = AssistantState.IDLE
        sttRetryCount   = 0
        emailRetryCount = 0
        phoneRetryCount = 0
        cart.clear()
        tempName = ""; tempEmail = ""; tempPhone = ""
        pendingReorderSuggestions = emptyList()
        LanguageManager.reset()
        apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle)
        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        try { porcupine.stop() } catch (_: Exception) {}
        if (currentState != AssistantState.IDLE) { Log.d("Butler", "⚠️ Wake word ignored — state: $currentState"); return }
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
                                    val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() }
                                    val greeting = if (lastProduct != null)
                                        "Welcome back $name! Last time you ordered $lastProduct. You can also book any service like plumber, doctor, or taxi. What would you like?"
                                    else IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), name)
                                    speak(greeting) { startListening() }
                                }
                            }
                        }
                    } else {
                        currentState = AssistantState.LISTENING
                        speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), name)) { startListening() }
                    }
                } else {
                    setUiState(ButlerUiState.AuthChoice)
                    try { stopLockTask() } catch (_: Exception) {}
                    authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VOICE SIGNUP
    // ══════════════════════════════════════════════════════════════════════════

    private fun startVoiceSignupFlow() {
        currentState = AssistantState.ASKING_IS_NEW_USER
        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_NEW_OR_RETURNING, prompt = "Are you a new customer, or have you ordered before?"))
        speak("Welcome to Butler! Are you a new customer, or have you ordered before?") { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STT — with longer timeout for number selection
    // ══════════════════════════════════════════════════════════════════════════

    // ✅ FIX: Standard listening — used for all general commands
    private fun startListening() {
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")
                    if (transcript.isNotBlank() && transcript.length > 3) {
                        val scriptLang = MultilingualMatcher.detectScript(transcript)
                        val detected   = LanguageDetector.detect(transcript)
                        LanguageManager.setLanguage(if (scriptLang != "en") scriptLang else detected)
                    }
                    if (transcript.isBlank()) {
                        sttRetryCount++
                        if (sttRetryCount < 2) startListening()
                        else { sttRetryCount = 0; speak("Sorry, I didn't catch that. Please speak clearly.") { startListening() } }
                        return@runOnUiThread
                    }
                    sttRetryCount = 0
                    setUiState(ButlerUiState.Thinking(transcript))
                    handleCommand(transcript)
                }
            },
            onError = { runOnUiThread { speak("Something went wrong") { startWakeWordListening() } } }
        )
    }

    // ✅ NEW: Extended listening for number selection (1/2/3) — gives more time
    // Called specifically when waiting for a product/provider selection
    private fun startListeningForSelection(
        onNumber: (Int) -> Unit,
        onOther: (String) -> Unit,
        retryPrompt: String = "Please say 1, 2, or 3."
    ) {
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT for selection...")

        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    Log.d("Butler", "Selection transcript: '$transcript'")

                    if (transcript.isBlank()) {
                        // ✅ Give ONE more extra attempt with extra patience
                        speak("I didn't hear you. Please say 1, 2, or 3 clearly.") {
                            Handler(Looper.getMainLooper()).postDelayed({
                                sarvamSTT.startListening(
                                    onResult = { t2 ->
                                        runOnUiThread {
                                            val t = t2.trim()
                                            val num = detectNumberFromSpeech(t)
                                            if (num > 0) onNumber(num) else if (t.isNotBlank()) onOther(t) else speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) }
                                        }
                                    },
                                    onError = { runOnUiThread { speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) } } }
                                )
                            }, 500) // 500ms pause before re-listening
                        }
                        return@runOnUiThread
                    }

                    val num = detectNumberFromSpeech(transcript)
                    if (num > 0) {
                        Log.d("Butler", "Number detected: $num from '$transcript'")
                        onNumber(num)
                    } else {
                        onOther(transcript)
                    }
                }
            },
            onError = {
                runOnUiThread {
                    speak("Didn't catch that. Say 1, 2, or 3.") {
                        startListeningForSelection(onNumber, onOther, retryPrompt)
                    }
                }
            }
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SERVICE FLOW
    // ══════════════════════════════════════════════════════════════════════════

    private fun launchServiceFlow(transcript: String) {
        currentState = AssistantState.IN_SERVICE_FLOW
        try { stopLockTask() } catch (_: Exception) {}
        val serviceIntent = ServiceManager.detectServiceIntent(transcript)
        val intent = Intent(this, ServiceActivity::class.java).apply {
            putExtra(ServiceActivity.EXTRA_SECTOR,   serviceIntent.sector?.name ?: "")
            putExtra(ServiceActivity.EXTRA_QUERY,    transcript)
            putExtra(ServiceActivity.EXTRA_IS_RX,    serviceIntent.isPrescription)
            putExtra(ServiceActivity.EXTRA_IS_EMERG, serviceIntent.isEmergency)
            putExtra("user_lat", userLocation?.latitude  ?: 0.0)
            putExtra("user_lng", userLocation?.longitude ?: 0.0)
        }
        serviceLauncher.launch(intent)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN COMMAND HANDLER
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleCommand(text: String) {
        val lower   = text.lowercase().trim()
        val cleaned = lower.replace(Regex("[,।.!?؟]"), "").trim()
        val lang    = LanguageManager.getLanguage()

        when (currentState) {

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    cleaned.contains("new") || cleaned.contains("first") || cleaned.contains("register") ||
                            cleaned.contains("नया") || cleaned.contains("నొత్త") || cleaned.contains("புதிய") -> {
                        currentState = AssistantState.ASKING_NAME
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_NAME, prompt = "What is your full name?"))
                        speak("Great! What is your full name?") { startListening() }
                    }
                    cleaned.contains("returning") || cleaned.contains("before") || cleaned.contains("existing") ||
                            cleaned.contains("login") || cleaned.contains("yes") || cleaned.contains("पहले") -> {
                        currentState = AssistantState.ASKING_EMAIL
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_EMAIL, prompt = EmailPasswordParser.getEmailPrompt(lang)))
                        speak(EmailPasswordParser.getEmailPrompt(lang)) { startListening() }
                    }
                    else -> speak("Please say new customer, or returning customer.") { startListening() }
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
                        runOnUiThread { speak("Please say just your name clearly.") { startListening() } }; return@launch
                    }
                    tempName = nameText.split(" ").firstOrNull { it.length > 1 }?.replaceFirstChar { it.uppercase() }
                        ?: nameText.replaceFirstChar { it.uppercase() }
                    runOnUiThread {
                        currentState = AssistantState.ASKING_EMAIL
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_EMAIL, collectedName = tempName,
                            prompt = "Nice to meet you $tempName! Please spell your email, letter by letter."))
                        speak("Nice to meet you $tempName! Please spell your email address, letter by letter. For example: r-a-v-i at gmail dot com") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_EMAIL -> {
                val parsed = EmailPasswordParser.parseEmail(text)
                if (parsed != null) {
                    tempEmail = parsed; emailRetryCount = 0
                    Log.d("Butler", "Email: $tempEmail")
                    currentState = AssistantState.ASKING_PHONE
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PHONE, collectedName = tempName, collectedEmail = tempEmail,
                        prompt = "What is your 10-digit mobile number?"))
                    speak("Got it — $tempEmail. Now, what is your 10-digit mobile number?") { startListening() }
                } else {
                    emailRetryCount++
                    if (emailRetryCount <= 2) speak(EmailPasswordParser.getEmailRetryPrompt(lang)) { startListening() }
                    else {
                        val fallback = EmailPasswordParser.parseEmailBestEffort(text)
                        if (fallback.contains("@")) {
                            tempEmail = fallback; emailRetryCount = 0
                            currentState = AssistantState.ASKING_PHONE
                            setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PHONE, collectedName = tempName, collectedEmail = tempEmail))
                            speak("OK, I got $tempEmail. What is your mobile number?") { startListening() }
                        } else {
                            emailRetryCount = 0
                            speak("I'm having trouble. Let me show you the sign-in screen.") {
                                try { stopLockTask() } catch (_: Exception) {}
                                authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                            }
                        }
                    }
                }
            }

            AssistantState.ASKING_PHONE -> {
                val digits = text.replace(Regex("[^0-9]"), "")
                val phone  = when {
                    digits.length == 10 -> digits
                    digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
                    digits.length > 10  -> digits.takeLast(10)
                    else -> ""
                }
                if (phone.length == 10) {
                    tempPhone = phone; phoneRetryCount = 0
                    currentState = AssistantState.ASKING_PASSWORD
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PASSWORD, tempName, tempEmail, tempPhone,
                        "Now choose a password. Spell each character clearly."))
                    speak("Got it — $tempPhone. Now choose a password. Spell each character clearly.") { startListening() }
                } else {
                    phoneRetryCount++
                    if (phoneRetryCount <= 2) speak("Please say your 10-digit mobile number again clearly, digit by digit.") { startListening() }
                    else {
                        phoneRetryCount = 0
                        currentState = AssistantState.ASKING_PASSWORD
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PASSWORD, tempName, tempEmail,
                            prompt = "Let's set your password. Spell each character clearly."))
                        speak("No problem, let's skip phone. Please choose a password.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = EmailPasswordParser.parsePassword(text).ifBlank { text.trim().replace(" ", "").trimEnd('.', ',', '!') }
                Log.d("Butler", "Password length: ${password.length}")
                if (password.length < 6) { speak("Password must be at least 6 characters. Please try again.") { startListening() }; return }
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.CREATING_ACCOUNT, tempName, tempEmail))
                        speak("Creating your account, please wait.") {}
                        UserSessionManager.signup(tempEmail, password, tempName, tempPhone).fold(
                            onSuccess = { profile ->
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                AnalyticsManager.logUserAuth("voice_signup", LanguageManager.getLanguage())
                                speak(IndianLanguageProcessor.getWelcomeGreeting(lang, firstName)) { startListening() }
                            },
                            onFailure = { error ->
                                if (error.message?.contains("user_already_exists") == true || error.message?.contains("already registered") == true)
                                    speak("Account already exists. Logging you in.") { lifecycleScope.launch { doLogin(tempEmail, password) } }
                                else
                                    speak("Sorry, couldn't create account. Please try again.") { startWakeWordListening() }
                            }
                        )
                    } else {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.LOGGING_IN))
                        doLogin(tempEmail, password)
                    }
                }
            }

            AssistantState.REORDER_CONFIRM -> {
                when {
                    MultilingualMatcher.isYes(cleaned) || IndianLanguageProcessor.detectIntent(cleaned) == "confirm" -> {
                        lifecycleScope.launch {
                            for (s in pendingReorderSuggestions)
                                apiClient.searchProduct(s.productName)?.let { cart.add(CartItem(it, s.avgQty)) }
                            runOnUiThread {
                                if (cart.isNotEmpty()) {
                                    currentState = AssistantState.CONFIRMING
                                    showCartAndSpeak("Perfect! Added ${cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }}. Shall I place the order?") { startListening() }
                                } else {
                                    currentState = AssistantState.LISTENING
                                    speak("Couldn't find those products. What would you like?") { startListening() }
                                }
                            }
                        }
                    }
                    MultilingualMatcher.isNo(cleaned) -> {
                        pendingReorderSuggestions = emptyList(); currentState = AssistantState.LISTENING
                        speak("No problem! What would you like today?") { startListening() }
                    }
                    else -> { pendingReorderSuggestions = emptyList(); currentState = AssistantState.LISTENING; handleOrderIntent(text, lower) }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // LISTENING — services OR grocery
            // ══════════════════════════════════════════════════════════════════

            AssistantState.LISTENING -> {
                if (ServiceVoiceHandler.isEmergency(lower)) {
                    speak("Emergency! Connecting to emergency services right away.") { launchServiceFlow(text) }; return
                }
                if (ServiceVoiceHandler.isPrescriptionRequest(lower)) {
                    speak("I'll help you order from your prescription. Opening camera now.") { launchServiceFlow(text) }; return
                }
                if (ServiceVoiceHandler.isServiceRequest(lower)) {
                    val intent = ServiceManager.detectServiceIntent(text)
                    speak("Finding ${intent.sector?.displayName ?: "service"} providers near you.") { launchServiceFlow(text) }; return
                }
                handleOrderIntent(text, lower)
            }

            AssistantState.ASKING_QUANTITY -> {
                val qty = extractQuantity(text); val product = tempProduct
                if (product != null) {
                    cart.add(CartItem(product, qty)); currentState = AssistantState.ASKING_MORE
                    val suggestion = suggestRelatedItem(product.name)
                    showCartAndSpeak(if (suggestion != null && cart.size == 1)
                        "Added $qty ${product.name}. Would you also like $suggestion? Or say done to checkout."
                    else "Added $qty ${product.name}. More items or say done to checkout?") { startListening() }
                } else speak("Let's try again. What would you like?") { currentState = AssistantState.LISTENING; startListening() }
            }

            AssistantState.ASKING_MORE    -> handleAskingMore(cleaned, text)
            AssistantState.EDITING_CART   -> handleCartEdit(cleaned, text)

            AssistantState.CONFIRMING -> {
                val intentFromLang = IndianLanguageProcessor.detectIntent(cleaned)
                when {
                    intentFromLang == "confirm" || MultilingualMatcher.isYes(cleaned) -> askPaymentMode()
                    intentFromLang == "cancel"  || MultilingualMatcher.isNo(cleaned)  ->
                        speak("Order cancelled. Goodbye!") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                    isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, text) }
                    else -> {
                        lifecycleScope.launch {
                            val parsed = AIOrderParser.parse(text); LanguageManager.setLanguage(parsed.detectedLanguage)
                            runOnUiThread {
                                when (parsed.intent) {
                                    "confirm_order" -> askPaymentMode()
                                    "cancel_order"  -> speak("Order cancelled.") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                                    else -> speak("Say yes to place the order, or no to cancel.") { startListening() }
                                }
                            }
                        }
                    }
                }
            }

            AssistantState.ASKING_PAYMENT_MODE  -> handlePaymentModeChoice(cleaned)
            AssistantState.WAITING_CARD_PAYMENT -> handlePaymentConfirmation(cleaned, "card")
            AssistantState.WAITING_UPI_PAYMENT  -> handlePaymentConfirmation(cleaned, "upi")
            AssistantState.WAITING_QR_PAYMENT   -> handlePaymentConfirmation(cleaned, "qr")
            AssistantState.CONFIRMING_CARD_PAID -> handlePaidOrNotPaid(cleaned, "card")
            AssistantState.CONFIRMING_UPI_PAID  -> handlePaidOrNotPaid(cleaned, "upi")
            AssistantState.CONFIRMING_QR_PAID   -> handlePaidOrNotPaid(cleaned, "qr")

            else -> {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT
    // ══════════════════════════════════════════════════════════════════════════

    private fun askPaymentMode() {
        pendingOrderTotal   = cart.sumOf { it.product.price * it.quantity }
        pendingOrderSummary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.ASKING_PAYMENT_MODE
        val card     = PaymentManager.getSavedCard(this)
        val hasSaved = card != null
        val cardInfo = if (card != null) "${card.network} card ending ${card.last4}" else ""
        setUiState(ButlerUiState.PaymentChoice(pendingOrderTotal, pendingOrderSummary, hasSaved, cardInfo))
        val amount   = toSpeakableAmount(pendingOrderTotal)
        val cardPart = if (hasSaved) "say card to pay with your saved $cardInfo," else "say card to add a new card,"
        speak("Your total is $amount. How would you like to pay? $cardPart say UPI to pay with UPI, or say QR to scan a QR code.") { startListening() }
    }

    private fun handlePaymentModeChoice(cleaned: String) {
        when {
            cleaned.contains("card") || cleaned.contains("debit") || cleaned.contains("credit") ||
                    cleaned.contains("saved") || cleaned.contains("कार्ड") || cleaned.contains("కార్డ్") -> {
                val card   = PaymentManager.getSavedCard(this)
                val amount = toSpeakableAmount(pendingOrderTotal)
                currentState = AssistantState.WAITING_CARD_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("card", pendingOrderTotal))
                speak(if (card != null) "I'll charge your ${card.network} card ending ${card.last4} for $amount. Please complete the payment."
                else "Please enter your card details and complete the payment for $amount.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("card") }, 2000)
                }
            }
            cleaned.contains("upi") || cleaned.contains("google pay") || cleaned.contains("phonepe") ||
                    cleaned.contains("paytm") || cleaned.contains("bhim") || cleaned.contains("यूपीआई") -> {
                val amount = toSpeakableAmount(pendingOrderTotal)
                currentState = AssistantState.WAITING_UPI_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("upi", pendingOrderTotal))
                speak("Open your UPI app and pay $amount to butler at upi. Take your time.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("upi") }, 3000)
                }
            }
            cleaned.contains("qr") || cleaned.contains("scan") || cleaned.contains("scanner") ||
                    cleaned.contains("क्यूआर") || cleaned.contains("స్కాన్") -> {
                val amount = toSpeakableAmount(pendingOrderTotal)
                currentState = AssistantState.WAITING_QR_PAYMENT
                setUiState(ButlerUiState.ShowQRCode(pendingOrderTotal, pendingOrderSummary))
                speak("I've shown a QR code on screen. Scan it with any UPI app to pay $amount. Take your time.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("qr") }, 5000)
                }
            }
            else -> speak("I didn't understand. Say card, UPI, or QR to choose.") { startListening() }
        }
    }

    private fun askIfPaid(mode: String) {
        currentState = when (mode) { "card" -> AssistantState.CONFIRMING_CARD_PAID; "upi" -> AssistantState.CONFIRMING_UPI_PAID; else -> AssistantState.CONFIRMING_QR_PAID }
        speak("Have you completed the payment of ${toSpeakableAmount(pendingOrderTotal)}? Say yes or paid if you have, or no if not yet.") { startListening() }
    }

    private fun handlePaidOrNotPaid(cleaned: String, mode: String) {
        val paid    = listOf("yes","paid","done","payment done","i paid","completed","successful","transferred","confirm","हाँ","हां","हो गया","पेमेंट हो गया","అవును","చేశాను","ஆம்","ha","kar diya","ho gaya")
        val notPaid = listOf("no","not yet","haven't","didn't","wait","not done","failed","नहीं","अभी नहीं","నహీ","ఇంకా","nahi","abhi nahi","ruko")
        when {
            paid.any    { cleaned.contains(it) } -> speak("Great! Payment confirmed. Placing your order now.") { placeOrder() }
            notPaid.any { cleaned.contains(it) } -> {
                val amount = toSpeakableAmount(pendingOrderTotal)
                speak(when (mode) {
                    "card" -> "No problem! Please complete the card payment of $amount."
                    "upi"  -> "No problem! Please pay $amount to butler at upi in your UPI app."
                    else   -> "No problem! Please scan the QR code and pay $amount."
                }) { Handler(Looper.getMainLooper()).postDelayed({ askIfPaid(mode) }, 8000) }
            }
            else -> speak("Say yes if you have paid, or no if you haven't.") { startListening() }
        }
    }

    private fun handlePaymentConfirmation(cleaned: String, mode: String) { askIfPaid(mode) }

    // ══════════════════════════════════════════════════════════════════════════
    // CART EDITING
    // ══════════════════════════════════════════════════════════════════════════

    private fun isCartEditIntent(s: String): Boolean =
        listOf("remove","delete","हटाओ","హటావో","ತೆಗೆ","change","update","बदलो","మార్చు","add more","increase","more").any { s.contains(it) }

    private fun handleCartEdit(cleaned: String, originalText: String) {
        if (cart.isEmpty()) { speak("Cart is empty. What would you like?") { currentState = AssistantState.LISTENING; startListening() }; return }
        val isRemove = cleaned.contains("remove") || cleaned.contains("delete") || cleaned.contains("हटाओ") || cleaned.contains("हटा")
        val isChange = cleaned.contains("change") || cleaned.contains("update") || cleaned.contains("बदलो") || cleaned.contains("make it")
        if (isRemove) {
            val item = cart.firstOrNull { it.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (item != null) {
                cart.remove(item)
                if (cart.isEmpty()) speak("Removed ${item.product.name}. Cart empty.") { currentState = AssistantState.LISTENING; startListening() }
                else { currentState = AssistantState.CONFIRMING; showCartAndSpeak("Removed ${item.product.name}. Shall I proceed to payment?") { startListening() } }
            } else speak("Couldn't find that. Which item to remove?") { startListening() }
            return
        }
        if (isChange) {
            val newQty = extractQuantity(cleaned)
            val item   = cart.firstOrNull { it.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (item != null && newQty > 0) {
                cart[cart.indexOf(item)] = CartItem(item.product, newQty)
                currentState = AssistantState.CONFIRMING
                showCartAndSpeak("Updated ${item.product.name} to $newQty. Shall I proceed?") { startListening() }
            } else speak("Tell me which item to change and the new quantity.") { startListening() }
            return
        }
        currentState = AssistantState.CONFIRMING; readCartAndConfirm()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ORDER INTENT
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleOrderIntent(text: String, lower: String) {
        val cleaned = lower.replace(Regex("[,।.!?]"), "").trim()
        if (cleaned.contains("repeat") || cleaned.contains("same as last")) { repeatLastOrder(); return }
        if (cleaned.contains("my orders") || cleaned.contains("history"))   { readOrderHistory(); return }
        if (isNoMoreIntent(cleaned)) { readCartAndConfirm(); return }
        if (cart.isNotEmpty() && isCartEditIntent(cleaned)) { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, text); return }
        val regional = IndianLanguageProcessor.normalizeProduct(cleaned)
        if (regional != cleaned && regional.isNotBlank()) { searchAndAskQuantity(regional); return }
        lifecycleScope.launch {
            val parsed = AIOrderParser.parse(text); LanguageManager.setLanguage(parsed.detectedLanguage)
            runOnUiThread {
                when (parsed.intent) {
                    "finish_order"  -> { if (cart.isEmpty()) speak("Cart is empty. What would you like?") { startListening() } else readCartAndConfirm() }
                    "confirm_order" -> { if (currentState == AssistantState.CONFIRMING) askPaymentMode() else readCartAndConfirm() }
                    "cancel_order"  -> speak("Cancelled.") { startWakeWordListening() }
                    "history"       -> readOrderHistory()
                    "order", "add_more" -> {
                        if (parsed.items.isEmpty()) { val fb = keywordFallback(cleaned); if (fb != null) searchAndAskQuantity(fb) else speak("Tell me what you want to order.") { startListening() } }
                        else if (parsed.items.size == 1) { val i = parsed.items.first(); searchAndAskQuantity(i.name, i.quantity, i.unit) }
                        else lifecycleScope.launch { addMultipleItemsToCart(parsed.items) }
                    }
                    else -> speak("Tell me what you want to order.") { startListening() }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ASKING MORE
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleAskingMore(cleaned: String, originalText: String) {
        when {
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("add") || cleaned.contains("more") ||
                    cleaned.contains("और") || cleaned.contains("aur") -> {
                currentState = AssistantState.LISTENING; showCartAndSpeak("What else would you like?") { startListening() }
            }
            isNoMoreIntent(cleaned) -> readCartAndConfirm()
            isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, originalText) }
            else -> {
                lifecycleScope.launch {
                    val parsed = AIOrderParser.parse(originalText); LanguageManager.setLanguage(parsed.detectedLanguage)
                    runOnUiThread {
                        when (parsed.intent) {
                            "finish_order", "cancel_order" -> readCartAndConfirm()
                            "order", "add_more" -> {
                                if (parsed.items.isNotEmpty()) { currentState = AssistantState.LISTENING; handleOrderIntent(originalText, originalText.lowercase()) }
                                else showCartAndSpeak("Add more or checkout?") { startListening() }
                            }
                            else -> showCartAndSpeak("Add more, or say done to checkout?") { startListening() }
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ✅ PRODUCT SEARCH — uses startListeningForSelection for 1/2/3
    // ══════════════════════════════════════════════════════════════════════════

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        lifecycleScope.launch {
            val recs = productRepo.getTopRecommendations(itemName, userLocation)
            if (recs.isNotEmpty()) {
                runOnUiThread { setUiState(ButlerUiState.ShowingRecommendations(itemName, recs)) }
                val readout = "I found ${recs.size} options for $itemName. " +
                        recs.mapIndexed { i, r -> "${i + 1}: ${r.productName} at ${toSpeakableAmount(r.priceRs)}" }.joinToString(". ") +
                        ". Say 1, 2, or 3 to pick."

                // ✅ Use extended selection listener — gives more time, handles all tones
                speakKeepingRecsVisible(readout) {
                    startListeningForSelection(
                        onNumber = { num ->
                            handleRecSelectionByIndex(num - 1, recs, qty, itemName)
                        },
                        onOther = { spoken ->
                            // Try name match
                            val pick = recs.firstOrNull { r ->
                                val rw = r.productName.lowercase().split(" ")
                                val sw = spoken.lowercase().split(" ").filter { it.length > 2 }
                                sw.any { s -> rw.any { w -> w.contains(s) || s.contains(w) } }
                            }
                            if (pick != null) {
                                handleRecSelectionByIndex(recs.indexOf(pick), recs, qty, itemName)
                            } else {
                                speakKeepingRecsVisible("Please say 1, 2, or 3 to select a product.") {
                                    startListeningForSelection(
                                        onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                                        onOther  = { _ -> speakKeepingRecsVisible("Please say 1, 2, or 3.") { startListeningForSelection(
                                            onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                                            onOther  = { _ -> speak("Let's try again. What would you like?") { currentState = AssistantState.LISTENING; startListening() } }
                                        ) } }
                                    )
                                }
                            }
                        },
                        retryPrompt = "Please say 1, 2, or 3 to choose a product."
                    )
                }
            } else {
                val product = apiClient.searchProduct(itemName)
                runOnUiThread {
                    if (product != null) {
                        tempProduct = product
                        if (qty > 0) {
                            cart.add(CartItem(product, qty)); currentState = AssistantState.ASKING_MORE
                            showCartAndSpeak("Added $qty ${product.name}. More items or checkout?") { startListening() }
                        } else { currentState = AssistantState.ASKING_QUANTITY; speak("How many ${product.name} would you like?") { startListening() } }
                    } else speak("Couldn't find $itemName. What else would you like?") { startListening() }
                }
            }
        }
    }

    // ✅ FIX: uses index directly (0-based) — no more type mismatch
    private fun handleRecSelectionByIndex(
        index: Int,
        recs: List<com.demo.butler_voice_app.api.ProductRecommendation>,
        qty: Int,
        itemName: String
    ) {
        val pick = recs.getOrNull(index)
        if (pick != null) {
            val finalQty = if (qty > 0) qty else 1
            // ✅ BUILD FIX: correct named parameters for ApiClient.Product
            val product = ApiClient.Product(
                id    = pick.productId,
                name  = pick.productName,
                price = pick.priceRs,
                unit  = pick.unit
            )
            cart.add(CartItem(product, finalQty))
            val suggestion = suggestRelatedItem(pick.productName)
            currentState   = AssistantState.ASKING_MORE
            showCartAndSpeak(
                if (suggestion != null && cart.size == 1) "Added ${pick.productName} from ${pick.storeName}. Would you also like $suggestion? Or say done to checkout."
                else "Added ${pick.productName} from ${pick.storeName}. More items or say done to checkout?"
            ) { startListening() }
        } else {
            speakKeepingRecsVisible("Sorry, that option isn't available. Please say 1, 2, or 3.") {
                startListeningForSelection(
                    onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                    onOther  = { _ -> speak("Please say 1, 2, or 3.") { startListeningForSelection(
                        onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                        onOther  = { _ -> speak("Let's try again. What would you like?") { currentState = AssistantState.LISTENING; startListening() } }
                    ) } }
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PLACE ORDER
    // ══════════════════════════════════════════════════════════════════════════

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
                lastOrderId = orderResult.id; lastPublicId = shortId; lastOrderTotal = orderResult.total_amount
                val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
                setUiState(ButlerUiState.OrderPlaced(shortId, orderResult.total_amount, cartItems, 30, firstName))
                speak(IndianLanguageProcessor.getOrderConfirmation(lang, firstName, shortId)) {
                    try { stopLockTask() } catch (_: Exception) {}
                    startActivity(Intent(this@MainActivity, DeliveryTrackingActivity::class.java).apply {
                        putExtra("order_id", lastOrderId); putExtra("public_id", lastPublicId)
                        putExtra("total", lastOrderTotal); putExtra("summary", pendingOrderSummary)
                    })
                    cart.clear(); UserSessionManager.logout()
                    Handler(Looper.getMainLooper()).postDelayed({ startWakeWordListening() }, 8000)
                }
            } catch (e: Exception) {
                Log.e("Butler", "Order failed: ${e.message}")
                runOnUiThread { currentState = AssistantState.CONFIRMING; showCartAndSpeak("Network issue. Say yes to retry or no to cancel.") { startListening() } }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CART HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private fun showCartAndSpeak(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("Butler", "Original: $text | Translated ($lang): $finalText")
            val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
            val total     = cart.sumOf { it.product.price * it.quantity }
            runOnUiThread {
                if (currentState == AssistantState.CONFIRMING || currentState == AssistantState.ASKING_MORE)
                    setUiState(ButlerUiState.CartReview(cartItems, total, text))
                else
                    setUiState(ButlerUiState.Speaking(finalText, cart = cartItems))
                ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() })
            }
        }
    }

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) { speak("Cart is empty. What would you like?") { currentState = AssistantState.LISTENING; startListening() }; return }
        currentState = AssistantState.CONFIRMING
        val total = cart.sumOf { it.product.price * it.quantity }
        showCartAndSpeak("You have ${cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }}. Total is ${toSpeakableAmount(total)}. Say yes to proceed to payment, or tell me what to change.") { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>(); val notFound = mutableListOf<String>()
        for (item in items) {
            val sn = IndianLanguageProcessor.normalizeProduct(item.name).ifBlank { item.name }
            val p  = apiClient.searchProduct(sn)
            if (p != null) { cart.add(CartItem(p, item.quantity)); found.add("${item.quantity} ${p.name}") }
            else { notFound.add(item.name); AnalyticsManager.logItemNotFound(item.name) }
        }
        runOnUiThread {
            currentState = AssistantState.ASKING_MORE
            showCartAndSpeak(if (notFound.isEmpty()) "Added ${found.joinToString(", ")}. More or checkout?"
            else "Added ${found.joinToString(", ")}. Couldn't find ${notFound.joinToString(", ")}. Anything else?") { startListening() }
        }
    }

    private fun isNoMoreIntent(s: String): Boolean {
        if (MultilingualMatcher.isDone(s)) return true
        if (IndianLanguageProcessor.DONE_PHRASES.any { s.contains(it, ignoreCase = true) }) return true
        return listOf("no","nope","done","nothing","finish","stop","checkout","place order","बस","हो गया","bas","nahi","kar do").any { s.contains(it) }
    }

    private fun suggestRelatedItem(p: String): String? = when {
        p.lowercase().contains("rice")  -> "dal"
        p.lowercase().contains("dal")   -> "rice"
        p.lowercase().contains("milk")  -> "sugar"
        p.lowercase().contains("bread") -> "butter"
        p.lowercase().contains("oil")   -> "salt"
        p.lowercase().contains("atta")  -> "oil"
        else -> null
    }

    private fun keywordFallback(s: String): String? {
        val r = IndianLanguageProcessor.normalizeProduct(s); if (r != s) return r
        return when {
            s.contains("rice")  || s.contains("चावल")  || s.contains("అన్నం")  -> "rice"
            s.contains("oil")   || s.contains("तेल")   || s.contains("నూనె")   -> "oil"
            s.contains("sugar") || s.contains("चीनी")  || s.contains("చక్కెర") -> "sugar"
            s.contains("dal")   || s.contains("दाल")   || s.contains("పప్పు")  -> "dal"
            s.contains("salt")  || s.contains("नमक")   || s.contains("ఉప్పు")  -> "salt"
            s.contains("milk")  || s.contains("दूध")   || s.contains("పాలు")   -> "milk"
            s.contains("wheat") || s.contains("atta")  || s.contains("गेहूं")  -> "wheat flour"
            s.contains("tea")   || s.contains("चाय")   || s.contains("టీ")     -> "tea"
            s.contains("ghee")  || s.contains("घी")    -> "ghee"
            s.contains("bread") || s.contains("रोटी")  -> "bread"
            s.contains("eggs")  || s.contains("egg")   || s.contains("अंडा") -> "eggs"
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
                    val greeting    = if (lastProduct != null) "Welcome back $firstName! Last time you ordered $lastProduct. What would you like?"
                    else IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)
                    speak(greeting) { startListening() }
                }
            },
            onFailure = { runOnUiThread { speak("Login failed. Please check your email and password.") { startWakeWordListening() } } }
        )
    }

    private fun readOrderHistory() {
        val h = UserSessionManager.purchaseHistory
        if (h.isEmpty()) speak("No orders yet. What would you like?") { startListening() }
        else speak("Recent orders: ${h.take(3).joinToString(", ") { it.product_name ?: "unknown" }}. Order again?") { startListening() }
    }

    private fun repeatLastOrder() {
        lifecycleScope.launch {
            val userId = UserSessionManager.currentUserId() ?: return@launch
            val orders = apiClient.getOrderHistory(userId)
            if (orders.isEmpty()) { runOnUiThread { speak("No previous orders.") { startListening() } }; return@launch }
            val items  = apiClient.getOrderItems(orders.first().id)
            if (items.isEmpty()) { runOnUiThread { speak("Couldn't load last order.") { startListening() } }; return@launch }
            for (item in items) { apiClient.searchProduct(item.product_name)?.let { cart.add(CartItem(it, item.quantity)) } }
            runOnUiThread { currentState = AssistantState.CONFIRMING; showCartAndSpeak("Repeating your last order. Say yes to pay.") { startListening() } }
        }
    }

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
            Log.d("Butler", "Original: $text"); Log.d("Butler", "Translated ($lang): $finalText")
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
                    put("messages", org.json.JSONArray().apply { put(org.json.JSONObject().apply { put("role", "user"); put("content", "Translate to English. Return ONLY the translated text: $text") }) })
                    put("max_tokens", 50); put("temperature", 0.1)
                }.toString().toRequestBody("application/json".toMediaType())
                val response = okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder().url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                        .addHeader("Content-Type", "application/json").post(body).build()
                ).execute()
                org.json.JSONObject(response.body?.string() ?: return@withContext text)
                    .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } catch (e: Exception) { Log.e("Butler", "translateToEnglish failed: ${e.message}"); text }
        }
    }
}