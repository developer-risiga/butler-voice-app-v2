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
import com.demo.butler_voice_app.api.UserSessionManager
import com.demo.butler_voice_app.services.*
import com.demo.butler_voice_app.voice.SarvamSTTManager
import com.demo.butler_voice_app.ai.LanguageManager
import com.demo.butler_voice_app.ai.TranslationManager
import kotlinx.coroutines.launch
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════════════
// SERVICE ACTIVITY — Handles all India services voice flow
// ══════════════════════════════════════════════════════════════════════════════

class ServiceActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SECTOR    = "extra_sector"
        const val EXTRA_QUERY     = "extra_query"
        const val EXTRA_IS_RX     = "extra_is_prescription"
        const val EXTRA_IS_EMERG  = "extra_is_emergency"
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private var screenState by mutableStateOf<ServiceScreenState>(ServiceScreenState.SectorHome)
    private var currentSector: ServiceSector? = null
    private var providers: List<ServiceProvider> = emptyList()
    private var selectedProvider: ServiceProvider? = null
    private var extractedMedicines: List<String> = emptyList()
    private var prescriptionStatus by mutableStateOf(PrescriptionUploadStatus.WAITING)
    private var userLat: Double? = null
    private var userLng: Double? = null

    private lateinit var ttsManager: TTSManager
    private lateinit var sarvamSTT: SarvamSTTManager

    // Image picker
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processPrescritionImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get location from parent
        userLat = intent.getDoubleExtra("user_lat", 0.0).takeIf { it != 0.0 }
        userLng = intent.getDoubleExtra("user_lng", 0.0).takeIf { it != 0.0 }

        sarvamSTT = SarvamSTTManager(this, BuildConfig.SARVAM_API_KEY)
        ttsManager = TTSManager(this, BuildConfig.ELEVENLABS_API_KEY, "RwXLkVKnRloV1UPh3Ccx")
        ttsManager.init {
            // Handle incoming intent
            val sector    = intent.getStringExtra(EXTRA_SECTOR)?.let { runCatching { ServiceSector.valueOf(it) }.getOrNull() }
            val query     = intent.getStringExtra(EXTRA_QUERY) ?: ""
            val isRx      = intent.getBooleanExtra(EXTRA_IS_RX, false)
            val isEmerg   = intent.getBooleanExtra(EXTRA_IS_EMERG, false)

            when {
                isEmerg              -> handleEmergency()
                isRx                 -> startPrescriptionFlow()
                sector != null       -> startSectorFlow(sector, query)
                else                 -> { screenState = ServiceScreenState.SectorHome; speakServicePrompt() }
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
                        sector           = s.sector,
                        providers        = s.providers,
                        query            = s.query,
                        isLoading        = s.isLoading,
                        onProviderSelected = { num -> onProviderChosen(num) },
                        onVoiceFilter    = { startListeningForFilter() }
                    )
                is ServiceScreenState.Prescription ->
                    PrescriptionUploadScreen(
                        status               = prescriptionStatus,
                        extractedMedicines   = extractedMedicines,
                        providers            = providers,
                        onUploadTap          = { imagePicker.launch("image/*") },
                        onProviderSelected   = { num -> onProviderChosen(num) }
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

    // ── Flow starters ──────────────────────────────────────────────────────────

    private fun startSectorFlow(sector: ServiceSector, query: String) {
        currentSector = sector
        screenState = ServiceScreenState.ProviderList(sector, emptyList(), query, isLoading = true)
        val lang = LanguageManager.getLanguage()
        speak(ServiceVoiceHandler.buildSectorDetectedPrompt(sector, lang)) {}

        lifecycleScope.launch {
            val filter = ServiceFilter()
            providers = ServiceManager.searchProviders(ServiceIntent(sector, query), userLat, userLng, filter)
            screenState = ServiceScreenState.ProviderList(sector, providers, query, isLoading = false)
            val response = ServiceManager.buildServiceVoiceResponse(
                sector,
                providers,
                UserSessionManager.currentProfile?.full_name?.split(" ")?.first() ?: ""
            )
            speak(response) { startListeningForSelection() }
        }
    }

    private fun startPrescriptionFlow() {
        screenState = ServiceScreenState.Prescription
        prescriptionStatus = PrescriptionUploadStatus.WAITING
        speak(ServiceVoiceHandler.buildPrescriptionUploadPrompt(LanguageManager.getLanguage())) {
            // Auto-open camera after 1s delay
            Handler(Looper.getMainLooper()).postDelayed({ imagePicker.launch("image/*") }, 1000)
        }
    }

    private fun handleEmergency() {
        currentSector = ServiceSector.AMBULANCE
        speak(ServiceVoiceHandler.buildEmergencyPrompt(LanguageManager.getLanguage())) {
            // In production: auto-dial 108
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:108"))
            startActivity(dialIntent)
            lifecycleScope.launch {
                providers = ServiceManager.searchProviders(ServiceIntent(ServiceSector.AMBULANCE, "emergency", isEmergency = true), userLat, userLng)
                screenState = ServiceScreenState.ProviderList(ServiceSector.AMBULANCE, providers, "emergency", false)
            }
        }
    }

    // ── Image processing for prescription ─────────────────────────────────────

    private fun processPrescritionImage(uri: Uri) {
        prescriptionStatus = PrescriptionUploadStatus.PROCESSING
        speak("Reading your prescription now…") {}

        lifecycleScope.launch {
            try {
                // Convert image to base64
                val inputStream = contentResolver.openInputStream(uri)
                val bytes       = inputStream?.readBytes() ?: byteArrayOf()
                inputStream?.close()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                // Extract medicines via GPT-4 Vision (RAG)
                val medicines = ServiceManager.extractMedicinesFromPrescription(base64, BuildConfig.OPENAI_API_KEY)
                extractedMedicines = medicines

                if (medicines.isEmpty()) {
                    speak("I couldn't clearly read the prescription. Please say the medicine names or try a clearer photo.") {
                        prescriptionStatus = PrescriptionUploadStatus.WAITING
                    }
                    return@launch
                }

                // Find nearby pharmacies
                providers = ServiceManager.findMedicalShopsForPrescription(medicines, userLat ?: 22.72, userLng ?: 75.86)
                prescriptionStatus = PrescriptionUploadStatus.MEDICINES_FOUND

                val prompt = ServiceVoiceHandler.buildMedicinesExtractedPrompt(medicines, providers.size, LanguageManager.getLanguage())
                speak(prompt) { startListeningForSelection() }

            } catch (e: Exception) {
                Log.e("ServiceActivity", "Prescription processing failed: ${e.message}")
                speak("Something went wrong reading the prescription. Please try again.") {
                    prescriptionStatus = PrescriptionUploadStatus.WAITING
                }
            }
        }
    }

    // ── Voice listening ────────────────────────────────────────────────────────

    private fun speakServicePrompt() {
        speak(ServiceVoiceHandler.buildServiceCategoryPrompt(LanguageManager.getLanguage())) {
            startListeningForSector()
        }
    }

    private fun startListeningForSector() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    Log.d("ServiceActivity", "Sector transcript: $transcript")
                    if (ServiceVoiceHandler.isEmergency(transcript)) { handleEmergency(); return@runOnUiThread }
                    if (ServiceVoiceHandler.isPrescriptionRequest(transcript)) { startPrescriptionFlow(); return@runOnUiThread }
                    val intent = ServiceManager.detectServiceIntent(transcript)
                    if (intent.sector != null) {
                        startSectorFlow(intent.sector, transcript)
                    } else {
                        speak("Sorry, I didn't catch that. Which service do you need?") { startListeningForSector() }
                    }
                }
            },
            onError = {
                runOnUiThread { speak("Something went wrong. Please try again.") { startListeningForSector() } }
            }
        )
    }

    private fun startListeningForSelection() {
        sarvamSTT.startListening(
            onResult = { transcript ->
                runOnUiThread {
                    Log.d("ServiceActivity", "Selection transcript: $transcript")

                    // Check for filter command
                    val filter = ServiceVoiceHandler.parseFilterCommand(transcript)
                    if (filter != null) {
                        applyFilter(filter); return@runOnUiThread
                    }

                    // Check for number selection
                    val num = ServiceVoiceHandler.parseNumberSelection(transcript)
                    if (num != null) { onProviderChosen(num); return@runOnUiThread }

                    // Check for back/cancel
                    if (transcript.lowercase().contains("back") || transcript.lowercase().contains("cancel") ||
                        transcript.lowercase().contains("वापस") || transcript.lowercase().contains("రద్దు")) {
                        goBack(); return@runOnUiThread
                    }

                    speak("Please say 1, 2 or 3 to select a provider, or say filter by rating, nearest, or cheapest.") {
                        startListeningForSelection()
                    }
                }
            },
            onError = {
                runOnUiThread { startListeningForSelection() }
            }
        )
    }

    private fun startListeningForFilter() {
        speak("How would you like to filter? Say: by rating, nearest, cheapest, or fastest.") {
            sarvamSTT.startListening(
                onResult = { transcript ->
                    runOnUiThread {
                        val filter = ServiceVoiceHandler.parseFilterCommand(transcript)
                        if (filter != null) applyFilter(filter)
                        else { speak("Showing top-rated providers.") { startListeningForSelection() } }
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
                                lower.contains("हां") || lower.contains("हाँ") || lower.contains("అవును") -> {
                            confirmBooking(provider)
                        }
                        lower.contains("no") || lower.contains("cancel") || lower.contains("back") ||
                                lower.contains("नहीं") || lower.contains("వద్దు") -> {
                            goBack()
                        }
                        else -> {
                            speak("Say yes to confirm the booking or no to go back.") {
                                startListeningForBookingConfirm(provider)
                            }
                        }
                    }
                }
            },
            onError = { runOnUiThread { startListeningForBookingConfirm(provider) } }
        )
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private fun onProviderChosen(number: Int) {
        val provider = providers.getOrNull(number - 1) ?: return
        selectedProvider = provider
        val lang = LanguageManager.getLanguage()
        speak(ServiceVoiceHandler.buildProviderSelectedPrompt(provider, lang)) {
            screenState = ServiceScreenState.BookingConfirm(provider)
            startListeningForBookingConfirm(provider)
        }
    }

    private fun confirmBooking(provider: ServiceProvider) {
        val bookingId = "BUT-${UUID.randomUUID().toString().takeLast(6).uppercase()}"
        val lang      = LanguageManager.getLanguage()

        if (screenState is ServiceScreenState.Prescription) {
            prescriptionStatus = PrescriptionUploadStatus.SENT_TO_PHARMACY
        }

        screenState = ServiceScreenState.BookingDone(provider, bookingId, provider.eta)
        speak(ServiceVoiceHandler.buildBookingConfirmedPrompt(provider, bookingId, lang)) {
            // Return to MainActivity after 8 seconds
            Handler(Looper.getMainLooper()).postDelayed({ finishWithResult(bookingId) }, 8000)
        }
    }

    private fun applyFilter(filter: ServiceFilter) {
        val sector = currentSector ?: return
        lifecycleScope.launch {
            providers = ServiceManager.searchProviders(ServiceIntent(sector, ""), userLat, userLng, filter)
            screenState = ServiceScreenState.ProviderList(sector, providers, "", false)
            val sortName = when (filter.sortBy) {
                ServiceSort.RATING    -> "top rated"
                ServiceSort.DISTANCE  -> "nearest"
                ServiceSort.PRICE_LOW -> "cheapest"
                ServiceSort.FASTEST   -> "fastest"
                else -> "best"
            }
            speak("Showing $sortName ${sector.displayName} providers. Say 1, 2, or 3 to choose.") {
                startListeningForSelection()
            }
        }
    }

    private fun goBack() {
        when (screenState) {
            is ServiceScreenState.BookingConfirm -> {
                screenState = ServiceScreenState.ProviderList(
                    currentSector!!, providers, "", false
                )
                speak("Going back to providers list.") { startListeningForSelection() }
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

    // ── Speak helper ───────────────────────────────────────────────────────────

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        lifecycleScope.launch {
            val lang      = LanguageManager.getLanguage()
            val finalText = TranslationManager.translate(text, lang)
            Log.d("ServiceActivity", "Speaking: $finalText")
            runOnUiThread {
                ttsManager.speak(text = finalText, language = lang, onDone = { onDone?.invoke() })
            }
        }
    }
}

// ── Screen state ──────────────────────────────────────────────────────────────
sealed class ServiceScreenState {
    object SectorHome : ServiceScreenState()
    data class ProviderList(val sector: ServiceSector, val providers: List<ServiceProvider>, val query: String, val isLoading: Boolean) : ServiceScreenState()
    object Prescription : ServiceScreenState()
    data class BookingConfirm(val provider: ServiceProvider) : ServiceScreenState()
    data class BookingDone(val provider: ServiceProvider, val bookingId: String, val eta: String) : ServiceScreenState()
}