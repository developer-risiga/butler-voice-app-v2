package com.demo.butler_voice_app

import android.content.Intent
import com.demo.butler_voice_app.api.FamilyMember
import com.demo.butler_voice_app.api.FamilyProfileManager
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
import com.demo.butler_voice_app.services.ServiceSector
import com.demo.butler_voice_app.services.ServiceVoiceHandler
import androidx.activity.result.contract.ActivityResultContracts
import com.demo.butler_voice_app.ai.MultilingualMatcher
import com.demo.butler_voice_app.ai.IndianLanguageProcessor
import com.demo.butler_voice_app.ai.EmailPasswordParser
import com.demo.butler_voice_app.ai.MoodDetector
import com.demo.butler_voice_app.ai.MoodAdapter
import com.demo.butler_voice_app.ai.UserMood
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
import com.demo.butler_voice_app.EmotionTone
import com.demo.butler_voice_app.ai.AIOrderParser
import com.demo.butler_voice_app.ai.AIParser
import com.demo.butler_voice_app.ai.IntentRouting
import com.demo.butler_voice_app.ai.LanguageDetector
import com.demo.butler_voice_app.ai.LanguageManager
import com.demo.butler_voice_app.ai.SessionLanguageManager
import com.demo.butler_voice_app.ai.StatusCheckHandler
import com.demo.butler_voice_app.ai.StatusQueryResult
import com.demo.butler_voice_app.ai.TranslationManager
import com.demo.butler_voice_app.api.ApiClient
import com.demo.butler_voice_app.api.SessionStore
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.ui.ButlerScreen
import com.demo.butler_voice_app.ui.ButlerUiState
import com.demo.butler_voice_app.ui.CartDisplayItem
import com.demo.butler_voice_app.utils.ButlerPhraseBank
import com.demo.butler_voice_app.utils.ButlerPersonalityEngine
import com.demo.butler_voice_app.utils.HumanFillerManager
import com.demo.butler_voice_app.voice.SarvamSTTManager
import com.demo.butler_voice_app.workers.ProactiveButlerWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.demo.butler_voice_app.api.PriceComparisonEngine
import com.demo.butler_voice_app.api.StorePrice
import com.demo.butler_voice_app.api.PriceComparison
import com.demo.butler_voice_app.api.ProductRecommendation
import java.util.concurrent.TimeUnit

