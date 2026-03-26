package com.demo.butler_voice_app.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
object SupabaseClient {

    private val httpClient = OkHttpClient()
    const val SUPABASE_URL = "https://dcabhsrchagikwzjmbvj.supabase.co"
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRjYWJoc3JjaGFnaWt3emptYnZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI0MjUwMTQsImV4cCI6MjA4ODAwMTAxNH0.h6v1GefgjWt_hlwzVgqcuH-eYJ1cK-I9RuP48MSdfCs"

    fun rpc(function: String, body: JSONObject): String {
        val url = "$SUPABASE_URL/rest/v1/rpc/$function"
        val request = Request.Builder()
            .url(url)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        return response.body?.string() ?: "[]"
    }

}
