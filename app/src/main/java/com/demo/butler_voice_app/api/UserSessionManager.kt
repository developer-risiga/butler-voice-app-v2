package com.demo.butler_voice_app.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class UserProfile(
    val id: String,
    val full_name: String? = null,
    val phone: String? = null,
    val preferred_brands: List<String>? = null,
    val frequent_items: List<String>? = null,
    val total_orders: Int = 0
)

@Serializable
data class PurchaseSummary(
    val product_name: String,
    val purchase_count: Int = 0
)

object UserSessionManager {

    var currentProfile  : UserProfile?          = null
    var purchaseHistory : List<PurchaseSummary> = emptyList()
    private var currentToken : String? = null
    private var currentUid   : String? = null

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val url get() = SupabaseClient.SUPABASE_URL
    private val key get() = SupabaseClient.SUPABASE_KEY

    fun isLoggedIn()    : Boolean = currentToken != null && currentUid != null
    fun currentUserId() : String? = currentUid
    fun getToken()      : String? = currentToken

    // ─── RESTORE SAVED SESSION ────────────────────────────────

    /**
     * Try to restore a previously saved session from disk.
     * Returns true if session was valid and restored.
     * Call this on every wake word detection.
     */
    suspend fun tryRestoreSession(): Boolean {
        // Already in memory
        if (isLoggedIn() && currentProfile != null) return true
        // Nothing saved
        if (!SessionStore.hasSession()) return false

        val savedToken = SessionStore.getToken() ?: return false
        val savedUid   = SessionStore.getUid()   ?: return false
        val savedName  = SessionStore.getName()

        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$url/rest/v1/profiles?id=eq.$savedUid&select=*")
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $savedToken")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                val res  = http.newCall(req).execute()
                val body = res.body?.string() ?: "[]"

                if (!res.isSuccessful) {
                    Log.w("Session", "Saved token invalid (${res.code}), clearing")
                    SessionStore.clear()
                    return@withContext false
                }

                currentToken = savedToken
                currentUid   = savedUid

                val arr   = json.parseToJsonElement(body).jsonArray
                val first = arr.firstOrNull()
                currentProfile = if (first != null) {
                    json.decodeFromJsonElement(UserProfile.serializer(), first)
                } else {
                    UserProfile(id = savedUid, full_name = savedName)
                }

                loadPurchaseHistory()

                Log.d("Session", "Session restored: ${currentProfile?.full_name}")
                true

            } catch (e: Exception) {
                Log.e("Session", "Restore failed: ${e.message}")
                SessionStore.clear()
                false
            }
        }
    }

    // ─── LOGIN ────────────────────────────────────────────────

    suspend fun login(email: String, password: String): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"email":"$email","password":"$password"}"""
                    .toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("$url/auth/v1/token?grant_type=password")
                    .addHeader("apikey", key)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val res     = http.newCall(req).execute()
                val resBody = res.body?.string() ?: ""

                if (!res.isSuccessful) {
                    return@withContext Result.failure(Exception("Login failed: $resBody"))
                }

                val obj      = json.parseToJsonElement(resBody).jsonObject
                currentToken = obj["access_token"]?.jsonPrimitive?.content
                currentUid   = obj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

                Log.d("Session", "Login success. UID: $currentUid")

                val userEmail = obj["user"]?.jsonObject?.get("email")?.jsonPrimitive?.content
                val profile   = loadProfile()
                // Persist session so next wake word auto-logs in
                SessionStore.save(currentToken!!, currentUid!!, profile.full_name, userEmail)
                Result.success(profile)

            } catch (e: Exception) {
                Log.e("Session", "Login error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ─── SIGNUP ───────────────────────────────────────────────

    suspend fun signup(
        email   : String,
        password: String,
        name    : String,
        phone   : String
    ): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("Session", "Attempting signup: email=$email, password length=${password.length}")

                val body = """{"email":"$email","password":"$password"}"""
                    .toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("$url/auth/v1/signup")
                    .addHeader("apikey", key)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val res     = http.newCall(req).execute()
                val resBody = res.body?.string() ?: ""
                Log.d("Session", "Signup response ${res.code}: $resBody")

                if (!res.isSuccessful) {
                    return@withContext Result.failure(Exception("Signup failed ${res.code}: $resBody"))
                }

                val obj      = json.parseToJsonElement(resBody).jsonObject
                currentToken = obj["access_token"]?.jsonPrimitive?.content
                currentUid   = obj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content

                Log.d("Session", "Signup success. UID: $currentUid, token: ${currentToken?.take(20)}")

                val userId = currentUid ?: throw Exception("No user ID after signup")
                val token  = currentToken ?: throw Exception("No token after signup")

                val profileJson = """{"id":"$userId","full_name":"$name","phone":"$phone"}"""
                val profileReq  = Request.Builder()
                    .url("$url/rest/v1/profiles")
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(profileJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val profileRes = http.newCall(profileReq).execute()
                Log.d("Session", "Profile insert code: ${profileRes.code}")

                val profile = UserProfile(id = userId, full_name = name, phone = phone)
                currentProfile = profile

                // Persist session
                SessionStore.save(token, userId, name, email)
                Result.success(profile)

            } catch (e: Exception) {
                Log.e("Session", "Signup exception ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ─── LOAD PROFILE ─────────────────────────────────────────

    suspend fun loadProfile(): UserProfile {
        return withContext(Dispatchers.IO) {
            val userId = currentUid ?: return@withContext UserProfile(id = "")
            val token  = currentToken ?: return@withContext UserProfile(id = userId)

            val profile = try {
                val req = Request.Builder()
                    .url("$url/rest/v1/profiles?id=eq.$userId&select=*")
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                val res  = http.newCall(req).execute()
                val body = res.body?.string() ?: "[]"
                Log.d("Session", "Profile fetch code: ${res.code}")

                val arr   = json.parseToJsonElement(body).jsonArray
                val first = arr.firstOrNull()
                if (first != null) {
                    json.decodeFromJsonElement(UserProfile.serializer(), first)
                } else {
                    UserProfile(id = userId)
                }
            } catch (e: Exception) {
                Log.e("Session", "Profile load error: ${e.message}")
                UserProfile(id = userId)
            }

            currentProfile = profile
            loadPurchaseHistory()
            Log.d("Session", "Loaded: ${profile.full_name}, history: ${purchaseHistory.size}")
            profile
        }
    }

    // ─── PURCHASE HISTORY ─────────────────────────────────────

    private suspend fun loadPurchaseHistory() {
        val userId = currentUid ?: return
        val token  = currentToken ?: return

        purchaseHistory = try {
            val req = Request.Builder()
                .url("$url/rest/v1/order_items?select=product_name,order_id,orders(user_id)&orders.user_id=eq.$userId&limit=10")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val res  = http.newCall(req).execute()
            val body = res.body?.string() ?: "[]"
            val arr  = json.parseToJsonElement(body).jsonArray

            arr.mapNotNull {
                val name = it.jsonObject["product_name"]?.jsonPrimitive?.content
                if (!name.isNullOrBlank()) PurchaseSummary(product_name = name) else null
            }.distinctBy { it.product_name }

        } catch (e: Exception) {
            Log.e("Session", "History load error: ${e.message}")
            emptyList()
        }
    }

    // ─── PERSONALIZATION ──────────────────────────────────────

    fun buildPersonalizationContext(): String {
        val profile = currentProfile ?: return ""
        val name    = profile.full_name ?: "the customer"
        val history = purchaseHistory.take(5)
        return if (history.isEmpty()) {
            "Customer name: $name. First time buyer."
        } else {
            val items = history.joinToString(", ") { it.product_name }
            "Customer name: $name. Previously ordered: $items. Suggest these if relevant."
        }
    }

    // ─── LOGOUT ───────────────────────────────────────────────

    /** Soft logout — clears memory, keeps disk session for next visit */
    fun logout() {
        currentToken    = null
        currentUid      = null
        currentProfile  = null
        purchaseHistory = emptyList()
        Log.d("Session", "Logged out (session persisted on disk)")
    }

    /** Hard logout — clears memory AND disk */
    fun hardLogout() {
        logout()
        SessionStore.clear()
        Log.d("Session", "Hard logout — session cleared from disk")
    }
}