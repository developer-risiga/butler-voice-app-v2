package com.demo.butler_voice_app.api

import android.content.Context
import android.util.Log

/**
 * Persistent session store — SharedPreferences backed.
 *
 * LANGUAGE MEMORY FIX:
 * Butler always resets to en-IN after wake word. This means Roy (Hindi speaker)
 * gets "Yes Roy, go ahead." every time even though he always speaks Hindi.
 *
 * Fix: Save the user's detected language after each session.
 * Next session, restore it so the greeting is in the right language.
 *
 * saveUserLanguage() — called when language switches (in MainActivity STT handler)
 * getUserLanguage()  — called in proceedAfterIdentification to set greeting language
 */
object SessionStore {

    private const val PREFS        = "butler_session_v2"
    private const val KEY_ACCESS   = "access_token"
    private const val KEY_REFRESH  = "refresh_token"
    private const val KEY_UID      = "uid"
    private const val KEY_NAME     = "name"
    private const val KEY_EMAIL    = "email"

    // Language preference stored per user ID
    // Key format: "lang_pref_<userId>"
    private const val KEY_LANG_PREFIX = "lang_pref_"

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.d("SessionStore", "Init. Has session: ${hasSession()}")
    }

    // ── Write ──────────────────────────────────────────────────────────────

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

    fun updateTokens(newAccessToken: String, newRefreshToken: String = "") {
        prefs?.edit()?.run {
            putString(KEY_ACCESS, newAccessToken)
            if (newRefreshToken.isNotBlank()) putString(KEY_REFRESH, newRefreshToken)
            apply()
        }
        Log.d("SessionStore", "Tokens updated")
    }

    fun clearSession() {
        // Keep language preferences when clearing session — they are per-user
        // and should survive logout/login cycles
        val langKeys = prefs?.all?.keys?.filter { it.startsWith(KEY_LANG_PREFIX) } ?: emptyList()
        val langValues = langKeys.associateWith { prefs?.getString(it, null) }

        prefs?.edit()?.clear()?.apply()

        // Restore language preferences
        prefs?.edit()?.apply {
            langValues.forEach { (key, value) ->
                if (value != null) putString(key, value)
            }
            apply()
        }

        Log.d("SessionStore", "Session cleared (language preferences retained)")
    }

    // ── Language preference ────────────────────────────────────────────────
    //
    // Called from MainActivity when a language switch is detected.
    // Only saves confirmed Indic languages (not "en" — that's the default).
    //
    // Usage in MainActivity STT handler:
    //   if (langSwitched) {
    //       UserSessionManager.currentUserId()?.let { uid ->
    //           val lang = SessionLanguageManager.ttsLanguage  // e.g. "hi"
    //           if (lang != "en") SessionStore.saveUserLanguage(uid, lang)
    //       }
    //   }
    fun saveUserLanguage(userId: String, lang: String) {
        if (userId.isBlank() || lang.isBlank() || lang == "en") return
        prefs?.edit()?.putString("$KEY_LANG_PREFIX$userId", lang)?.apply()
        Log.d("SessionStore", "Language preference saved: userId=$userId lang=$lang")
    }

    // Called from MainActivity.proceedAfterIdentification() to restore greeting language.
    // Returns "en" if no preference stored (first-time user or English speaker).
    fun getUserLanguage(userId: String): String {
        if (userId.isBlank()) return "en"
        return prefs?.getString("$KEY_LANG_PREFIX$userId", "en") ?: "en"
    }

    fun clearLanguagePreference(userId: String) {
        prefs?.edit()?.remove("$KEY_LANG_PREFIX$userId")?.apply()
    }

    // ── Read ───────────────────────────────────────────────────────────────

    fun getToken():        String? = prefs?.getString(KEY_ACCESS,  null)
    fun getRefreshToken(): String? = prefs?.getString(KEY_REFRESH, null)
    fun getUid():          String? = prefs?.getString(KEY_UID,     null)
    fun getName():         String? = prefs?.getString(KEY_NAME,    null)
    fun getEmail():        String? = prefs?.getString(KEY_EMAIL,   null)

    fun hasSession():      Boolean = !getToken().isNullOrBlank()
    fun hasRefreshToken(): Boolean = !getRefreshToken().isNullOrBlank()

    // ── Aliases ────────────────────────────────────────────────────────────

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