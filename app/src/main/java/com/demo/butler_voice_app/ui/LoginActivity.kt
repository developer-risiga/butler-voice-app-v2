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
private val LoginBgDeep      = Color(0xFF070B14)
private val LoginBgCard      = Color(0xFF0E1520)
private val LoginAccentTeal  = Color(0xFF00D4AA)
private val LoginAccentBlue  = Color(0xFF4F9EFF)
private val LoginAccentRed   = Color(0xFFFF5C5C)
private val LoginTextPrimary = Color(0xFFF0F4FF)
private val LoginTextSecond  = Color(0xFF8899BB)
private val LoginBorderColor = Color(0xFF1E2D45)

// NOTE: All constants (RESULT_MANUAL_LOGIN, RESULT_USE_VOICE, EXTRA_EMAIL, etc.)
// are defined in Constants.kt — do NOT redeclare them here.

/**
 * LoginActivity — legacy auth screen (kept for compatibility).
 * New installs use AuthActivity instead.
 */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            Box(modifier = Modifier.fillMaxSize().background(LoginBgDeep)) {
                LoginScreen(
                    onManualAuth = { email, password, isNew ->
                        activity.setResult(RESULT_MANUAL_LOGIN, Intent().apply {
                            putExtra(EXTRA_EMAIL, email)
                            putExtra(EXTRA_PASSWORD, password)
                            putExtra(EXTRA_IS_NEW_USER, isNew)
                        })
                        activity.finish()
                    },
                    onUseVoice = {
                        activity.setResult(RESULT_USE_VOICE)
                        activity.finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    onManualAuth: (email: String, password: String, isNew: Boolean) -> Unit,
    onUseVoice: () -> Unit
) {
    var isNewUser by remember { mutableStateOf(true) }
    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var name      by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf("") }

    val inf = rememberInfiniteTransition(label = "login_bg")
    val glowAlpha by inf.animateFloat(
        initialValue = 0.03f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LoginAccentTeal.copy(alpha = glowAlpha), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.2f),
                    radius = size.width * 0.7f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier.size(72.dp).background(
                    brush = Brush.radialGradient(listOf(LoginAccentTeal.copy(0.9f), Color(0xFF004A37))),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 28.sp, color = Color.White) }

            Spacer(Modifier.height(16.dp))
            Text("Butler", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = LoginTextPrimary, letterSpacing = 1.sp)
            Text("Voice-first grocery ordering", fontSize = 13.sp, color = LoginTextSecond)
            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(LoginBgCard).border(1.dp, LoginBorderColor, RoundedCornerShape(12.dp)).padding(4.dp)
            ) {
                Row {
                    LoginTabOption("New Customer", isNewUser, { isNewUser = true; error = "" }, Modifier.weight(1f))
                    LoginTabOption("Returning",    !isNewUser, { isNewUser = false; error = "" }, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(visible = isNewUser) {
                Column {
                    LoginField("Full Name", name, "Your name", KeyboardType.Text) { name = it }
                    Spacer(Modifier.height(12.dp))
                }
            }

            LoginField("Email Address", email, "you@example.com", KeyboardType.Email) { email = it.trim() }
            Spacer(Modifier.height(12.dp))

            Column {
                Text("Password", fontSize = 12.sp, color = LoginTextSecond, modifier = Modifier.padding(bottom = 6.dp))
                BasicTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(fontSize = 16.sp, color = LoginTextPrimary),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(LoginBgCard).border(1.dp, LoginBorderColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    if (password.isEmpty()) Text("Min 8 characters", fontSize = 15.sp, color = LoginTextSecond.copy(0.5f))
                                    inner()
                                }
                                Text(if (showPass) "Hide" else "Show", fontSize = 12.sp, color = LoginAccentBlue, modifier = Modifier.clickable { showPass = !showPass })
                            }
                        }
                    }
                )
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, fontSize = 13.sp, color = LoginAccentRed, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))

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
                colors = ButtonDefaults.buttonColors(containerColor = LoginAccentTeal)
            ) {
                Text(if (isNewUser) "Create Account" else "Log In", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Divider(modifier = Modifier.weight(1f), color = LoginBorderColor)
                Text(" or ", fontSize = 12.sp, color = LoginTextSecond)
                Divider(modifier = Modifier.weight(1f), color = LoginBorderColor)
            }
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onUseVoice,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, LoginAccentBlue)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Use Voice Instead", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LoginAccentBlue)
                        Text("Say your details to Butler", fontSize = 11.sp, color = LoginTextSecond)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LoginTabOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) LoginAccentTeal.copy(0.15f) else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) LoginAccentTeal.copy(0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) LoginAccentTeal else LoginTextSecond)
    }
}

@Composable
private fun LoginField(label: String, value: String, placeholder: String, keyboardType: KeyboardType, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = LoginTextSecond, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 16.sp, color = LoginTextPrimary),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(LoginBgCard).border(1.dp, LoginBorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = LoginTextSecond.copy(0.5f))
                    inner()
                }
            }
        )
    }
}