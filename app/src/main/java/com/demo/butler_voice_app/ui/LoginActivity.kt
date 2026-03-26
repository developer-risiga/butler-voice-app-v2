package com.demo.butler_voice_app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

// ── Colors ────────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF070B14)
private val BgCard      = Color(0xFF0E1520)
private val AccentTeal  = Color(0xFF00D4AA)
private val AccentBlue  = Color(0xFF4F9EFF)
private val AccentAmber = Color(0xFFFFB830)
private val AccentRed   = Color(0xFFFF5C5C)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecond  = Color(0xFF8899BB)
private val BorderColor = Color(0xFF1E2D45)

const val RESULT_MANUAL_LOGIN  = 200
const val RESULT_USE_VOICE     = 201
const val EXTRA_EMAIL          = "email"
const val EXTRA_PASSWORD       = "password"
const val EXTRA_IS_NEW_USER    = "is_new_user"

/**
 * LoginActivity — shown when user wants to sign up or log in manually.
 * Also has a "Use Voice Instead" button that returns to the voice flow.
 */
class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep)
            ) {
                LoginScreen(
                    onManualAuth = { email, password, isNew ->
                        setResult(RESULT_MANUAL_LOGIN, Intent().apply {
                            putExtra(EXTRA_EMAIL, email)
                            putExtra(EXTRA_PASSWORD, password)
                            putExtra(EXTRA_IS_NEW_USER, isNew)
                        })
                        finish()
                    },
                    onUseVoice = {
                        setResult(RESULT_USE_VOICE)
                        finish()
                    }
                )
            }
        }
    }
}

// ── Login Screen ──────────────────────────────────────────────────────────────

@Composable
private fun LoginScreen(
    onManualAuth: (email: String, password: String, isNew: Boolean) -> Unit,
    onUseVoice: () -> Unit
) {
    var isNewUser  by remember { mutableStateOf(true) }
    var email      by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var name       by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf("") }

    val inf = rememberInfiniteTransition(label = "login_bg")
    val glowAlpha by inf.animateFloat(
        initialValue = 0.03f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentTeal.copy(alpha = glowAlpha), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.2f),
                    radius = size.width * 0.7f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Logo orb
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(AccentTeal.copy(0.9f), Color(0xFF004A37))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", fontSize = 28.sp, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Butler",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Text(
                "Voice-first grocery ordering",
                fontSize = 13.sp,
                color = TextSecond
            )

            Spacer(Modifier.height(32.dp))

            // Tab selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Row {
                    TabOption(
                        text = "New Customer",
                        selected = isNewUser,
                        onClick = { isNewUser = true; error = "" },
                        modifier = Modifier.weight(1f)
                    )
                    TabOption(
                        text = "Returning",
                        selected = !isNewUser,
                        onClick = { isNewUser = false; error = "" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Form fields
            AnimatedVisibility(visible = isNewUser) {
                Column {
                    AuthField(
                        label = "Full Name",
                        value = name,
                        placeholder = "Your name",
                        keyboardType = KeyboardType.Text,
                        onValueChange = { name = it }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            AuthField(
                label = "Email Address",
                value = email,
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                onValueChange = { email = it.trim() }
            )

            Spacer(Modifier.height(12.dp))

            // Password field
            Column {
                Text("Password", fontSize = 12.sp, color = TextSecond, modifier = Modifier.padding(bottom = 6.dp))
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgCard)
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (password.isEmpty()) {
                                        Text("Min 8 characters", fontSize = 15.sp, color = TextSecond.copy(0.5f))
                                    }
                                    inner()
                                }
                                Text(
                                    if (showPass) "Hide" else "Show",
                                    fontSize = 12.sp,
                                    color = AccentBlue,
                                    modifier = Modifier.clickable { showPass = !showPass }
                                )
                            }
                        }
                    }
                )
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, fontSize = 13.sp, color = AccentRed, textAlign = TextAlign.Center)
            }

            if (isNewUser) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Your data is encrypted and never shared",
                        fontSize = 11.sp,
                        color = TextSecond
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Primary button
            Button(
                onClick = {
                    error = ""
                    when {
                        isNewUser && name.isBlank() -> error = "Please enter your name"
                        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> error = "Enter a valid email address"
                        password.length < 8 -> error = "Password must be at least 8 characters"
                        else -> onManualAuth(email, password, isNewUser)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
            ) {
                Text(
                    if (isNewUser) "Create Account" else "Log In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(16.dp))

            // Divider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Divider(modifier = Modifier.weight(1f), color = BorderColor)
                Text("  or  ", fontSize = 12.sp, color = TextSecond)
                Divider(modifier = Modifier.weight(1f), color = BorderColor)
            }

            Spacer(Modifier.height(16.dp))

            // Voice signup button
            OutlinedButton(
                onClick = onUseVoice,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, AccentBlue)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Use Voice Instead",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                        Text(
                            "Say your details to Butler",
                            fontSize = 11.sp,
                            color = TextSecond
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Privacy note
            Text(
                "By continuing, you agree to Butler's Terms of Service and Privacy Policy. We use bank-grade AES-256 encryption to protect your data.",
                fontSize = 11.sp,
                color = TextSecond,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TabOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentTeal.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) AccentTeal.copy(0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AccentTeal else TextSecond
        )
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecond, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = TextSecond.copy(0.5f))
                    }
                    inner()
                }
            }
        )
    }
}