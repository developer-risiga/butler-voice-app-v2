package com.demo.butler_voice_app.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class FamilyMember(
    val id: String,               // unique per member, e.g. "subhash", "priya"
    val displayName: String,      // "Subhash", "Priya Didi"
    val emoji: String,            // shown on screen: "👨", "👩", "👦"
    val userId: String,           // Supabase user ID — their account
    val language: String = "en",  // preferred language
    val lastOrderSummary: String = "" // "last ordered rice, oil"
)

object FamilyProfileManager {

    private const val TAG   = "FamilyProfiles"
    private const val PREFS = "butler_family"
    private const val KEY   = "members"

    // In-memory list for the session
    private var members: MutableList<FamilyMember> = mutableListOf()
    var activeProfile: FamilyMember? = null
        private set

    // ── Load saved family members from SharedPreferences ─────────────────────
    fun load(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json  = prefs.getString(KEY, null) ?: return
            val arr   = JSONArray(json)
            members.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                members.add(FamilyMember(
                    id               = obj.getString("id"),
                    displayName      = obj.getString("displayName"),
                    emoji            = obj.optString("emoji", "👤"),
                    userId           = obj.getString("userId"),
                    language         = obj.optString("language", "en"),
                    lastOrderSummary = obj.optString("lastOrderSummary", "")
                ))
            }
            Log.d(TAG, "Loaded ${members.size} family members")
        } catch (e: Exception) {
            Log.e(TAG, "Load failed: ${e.message}")
        }
    }

    // ── Save to SharedPreferences ────────────────────────────────────────────
    fun save(context: Context) {
        try {
            val arr = JSONArray()
            members.forEach { m ->
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("displayName", m.displayName)
                    put("emoji", m.emoji)
                    put("userId", m.userId)
                    put("language", m.language)
                    put("lastOrderSummary", m.lastOrderSummary)
                })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${e.message}")
        }
    }

    fun getMembers(): List<FamilyMember> = members.toList()

    fun hasFamilyProfiles(): Boolean = members.size > 1

    // ── Add a new member (called after signup) ───────────────────────────────
    fun addMember(context: Context, member: FamilyMember) {
        members.removeAll { it.id == member.id }
        members.add(member)
        save(context)
        Log.d(TAG, "Added family member: ${member.displayName}")
    }

    // ── Detect who is speaking from a voice transcript ──────────────────────
    // Returns the matched member or null if not recognised
    fun detectSpeaker(transcript: String): FamilyMember? {
        val lower = transcript.lowercase().trim()
        return members.firstOrNull { member ->
            val name  = member.displayName.lowercase()
            val id    = member.id.lowercase()
            // Match "main subhash hoon", "subhash", "yeh subhash hai", etc.
            lower.contains(name) || lower.contains(id) ||
                    lower.contains("main $name") || lower.contains("mera naam $name") ||
                    lower.contains("i am $name") || lower.contains("this is $name")
        }
    }

    // ── Activate a profile for this session ──────────────────────────────────
    fun setActive(member: FamilyMember) {
        activeProfile = member
        Log.d(TAG, "Active profile: ${member.displayName}")
    }

    fun clearActive() { activeProfile = null }

    // ── Build the "who is speaking" question ─────────────────────────────────
    fun buildWhoQuestion(lang: String): String {
        val names = members.joinToString(", ") { it.displayName }
        return when {
            lang.startsWith("hi") -> "kaun bol raha hai? $names mein se?"
            else                  -> "who is it? $names?"
        }
    }

    // ── Build greeting for identified member ─────────────────────────────────
    fun buildPersonalGreeting(member: FamilyMember, lang: String): String {
        val name = member.displayName.split(" ").first()
        val last = member.lastOrderSummary
        return when {
            lang.startsWith("hi") && last.isNotBlank() ->
                "haan $name bhai! last time $last tha. kya chahiye aaj?"
            lang.startsWith("hi") ->
                "haan $name! kya chahiye?"
            last.isNotBlank() ->
                "hey $name! last time you got $last. what do you need?"
            else ->
                "hey $name! what can I get you?"
        }
    }

    // ── Update last order summary after order placed ─────────────────────────
    fun updateLastOrder(context: Context, memberId: String, orderSummary: String) {
        val idx = members.indexOfFirst { it.id == memberId }
        if (idx >= 0) {
            members[idx] = members[idx].copy(lastOrderSummary = orderSummary)
            save(context)
        }
    }

    // ── Register the current logged-in user as a family member if not already ─
    fun ensureCurrentUserRegistered(context: Context, profile: UserProfile) {
        val userId = profile.id
        if (members.none { it.userId == userId }) {
            val name   = profile.full_name?.split(" ")?.first() ?: "User"
            val member = FamilyMember(
                id          = name.lowercase(),
                displayName = name,
                emoji       = "👤",
                userId      = userId
            )
            addMember(context, member)
        }
    }
}