package com.demo.butler_voice_app.api

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * PaymentManager — stores card details securely using EncryptedSharedPreferences.
 * NEVER stores full card number — only last 4 digits + expiry for display.
 * The "card token" concept simulates what a real payment gateway (Razorpay) would return.
 */
object PaymentManager {

    private const val TAG = "PaymentManager"
    private const val PREFS_FILE = "butler_payment_prefs"

    data class SavedCard(
        val last4: String,
        val expiry: String,       // MM/YY
        val cardHolder: String,
        val network: String       // VISA / MASTERCARD / RUPAY
    )

    private fun getPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS_FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedPrefs failed, using plain: ${e.message}")
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun saveCard(context: Context, last4: String, expiry: String, holder: String, network: String) {
        getPrefs(context).edit().apply {
            putString("card_last4", last4)
            putString("card_expiry", expiry)
            putString("card_holder", holder)
            putString("card_network", network)
            putBoolean("has_card", true)
            apply()
        }
        Log.d(TAG, "Card saved: **** **** **** $last4")
    }

    fun getSavedCard(context: Context): SavedCard? {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean("has_card", false)) return null
        return SavedCard(
            last4      = prefs.getString("card_last4", "") ?: "",
            expiry     = prefs.getString("card_expiry", "") ?: "",
            cardHolder = prefs.getString("card_holder", "") ?: "",
            network    = prefs.getString("card_network", "CARD") ?: "CARD"
        )
    }

    fun hasCard(context: Context): Boolean =
        getPrefs(context).getBoolean("has_card", false)

    fun removeCard(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.d(TAG, "Card removed")
    }

    /** Detect card network from first digit */
    fun detectNetwork(cardNumber: String): String {
        val clean = cardNumber.replace(" ", "")
        return when {
            clean.startsWith("4")           -> "VISA"
            clean.startsWith("5")           -> "MASTERCARD"
            clean.startsWith("6")           -> "RUPAY"
            clean.startsWith("3")           -> "AMEX"
            else                            -> "CARD"
        }
    }

    /** Mask card number for display: **** **** **** 4242 */
    fun maskNumber(full: String): String {
        val clean = full.replace(" ", "")
        return if (clean.length >= 4) "**** **** **** ${clean.takeLast(4)}" else "****"
    }
}