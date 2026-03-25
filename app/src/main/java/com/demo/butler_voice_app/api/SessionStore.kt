package com.demo.butler_voice_app.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persists user session across app restarts.
 * CREATE THIS FILE at: app/src/main/java/com/demo/butler_voice_app/api/SessionStore.kt
 */
object SessionStore {

    private const val PREFS     = "butler_session"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_UID   = "user_id"
    private const val KEY_NAME  = "full_name"
    private const val KEY_EMAIL = "email"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.d("SessionStore", "Init. Has session: ${hasSession()}")
    }

    fun save(token: String, uid: String, name: String?, email: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_UID,   uid)
            .putString(KEY_NAME,  name  ?: "")
            .putString(KEY_EMAIL, email ?: "")
            .apply()
        Log.d("SessionStore", "Session saved for uid=$uid name=$name")
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun getUid():   String? = prefs.getString(KEY_UID,   null)?.takeIf { it.isNotBlank() }
    fun getName():  String? = prefs.getString(KEY_NAME,  null)?.takeIf { it.isNotBlank() }
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.edit().clear().apply()
        Log.d("SessionStore", "Session cleared")
    }

    fun hasSession(): Boolean = !getToken().isNullOrBlank() && !getUid().isNullOrBlank()
}
