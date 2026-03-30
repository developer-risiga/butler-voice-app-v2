package com.demo.butler_voice_app

import android.content.Context

object ProactiveSession {

    // Returns the pending message if launched from notification, null otherwise
    fun consumePendingMessage(context: Context): ProactiveData? {
        val prefs = context.getSharedPreferences("butler_proactive", Context.MODE_PRIVATE)
        val msg   = prefs.getString("pending_message", null) ?: return null
        val prod  = prefs.getString("pending_product", null) ?: return null
        val qty   = prefs.getInt("pending_qty", 1)
        val time  = prefs.getLong("message_time", 0L)

        // Only use if message is less than 2 hours old
        if (System.currentTimeMillis() - time > 2 * 60 * 60 * 1000) {
            prefs.edit().clear().apply()
            return null
        }

        // Consume — clear so it doesn't trigger again
        prefs.edit().clear().apply()
        return ProactiveData(msg, prod, qty)
    }
}

data class ProactiveData(
    val message: String,      // what Butler will say
    val productName: String,  // the product to add to cart
    val quantity: Int         // how much to add
)