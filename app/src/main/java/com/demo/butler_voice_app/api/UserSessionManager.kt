package com.demo.butler_voice_app.api

import android.util.Log
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
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
    val purchase_count: Int
)

object UserSessionManager {

    var currentProfile: UserProfile? = null
    var purchaseHistory: List<PurchaseSummary> = emptyList()

    // ── Check if anyone is logged in ──────────────────────────
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

    // ── Login with email + password ───────────────────────────
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

    // ── Signup new user ───────────────────────────────────────
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

            // Create profile row
            val profile = UserProfile(
                id         = userId,
                full_name  = name,
                phone      = phone,
                total_orders = 0
            )

            SupabaseClient.client.from("profiles").insert(profile)

            currentProfile = profile
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("Session", "Signup failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Load profile + purchase history ──────────────────────
    suspend fun loadProfile(): UserProfile {
        val userId = currentUserId() ?: throw Exception("Not logged in")

        val profile = SupabaseClient.client
            .from("profiles")
            .select()
            .decodeList<UserProfile>()
            .firstOrNull { it.id == userId }
            ?: UserProfile(id = userId)

        currentProfile = profile

        // Load purchase history
        purchaseHistory = try {
            SupabaseClient.client
                .from("user_purchase_summary")
                .select(columns = Columns.list("product_name", "purchase_count"))
                .decodeList<PurchaseSummary>()
                .filter { it.product_name.isNotBlank() }
                .take(10)
        } catch (e: Exception) {
            Log.e("Session", "History load failed: ${e.message}")
            emptyList()
        }

        Log.d("Session", "Loaded profile: ${profile.full_name}, history: ${purchaseHistory.size} items")
        return profile
    }

    // ── Build personalization context for Claude ──────────────
    fun buildPersonalizationContext(): String {
        val profile = currentProfile ?: return ""
        val name = profile.full_name ?: "the user"
        val history = purchaseHistory.take(5)

        return if (history.isEmpty()) {
            "Customer name: $name. First time buyer — no purchase history."
        } else {
            val historyText = history.joinToString(", ") {
                "${it.product_name} (${it.purchase_count}x)"
            }
            "Customer name: $name. Previous purchases: $historyText. " +
            "Preferred brands: ${profile.preferred_brands?.joinToString(", ") ?: "none recorded"}. " +
            "Suggest their usual brands when they ask for a product category."
        }
    }

    // ── Logout after order (kiosk mode) ──────────────────────
    suspend fun logout() {
        try {
            SupabaseClient.client.auth.signOut()
            currentProfile = null
            purchaseHistory = emptyList()
            Log.d("Session", "User logged out")
        } catch (e: Exception) {
            Log.e("Session", "Logout failed: ${e.message}")
        }
    }
}
