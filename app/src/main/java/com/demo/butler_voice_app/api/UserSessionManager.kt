package com.demo.butler_voice_app.api

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

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

    fun isLoggedIn(): Boolean {
        return try {
            SupabaseClient.client.auth.currentUserOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    fun currentUserId(): String? {
        return try {
            SupabaseClient.client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun login(email: String, password: String): Result<UserProfile> {
        return try {
            SupabaseClient.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val profile = loadProfile()
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("Session", "Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signup(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<UserProfile> {
        return try {
            SupabaseClient.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = currentUserId() ?: throw Exception("No user ID after signup")
            val profile = UserProfile(
                id        = userId,
                full_name = name,
                phone     = phone
            )
            SupabaseClient.client.from("profiles").insert(profile)
            currentProfile = profile
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("Session", "Signup failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loadProfile(): UserProfile {
        val userId = currentUserId() ?: throw Exception("Not logged in")
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

    suspend fun logout() {
        try {
            SupabaseClient.client.auth.signOut()
            currentProfile = null
            purchaseHistory = emptyList()
            Log.d("Session", "Logged out")
        } catch (e: Exception) {
            Log.e("Session", "Logout failed: ${e.message}")
        }
    }
}
