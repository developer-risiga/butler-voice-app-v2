package com.demo.butler_voice_app.api

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email

object AuthManager {

    // ✅ LOGIN
    suspend fun login(email: String, password: String): Boolean {
        return try {

            SupabaseClient.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ✅ SIGNUP
    suspend fun signup(email: String, password: String): Boolean {
        return try {

            SupabaseClient.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    fun currentUserId(): String? {
    return try {
        SupabaseClient.client.auth.currentUserOrNull()?.id
    } catch (e: Exception) {
        Log.e("AuthManager", "Could not get user ID: ${e.message}")
        null
    }
}
}
