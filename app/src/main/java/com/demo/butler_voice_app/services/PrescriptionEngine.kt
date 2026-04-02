package com.demo.butler_voice_app.services

import android.util.Base64
import android.util.Log
import com.demo.butler_voice_app.BuildConfig
import com.demo.butler_voice_app.api.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * PrescriptionEngine — handles everything prescription-related.
 *
 * INTEGRATES with your existing models:
 *   PrescriptionMedicine, PrescriptionStatus, ServiceProvider
 *
 * TWO STEPS:
 * 1. extractMedicines(imageBytes) → GPT-4o Vision reads doctor handwriting
 *    Returns List<String> — compatible with ServiceManager.findMedicalShopsForPrescription()
 *
 * 2. verifyAndEnrichMedicines(rawList) → matches against Supabase medicines table
 *    Returns List<PrescriptionMedicine> with availability + price per medicine
 *    (Optional step — gracefully falls back if table doesn't exist yet)
 *
 * USAGE in ServiceActivity:
 *   val medicines = PrescriptionEngine.extractMedicines(bytes)
 *   val enriched  = PrescriptionEngine.verifyAndEnrichMedicines(medicines)
 */
object PrescriptionEngine {

    private const val TAG = "PrescriptionEngine"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ══════════════════════════════════════════════════════════════════════
    // STEP 1 — Extract medicine names from prescription image
    //
    // Uses GPT-4o Vision with a prompt specifically tuned for:
    //   • Indian doctor handwriting styles
    //   • Common abbreviations (Tab., Cap., BD, TDS, OD, SOS...)
    //   • Indian brand names (Crocin, Dolo, Pantop, Azee, Combiflam...)
    //   • Mixed English + Hindi prescriptions
    //
    // Returns a clean List<String> like:
    //   ["Paracetamol 500mg Tab", "Azithromycin 250mg Cap", "Pantoprazole 40mg Tab"]
    // ══════════════════════════════════════════════════════════════════════

    suspend fun extractMedicines(imageBytes: ByteArray): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val mimeType = detectMimeType(imageBytes)
                val base64   = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                Log.d(TAG, "Sending ${imageBytes.size} bytes to GPT-4o Vision")

                val prompt = buildIndianPrescriptionPrompt()

                val requestBody = JSONObject().apply {
                    put("model", "gpt-4o")
                    put("max_tokens", 800)
                    put("temperature", 0.1) // Low temp = more consistent, less creative
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", prompt)
                                })
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:$mimeType;base64,$base64")
                                        put("detail", "high") // High detail = reads small text better
                                    })
                                })
                            })
                        }
                    ))
                }.toString()

                val response = http.newCall(
                    Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "GPT-4o response: ${responseBody.take(600)}")

                val content = JSONObject(responseBody)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
                    .replace("```json", "").replace("```", "").trim()

                Log.d(TAG, "Extracted content: $content")

                val arr      = JSONArray(content)
                val medicines = (0 until arr.length())
                    .map { arr.getString(it).trim() }
                    .filter { it.isNotBlank() && it != "[]" }

                Log.d(TAG, "Extracted ${medicines.size} medicines: $medicines")
                medicines

            } catch (e: Exception) {
                Log.e(TAG, "GPT-4o Vision failed", e)
                emptyList()
            }
        }

    // ══════════════════════════════════════════════════════════════════════
    // STEP 2 — Verify each medicine against your Supabase medicines table
    //          and return enriched PrescriptionMedicine objects
    //
    // If your medicines table doesn't exist yet, this gracefully returns
    // each medicine as available=true with price=0.0 (no crash).
    //
    // Supabase table expected:
    //   medicines: id, name, generic_name, category
    //   (run supabase_medicine_schema.sql to create it)
    // ══════════════════════════════════════════════════════════════════════

    suspend fun verifyAndEnrichMedicines(
        rawMedicines: List<String>
    ): List<PrescriptionMedicine> = withContext(Dispatchers.IO) {

        rawMedicines.map { raw ->
            try {
                // Extract the core drug name for searching (strip dose/form)
                val coreName = stripDoseAndForm(raw)
                Log.d(TAG, "Searching Supabase for: '$coreName' (from '$raw')")

                val url = "${SupabaseClient.SUPABASE_URL}/rest/v1/medicines" +
                        "?or=(name.ilike.*${encode(coreName)}*,generic_name.ilike.*${encode(coreName)}*)" +
                        "&select=name,generic_name" +
                        "&limit=1"

                val response = OkHttpClient().newCall(
                    Request.Builder()
                        .url(url)
                        .header("apikey", SupabaseClient.SUPABASE_KEY)
                        .header("Authorization", "Bearer ${SupabaseClient.SUPABASE_KEY}")
                        .get()
                        .build()
                ).execute()

                val body = response.body?.string() ?: "[]"
                val arr  = JSONArray(body)

                if (arr.length() > 0) {
                    val match = arr.getJSONObject(0)
                    Log.d(TAG, "Verified '$raw' → '${match.optString("name")}'")
                    PrescriptionMedicine(
                        name      = match.optString("name", raw),
                        quantity  = extractQuantity(raw),
                        price     = 0.0, // fetched per pharmacy in step 3
                        available = true
                    )
                } else {
                    Log.d(TAG, "Not in catalogue: '$raw' — using as-is")
                    PrescriptionMedicine(
                        name      = raw,
                        quantity  = extractQuantity(raw),
                        price     = 0.0,
                        available = true // assume available — pharmacy will confirm
                    )
                }
            } catch (e: Exception) {
                // Catalogue lookup failed — don't crash, use OCR name as-is
                Log.w(TAG, "Catalogue lookup failed for '$raw': ${e.message}")
                PrescriptionMedicine(
                    name      = raw,
                    quantity  = "1",
                    price     = 0.0,
                    available = true
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FULL FLOW — convenience function combining steps 1 + 2
    //
    // Call this from ServiceActivity.processPrescriptionBytesAsync():
    //
    //   val (rawMedicines, enriched) = PrescriptionEngine.processImage(bytes)
    //   extractedMedicines = rawMedicines  // for ServiceManager search
    //   detailedMedicines  = enriched       // for showing per-item availability
    // ══════════════════════════════════════════════════════════════════════

    data class ExtractionResult(
        val rawMedicines: List<String>,            // ["Paracetamol 500mg Tab", ...]
        val enrichedMedicines: List<PrescriptionMedicine>  // verified + priced
    )

    suspend fun processImage(imageBytes: ByteArray): ExtractionResult {
        val raw      = extractMedicines(imageBytes)
        if (raw.isEmpty()) return ExtractionResult(emptyList(), emptyList())
        val enriched = verifyAndEnrichMedicines(raw)
        return ExtractionResult(raw, enriched)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * The critical part — teaches GPT-4o Indian prescription patterns.
     *
     * Why this matters:
     *   • Doctors write "Tab. PCM 500" not "Paracetamol Tablet 500mg"
     *   • BD/TDS/OD are frequencies — NOT medicine names
     *   • Brand names like Crocin, Dolo, Combiflam must be recognised
     *   • Telugu/Hindi drug names appear on same slip as English
     */
    private fun buildIndianPrescriptionPrompt(): String = """
You are an expert at reading Indian doctor prescription handwriting.

ABBREVIATIONS TO RECOGNISE:
Forms: Tab./T.=Tablet, Cap./C.=Capsule, Inj.=Injection, Syr./Syp.=Syrup, Oint./Cr.=Ointment, Drops/Gtt.=Eye/Ear drops
Frequency (DO NOT include these in output): BD/BID=twice, TDS/TID=thrice, OD=once, QID=four times, HS=bedtime, SOS=if needed, AC=before food, PC=after food

COMMON SHORT FORMS → FULL NAME:
PCM=Paracetamol, Pan=Pantoprazole, Amox=Amoxicillin, Azith=Azithromycin, Cefi=Cefixime, Ibu=Ibuprofen, Dom=Domperidone, Ond=Ondansetron, Metf=Metformin, Aten=Atenolol, Amlo=Amlodipine, Levo=Levocetirizine, Mont=Montelukast, Rab=Rabeprazole, Oflox=Ofloxacin

COMMON INDIAN BRAND NAMES:
Crocin/Dolo/Calpol=Paracetamol | Augmentin=Amoxicillin+Clavulanate | Pantop/Pantocid/Pan-D=Pantoprazole | Zithromax/Azee=Azithromycin | Taxim/Cefolac=Cefixime | Combiflam=Ibuprofen+Paracetamol | Dolopar=Paracetamol | Mox=Amoxicillin | Ciplox=Ciprofloxacin | Norflox=Norfloxacin | Sporolac=Probiotic | Digene=Antacid | Pudin Hara=Mint antacid

INSTRUCTIONS:
- Extract EVERY medicine visible on the prescription
- For each: combine name + dose + form into ONE string
- If handwriting is partially unclear, make your BEST GUESS — never skip
- The prescription may be in English, Hindi, Telugu, or mixed
- DO NOT include: doctor name, patient name, date, frequency/timing instructions
- DO NOT include vitamin supplements unless explicitly written as a medicine

OUTPUT FORMAT: Return ONLY a valid JSON array of strings. No explanation, no markdown.
Example: ["Paracetamol 500mg Tab", "Pantoprazole 40mg Tab", "Azithromycin 500mg Cap", "Ondansetron 4mg Tab"]
If image is not a prescription or completely illegible: []
""".trimIndent()

    /** Detect image MIME type from file header bytes */
    private fun detectMimeType(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size > 3 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() -> "application/pdf"
        else -> "image/jpeg" // safe default
    }

    /** Strip dose and form to get core drug name for catalogue search */
    private fun stripDoseAndForm(raw: String): String {
        return raw
            .replace(Regex("\\d+\\.?\\d*\\s*(mg|ml|mcg|g|iu|%)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(Tab|Cap|Syr|Syp|Inj|Oint|Cream|Drops|Gel|Spray|Sachet|Susp)\\.?\\b", RegexOption.IGNORE_CASE), "")
            .trim()
            .split(" ")
            .firstOrNull { it.length > 2 }
            ?.trim() ?: raw.trim()
    }

    /** Extract quantity/dose string from medicine line */
    private fun extractQuantity(raw: String): String {
        val doseMatch = Regex("\\d+\\.?\\d*\\s*(mg|ml|mcg|g)", RegexOption.IGNORE_CASE)
            .find(raw)
        return doseMatch?.value?.trim() ?: "1"
    }

    /** URL-encode for Supabase query param */
    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}