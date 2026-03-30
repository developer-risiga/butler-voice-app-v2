package com.demo.butler_voice_app.ai

// MoodAdapter — changes Butler's behavior based on detected mood
object MoodAdapter {

    // How Butler speaks differently per mood
    fun adaptGreeting(mood: UserMood, name: String, lang: String): String {
        return when (mood) {
            UserMood.FRUSTRATED -> when {
                lang.startsWith("hi") -> "$name bhai, batao kya chahiye. seedha bolein."
                else -> "Go ahead $name, what do you need?"
            }
            UserMood.RUSHED -> when {
                lang.startsWith("hi") -> "haan $name, jaldi batao."
                else -> "Sure $name, quick — what do you need?"
            }
            UserMood.TIRED -> when {
                lang.startsWith("hi") -> "$name bhai, le aata hoon usual order?"
                else -> "Want me to reorder your usual, $name?"
            }
            UserMood.CALM -> when {
                lang.startsWith("hi") -> "haan $name, kya chahiye?"
                else -> "What can I get you, $name?"
            }
        }
    }

    // Should Butler skip the product options list and go direct?
    fun shouldSkipOptions(mood: UserMood): Boolean =
        mood == UserMood.FRUSTRATED || mood == UserMood.RUSHED

    // Should Butler skip the quantity confirmation step?
    fun shouldSkipQuantityConfirm(mood: UserMood): Boolean =
        mood == UserMood.RUSHED

    // Should Butler skip the cart summary and go straight to payment?
    fun shouldSkipCartSummary(mood: UserMood): Boolean =
        mood == UserMood.RUSHED && true

    // Should Butler proactively suggest the last order?
    fun shouldSuggestReorder(mood: UserMood): Boolean =
        mood == UserMood.TIRED

    // After a FRUSTRATED mood — acknowledge it subtly
    fun getFrustrationAck(lang: String): String = when {
        lang.startsWith("hi") -> "theek hai, abhi karta hoon."
        else -> "on it right now."
    }

    // Shorter product announcement for rushed users
    fun buildRushedProductAnnouncement(
        productName: String,
        price: Double,
        storeName: String,
        lang: String
    ): String {
        val p = price.toInt()
        return when {
            lang.startsWith("hi") -> "$productName — $p rupees. loon?"
            else -> "$productName — $p rupees. Shall I add it?"
        }
    }
}