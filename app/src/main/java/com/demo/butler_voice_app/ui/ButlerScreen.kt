package com.demo.butler_voice_app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── STATE ────────────────────────────────────────────────────
sealed class ButlerUiState {
    object Idle : ButlerUiState()
    object Listening : ButlerUiState()
    data class Thinking(val heard: String) : ButlerUiState()
    data class Speaking(val text: String, val isFallback: Boolean = false) : ButlerUiState()
    data class OrderDone(val shortId: String, val total: Double) : ButlerUiState()
    data class Error(val message: String) : ButlerUiState()
}

// ─── COLORS ───────────────────────────────────────────────────
private val OrbIdle     = Color(0xFF444441)
private val OrbListen   = Color(0xFF1D9E75)
private val OrbThink    = Color(0xFF7F77DD)
private val OrbSpeak    = Color(0xFF378ADD)
private val OrbSuccess  = Color(0xFF1D9E75)
private val OrbError    = Color(0xFFE24B4A)
private val BgDark      = Color(0xFF0A0A0A)
private val CardDark    = Color(0xFF1A1A1A)

// ─── MAIN SCREEN ──────────────────────────────────────────────
@Composable
fun ButlerScreen(state: ButlerUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {

            // App label
            Text(
                text = "BUTLER",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF444441),
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(48.dp))

            // Animated orb
            PulsingOrb(state)

            Spacer(Modifier.height(40.dp))

            // Status text
            Text(
                text = stateLabel(state),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Heard transcript
            if (state is ButlerUiState.Thinking) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\"${state.heard}\"",
                    fontSize = 14.sp,
                    color = Color(0xFF888780),
                    textAlign = TextAlign.Center
                )
            }

            // Speaking text
            if (state is ButlerUiState.Speaking) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.text,
                    fontSize = 15.sp,
                    color = Color(0xFFCCCCCC),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                if (state.isFallback) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Using backup voice",
                        fontSize = 11.sp,
                        color = Color(0xFF444441)
                    )
                }
            }

            // Order success card
            if (state is ButlerUiState.OrderDone) {
                Spacer(Modifier.height(24.dp))
                OrderSuccessCard(state.shortId, state.total)
            }

            // Error
            if (state is ButlerUiState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.message,
                    fontSize = 14.sp,
                    color = OrbError,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── PULSING ORB ──────────────────────────────────────────────
@Composable
fun PulsingOrb(state: ButlerUiState) {
    val orbColor = when (state) {
        is ButlerUiState.Idle      -> OrbIdle
        is ButlerUiState.Listening -> OrbListen
        is ButlerUiState.Thinking  -> OrbThink
        is ButlerUiState.Speaking  -> OrbSpeak
        is ButlerUiState.OrderDone -> OrbSuccess
        is ButlerUiState.Error     -> OrbError
    }

    val pulseTarget = when (state) {
        is ButlerUiState.Listening -> 1.3f
        is ButlerUiState.Speaking  -> 1.15f
        is ButlerUiState.Thinking  -> 1.08f
        else -> 1.02f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = pulseTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    is ButlerUiState.Listening -> 600
                    is ButlerUiState.Speaking  -> 800
                    else -> 1200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .background(orbColor.copy(alpha = 0.12f), CircleShape)
        )
        // Middle ring
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(scale * 0.92f)
                .background(orbColor.copy(alpha = 0.2f), CircleShape)
        )
        // Core orb
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(orbColor, CircleShape)
        )
    }
}

// ─── ORDER SUCCESS CARD ───────────────────────────────────────
@Composable
fun OrderSuccessCard(shortId: String, total: Double) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F2E22),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Order confirmed",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF5DCAA5)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "#$shortId",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D9E75)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "₹%.2f".format(total),
                fontSize = 16.sp,
                color = Color(0xFF9FE1CB)
            )
        }
    }
}

// ─── HELPERS ──────────────────────────────────────────────────
private fun stateLabel(state: ButlerUiState): String = when (state) {
    is ButlerUiState.Idle      -> "Say  \"Hey Butler\""
    is ButlerUiState.Listening -> "Listening..."
    is ButlerUiState.Thinking  -> "Thinking..."
    is ButlerUiState.Speaking  -> ""
    is ButlerUiState.OrderDone -> "Order placed!"
    is ButlerUiState.Error     -> "Something went wrong"
}
