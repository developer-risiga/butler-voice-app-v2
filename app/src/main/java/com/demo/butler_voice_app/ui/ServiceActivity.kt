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
import com.demo.butler_voice_app.TTSManager
import com.demo.butler_voice_app.api.SupabaseClient
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.services.*
import com.demo.butler_voice_app.voice.SarvamSTTManager
import com.demo.butler_voice_app.ai.LanguageManager
import com.demo.butler_voice_app.ai.TranslationManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

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

    private lateinit var ttsManager : TTSManager
    private lateinit var sarvamSTT  : SarvamSTTManager

    // ── Image / document picker ───────────────────────────────────────────────
    // Accepts images AND documents (PDF/scanned) for prescription

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { processPrescritionImage(it) } }

    private val cameraPicker = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Convert bitmap to URI
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes  = stream.toByteArray()
            processPrescriptionBytes(bytes)
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
            val sector  = intent.getStringExtra(EXTRA_SECTOR)?.let { runCatching { ServiceSector.valueOf(it) }.getOrNull() }
            val query   = intent.getStringExtra(EXTRA_QUERY) ?: ""
            val isRx    = intent.getBooleanExtra(EXTRA_IS_RX, false)
            val isEmerg = intent.getBooleanExtra(EXTRA_IS_EMERG, false)

            when {
                isEmerg  -> handleEmergency()
                isRx     -> startPrescriptionFlow()
                // ── KEY FIX: MEDICINE sector ALWAYS goes to prescription flow ──
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

    // ══════════════════════════════════════════════════════════════════════════
    // PRESCRIPTION FLOW — Full RAG pipeline
    // 1. Ask user to upload or take photo
    // 2. GPT-4o Vision extracts medicine names from handwritten prescription
    // 3. Search nearby pharmacies that stock those medicines
    // 4. Voice-present options, user picks and books
    // ══════════════════════════════════════════════════════════════════════════

    private fun startPrescriptionFlow() {
        currentSector      = ServiceSector.MEDICINE
        screenState        = ServiceScreenState.Prescription
        prescriptionStatus = PrescriptionUploadStatus.WAITING
        extractedMedicines = emptyList()
        providers          = emptyList()

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

    // Show a UI choice dialog + voice for camera vs gallery
    private fun showUploadChoice() {
        speak("Say camera to take a photo, or upload to choose from gallery.") {
            startListeningForPrescriptionUpload()
        }
    }

    // ── Manual medicine entry (fallback if no prescription image) ─────────────

    private val manualMedicines = mutableListOf<String>()

    private fun startListeningForManualMedicines() {
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

    // ── Process prescription image from gallery ───────────────────────────────

    private fun processPrescritionImage(uri: Uri) {
        prescriptionStatus = PrescriptionUploadStatus.PROCESSING
        speak("Reading your prescription. Please wait a moment.") {}

        lifecycleScope.launch {
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
                Log.e("ServiceActivity", "File read error: ${e.message}")
                runOnUiThread {
                    speak("Something went wrong. Please try again.") {
                        prescriptionStatus = PrescriptionUploadStatus.WAITING
                        startListeningForPrescriptionUpload()
                    }
                }
            }
        }
    }

    // ── Process prescription from camera bitmap ───────────────────────────────

    private fun processPrescriptionBytes(bytes: ByteArray) {
        prescriptionStatus = PrescriptionUploadStatus.PROCESSING
        speak("Reading your prescription now…") {}
        lifecycleScope.launch { processPrescriptionBytesAsync(bytes) }
    }

    // ── CORE RAG FUNCTION — GPT-4o Vision extracts medicines ─────────────────

    private suspend fun processPrescriptionBytesAsync(bytes: ByteArray) {
        try {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            Log.d("ServiceActivity", "Sending ${bytes.size} bytes to GPT-4o Vision")

            // Determine image type
            val mimeType = when {
                bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
                else -> "image/jpeg"
            }

            // ── GPT-4o Vision — extracts medicines from ANY handwriting ───────
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
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(org.json.JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", org.json.JSONObject().apply {
                                    put("url", "data:$mimeType;base64,$base64")
                                    put("detail", "high")   // high detail for handwritten prescriptions
                                })
                            })
                        })
                    }
                ))
            }.toString()

            val response = OkHttpClient().newCall(
                okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()

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

            prescriptionStatus = PrescriptionUploadStatus.MEDICINES_FOUND

            // Speak the medicines found
            val medicineList = medicines.take(4).joinToString(", ")
            val moreText     = if (medicines.size > 4) " and ${medicines.size - 4} more" else ""

            runOnUiThread {
                speak(
                    "I found ${medicines.size} medicine${if (medicines.size > 1) "s" else ""}: " +
                            "$medicineList$moreText. " +
                            "Now searching for nearby pharmacies that have these medicines."
                ) {
                    searchPharmaciesForMedicines()
                }
            }

        } catch (e: Exception) {
            Log.e("ServiceActivity", "GPT-4o Vision error: ${e.message}")
            runOnUiThread {
                speak(
                    "I had trouble reading the prescription. " +
                            "Please say the medicine names one by one."
                ) {
                    prescriptionStatus = PrescriptionUploadStatus.WAITING
                    startListeningForManualMedicines()
                }
            }
        }
    }

    // ── Search pharmacies for extracted medicines ─────────────────────────────

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

                val prompt = ServiceVoiceHandler.buildMedicinesExtractedPrompt(
                    medicines, providers.size, LanguageManager.getLanguage()
                )
                runOnUiThread {
                    speak(prompt) { startListeningForSelection() }
                }
            } catch (e: Exception) {
                Log.e("ServiceActivity", "Pharmacy search error: ${e.message}")
                runOnUiThread {
                    speak("I had trouble finding nearby pharmacies. Please try again.") {
                        startListeningForPrescriptionUpload()
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTOR FLOWS
    // ══════════════════════════════════════════════════════════════════════════

    private fun startSectorFlow(sector: ServiceSector, query: String) {
        currentSector = sector
        screenState = ServiceScreenState.ProviderList(sector, emptyList(), query, isLoading = true)
        speak(ServiceVoiceHandler.buildSectorDetectedPrompt(sector, LanguageManager.getLanguage())) {}
        lifecycleScope.launch {
            providers   = ServiceManager.searchProviders(ServiceIntent(sector, query), userLat, userLng, ServiceFilter())
            screenState = ServiceScreenState.ProviderList(sector, providers, query, isLoading = false)
            val response = ServiceManager.buildServiceVoiceResponse(
                sector, providers,
                UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
            )
            runOnUiThread { speak(response) { startListeningForSelection() } }
        }
    }

    private fun handleEmergency() {
        currentSector = ServiceSector.AMBULANCE
        speak(ServiceVoiceHandler.buildEmergencyPrompt(LanguageManager.getLanguage())) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")))
            lifecycleScope.launch {
                providers   = ServiceManager.searchProviders(ServiceIntent(ServiceSector.AMBULANCE, "emergency", isEmergency = true), userLat, userLng)
                screenState = ServiceScreenState.ProviderList(ServiceSector.AMBULANCE, providers, "emergency", false)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VOICE LISTENING
    // ══════════════════════════════════════════════════════════════════════════

    private fun speakServicePrompt() {
        speak(ServiceVoiceHandler.buildServiceCategoryPrompt(LanguageManager.getLanguage())) { startListeningForSector() }
    }

    private fun startListeningForSector() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    if (ServiceVoiceHandler.isEmergency(transcript))         { handleEmergency();        return@runOnUiThread }
                    if (ServiceVoiceHandler.isPrescriptionRequest(transcript)) { startPrescriptionFlow(); return@runOnUiThread }
                    val intent = ServiceManager.detectServiceIntent(transcript)
                    if (intent.sector == ServiceSector.MEDICINE)              { startPrescriptionFlow(); return@runOnUiThread }
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
                    val filter = ServiceVoiceHandler.parseFilterCommand(transcript)
                    if (filter != null) { applyFilter(filter); return@runOnUiThread }
                    val num = ServiceVoiceHandler.parseNumberSelection(transcript)
                    if (num != null) { onProviderChosen(num); return@runOnUiThread }
                    val lower = transcript.lowercase()
                    if (lower.contains("back") || lower.contains("cancel") ||
                        lower.contains("वापस") || lower.contains("రద్దు")) { goBack(); return@runOnUiThread }
                    speak("Please say 1, 2 or 3 to select a pharmacy.") { startListeningForSelection() }
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
                                lower.contains("हां") || lower.contains("हाँ")    || lower.contains("అవును") -> confirmBooking(provider)
                        lower.contains("no")  || lower.contains("cancel") || lower.contains("back") ||
                                lower.contains("नहीं") || lower.contains("వద్దు")                           -> goBack()
                        else -> speak("Say yes to confirm or no to go back.") { startListeningForBookingConfirm(provider) }
                    }
                }
            },
            onError = { runOnUiThread { startListeningForBookingConfirm(provider) } }
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BOOKING
    // ══════════════════════════════════════════════════════════════════════════

    private fun onProviderChosen(number: Int) {
        val provider = providers.getOrNull(number - 1) ?: return
        selectedProvider = provider
        speak(ServiceVoiceHandler.buildProviderSelectedPrompt(provider, LanguageManager.getLanguage())) {
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
        speak(ServiceVoiceHandler.buildBookingConfirmedPrompt(provider, bookingId, lang)) {
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
                Log.e("ServiceActivity", "Booking save error: ${e.message}")
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
            speak("Showing $sortName providers. Say 1, 2, or 3.") { startListeningForSelection() }
        }
    }

    private fun goBack() {
        when (screenState) {
            is ServiceScreenState.BookingConfirm -> {
                screenState = ServiceScreenState.ProviderList(currentSector!!, providers, "", false)
                speak("Going back to providers.") { startListeningForSelection() }
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

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — save booking
    // FIX: was failing with null because token wasn't attached
    // ══════════════════════════════════════════════════════════════════════════

    private suspend fun saveBookingToSupabase(
        bookingId: String,
        userId: String,
        providerName: String,
        providerPhone: String?,
        sector: String,
        etaMinutes: Int,
        medicines: List<String>? = null
    ) {
        val supabaseUrl = SupabaseClient.SUPABASE_URL
        val supabaseKey = SupabaseClient.SUPABASE_KEY
        val token       = UserSessionManager.getToken() ?: supabaseKey   // use user JWT if available, fallback to anon key

        val body = org.json.JSONObject().apply {
            put("booking_id",    bookingId)
            put("user_id",       userId)
            put("provider_name", providerName)
            put("sector",        sector)
            put("status",        "booked")
            put("eta_minutes",   etaMinutes)
            if (providerPhone != null)         put("provider_phone", providerPhone)
            if (!medicines.isNullOrEmpty())    put("medicines",      medicines.joinToString(", "))
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

        val response = OkHttpClient().newCall(request).execute()
        Log.d("ServiceActivity", "Booking saved: HTTP ${response.code} — $bookingId")

        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "no body"
            Log.e("ServiceActivity", "Booking save failed: $err")
        }
    }

    // ── Speak helper ──────────────────────────────────────────────────────────

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("ServiceActivity", "Speaking: $finalText")
            runOnUiThread { ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() }) }
        }
    }
}

sealed class ServiceScreenState {
    object SectorHome : ServiceScreenState()
    data class ProviderList(val sector: ServiceSector, val providers: List<ServiceProvider>, val query: String, val isLoading: Boolean) : ServiceScreenState()
    object Prescription : ServiceScreenState()
    data class BookingConfirm(val provider: ServiceProvider) : ServiceScreenState()
    data class BookingDone(val provider: ServiceProvider, val bookingId: String, val eta: String) : ServiceScreenState()
}