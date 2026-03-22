package com.demo.butler_voice_app.voice

import android.util.Log
import okhttp3.*
import okio.ByteString

class ElevenLabsSTTManager(
    private val apiKey: String
) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun startListening(onText: (String) -> Unit) {

        val request = Request.Builder()
            .url("wss://api.elevenlabs.io/v1/speech-to-text/stream")
            .addHeader("xi-api-key", apiKey)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("STT", "✅ Connected to ElevenLabs")

                // ✅ SEND CONFIG
                val initMessage = """
                    {
                      "config": {
                        "language": "en",
                        "model_id": "scribe_v2"
                      }
                    }
                """.trimIndent()

                webSocket.send(initMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("STT", "Received: $text")
                onText(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("STT", "Error: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("STT", "Closed: $reason")
            }
        })
    }

    fun sendAudio(audioBytes: ByteArray) {
        webSocket?.send(ByteString.of(*audioBytes))
    }

    fun stop() {
        webSocket?.close(1000, "Stopped")
    }
}