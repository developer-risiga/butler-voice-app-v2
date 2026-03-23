package com.demo.butler_voice_app.api

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    var currentProfile: UserProfile? = null
    var purchaseHistory: List<PurchaseSummary> = emptyList()
    private var currentToken: String? = null
    private var currentUid: String? = null

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun isLoggedIn(): Boolean = currentToken != null && currentUid != null

    fun currentUserId(): String? = currentUid

    suspend fun login(email: String, password: String): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"email":"$email","password":"$password"}"""
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("${SupabaseClient.SUPABASE_URL}/auth/v1/token?grant_type=password")
                    .addHeader("apikey", SupabaseClient.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Login failed: $responseBody"))
                }

                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                currentToken = jsonObj["access_token"]?.jsonPrimitive?.content
                currentUid = jsonObj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

                Log.d("Session", "Login success. UID: $currentUid")
                val profile = loadProfile()
                Result.success(profile)
            } catch (e: Exception) {
                Log.e("Session", "Login failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun signup(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"email":"$email","password":"$password"}"""
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("${SupabaseClient.SUPABASE_URL}/auth/v1/signup")
                    .addHeader("apikey", SupabaseClient.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Signup failed: $responseBody"))
                }

                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                currentToken = jsonObj["access_token"]?.jsonPrimitive?.content
                currentUid = jsonObj["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    ?: jsonObj["id"]?.jsonPrimitive?.content

                Log.d("Session", "Signup success. UID: $currentUid")

                val userId = currentUid ?: throw Exception("No user ID after signup")
                val profile = UserProfile(
                    id        = userId,
                    full_name = name,
                    phone     = phone
                )

                try {
                    SupabaseClient.client.from("profiles").insert(profile)
                } catch (e: Exception) {
                    Log.w("Session", "Profile insert failed: ${e.message}")
                }

                currentProfile = profile
                Result.success(profile)
            } catch (e: Exception) {
                Log.e("Session", "Signup failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun loadProfile(): UserProfile {
        val userId = currentUid ?: throw Exception("Not logged in")
        val profile = try {
            SupabaseClient.client
                .from("profiles")
                .select()
                .decodeList<UserProfile>()
                .firstOrNull { it.id == userId }
                ?: UserProfile(id = userId)
        } catch (e: Exception) {
            Log.e("Session", "Profile load failed: ${e.message}")
            UserProfile(id = userId)
        }
        currentProfile = profile

        purchaseHistory = try {
            SupabaseClient.client
                .from("order_items")
                .select(columns = Columns.list("product_name"))
                .decodeList<PurchaseSummary>()
                .take(5)
        } catch (e: Exception) {
            Log.e("Session", "History load failed: ${e.message}")
            emptyList()
        }

        Log.d("Session", "Profile: ${profile.full_name}, history: ${purchaseHistory.size}")
        return profile
    }

    fun buildPersonalizationContext(): String {
        val profile = currentProfile ?: return ""
        val name = profile.full_name ?: "the customer"
        val history = purchaseHistory.take(5)
        return if (history.isEmpty()) {
            "Customer name: $name. First time buyer."
        } else {
            val historyText = history.joinToString(", ") { it.product_name }
            "Customer name: $name. Previously ordered: $historyText. Suggest these if relevant."
        }
    }

    fun logout() {
        currentToken = null
        currentUid = null
        currentProfile = null
        purchaseHistory = emptyList()
        Log.d("Session", "Logged out")
    }
}
