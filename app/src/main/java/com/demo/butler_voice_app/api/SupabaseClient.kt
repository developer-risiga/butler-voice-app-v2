package com.demo.butler_voice_app.api

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://dcabhsrchagikwzjmbvj.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRjYWJoc3JjaGFnaWt3emptYnZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI0MjUwMTQsImV4cCI6MjA4ODAwMTAxNH0.h6v1GefgjWt_hlwzVgqcuH-eYJ1cK-I9RuP48MSdfCs"
    ) {
        install(Postgrest)
        install(Auth)
    }
}
