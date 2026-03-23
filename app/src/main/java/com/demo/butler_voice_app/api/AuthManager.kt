package com.demo.butler_voice_app.api

object AuthManager {
    fun currentUserId(): String? = UserSessionManager.currentUserId()
}
