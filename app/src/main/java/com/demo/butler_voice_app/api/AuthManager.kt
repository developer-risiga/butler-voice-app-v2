package com.demo.butler_voice_app.api

import io.github.jan.supabase.gotrue.providers.builtin.Email

object AuthManager {

    suspend fun signup(email: String, password: String) {
        SupabaseClient.client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun login(email: String, password: String) {
        SupabaseClient.client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }
}