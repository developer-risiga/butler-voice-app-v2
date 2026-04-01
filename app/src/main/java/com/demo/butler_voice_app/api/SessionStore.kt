package com.demo.butler_voice_app.api

import android.content.Context
import android.util.Log

/**
 * Persistent session store — SharedPreferences backed.
 *
 * ── BUG 7 FIX ──────────────────────────────────────────────────────────────
 * Previous version had no refresh token storage. When the access token
 * expired (401), tryRestoreSession() cleared everything and forced full
 * re-login on every cold start.
 *
 * This version stores both access_token AND refresh_token so MainActivity
 * can silently refresh before falling through to the AuthActivity screen.
 * ───────────────────────────────────────────────────────────────────────────
 */
object SessionStore {

    private const val PREFS        = "butler_session_v2"
    private const val KEY_ACCESS   = "access_token"
    private const val KEY_REFRESH  = "refresh_token"
    private const val KEY_UID      = "uid"
    private const val KEY_NAME     = "name"
    private const val KEY_EMAIL    = "email"

    private var prefs: android.content.SharedPreferences? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.d("SessionStore", "Init. Has session: ${hasSession()}")
    }

    // ── Write ──────────────────────────────────────────────────────────────

    /**
     * Save a full session after login or signup.
     * [refreshToken] should be the Supabase refresh_token from the auth response.
     */
    fun saveSession(
        accessToken:  String,
        uid:          String,
        name:         String,
        refreshToken: String = "",
        email:        String = ""
    ) {
        prefs?.edit()?.run {
            putString(KEY_ACCESS,  accessToken)
            putString(KEY_REFRESH, refreshToken)
            putString(KEY_UID,     uid)
            putString(KEY_NAME,    name)
            putString(KEY_EMAIL,   email)
            apply()
        }
        Log.d("SessionStore", "Session saved for uid=$uid name=$name")
    }

    /**
     * Called after a successful token refresh — updates tokens without
     * touching uid / name / email.
     */
    fun updateTokens(newAccessToken: String, newRefreshToken: String = "") {
        prefs?.edit()?.run {
            putString(KEY_ACCESS, newAccessToken)
            if (newRefreshToken.isNotBlank()) putString(KEY_REFRESH, newRefreshToken)
            apply()
        }
        Log.d("SessionStore", "Tokens updated")
    }

    fun clearSession() {
        prefs?.edit()?.clear()?.apply()
        Log.d("SessionStore", "Session cleared")
    }

    // ── Read ───────────────────────────────────────────────────────────────

    fun getToken():        String? = prefs?.getString(KEY_ACCESS,  null)
    fun getRefreshToken(): String? = prefs?.getString(KEY_REFRESH, null)
    fun getUid():          String? = prefs?.getString(KEY_UID,     null)
    fun getName():         String? = prefs?.getString(KEY_NAME,    null)
    fun getEmail():        String? = prefs?.getString(KEY_EMAIL,   null)

    fun hasSession():      Boolean = !getToken().isNullOrBlank()
    fun hasRefreshToken(): Boolean = !getRefreshToken().isNullOrBlank()

    // ── Aliases used by UserSessionManager ────────────────────────────────
    fun save(
        token:        String,
        uid:          String,
        name:         String?,
        email:        String?,
        refreshToken: String = ""
    ) = saveSession(
        accessToken  = token,
        uid          = uid,
        name         = name ?: "",
        refreshToken = refreshToken,
        email        = email ?: ""
    )

    fun clear() = clearSession()
}