enum class AssistantState {
    IDLE, CHECKING_AUTH,
    ASKING_IS_NEW_USER, ASKING_NAME, ASKING_EMAIL, ASKING_PHONE, ASKING_PASSWORD,
    LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING, REORDER_CONFIRM, EDITING_CART,
    CONFIRMING_ADD_PRODUCT,  // template 3: waiting yes/no after price confirmation
    ASKING_PRODUCT_TYPE,     // template 2: waiting for basmati/brown/normal
    ASKING_PAYMENT_MODE,
    WAITING_CARD_PAYMENT, WAITING_UPI_PAYMENT, WAITING_QR_PAYMENT,
    CONFIRMING_CARD_PAID, CONFIRMING_UPI_PAID, CONFIRMING_QR_PAID,
    IN_SERVICE_FLOW,
    IN_SERVICE_SUBTYPE_FLOW,
    ASKING_WHO
}

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
    private var sttRetryCount    = 0
    private var totalEmptyRetries = 0
    private var sttErrorCount    = 0

    private var pendingReorderSuggestions: List<ReorderSuggestion> = emptyList()
    private var lastOrderId    = ""
    private var lastPublicId   = ""
    private var lastOrderTotal = 0.0

    private var sessionLastProduct: String? = null
    private var sessionLastQty: Int = 0
    private var lastBookingId: String? = null

    // ── Template personalization ──────────────────────────────────────────
    // Set in proceedAfterIdentification so every BPE call can use {name}
    private var sessionUserName: String = "ji"

    // ── Template 2+3: pending product confirmation fields ─────────────────
    // Set when user picks a brand; we show price + ask before adding to cart
    private var pendingAddRecs: List<com.demo.butler_voice_app.api.ProductRecommendation> = emptyList()
    private var pendingAddIndex: Int = 0
    private var pendingAddQty: Int = 1
    private var pendingAddItemName: String = ""

    // ── Template 2: product type pending ──────────────────────────────────
    private var pendingProductCategory: String = ""

    private var pendingProactiveData: ProactiveData? = null
    private var currentMood: UserMood = UserMood.CALM

    private data class ServiceSubTypeSession(
        val sector: ServiceSector,
        val originalTranscript: String,
        var retryCount: Int = 0
    )
    private var serviceSubTypeSession: ServiceSubTypeSession? = null

    private lateinit var ttsManager : TTSManager
    private lateinit var sarvamSTT  : SarvamSTTManager
    private lateinit var porcupine  : WakeWordManager

    private val apiClient   = ApiClient()
    private val cart        = mutableListOf<CartItem>()
    private var tempProduct : ApiClient.Product? = null
    private var currentState = AssistantState.IDLE
    private val recordRequestCode = 101

    @Volatile private var sttListenId = 0

    private fun toSpeakableAmount(amount: Double): String {
        val n    = amount.toInt()
        val lang = LanguageManager.getLanguage()
        return if (lang.startsWith("hi") || lang.startsWith("te") || lang.startsWith("mr"))
            "${com.demo.butler_voice_app.utils.ButlerSpeechFormatter.numberToHindi(n)} rupaye"
        else
            "$n rupees"
    }

    // ══════════════════════════════════════════════════════════════════════
    // HUMAN HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun speakFillerThen(action: () -> Unit) {
        action()
    }

    private fun speakWithTransition(text: String, onDone: (() -> Unit)? = null) {
        val transition = HumanFillerManager.getTransition(LanguageManager.getLanguage())
        speak("$transition $text", onDone)
    }

    private fun speakWithMoodContext(text: String, onDone: (() -> Unit)? = null) {
        val lang    = LanguageManager.getLanguage()
        val moodAck = HumanFillerManager.getMoodAck(currentMood, lang)
        speak(if (moodAck != null) "$moodAck $text" else text, onDone)
    }

    private fun buildShortConfirm(lang: String): String {
        val items = cart.joinToString(", ") { item ->
            val n = item.product.name.lowercase().split(" ")
                .take(2).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            "${item.quantity} $n"
        }
        val total = "₹${cart.sumOf { it.product.price * it.quantity }.toInt()}"
        // Template 6: "Theek hai… bas itna hi hai na {name}? Order place karna hai?"
        return ButlerPersonalityEngine.confirmOrder(sessionUserName, items, total, lang)
    }

    // ══════════════════════════════════════════════════════════════════════
    // CENTRAL ROUTER
    // ══════════════════════════════════════════════════════════════════════

    private fun routeTranscript(text: String, lower: String): Boolean {
        val lang = LanguageManager.getLanguage()

        val emotionTone = detectEmotionTone(lower)

        val isEmergency = ServiceVoiceHandler.isEmergency(lower) ||
                detectEmotionTone(lower) == EmotionTone.EMERGENCY
        if (isEmergency) {
            val emergencyText = when {
                lang.startsWith("hi") ->
                    "घबराइए मत! अभी ambulance बुला रहा हूँ। एक 08 पर call हो रहा है।"
                lang.startsWith("te") ->
                    "భయపడకండి! ఇప్పుడే ambulance పిలుస్తున్నాను. 108కు call అవుతోంది."
                lang.startsWith("ta") ->
                    "பயப்படாதீர்கள்! இப்போதே ambulance அழைக்கிறேன்."
                lang.startsWith("kn") ->
                    "ಭಯಪಡಬೇಡಿ! ಈಗಲೇ ambulance ಕರೆಯುತ್ತಿದ್ದೇನೆ."
                lang.startsWith("ml") ->
                    "പേടിക്കേണ്ട! ഇപ്പോൾ ambulance വിളിക്കുന്നു."
                else ->
                    "Don't worry! Calling ambulance right now. Dialing 108."
            }
            speak(emergencyText, EmotionTone.EMERGENCY) { launchServiceFlow(text) }
            return true
        }

        if (ServiceVoiceHandler.isPrescriptionRequest(lower)) {
            val rxText = when {
                lang.startsWith("hi") -> "ठीक है, prescription के लिए camera खोल रहा हूँ।"
                else -> "Opening camera for your prescription."
            }
            speak(rxText, EmotionTone.EMPATHETIC) { launchServiceFlow(text) }
            return true
        }

        if (currentState == AssistantState.LISTENING || currentState == AssistantState.REORDER_CONFIRM) {
            if (ServiceVoiceHandler.isServiceRequest(lower)) {
                val intent = ServiceManager.detectServiceIntent(text)
                val sector = intent.sector

                if (sector != null && ServiceVoiceHandler.hasSectorSubTypes(sector)) {
                    serviceSubTypeSession = ServiceSubTypeSession(sector, text)
                    currentState = AssistantState.IN_SERVICE_SUBTYPE_FLOW
                    val prompt = com.demo.butler_voice_app.services.ServiceVoiceEngine.subTypePrompt(sector, lang)
                    speak(prompt, emotionTone) { startListening() }
                } else {
                    val sectorName = sector?.let {
                        com.demo.butler_voice_app.ai.HindiSectorNames.get(it.name, lang)
                    } ?: "service"
                    val findText = when {
                        lang.startsWith("hi") -> "$sectorName ढूंढ रहा हूँ।"
                        else -> com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(sector ?: com.demo.butler_voice_app.services.ServiceSector.ELECTRICIAN, lang)
                    }
                    speak(findText, emotionTone) {
                        launchServiceFlow(text, overrideSector = sector)
                    }
                }
                return true
            }
        }

        if (StatusCheckHandler.isStatusQuery(lower) &&
            (currentState == AssistantState.LISTENING || currentState == AssistantState.REORDER_CONFIRM)) {
            handleStatusQuery(text)
            return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    // LAUNCHERS
    // ══════════════════════════════════════════════════════════════════════

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
                                FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: displayName
                                sessionUserName = firstName  // ← set name for BPE templates
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
                            FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                            currentState = AssistantState.LISTENING
                            val firstName = profile.full_name?.split(" ")?.first() ?: displayName.split(" ").first()
                            sessionUserName = firstName  // ← set name for BPE templates
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
                lastBookingId = bookingId
                val lang = LanguageManager.getLanguage()
                speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.returnToMain(bookingId, lang)) {
                    currentState = AssistantState.LISTENING
                    startListening()
                }
            } else {
                currentState = AssistantState.LISTENING
                speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.returnNoBooking(LanguageManager.getLanguage())) { startListening() }
            }
        }, 500)
    }

    // ══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════

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
        FamilyProfileManager.load(this)
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiClient.prefetchProducts()
                Log.d("Butler", "Cache warmed")
            } catch (_: Exception) {}
        }

        ProactiveButlerWorker.schedule(this)
        checkProactiveLaunch()
    }

    override fun onPause()   { super.onPause();   porcupine.stop(); sarvamSTT.stop() }
    override fun onDestroy() { super.onDestroy(); porcupine.stop(); sarvamSTT.stop(); ttsManager.shutdown() }
    override fun onResume()  {
        super.onResume()
        if (currentState == AssistantState.IDLE) { try { startLockTask() } catch (_: Exception) {} }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PROACTIVE BUTLER
    // ══════════════════════════════════════════════════════════════════════

    private fun checkProactiveLaunch() {
        val fromNotification = intent?.getBooleanExtra("proactive_launch", false) ?: false
        if (!fromNotification) return
        val data = ProactiveSession.consumePendingMessage(this) ?: return
        Log.d("Butler", "Proactive launch detected: ${data.productName}")
        currentState = AssistantState.CHECKING_AUTH
        lifecycleScope.launch {
            val restored = UserSessionManager.tryRestoreSession()
            runOnUiThread {
                if (restored && UserSessionManager.currentProfile != null) {
                    currentState = AssistantState.REORDER_CONFIRM
                    pendingProactiveData = data
                    speak(data.message) { startListening() }
                } else {
                    startWakeWordListening()
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOCATION
    // ══════════════════════════════════════════════════════════════════════

    private fun startLocationUpdates() {
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 30000L, 50f) { loc -> userLocation = loc }
                userLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) { Log.e("Butler", "Location error: ${e.message}") }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // WAKE WORD
    // ══════════════════════════════════════════════════════════════════════

    private fun startWakeWordListening() {
        currentState    = AssistantState.IDLE
        sttRetryCount   = 0
        emailRetryCount = 0
        phoneRetryCount = 0
        cart.clear()
        tempName = ""; tempEmail = ""; tempPhone = ""
        pendingReorderSuggestions = emptyList()
        pendingProactiveData      = null
        sessionLastProduct = null
        sessionLastQty     = 0
        serviceSubTypeSession = null
        currentMood = UserMood.CALM
        totalEmptyRetries = 0
        MoodDetector.reset()
        FamilyProfileManager.clearActive()
        LanguageManager.reset()
        SessionLanguageManager.reset()
        ButlerPersonalityEngine.resetSession()
        AIParser.resetDebounce()
        apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle)

        sttListenId++

        Log.d("Butler", "Waiting for wake word...")
        try { porcupine.stop() } catch (_: Exception) {}
        porcupine.start()
    }

    private fun onWakeWordDetected() {
        try { porcupine.stop() } catch (_: Exception) {}
        if (currentState != AssistantState.IDLE) {
            Log.d("Butler", "⚠️ Wake word ignored — state: $currentState"); return
        }
        currentState = AssistantState.CHECKING_AUTH
        setUiState(ButlerUiState.Thinking("Checking session…"))

        lifecycleScope.launch {
            var restored = UserSessionManager.tryRestoreSession()

            if (!restored && SessionStore.hasRefreshToken()) {
                Log.d("Butler", "Access token expired — attempting silent refresh")
                val refreshed = attemptTokenRefresh()
                if (refreshed) {
                    restored = UserSessionManager.tryRestoreSession()
                    Log.d("Butler", "Post-refresh restore: $restored")
                }
            }

            runOnUiThread {
                if (restored && UserSessionManager.currentProfile != null) {
                    val profile = UserSessionManager.currentProfile!!
                    val lang    = LanguageManager.getLanguage()
                    FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                    if (FamilyProfileManager.hasFamilyProfiles()) {
                        currentState = AssistantState.ASKING_WHO
                        val members  = FamilyProfileManager.getMembers()
                        val question = FamilyProfileManager.buildWhoQuestion(lang)
                        setUiState(ButlerUiState.FamilySelection(members, question))
                        speak(question) { startListening() }
                        return@runOnUiThread
                    }
                    val name    = profile.full_name?.split(" ")?.first() ?: "there"
                    val history = UserSessionManager.purchaseHistory
                    AnalyticsManager.logSessionStart(UserSessionManager.currentUserId(), lang)
                    proceedAfterIdentification(name, history, lang)
                } else {
                    setUiState(ButlerUiState.AuthChoice)
                    try { stopLockTask() } catch (_: Exception) {}
                    authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java))
                }
            }
        }
    }

    private suspend fun attemptTokenRefresh(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = SessionStore.getRefreshToken() ?: return@withContext false
        try {
            val body = """{"refresh_token":"$refreshToken"}"""
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10,  TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url("${SupabaseClient.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey",       SupabaseClient.SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("Butler", "Token refresh HTTP ${response.code}")
                return@withContext false
            }

            val json        = org.json.JSONObject(response.body?.string() ?: return@withContext false)
            val newAccess   = json.optString("access_token")
            val newRefresh  = json.optString("refresh_token")

            if (newAccess.isBlank()) return@withContext false

            SessionStore.updateTokens(newAccess, newRefresh)
            Log.d("Butler", "Token refreshed successfully")
            true

        } catch (e: Exception) {
            Log.e("Butler", "Token refresh error: ${e.message}")
            false
        }
    }

    private fun proceedAfterIdentification(
        name: String,
        history: List<com.demo.butler_voice_app.api.PurchaseSummary>,
        lang: String
    ) {
        // ── FIX Issue 1: Never proactively say "Want the usual Daawat Brown?" ──
        // User didn't ask to reorder. Butler was guessing and sounding presumptuous.
        // "Want the usual?" before user has asked for ANYTHING is a bad UX pattern.
        // Fix: Always greet normally. User can say "same as last time" / "wahi do"
        // to trigger reorder. SmartReorderManager still available via REORDER_CONFIRM
        // when user explicitly requests it in handleOrderIntent.
        // ─────────────────────────────────────────────────────────────────────────
        val lockedCode = when {
            lang.startsWith("hi") -> "hi-IN"
            lang.startsWith("te") -> "te-IN"
            lang.startsWith("ta") -> "ta-IN"
            lang.startsWith("kn") -> "kn-IN"
            lang.startsWith("ml") -> "ml-IN"
            lang.startsWith("pa") -> "pa-IN"
            lang.startsWith("gu") -> "gu-IN"
            lang.startsWith("mr") -> "mr-IN"
            else                  -> "en-IN"
        }
        SessionLanguageManager.forceSet(lockedCode)
        sessionUserName = name  // ← set for all BPE template {name} calls

        // Warm demo cache while greeting plays
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                productRepo.getTopRecommendations("rice", userLocation)
                productRepo.getTopRecommendations("dal",  userLocation)
                productRepo.getTopRecommendations("oil",  userLocation)
                Log.d("Butler", "Demo cache warmed: rice, dal, oil")
            } catch (_: Exception) {}
        }

        currentState = AssistantState.LISTENING
        val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() && it != "null" }
        val shortLast   = lastProduct?.lowercase()?.split(" ")?.take(2)
            ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val greeting = ButlerPersonalityEngine.greeting(name, lang, shortLast, currentMood)
        speak(greeting, EmotionTone.WARM) { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VOICE SIGNUP
    // ══════════════════════════════════════════════════════════════════════

    private fun startVoiceSignupFlow() {
        currentState = AssistantState.ASKING_IS_NEW_USER
        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_NEW_OR_RETURNING,
            prompt = "Are you a new customer, or have you ordered before?"))
        speak("Welcome to Butler! Are you a new customer, or have you ordered before?") { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // STT
    // ══════════════════════════════════════════════════════════════════════

    private fun startListening() {
        sarvamSTT.stop()
        val myId = ++sttListenId

        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")

        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    if (myId != sttListenId) {
                        Log.d("Butler", "STT callback discarded (stale id $myId, current $sttListenId)")
                        return@runOnUiThread
                    }

                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")

                    val pcm      = sarvamSTT.lastPcmBuffer
                    val duration = sarvamSTT.lastRecordingDurationMs
                    if (pcm.isNotEmpty()) {
                        currentMood = MoodDetector.analyse(pcm, duration)
                        Log.d("Butler", "Mood: $currentMood")
                    }
                    if (transcript.isBlank()) MoodDetector.recordRetry()

                    if (transcript.isNotBlank() && transcript.length > 2) {
                        val scriptLang     = MultilingualMatcher.detectScript(transcript)
                        val detected       = LanguageDetector.detect(transcript)
                        val rawLang        = if (scriptLang != "en") scriptLang else detected

                        val sarvamLangCode = "$rawLang-IN"
                        val langSwitched   = SessionLanguageManager.onDetection(sarvamLangCode, transcript)
                        if (langSwitched) {
                            val newBase = SessionLanguageManager.ttsLanguage
                            LanguageManager.setLanguage(newBase)
                            Log.d("Butler", "🔄 Language switched to ${SessionLanguageManager.lockedLanguage}")
                        } else {
                            val locked = SessionLanguageManager.ttsLanguage
                            if (LanguageManager.getLanguage() != locked) {
                                LanguageManager.setLanguage(locked)
                            }
                        }
                    }

                    if (transcript.isBlank()) {
                        SessionLanguageManager.onBlankTranscript()

                        sttRetryCount++
                        totalEmptyRetries++
                        val lang = LanguageManager.getLanguage()

                        if (totalEmptyRetries >= 5) {
                            totalEmptyRetries = 0
                            sttRetryCount = 0
                            MoodDetector.reset()
                            val giveUpMsg  = ButlerPersonalityEngine.giveUp(lang, currentMood)
                            // Give up with EMPATHETIC tone — user has been patient
                            speak(giveUpMsg, ButlerPersonalityEngine.toneForGiveUp()) { startWakeWordListening() }
                            return@runOnUiThread
                        }

                        // ── FIX Issue 2 + 7: Silence ≠ error. User is thinking.
                        // Old: 2 silent retries then speak → user got "Didn't catch that"
                        // too quickly after any pause. Caused frustration and looping.
                        // Fix: 3 silent retries before speaking. More patient, less intrusive.
                        if (sttRetryCount < 3) {
                            startListening()
                        } else {
                            sttRetryCount = 0
                            val retryMsg  = ButlerPersonalityEngine.didntHear(lang, currentMood, totalEmptyRetries)
                            // Retry with EMPATHETIC tone — never impatient, always gentle
                            speak(retryMsg, ButlerPersonalityEngine.toneForRetry()) { startListening() }
                        }
                        return@runOnUiThread
                    }

                    totalEmptyRetries = 0
                    sttRetryCount = 0
                    sttErrorCount = 0
                    MoodDetector.reset()
                    setUiState(ButlerUiState.Thinking(transcript))
                    handleCommand(transcript)
                }
            },
            onError = {
                runOnUiThread {
                    val lang = LanguageManager.getLanguage()
                    sttErrorCount++
                    val isLikelyRateLimit = sttErrorCount >= 2

                    if (isLikelyRateLimit) {
                        sttErrorCount = 0
                        val waitMsg = when {
                            lang.startsWith("hi") -> "एक पल..."
                            lang.startsWith("te") -> "ఒక్క నిమిషం..."
                            lang.startsWith("ta") -> "ஒரு நிமிடம்..."
                            lang.startsWith("kn") -> "ಒಂದು ನಿಮಿಷ..."
                            lang.startsWith("ml") -> "ഒരു നിമിഷം..."
                            lang.startsWith("pa") -> "ਇੱਕ ਪਲ..."
                            lang.startsWith("gu") -> "એક ક્ષણ..."
                            lang.startsWith("mr") -> "एक क्षण..."
                            else                  -> "One moment..."
                        }
                        speak(waitMsg) {
                            Handler(Looper.getMainLooper()).postDelayed(
                                { startListening() }, 3000
                            )
                        }
                    } else {
                        val errReply = when {
                            lang.startsWith("hi") -> "फिर से बोलें।"
                            lang.startsWith("te") -> "మళ్ళీ చెప్పండి."
                            lang.startsWith("ta") -> "மீண்டும் சொல்லுங்கள்."
                            lang.startsWith("kn") -> "ಮತ್ತೆ ಹೇಳಿ."
                            lang.startsWith("ml") -> "വീണ്ടും പറയൂ."
                            lang.startsWith("pa") -> "ਫਿਰ ਦੱਸੋ।"
                            lang.startsWith("gu") -> "ફરી બોલો."
                            else                  -> "Please say that again."
                        }
                        speak(errReply) { startListening() }
                    }
                }
            }
        )
    }

    private fun startListeningForSelection(
        onNumber: (Int) -> Unit,
        onOther: (String) -> Unit,
        retryPrompt: String = "Please say 1, 2, or 3."
    ) {
        sarvamSTT.stop()
        sttListenId++

        setUiState(ButlerUiState.Listening)
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    if (transcript.isBlank()) {
                        val lang = LanguageManager.getLanguage()
                        // FIX Issue 4: No screen references — voice assistant must work without screen
                        val blankMsg = when {
                            lang.startsWith("hi") -> "Brand ka naam bolein."
                            lang.startsWith("te") -> "Brand peyru cheppandi."
                            lang.startsWith("ta") -> "Brand peyar sollungal."
                            lang.startsWith("kn") -> "Brand hesaru heli."
                            lang.startsWith("ml") -> "Brand peru parayo."
                            lang.startsWith("pa") -> "Brand naam dasao."
                            lang.startsWith("gu") -> "Brand naam bolo."
                            else -> "Say the brand name."
                        }
                        speak(blankMsg) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                sarvamSTT.startListening(
                                    onResult = { t2 ->
                                        runOnUiThread {
                                            val t   = t2.trim()
                                            val num = detectNumberFromSpeech(t)
                                            when {
                                                num > 0        -> onNumber(num)
                                                t.isNotBlank() -> onOther(t)
                                                else           -> speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) }
                                            }
                                        }
                                    },
                                    onError = { runOnUiThread { speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) } } }
                                )
                            }, 500)
                        }
                        return@runOnUiThread
                    }
                    val num = detectNumberFromSpeech(transcript)
                    if (num > 0) onNumber(num) else onOther(transcript)
                }
            },
            onError = {
                runOnUiThread {
                    val lang = LanguageManager.getLanguage()
                    sttErrorCount++
                    if (sttErrorCount >= 2) {
                        sttErrorCount = 0
                        val waitMsg = when {
                            lang.startsWith("hi") -> "एक पल..."
                            lang.startsWith("te") -> "ఒక్క నిమిషం..."
                            else                  -> "One moment..."
                        }
                        speak(waitMsg) {
                            Handler(Looper.getMainLooper()).postDelayed(
                                { startListeningForSelection(onNumber, onOther, retryPrompt) }, 3000
                            )
                        }
                    } else {
                        speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) }
                    }
                }
            }
        )
    }

    private fun detectNumberFromSpeech(spoken: String): Int {
        val s = spoken.lowercase().trim().replace(Regex("[,।.!?॥]"), "").trim()

        val digit = Regex("\\b([123])\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()
        if (digit != null) return digit

        return when {
            s.contains("one")    || s.contains("wan")    || s.contains("won")     ||
                    s.contains("first")  || s.contains("pehla")  || s.contains("pehli")   ||
                    s.contains("ek")     || s.contains("एक")     || s.contains("पहला")    ||
                    s.contains("pehle")  || s.contains("pehli")  || s.contains("number one") ||
                    s.contains("ఒకటి")   || s.contains("మొదటి") || s.contains("ஒன்று")   ||
                    s.contains("முதல்") || s.contains("ಒಂದು")   || s.contains("ಮೊದಲ")    ||
                    s.contains("ഒന്ന്")  || s.contains("ഒന്നാ") || s.contains("ਇੱਕ")     ||
                    s.contains("ਪਹਿਲਾ") || s.contains("એક")     || s.contains(" prva")    ||
                    s.contains("ek no")  || s.contains("option 1") || s.contains("number 1") -> 1

            s.contains("two")    || s.contains("too")    || s.contains("to")      ||
                    s.contains("second") || s.contains("doosra") || s.contains("doosri")  ||
                    s.contains("do")     || s.contains("दो")     || s.contains("दूसरा")   ||
                    s.contains("రెండు")  || s.contains("రెండో")  || s.contains("இரண்டு")  ||
                    s.contains("இரண்டாவது") || s.contains("ಎರಡು") || s.contains("రెండు")  ||
                    s.contains("രണ്ട്")  || s.contains("ਦੋ")     || s.contains("ਦੂਜਾ")   ||
                    s.contains("બે")     || s.contains("ਦੋਵੇਂ")  ||
                    s.contains("option 2") || s.contains("number 2") -> 2

            s.contains("three")  || s.contains("tree")   || s.contains("third")   ||
                    s.contains("teesra") || s.contains("teesri") || s.contains("teen")    ||
                    s.contains("तीन")    || s.contains("तीसरा")  || s.contains("మూడు")   ||
                    s.contains("మూడో")   || s.contains("மூன்று") || s.contains("மூன்றாவது") ||
                    s.contains("ಮೂರು")  || s.contains("മൂന്ന്") || s.contains("ਤਿੰਨ")   ||
                    s.contains("ਤੀਜਾ")  || s.contains("ત્રણ")   ||
                    s.contains("option 3") || s.contains("number 3") -> 3

            else -> -1
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SERVICE FLOW LAUNCHER
    // ══════════════════════════════════════════════════════════════════════

    private fun launchServiceFlow(
        transcript: String,
        overrideSector: ServiceSector? = null,
        subTypeId: String? = null
    ) {
        currentState = AssistantState.IN_SERVICE_FLOW
        serviceSubTypeSession = null
        sttListenId++
        try { stopLockTask() } catch (_: Exception) {}

        val serviceIntent = if (overrideSector != null)
            com.demo.butler_voice_app.services.ServiceIntent(overrideSector, transcript)
        else
            ServiceManager.detectServiceIntent(transcript)

        val intent = Intent(this, ServiceActivity::class.java).apply {
            putExtra(ServiceActivity.EXTRA_SECTOR,   serviceIntent.sector?.name ?: "")
            putExtra(ServiceActivity.EXTRA_QUERY,    transcript)
            putExtra(ServiceActivity.EXTRA_IS_RX,    serviceIntent.isPrescription)
            putExtra(ServiceActivity.EXTRA_IS_EMERG, serviceIntent.isEmergency)
            putExtra("user_lat",  userLocation?.latitude  ?: 0.0)
            putExtra("user_lng",  userLocation?.longitude ?: 0.0)
            putExtra("sub_type",  subTypeId ?: "")
        }
        serviceLauncher.launch(intent)
    }

    // ══════════════════════════════════════════════════════════════════════
    // MAIN COMMAND HANDLER
    // ══════════════════════════════════════════════════════════════════════

    private fun handleCommand(text: String) {
        val lower   = text.lowercase().trim()
        val cleaned = lower.replace(Regex("[,।.!?؟]"), "").trim()
        val lang    = LanguageManager.getLanguage()
        if (routeTranscript(text, lower)) return

        when (currentState) {

            AssistantState.IN_SERVICE_SUBTYPE_FLOW -> {
                val session = serviceSubTypeSession
                if (session == null) { currentState = AssistantState.LISTENING; startListening(); return }

                val matched = ServiceVoiceHandler.matchSubType(text, session.sector)

                if (matched != null) {
                    val whenPrompt    = ButlerPhraseBank.get("service_when", lang)
                    val confirmPrompt = "${matched.getDisplay(lang)} — $whenPrompt"
                    speak(confirmPrompt) {
                        sarvamSTT.startListening(
                            onResult = { timeText ->
                                runOnUiThread {
                                    val timeSlot      = extractTimeSlotFromSpeech(timeText.trim())
                                    val subTypeName = matched.displayEn.ifBlank { matched.id }
                                    val bookingPrompt = com.demo.butler_voice_app.services.ServiceVoiceEngine.bookingConfirmPrompt(session.sector, subTypeName, timeSlot, lang)
                                    speak(bookingPrompt) {
                                        sarvamSTT.startListening(
                                            onResult = { confirmText ->
                                                runOnUiThread {
                                                    val c = confirmText.trim().lowercase()
                                                    if (MultilingualMatcher.isYes(c) ||
                                                        c.contains("haan") || c.contains("ha") ||
                                                        c.contains("ok") || c.contains("pakka")) {
                                                        val enhancedQuery  = "${session.originalTranscript} ${matched.displayEn} $timeSlot"
                                                        speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(session.sector, lang)) {
                                                            launchServiceFlow(enhancedQuery, session.sector, matched.id)
                                                        }
                                                    } else {
                                                        serviceSubTypeSession = null
                                                        currentState = AssistantState.LISTENING
                                                        val cancelMsg = when {
                                                            lang.startsWith("hi") -> "theek hai. koi aur service chahiye, ya grocery order karein?"
                                                            lang.startsWith("te") -> "సరే. వేరే సేవ కావాలా, లేదా గ్రోసరీ ఆర్డర్ చేయాలా?"
                                                            else                  -> "no problem. want a different service, or can I help with something else?"
                                                        }
                                                        speak(cancelMsg) { startListening() }
                                                    }
                                                }
                                            },
                                            onError = {
                                                runOnUiThread { launchServiceFlow(session.originalTranscript, session.sector, matched.id) }
                                            }
                                        )
                                    }
                                }
                            },
                            onError = { runOnUiThread { launchServiceFlow(session.originalTranscript, session.sector, matched.id) } }
                        )
                    }
                } else {
                    session.retryCount++
                    if (session.retryCount <= 2) {
                        speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.subTypeRetry(session.sector, lang)) { startListening() }
                    } else {
                        serviceSubTypeSession = null
                        speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(session.sector, lang)) { launchServiceFlow(session.originalTranscript, session.sector, null) }
                    }
                }
            }

            AssistantState.ASKING_WHO -> {
                val detected = FamilyProfileManager.detectSpeaker(text)
                if (detected != null) {
                    FamilyProfileManager.setActive(detected)
                    currentState = AssistantState.LISTENING
                    val greeting = FamilyProfileManager.buildPersonalGreeting(detected, lang)
                    AnalyticsManager.logSessionStart(detected.userId, lang)
                    lifecycleScope.launch {
                        val suggestions = SmartReorderManager.getSuggestions(detected.userId)
                        val smartMsg    = SmartReorderManager.buildReorderGreeting(suggestions, detected.displayName)
                        runOnUiThread {
                            if (smartMsg != null && suggestions.isNotEmpty()) {
                                pendingReorderSuggestions = suggestions
                                currentState = AssistantState.REORDER_CONFIRM
                                speak(smartMsg) { startListening() }
                            } else {
                                speak(greeting) { startListening() }
                            }
                        }
                    }
                } else {
                    val members = FamilyProfileManager.getMembers()
                    val names   = members.joinToString(", ") { it.displayName }
                    val retry   = if (lang.startsWith("hi")) "pehchan nahi hua. $names mein se kaun hain aap?"
                    else "didn't catch that — are you $names?"
                    setUiState(ButlerUiState.FamilySelection(members, retry))
                    speak(retry) {
                        val profile = UserSessionManager.currentProfile!!
                        val name    = profile.full_name?.split(" ")?.first() ?: "there"
                        currentState = AssistantState.LISTENING
                        speak(IndianLanguageProcessor.getWelcomeGreeting(lang, name)) { startListening() }
                    }
                }
            }

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
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_EMAIL,
                            prompt = EmailPasswordParser.getEmailPrompt(lang)))
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
                        .replace(Regex("i am\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("mera naam\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(".", "").trim()
                    if (nameText.isBlank() || nameText.contains("@")) {
                        runOnUiThread { speak("Please say just your name clearly.") { startListening() } }; return@launch
                    }
                    tempName = nameText.split(" ").firstOrNull { it.length > 1 }
                        ?.replaceFirstChar { it.uppercase() } ?: nameText.replaceFirstChar { it.uppercase() }
                    runOnUiThread {
                        currentState = AssistantState.ASKING_EMAIL
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_EMAIL,
                            collectedName = tempName,
                            prompt = "Nice to meet you $tempName! Please spell your email, letter by letter."))
                        speak("Nice to meet you $tempName! Please spell your email address, letter by letter.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_EMAIL -> {
                val parsed = EmailPasswordParser.parseEmail(text)
                if (parsed != null) {
                    tempEmail = parsed; emailRetryCount = 0
                    currentState = AssistantState.ASKING_PHONE
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PHONE,
                        collectedName = tempName, collectedEmail = tempEmail,
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
                            setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PHONE,
                                collectedName = tempName, collectedEmail = tempEmail))
                            speak("OK, I got $tempEmail. What is your mobile number?") { startListening() }
                        } else {
                            emailRetryCount = 0
                            speak("I am having trouble. Let me show you the sign-in screen.") {
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
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PASSWORD,
                        tempName, tempEmail, tempPhone, "Now choose a password."))
                    speak("Got it — $tempPhone. Now choose a password. Spell each character clearly.") { startListening() }
                } else {
                    phoneRetryCount++
                    if (phoneRetryCount <= 2) speak("Please say your 10-digit mobile number again, digit by digit.") { startListening() }
                    else {
                        phoneRetryCount = 0
                        currentState = AssistantState.ASKING_PASSWORD
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PASSWORD,
                            tempName, tempEmail, prompt = "Let us set your password."))
                        speak("No problem, let us skip phone. Please choose a password.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = EmailPasswordParser.parsePassword(text)
                    .ifBlank { text.trim().replace(" ", "").trimEnd('.', ',', '!') }
                if (password.length < 6) {
                    speak("Password must be at least 6 characters. Please try again.") { startListening() }; return
                }
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.CREATING_ACCOUNT, tempName, tempEmail))
                        speak("Creating your account, please wait.") {}
                        UserSessionManager.signup(tempEmail, password, tempName, tempPhone).fold(
                            onSuccess = { profile ->
                                FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                sessionUserName = firstName  // ← set name for BPE templates
                                AnalyticsManager.logUserAuth("voice_signup", LanguageManager.getLanguage())
                                speak(IndianLanguageProcessor.getWelcomeGreeting(lang, firstName)) { startListening() }
                            },
                            onFailure = { error ->
                                if (error.message?.contains("user_already_exists") == true || error.message?.contains("already registered") == true)
                                    speak("Account already exists. Logging you in.") { lifecycleScope.launch { doLogin(tempEmail, password) } }
                                else
                                    speak("Sorry, could not create account. Please try again.") { startWakeWordListening() }
                            }
                        )
                    } else {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.LOGGING_IN))
                        doLogin(tempEmail, password)
                    }
                }
            }

            AssistantState.REORDER_CONFIRM -> {
                val proactive = pendingProactiveData
                if (proactive != null) {
                    when {
                        MultilingualMatcher.isYes(cleaned) || IndianLanguageProcessor.detectIntent(cleaned) == "confirm" -> {
                            pendingProactiveData = null
                            lifecycleScope.launch {
                                val product = apiClient.searchProduct(proactive.productName)
                                runOnUiThread {
                                    if (product != null) {
                                        cart.add(CartItem(product, proactive.quantity))
                                        currentState = AssistantState.CONFIRMING
                                        showCartAndSpeak(buildShortConfirm(LanguageManager.getLanguage())) { startListening() }
                                    } else {
                                        currentState = AssistantState.LISTENING
                                        speak("${proactive.productName} nahi mila. ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() }
                                    }
                                }
                            }
                        }
                        MultilingualMatcher.isNo(cleaned) -> {
                            pendingProactiveData = null; currentState = AssistantState.LISTENING
                            speak("theek hai! ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() }
                        }
                        else -> {
                            pendingProactiveData = null; currentState = AssistantState.LISTENING
                            handleOrderIntent(text, lower)
                        }
                    }
                    return
                }

                // ── REORDER_CONFIRM branch ────────────────────────────────────────
                when {
                    MultilingualMatcher.isYes(cleaned) && !isNoMoreIntent(cleaned) &&
                            IndianLanguageProcessor.detectIntent(cleaned) != "order_new" -> {
                        // Pure yes with no product name → accept the reorder suggestion
                        lifecycleScope.launch {
                            for (s in pendingReorderSuggestions)
                                apiClient.searchProduct(s.productName)?.let { cart.add(CartItem(it, s.avgQty)) }
                            runOnUiThread {
                                if (cart.isNotEmpty()) {
                                    currentState = AssistantState.CONFIRMING
                                    showCartAndSpeak(buildShortConfirm(LanguageManager.getLanguage())) { startListening() }
                                } else {
                                    currentState = AssistantState.LISTENING
                                    speak("Could not find those products. ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() }
                                }
                            }
                        }
                    }

                    // ── FIX Bug 1: "नहीं, मुझे राइस चाहिए।" in ONE turn ─────────
                    // Previously: isNo() fired → asked "क्या चाहिए?" → user had to
                    // repeat the product on the next turn.
                    //
                    // Fix: strip the leading negation, check if a product remains
                    // in the same sentence, and route directly to searchAndAskQuantity.
                    // No extra STT round-trip needed.
                    //
                    // Negation prefixes covered (Hindi, English, Telugu, Tamil,
                    // Kannada, Malayalam, Punjabi, Gujarati):
                    //   nahi, nahin, nope, no, नहीं, नही, ना, నో, வேண்டாம், ಬೇಡ,
                    //   വേണ്ട, ਨਹੀਂ, ના
                    // ─────────────────────────────────────────────────────────────
                    MultilingualMatcher.isNo(cleaned) -> {
                        pendingReorderSuggestions = emptyList()
                        currentState = AssistantState.LISTENING

                        val strippedText = text
                            .replace(
                                Regex(
                                    "^(nahi|nahin|nope|no|नहीं|नही|ना|వద్దు|வேண்டாம்|ಬೇಡ|വേണ്ട|ਨਹੀਂ|ના)[,।\\.\\s]+",
                                    RegexOption.IGNORE_CASE
                                ), ""
                            )
                            .trim()

                        val instant = instantGroceryDetect(strippedText.lowercase(), LanguageManager.getLanguage())
                        when {
                            instant != null -> {
                                // "नहीं, मुझे राइस चाहिए" → instant match → straight to product
                                searchAndAskQuantity(instant.first, instant.second)
                            }
                            strippedText.isNotBlank() && strippedText.length < text.length -> {
                                // Something after the negation but not a simple keyword → AIParser
                                handleOrderIntent(strippedText, strippedText.lowercase())
                            }
                            else -> {
                                // Pure "नहीं" with no product → ask what they want
                                val noLang = LanguageManager.getLanguage()
                                val noMsg = when {
                                    noLang.startsWith("hi") -> "ठीक है! क्या चाहिए?"
                                    noLang.startsWith("te") -> "సరే! ఏం కావాలి?"
                                    noLang.startsWith("ta") -> "சரி! என்ன வேணும்?"
                                    noLang.startsWith("kn") -> "ಸರಿ! ಏನು ಬೇಕು?"
                                    noLang.startsWith("ml") -> "ശരി! എന്ത് വേണം?"
                                    noLang.startsWith("pa") -> "ਠੀਕ ਹੈ! ਕੀ ਚਾਹੀਦਾ?"
                                    noLang.startsWith("gu") -> "ઠીક છે! શું જોઈએ?"
                                    else -> "No problem! What would you like?"
                                }
                                speak(noMsg) { startListening() }
                            }
                        }
                    }

                    else -> {
                        // Not yes, not no → treat as a new order intent directly
                        pendingReorderSuggestions = emptyList()
                        currentState = AssistantState.LISTENING
                        handleOrderIntent(text, lower)
                    }
                }
            }

            AssistantState.LISTENING      -> handleOrderIntent(text, lower)

            AssistantState.ASKING_QUANTITY -> {
                val qty = extractQuantity(text); val product = tempProduct
                if (product != null) {
                    cart.add(CartItem(product, qty))
                    sessionLastProduct = product.name; sessionLastQty = qty
                    currentState = AssistantState.ASKING_MORE
                    val addedMsg = ButlerPersonalityEngine.itemAdded(
                        sessionUserName, product.name, lang, currentMood, cart.size)
                    showCartAndSpeak(addedMsg) { startListening() }
                } else {
                    speak(ButlerPersonalityEngine.productNotFound("", lang)) {
                        currentState = AssistantState.LISTENING; startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE          -> handleAskingMore(cleaned, text)
            AssistantState.CONFIRMING_ADD_PRODUCT -> handleConfirmingAddProduct(cleaned)
            AssistantState.ASKING_PRODUCT_TYPE    -> handleAskingProductType(cleaned)
            AssistantState.EDITING_CART   -> handleCartEdit(cleaned, text)

            AssistantState.CONFIRMING -> {
                val intentFromLang = IndianLanguageProcessor.detectIntent(cleaned)
                when {
                    intentFromLang == "confirm" || MultilingualMatcher.isYes(cleaned) -> askPaymentMode()
                    intentFromLang == "cancel"  || MultilingualMatcher.isNo(cleaned)  ->
                        speak("theek hai, cancel.") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                    isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, text) }
                    else -> {
                        lifecycleScope.launch {
                            val parsed = AIOrderParser.parse(text); LanguageManager.setLanguage(parsed.detectedLanguage)
                            runOnUiThread {
                                when (parsed.intent) {
                                    "confirm_order" -> askPaymentMode()
                                    "cancel_order"  -> speak("theek hai, cancel.") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                                    else -> speak("haan bolein toh order, na bolein toh cancel.") { startListening() }
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

    // ══════════════════════════════════════════════════════════════════════
    // STATUS QUERY
    // ══════════════════════════════════════════════════════════════════════

    private fun handleStatusQuery(text: String) {
        val firstName = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
        val userId    = UserSessionManager.currentUserId() ?: return
        val lang      = LanguageManager.getLanguage()
        setUiState(ButlerUiState.Thinking(if (lang == "hi") "स्टेटस चेक कर रहे हैं…" else "Checking status…"))
        lifecycleScope.launch {
            val result = StatusCheckHandler.handleStatusQuery(
                text = text, lang = lang, firstName = firstName,
                userId = userId, lastBookingId = lastBookingId
            )
            runOnUiThread { speak(result.voiceText) { currentState = AssistantState.LISTENING; startListening() } }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAYMENT
    // ══════════════════════════════════════════════════════════════════════

    private fun askPaymentMode() {
        pendingOrderTotal   = cart.sumOf { it.product.price * it.quantity }
        pendingOrderSummary = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        currentState = AssistantState.ASKING_PAYMENT_MODE
        val card     = PaymentManager.getSavedCard(this)
        val hasSaved = card != null
        val cardInfo = if (card != null) "${card.network} card ending ${card.last4}" else ""
        setUiState(ButlerUiState.PaymentChoice(pendingOrderTotal, pendingOrderSummary, hasSaved, cardInfo))
        speak(ButlerPersonalityEngine.askPaymentMode(sessionUserName, LanguageManager.getLanguage())) { startListening() }
    }

    private fun handlePaymentModeChoice(cleaned: String) {
        val lang    = LanguageManager.getLanguage()
        val hasUPI  = cleaned.contains("upi")     || cleaned.contains("google pay") ||
                cleaned.contains("phonepe") || cleaned.contains("paytm")      ||
                cleaned.contains("bhim")    || cleaned.contains("यूपीआई")     || cleaned.contains("gpay")
        val hasCard = cleaned.contains("card")    || cleaned.contains("debit")      ||
                cleaned.contains("credit")  || cleaned.contains("saved")      ||
                cleaned.contains("कार्ड")   || cleaned.contains("కార్డ్")
        val hasQR   = cleaned.contains("qr")      || cleaned.contains("q r")        ||
                cleaned.contains("q.r")     || cleaned.contains("scan")       || cleaned.contains("क्यूआर")

        when {
            // ── FIX Issue 8: Both UPI and card in utterance → ASK, don't guess ─
            // "UPI card" = ambiguous. Could be STT mishear OR genuine confusion.
            // Old code: picked UPI (after previous fix) silently.
            // New code: ask once clearly. User answers with a single word.
            // ─────────────────────────────────────────────────────────────────
            hasUPI && hasCard -> {
                val askMsg = when {
                    lang.startsWith("hi") -> "UPI से doge ya card se?"
                    lang.startsWith("te") -> "UPI istara leda card?"
                    else                  -> "UPI or card?"
                }
                speak(askMsg) { startListening() }
            }

            hasUPI -> {
                // FIX: Use ₹ symbol ("₹50") not spelled words ("pachaas rupaye")
                // ElevenLabs reads "₹50" naturally. Words get weird when TranslationManager
                // translates them ("pachaas rupaye" → unexpected forms in some voices).
                val amountRs = "₹${pendingOrderTotal.toInt()}"
                currentState = AssistantState.WAITING_UPI_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("upi", pendingOrderTotal))
                speak(ButlerPersonalityEngine.upiInstruction(amountRs, lang)) {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("upi") }, 3000)
                }
            }

            hasCard -> {
                val card     = PaymentManager.getSavedCard(this)
                val amountRs = "₹${pendingOrderTotal.toInt()}"
                currentState = AssistantState.WAITING_CARD_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("card", pendingOrderTotal))
                speak(if (card != null) "${card.network} card ${card.last4} pe $amountRs charge hoga. Payment complete karein."
                else "Card details enter karein aur $amountRs pay karein.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("card") }, 2000)
                }
            }

            hasQR -> {
                val amountRs = "₹${pendingOrderTotal.toInt()}"
                currentState = AssistantState.WAITING_QR_PAYMENT
                setUiState(ButlerUiState.ShowQRCode(pendingOrderTotal, pendingOrderSummary))
                speakKeepingQRVisible("Screen pe QR code hai. Kisi bhi UPI app se $amountRs pay karein.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("qr") }, 20000)
                }
            }

            else -> {
                val askMsg = when {
                    lang.startsWith("hi") -> "UPI से doge ya card se?"
                    lang.startsWith("te") -> "UPI istara leda card?"
                    else                  -> "UPI or card?"
                }
                speak(askMsg) { startListening() }
            }
        }
    }

    private fun askIfPaid(mode: String) {
        currentState = when (mode) {
            "card" -> AssistantState.CONFIRMING_CARD_PAID
            "upi"  -> AssistantState.CONFIRMING_UPI_PAID
            else   -> AssistantState.CONFIRMING_QR_PAID
        }
        val paidLang = LanguageManager.getLanguage()
        val paidMode = when (currentState) {
            AssistantState.CONFIRMING_UPI_PAID  -> "upi"
            AssistantState.CONFIRMING_CARD_PAID -> "card"
            else -> "qr"
        }
        speak(ButlerPersonalityEngine.askIfPaid(paidLang, paidMode, "₹${pendingOrderTotal.toInt()}")) { startListening() }
    }

    private fun handlePaidOrNotPaid(cleaned: String, mode: String) {
        val paid    = listOf(
            "yes","paid","done","payment done","i paid","completed","transferred","confirm",
            "हाँ","हां","हो गया","हो गई","ho gaya","ho gayi","kar diya","ha","kar di",
            "పేమెంట్ చేశాను","అవును","ஆம்",
            "ਹਾਂ","ਹੋ ਗਈ","ਪੇਮੈਂਟ ਹੋ ਗਈ",
            "હા","કન્ફર્મ","ચૂકવ્યું","થઈ ગઈ"
        )
        val notPaid = listOf(
            "no","not yet","haven't","wait","not done","failed",
            "नहीं","अभी नहीं","nahi","abhi nahi",
            "లేదు","ఇంకా",
            "ਨਹੀਂ","ਨਹੀ",
            "ના","નહીં"
        )
        val lang     = LanguageManager.getLanguage()
        val amountRs = "₹${pendingOrderTotal.toInt()}"
        when {
            paid.any    { cleaned.contains(it) } -> speak(ButlerPersonalityEngine.paymentDone(lang), ButlerPersonalityEngine.toneForPaymentDone()) { placeOrder() }
            notPaid.any { cleaned.contains(it) } -> {
                speak(when (mode) {
                    "card" -> "theek hai! card se $amountRs pay karein."
                    "upi"  -> "theek hai! UPI pe $amountRs bhejein."
                    else   -> "theek hai! QR scan karke $amountRs pay karein."
                }) { Handler(Looper.getMainLooper()).postDelayed({ askIfPaid(mode) }, 8000) }
            }
            else -> speak("haan bolein toh paid, nahi bolein toh cancel.") { startListening() }
        }
    }

    private fun handlePaymentConfirmation(cleaned: String, mode: String) { askIfPaid(mode) }

    // ══════════════════════════════════════════════════════════════════════
    // CART EDITING
    // ══════════════════════════════════════════════════════════════════════

    private fun isCartEditIntent(s: String): Boolean =
        listOf("remove","delete","हटाओ","హటావో","change","update","బదలు","बदलो","మార్చు").any { s.contains(it) }

    private fun handleCartEdit(cleaned: String, originalText: String) {
        val lang = LanguageManager.getLanguage()
        if (cart.isEmpty()) {
            speak(ButlerPersonalityEngine.cartEmpty(lang)) {
                currentState = AssistantState.LISTENING; startListening()
            }
            return
        }
        val isRemove = cleaned.contains("remove") || cleaned.contains("delete") || cleaned.contains("हटाओ") || cleaned.contains("हटा")
        val isChange = cleaned.contains("change") || cleaned.contains("update") || cleaned.contains("बदलो") || cleaned.contains("make it")
        if (isRemove) {
            val item = cart.firstOrNull { it.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (item != null) {
                cart.remove(item)
                if (cart.isEmpty()) speak("${ButlerPersonalityEngine.itemRemoved(item.product.name, lang)} ${ButlerPersonalityEngine.cartEmpty(lang)}") { currentState = AssistantState.LISTENING; startListening() }
                else { currentState = AssistantState.CONFIRMING; showCartAndSpeak("${ButlerPersonalityEngine.itemRemoved(item.product.name, lang)} ${buildShortConfirm(lang)}") { startListening() } }
            } else speak("kaunsa item hatana hai?") { startListening() }
            return
        }
        if (isChange) {
            val newQty = extractQuantity(cleaned)
            val item   = cart.firstOrNull { it.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (item != null && newQty > 0) {
                cart[cart.indexOf(item)] = CartItem(item.product, newQty)
                currentState = AssistantState.CONFIRMING
                showCartAndSpeak("${item.product.name} $newQty kar diya. ${buildShortConfirm(lang)}") { startListening() }
            } else speak("kaunsa item aur kitna chahiye?") { startListening() }
            return
        }
        currentState = AssistantState.CONFIRMING; readCartAndConfirm()
    }

    // ══════════════════════════════════════════════════════════════════════
    // ORDER INTENT
    // ══════════════════════════════════════════════════════════════════════

    private fun handleOrderIntent(text: String, lower: String) {
        val cleaned = lower.replace(Regex("[,।.!?]"), "").trim()
        val lang    = LanguageManager.getLanguage()
        if (cleaned.contains("repeat") || cleaned.contains("same as last")) { repeatLastOrder(); return }
        if (cleaned.contains("my orders") || cleaned.contains("history"))   { readOrderHistory(); return }
        if (isNoMoreIntent(cleaned)) { readCartAndConfirm(); return }
        if (cart.isNotEmpty() && isCartEditIntent(cleaned)) { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, text); return }
        val regional = IndianLanguageProcessor.normalizeProduct(cleaned)
        if (regional != cleaned && regional.isNotBlank()) { searchAndAskQuantity(regional); return }

        val instant = instantGroceryDetect(cleaned, lang)
        if (instant != null) {
            searchAndAskQuantity(instant.first, instant.second)
            return
        }

        speakFillerThen {
            lifecycleScope.launch {
                val fullParsed = AIParser.parse(text)
                LanguageManager.setLanguage(fullParsed.language)

                runOnUiThread {
                    when (val routing = fullParsed.routing) {
                        is IntentRouting.GoToService -> {
                            val cat    = routing.category
                            val sector = mapCategoryToSector(cat)
                            val prompt = if (sector != null)
                                com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(sector, lang)
                            else
                                com.demo.butler_voice_app.services.ServiceVoiceEngine.categoryPrompt(lang)
                            speak(prompt) { launchServiceFlow(text, overrideSector = sector) }
                        }
                        is IntentRouting.GoToGrocery -> {
                            val items = routing.items
                            when {
                                items.isEmpty() -> {
                                    val fb = keywordFallback(cleaned)
                                    if (fb != null) searchAndAskQuantity(fb)
                                    else speak(ButlerPhraseBank.get("ask_item", lang)) { startListening() }
                                }
                                items.size == 1 -> {
                                    val i = items.first()
                                    searchAndAskQuantity(i.name, i.quantity, i.unit)
                                }
                                else -> lifecycleScope.launch { addMultipleItemsToCart(items) }
                            }
                        }
                        is IntentRouting.FinishOrder -> {
                            if (cart.isEmpty()) speak("cart khaali hai. ${ButlerPhraseBank.get("ask_item", lang)}") { startListening() }
                            else readCartAndConfirm()
                        }
                        is IntentRouting.ConfirmOrder -> {
                            if (currentState == AssistantState.CONFIRMING) askPaymentMode()
                            else readCartAndConfirm()
                        }
                        is IntentRouting.CancelOrder -> {
                            speak("theek hai, cancel.") { startWakeWordListening() }
                        }
                        else -> {
                            val fb = keywordFallback(cleaned)
                            if (fb != null) {
                                searchAndAskQuantity(fb)
                            } else {
                                val clarifyMsg = when {
                                    lang.startsWith("hi") ->
                                        "क्या मँगाना है? rice, dal, तेल, या कुछ और?"
                                    lang.startsWith("te") ->
                                        "ఏమి కావాలి? rice, dal, oil, లేదా ఇంకేమైనా?"
                                    lang.startsWith("ta") ->
                                        "என்ன வேணும்? rice, dal, oil, அல்லது வேற ஏதாவது?"
                                    lang.startsWith("kn") ->
                                        "ಏನು ಬೇಕು? rice, dal, oil, ಅಥವಾ ಬೇರೇನಾದರೂ?"
                                    lang.startsWith("ml") ->
                                        "എന്ത് വേണം? rice, dal, oil, അല്ലെങ്കിൽ മറ്റെന്തെങ്കിലും?"
                                    lang.startsWith("pa") ->
                                        "ਕੀ ਚਾਹੀਦਾ? rice, dal, oil, ਜਾਂ ਕੁਝ ਹੋਰ?"
                                    lang.startsWith("gu") ->
                                        "શું જોઈએ? rice, dal, oil, અથવા બીજું કંઈ?"
                                    lang.startsWith("mr") ->
                                        "काय हवं? rice, dal, oil, किंवा आणखी काही?"
                                    else ->
                                        "What would you like? Say rice, dal, oil, or any grocery item."
                                }
                                speak(clarifyMsg) { startListening() }
                            }
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ASKING MORE
    // ══════════════════════════════════════════════════════════════════════

    private fun handleAskingMore(cleaned: String, originalText: String) {
        val lang = LanguageManager.getLanguage()
        when {
            // ── isNoMoreIntent checked FIRST (explicit checkout phrases only) ──
            isNoMoreIntent(cleaned) -> readCartAndConfirm()

            // ── FIX Issue 5 + 7: isYes branch — restore context ──────────────
            // Old: "हाँ" → re-ask "kuch aur chahiye?" → LOOP.
            // Why: Butler forgot it had just suggested "दाल भी साथ में दूं?" and
            // user said yes to it. Context was lost.
            //
            // Fix: When user says yes, check in this order:
            //   1. Did they embed a product name? → search that product directly
            //   2. Was there an active suggestion? (दाल after rice) → search it
            //   3. Only if neither → ask what they want
            // ─────────────────────────────────────────────────────────────────
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("add") || cleaned.contains("more") ||
                    cleaned.contains("और") || cleaned.contains("aur") -> {

                // Priority 1: Product keyword embedded in utterance ("हाँ दाल भी चाहिए")
                val instant = instantGroceryDetect(cleaned, lang)
                if (instant != null) {
                    currentState = AssistantState.LISTENING
                    searchAndAskQuantity(instant.first, instant.second)
                    return
                }

                // Priority 2: Active suggestion from last product ("Rice was added →
                // suggestion was 'दाल'" → user said yes → search dal)
                val suggestionItem = getSuggestionSearchTerm(sessionLastProduct, lang)
                if (suggestionItem != null) {
                    currentState = AssistantState.LISTENING
                    searchAndAskQuantity(suggestionItem)
                    return
                }

                // Priority 3: Nothing to infer — ask what they want
                currentState = AssistantState.LISTENING
                val prompt = ButlerPersonalityEngine.askMore(sessionUserName, lang, currentMood, cart.size, sessionLastProduct)
                showCartAndSpeak(prompt) { startListening() }
            }

            isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, originalText) }

            else -> {
                lifecycleScope.launch {
                    val fullParsed = AIParser.parse(originalText)
                    LanguageManager.setLanguage(fullParsed.language)
                    runOnUiThread {
                        when (fullParsed.routing) {
                            is IntentRouting.FinishOrder,
                            is IntentRouting.CancelOrder -> readCartAndConfirm()
                            is IntentRouting.GoToGrocery -> {
                                val items = (fullParsed.routing as IntentRouting.GoToGrocery).items
                                if (items.isNotEmpty()) {
                                    currentState = AssistantState.LISTENING
                                    handleOrderIntent(originalText, originalText.lowercase())
                                } else {
                                    showCartAndSpeak(ButlerPhraseBank.get("ask_more", lang)) { startListening() }
                                }
                            }
                            else -> showCartAndSpeak(
                                if (lang.startsWith("hi")) "और क्या चाहिए?"
                                else ButlerPhraseBank.get("ask_more", lang)
                            ) { startListening() }
                        }
                    }
                }
            }
        }
    }

    // ── Template 2: handle subtype selection (basmati/brown/normal) ──────
    private fun handleAskingProductType(cleaned: String) {
        val lang = LanguageManager.getLanguage()
        // Map what user said to a refined search term
        val refined = when {
            cleaned.contains("basmati")                                           -> "basmati rice"
            cleaned.contains("brown")                                             -> "brown rice"
            cleaned.contains("normal") || cleaned.contains("regular") ||
                    cleaned.contains("sona") || cleaned.contains("सोना") ||
                    cleaned.contains("idli") || cleaned.contains("raw")           -> "rice"
            cleaned.contains("toor")  || cleaned.contains("arhar") ||
                    cleaned.contains("अरहर")                                      -> "toor dal"
            cleaned.contains("moong") || cleaned.contains("मूंग")                -> "moong dal"
            cleaned.contains("masoor")|| cleaned.contains("मसूर")                -> "masoor dal"
            cleaned.contains("urad")  || cleaned.contains("उड़द")                -> "urad dal"
            cleaned.contains("mustard")|| cleaned.contains("sarson") ||
                    cleaned.contains("sarso")                                     -> "mustard oil"
            cleaned.contains("sunflower")                                        -> "sunflower oil"
            cleaned.contains("coconut")|| cleaned.contains("nariyal")           -> "coconut oil"
            cleaned.contains("wheat") || cleaned.contains("gehun")              -> "wheat atta"
            cleaned.contains("multigrain")                                        -> "multigrain atta"
            else -> pendingProductCategory  // fallback: search original category
        }
        currentState = AssistantState.LISTENING
        searchAndAskQuantity(refined)
    }
    // Mirrors ButlerPersonalityEngine.getRelatedSuggestion but returns
    // an English search keyword for SmartProductRepository (not Devanagari).
    // e.g. sessionLastProduct = "Daawat Brown rice" → "rice" → suggestion "dal"
    private fun getSuggestionSearchTerm(lastProduct: String?, lang: String): String? {
        if (lastProduct == null) return null
        val p = lastProduct.lowercase()
        val map = mapOf(
            "rice"  to "dal",  "dal"    to "rice",  "oil"    to "atta",
            "atta"  to "oil",  "milk"   to "bread",  "bread"  to "butter",
            "tea"   to "sugar","sugar"  to "tea",    "ghee"   to "dal",
            "eggs"  to "bread","curd"   to "rice",   "butter" to "bread",
            "chawal" to "dal", "daal"   to "rice",   "tel"    to "atta"
        )
        return map.entries.firstOrNull { p.contains(it.key) }?.value
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRODUCT SEARCH
    // ══════════════════════════════════════════════════════════════════════

    private fun buildProductVoiceReadout(
        recs: List<ProductRecommendation>,
        itemName: String,
        lang: String
    ): String {
        fun shortName(r: ProductRecommendation) = r.productName.lowercase().split(" ")
            .take(2).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return when {
            lang.startsWith("hi") -> {
                // ── FIX: "देखो, rice — Brand ₹X। Kaunsa lena hai?" → "Rice mein Brand ₹X, Brand2 ₹Y, Brand3 hai."
                // No "देखो" filler. No askSelection appended — Butler shows options and
                // immediately starts listening. If brand not matched, recapMsg asks "Kaunsa X du?"
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                val itemDisplay = when {
                    itemName.contains("rice")  || itemName.contains("chawal")  -> "Rice"
                    itemName.contains("dal")   || itemName.contains("daal")    -> "Daal"
                    itemName.contains("oil")   || itemName.contains("tel")     -> "Tel"
                    itemName.contains("milk")  || itemName.contains("doodh")   -> "Doodh"
                    itemName.contains("atta")  || itemName.contains("flour")   -> "Atta"
                    itemName.contains("sugar") || itemName.contains("cheeni")  -> "Cheeni"
                    itemName.contains("salt")  || itemName.contains("namak")   -> "Namak"
                    itemName.contains("tea")   || itemName.contains("chai")    -> "Chai"
                    itemName.contains("ghee")                                   -> "Ghee"
                    itemName.contains("bread")                                  -> "Bread"
                    itemName.contains("eggs")  || itemName.contains("egg")     -> "Ande"
                    else -> itemName.replaceFirstChar { it.uppercase() }
                }
                "$itemDisplay mein $optionText hai."
            }
            lang.startsWith("te") -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                val intro = if (cart.isEmpty()) "చూడు," else "మరి చూడు,"
                "$intro $itemName — $optionText. ${ButlerPersonalityEngine.askSelection("te", currentMood)}"
            }
            lang.startsWith("ta") -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                "$itemName — $optionText. எந்த brand வேணும்?"
            }
            lang.startsWith("kn") -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                "$itemName — $optionText. ಯಾವ brand ಬೇಕು?"
            }
            lang.startsWith("ml") -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                "$itemName — $optionText. ഏത് brand വേണം?"
            }
            lang.startsWith("pa") -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                "$itemName — $optionText. ਕਿਹੜਾ ਚਾਹੀਦਾ? ਨਾਮ ਦੱਸੋ।"
            }
            lang.startsWith("gu") -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                "$itemName — $optionText. કયું જોઈએ? નામ બોલો."
            }
            else -> {
                val optionText = recs.joinToString(", ") { r ->
                    "${shortName(r)} ₹${r.priceRs.toInt()}"
                }
                "$itemName — $optionText. Which brand do you want?"
            }
        }
    }

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        val lang = LanguageManager.getLanguage()

        // ── Template 2: ASK_TYPE — "Which rice — basmati, brown or normal?" ──
        // If user said a GENERIC category name (rice/dal/oil) with no quantity
        // specified and no subtype, ask what type before searching.
        // This makes Butler feel smarter: it thinks about what the user actually
        // needs instead of dumping brand names immediately.
        // Only triggers on first call (qty == 0) to avoid infinite re-triggering.
        val genericTerms = setOf(
            "rice", "dal", "daal", "oil", "tel", "atta", "flour",
            "milk", "doodh", "tea", "chai",
            "चावल", "दाल", "तेल", "आटा", "दूध", "चाय"
        )
        if (qty == 0 && itemName.lowercase().trim() in genericTerms) {
            pendingProductCategory = itemName
            currentState = AssistantState.ASKING_PRODUCT_TYPE
            speak(ButlerPersonalityEngine.askProductType(itemName, sessionUserName, lang)) {
                startListening()
            }
            return
        }

        speakFillerThen {
            lifecycleScope.launch {
                val recs = productRepo.getTopRecommendations(itemName, userLocation)
                if (recs.isNotEmpty()) {
                    runOnUiThread { setUiState(ButlerUiState.ShowingRecommendations(itemName, recs)) }

                    // ── FIX Issue 3: REMOVED single-best-option FRUSTRATED path ──
                    // Old: mood=FRUSTRATED → "Daawat Brown ₹45 — best option. Want it?"
                    // Wrong: Butler was skipping user choice entirely.
                    // "Rice has many types. Butler skipped user choice."
                    // Fix: ALWAYS show 3 options regardless of mood.
                    // FRUSTRATED users still get a shorter, direct readout (handled in
                    // buildProductVoiceReadout format) but they MUST choose the brand.
                    // ─────────────────────────────────────────────────────────────────

                    val readout = buildProductVoiceReadout(recs, itemName, lang)

                    // ── FIX Issue 4: Never mention screen — voice-first ───────────
                    // "screen पर दिखा नाम बोलें।" breaks voice-first UX.
                    // Butler must work with eyes closed. Just ask for the brand name.
                    val nameRetryPrompt = when {
                        lang.startsWith("hi") -> "Brand ka naam bolein."
                        lang.startsWith("te") -> "Brand peyru cheppandi."
                        lang.startsWith("ta") -> "Brand peyar sollungal."
                        lang.startsWith("kn") -> "Brand hesaru heli."
                        lang.startsWith("ml") -> "Brand peru parayo."
                        lang.startsWith("pa") -> "Brand naam dasao."
                        lang.startsWith("gu") -> "Brand naam bolo."
                        else                  -> "Say the brand name."
                    }

                    speakKeepingRecsVisible(readout) {
                        startListeningForSelection(
                            onNumber = { num ->
                                handleRecSelectionByIndex(num - 1, recs, qty, itemName)
                            },
                            onOther = { spoken ->
                                val sLow = spoken.lowercase().trim()
                                    .replace(Regex("[।,.!?]"), "")

                                val wantsCheapest = sLow.contains("sasta") ||
                                        sLow.contains("cheap") || sLow.contains("best") ||
                                        sLow.contains("sabse") || sLow.contains("सबसे") ||
                                        sLow.contains("first") || sLow.contains("pehla") ||
                                        sLow.contains("పర్వాలేదు") || sLow.contains("చెప్పింది")

                                val wantsLast = sLow.contains("last") || sLow.contains("teesra") ||
                                        sLow.contains("third") || sLow.contains("teen") ||
                                        sLow.contains("तीसरा") || sLow.contains("అది చివరిది")

                                val wantsFirst = sLow.contains("haan") || sLow.contains("yes") ||
                                        sLow.contains("theek") || sLow.contains("ok") ||
                                        sLow.contains("wahi") || sLow.contains("yahi") ||
                                        sLow.contains("అవును") || sLow.contains("ஆம்") ||
                                        sLow.contains("ಹೌದು") || sLow.contains("ഹ")

                                when {
                                    wantsCheapest -> handleRecSelectionByIndex(0, recs, qty, itemName)
                                    wantsLast     -> handleRecSelectionByIndex(2, recs, qty, itemName)
                                    wantsFirst    -> handleRecSelectionByIndex(0, recs, qty, itemName)
                                    else -> {
                                        // ── FIX: Cross-script brand matching ─────────────────────────
                                        // PROBLEM: Sarvam STT returns "दावत" when user says "Daawat".
                                        // Product names in DB are English: "Daawat Brown Basmati".
                                        // Old: "दावत".startsWith("daawat") = FALSE → no match → asks again.
                                        //
                                        // FIX: 3-pass matching:
                                        //   Pass 1: Direct English word match (user spoke English)
                                        //   Pass 2: Normalize Devanagari → English, then match again
                                        //   Pass 3: Phonetic contains check (loose substring match)
                                        //   Fallback: Pick the first rec rather than re-asking (user
                                        //             already confirmed by saying a name — just pick best)
                                        // ─────────────────────────────────────────────────────────────

                                        val normalized = normalizeBrandSpelling(sLow)
                                        val pick = matchBrandFromSpoken(sLow, normalized, recs)

                                        if (pick != null) {
                                            handleRecSelectionByIndex(recs.indexOf(pick), recs, qty, itemName)
                                        } else {
                                            // ── SHORT RECAP: user didn't say a recognized brand ──────────
                                            // Brands are visible on screen — no need to re-read the list.
                                            // Just ask "Kaunsa rice du?" — clean, natural, non-repetitive.
                                            val itemDisplay = when {
                                                itemName.contains("rice")  || itemName.contains("chawal")  -> if (lang.startsWith("hi")) "rice" else "rice"
                                                itemName.contains("dal")   || itemName.contains("daal")    -> "daal"
                                                itemName.contains("oil")   || itemName.contains("tel")     -> if (lang.startsWith("hi")) "tel" else "oil"
                                                itemName.contains("milk")  || itemName.contains("doodh")   -> if (lang.startsWith("hi")) "doodh" else "milk"
                                                itemName.contains("atta")  || itemName.contains("flour")   -> "atta"
                                                itemName.contains("sugar") || itemName.contains("cheeni")  -> if (lang.startsWith("hi")) "cheeni" else "sugar"
                                                itemName.contains("tea")   || itemName.contains("chai")    -> "chai"
                                                itemName.contains("ghee")                                   -> "ghee"
                                                else -> itemName
                                            }
                                            val recapMsg = when {
                                                lang.startsWith("hi") -> "Kaunsa $itemDisplay du?"
                                                lang.startsWith("te") -> "Edi kavali?"
                                                lang.startsWith("ta") -> "Edu vendum?"
                                                lang.startsWith("kn") -> "Yavudu beku?"
                                                lang.startsWith("ml") -> "Eth veno?"
                                                lang.startsWith("pa") -> "Kihra chahida?"
                                                lang.startsWith("gu") -> "Kyu joiye?"
                                                else                  -> "Which one?"
                                            }
                                            speakKeepingRecsVisible(recapMsg) {
                                                startListeningForSelection(
                                                    onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                                                    onOther  = { spokenAgain ->
                                                        val sLow2 = spokenAgain.lowercase().trim()
                                                            .replace(Regex("[।,.!?]"), "")
                                                        val norm2  = normalizeBrandSpelling(sLow2)
                                                        val pick2  = matchBrandFromSpoken(sLow2, norm2, recs)
                                                        handleRecSelectionByIndex(
                                                            if (pick2 != null) recs.indexOf(pick2) else 0,
                                                            recs, qty, itemName
                                                        )
                                                    },
                                                    retryPrompt = nameRetryPrompt
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            retryPrompt = nameRetryPrompt
                        )
                    }
                } else {
                    val product = apiClient.searchProduct(itemName)
                    runOnUiThread {
                        if (product != null) {
                            tempProduct = product
                            if (qty > 0) {
                                cart.add(CartItem(product, qty))
                                sessionLastProduct = product.name; sessionLastQty = qty
                                currentState = AssistantState.ASKING_MORE
                                // Template 4: "Theek hai, {product} cart mein add kar diya… aur kuch chahiye {name}?"
                                val addedMsg = ButlerPersonalityEngine.itemAdded(
                                    sessionUserName, product.name, lang, currentMood, cart.size)
                                showCartAndSpeak(addedMsg) { startListening() }
                            } else {
                                currentState = AssistantState.ASKING_QUANTITY
                                speak(ButlerPersonalityEngine.askQuantity(product.name, lang)) { startListening() }
                            }
                        } else {
                            speak(ButlerPersonalityEngine.productNotFound(itemName, lang)) { startListening() }
                        }
                    }
                }
            }
        }
    }

    private fun handleRecSelectionByIndex(
        index: Int,
        recs: List<com.demo.butler_voice_app.api.ProductRecommendation>,
        qty: Int,
        itemName: String
    ) {
        val pick = recs.getOrNull(index)
        val lang = LanguageManager.getLanguage()
        if (pick != null) {
            // ── Template 3: CONFIRM_ADD_PRODUCT ────────────────────────────
            // Don't silently add. Show price and ask: "{product} ₹{price} ka hai…
            // kya ise cart mein add karna hai {name}?"
            // User must say yes before item enters cart.
            pendingAddRecs      = recs
            pendingAddIndex     = index
            pendingAddQty       = if (qty > 0) qty else 1
            pendingAddItemName  = itemName
            currentState        = AssistantState.CONFIRMING_ADD_PRODUCT

            // Is this the first item or a subsequent one?
            val msg = if (cart.isEmpty()) {
                ButlerPersonalityEngine.confirmAddProduct(
                    sessionUserName, pick.productName, pick.priceRs.toInt(), lang)
            } else {
                ButlerPersonalityEngine.confirmAddNext(
                    sessionUserName, pick.productName, pick.priceRs.toInt(), lang)
            }
            speakKeepingRecsVisible(msg, ButlerPersonalityEngine.toneForConfirmAdd()) { startListening() }
        } else {
            speakKeepingRecsVisible(
                when {
                    lang.startsWith("hi") -> "Brand ka naam bolein."
                    lang.startsWith("te") -> "Brand peyru cheppandi."
                    lang.startsWith("ta") -> "Brand peyar sollungal."
                    lang.startsWith("kn") -> "Brand hesaru heli."
                    lang.startsWith("ml") -> "Brand peru parayo."
                    lang.startsWith("pa") -> "Brand naam dasao."
                    lang.startsWith("gu") -> "Brand naam bolo."
                    else                  -> "Say the brand name."
                }
            ) {
                startListeningForSelection(
                    onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                    onOther  = { _ ->
                        speak(ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())) {
                            currentState = AssistantState.LISTENING; startListening()
                        }
                    }
                )
            }
        }
    }

    // ── Template 3/5: handle the yes/no after price confirmation ─────────
    private fun handleConfirmingAddProduct(cleaned: String) {
        val lang = LanguageManager.getLanguage()
        val pick = pendingAddRecs.getOrNull(pendingAddIndex)

        if (pick == null) {
            currentState = AssistantState.LISTENING; startListening(); return
        }

        when {
            // User says YES → add to cart → template 4 (ITEM_ADDED)
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("हाँ") ||
                    cleaned.contains("हां") || cleaned.contains("ha ") ||
                    cleaned.contains("han ") || cleaned.contains("le lo") ||
                    cleaned.contains("le lena") || cleaned.contains("add karo") ||
                    cleaned.contains("daal do") || cleaned.contains("rakh do") -> {

                cart.add(CartItem(ApiClient.Product(
                    id    = pick.productId,
                    name  = pick.productName,
                    price = pick.priceRs,
                    unit  = pick.unit
                ), pendingAddQty))
                sessionLastProduct = pick.productName
                sessionLastQty     = pendingAddQty
                currentState       = AssistantState.ASKING_MORE

                val categoryWord = extractCategoryWord(pendingAddItemName)
                val hasCategory  = categoryWord.isNotBlank() &&
                        pick.productName.lowercase().contains(categoryWord.lowercase())
                val displayName  = if (categoryWord.isNotBlank() && !hasCategory)
                    "${pick.productName.split(" ").take(2).joinToString(" ")} $categoryWord"
                else pick.productName.split(" ").take(2).joinToString(" ")

                // Template 4: "Theek hai, {product} cart mein add kar diya… aur kuch chahiye {name}?"
                val addedMsg = ButlerPersonalityEngine.itemAdded(
                    sessionUserName, displayName, lang, currentMood, cart.size)
                showCartAndSpeak(addedMsg, ButlerPersonalityEngine.toneForItemAdded()) { startListening() }
            }

            // User says NO → ask which brand they want instead
            MultilingualMatcher.isNo(cleaned) || cleaned.contains("नहीं") ||
                    cleaned.contains("nahi") || cleaned.contains("koi aur") -> {

                val askMsg = when {
                    lang.startsWith("hi") -> "कौन सा chahiye? Brand ka naam bolein."
                    lang.startsWith("te") -> "Endha brand kavali? Peyru cheppandi."
                    else -> "Which brand? Say the name."
                }
                speakKeepingRecsVisible(askMsg) {
                    startListeningForSelection(
                        onNumber = { n ->
                            handleRecSelectionByIndex(n - 1, pendingAddRecs, pendingAddQty, pendingAddItemName)
                        },
                        onOther = { spoken ->
                            val words = spoken.lowercase().split(" ").filter { it.length >= 3 }
                            val match = pendingAddRecs.firstOrNull { rec ->
                                words.any { sw -> rec.productName.lowercase().contains(sw) }
                            }
                            handleRecSelectionByIndex(
                                if (match != null) pendingAddRecs.indexOf(match) else 0,
                                pendingAddRecs, pendingAddQty, pendingAddItemName
                            )
                        }
                    )
                }
            }

            // Unclear → ask again politely
            else -> {
                val askMsg = when {
                    lang.startsWith("hi") -> "हाँ ya नहीं?"
                    else                  -> "Yes or no?"
                }
                speak(askMsg) { startListening() }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PLACE ORDER
    // ══════════════════════════════════════════════════════════════════════

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId()
                if (userId == null) {
                    speak(ButlerPersonalityEngine.sessionExpired(LanguageManager.getLanguage())) { startWakeWordListening() }; return@launch
                }
                val orderResult = apiClient.createOrder(cart, userId)
                val shortId     = if (orderResult.public_id.isNotBlank()) orderResult.public_id else orderResult.id.takeLast(6).uppercase()
                val firstName   = FamilyProfileManager.activeProfile?.displayName?.split(" ")?.first()
                    ?: UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
                val lang        = LanguageManager.getLanguage()
                Log.d("Butler", "Order placed: ${orderResult.id}")
                AnalyticsManager.logOrderPlaced(orderResult.id, orderResult.total_amount, cart.size, lang)
                lastOrderId = orderResult.id; lastPublicId = shortId; lastOrderTotal = orderResult.total_amount
                FamilyProfileManager.activeProfile?.let { member ->
                    val summary = cart.take(2).joinToString(", ") { it.product.name }
                    FamilyProfileManager.updateLastOrder(this@MainActivity, member.id, summary)
                }
                val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
                setUiState(ButlerUiState.OrderPlaced(shortId, orderResult.total_amount, cartItems, 30, firstName))
                speak(ButlerPersonalityEngine.orderPlaced(firstName, shortId, toSpeakableAmount(orderResult.total_amount), 30, lang), EmotionTone.WARM) {
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
                runOnUiThread {
                    currentState = AssistantState.CONFIRMING
                    showCartAndSpeak(ButlerPersonalityEngine.orderError(LanguageManager.getLanguage())) { startListening() }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CART HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun showCartAndSpeak(
        text: String,
        tone: EmotionTone = EmotionTone.WARM,
        onDone: (() -> Unit)? = null
    ) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            val ttsText   = com.demo.butler_voice_app.utils.ButlerSpeechFormatter.format(finalText, lang)
            Log.d("Butler", "Original: $text")
            Log.d("Butler", "TTS ($lang): $ttsText")
            val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
            val total     = cart.sumOf { it.product.price * it.quantity }
            runOnUiThread {
                if (currentState == AssistantState.CONFIRMING || currentState == AssistantState.ASKING_MORE)
                    setUiState(ButlerUiState.CartReview(cartItems, total, text))
                else
                    setUiState(ButlerUiState.Speaking(ttsText, cart = cartItems))
                ttsManager.speak(text = ttsText, language = lang, tone = tone, onDone = { onDone?.invoke() })
            }
        }
    }

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) {
            speak(ButlerPersonalityEngine.cartEmpty(LanguageManager.getLanguage())) {
                currentState = AssistantState.LISTENING; startListening()
            }
            return
        }
        currentState = AssistantState.CONFIRMING
        // Template 6: use WARM tone — this is the "almost done" moment
        showCartAndSpeak(
            buildShortConfirm(LanguageManager.getLanguage()),
            ButlerPersonalityEngine.toneForConfirmOrder()
        ) { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>(); val notFound = mutableListOf<String>()
        for (item in items) {
            val sn = IndianLanguageProcessor.normalizeProduct(item.name).ifBlank { item.name }
            val p  = apiClient.searchProduct(sn)
            if (p != null) { cart.add(CartItem(p, item.quantity)); found.add("${item.quantity} ${p.name}") }
            else { notFound.add(item.name); AnalyticsManager.logItemNotFound(item.name) }
        }
        val lang = LanguageManager.getLanguage()
        runOnUiThread {
            currentState = AssistantState.ASKING_MORE
            showCartAndSpeak(
                if (notFound.isEmpty()) "${found.joinToString(", ")} add ho gaya. ${ButlerPhraseBank.get("ask_more", lang)}"
                else "${found.joinToString(", ")} add ho gaya. ${notFound.joinToString(", ")} nahi mila. ${ButlerPhraseBank.get("ask_more", lang)}"
            ) { startListening() }
        }
    }

    private fun isNoMoreIntent(s: String): Boolean {
        // ── FIX Issue 6 CRITICAL: "Nothing" ≠ "Proceed to checkout" ─────────
        // Old list included: "no","nope","nothing","stop","nahi"
        // "कुछ नहीं" / "nothing" / "stop" → user means STOP, not "place my order"
        // This was silently routing to checkout on ambiguous words = WRONG ORDERS.
        //
        // Fix: ONLY explicit "I'm done adding, place the order" phrases trigger checkout.
        // Ambiguous negative words (no, nahi, nothing, stop) → fall through to else branch.
        // User must use an explicit done/checkout signal to confirm.
        // ─────────────────────────────────────────────────────────────────────
        if (MultilingualMatcher.isDone(s)) return true
        if (IndianLanguageProcessor.DONE_PHRASES.any { s.contains(it, ignoreCase = true) }) return true
        return listOf(
            // ✅ EXPLICIT done-with-cart signals only:
            "done","finish","checkout","place order",
            "बस","bas",
            "ऑर्डर करो","order karo","order kar do","order kardo",
            "ऑर्डर कर दो","ऑर्डर कर","place karo","place kar",
            "kar do","bas karo","theek hai ab","ab karo",
            "order kar doon","order de do","confirm"
            // ❌ REMOVED: "no","nope","nothing","stop","nahi","ho gaya"
            // These are ambiguous — "nothing" and "nahi" alone mean STOP/CANCEL,
            // not "proceed to checkout". Do not include them here.
        ).any { s.contains(it, ignoreCase = true) }
    }

    private fun extractTimeSlotFromSpeech(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("aaj")     || lower.contains("today")    || lower.contains("आज")  -> "today"
            lower.contains("kal")     || lower.contains("tomorrow") || lower.contains("कल")  -> "tomorrow"
            lower.contains("morning") || lower.contains("subah")    || lower.contains("सुबह") -> "morning"
            lower.contains("evening") || lower.contains("shaam")    || lower.contains("शाम") -> "evening"
            lower.contains("abhi")    || lower.contains("now")      || lower.contains("right now") -> "now"
            else -> null
        }
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
            s.contains("ghee")  || s.contains("घी")                             -> "ghee"
            s.contains("bread") || s.contains("रोटी")                           -> "bread"
            s.contains("eggs")  || s.contains("egg")   || s.contains("अंडा")   -> "eggs"
            else -> null
        }
    }

    private fun instantGroceryDetect(s: String, lang: String): Pair<String, Int>? {
        val qty = extractQuantity(s)
        val detectedQty = when {
            Regex("\\d+").containsMatchIn(s) -> qty
            listOf("do","दो","రెండు","two","2 kilo","2 kg").any { s.contains(it) } -> 2
            listOf("teen","तीन","మూడు","three").any { s.contains(it) } -> 3
            else -> 0
        }

        if (s.contains("rice") || s.contains("chawal") || s.contains("चावल") ||
            s.contains("అన్నం") || s.contains("basmati") || s.contains("baasmati"))
            return Pair("rice", detectedQty)

        if (s.contains("dal") || s.contains("daal") || s.contains("दाल") ||
            s.contains("పప్పు") || s.contains("pappu") || s.contains("lentil"))
            return Pair("dal", detectedQty)

        if (s.contains("oil") || s.contains("tel") || s.contains("तेल") ||
            s.contains("నూనె") || s.contains("nune") || s.contains("cooking oil") ||
            s.contains("sarso") || s.contains("sunflower"))
            return Pair("oil", detectedQty)

        if (s.contains("milk") || s.contains("doodh") || s.contains("दूध") ||
            s.contains("పాలు") || s.contains("paalu"))
            return Pair("milk", detectedQty)

        if (s.contains("atta") || s.contains("aata") || s.contains("आटा") ||
            s.contains("flour") || s.contains("wheat") || s.contains("గోధుమ"))
            return Pair("wheat flour", detectedQty)

        if (s.contains("sugar") || s.contains("cheeni") || s.contains("chini") ||
            s.contains("चीनी") || s.contains("చక్కెర") || s.contains("shakkar"))
            return Pair("sugar", detectedQty)

        if (s.contains("salt") || s.contains("namak") || s.contains("नमक") ||
            s.contains("ఉప్పు") || s.contains("uppu"))
            return Pair("salt", detectedQty)

        if (s.contains("tea") || s.contains("chai") || s.contains("chaa") ||
            s.contains("चाय") || s.contains("టీ") || s.contains("tii"))
            return Pair("tea", detectedQty)

        if (s.contains("ghee") || s.contains("ghi") || s.contains("घी") ||
            s.contains("నేయి") || s.contains("neyi"))
            return Pair("ghee", detectedQty)

        if (s.contains("bread") || s.contains("roti") || s.contains("pav") ||
            s.contains("రొట్టె") || s.contains("rotte"))
            return Pair("bread", detectedQty)

        if (s.contains("egg") || s.contains("anda") || s.contains("अंडा") ||
            s.contains("గుడ్లు") || s.contains("gudlu"))
            return Pair("eggs", detectedQty)

        if (s.contains("coffee") || s.contains("kaafi") || s.contains("కాఫీ"))
            return Pair("coffee", detectedQty)

        if (s.contains("soap") || s.contains("sabun") || s.contains("సబ్బు"))
            return Pair("soap", detectedQty)

        if (s.contains("biscuit") || s.contains("biscuits") || s.contains("బిస్కెట్"))
            return Pair("biscuit", detectedQty)

        if (s.contains("butter") || s.contains("makhan") || s.contains("मक्खन"))
            return Pair("butter", detectedQty)

        if (s.contains("curd") || s.contains("dahi") || s.contains("दही") ||
            s.contains("పెరుగు") || s.contains("perugu"))
            return Pair("curd", detectedQty)

        if (s.contains("daawat") || s.contains("dawat"))  return Pair("daawat rice", detectedQty)
        if (s.contains("fortune"))                         return Pair("fortune oil", detectedQty)
        if (s.contains("aashirvaad") || s.contains("aashirvad")) return Pair("aashirvaad atta", detectedQty)
        if (s.contains("amul"))                            return Pair("amul butter", detectedQty)
        if (s.contains("tata salt") || s.contains("tata namak")) return Pair("tata salt", detectedQty)

        return null
    }

    private suspend fun doLogin(email: String, password: String) {
        UserSessionManager.login(email, password).fold(
            onSuccess = { profile ->
                FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                runOnUiThread {
                    currentState = AssistantState.LISTENING
                    val firstName   = profile.full_name?.split(" ")?.first() ?: "there"
                    sessionUserName = firstName  // ← critical: set name for all BPE templates
                    val history     = UserSessionManager.purchaseHistory
                    AnalyticsManager.logUserAuth("login", LanguageManager.getLanguage())
                    val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() && it != "null" }
                    val lang        = LanguageManager.getLanguage()
                    val shortLast = lastProduct?.lowercase()?.split(" ")
                        ?.take(2)?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    val greeting = ButlerPersonalityEngine.greeting(firstName, lang, shortLast, currentMood)
                    speak(greeting, EmotionTone.WARM) { startListening() }
                }
            },
            onFailure = {
                runOnUiThread { speak("login nahi hua. email aur password check karein.") { startWakeWordListening() } }
            }
        )
    }

    private fun readOrderHistory() {
        val h    = UserSessionManager.purchaseHistory
        val lang = LanguageManager.getLanguage()
        if (h.isEmpty()) speak(ButlerPhraseBank.get("no_orders", lang)) { startListening() }
        else speak("recent orders: ${h.take(3).joinToString(", ") { it.product_name ?: "unknown" }}. dobara order karein?") { startListening() }
    }

    private fun repeatLastOrder() {
        lifecycleScope.launch {
            val userId = UserSessionManager.currentUserId() ?: return@launch
            val orders = apiClient.getOrderHistory(userId)
            if (orders.isEmpty()) { runOnUiThread { speak("koi purana order nahi.") { startListening() } }; return@launch }
            val items  = apiClient.getOrderItems(orders.first().id)
            if (items.isEmpty()) { runOnUiThread { speak("last order load nahi hua.") { startListening() } }; return@launch }
            for (item in items) { apiClient.searchProduct(item.product_name)?.let { cart.add(CartItem(it, item.quantity)) } }
            runOnUiThread {
                currentState = AssistantState.CONFIRMING
                showCartAndSpeak("last order ready hai. ${buildShortConfirm(LanguageManager.getLanguage())}") { startListening() }
            }
        }
    }

    // ── Maps Devanagari/phonetic brand name → English for cross-script match ─
    // PROBLEM: Sarvam STT returns "दावत" when user says "Daawat" in Hindi.
    // Product names in the DB are English ("Daawat Brown Basmati").
    // Direct string comparison fails across scripts.
    // This map covers the most common Indian grocery brands + their Hindi spellings.
    private fun normalizeBrandSpelling(spoken: String): String {
        val brandMap = mapOf(
            // ── Rice brands ───────────────────────────────────────────────
            "दावत" to "daawat", "दावात" to "daawat", "dawat" to "daawat", "daavat" to "daawat",
            "इंडिया गेट" to "india gate", "इण्डिया गेट" to "india gate",
            "यूनिटी" to "unity", "आर्चीज़" to "archies", "आर्चीस" to "archies",
            "सोनम" to "sonam", "सोनारी" to "sonari", "दुबार" to "dubar",
            "कोहिनूर" to "kohinoor", "लाल किला" to "lal qilla",
            // ── Oil brands ────────────────────────────────────────────────
            "फॉर्च्यून" to "fortune", "फार्च्यून" to "fortune", "फर्च्यून" to "fortune",
            "पतंजलि" to "patanjali", "सफोला" to "saffola", "सफ्फोला" to "saffola",
            "फ्रीडम" to "freedom", "गोल्डविनर" to "goldwinner", "विमल" to "vimal",
            "डाल्डा" to "dalda", "रूचि" to "ruchi", "सनफ्लावर" to "sunflower",
            "गावर" to "gaurav", "सरसों" to "mustard", "तिल" to "sesame",
            // ── Atta / Flour brands ───────────────────────────────────────
            "आशीर्वाद" to "aashirvaad", "आशिर्वाद" to "aashirvaad", "आशीर्वाद" to "aashirvaad",
            "अन्नपूर्णा" to "annapurna", "पिलसबरी" to "pillsbury",
            "शक्ति भोग" to "shakti bhog", "रिलायंस" to "reliance select",
            // ── Dal brands ────────────────────────────────────────────────
            "तूर" to "toor", "अरहर" to "arhar", "मूंग" to "moong",
            "मसूर" to "masoor", "उड़द" to "urad", "चना" to "chana",
            // ── Salt brands ───────────────────────────────────────────────
            "टाटा" to "tata", "कैप्टन कुक" to "captain cook", "अनपुर्णा" to "annapurna",
            // ── Milk / Dairy brands ───────────────────────────────────────
            "अमूल" to "amul", "नंदिनी" to "nandini", "मदर डेयरी" to "mother dairy",
            "गोकुल" to "gokul", "सांची" to "sanchi", "परागन" to "parag",
            // ── Tea brands ────────────────────────────────────────────────
            "ताज महल" to "taj mahal", "रेड लेबल" to "red label",
            "ब्रुक बॉन्ड" to "brooke bond", "टेटली" to "tetley",
            "वाघ बकरी" to "wagh bakri", "गिरनार" to "girnar",
            // ── Sugar brands ──────────────────────────────────────────────
            "धाम्पुर" to "dhampur", "केसरी" to "kesari",
            // ── Ghee brands ───────────────────────────────────────────────
            "अमूल घी" to "amul ghee", "पतंजलि घी" to "patanjali ghee",
            "नंदिनी घी" to "nandini ghee", "गोवर्धन" to "gowardhan",
            // ── Biscuit brands ────────────────────────────────────────────
            "पारले" to "parle", "ब्रिटानिया" to "britannia", "सनफीस्ट" to "sunfeast",
            // ── Telugu/Tamil brand names (common phonetics) ───────────────
            "దావత్" to "daawat", "ఇండియా గేట్" to "india gate",
            "ఫార్చ్యూన్" to "fortune", "పతంజలి" to "patanjali",
            "அமுல்" to "amul", "பதஞ்சலி" to "patanjali",
            "ಅಮೂಲ್" to "amul", "ಪತಂಜಲಿ" to "patanjali"
        )
        var result = spoken
        // Sort by length descending so longer phrases match before shorter ones
        brandMap.entries.sortedByDescending { it.key.length }.forEach { (script, latin) ->
            if (result.contains(script)) result = result.replace(script, latin)
        }
        return result
    }

    // ── 3-pass brand matcher: handles Latin, Devanagari, and phonetic ─────
    // Pass 1: Direct word match (Latin → Latin, for English speakers)
    // Pass 2: Normalized match (Devanagari → Latin, for Hindi speakers)
    // Pass 3: Phonetic contains check (loose match for any remaining cases)
    //
    // Returns the best matching ProductRecommendation or null.
    private fun matchBrandFromSpoken(
        sLow: String,
        normalized: String,
        recs: List<ProductRecommendation>
    ): ProductRecommendation? {

        fun wordScore(query: String, rec: ProductRecommendation): Int {
            val recWords   = rec.productName.lowercase().split(" ")
            val queryWords = query.split(" ").filter { it.length >= 3 }
            return queryWords.count { sw -> recWords.any { rw -> rw.startsWith(sw) || sw.startsWith(rw) } }
        }

        fun containsScore(query: String, rec: ProductRecommendation): Int {
            val recLow = rec.productName.lowercase()
            // Single-word query directly contained in product name (e.g. "daawat" in "daawat brown")
            val queryWords = query.split(" ").filter { it.length >= 3 }
            return queryWords.count { sw -> recLow.contains(sw) }
        }

        // Pass 1 + 2: word boundary matching on original and normalized
        val directPick = recs.maxByOrNull { rec ->
            maxOf(wordScore(sLow, rec), wordScore(normalized, rec))
        }?.takeIf { rec ->
            maxOf(wordScore(sLow, rec), wordScore(normalized, rec)) > 0
        }
        if (directPick != null) return directPick

        // Pass 3: loose contains check on original and normalized
        val containsPick = recs.maxByOrNull { rec ->
            maxOf(containsScore(sLow, rec), containsScore(normalized, rec))
        }?.takeIf { rec ->
            maxOf(containsScore(sLow, rec), containsScore(normalized, rec)) > 0
        }
        if (containsPick != null) return containsPick

        // Pass 4: if the entire spoken text is contained in any product name
        // (handles short one-word brand names like "amul", "tata")
        val singleWordPick = recs.firstOrNull { rec ->
            rec.productName.lowercase().contains(sLow) ||
                    rec.productName.lowercase().contains(normalized)
        }
        return singleWordPick
    }

    // ── Maps itemName search term → spoken category word ─────────────────
    // Used in handleConfirmingAddProduct to append the product noun to the
    // brand name in TTS: "Daawat Brown rice" not just "Daawat Brown".
    private fun extractCategoryWord(itemName: String): String {
        val s = itemName.lowercase()
        return when {
            s.contains("rice")   || s.contains("chawal")  || s.contains("basmati") -> "rice"
            s.contains("dal")    || s.contains("daal")    || s.contains("lentil")  -> "dal"
            s.contains("oil")    || s.contains("tel")                               -> "oil"
            s.contains("milk")   || s.contains("doodh")                             -> "milk"
            s.contains("atta")   || s.contains("flour")   || s.contains("aata")    -> "atta"
            s.contains("sugar")  || s.contains("cheeni")  || s.contains("shakkar") -> "sugar"
            s.contains("salt")   || s.contains("namak")                             -> "salt"
            s.contains("tea")    || s.contains("chai")                              -> "tea"
            s.contains("ghee")                                                      -> "ghee"
            s.contains("bread")  || s.contains("pav")                               -> "bread"
            s.contains("egg")                                                        -> "eggs"
            s.contains("coffee")                                                     -> "coffee"
            s.contains("butter") || s.contains("makhan")                            -> "butter"
            s.contains("curd")   || s.contains("dahi")                              -> "curd"
            s.contains("soap")   || s.contains("sabun")                             -> "soap"
            s.contains("biscuit")                                                    -> "biscuit"
            else -> ""
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

    private fun detectEmotionTone(text: String): EmotionTone {
        val lower = text.lowercase()

        val emergencyWords = listOf(
            "emergency", "ambulance", "heart", "chest", "breathe", "breathing",
            "unconscious", "accident", "bleeding", "faint", "stroke", "attack",
            "pain", "paining", "hurting", "dying", "help me", "urgent",
            "please help", "not breathing", "collapsed", "seizure",
            "दर्द", "दिल", "सांस", "बेहोश", "खून", "हादसा", "एम्बुलेंस",
            "दर्द है", "दर्द हो", "दर्द हो रहा", "दिल में दर्द",
            "मेरे दिल", "सीने में", "सांस नहीं", "गिर गया", "बचाओ",
            "madad", "bachao", "जल्दी", "मदद करो",
            "dard hai", "dard ho raha", "dil mein dard", "mere dil mein",
            "seene mein", "sans nahi", "ambulance bulao", "doctor bulao",
            "help karo", "bachao mujhe", "gir gaya", "behosh",
            "నొప్పి", "గుండె", "శ్వాస", "అపస్మారం", "రక్తం",
            "నొప్పిగా", "గుండె నొప్పి", "శ్వాస రావడం లేదు",
            "అంబులెన్స్", "సహాయం", "పడిపోయాను",
            "வலி", "இதயம்", "ನೋವು", "ഹൃദയം", "വേദന"
        )
        if (emergencyWords.any { lower.contains(it) }) return EmotionTone.EMERGENCY

        val distressWords = listOf(
            "worried", "scared", "tension", "anxious", "upset", "crying",
            "lost", "stolen", "problem", "trouble", "issue", "pareshan",
            "परेशान", "डर", "घबराहट", "चिंता", "problem hai",
            "సమస్య", "భయం", "ఆందోళన"
        )
        if (distressWords.any { lower.contains(it) }) return EmotionTone.EMPATHETIC

        return EmotionTone.NORMAL
    }

    private fun speak(
        text: String,
        tone: EmotionTone = EmotionTone.NORMAL,
        onDone: (() -> Unit)? = null
    ) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            val ttsText   = com.demo.butler_voice_app.utils.ButlerSpeechFormatter.format(finalText, lang)
            Log.d("Butler", "Original: $text")
            Log.d("Butler", "TTS ($lang): $ttsText")
            runOnUiThread {
                setUiState(ButlerUiState.Speaking(ttsText))
                ttsManager.speak(text = ttsText, language = lang, tone = tone, onDone = { onDone?.invoke() })
            }
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)?) = speak(text, EmotionTone.NORMAL, onDone)

    private fun speakKeepingRecsVisible(
        text: String,
        tone: EmotionTone = EmotionTone.NORMAL,
        onDone: (() -> Unit)? = null
    ) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            runOnUiThread {
                ttsManager.speak(text = finalText, language = lang, tone = tone, onDone = { onDone?.invoke() })
            }
        }
    }
    private fun speakKeepingRecsVisible(text: String, onDone: (() -> Unit)?) =
        speakKeepingRecsVisible(text, EmotionTone.NORMAL, onDone)

    private fun speakKeepingQRVisible(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("Butler", "QR speak (no UI change): $finalText")
            runOnUiThread { ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() }) }
        }
    }

    private suspend fun translateToEnglish(text: String): String {
        return withContext(Dispatchers.IO) {
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
                val response = okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                        .addHeader("Content-Type", "application/json")
                        .post(body).build()
                ).execute()
                org.json.JSONObject(response.body?.string() ?: return@withContext text)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            } catch (e: Exception) {
                Log.e("Butler", "translateToEnglish failed: ${e.message}"); text
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SERVICE ROUTING HELPER
    // ══════════════════════════════════════════════════════════════════════

    private fun mapCategoryToSector(category: String?): ServiceSector? {
        if (category.isNullOrBlank()) return null
        val cat = category.lowercase().trim()
        return when {
            cat.contains("electric")                             -> ServiceSector.ELECTRICIAN
            cat.contains("plumb")                                -> ServiceSector.PLUMBER
            cat.contains("clean") || cat.contains("maid")
                    || cat.contains("cook")                      -> ServiceSector.CLEANING
            cat.contains("carpenter") || cat.contains("carpent") -> ServiceSector.CARPENTER
            cat.contains("paint")                                -> ServiceSector.PAINTER
            cat.contains("doctor") || cat.contains("physician")
                    || cat.contains("specialist")                -> ServiceSector.DOCTOR
            cat.contains("medicine") || cat.contains("pharmacy")
                    || cat.contains("chemist")                   -> ServiceSector.MEDICINE
            cat.contains("mechanic") || cat.contains("vehicle")
                    || cat.contains("car service")
                    || cat.contains("car wash")                  -> ServiceSector.CAR_SERVICE
            cat.contains("pest")                                 -> ServiceSector.PEST_CONTROL
            cat.contains("ac") || cat.contains("air condition")
                    || cat.contains("appliance")                 -> ServiceSector.AC_REPAIR
            cat.contains("laundry") || cat.contains("wash")      -> ServiceSector.CLEANING
            cat.contains("beauty") || cat.contains("salon")
                    || cat.contains("parlour")
                    || cat.contains("haircut")                   -> ServiceSector.SALON
            cat.contains("spa") || cat.contains("massage")       -> ServiceSector.SPA
            cat.contains("security") || cat.contains("guard")    -> ServiceSector.SECURITY
            cat.contains("nurse") || cat.contains("nursing")
                    || cat.contains("home care")                 -> ServiceSector.HOME_NURSING
            cat.contains("ambulance") || cat.contains("emergency") -> ServiceSector.AMBULANCE
            cat.contains("food") || cat.contains("delivery")     -> ServiceSector.FOOD
            cat.contains("taxi") || cat.contains("cab")
                    || cat.contains("auto")                      -> ServiceSector.TAXI
            cat.contains("tutor") || cat.contains("teacher")
                    || cat.contains("coaching")                  -> ServiceSector.TUTOR
            else                                                 -> null
        }
    }
}