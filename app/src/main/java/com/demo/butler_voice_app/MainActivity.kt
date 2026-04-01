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
import com.demo.butler_voice_app.utils.HumanFillerManager
import com.demo.butler_voice_app.voice.SarvamSTTManager
import com.demo.butler_voice_app.workers.ProactiveButlerWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.demo.butler_voice_app.api.PriceComparisonEngine
import com.demo.butler_voice_app.api.StorePrice
import com.demo.butler_voice_app.api.PriceComparison
import java.util.concurrent.TimeUnit

enum class AssistantState {
    IDLE, CHECKING_AUTH,
    ASKING_IS_NEW_USER, ASKING_NAME, ASKING_EMAIL, ASKING_PHONE, ASKING_PASSWORD,
    LISTENING, ASKING_QUANTITY, ASKING_MORE, CONFIRMING, REORDER_CONFIRM, EDITING_CART,
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
    private var sttRetryCount = 0

    private var pendingReorderSuggestions: List<ReorderSuggestion> = emptyList()
    private var lastOrderId    = ""
    private var lastPublicId   = ""
    private var lastOrderTotal = 0.0

    private var sessionLastProduct: String? = null
    private var sessionLastQty: Int = 0
    private var lastBookingId: String? = null

    private var pendingProactiveData: ProactiveData? = null
    private var currentMood: UserMood = UserMood.CALM

    // ── Service sub-type session ──────────────────────────────────────────
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
    private var totalEmptyRetries = 0

    // ── BUG 2 FIX ─────────────────────────────────────────────────────────
    // STT retry race condition: when a blank transcript fires, startListening()
    // was called immediately without stopping the old STT session. The old
    // session's next callback then fired ~54ms later as a SECOND blank
    // transcript, immediately hitting retry count 2 and speaking "सुना नहीं"
    // before the user had a chance to say anything.
    //
    // Fix: every call to startListening() increments sttListenId. Each
    // SarvamSTT callback captures the id at the time it was created.
    // Any callback whose id no longer matches the current id is stale and
    // is silently discarded. sarvamSTT.stop() before the new session
    // ensures only one recording is active at a time.
    // ─────────────────────────────────────────────────────────────────────
    @Volatile private var sttListenId = 0

    private fun toSpeakableAmount(amount: Double): String = "${amount.toInt()} rupees"

