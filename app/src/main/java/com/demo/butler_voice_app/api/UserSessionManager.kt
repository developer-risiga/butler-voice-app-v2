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
