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
    CONFIRMING_ADD_PRODUCT, ASKING_PRODUCT_TYPE, ASKING_PAYMENT_MODE,
    WAITING_CARD_PAYMENT, WAITING_UPI_PAYMENT, WAITING_QR_PAYMENT,
    CONFIRMING_CARD_PAID, CONFIRMING_UPI_PAID, CONFIRMING_QR_PAID,
    IN_SERVICE_FLOW, IN_SERVICE_SUBTYPE_FLOW, ASKING_WHO
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
    private var tempName = ""; private var tempEmail = ""; private var tempPhone = ""
    private var sttRetryCount = 0; private var totalEmptyRetries = 0; private var sttErrorCount = 0
    private var pendingReorderSuggestions: List<ReorderSuggestion> = emptyList()
    private var lastOrderId = ""; private var lastPublicId = ""; private var lastOrderTotal = 0.0
    private var sessionLastProduct: String? = null; private var sessionLastQty: Int = 0
    private var lastBookingId: String? = null
    private var sessionUserName: String = "ji"
    private var pendingAddRecs: List<ProductRecommendation> = emptyList()
    private var pendingAddIndex = 0; private var pendingAddQty = 1; private var pendingAddItemName = ""
    private var pendingProductCategory: String = ""
    private var pendingProactiveData: ProactiveData? = null
    private var currentMood: UserMood = UserMood.CALM

    private data class ServiceSubTypeSession(val sector: ServiceSector, val originalTranscript: String, var retryCount: Int = 0)
    private var serviceSubTypeSession: ServiceSubTypeSession? = null

    private lateinit var ttsManager: TTSManager
    private lateinit var sarvamSTT: SarvamSTTManager
    private lateinit var porcupine: WakeWordManager

    private val apiClient = ApiClient()
    private val cart = mutableListOf<CartItem>()
    private var tempProduct: ApiClient.Product? = null
    private var currentState = AssistantState.IDLE
    private val recordRequestCode = 101

    @Volatile private var sttListenId = 0

    private fun toSpeakableAmount(amount: Double): String {
        val n = amount.toInt(); val lang = LanguageManager.getLanguage()
        return if (lang.startsWith("hi") || lang.startsWith("te") || lang.startsWith("mr"))
            "${com.demo.butler_voice_app.utils.ButlerSpeechFormatter.numberToHindi(n)} rupaye"
        else "$n rupees"
    }

    private fun speakFillerThen(action: () -> Unit) { action() }

    private fun speakWithTransition(text: String, onDone: (() -> Unit)? = null) =
        speak("${HumanFillerManager.getTransition(LanguageManager.getLanguage())} $text", onDone)

    private fun speakWithMoodContext(text: String, onDone: (() -> Unit)? = null) {
        val moodAck = HumanFillerManager.getMoodAck(currentMood, LanguageManager.getLanguage())
        speak(if (moodAck != null) "$moodAck $text" else text, onDone)
    }

    private fun buildShortConfirm(lang: String): String {
        val items = cart.joinToString(", ") { item ->
            val n = item.product.name.lowercase().split(" ").take(2).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            "${item.quantity} $n"
        }
        return ButlerPersonalityEngine.confirmOrder(sessionUserName, items, "₹${cart.sumOf { it.product.price * it.quantity }.toInt()}", lang)
    }

    // ══════════════════════════════════════════════════════════════════════
    // CENTRAL ROUTER
    // ══════════════════════════════════════════════════════════════════════

    private fun routeTranscript(text: String, lower: String): Boolean {
        val lang = LanguageManager.getLanguage()
        val emotionTone = detectEmotionTone(lower)
        if (ServiceVoiceHandler.isEmergency(lower) || emotionTone == EmotionTone.EMERGENCY) {
            val emergencyText = when {
                lang.startsWith("hi") -> "Ghabraiye mat! Abhi ambulance bula raha hoon. Ek oh aath par call ho raha hai."
                lang.startsWith("te") -> "Bayapadakandi! Ippude ambulance pilustunnanu. 108ku call avutundi."
                lang.startsWith("ta") -> "Bayappadatheenga! Ippo ambulance azhaikkiren."
                lang.startsWith("kn") -> "Bhayapadabedi! Egalee ambulance karayttiddeeni."
                lang.startsWith("ml") -> "Pedikaanda! Ippol ambulance vilikkunnu."
                else -> "Don't worry! Calling ambulance right now. Dialing 108."
            }
            speak(emergencyText, EmotionTone.EMERGENCY) { launchServiceFlow(text) }; return true
        }
        if (ServiceVoiceHandler.isPrescriptionRequest(lower)) {
            val rxText = when { lang.startsWith("hi") -> "Theek hai, prescription ke liye camera khol raha hoon."; else -> "Opening camera for your prescription." }
            speak(rxText, EmotionTone.EMPATHETIC) { launchServiceFlow(text) }; return true
        }
        if (currentState == AssistantState.LISTENING || currentState == AssistantState.REORDER_CONFIRM) {
            if (ServiceVoiceHandler.isServiceRequest(lower)) {
                val intent = ServiceManager.detectServiceIntent(text); val sector = intent.sector
                if (sector != null && ServiceVoiceHandler.hasSectorSubTypes(sector)) {
                    serviceSubTypeSession = ServiceSubTypeSession(sector, text)
                    currentState = AssistantState.IN_SERVICE_SUBTYPE_FLOW
                    speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.subTypePrompt(sector, lang), emotionTone) { startListening() }
                } else {
                    speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(sector ?: ServiceSector.ELECTRICIAN, lang), emotionTone) { launchServiceFlow(text, overrideSector = sector) }
                }
                return true
            }
        }
        if (StatusCheckHandler.isStatusQuery(lower) && (currentState == AssistantState.LISTENING || currentState == AssistantState.REORDER_CONFIRM)) {
            handleStatusQuery(text); return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    // LAUNCHERS
    // ══════════════════════════════════════════════════════════════════════

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                                sessionUserName = firstName
                                speak(IndianLanguageProcessor.getWelcomeGreeting(LanguageManager.getLanguage(), firstName)) { startListening() }
                            },
                            onFailure = { err ->
                                if (err.message?.contains("already") == true) speak("Account already exists. Logging you in.") { lifecycleScope.launch { doLogin(email, pass) } }
                                else speak("Account creation failed. Please try again.") { startWakeWordListening() }
                            }
                        )
                    } else { setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.LOGGING_IN)); doLogin(email, pass) }
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
                            sessionUserName = firstName
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

    private val serviceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try { startLockTask() } catch (_: Exception) {}
        currentState = AssistantState.IDLE
        Handler(Looper.getMainLooper()).postDelayed({
            val bookingId = result.data?.getStringExtra("booking_id")
            if (!bookingId.isNullOrBlank()) {
                lastBookingId = bookingId
                speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.returnToMain(bookingId, LanguageManager.getLanguage())) { currentState = AssistantState.LISTENING; startListening() }
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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        setContent { val state by uiState; ButlerScreen(state = state) }
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL; audioManager.isSpeakerphoneOn = true
        SessionStore.init(this); FamilyProfileManager.load(this); startLocationUpdates()
        sarvamSTT = SarvamSTTManager(this, BuildConfig.SARVAM_API_KEY)
        porcupine = WakeWordManager(this, BuildConfig.PORCUPINE_ACCESS_KEY) { runOnUiThread { onWakeWordDetected() } }
        ttsManager = TTSManager(context = this, elevenLabsApiKey = BuildConfig.ELEVENLABS_API_KEY, voiceId = "K2Byg54sHB1oHegvENtI")
        ttsManager.init { checkMicPermission() }
        lifecycleScope.launch(Dispatchers.IO) { try { apiClient.prefetchProducts(); Log.d("Butler", "Cache warmed") } catch (_: Exception) {} }
        ProactiveButlerWorker.schedule(this); checkProactiveLaunch()
    }

    override fun onPause()   { super.onPause();   porcupine.stop(); sarvamSTT.stop() }
    override fun onDestroy() { super.onDestroy(); porcupine.stop(); sarvamSTT.stop(); ttsManager.shutdown() }
    override fun onResume()  { super.onResume(); if (currentState == AssistantState.IDLE) { try { startLockTask() } catch (_: Exception) {} } }

    private fun checkProactiveLaunch() {
        val fromNotification = intent?.getBooleanExtra("proactive_launch", false) ?: false
        if (!fromNotification) return
        val data = ProactiveSession.consumePendingMessage(this) ?: return
        currentState = AssistantState.CHECKING_AUTH
        lifecycleScope.launch {
            val restored = UserSessionManager.tryRestoreSession()
            runOnUiThread {
                if (restored && UserSessionManager.currentProfile != null) {
                    currentState = AssistantState.REORDER_CONFIRM; pendingProactiveData = data
                    speak(data.message) { startListening() }
                } else startWakeWordListening()
            }
        }
    }

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
        currentState = AssistantState.IDLE; sttRetryCount = 0; emailRetryCount = 0; phoneRetryCount = 0
        cart.clear(); tempName = ""; tempEmail = ""; tempPhone = ""
        pendingReorderSuggestions = emptyList(); pendingProactiveData = null
        sessionLastProduct = null; sessionLastQty = 0; serviceSubTypeSession = null
        currentMood = UserMood.CALM; totalEmptyRetries = 0
        MoodDetector.reset(); FamilyProfileManager.clearActive(); LanguageManager.reset()
        SessionLanguageManager.reset(); ButlerPersonalityEngine.resetSession()
        AIParser.resetDebounce(); apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle); sttListenId++
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
            var restored = UserSessionManager.tryRestoreSession()
            if (!restored && SessionStore.hasRefreshToken()) {
                if (attemptTokenRefresh()) restored = UserSessionManager.tryRestoreSession()
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
                        speak(question) { startListening() }; return@runOnUiThread
                    }
                    val name = profile.full_name?.split(" ")?.first() ?: "there"
                    AnalyticsManager.logSessionStart(UserSessionManager.currentUserId(), lang)
                    proceedAfterIdentification(name, UserSessionManager.purchaseHistory, lang)
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
            val client = okhttp3.OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
            val request = okhttp3.Request.Builder()
                .url("${SupabaseClient.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SupabaseClient.SUPABASE_KEY).addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            val json = org.json.JSONObject(response.body?.string() ?: return@withContext false)
            val newAccess  = json.optString("access_token")
            val newRefresh = json.optString("refresh_token")
            if (newAccess.isBlank()) return@withContext false
            SessionStore.updateTokens(newAccess, newRefresh); true
        } catch (e: Exception) { false }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PROCEED AFTER IDENTIFICATION
    // ══════════════════════════════════════════════════════════════════════
    // PATCH 1: Restore stored language so greeting is in user's language.
    // Roy spoke Hindi last session → storedLang="hi" → forceSet("hi-IN") → Hindi greeting.

    private fun proceedAfterIdentification(name: String, history: List<com.demo.butler_voice_app.api.PurchaseSummary>, lang: String) {
        // ── PATCH 1 START ──────────────────────────────────────────────
        val uid           = UserSessionManager.currentUserId() ?: ""
        val storedLang    = SessionStore.getUserLanguage(uid)
        val effectiveLang = if (storedLang != "en") storedLang else lang
        Log.d("Butler", "Language restore: stored=$storedLang incoming=$lang effective=$effectiveLang")
        // ── PATCH 1 END ────────────────────────────────────────────────

        val lockedCode = when {
            effectiveLang.startsWith("hi") -> "hi-IN"
            effectiveLang.startsWith("te") -> "te-IN"
            effectiveLang.startsWith("ta") -> "ta-IN"
            effectiveLang.startsWith("kn") -> "kn-IN"
            effectiveLang.startsWith("ml") -> "ml-IN"
            effectiveLang.startsWith("pa") -> "pa-IN"
            effectiveLang.startsWith("gu") -> "gu-IN"
            effectiveLang.startsWith("mr") -> "mr-IN"
            else                            -> "en-IN"
        }
        SessionLanguageManager.forceSet(lockedCode)
        LanguageManager.setLanguage(effectiveLang)
        sessionUserName = name

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
        val shortLast   = lastProduct?.lowercase()?.split(" ")?.take(2)?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val greeting    = ButlerPersonalityEngine.greeting(name, effectiveLang, shortLast, currentMood)
        speak(greeting, EmotionTone.WARM) { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VOICE SIGNUP
    // ══════════════════════════════════════════════════════════════════════

    private fun startVoiceSignupFlow() {
        currentState = AssistantState.ASKING_IS_NEW_USER
        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_NEW_OR_RETURNING, prompt = "Are you a new customer, or have you ordered before?"))
        speak("Welcome to Butler! Are you a new customer, or have you ordered before?") { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // STT
    // ══════════════════════════════════════════════════════════════════════
    // PATCH 2: After language switch detected, save it to SessionStore.
    // Next session, proceedAfterIdentification reads it back → correct greeting.

    private fun startListening() {
        sarvamSTT.stop()
        val myId = ++sttListenId
        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")

        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    if (myId != sttListenId) { Log.d("Butler", "STT callback discarded (stale id $myId, current $sttListenId)"); return@runOnUiThread }
                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")
                    val pcm = sarvamSTT.lastPcmBuffer; val duration = sarvamSTT.lastRecordingDurationMs
                    if (pcm.isNotEmpty()) { currentMood = MoodDetector.analyse(pcm, duration); Log.d("Butler", "Mood: $currentMood") }
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
                            // ── PATCH 2 START ──────────────────────────────────────────
                            // Save detected language so next session greeting is correct.
                            // Skip saving "en" — that's just the fallback default.
                            if (newBase != "en") {
                                UserSessionManager.currentUserId()?.let { uid ->
                                    SessionStore.saveUserLanguage(uid, newBase)
                                    Log.d("Butler", "Language preference saved: $newBase for $uid")
                                }
                            }
                            // ── PATCH 2 END ────────────────────────────────────────────
                        } else {
                            val locked = SessionLanguageManager.ttsLanguage
                            if (LanguageManager.getLanguage() != locked) LanguageManager.setLanguage(locked)
                        }
                    }

                    if (transcript.isBlank()) {
                        SessionLanguageManager.onBlankTranscript()
                        sttRetryCount++; totalEmptyRetries++
                        val lang = LanguageManager.getLanguage()
                        if (totalEmptyRetries >= 5) {
                            totalEmptyRetries = 0; sttRetryCount = 0; MoodDetector.reset()
                            speak(ButlerPersonalityEngine.giveUp(lang, currentMood), ButlerPersonalityEngine.toneForGiveUp()) { startWakeWordListening() }
                            return@runOnUiThread
                        }
                        if (sttRetryCount < 3) startListening()
                        else { sttRetryCount = 0; speak(ButlerPersonalityEngine.didntHear(lang, currentMood, totalEmptyRetries), ButlerPersonalityEngine.toneForRetry()) { startListening() } }
                        return@runOnUiThread
                    }

                    totalEmptyRetries = 0; sttRetryCount = 0; sttErrorCount = 0; MoodDetector.reset()
                    setUiState(ButlerUiState.Thinking(transcript))
                    handleCommand(transcript)
                }
            },
            onError = {
                runOnUiThread {
                    val lang = LanguageManager.getLanguage(); sttErrorCount++
                    if (sttErrorCount >= 2) {
                        sttErrorCount = 0
                        val waitMsg = when {
                            lang.startsWith("hi") -> "Ek pal..."; lang.startsWith("te") -> "Okka nimisham..."
                            lang.startsWith("ta") -> "Oru nimisham..."; lang.startsWith("kn") -> "Ondu nimisha..."
                            lang.startsWith("ml") -> "Oru nimisham..."; lang.startsWith("pa") -> "Ikk pal..."
                            lang.startsWith("gu") -> "Ek kshan..."; lang.startsWith("mr") -> "Ek kshan..."
                            else -> "One moment..."
                        }
                        speak(waitMsg) { Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 3000) }
                    } else {
                        val errReply = when {
                            lang.startsWith("hi") -> "Phir se bolein."; lang.startsWith("te") -> "Malli cheppandi."
                            lang.startsWith("ta") -> "Meendum sollunga."; lang.startsWith("kn") -> "Matte heli."
                            lang.startsWith("ml") -> "Veedum parayo."; lang.startsWith("pa") -> "Phir dasao."
                            lang.startsWith("gu") -> "Fari bolo."; else -> "Please say that again."
                        }
                        speak(errReply) { startListening() }
                    }
                }
            }
        )
    }

    private fun startListeningForSelection(onNumber: (Int) -> Unit, onOther: (String) -> Unit, retryPrompt: String = "Please say 1, 2, or 3.") {
        sarvamSTT.stop(); sttListenId++; setUiState(ButlerUiState.Listening)
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    if (transcript.isBlank()) {
                        val lang = LanguageManager.getLanguage()
                        val blankMsg = when {
                            lang.startsWith("hi") -> "Brand ka naam btaiye."; lang.startsWith("te") -> "Brand peyru cheppandi."
                            lang.startsWith("ta") -> "Brand peyar sollungal."; lang.startsWith("kn") -> "Brand hesaru heli."
                            lang.startsWith("ml") -> "Brand peru parayo."; lang.startsWith("pa") -> "Brand naam dasao."
                            lang.startsWith("gu") -> "Brand naam bolo."; else -> "Say the brand name."
                        }
                        speak(blankMsg) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                sarvamSTT.startListening(
                                    onResult = { t2 -> runOnUiThread { val t = t2.trim(); val num = detectNumberFromSpeech(t); when { num > 0 -> onNumber(num); t.isNotBlank() -> onOther(t); else -> speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) } } } },
                                    onError  = { runOnUiThread { speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) } } }
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
                    sttErrorCount++
                    if (sttErrorCount >= 2) { sttErrorCount = 0; speak("Ek pal...") { Handler(Looper.getMainLooper()).postDelayed({ startListeningForSelection(onNumber, onOther, retryPrompt) }, 3000) } }
                    else speak(retryPrompt) { startListeningForSelection(onNumber, onOther, retryPrompt) }
                }
            }
        )
    }

    private fun detectNumberFromSpeech(spoken: String): Int {
        val s = spoken.lowercase().trim().replace(Regex("[,।.!?॥]"), "").trim()
        Regex("\\b([123])\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return when {
            s.contains("one") || s.contains("first") || s.contains("pehla") || s.contains("ek") || s.contains("एक") || s.contains("option 1") || s.contains("number 1") -> 1
            s.contains("two") || s.contains("second") || s.contains("doosra") || s.contains("do") || s.contains("दो") || s.contains("రెండు") || s.contains("option 2") || s.contains("number 2") -> 2
            s.contains("three") || s.contains("third") || s.contains("teesra") || s.contains("teen") || s.contains("तीन") || s.contains("మూడు") || s.contains("option 3") || s.contains("number 3") -> 3
            else -> -1
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SERVICE FLOW
    // ══════════════════════════════════════════════════════════════════════

    private fun launchServiceFlow(transcript: String, overrideSector: ServiceSector? = null, subTypeId: String? = null) {
        currentState = AssistantState.IN_SERVICE_FLOW; serviceSubTypeSession = null; sttListenId++
        try { stopLockTask() } catch (_: Exception) {}
        val serviceIntent = if (overrideSector != null) com.demo.butler_voice_app.services.ServiceIntent(overrideSector, transcript) else ServiceManager.detectServiceIntent(transcript)
        val intent = Intent(this, ServiceActivity::class.java).apply {
            putExtra(ServiceActivity.EXTRA_SECTOR,   serviceIntent.sector?.name ?: "")
            putExtra(ServiceActivity.EXTRA_QUERY,    transcript)
            putExtra(ServiceActivity.EXTRA_IS_RX,    serviceIntent.isPrescription)
            putExtra(ServiceActivity.EXTRA_IS_EMERG, serviceIntent.isEmergency)
            putExtra("user_lat", userLocation?.latitude ?: 0.0); putExtra("user_lng", userLocation?.longitude ?: 0.0)
            putExtra("sub_type", subTypeId ?: "")
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
                val session = serviceSubTypeSession ?: run { currentState = AssistantState.LISTENING; startListening(); return }
                val matched = ServiceVoiceHandler.matchSubType(text, session.sector)
                if (matched != null) {
                    speak("${matched.getDisplay(lang)} — ${ButlerPhraseBank.get("service_when", lang)}") {
                        sarvamSTT.startListening(
                            onResult = { timeText ->
                                runOnUiThread {
                                    val timeSlot = extractTimeSlotFromSpeech(timeText.trim())
                                    speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.bookingConfirmPrompt(session.sector, matched.displayEn.ifBlank { matched.id }, timeSlot, lang)) {
                                        sarvamSTT.startListening(
                                            onResult = { confirmText ->
                                                runOnUiThread {
                                                    val c = confirmText.trim().lowercase()
                                                    if (MultilingualMatcher.isYes(c) || c.contains("haan") || c.contains("ok") || c.contains("pakka")) {
                                                        speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(session.sector, lang)) {
                                                            launchServiceFlow("${session.originalTranscript} ${matched.displayEn} $timeSlot", session.sector, matched.id)
                                                        }
                                                    } else {
                                                        serviceSubTypeSession = null; currentState = AssistantState.LISTENING
                                                        speak(if (lang.startsWith("hi")) "Theek hai. Koi aur service chahiye?" else "No problem. Anything else?") { startListening() }
                                                    }
                                                }
                                            },
                                            onError = { runOnUiThread { launchServiceFlow(session.originalTranscript, session.sector, matched.id) } }
                                        )
                                    }
                                }
                            },
                            onError = { runOnUiThread { launchServiceFlow(session.originalTranscript, session.sector, matched.id) } }
                        )
                    }
                } else {
                    session.retryCount++
                    if (session.retryCount <= 2) speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.subTypeRetry(session.sector, lang)) { startListening() }
                    else { serviceSubTypeSession = null; speak(com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(session.sector, lang)) { launchServiceFlow(session.originalTranscript, session.sector, null) } }
                }
            }

            AssistantState.ASKING_WHO -> {
                val detected = FamilyProfileManager.detectSpeaker(text)
                if (detected != null) {
                    FamilyProfileManager.setActive(detected); currentState = AssistantState.LISTENING
                    AnalyticsManager.logSessionStart(detected.userId, lang)
                    lifecycleScope.launch {
                        val suggestions = SmartReorderManager.getSuggestions(detected.userId)
                        val smartMsg    = SmartReorderManager.buildReorderGreeting(suggestions, detected.displayName)
                        runOnUiThread {
                            if (smartMsg != null && suggestions.isNotEmpty()) {
                                pendingReorderSuggestions = suggestions; currentState = AssistantState.REORDER_CONFIRM
                                speak(smartMsg) { startListening() }
                            } else speak(FamilyProfileManager.buildPersonalGreeting(detected, lang)) { startListening() }
                        }
                    }
                } else {
                    val members = FamilyProfileManager.getMembers(); val names = members.joinToString(", ") { it.displayName }
                    val retry = if (lang.startsWith("hi")) "Pehchan nahi hua. $names mein se kaun hain aap?" else "Didn't catch that — are you $names?"
                    setUiState(ButlerUiState.FamilySelection(members, retry))
                    speak(retry) {
                        val name = UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: "there"
                        currentState = AssistantState.LISTENING
                        speak(IndianLanguageProcessor.getWelcomeGreeting(lang, name)) { startListening() }
                    }
                }
            }

            AssistantState.ASKING_IS_NEW_USER -> {
                when {
                    cleaned.contains("new") || cleaned.contains("first") || cleaned.contains("register") || cleaned.contains("नया") -> {
                        currentState = AssistantState.ASKING_NAME
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_NAME, prompt = "What is your full name?"))
                        speak("Great! What is your full name?") { startListening() }
                    }
                    cleaned.contains("returning") || cleaned.contains("before") || cleaned.contains("login") || cleaned.contains("yes") || cleaned.contains("पहले") -> {
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
                        .replace(Regex("i am\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("mera naam\\s*", RegexOption.IGNORE_CASE), "").replace(".", "").trim()
                    if (nameText.isBlank() || nameText.contains("@")) { runOnUiThread { speak("Please say just your name clearly.") { startListening() } }; return@launch }
                    tempName = nameText.split(" ").firstOrNull { it.length > 1 }?.replaceFirstChar { it.uppercase() } ?: nameText.replaceFirstChar { it.uppercase() }
                    runOnUiThread {
                        currentState = AssistantState.ASKING_EMAIL
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_EMAIL, collectedName = tempName, prompt = "Nice to meet you $tempName! Please spell your email, letter by letter."))
                        speak("Nice to meet you $tempName! Please spell your email address, letter by letter.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_EMAIL -> {
                val parsed = EmailPasswordParser.parseEmail(text)
                if (parsed != null) {
                    tempEmail = parsed; emailRetryCount = 0; currentState = AssistantState.ASKING_PHONE
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PHONE, collectedName = tempName, collectedEmail = tempEmail, prompt = "What is your 10-digit mobile number?"))
                    speak("Got it — $tempEmail. Now, what is your 10-digit mobile number?") { startListening() }
                } else {
                    emailRetryCount++
                    if (emailRetryCount <= 2) speak(EmailPasswordParser.getEmailRetryPrompt(lang)) { startListening() }
                    else {
                        val fallback = EmailPasswordParser.parseEmailBestEffort(text)
                        if (fallback.contains("@")) {
                            tempEmail = fallback; emailRetryCount = 0; currentState = AssistantState.ASKING_PHONE
                            setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PHONE, collectedName = tempName, collectedEmail = tempEmail))
                            speak("OK, I got $tempEmail. What is your mobile number?") { startListening() }
                        } else {
                            emailRetryCount = 0
                            speak("I am having trouble. Let me show you the sign-in screen.") { try { stopLockTask() } catch (_: Exception) {}; authLauncher.launch(Intent(this@MainActivity, AuthActivity::class.java)) }
                        }
                    }
                }
            }

            AssistantState.ASKING_PHONE -> {
                val digits = text.replace(Regex("[^0-9]"), "")
                val phone  = when { digits.length == 10 -> digits; digits.length == 12 && digits.startsWith("91") -> digits.substring(2); digits.length > 10 -> digits.takeLast(10); else -> "" }
                if (phone.length == 10) {
                    tempPhone = phone; phoneRetryCount = 0; currentState = AssistantState.ASKING_PASSWORD
                    setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PASSWORD, tempName, tempEmail, tempPhone, "Now choose a password."))
                    speak("Got it — $tempPhone. Now choose a password. Spell each character clearly.") { startListening() }
                } else {
                    phoneRetryCount++
                    if (phoneRetryCount <= 2) speak("Please say your 10-digit mobile number again, digit by digit.") { startListening() }
                    else {
                        phoneRetryCount = 0; currentState = AssistantState.ASKING_PASSWORD
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.ASK_PASSWORD, tempName, tempEmail, prompt = "Let us set your password."))
                        speak("No problem, let us skip phone. Please choose a password.") { startListening() }
                    }
                }
            }

            AssistantState.ASKING_PASSWORD -> {
                val password = EmailPasswordParser.parsePassword(text).ifBlank { text.trim().replace(" ", "").trimEnd('.', ',', '!') }
                if (password.length < 6) { speak("Password must be at least 6 characters. Please try again.") { startListening() }; return }
                lifecycleScope.launch {
                    if (tempName.isNotBlank()) {
                        setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.CREATING_ACCOUNT, tempName, tempEmail))
                        speak("Creating your account, please wait.") {}
                        UserSessionManager.signup(tempEmail, password, tempName, tempPhone).fold(
                            onSuccess = { profile ->
                                FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                                currentState = AssistantState.LISTENING
                                val firstName = profile.full_name?.split(" ")?.first() ?: tempName
                                sessionUserName = firstName
                                AnalyticsManager.logUserAuth("voice_signup", LanguageManager.getLanguage())
                                speak(IndianLanguageProcessor.getWelcomeGreeting(lang, firstName)) { startListening() }
                            },
                            onFailure = { error ->
                                if (error.message?.contains("user_already_exists") == true || error.message?.contains("already registered") == true)
                                    speak("Account already exists. Logging you in.") { lifecycleScope.launch { doLogin(tempEmail, password) } }
                                else speak("Sorry, could not create account. Please try again.") { startWakeWordListening() }
                            }
                        )
                    } else { setUiState(ButlerUiState.VoiceSignupStep(ButlerUiState.SignupStep.LOGGING_IN)); doLogin(tempEmail, password) }
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
                                    if (product != null) { cart.add(CartItem(product, proactive.quantity)); currentState = AssistantState.CONFIRMING; showCartAndSpeak(buildShortConfirm(LanguageManager.getLanguage())) { startListening() } }
                                    else { currentState = AssistantState.LISTENING; speak("${proactive.productName} nahi mila. ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() } }
                                }
                            }
                        }
                        MultilingualMatcher.isNo(cleaned) -> { pendingProactiveData = null; currentState = AssistantState.LISTENING; speak("Theek hai! ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() } }
                        else -> { pendingProactiveData = null; currentState = AssistantState.LISTENING; handleOrderIntent(text, lower) }
                    }
                    return
                }
                when {
                    MultilingualMatcher.isYes(cleaned) && !isNoMoreIntent(cleaned) && IndianLanguageProcessor.detectIntent(cleaned) != "order_new" -> {
                        lifecycleScope.launch {
                            for (s in pendingReorderSuggestions) apiClient.searchProduct(s.productName)?.let { cart.add(CartItem(it, s.avgQty)) }
                            runOnUiThread {
                                if (cart.isNotEmpty()) { currentState = AssistantState.CONFIRMING; showCartAndSpeak(buildShortConfirm(LanguageManager.getLanguage())) { startListening() } }
                                else { currentState = AssistantState.LISTENING; speak("Nahi mila. ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() } }
                            }
                        }
                    }
                    MultilingualMatcher.isNo(cleaned) -> {
                        pendingReorderSuggestions = emptyList(); currentState = AssistantState.LISTENING
                        val strippedText = text.replace(Regex("^(nahi|nahin|nope|no|नहीं|नही|ना|వద్దు|வேண்டாம்|ಬೇಡ|വേണ്ട|ਨਹੀਂ|ના)[,।\\.\\s]+", RegexOption.IGNORE_CASE), "").trim()
                        val instant = instantGroceryDetect(strippedText.lowercase(), LanguageManager.getLanguage())
                        when {
                            instant != null -> searchAndAskQuantity(instant.first, instant.second)
                            strippedText.isNotBlank() && strippedText.length < text.length -> handleOrderIntent(strippedText, strippedText.lowercase())
                            else -> {
                                val noMsg = when {
                                    lang.startsWith("hi") -> "Theek hai! Kya chahiye?"; lang.startsWith("te") -> "Sare! Emi kavali?"
                                    lang.startsWith("ta") -> "Sari! Enna vendum?"; lang.startsWith("kn") -> "Sari! Enu beku?"
                                    lang.startsWith("ml") -> "Sheri! Enthu veno?"; else -> "No problem! What would you like?"
                                }
                                speak(noMsg) { startListening() }
                            }
                        }
                    }
                    else -> { pendingReorderSuggestions = emptyList(); currentState = AssistantState.LISTENING; handleOrderIntent(text, lower) }
                }
            }

            AssistantState.LISTENING -> handleOrderIntent(text, lower)

            AssistantState.ASKING_QUANTITY -> {
                val qty = extractQuantity(text); val product = tempProduct
                if (product != null) {
                    cart.add(CartItem(product, qty)); sessionLastProduct = product.name; sessionLastQty = qty
                    currentState = AssistantState.ASKING_MORE
                    showCartAndSpeak(ButlerPersonalityEngine.itemAdded(sessionUserName, product.name, lang, currentMood, cart.size)) { startListening() }
                } else speak(ButlerPersonalityEngine.productNotFound("", lang)) { currentState = AssistantState.LISTENING; startListening() }
            }

            AssistantState.ASKING_MORE            -> handleAskingMore(cleaned, text)
            AssistantState.CONFIRMING_ADD_PRODUCT -> handleConfirmingAddProduct(cleaned)
            AssistantState.ASKING_PRODUCT_TYPE    -> handleAskingProductType(cleaned)
            AssistantState.EDITING_CART           -> handleCartEdit(cleaned, text)

            AssistantState.CONFIRMING -> {
                val intentFromLang = IndianLanguageProcessor.detectIntent(cleaned)
                when {
                    intentFromLang == "confirm" || MultilingualMatcher.isYes(cleaned) -> askPaymentMode()
                    intentFromLang == "cancel"  || MultilingualMatcher.isNo(cleaned)  -> speak("Theek hai, cancel.") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                    isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, text) }
                    else -> {
                        lifecycleScope.launch {
                            val parsed = AIOrderParser.parse(text); LanguageManager.setLanguage(parsed.detectedLanguage)
                            runOnUiThread {
                                when (parsed.intent) {
                                    "confirm_order" -> askPaymentMode()
                                    "cancel_order"  -> speak("Theek hai, cancel.") { cart.clear(); UserSessionManager.logout(); startWakeWordListening() }
                                    else            -> speak("Haan bolein toh order, na bolein toh cancel.") { startListening() }
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
        lifecycleScope.launch {
            val result = StatusCheckHandler.handleStatusQuery(text, lang, firstName, userId, lastBookingId)
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
        val card = PaymentManager.getSavedCard(this)
        setUiState(ButlerUiState.PaymentChoice(pendingOrderTotal, pendingOrderSummary, card != null, if (card != null) "${card.network} card ending ${card.last4}" else ""))
        speak(ButlerPersonalityEngine.askPaymentMode(sessionUserName, LanguageManager.getLanguage())) { startListening() }
    }

    private fun handlePaymentModeChoice(cleaned: String) {
        val lang    = LanguageManager.getLanguage()
        val hasUPI  = cleaned.contains("upi") || cleaned.contains("google pay") || cleaned.contains("phonepe") || cleaned.contains("paytm") || cleaned.contains("bhim") || cleaned.contains("gpay")
        val hasCard = cleaned.contains("card") || cleaned.contains("debit") || cleaned.contains("credit")
        val hasQR   = cleaned.contains("qr") || cleaned.contains("scan")
        when {
            hasUPI && hasCard -> speak(if (lang.startsWith("hi")) "UPI se doge ya card se?" else "UPI or card?") { startListening() }
            hasUPI -> {
                currentState = AssistantState.WAITING_UPI_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("upi", pendingOrderTotal))
                speak(ButlerPersonalityEngine.upiInstruction("₹${pendingOrderTotal.toInt()}", lang)) { Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("upi") }, 3000) }
            }
            hasCard -> {
                val card = PaymentManager.getSavedCard(this)
                currentState = AssistantState.WAITING_CARD_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("card", pendingOrderTotal))
                speak(if (card != null) "${card.network} card ${card.last4} pe ₹${pendingOrderTotal.toInt()} charge hoga. Payment complete karein." else "Card details enter karein aur ₹${pendingOrderTotal.toInt()} pay karein.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("card") }, 2000)
                }
            }
            hasQR -> {
                currentState = AssistantState.WAITING_QR_PAYMENT
                setUiState(ButlerUiState.ShowQRCode(pendingOrderTotal, pendingOrderSummary))
                speakKeepingQRVisible("Screen pe QR code hai. Kisi bhi UPI app se ₹${pendingOrderTotal.toInt()} pay karein.") { Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("qr") }, 20000) }
            }
            else -> speak(if (lang.startsWith("hi")) "UPI se doge ya card se?" else "UPI or card?") { startListening() }
        }
    }

    private fun askIfPaid(mode: String) {
        currentState = when (mode) { "card" -> AssistantState.CONFIRMING_CARD_PAID; "upi" -> AssistantState.CONFIRMING_UPI_PAID; else -> AssistantState.CONFIRMING_QR_PAID }
        speak(ButlerPersonalityEngine.askIfPaid(LanguageManager.getLanguage(), mode, "₹${pendingOrderTotal.toInt()}")) { startListening() }
    }

    private fun handlePaidOrNotPaid(cleaned: String, mode: String) {
        val paid    = listOf("yes","paid","done","ho gaya","ho gayi","haan","ha","kar diya","transferred","confirm","हाँ","हां","हो गया","ਹਾਂ","ਹੋ ਗਈ","હા")
        val notPaid = listOf("no","not yet","haven't","wait","failed","nahi","abhi nahi","नहीं","ਨਹੀਂ","ਨਹੀ","ના")
        val lang    = LanguageManager.getLanguage()
        when {
            paid.any    { cleaned.contains(it) } -> speak(ButlerPersonalityEngine.paymentDone(lang), ButlerPersonalityEngine.toneForPaymentDone()) { placeOrder() }
            notPaid.any { cleaned.contains(it) } -> {
                speak(when (mode) { "card" -> "Theek hai! Card se ₹${pendingOrderTotal.toInt()} pay karein."; "upi" -> "Theek hai! UPI pe ₹${pendingOrderTotal.toInt()} bhejein."; else -> "Theek hai! QR scan karke ₹${pendingOrderTotal.toInt()} pay karein." }) {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid(mode) }, 8000)
                }
            }
            else -> speak("Haan bolein toh paid, nahi bolein toh cancel.") { startListening() }
        }
    }

    private fun handlePaymentConfirmation(cleaned: String, mode: String) { askIfPaid(mode) }

    // ══════════════════════════════════════════════════════════════════════
    // CART EDITING
    // ══════════════════════════════════════════════════════════════════════

    private fun isCartEditIntent(s: String) = listOf("remove","delete","हटाओ","హటావో","change","update","బదలు","बदलो").any { s.contains(it) }

    private fun handleCartEdit(cleaned: String, originalText: String) {
        val lang = LanguageManager.getLanguage()
        if (cart.isEmpty()) { speak(ButlerPersonalityEngine.cartEmpty(lang)) { currentState = AssistantState.LISTENING; startListening() }; return }
        val isRemove = cleaned.contains("remove") || cleaned.contains("delete") || cleaned.contains("हटाओ")
        val isChange = cleaned.contains("change") || cleaned.contains("update") || cleaned.contains("बदलो")
        if (isRemove) {
            val item = cart.firstOrNull { it.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (item != null) {
                cart.remove(item)
                if (cart.isEmpty()) speak("${ButlerPersonalityEngine.itemRemoved(item.product.name, lang)} ${ButlerPersonalityEngine.cartEmpty(lang)}") { currentState = AssistantState.LISTENING; startListening() }
                else { currentState = AssistantState.CONFIRMING; showCartAndSpeak("${ButlerPersonalityEngine.itemRemoved(item.product.name, lang)} ${buildShortConfirm(lang)}") { startListening() } }
            } else speak("Kaunsa item hatana hai?") { startListening() }
            return
        }
        if (isChange) {
            val newQty = extractQuantity(cleaned)
            val item   = cart.firstOrNull { it.product.name.lowercase().split(" ").any { w -> cleaned.contains(w) && w.length > 3 } }
            if (item != null && newQty > 0) {
                cart[cart.indexOf(item)] = CartItem(item.product, newQty); currentState = AssistantState.CONFIRMING
                showCartAndSpeak("${item.product.name} $newQty kar diya. ${buildShortConfirm(lang)}") { startListening() }
            } else speak("Kaunsa item aur kitna chahiye?") { startListening() }
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
        if (instant != null) { searchAndAskQuantity(instant.first, instant.second); return }

        speakFillerThen {
            lifecycleScope.launch {
                val fullParsed = AIParser.parse(text); LanguageManager.setLanguage(fullParsed.language)
                runOnUiThread {
                    when (val routing = fullParsed.routing) {
                        is IntentRouting.GoToService -> {
                            val sector = mapCategoryToSector(routing.category)
                            speak(if (sector != null) com.demo.butler_voice_app.services.ServiceVoiceEngine.sectorDetected(sector, lang) else com.demo.butler_voice_app.services.ServiceVoiceEngine.categoryPrompt(lang)) { launchServiceFlow(text, overrideSector = sector) }
                        }
                        is IntentRouting.GoToGrocery -> {
                            val items = routing.items
                            when {
                                items.isEmpty() -> { val fb = keywordFallback(cleaned); if (fb != null) searchAndAskQuantity(fb) else speak(ButlerPhraseBank.get("ask_item", lang)) { startListening() } }
                                items.size == 1 -> { val i = items.first(); searchAndAskQuantity(i.name, i.quantity, i.unit) }
                                else -> lifecycleScope.launch { addMultipleItemsToCart(items) }
                            }
                        }
                        is IntentRouting.FinishOrder  -> { if (cart.isEmpty()) speak("Cart khaali hai. ${ButlerPhraseBank.get("ask_item", lang)}") { startListening() } else readCartAndConfirm() }
                        is IntentRouting.ConfirmOrder -> { if (currentState == AssistantState.CONFIRMING) askPaymentMode() else readCartAndConfirm() }
                        is IntentRouting.CancelOrder  -> speak("Theek hai, cancel.") { startWakeWordListening() }
                        else -> {
                            val fb = keywordFallback(cleaned)
                            if (fb != null) searchAndAskQuantity(fb)
                            else {
                                val clarifyMsg = when {
                                    lang.startsWith("hi") -> "Kya mangwana hai? Rice, dal, tel, ya kuch aur?"
                                    lang.startsWith("te") -> "Emi kavali? Rice, dal, oil, leda inkaa emi?"
                                    lang.startsWith("ta") -> "Enna vendum? Rice, dal, oil, leda vera enna?"
                                    lang.startsWith("kn") -> "Enu beku? Rice, dal, oil, leda bere enu?"
                                    lang.startsWith("ml") -> "Enthu veno? Rice, dal, oil, allengil innum enthu?"
                                    lang.startsWith("pa") -> "Ki chahida? Rice, dal, oil, ya hor ki?"
                                    lang.startsWith("gu") -> "Shu joiye? Rice, dal, oil, ke biju ki?"
                                    else -> "What would you like? Say rice, dal, oil, or any grocery item."
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
            isNoMoreIntent(cleaned) -> readCartAndConfirm()
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("add") || cleaned.contains("more") || cleaned.contains("aur") -> {
                val instant    = instantGroceryDetect(cleaned, lang)
                val suggestion = getSuggestionSearchTerm(sessionLastProduct, lang)
                when {
                    instant != null    -> { currentState = AssistantState.LISTENING; searchAndAskQuantity(instant.first, instant.second) }
                    suggestion != null -> { currentState = AssistantState.LISTENING; searchAndAskQuantity(suggestion) }
                    else -> { currentState = AssistantState.LISTENING; showCartAndSpeak(ButlerPersonalityEngine.askMore(sessionUserName, lang, currentMood, cart.size, sessionLastProduct)) { startListening() } }
                }
            }
            isCartEditIntent(cleaned) -> { currentState = AssistantState.EDITING_CART; handleCartEdit(cleaned, originalText) }
            else -> {
                lifecycleScope.launch {
                    val fullParsed = AIParser.parse(originalText); LanguageManager.setLanguage(fullParsed.language)
                    runOnUiThread {
                        when (fullParsed.routing) {
                            is IntentRouting.FinishOrder, is IntentRouting.CancelOrder -> readCartAndConfirm()
                            is IntentRouting.GoToGrocery -> {
                                val items = (fullParsed.routing as IntentRouting.GoToGrocery).items
                                if (items.isNotEmpty()) { currentState = AssistantState.LISTENING; handleOrderIntent(originalText, originalText.lowercase()) }
                                else showCartAndSpeak(ButlerPhraseBank.get("ask_more", lang)) { startListening() }
                            }
                            else -> showCartAndSpeak(ButlerPhraseBank.get("ask_more", lang)) { startListening() }
                        }
                    }
                }
            }
        }
    }

    private fun handleAskingProductType(cleaned: String) {
        val refined = when {
            cleaned.contains("basmati")                                          -> "basmati rice"
            cleaned.contains("brown")                                            -> "brown rice"
            cleaned.contains("normal") || cleaned.contains("regular") || cleaned.contains("sona") || cleaned.contains("idli") -> "rice"
            cleaned.contains("toor") || cleaned.contains("arhar")               -> "toor dal"
            cleaned.contains("moong")                                            -> "moong dal"
            cleaned.contains("masoor")                                           -> "masoor dal"
            cleaned.contains("urad")                                             -> "urad dal"
            cleaned.contains("mustard") || cleaned.contains("sarson")           -> "mustard oil"
            cleaned.contains("sunflower")                                        -> "sunflower oil"
            cleaned.contains("coconut") || cleaned.contains("nariyal")          -> "coconut oil"
            cleaned.contains("wheat") || cleaned.contains("gehun")              -> "wheat atta"
            cleaned.contains("multigrain")                                       -> "multigrain atta"
            else -> pendingProductCategory
        }
        currentState = AssistantState.LISTENING; searchAndAskQuantity(refined)
    }

    private fun getSuggestionSearchTerm(lastProduct: String?, lang: String): String? {
        if (lastProduct == null) return null
        val p = lastProduct.lowercase()
        val map = mapOf("rice" to "dal","dal" to "rice","oil" to "atta","atta" to "oil","milk" to "bread","bread" to "butter","tea" to "sugar","sugar" to "tea","ghee" to "dal","eggs" to "bread","curd" to "rice","butter" to "bread")
        return map.entries.firstOrNull { p.contains(it.key) }?.value
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRODUCT SEARCH
    // ══════════════════════════════════════════════════════════════════════

    private fun buildProductVoiceReadout(recs: List<ProductRecommendation>, itemName: String, lang: String): String {
        fun shortName(r: ProductRecommendation) = r.productName.lowercase().split(" ").take(2).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val optionText = recs.joinToString(", ") { r -> "${shortName(r)} ₹${r.priceRs.toInt()}" }
        return when {
            lang.startsWith("hi") -> {
                val itemDisplay = when { itemName.contains("rice") || itemName.contains("chawal") -> "Rice"; itemName.contains("dal") -> "Daal"; itemName.contains("oil") || itemName.contains("tel") -> "Tel"; itemName.contains("milk") || itemName.contains("doodh") -> "Doodh"; itemName.contains("atta") || itemName.contains("flour") -> "Atta"; itemName.contains("sugar") || itemName.contains("cheeni") -> "Cheeni"; itemName.contains("salt") || itemName.contains("namak") -> "Namak"; itemName.contains("tea") || itemName.contains("chai") -> "Chai"; itemName.contains("ghee") -> "Ghee"; itemName.contains("bread") -> "Bread"; itemName.contains("egg") -> "Ande"; else -> itemName.replaceFirstChar { it.uppercase() } }
                "$itemDisplay mein $optionText hai."
            }
            lang.startsWith("te") -> "$itemName — $optionText. ${ButlerPersonalityEngine.askSelection("te", currentMood)}"
            lang.startsWith("ta") -> "$itemName — $optionText. Endha brand vendum?"
            lang.startsWith("kn") -> "$itemName — $optionText. Yaava brand beku?"
            lang.startsWith("ml") -> "$itemName — $optionText. Etha brand veno?"
            else                  -> "$itemName — $optionText. Which brand do you want?"
        }
    }

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        val lang = LanguageManager.getLanguage()
        val genericTerms = setOf("rice","dal","daal","oil","tel","atta","flour","milk","doodh","tea","chai","चावल","दाल","तेल","आटा","दूध","चाय")
        if (qty == 0 && itemName.lowercase().trim() in genericTerms) {
            pendingProductCategory = itemName; currentState = AssistantState.ASKING_PRODUCT_TYPE
            speak(ButlerPersonalityEngine.askProductType(itemName, sessionUserName, lang)) { startListening() }; return
        }
        speakFillerThen {
            lifecycleScope.launch {
                val searchResult = productRepo.searchWithFallback(itemName, userLocation)
                val recs = searchResult.products
                if (recs.isNotEmpty()) {
                    if (searchResult.isCategoryMismatch) {
                        runOnUiThread {
                            pendingAddRecs = recs; pendingAddIndex = 0; pendingAddQty = if (qty > 0) qty else 1; pendingAddItemName = itemName
                            currentState = AssistantState.CONFIRMING_ADD_PRODUCT
                            speakKeepingRecsVisible(ButlerPersonalityEngine.productCategoryMismatch(searchResult.requestedKeyword, recs.first().readableName, recs.first().priceRs.toInt(), lang, sessionUserName), ButlerPersonalityEngine.toneForSubstitute()) { startListening() }
                        }
                        return@launch
                    }
                    runOnUiThread { setUiState(ButlerUiState.ShowingRecommendations(itemName, recs)) }
                    val readout = buildProductVoiceReadout(recs, itemName, lang)
                    val nameRetryPrompt = when { lang.startsWith("hi") -> "Brand ka naam btaiye."; lang.startsWith("te") -> "Brand peyru cheppandi."; lang.startsWith("ta") -> "Brand peyar sollungal."; lang.startsWith("kn") -> "Brand hesaru heli."; lang.startsWith("ml") -> "Brand peru parayo."; lang.startsWith("pa") -> "Brand naam dasao."; lang.startsWith("gu") -> "Brand naam bolo."; else -> "Say the brand name." }
                    speakKeepingRecsVisible(readout) {
                        startListeningForSelection(
                            onNumber = { num -> handleRecSelectionByIndex(num - 1, recs, qty, itemName) },
                            onOther  = { spoken ->
                                val sLow = spoken.lowercase().trim().replace(Regex("[।,.!?]"), "")
                                val wantsFirst = sLow.contains("haan") || sLow.contains("yes") || sLow.contains("theek") || sLow.contains("ok") || sLow.contains("wahi") || sLow.contains("sasta") || sLow.contains("pehla")
                                if (wantsFirst) { handleRecSelectionByIndex(0, recs, qty, itemName); return@startListeningForSelection }
                                val normalized = normalizeBrandSpelling(sLow)
                                val pick = matchBrandFromSpoken(sLow, normalized, recs)
                                if (pick != null) { handleRecSelectionByIndex(recs.indexOf(pick), recs, qty, itemName) }
                                else {
                                    speakKeepingRecsVisible(when { lang.startsWith("hi") -> "Kaunsa ${extractCategoryWord(itemName).ifBlank { itemName }} du?"; lang.startsWith("te") -> "Edi kavali?"; lang.startsWith("ta") -> "Edu vendum?"; lang.startsWith("kn") -> "Yavudu beku?"; lang.startsWith("ml") -> "Eth veno?"; else -> "Which one?" }) {
                                        startListeningForSelection(
                                            onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                                            onOther  = { s2 -> val p2 = matchBrandFromSpoken(s2.lowercase(), normalizeBrandSpelling(s2.lowercase()), recs); handleRecSelectionByIndex(if (p2 != null) recs.indexOf(p2) else 0, recs, qty, itemName) },
                                            retryPrompt = nameRetryPrompt
                                        )
                                    }
                                }
                            },
                            retryPrompt = nameRetryPrompt
                        )
                    }
                } else runOnUiThread { speak(ButlerPersonalityEngine.productNotFound(itemName, lang)) { startListening() } }
            }
        }
    }

    private fun handleRecSelectionByIndex(index: Int, recs: List<ProductRecommendation>, qty: Int, itemName: String) {
        val pick = recs.getOrNull(index); val lang = LanguageManager.getLanguage()
        if (pick != null) {
            pendingAddRecs = recs; pendingAddIndex = index; pendingAddQty = if (qty > 0) qty else 1; pendingAddItemName = itemName
            currentState = AssistantState.CONFIRMING_ADD_PRODUCT
            val msg = if (cart.isEmpty()) ButlerPersonalityEngine.confirmAddProduct(sessionUserName, pick.productName, pick.priceRs.toInt(), lang) else ButlerPersonalityEngine.confirmAddNext(sessionUserName, pick.productName, pick.priceRs.toInt(), lang)
            speakKeepingRecsVisible(msg, ButlerPersonalityEngine.toneForConfirmAdd()) { startListening() }
        } else {
            val askMsg = when { lang.startsWith("hi") -> "Brand ka naam btaiye."; lang.startsWith("te") -> "Brand peyru cheppandi."; else -> "Say the brand name." }
            speakKeepingRecsVisible(askMsg) { startListeningForSelection(onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) }, onOther = { _ -> speak(ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())) { currentState = AssistantState.LISTENING; startListening() } }) }
        }
    }

    private fun handleConfirmingAddProduct(cleaned: String) {
        val lang = LanguageManager.getLanguage(); val pick = pendingAddRecs.getOrNull(pendingAddIndex)
        if (pick == null) { currentState = AssistantState.LISTENING; startListening(); return }
        when {
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("le lo") || cleaned.contains("add karo") || cleaned.contains("daal do") || cleaned.contains("rakh do") -> {
                cart.add(CartItem(ApiClient.Product(id = pick.productId, name = pick.productName, price = pick.priceRs, unit = pick.unit), pendingAddQty))
                sessionLastProduct = pick.productName; sessionLastQty = pendingAddQty; currentState = AssistantState.ASKING_MORE
                val categoryWord = extractCategoryWord(pendingAddItemName)
                val hasCategory  = categoryWord.isNotBlank() && pick.productName.lowercase().contains(categoryWord.lowercase())
                val displayName  = if (categoryWord.isNotBlank() && !hasCategory) "${pick.productName.split(" ").take(2).joinToString(" ")} $categoryWord" else pick.productName.split(" ").take(2).joinToString(" ")
                showCartAndSpeak(ButlerPersonalityEngine.itemAdded(sessionUserName, displayName, lang, currentMood, cart.size), ButlerPersonalityEngine.toneForItemAdded()) { startListening() }
            }
            MultilingualMatcher.isNo(cleaned) || cleaned.contains("nahi") || cleaned.contains("koi aur") -> {
                speakKeepingRecsVisible(if (lang.startsWith("hi")) "Kaunsa chahiye? Brand ka naam btaiye." else "Which brand? Say the name.") {
                    startListeningForSelection(
                        onNumber = { n -> handleRecSelectionByIndex(n - 1, pendingAddRecs, pendingAddQty, pendingAddItemName) },
                        onOther  = { spoken ->
                            val words = spoken.lowercase().split(" ").filter { it.length >= 3 }
                            val match = pendingAddRecs.firstOrNull { rec -> words.any { sw -> rec.productName.lowercase().contains(sw) } }
                            handleRecSelectionByIndex(if (match != null) pendingAddRecs.indexOf(match) else 0, pendingAddRecs, pendingAddQty, pendingAddItemName)
                        }
                    )
                }
            }
            else -> speak(if (lang.startsWith("hi")) "Haan ya nahi?" else "Yes or no?") { startListening() }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PLACE ORDER
    // ══════════════════════════════════════════════════════════════════════

    private fun placeOrder() {
        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId() ?: run { speak(ButlerPersonalityEngine.sessionExpired(LanguageManager.getLanguage())) { startWakeWordListening() }; return@launch }
                val orderResult = apiClient.createOrder(cart, userId)
                val shortId     = if (orderResult.public_id.isNotBlank()) orderResult.public_id else orderResult.id.takeLast(6).uppercase()
                val firstName   = FamilyProfileManager.activeProfile?.displayName?.split(" ")?.first() ?: UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
                val lang        = LanguageManager.getLanguage()
                Log.d("Butler", "Order placed: ${orderResult.id}")
                AnalyticsManager.logOrderPlaced(orderResult.id, orderResult.total_amount, cart.size, lang)
                lastOrderId = orderResult.id; lastPublicId = shortId; lastOrderTotal = orderResult.total_amount
                FamilyProfileManager.activeProfile?.let { member -> FamilyProfileManager.updateLastOrder(this@MainActivity, member.id, cart.take(2).joinToString(", ") { it.product.name }) }
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
                runOnUiThread { currentState = AssistantState.CONFIRMING; showCartAndSpeak(ButlerPersonalityEngine.orderError(LanguageManager.getLanguage())) { startListening() } }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CART HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun showCartAndSpeak(text: String, tone: EmotionTone = EmotionTone.WARM, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            val ttsText   = com.demo.butler_voice_app.utils.ButlerSpeechFormatter.formatWithEmotion(finalText, lang, tone)
            Log.d("Butler", "Original: $text"); Log.d("Butler", "TTS ($lang): $ttsText")
            val cartItems = cart.map { CartDisplayItem(it.product.name, it.quantity, it.product.price) }
            val total     = cart.sumOf { it.product.price * it.quantity }
            runOnUiThread {
                if (currentState == AssistantState.CONFIRMING || currentState == AssistantState.ASKING_MORE) setUiState(ButlerUiState.CartReview(cartItems, total, text))
                else setUiState(ButlerUiState.Speaking(ttsText, cart = cartItems))
                ttsManager.speak(text = ttsText, language = lang, tone = tone, onDone = { onDone?.invoke() })
            }
        }
    }

    private fun readCartAndConfirm() {
        if (cart.isEmpty()) { speak(ButlerPersonalityEngine.cartEmpty(LanguageManager.getLanguage())) { currentState = AssistantState.LISTENING; startListening() }; return }
        currentState = AssistantState.CONFIRMING
        showCartAndSpeak(buildShortConfirm(LanguageManager.getLanguage()), ButlerPersonalityEngine.toneForConfirmOrder()) { startListening() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun addMultipleItemsToCart(items: List<com.demo.butler_voice_app.ai.ParsedItem>) {
        val found = mutableListOf<String>(); val notFound = mutableListOf<String>()
        for (item in items) {
            val sn = IndianLanguageProcessor.normalizeProduct(item.name).ifBlank { item.name }
            val p  = apiClient.searchProduct(sn)
            if (p != null) { cart.add(CartItem(p, item.quantity)); found.add("${item.quantity} ${p.name}") } else { notFound.add(item.name); AnalyticsManager.logItemNotFound(item.name) }
        }
        val lang = LanguageManager.getLanguage()
        runOnUiThread {
            currentState = AssistantState.ASKING_MORE
            showCartAndSpeak(if (notFound.isEmpty()) "${found.joinToString(", ")} add ho gaya. ${ButlerPhraseBank.get("ask_more", lang)}" else "${found.joinToString(", ")} add ho gaya. ${notFound.joinToString(", ")} nahi mila. ${ButlerPhraseBank.get("ask_more", lang)}") { startListening() }
        }
    }

    private fun isNoMoreIntent(s: String): Boolean {
        if (MultilingualMatcher.isDone(s)) return true
        if (IndianLanguageProcessor.DONE_PHRASES.any { s.contains(it, ignoreCase = true) }) return true
        return listOf("done","finish","checkout","place order","बस","bas","order karo","order kar do","kar do","bas karo","order kar doon","order de do","confirm").any { s.contains(it, ignoreCase = true) }
    }

    private fun extractTimeSlotFromSpeech(text: String): String? {
        val lower = text.lowercase()
        return when { lower.contains("aaj") || lower.contains("today") -> "today"; lower.contains("kal") || lower.contains("tomorrow") -> "tomorrow"; lower.contains("morning") || lower.contains("subah") -> "morning"; lower.contains("evening") || lower.contains("shaam") -> "evening"; lower.contains("abhi") || lower.contains("now") -> "now"; else -> null }
    }

    private fun keywordFallback(s: String): String? {
        val r = IndianLanguageProcessor.normalizeProduct(s); if (r != s) return r
        return when {
            s.contains("rice") || s.contains("चावल") -> "rice"; s.contains("oil") || s.contains("तेल") -> "oil"
            s.contains("sugar") || s.contains("चीनी") -> "sugar"; s.contains("dal") || s.contains("दाल") -> "dal"
            s.contains("salt") || s.contains("नमक") -> "salt"; s.contains("milk") || s.contains("दूध") -> "milk"
            s.contains("wheat") || s.contains("atta") -> "wheat flour"; s.contains("tea") || s.contains("चाय") -> "tea"
            s.contains("ghee") || s.contains("घी") -> "ghee"; s.contains("bread") || s.contains("रोटी") -> "bread"
            s.contains("eggs") || s.contains("egg") -> "eggs"; else -> null
        }
    }

    private fun instantGroceryDetect(s: String, lang: String): Pair<String, Int>? {
        val qty = extractQuantity(s)
        val detectedQty = when { Regex("\\d+").containsMatchIn(s) -> qty; listOf("do","दो","రెండు","two").any { s.contains(it) } -> 2; listOf("teen","तीन","మూడు","three").any { s.contains(it) } -> 3; else -> 0 }
        return when {
            s.contains("rice") || s.contains("chawal") || s.contains("चावल") || s.contains("basmati") -> Pair("rice", detectedQty)
            s.contains("dal") || s.contains("daal") || s.contains("दाल") || s.contains("lentil")      -> Pair("dal", detectedQty)
            s.contains("oil") || s.contains("tel") || s.contains("तेल") || s.contains("sunflower")    -> Pair("oil", detectedQty)
            s.contains("milk") || s.contains("doodh") || s.contains("दूध")                            -> Pair("milk", detectedQty)
            s.contains("atta") || s.contains("aata") || s.contains("आटा") || s.contains("flour")      -> Pair("wheat flour", detectedQty)
            s.contains("sugar") || s.contains("cheeni") || s.contains("चीनी") || s.contains("shakkar") -> Pair("sugar", detectedQty)
            s.contains("salt") || s.contains("namak") || s.contains("नमक")                            -> Pair("salt", detectedQty)
            s.contains("tea") || s.contains("chai") || s.contains("चाय")                              -> Pair("tea", detectedQty)
            s.contains("ghee") || s.contains("घी")                                                    -> Pair("ghee", detectedQty)
            s.contains("bread") || s.contains("roti") || s.contains("pav")                            -> Pair("bread", detectedQty)
            s.contains("egg") || s.contains("anda") || s.contains("अंडा")                             -> Pair("eggs", detectedQty)
            s.contains("butter") || s.contains("makhan") || s.contains("मक्खन")                       -> Pair("butter", detectedQty)
            s.contains("curd") || s.contains("dahi") || s.contains("दही")                             -> Pair("curd", detectedQty)
            s.contains("coffee") || s.contains("kaafi")                                                -> Pair("coffee", detectedQty)
            s.contains("soap") || s.contains("sabun")                                                  -> Pair("soap", detectedQty)
            s.contains("biscuit")                                                                       -> Pair("biscuit", detectedQty)
            s.contains("daawat") || s.contains("dawat")                                                -> Pair("daawat rice", detectedQty)
            s.contains("fortune")                                                                       -> Pair("fortune oil", detectedQty)
            s.contains("aashirvaad") || s.contains("aashirvad")                                        -> Pair("aashirvaad atta", detectedQty)
            s.contains("amul")                                                                          -> Pair("amul butter", detectedQty)
            s.contains("tata salt") || s.contains("tata namak")                                        -> Pair("tata salt", detectedQty)
            else -> null
        }
    }

    private suspend fun doLogin(email: String, password: String) {
        UserSessionManager.login(email, password).fold(
            onSuccess = { profile ->
                FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                runOnUiThread {
                    currentState = AssistantState.LISTENING; sessionUserName = profile.full_name?.split(" ")?.first() ?: "there"
                    AnalyticsManager.logUserAuth("login", LanguageManager.getLanguage())
                    val lastProduct = UserSessionManager.purchaseHistory.firstOrNull()?.product_name?.takeIf { it.isNotBlank() && it != "null" }
                    val lang        = LanguageManager.getLanguage()
                    val shortLast   = lastProduct?.lowercase()?.split(" ")?.take(2)?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    speak(ButlerPersonalityEngine.greeting(sessionUserName, lang, shortLast, currentMood), EmotionTone.WARM) { startListening() }
                }
            },
            onFailure = { runOnUiThread { speak("Login nahi hua. Email aur password check karein.") { startWakeWordListening() } } }
        )
    }

    private fun readOrderHistory() {
        val h = UserSessionManager.purchaseHistory; val lang = LanguageManager.getLanguage()
        if (h.isEmpty()) speak(ButlerPhraseBank.get("no_orders", lang)) { startListening() }
        else speak("Recent orders: ${h.take(3).joinToString(", ") { it.product_name ?: "unknown" }}. Dobara order karein?") { startListening() }
    }

    private fun repeatLastOrder() {
        lifecycleScope.launch {
            val userId = UserSessionManager.currentUserId() ?: return@launch
            val orders = apiClient.getOrderHistory(userId)
            if (orders.isEmpty()) { runOnUiThread { speak("Koi purana order nahi.") { startListening() } }; return@launch }
            val items = apiClient.getOrderItems(orders.first().id)
            if (items.isEmpty()) { runOnUiThread { speak("Last order load nahi hua.") { startListening() } }; return@launch }
            for (item in items) apiClient.searchProduct(item.product_name)?.let { cart.add(CartItem(it, item.quantity)) }
            runOnUiThread { currentState = AssistantState.CONFIRMING; showCartAndSpeak("Last order ready hai. ${buildShortConfirm(LanguageManager.getLanguage())}") { startListening() } }
        }
    }

    private fun normalizeBrandSpelling(spoken: String): String {
        val brandMap = mapOf(
            "दावत" to "daawat", "दावात" to "daawat", "dawat" to "daawat", "daavat" to "daawat",
            "इंडिया गेट" to "india gate", "यूनिटी" to "unity", "आर्चीज़" to "archies", "आर्चीस" to "archies",
            "फॉर्च्यून" to "fortune", "फार्च्यून" to "fortune", "फर्च्यून" to "fortune",
            "पतंजलि" to "patanjali", "सफोला" to "saffola", "सफ्फोला" to "saffola",
            "आशीर्वाद" to "aashirvaad", "आशिर्वाद" to "aashirvaad",
            "अन्नपूर्णा" to "annapurna", "शक्ति भोग" to "shakti bhog",
            "तूर" to "toor", "अरहर" to "arhar", "मूंग" to "moong", "मसूर" to "masoor", "उड़द" to "urad",
            "टाटा" to "tata", "अमूल" to "amul", "नंदिनी" to "nandini", "मदर डेयरी" to "mother dairy",
            "ताज महल" to "taj mahal", "रेड लेबल" to "red label", "वाघ बकरी" to "wagh bakri", "गिरनार" to "girnar",
            "అముల్" to "amul", "దావత్" to "daawat", "ఇండియా గేట్" to "india gate", "ఫార్చ్యూన్" to "fortune",
            "பதஞ்சலி" to "patanjali", "ಅಮೂಲ್" to "amul", "ಪತಂಜಲಿ" to "patanjali"
        )
        var result = spoken
        brandMap.entries.sortedByDescending { it.key.length }.forEach { (script, latin) -> if (result.contains(script)) result = result.replace(script, latin) }
        return result
    }

    private fun matchBrandFromSpoken(sLow: String, normalized: String, recs: List<ProductRecommendation>): ProductRecommendation? {
        fun wordScore(q: String, rec: ProductRecommendation) = q.split(" ").filter { it.length >= 3 }.count { sw -> rec.productName.lowercase().split(" ").any { rw -> rw.startsWith(sw) || sw.startsWith(rw) } }
        fun containsScore(q: String, rec: ProductRecommendation) = q.split(" ").filter { it.length >= 3 }.count { sw -> rec.productName.lowercase().contains(sw) }
        return recs.maxByOrNull { maxOf(wordScore(sLow, it), wordScore(normalized, it)) }?.takeIf { maxOf(wordScore(sLow, it), wordScore(normalized, it)) > 0 }
            ?: recs.maxByOrNull { maxOf(containsScore(sLow, it), containsScore(normalized, it)) }?.takeIf { maxOf(containsScore(sLow, it), containsScore(normalized, it)) > 0 }
            ?: recs.firstOrNull { it.productName.lowercase().contains(sLow) || it.productName.lowercase().contains(normalized) }
    }

    private fun extractCategoryWord(itemName: String): String {
        val s = itemName.lowercase()
        return when {
            s.contains("rice") || s.contains("chawal") || s.contains("basmati") -> "rice"
            s.contains("dal") || s.contains("daal") || s.contains("lentil")     -> "dal"
            s.contains("oil") || s.contains("tel")                               -> "oil"
            s.contains("milk") || s.contains("doodh")                            -> "milk"
            s.contains("atta") || s.contains("flour") || s.contains("aata")     -> "atta"
            s.contains("sugar") || s.contains("cheeni") || s.contains("shakkar") -> "sugar"
            s.contains("salt") || s.contains("namak")                            -> "salt"
            s.contains("tea") || s.contains("chai")                              -> "tea"
            s.contains("ghee")  -> "ghee"; s.contains("bread") || s.contains("pav") -> "bread"
            s.contains("egg")   -> "eggs"; s.contains("coffee") -> "coffee"
            s.contains("butter") || s.contains("makhan") -> "butter"
            s.contains("curd") || s.contains("dahi")     -> "curd"
            s.contains("soap") || s.contains("sabun")    -> "soap"
            s.contains("biscuit") -> "biscuit"
            else -> ""
        }
    }

    private fun extractQuantity(text: String): Int {
        val w = mapOf("one" to 1,"two" to 2,"three" to 3,"four" to 4,"five" to 5,"six" to 6,"seven" to 7,"eight" to 8,"nine" to 9,"ten" to 10,
            "एक" to 1,"दो" to 2,"तीन" to 3,"चार" to 4,"पाँच" to 5,"ek" to 1,"do" to 2,"teen" to 3,"char" to 4,"paanch" to 5,
            "ఒకటి" to 1,"రెండు" to 2,"మూడు" to 3,"నాలుగు" to 4,"ఐదు" to 5)
        return Regex("\\d+").find(text)?.value?.toIntOrNull() ?: w.entries.firstOrNull { text.lowercase().contains(it.key) }?.value ?: 1
    }

    private fun setUiState(s: ButlerUiState) = runOnUiThread { uiState.value = s }

    private fun detectEmotionTone(text: String): EmotionTone {
        val lower = text.lowercase()
        val emergencyWords = listOf("emergency","ambulance","heart","chest","breathe","unconscious","accident","bleeding","faint","stroke","attack","dying","help me","not breathing","collapsed","दर्द","दिल","सांस","बेहोश","खून","बचाओ","madad","bachao","నొప్పి","గుండె","శ్వాస")
        if (emergencyWords.any { lower.contains(it) }) return EmotionTone.EMERGENCY
        val distressWords = listOf("worried","scared","tension","anxious","upset","crying","stolen","problem","pareshan","परेशान","डर","చింత","ఆందోళన")
        if (distressWords.any { lower.contains(it) }) return EmotionTone.EMPATHETIC
        return EmotionTone.NORMAL
    }

    private fun speak(text: String, tone: EmotionTone = EmotionTone.NORMAL, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            val ttsText   = com.demo.butler_voice_app.utils.ButlerSpeechFormatter.formatWithEmotion(finalText, lang, tone)
            Log.d("Butler", "Original: $text"); Log.d("Butler", "TTS ($lang): $ttsText")
            runOnUiThread { setUiState(ButlerUiState.Speaking(ttsText)); ttsManager.speak(text = ttsText, language = lang, tone = tone, onDone = { onDone?.invoke() }) }
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)?) = speak(text, EmotionTone.NORMAL, onDone)

    private fun speakKeepingRecsVisible(text: String, tone: EmotionTone = EmotionTone.NORMAL, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            val ttsText   = com.demo.butler_voice_app.utils.ButlerSpeechFormatter.formatWithEmotion(finalText, lang, tone)
            runOnUiThread { ttsManager.speak(text = ttsText, language = lang, tone = tone, onDone = { onDone?.invoke() }) }
        }
    }

    private fun speakKeepingRecsVisible(text: String, onDone: (() -> Unit)?) = speakKeepingRecsVisible(text, EmotionTone.NORMAL, onDone)

    private fun speakKeepingQRVisible(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            runOnUiThread { ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() }) }
        }
    }

    private suspend fun translateToEnglish(text: String): String = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject().apply { put("model","gpt-4o-mini"); put("messages", org.json.JSONArray().apply { put(org.json.JSONObject().apply { put("role","user"); put("content","Translate to English. Return ONLY the translated text: $text") }) }); put("max_tokens",50); put("temperature",0.1) }.toString().toRequestBody("application/json".toMediaType())
            val response = okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization","Bearer ${BuildConfig.OPENAI_API_KEY}").addHeader("Content-Type","application/json").post(body).build()).execute()
            org.json.JSONObject(response.body?.string() ?: return@withContext text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) { text }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SERVICE ROUTING HELPER
    // ══════════════════════════════════════════════════════════════════════

    private fun mapCategoryToSector(category: String?): ServiceSector? {
        if (category.isNullOrBlank()) return null
        val cat = category.lowercase().trim()
        return when {
            cat.contains("electric")                                                            -> ServiceSector.ELECTRICIAN
            cat.contains("plumb")                                                               -> ServiceSector.PLUMBER
            cat.contains("clean") || cat.contains("maid") || cat.contains("cook")              -> ServiceSector.CLEANING
            cat.contains("carpenter")                                                           -> ServiceSector.CARPENTER
            cat.contains("paint")                                                               -> ServiceSector.PAINTER
            cat.contains("doctor") || cat.contains("physician") || cat.contains("specialist")  -> ServiceSector.DOCTOR
            cat.contains("medicine") || cat.contains("pharmacy") || cat.contains("chemist")    -> ServiceSector.MEDICINE
            cat.contains("mechanic") || cat.contains("vehicle") || cat.contains("car")         -> ServiceSector.CAR_SERVICE
            cat.contains("pest")                                                                -> ServiceSector.PEST_CONTROL
            cat.contains("ac") || cat.contains("air condition") || cat.contains("appliance")   -> ServiceSector.AC_REPAIR
            cat.contains("laundry")                                                             -> ServiceSector.CLEANING
            cat.contains("beauty") || cat.contains("salon") || cat.contains("parlour") || cat.contains("haircut") -> ServiceSector.SALON
            cat.contains("spa") || cat.contains("massage")                                     -> ServiceSector.SPA
            cat.contains("security") || cat.contains("guard")                                  -> ServiceSector.SECURITY
            cat.contains("nurse") || cat.contains("home care")                                 -> ServiceSector.HOME_NURSING
            cat.contains("ambulance") || cat.contains("emergency")                             -> ServiceSector.AMBULANCE
            cat.contains("food") || cat.contains("delivery")                                   -> ServiceSector.FOOD
            cat.contains("taxi") || cat.contains("cab") || cat.contains("auto")                -> ServiceSector.TAXI
            cat.contains("tutor") || cat.contains("teacher") || cat.contains("coaching")       -> ServiceSector.TUTOR
            else                                                                                -> null
        }
    }
}