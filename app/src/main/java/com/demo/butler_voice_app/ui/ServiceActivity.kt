package com.demo.butler_voice_app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.demo.butler_voice_app.BuildConfig
import com.demo.butler_voice_app.EmotionTone
import com.demo.butler_voice_app.TTSManager
import com.demo.butler_voice_app.api.SupabaseClient
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.services.*
import com.demo.butler_voice_app.voice.SarvamSTTManager
import com.demo.butler_voice_app.ai.LanguageManager
import com.demo.butler_voice_app.ai.TranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ServiceActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SECTOR   = "extra_sector"
        const val EXTRA_QUERY    = "extra_query"
        const val EXTRA_IS_RX    = "extra_is_prescription"
        const val EXTRA_IS_EMERG = "extra_is_emergency"
    }

    private var screenState        by mutableStateOf<ServiceScreenState>(ServiceScreenState.SectorHome)
    private var currentSector      : ServiceSector? = null
    private var providers          : List<ServiceProvider> = emptyList()
    private var selectedProvider   : ServiceProvider? = null
    private var extractedMedicines : List<String> = emptyList()
    private var prescriptionStatus by mutableStateOf(PrescriptionUploadStatus.WAITING)
    private var userLat            : Double? = null
    private var userLng            : Double? = null

    private var medicinesFromOcr = false

    private val bookingHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15,  TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private lateinit var ttsManager : TTSManager
    private lateinit var sarvamSTT  : SarvamSTTManager

    // ── Image / document picker ───────────────────────────────────────────

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { processPrescritionImage(it) } }

    private val cameraPicker = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            processPrescriptionBytes(stream.toByteArray())
        } else {
            speak("Camera was closed. Please try again or say upload.") {
                startListeningForPrescriptionUpload()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userLat = intent.getDoubleExtra("user_lat", 0.0).takeIf { it != 0.0 }
        userLng = intent.getDoubleExtra("user_lng", 0.0).takeIf { it != 0.0 }

        sarvamSTT = SarvamSTTManager(this, BuildConfig.SARVAM_API_KEY)
        ttsManager = TTSManager(this, BuildConfig.ELEVENLABS_API_KEY, "RwXLkVKnRloV1UPh3Ccx")
        ttsManager.init {
            val sector = intent.getStringExtra(EXTRA_SECTOR)?.let {
                if (it.isBlank()) null
                else runCatching { ServiceSector.valueOf(it.uppercase()) }.getOrNull()
            }
            val query   = intent.getStringExtra(EXTRA_QUERY) ?: ""
            val isRx    = intent.getBooleanExtra(EXTRA_IS_RX, false)
            val isEmerg = intent.getBooleanExtra(EXTRA_IS_EMERG, false)

            when {
                isEmerg  -> handleEmergency()
                isRx     -> startPrescriptionFlow()
                sector == ServiceSector.MEDICINE -> startPrescriptionFlow()
                sector != null -> startSectorFlow(sector, query)
                else -> { screenState = ServiceScreenState.SectorHome; speakServicePrompt() }
            }
        }

        setContent {
            when (val s = screenState) {
                is ServiceScreenState.SectorHome ->
                    ServicesSectorScreen(
                        onSectorSelected = { sector -> startSectorFlow(sector, "") },
                        onVoiceSearch    = { speakServicePrompt() }
                    )
                is ServiceScreenState.ProviderList ->
                    ServiceProvidersScreen(
                        sector             = s.sector,
                        providers          = s.providers,
                        query              = s.query,
                        isLoading          = s.isLoading,
                        onProviderSelected = { num -> onProviderChosen(num) },
                        onVoiceFilter      = { startListeningForFilter() }
                    )
                is ServiceScreenState.Prescription ->
                    PrescriptionUploadScreen(
                        status             = prescriptionStatus,
                        extractedMedicines = extractedMedicines,
                        providers          = providers,
                        onUploadTap        = { showUploadChoice() },
                        onProviderSelected = { num -> onProviderChosen(num) }
                    )
                is ServiceScreenState.BookingConfirm ->
                    ServiceBookingScreen(
                        provider  = s.provider,
                        onConfirm = { confirmBooking(s.provider) },
                        onCancel  = { goBack() }
                    )
                is ServiceScreenState.BookingDone ->
                    ServiceBookedScreen(
                        provider  = s.provider,
                        bookingId = s.bookingId,
                        eta       = s.eta
                    )
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); ttsManager.shutdown(); sarvamSTT.stop() }

    // ══════════════════════════════════════════════════════════════════════
    // PRESCRIPTION FLOW
    // ══════════════════════════════════════════════════════════════════════

    private fun startPrescriptionFlow() {
        currentSector      = ServiceSector.MEDICINE
        screenState        = ServiceScreenState.Prescription
        prescriptionStatus = PrescriptionUploadStatus.WAITING
        extractedMedicines = emptyList()
        providers          = emptyList()
        medicinesFromOcr   = false

        speak(
            "I will help you find a pharmacy for your medicines. " +
                    "Please say camera to take a photo of your prescription, " +
                    "or say upload to choose an image from your gallery."
        ) {
            startListeningForPrescriptionUpload()
        }
    }

    private fun startListeningForPrescriptionUpload() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    val lower = transcript.lowercase()
                    when {
                        lower.contains("camera") || lower.contains("photo") ||
                                lower.contains("click")  || lower.contains("take")  ||
                                lower.contains("कैमरा")  || lower.contains("फोटो") -> {
                            speak("Opening camera. Please take a clear photo of your prescription.") {
                                cameraPicker.launch(null)
                            }
                        }
                        lower.contains("upload") || lower.contains("gallery") ||
                                lower.contains("file")   || lower.contains("scan")    ||
                                lower.contains("galeri") -> {
                            speak("Opening gallery. Please select your prescription image or PDF.") {
                                imagePicker.launch("image/*")
                            }
                        }
                        lower.contains("skip") || lower.contains("no prescription") ||
                                lower.contains("manually") -> {
                            speak("OK, please say the medicine names one by one.") {
                                startListeningForManualMedicines()
                            }
                        }
                        else -> {
                            speak("Say camera to take a photo, or upload to choose from gallery.") {
                                startListeningForPrescriptionUpload()
                            }
                        }
                    }
                }
            },
            onError = {
                runOnUiThread {
                    speak("Say camera to take a photo, or upload to choose a file.") {
                        startListeningForPrescriptionUpload()
                    }
                }
            }
        )
    }

    private fun showUploadChoice() {
        speak("Say camera to take a photo, or upload to choose from gallery.") {
            startListeningForPrescriptionUpload()
        }
    }

    // ── Manual medicine entry ─────────────────────────────────────────────

    private val manualMedicines = mutableListOf<String>()

    private fun startListeningForManualMedicines() {
        medicinesFromOcr = false
        manualMedicines.clear()
        speak("Please say the first medicine name.") {
            listenForNextMedicine()
        }
    }

    private fun listenForNextMedicine() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    val lower = transcript.lowercase().trim()
                    when {
                        lower.contains("done") || lower.contains("that's all") ||
                                lower.contains("search") || lower.contains("bas") || lower.contains("बस") -> {
                            if (manualMedicines.isEmpty()) {
                                speak("I didn't get any medicines. Please try again.") {
                                    startListeningForManualMedicines()
                                }
                            } else {
                                extractedMedicines = manualMedicines.toList()
                                searchPharmaciesForMedicines()
                            }
                        }
                        transcript.isNotBlank() && transcript.length > 2 -> {
                            manualMedicines.add(transcript.trim())
                            val count = manualMedicines.size
                            speak("Got $count medicine${if (count > 1) "s" else ""}. Say another medicine name, or say done to search.") {
                                listenForNextMedicine()
                            }
                        }
                        else -> {
                            speak("Please say the medicine name clearly.") {
                                listenForNextMedicine()
                            }
                        }
                    }
                }
            },
            onError = { runOnUiThread { listenForNextMedicine() } }
        )
    }

    // ── Process prescription image from gallery ───────────────────────────

    private fun processPrescritionImage(uri: Uri) {
        prescriptionStatus = PrescriptionUploadStatus.PROCESSING
        speak("Reading your prescription. Please wait a moment.") {}

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes       = inputStream?.readBytes() ?: byteArrayOf()
                inputStream?.close()

                if (bytes.isEmpty()) {
                    runOnUiThread {
                        speak("Could not read the file. Please try again.") {
                            prescriptionStatus = PrescriptionUploadStatus.WAITING
                            startListeningForPrescriptionUpload()
                        }
                    }
                    return@launch
                }

                processPrescriptionBytesAsync(bytes)
            } catch (e: Exception) {
                Log.e("ServiceActivity", "File read error", e)
                runOnUiThread {
                    speak("Something went wrong. Please try again.") {
                        prescriptionStatus = PrescriptionUploadStatus.WAITING
                        startListeningForPrescriptionUpload()
                    }
                }
            }
        }
    }

    private fun processPrescriptionBytes(bytes: ByteArray) {
        prescriptionStatus = PrescriptionUploadStatus.PROCESSING
        speak("Reading your prescription now…") {}
        lifecycleScope.launch(Dispatchers.IO) { processPrescriptionBytesAsync(bytes) }
    }

    // ── CORE: GPT-4o Vision extracts medicines ────────────────────────────

    private suspend fun processPrescriptionBytesAsync(bytes: ByteArray) {
        try {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Log.d("ServiceActivity", "Sending ${bytes.size} bytes to GPT-4o Vision")

            val mimeType = when {
                bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
                else -> "image/jpeg"
            }

            val prompt = """You are a medical prescription reader for Indian pharmacies.
Extract ALL medicine names with dosage from this prescription image.
The handwriting may be in English, Hindi, or other Indian languages.
Include brand names, generic names, and dosages if visible.
Return ONLY a JSON array of strings, nothing else.
Example: ["Paracetamol 500mg", "Azithromycin 250mg", "Pantoprazole 40mg"]
If the image is unclear or not a prescription, return: []
Do NOT include instructions or frequency — only names and doses."""

            val requestBody = org.json.JSONObject().apply {
                put("model", "gpt-4o")
                put("max_tokens", 800)
                put("messages", org.json.JSONArray().put(
                    org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("type", "text"); put("text", prompt)
                            })
                            put(org.json.JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", org.json.JSONObject().apply {
                                    put("url", "data:$mimeType;base64,$base64")
                                    put("detail", "high")
                                })
                            })
                        })
                    }
                ))
            }.toString()

            val response = withContext(Dispatchers.IO) {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60,  TimeUnit.SECONDS)
                    .build()
                    .newCall(
                        okhttp3.Request.Builder()
                            .url("https://api.openai.com/v1/chat/completions")
                            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                            .addHeader("Content-Type",  "application/json")
                            .post(requestBody.toRequestBody("application/json".toMediaType()))
                            .build()
                    ).execute()
            }

            val responseBody = response.body?.string() ?: ""
            Log.d("ServiceActivity", "GPT-4o response: ${responseBody.take(500)}")

            val content = org.json.JSONObject(responseBody)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
                .replace("```json", "").replace("```", "").trim()

            Log.d("ServiceActivity", "Extracted medicines content: $content")

            val arr       = org.json.JSONArray(content)
            val medicines = (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }

            Log.d("ServiceActivity", "Extracted ${medicines.size} medicines: $medicines")

            extractedMedicines = medicines

            if (medicines.isEmpty()) {
                runOnUiThread {
                    speak(
                        "I couldn't clearly read the medicines from the prescription. " +
                                "The handwriting may be unclear. " +
                                "Please say the medicine names one by one, or try a clearer photo."
                    ) {
                        prescriptionStatus = PrescriptionUploadStatus.WAITING
                        startListeningForManualMedicines()
                    }
                }
                return
            }

            medicinesFromOcr   = true
            prescriptionStatus = PrescriptionUploadStatus.MEDICINES_FOUND

            val medicineList = medicines.take(4).joinToString(", ")
            val moreText     = if (medicines.size > 4) " and ${medicines.size - 4} more" else ""

            runOnUiThread {
                speak(
                    "I found ${medicines.size} medicine${if (medicines.size > 1) "s" else ""}" +
                            " in your prescription: $medicineList$moreText. " +
                            "Now searching for nearby pharmacies that have these medicines."
                ) {
                    searchPharmaciesForMedicines()
                }
            }

        } catch (e: Exception) {
            Log.e("ServiceActivity", "GPT-4o Vision error", e)
            runOnUiThread {
                speak("I had trouble reading the prescription. Please say the medicine names one by one.") {
                    prescriptionStatus = PrescriptionUploadStatus.WAITING
                    startListeningForManualMedicines()
                }
            }
        }
    }

    // ── Search pharmacies for extracted/entered medicines ─────────────────

    private fun searchPharmaciesForMedicines() {
        lifecycleScope.launch {
            try {
                val medicines = extractedMedicines
                providers = ServiceManager.findMedicalShopsForPrescription(
                    medicines,
                    userLat ?: 22.72,
                    userLng ?: 75.86
                )
                screenState = ServiceScreenState.Prescription

                val lang   = LanguageManager.getLanguage()
                // ── HUMANISED: use ServiceVoiceEngine for pharmacy list prompts ──
                // prescriptionFound = "Prescription se mila: X. 3 pharmacy paas mein. Kaun si?"
                // prescriptionManual = "Aapne bataya: X. 3 pharmacy available. Naam bolein."
                val prompt = if (medicinesFromOcr) {
                    ServiceVoiceEngine.prescriptionFound(medicines, providers.size, lang)
                } else {
                    ServiceVoiceEngine.prescriptionManual(medicines, providers.size, lang)
                }

                runOnUiThread {
                    speak(prompt) { startListeningForSelection() }
                }
            } catch (e: Exception) {
                Log.e("ServiceActivity", "Pharmacy search error", e)
                runOnUiThread {
                    speak("I had trouble finding nearby pharmacies. Please try again.") {
                        startListeningForPrescriptionUpload()
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SECTOR FLOWS
    // ══════════════════════════════════════════════════════════════════════

    private fun startSectorFlow(sector: ServiceSector, query: String) {
        currentSector = sector
        screenState   = ServiceScreenState.ProviderList(sector, emptyList(), query, isLoading = true)

        // ── HUMANISED: "Theek hai, electrician dhundh raha hoon aapke paas."
        speak(ServiceVoiceEngine.sectorDetected(sector, LanguageManager.getLanguage())) {}

        lifecycleScope.launch {
            providers   = ServiceManager.searchProviders(ServiceIntent(sector, query), userLat, userLng, ServiceFilter())
            screenState = ServiceScreenState.ProviderList(sector, providers, query, isLoading = false)

            // ── HUMANISED: "3 electrician mile hain. 1: ABC — 15 min mein. Kaun sa?"
            val response = ServiceVoiceEngine.providerList(sector, providers, LanguageManager.getLanguage())
            runOnUiThread { speak(response) { startListeningForSelection() } }
        }
    }

    private fun handleEmergency() {
        currentSector = ServiceSector.AMBULANCE
        val lang = LanguageManager.getLanguage()
        // Emergency tone — Butler must NEVER sound cheerful in a medical emergency
        val emergencyText = when {
            lang.startsWith("hi") ->
                "घबराइए मत! अभी ambulance बुला रहा हूँ। 108 dial कर रहा हूँ।"
            lang.startsWith("te") ->
                "భయపడకండి! వెంటనే ambulance పిలుస్తున్నాను."
            else ->
                "Don't panic. Calling ambulance right now. Dialling 108."
        }
        speak(emergencyText, EmotionTone.EMERGENCY) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")))
            lifecycleScope.launch {
                providers   = ServiceManager.searchProviders(
                    ServiceIntent(ServiceSector.AMBULANCE, "emergency", isEmergency = true), userLat, userLng
                )
                screenState = ServiceScreenState.ProviderList(
                    ServiceSector.AMBULANCE, providers, "emergency", false
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VOICE LISTENING
    // ══════════════════════════════════════════════════════════════════════

    private fun speakServicePrompt() {
        // ── HUMANISED: "Batao, kya chahiye? Electrician, plumber, doctor, taxi?"
        speak(ServiceVoiceEngine.categoryPrompt(LanguageManager.getLanguage())) {
            startListeningForSector()
        }
    }

    private fun startListeningForSector() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    // Logic functions stay in ServiceVoiceHandler — don't change these
                    if (ServiceVoiceHandler.isEmergency(transcript))           { handleEmergency();        return@runOnUiThread }
                    if (ServiceVoiceHandler.isPrescriptionRequest(transcript))  { startPrescriptionFlow(); return@runOnUiThread }
                    val intent = ServiceManager.detectServiceIntent(transcript)
                    if (intent.sector == ServiceSector.MEDICINE)               { startPrescriptionFlow(); return@runOnUiThread }
                    if (intent.sector != null) startSectorFlow(intent.sector, transcript)
                    else speak("Sorry, which service do you need?") { startListeningForSector() }
                }
            },
            onError = { runOnUiThread { speak("Something went wrong.") { startListeningForSector() } } }
        )
    }

    private fun startListeningForSelection() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    val lower = transcript.lowercase().trim()

                    // 1. Filter commands (nearest/cheapest/etc) — logic stays in ServiceVoiceHandler
                    val filter = ServiceVoiceHandler.parseFilterCommand(transcript)
                    if (filter != null) { applyFilter(filter); return@runOnUiThread }

                    // 2. Back / cancel
                    if (lower.contains("back") || lower.contains("cancel") ||
                        lower.contains("वापस") || lower.contains("రద్దు") ||
                        lower.contains("wapas") || lower.contains("nahi")) {
                        goBack(); return@runOnUiThread
                    }

                    // 3. Match by PROVIDER NAME — user says "ABC" or "pehla wala"
                    //    Split provider name into words, check if any word (length > 2)
                    //    appears in the transcript. First match wins.
                    val nameMatch = providers.indexOfFirst { provider ->
                        val nameWords = provider.name.lowercase().split(" ", "-")
                        nameWords.any { word ->
                            word.length > 2 && lower.contains(word)
                        }
                    }
                    if (nameMatch >= 0) { onProviderChosen(nameMatch + 1); return@runOnUiThread }

                    // 4. Fallback: still accept "pehla"/"first"/"one" ordinal words
                    //    so users who instinctively say a number still work
                    val num = ServiceVoiceHandler.parseNumberSelection(transcript)
                    if (num != null) { onProviderChosen(num); return@runOnUiThread }

                    // 5. No match — ask again with name prompt
                    speak(ServiceVoiceEngine.selectionRetry(LanguageManager.getLanguage())) {
                        startListeningForSelection()
                    }
                }
            },
            onError = { runOnUiThread { startListeningForSelection() } }
        )
    }

    private fun startListeningForFilter() {
        speak("Filter by: rating, nearest, cheapest, or fastest.") {
            sarvamSTT.startListening(
                onResult = { transcript ->
                    runOnUiThread {
                        val filter = ServiceVoiceHandler.parseFilterCommand(transcript)
                        if (filter != null) applyFilter(filter)
                        else speak("Showing top-rated providers.") { startListeningForSelection() }
                    }
                },
                onError = { runOnUiThread { startListeningForSelection() } }
            )
        }
    }

    private fun startListeningForBookingConfirm(provider: ServiceProvider) {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    val lower = transcript.lowercase()
                    when {
                        lower.contains("yes") || lower.contains("confirm") || lower.contains("book") ||
                                lower.contains("हां") || lower.contains("हाँ") || lower.contains("అవును") -> confirmBooking(provider)
                        lower.contains("no")  || lower.contains("cancel") || lower.contains("back") ||
                                lower.contains("नहीं") || lower.contains("వద్దు")                       -> goBack()
                        // ── HUMANISED: "Haan bolein toh book, nahi bolein toh wapas."
                        else -> speak(ServiceVoiceEngine.confirmYesNoPrompt(LanguageManager.getLanguage())) { startListeningForBookingConfirm(provider) }
                    }
                }
            },
            onError = { runOnUiThread { startListeningForBookingConfirm(provider) } }
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // BOOKING
    // ══════════════════════════════════════════════════════════════════════

    private fun onProviderChosen(number: Int) {
        val provider = providers.getOrNull(number - 1) ?: return
        selectedProvider = provider
        // ── HUMANISED: "ABC — book karoon? 15 min mein aa jayenge."
        speak(ServiceVoiceEngine.providerSelected(provider, LanguageManager.getLanguage())) {
            screenState = ServiceScreenState.BookingConfirm(provider)
            startListeningForBookingConfirm(provider)
        }
    }

    private fun confirmBooking(provider: ServiceProvider) {
        val bookingId = "BUT-${java.util.UUID.randomUUID().toString().takeLast(6).uppercase()}"
        val lang      = LanguageManager.getLanguage()

        if (screenState is ServiceScreenState.Prescription)
            prescriptionStatus = PrescriptionUploadStatus.SENT_TO_PHARMACY

        screenState = ServiceScreenState.BookingDone(provider, bookingId, provider.eta)

        // ── HUMANISED: "Ho gaya! ABC aa rahe hain. Booking ID: BUT-XYZ. 15 min mein."
        speak(ServiceVoiceEngine.bookingConfirmed(provider, bookingId, lang)) {
            Handler(Looper.getMainLooper()).postDelayed({ finishWithResult(bookingId) }, 8000)
        }

        lifecycleScope.launch {
            try {
                val userId = UserSessionManager.currentUserId() ?: run {
                    Log.e("ServiceActivity", "No userId — booking NOT saved")
                    return@launch
                }
                saveBookingToSupabase(
                    bookingId     = bookingId,
                    userId        = userId,
                    providerName  = provider.name,
                    providerPhone = null,
                    sector        = provider.sector.name,
                    etaMinutes    = provider.eta.filter { it.isDigit() }.toIntOrNull() ?: 15,
                    medicines     = extractedMedicines.takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                Log.e("ServiceActivity", "Booking save error", e)
            }
        }
    }

    private fun applyFilter(filter: ServiceFilter) {
        val sector = currentSector ?: return
        lifecycleScope.launch {
            providers   = ServiceManager.searchProviders(ServiceIntent(sector, ""), userLat, userLng, filter)
            screenState = ServiceScreenState.ProviderList(sector, providers, "", false)
            val sortName = when (filter.sortBy) {
                ServiceSort.RATING    -> "top rated"
                ServiceSort.DISTANCE  -> "nearest"
                ServiceSort.PRICE_LOW -> "cheapest"
                ServiceSort.FASTEST   -> "fastest"
                else -> "best"
            }
            // ── HUMANISED: "Top rated wale 3 option hain. Kaun sa?"
            speak(ServiceVoiceEngine.filterResult(sortName, providers.size, LanguageManager.getLanguage())) { startListeningForSelection() }
        }
    }

    private fun goBack() {
        when (screenState) {
            is ServiceScreenState.BookingConfirm -> {
                screenState = ServiceScreenState.ProviderList(currentSector!!, providers, "", false)
                // ── HUMANISED: "Theek hai, pehle wali list pe wapas."
                speak(ServiceVoiceEngine.goBack(LanguageManager.getLanguage())) { startListeningForSelection() }
            }
            is ServiceScreenState.ProviderList, is ServiceScreenState.Prescription -> {
                screenState = ServiceScreenState.SectorHome
                speakServicePrompt()
            }
            else -> finish()
        }
    }

    private fun finishWithResult(bookingId: String) {
        setResult(Activity.RESULT_OK, Intent().apply { putExtra("booking_id", bookingId) })
        finish()
    }

    // ══════════════════════════════════════════════════════════════════════
    // SUPABASE — save booking
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun saveBookingToSupabase(
        bookingId:     String,
        userId:        String,
        providerName:  String,
        providerPhone: String?,
        sector:        String,
        etaMinutes:    Int,
        medicines:     List<String>? = null
    ) = withContext(Dispatchers.IO) {

        val supabaseUrl = SupabaseClient.SUPABASE_URL
        val supabaseKey = SupabaseClient.SUPABASE_KEY
        val token       = UserSessionManager.getToken() ?: supabaseKey

        val body = org.json.JSONObject().apply {
            put("booking_id",    bookingId)
            put("user_id",       userId)
            put("provider_name", providerName)
            put("sector",        sector)
            put("status",        "booked")
            put("eta_minutes",   etaMinutes)
            if (providerPhone != null)      put("provider_phone", providerPhone)
            if (!medicines.isNullOrEmpty()) put("medicines",      medicines.joinToString(", "))
        }.toString()

        Log.d("ServiceActivity", "Saving booking $bookingId for user $userId to Supabase")

        val request = okhttp3.Request.Builder()
            .url("$supabaseUrl/rest/v1/bookings")
            .header("apikey",        supabaseKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type",  "application/json")
            .header("Prefer",        "return=minimal")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = bookingHttpClient.newCall(request).execute()
        Log.d("ServiceActivity", "Booking saved: HTTP ${response.code} — $bookingId")

        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "no body"
            Log.e("ServiceActivity", "Booking save failed HTTP ${response.code}: $err")
            throw Exception("Booking HTTP ${response.code}: $err")
        }
    }

    // ── Speak helper ──────────────────────────────────────────────────────

    private fun speak(
        text: String,
        tone: EmotionTone = EmotionTone.NORMAL,
        onDone: (() -> Unit)? = null
    ) {
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("ServiceActivity", "Speaking [$tone]: $finalText")
            runOnUiThread {
                ttsManager.speak(text = finalText, language = lang, tone = tone, onDone = { onDone?.invoke() })
            }
        }
    }

    // Backward-compat overload
    private fun speak(text: String, onDone: (() -> Unit)?) = speak(text, EmotionTone.NORMAL, onDone)
}

sealed class ServiceScreenState {
    object SectorHome : ServiceScreenState()
    data class ProviderList(
        val sector: ServiceSector, val providers: List<ServiceProvider>,
        val query: String, val isLoading: Boolean
    ) : ServiceScreenState()
    object Prescription : ServiceScreenState()
    data class BookingConfirm(val provider: ServiceProvider) : ServiceScreenState()
    data class BookingDone(val provider: ServiceProvider, val bookingId: String, val eta: String) : ServiceScreenState()
}