    // ══════════════════════════════════════════════════════════════════════
    // HUMAN HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun speakFillerThen(action: () -> Unit) {
        val lang   = LanguageManager.getLanguage()
        val filler = HumanFillerManager.getThinkingFiller(lang)
        ttsManager.speak(text = filler, language = lang, onDone = null)
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
        val items  = cart.joinToString(", ") { "${it.quantity} ${it.product.name}" }
        val total  = toSpeakableAmount(cart.sumOf { it.product.price * it.quantity })
        val phrase = ButlerPhraseBank.get("confirm_order", lang)
        return "$items. $total. $phrase"
    }

    // ══════════════════════════════════════════════════════════════════════
    // CENTRAL ROUTER
    // ══════════════════════════════════════════════════════════════════════

    private fun routeTranscript(text: String, lower: String): Boolean {
        val lang = LanguageManager.getLanguage()

        if (ServiceVoiceHandler.isEmergency(lower)) {
            speak(ServiceVoiceHandler.buildEmergencyPrompt(lang)) { launchServiceFlow(text) }
            return true
        }

        if (ServiceVoiceHandler.isPrescriptionRequest(lower)) {
            speak("I will help you order from your prescription. Opening camera now.") { launchServiceFlow(text) }
            return true
        }

        if (currentState == AssistantState.LISTENING || currentState == AssistantState.REORDER_CONFIRM) {
            if (ServiceVoiceHandler.isServiceRequest(lower)) {
                val intent = ServiceManager.detectServiceIntent(text)
                val sector = intent.sector

                if (sector != null && ServiceVoiceHandler.hasSectorSubTypes(sector)) {
                    serviceSubTypeSession = ServiceSubTypeSession(sector, text)
                    currentState = AssistantState.IN_SERVICE_SUBTYPE_FLOW
                    val prompt = ServiceVoiceHandler.buildSubTypePrompt(sector, lang)
                    speak(prompt) { startListening() }
                } else {
                    val sectorName = sector?.let {
                        com.demo.butler_voice_app.ai.HindiSectorNames.get(it.name, lang)
                    } ?: "service"
                    speak("Finding $sectorName providers near you.") {
                        // ── BUG 3 FIX ──────────────────────────────────────────
                        // Previously: launchServiceFlow(text) with no overrideSector.
                        // Inside launchServiceFlow, ServiceManager.detectServiceIntent()
                        // was called AGAIN on the same transcript. On that second
                        // call it returned sector=null (e.g. for "मुझे इलेक्ट्रिशियन
                        // चाहिए।"), causing EXTRA_SECTOR="" to be passed to
                        // ServiceActivity. ServiceActivity then fell through to the
                        // else branch and asked "which service do you need?" — even
                        // though the user had already said "electrician".
                        //
                        // Fix: pass the already-detected sector as overrideSector so
                        // the second detection is skipped entirely.
                        // ─────────────────────────────────────────────────────
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
                val confirmMsg = ButlerPhraseBank.get("service_booking_confirm", lang) +
                        " ID $bookingId. ${ButlerPhraseBank.get("ask_item", lang)}"
                speak(confirmMsg) {
                    currentState = AssistantState.LISTENING
                    startListening()
                }
            } else {
                currentState = AssistantState.LISTENING
                speak(ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())) { startListening() }
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

        // ── BUG 8 FIX ─────────────────────────────────────────────────────
        // Previously: apiClient.searchProduct("rice") was called to warm the
        // cache. searchProduct() is a product-search function, not a pre-fetch
        // function. Calling it at startup meant 457KB of JSON was fetched and
        // 3000 items were deserialized DURING startup frame rendering, causing
        // 76+ skipped frames / 2240ms Davey alerts.
        //
        // Fix: use the dedicated prefetchProducts() which runs entirely on
        // Dispatchers.IO and populates the same shared cache without touching
        // the main thread. The coroutine runs fire-and-forget so it doesn't
        // delay ttsManager.init() or the wake word engine starting up.
        // ─────────────────────────────────────────────────────────────────
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
        AIParser.resetDebounce()
        apiClient.clearProductCache()
        setUiState(ButlerUiState.Idle)

        // ── BUG 2 FIX: invalidate any in-flight startListening callbacks ─
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
            // ── BUG 7 FIX ─────────────────────────────────────────────────
            // Previously: tryRestoreSession() → if 401, session cleared →
            // user forced to log in on every cold start after token expiry.
            //
            // Fix: when tryRestoreSession() fails AND a refresh token exists
            // in SessionStore, silently call the Supabase token refresh
            // endpoint. On success, the new access token is written to
            // SessionStore and tryRestoreSession() is retried. Only if the
            // refresh also fails do we fall through to the AuthActivity.
            //
            // Note: UserSessionManager.tryRestoreSession() should save the
            // refresh_token alongside the access_token when first logging in.
            // SessionStore.saveSession(accessToken, uid, name, refreshToken)
            // ─────────────────────────────────────────────────────────────
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

    // ── BUG 7 FIX: silent token refresh via Supabase /auth/v1/token ──────

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
        if (history.isNotEmpty()) {
            lifecycleScope.launch {
                val userId = FamilyProfileManager.activeProfile?.userId
                    ?: UserSessionManager.currentUserId() ?: ""
                val suggestions = SmartReorderManager.getSuggestions(userId)
                val smartMsg    = SmartReorderManager.buildReorderGreeting(suggestions, name)
                runOnUiThread {
                    if (smartMsg != null && suggestions.isNotEmpty()) {
                        pendingReorderSuggestions = suggestions
                        currentState = AssistantState.REORDER_CONFIRM
                        speak(smartMsg) { startListening() }
                    } else {
                        currentState = AssistantState.LISTENING
                        val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() }
                        val greeting = when {
                            MoodAdapter.shouldSuggestReorder(currentMood) && lastProduct != null ->
                                MoodAdapter.adaptGreeting(currentMood, name, lang)
                            lastProduct != null ->
                                "welcome back $name! last time $lastProduct. kya chahiye aaj?"
                            else ->
                                IndianLanguageProcessor.getWelcomeGreeting(lang, name)
                        }
                        speak(greeting) { startListening() }
                    }
                }
            }
        } else {
            currentState = AssistantState.LISTENING
            speak(IndianLanguageProcessor.getWelcomeGreeting(lang, name)) { startListening() }
        }
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
    // STT — main voice loop
    // ══════════════════════════════════════════════════════════════════════

    private fun startListening() {
        // ── BUG 2 FIX: stop any existing recording before starting a new one.
        // sarvamSTT.stop() cancels the previous session's callback chain so
        // its onResult can never fire after this point.
        sarvamSTT.stop()

        // ── BUG 2 FIX: capture a session id. The lambda below captures myId.
        // If startListening() is called again before this callback fires,
        // sttListenId will have been incremented and myId != sttListenId,
        // so the stale callback returns immediately without any side effects.
        val myId = ++sttListenId

        setUiState(ButlerUiState.Listening)
        Log.d("Butler", "Starting STT...")

        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    // ── BUG 2 FIX: stale callback guard ──────────────────
                    if (myId != sttListenId) {
                        Log.d("Butler", "STT callback discarded (stale id $myId, current $sttListenId)")
                        return@runOnUiThread
                    }

                    val transcript = text.trim()
                    Log.d("Butler", "Transcript: $transcript")

                    // ── MOOD DETECTION ────────────────────────────────────
                    val pcm      = sarvamSTT.lastPcmBuffer
                    val duration = sarvamSTT.lastRecordingDurationMs
                    if (pcm.isNotEmpty()) {
                        currentMood = MoodDetector.analyse(pcm, duration)
                        Log.d("Butler", "Mood: $currentMood")
                    }
                    if (transcript.isBlank()) MoodDetector.recordRetry()

                    // ── LANGUAGE DETECTION & SWITCHING ───────────────────
                    if (transcript.isNotBlank() && transcript.length > 3) {
                        val scriptLang = MultilingualMatcher.detectScript(transcript)
                        val detected   = LanguageDetector.detect(transcript)
                        val rawLang    = if (scriptLang != "en") scriptLang else detected
                        LanguageManager.setLanguage(rawLang)

                        val sarvamLangCode = "$rawLang-IN"
                        val langSwitched   = SessionLanguageManager.onDetection(sarvamLangCode)
                        if (langSwitched) {
                            Log.d("Butler", "🔄 Language switched to ${SessionLanguageManager.lockedLanguage}")
                            val newBase = SessionLanguageManager.ttsLanguage
                            LanguageManager.setLanguage(newBase)
                            Log.d("Butler", "LanguageManager synced to $newBase")
                        }
                    }

                    // ── EMPTY TRANSCRIPT ─────────────────────────────────
                    if (transcript.isBlank()) {
                        SessionLanguageManager.onBlankTranscript()

                        sttRetryCount++
                        totalEmptyRetries++
                        val lang = LanguageManager.getLanguage()

                        if (totalEmptyRetries >= 5) {
                            totalEmptyRetries = 0
                            sttRetryCount = 0
                            MoodDetector.reset()
                            val giveUpMsg = when {
                                lang.startsWith("hi") -> "ठीक है। जब बोलना हो तब hey butler बोलना।"
                                lang.startsWith("te") -> "సరే. మళ్ళీ అవసరమైతే hey butler చెప్పండి."
                                else                  -> "no problem. say hey butler when you're ready."
                            }
                            speak(giveUpMsg) { startWakeWordListening() }
                            return@runOnUiThread
                        }

                        // ── BUG 2 FIX: sttRetryCount < 2 now safely retries
                        // because sttListenId prevents the old callback from
                        // triggering a phantom second retry.
                        if (sttRetryCount < 2) {
                            startListening()   // safe — stale callbacks are guarded above
                        } else {
                            sttRetryCount = 0
                            val retryMsg = HumanFillerManager.getEmptyRetry(lang, totalEmptyRetries - 1)
                            speak(retryMsg) { startListening() }
                        }
                        return@runOnUiThread
                    }

                    totalEmptyRetries = 0
                    sttRetryCount = 0
                    setUiState(ButlerUiState.Thinking(transcript))
                    handleCommand(transcript)
                }
            },
            onError = { runOnUiThread { speak("Something went wrong") { startWakeWordListening() } } }
        )
    }

    private fun startListeningForSelection(
        onNumber: (Int) -> Unit,
        onOther: (String) -> Unit,
        retryPrompt: String = "Please say 1, 2, or 3."
    ) {
        // ── BUG 2 FIX: invalidate any pending startListening() callback when
        // switching to the selection sub-flow, so stale main-loop callbacks
        // don't fire over the top of selection callbacks.
        sarvamSTT.stop()
        sttListenId++

        setUiState(ButlerUiState.Listening)
        sarvamSTT.startListening(
            onResult = { text ->
                runOnUiThread {
                    val transcript = text.trim()
                    if (transcript.isBlank()) {
                        speak("I didn't hear you. Please say 1, 2, or 3 clearly.") {
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
            onError = { runOnUiThread { speak("Didn't catch that. Say 1, 2, or 3.") { startListeningForSelection(onNumber, onOther, retryPrompt) } } }
        )
    }

    private fun detectNumberFromSpeech(spoken: String): Int {
        val s = spoken.lowercase().trim().replace(Regex("[,।.!?]"), "").trim()
        val digit = Regex("\\b([123])\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()
        if (digit != null) return digit
        return when {
            s.contains("one")   || s.contains("wan")    || s.contains("won")    || s.contains("first")  ||
                    s.contains("pehla") || s.contains("ek")     || s.contains("एक")     || s.contains("ఒకటి")   ||
                    s.contains("ஒன்று") || s.contains("option one") || s.contains("number one") -> 1
            s.contains("two")   || s.contains("too")    || s.contains("second") || s.contains("doosra") ||
                    s.contains("do")    || s.contains("दो")     || s.contains("రెండు")  || s.contains("இரண்டு") ||
                    s.contains("option two") || s.contains("number two") -> 2
            s.contains("three") || s.contains("tree")   || s.contains("third")  || s.contains("teesra") ||
                    s.contains("teen")  || s.contains("तीन")    || s.contains("మూడు")   || s.contains("மூன்று") ||
                    s.contains("option three") || s.contains("number three") -> 3
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
        // ── BUG 2 FIX: invalidate STT callbacks before handing control to
        // ServiceActivity so no ghost callbacks fire when we return.
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
                                    val bookingPrompt = ServiceVoiceHandler.buildBookingConfirmPrompt(session.sector, matched, timeSlot, lang)
                                    speak(bookingPrompt) {
                                        sarvamSTT.startListening(
                                            onResult = { confirmText ->
                                                runOnUiThread {
                                                    val c = confirmText.trim().lowercase()
                                                    if (MultilingualMatcher.isYes(c) ||
                                                        c.contains("haan") || c.contains("ha") ||
                                                        c.contains("ok") || c.contains("pakka")) {
                                                        val enhancedQuery  = "${session.originalTranscript} ${matched.displayEn} $timeSlot"
                                                        val sectorDisplay  = com.demo.butler_voice_app.ai.HindiSectorNames.get(session.sector.name, lang)
                                                        speak("Finding $sectorDisplay near you.") {
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
                        speak(ServiceVoiceHandler.buildSubTypeRetryPrompt(session.sector, lang)) { startListening() }
                    } else {
                        serviceSubTypeSession = null
                        val sectorName = com.demo.butler_voice_app.ai.HindiSectorNames.get(session.sector.name, lang)
                        speak("Finding $sectorName near you.") { launchServiceFlow(session.originalTranscript, session.sector, null) }
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
                when {
                    MultilingualMatcher.isYes(cleaned) || IndianLanguageProcessor.detectIntent(cleaned) == "confirm" -> {
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
                    MultilingualMatcher.isNo(cleaned) -> {
                        pendingReorderSuggestions = emptyList(); currentState = AssistantState.LISTENING
                        speak("no problem! ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") { startListening() }
                    }
                    else -> {
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
                    val suggestion = HumanFillerManager.getRelatedSuggestion(product.name, lang)
                    val addedMsg   = ButlerPhraseBank.get("added_item", lang)
                    val askMore    = ButlerPhraseBank.get("ask_more", lang)
                    showCartAndSpeak(
                        if (suggestion != null && cart.size == 1) "$addedMsg $qty ${product.name}. $suggestion bhi chahiye? $askMore"
                        else "$addedMsg $qty ${product.name}. $askMore"
                    ) { startListening() }
                } else {
                    speak("Let us try again. ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") {
                        currentState = AssistantState.LISTENING; startListening()
                    }
                }
            }

            AssistantState.ASKING_MORE    -> handleAskingMore(cleaned, text)
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
        speak("total ${toSpeakableAmount(pendingOrderTotal)}. ${ButlerPhraseBank.get("ask_payment", LanguageManager.getLanguage())}") { startListening() }
    }

    private fun handlePaymentModeChoice(cleaned: String) {
        when {
            cleaned.contains("card") || cleaned.contains("debit") || cleaned.contains("credit") ||
                    cleaned.contains("saved") || cleaned.contains("कार्ड") || cleaned.contains("కార్డ్") -> {
                val card   = PaymentManager.getSavedCard(this)
                val amount = toSpeakableAmount(pendingOrderTotal)
                currentState = AssistantState.WAITING_CARD_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("card", pendingOrderTotal))
                speak(if (card != null) "${card.network} card ${card.last4} pe $amount charge hoga. payment complete karein."
                else "card details enter karein aur $amount pay karein.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("card") }, 2000)
                }
            }
            cleaned.contains("upi") || cleaned.contains("google pay") || cleaned.contains("phonepe") ||
                    cleaned.contains("paytm") || cleaned.contains("bhim") || cleaned.contains("यूपीआई") -> {
                val amount = toSpeakableAmount(pendingOrderTotal)
                currentState = AssistantState.WAITING_UPI_PAYMENT
                setUiState(ButlerUiState.WaitingPaymentConfirm("upi", pendingOrderTotal))
                speak("UPI app mein $amount pay karein butler at upi pe.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("upi") }, 3000)
                }
            }
            cleaned.contains("qr") || cleaned.contains("scan") || cleaned.contains("क्यूआर") -> {
                val amount = toSpeakableAmount(pendingOrderTotal)
                currentState = AssistantState.WAITING_QR_PAYMENT
                setUiState(ButlerUiState.ShowQRCode(pendingOrderTotal, pendingOrderSummary))
                speakKeepingQRVisible("screen pe QR code hai. kisi bhi UPI app se $amount pay karein.") {
                    Handler(Looper.getMainLooper()).postDelayed({ askIfPaid("qr") }, 20000)
                }
            }
            else -> speak("card, UPI, ya QR bolein.") { startListening() }
        }
    }

    private fun askIfPaid(mode: String) {
        currentState = when (mode) {
            "card" -> AssistantState.CONFIRMING_CARD_PAID
            "upi"  -> AssistantState.CONFIRMING_UPI_PAID
            else   -> AssistantState.CONFIRMING_QR_PAID
        }
        speak(ButlerPhraseBank.get("payment_confirm_ask", LanguageManager.getLanguage())) { startListening() }
    }

    private fun handlePaidOrNotPaid(cleaned: String, mode: String) {
        val paid    = listOf("yes","paid","done","payment done","i paid","completed","transferred","confirm",
            "हाँ","हां","हो गया","పేమెంట్ చేశాను","అవును","ஆம்","ha","kar diya","ho gaya")
        val notPaid = listOf("no","not yet","haven't","wait","not done","failed","नहीं","अभी नहीं","లేదు","ఇంకా","nahi","abhi nahi")
        val lang = LanguageManager.getLanguage()
        val amount = toSpeakableAmount(pendingOrderTotal)
        when {
            paid.any    { cleaned.contains(it) } -> speak(ButlerPhraseBank.get("payment_done", lang)) { placeOrder() }
            notPaid.any { cleaned.contains(it) } -> {
                speak(when (mode) {
                    "card" -> "theek hai! card se $amount pay karein."
                    "upi"  -> "theek hai! UPI pe $amount bhejein."
                    else   -> "theek hai! QR scan karke $amount pay karein."
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
            speak("cart khaali hai. ${ButlerPhraseBank.get("ask_item", lang)}") {
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
                if (cart.isEmpty()) speak("${item.product.name} hata diya. cart khaali. ${ButlerPhraseBank.get("ask_item", lang)}") { currentState = AssistantState.LISTENING; startListening() }
                else { currentState = AssistantState.CONFIRMING; showCartAndSpeak("${item.product.name} hata diya. ${buildShortConfirm(lang)}") { startListening() } }
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

        speakFillerThen {
            lifecycleScope.launch {
                // ── RESPONSE TIME FIX ─────────────────────────────────────
                // Previously: AIParser.parse() + AIOrderParser.parse() = 2 sequential
                // GPT calls = 5+ seconds per voice command.
                //
                // Fix: use ONLY AIParser.parse() result. It already returns items
                // for grocery orders (GoToGrocery routing). AIOrderParser is removed
                // from this path entirely, saving ~3 seconds per interaction.
                // ─────────────────────────────────────────────────────────
                val fullParsed = AIParser.parse(text)
                LanguageManager.setLanguage(fullParsed.language)

                runOnUiThread {
                    when (val routing = fullParsed.routing) {
                        is IntentRouting.GoToService -> {
                            speak("Finding ${routing.category} providers near you.") {
                                launchServiceFlow(text)
                            }
                        }
                        is IntentRouting.GoToGrocery -> {
                            // Items come from AIParser directly — no second GPT call
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
                            // Unknown / AskClarify / ShowMenu — try keyword fallback first
                            val fb = keywordFallback(cleaned)
                            if (fb != null) searchAndAskQuantity(fb)
                            else speak(ButlerPhraseBank.get("ask_item", lang)) { startListening() }
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
            MultilingualMatcher.isYes(cleaned) || cleaned.contains("add") || cleaned.contains("more") ||
                    cleaned.contains("और") || cleaned.contains("aur") -> {
                currentState = AssistantState.LISTENING
                val lastProd = sessionLastProduct
                val prompt   = if (lastProd != null && cart.size == 1) "wahi waala ${lastProd} dobara? ya kuch aur?"
                else ButlerPhraseBank.get("ask_item", lang)
                showCartAndSpeak(prompt) { startListening() }
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
                                else showCartAndSpeak(ButlerPhraseBank.get("ask_more", lang)) { startListening() }
                            }
                            else -> showCartAndSpeak(ButlerPhraseBank.get("ask_more", lang)) { startListening() }
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRODUCT SEARCH
    // ══════════════════════════════════════════════════════════════════════

    private fun searchAndAskQuantity(itemName: String, qty: Int = 0, unit: String? = null) {
        val lang = LanguageManager.getLanguage()
        speakFillerThen {
            lifecycleScope.launch {
                val recs = productRepo.getTopRecommendations(itemName, userLocation)
                if (recs.isNotEmpty()) {
                    runOnUiThread { setUiState(ButlerUiState.ShowingRecommendations(itemName, recs)) }

                    if (MoodAdapter.shouldSkipOptions(currentMood) &&
                        currentState != AssistantState.IN_SERVICE_SUBTYPE_FLOW) {
                        val best = recs.minByOrNull { it.priceRs }!!
                        val ack  = if (currentMood == UserMood.FRUSTRATED) MoodAdapter.getFrustrationAck(lang) else ""
                        val msg  = MoodAdapter.buildRushedProductAnnouncement(best.productName, best.priceRs, best.storeName, lang)
                        runOnUiThread {
                            speakKeepingRecsVisible("$ack $msg".trim()) {
                                startListeningForSelection(
                                    onNumber = { handleRecSelectionByIndex(0, recs, qty, itemName) },
                                    onOther  = { _ -> handleRecSelectionByIndex(0, recs, qty, itemName) }
                                )
                            }
                        }
                        return@launch
                    }

                    val comparison = productRepo.getPriceComparison(itemName, userLocation)
                    val readout = if (comparison != null && comparison.allPrices.size > 1) {
                        PriceComparisonEngine.buildVoiceAnnouncement(comparison, lang)
                    } else {
                        "${recs.size} options mila $itemName ke liye. " +
                                recs.mapIndexed { i, r ->
                                    val shortName = r.productName.split(" ").take(3).joinToString(" ")
                                    "${i + 1}: $shortName, ${toSpeakableAmount(r.priceRs)}"
                                }.joinToString(". ") + ". 1, 2 ya 3 bolein."
                    }

                    speakKeepingRecsVisible(readout) {
                        startListeningForSelection(
                            onNumber = { num -> handleRecSelectionByIndex(num - 1, recs, qty, itemName) },
                            onOther  = { spoken ->
                                val sLow = spoken.lowercase()
                                if (comparison != null && (sLow.contains("haan") || sLow.contains("yes") ||
                                            sLow.contains("sasta") || sLow.contains("theek"))) {
                                    val bestIdx = recs.indexOfFirst { it.storeId == comparison.cheapest.storeId }
                                    handleRecSelectionByIndex(if (bestIdx >= 0) bestIdx else 0, recs, qty, itemName)
                                } else {
                                    val pick = recs.firstOrNull { r ->
                                        val rw = r.productName.lowercase().split(" ")
                                        val sw = spoken.lowercase().split(" ").filter { it.length > 2 }
                                        sw.any { s -> rw.any { w -> w.contains(s) || s.contains(w) } }
                                    }
                                    if (pick != null) handleRecSelectionByIndex(recs.indexOf(pick), recs, qty, itemName)
                                    else speakKeepingRecsVisible("1, 2 ya 3 bolein.") {
                                        startListeningForSelection(
                                            onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                                            onOther  = { _ -> speak(ButlerPhraseBank.get("ask_item", lang)) { currentState = AssistantState.LISTENING; startListening() } }
                                        )
                                    }
                                }
                            },
                            retryPrompt = "1, 2 ya 3 bolein."
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
                                val addedMsg = ButlerPhraseBank.get("added_item", lang)
                                val askMore  = ButlerPhraseBank.get("ask_more", lang)
                                showCartAndSpeak("$addedMsg $qty ${product.name}. $askMore") { startListening() }
                            } else {
                                currentState = AssistantState.ASKING_QUANTITY
                                speak("${product.name}? ${ButlerPhraseBank.get("ask_quantity", lang)}") { startListening() }
                            }
                        } else {
                            speak(ButlerPhraseBank.get("ask_item", lang).let { "$itemName nahi mila. $it" }) { startListening() }
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
            val finalQty = if (qty > 0) qty else 1
            cart.add(CartItem(ApiClient.Product(id = pick.productId, name = pick.productName, price = pick.priceRs, unit = pick.unit), finalQty))
            sessionLastProduct = pick.productName; sessionLastQty = finalQty
            val suggestion = HumanFillerManager.getRelatedSuggestion(pick.productName, lang)
            currentState   = AssistantState.ASKING_MORE
            val addedMsg   = ButlerPhraseBank.get("added_item", lang)
            val askMore    = ButlerPhraseBank.get("ask_more", lang)
            showCartAndSpeak(
                if (suggestion != null && cart.size == 1) "$addedMsg ${pick.productName}. $suggestion bhi chahiye? $askMore"
                else "$addedMsg ${pick.productName}. $askMore"
            ) { startListening() }
        } else {
            speakKeepingRecsVisible("1, 2 ya 3 mein se bolein.") {
                startListeningForSelection(
                    onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                    onOther  = { _ -> speak("1, 2 ya 3 bolein.") {
                        startListeningForSelection(
                            onNumber = { n -> handleRecSelectionByIndex(n - 1, recs, qty, itemName) },
                            onOther  = { _ -> speak(ButlerPhraseBank.get("ask_item", lang)) { currentState = AssistantState.LISTENING; startListening() } }
                        )
                    }}
                )
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
                    speak("session expire ho gayi. hey butler bolein.") { startWakeWordListening() }; return@launch
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
                runOnUiThread {
                    currentState = AssistantState.CONFIRMING
                    showCartAndSpeak(ButlerPhraseBank.get("error_retry", LanguageManager.getLanguage())) { startListening() }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CART HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun showCartAndSpeak(text: String, onDone: (() -> Unit)? = null) {
        sarvamSTT.stop()
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("Butler", "Original: $text")
            Log.d("Butler", "Translated ($lang): $finalText")
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
        if (cart.isEmpty()) {
            speak("cart khaali hai. ${ButlerPhraseBank.get("ask_item", LanguageManager.getLanguage())}") {
                currentState = AssistantState.LISTENING; startListening()
            }
            return
        }
        currentState = AssistantState.CONFIRMING
        speakWithTransition(buildShortConfirm(LanguageManager.getLanguage())) { startListening() }
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
        if (MultilingualMatcher.isDone(s)) return true
        if (IndianLanguageProcessor.DONE_PHRASES.any { s.contains(it, ignoreCase = true) }) return true
        return listOf("no","nope","done","nothing","finish","stop","checkout","place order",
            "बस","हो गया","bas","nahi","kar do").any { s.contains(it) }
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

    private suspend fun doLogin(email: String, password: String) {
        UserSessionManager.login(email, password).fold(
            onSuccess = { profile ->
                FamilyProfileManager.ensureCurrentUserRegistered(this@MainActivity, profile)
                runOnUiThread {
                    currentState = AssistantState.LISTENING
                    val firstName   = profile.full_name?.split(" ")?.first() ?: "there"
                    val history     = UserSessionManager.purchaseHistory
                    AnalyticsManager.logUserAuth("login", LanguageManager.getLanguage())
                    val lastProduct = history.firstOrNull()?.product_name?.takeIf { it.isNotBlank() && it != "null" }
                    val lang        = LanguageManager.getLanguage()
                    val greeting    = if (lastProduct != null)
                        "welcome back $firstName! last time $lastProduct tha. ${ButlerPhraseBank.get("ask_item", lang)}"
                    else IndianLanguageProcessor.getWelcomeGreeting(lang, firstName)
                    speak(greeting) { startListening() }
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
}