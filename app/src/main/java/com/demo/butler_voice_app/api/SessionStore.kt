package com.demo.butler_voice_app.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object SessionStore {

    private const val PREFS         = "butler_session"
    private const val KEY_TOKEN     = "access_token"
    private const val KEY_UID       = "user_id"
    private const val KEY_NAME      = "full_name"
    private const val KEY_EMAIL     = "email"
    // ── BUG 7 FIX: store refresh_token so onWakeWordDetected() can silently
    // renew an expired access token instead of forcing full re-login.
    private const val KEY_REFRESH   = "refresh_token"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.d("SessionStore", "Init. Has session: ${hasSession()}")
    }

    // ── BUG 7 FIX: added optional refreshToken param (default = "")
    // All existing callers that omit it continue to compile unchanged.
    fun save(token: String, uid: String, name: String?, email: String?, refreshToken: String = "") {
        prefs.edit()
            .putString(KEY_TOKEN,   token)
            .putString(KEY_UID,     uid)
            .putString(KEY_NAME,    name  ?: "")
            .putString(KEY_EMAIL,   email ?: "")
            .putString(KEY_REFRESH, refreshToken)
            .apply()
        Log.d("SessionStore", "Session saved for uid=$uid name=$name")
    }

    // ── BUG 7 FIX: called after a successful silent token refresh.
    // Updates only the tokens, leaves uid / name / email untouched.
    fun updateTokens(newAccessToken: String, newRefreshToken: String = "") {
        prefs.edit()
            .putString(KEY_TOKEN, newAccessToken)
            .also { if (newRefreshToken.isNotBlank()) it.putString(KEY_REFRESH, newRefreshToken) }
            .apply()
        Log.d("SessionStore", "Tokens updated")
    }

    fun clear() {
        prefs.edit().clear().apply()
        Log.d("SessionStore", "Session cleared")
    }

    // ── Existing getters — unchanged ──────────────────────────────────────
    fun getToken():        String? = prefs.getString(KEY_TOKEN,   null)?.takeIf { it.isNotBlank() }
    fun getUid():          String? = prefs.getString(KEY_UID,     null)?.takeIf { it.isNotBlank() }
    fun getName():         String? = prefs.getString(KEY_NAME,    null)?.takeIf { it.isNotBlank() }
    fun getEmail():        String? = prefs.getString(KEY_EMAIL,   null)?.takeIf { it.isNotBlank() }
    fun hasSession():      Boolean = !getToken().isNullOrBlank() && !getUid().isNullOrBlank()

    // ── BUG 7 FIX: new getter + guard ─────────────────────────────────────
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }
    fun hasRefreshToken(): Boolean = !getRefreshToken().isNullOrBlank()
}