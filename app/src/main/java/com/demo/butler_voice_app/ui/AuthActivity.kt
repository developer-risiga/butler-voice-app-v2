package com.demo.butler_voice_app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

// ── Constants ─────────────────────────────────────────────────────────────────
const val RESULT_MANUAL_LOGIN  = 200
const val RESULT_USE_VOICE     = 201
const val RESULT_GOOGLE_AUTH   = 202

const val EXTRA_EMAIL          = "extra_email"
const val EXTRA_PASSWORD       = "extra_password"
const val EXTRA_NAME           = "extra_name"
const val EXTRA_IS_NEW_USER    = "extra_is_new"
const val EXTRA_ORDER_TOTAL    = "extra_order_total"
const val EXTRA_ORDER_SUMMARY  = "extra_order_summary"
const val EXTRA_GOOGLE_TOKEN   = "extra_google_token"
const val EXTRA_GOOGLE_EMAIL   = "extra_google_email"
const val EXTRA_GOOGLE_NAME    = "extra_google_name"

// ── Colors ────────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF070B14)
private val BgCard      = Color(0xFF0E1520)
private val BgCardAlt   = Color(0xFF111A28)
private val AccentTeal  = Color(0xFF00D4AA)
private val AccentBlue  = Color(0xFF4F9EFF)
private val AccentAmber = Color(0xFFFFB830)
private val AccentRed   = Color(0xFFFF4444)
private val AccentGoogle= Color(0xFF4285F4)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecond  = Color(0xFF8899BB)
private val BorderColor = Color(0xFF1E2D45)

/**
 * AuthActivity — Beautiful hybrid auth screen.
 * Offers: Google Sign-In · Manual email/password · Voice signup
 */
class AuthActivity : ComponentActivity() {

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task  = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val acct  = task.getResult(ApiException::class.java)
                val token = acct?.idToken ?: return@registerForActivityResult
                setResult(RESULT_GOOGLE_AUTH, Intent().apply {
                    putExtra(EXTRA_GOOGLE_TOKEN, token)
                    putExtra(EXTRA_GOOGLE_EMAIL, acct.email ?: "")
                    putExtra(EXTRA_GOOGLE_NAME, acct.displayName ?: "")
                })
                finish()
            } catch (e: ApiException) {
                android.util.Log.e("Auth", "Google sign-in failed: ${e.statusCode}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {
                AuthScreen(
                    onGoogleSignIn = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken("YOUR_WEB_CLIENT_ID_HERE")
                            .requestEmail().requestProfile().build()
                        googleLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
                    },
                    onManualAuth = { email, password, name, isNew ->
                        setResult(RESULT_MANUAL_LOGIN, Intent().apply {
                            putExtra(EXTRA_EMAIL, email)
                            putExtra(EXTRA_PASSWORD, password)
                            putExtra(EXTRA_NAME, name)
                            putExtra(EXTRA_IS_NEW_USER, isNew)
                        })
                        finish()
                    },
                    onVoice = {
                        setResult(RESULT_USE_VOICE)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    onGoogleSignIn: () -> Unit,
    onManualAuth: (email: String, password: String, name: String, isNew: Boolean) -> Unit,
    onVoice: () -> Unit
) {
    var isNew       by remember { mutableStateOf(true) }
    var name        by remember { mutableStateOf("") }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showPass    by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }

    val inf = rememberInfiniteTransition(label = "auth")
    val glowA by inf.animateFloat(0.03f, 0.10f,
        infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "ga")
    val glowB by inf.animateFloat(0.02f, 0.07f,
        infiniteRepeatable(tween(4000, delayMillis = 1000, easing = EaseInOutSine), RepeatMode.Reverse), label = "gb")

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated background glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AccentTeal.copy(glowA), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.12f),
                    radius = size.width * 0.75f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AccentBlue.copy(glowB), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.width * 0.55f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))

            // ── Logo ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.size(76.dp).background(
                    brush = Brush.radialGradient(listOf(AccentTeal.copy(0.9f), Color(0xFF004A37))),
                    shape = CircleShape
                ),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 30.sp, color = Color.White) }

            Spacer(Modifier.height(14.dp))

            Text("Butler", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = TextPrimary, letterSpacing = 1.sp)
            Text("Voice-first grocery ordering", fontSize = 13.sp, color = TextSecond)

            Spacer(Modifier.height(10.dp))

            // USP row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UspPill("🎤 Voice AI")
                UspPill("⚡ 10-min delivery")
                UspPill("🔒 Secure")
            }

            Spacer(Modifier.height(28.dp))

            // ── Google Sign-In ────────────────────────────────────────────────
            Button(
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(22.dp).background(AccentGoogle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("G", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                }
            }

            Spacer(Modifier.height(16.dp))

            DividerRow("or continue with email")

            Spacer(Modifier.height(16.dp))

            // ── Tab bar ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(BgCard).border(1.dp, BorderColor, RoundedCornerShape(12.dp)).padding(4.dp)
            ) {
                Row {
                    AuthTab("New Account", isNew, Modifier.weight(1f)) { isNew = true; errorMsg = "" }
                    AuthTab("Sign In", !isNew, Modifier.weight(1f)) { isNew = false; errorMsg = "" }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Name (new user only) ─────────────────────────────────────────
            AnimatedVisibility(isNew, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    InputField("Full Name", name, "Your full name", KeyboardType.Text) { name = it }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Email ────────────────────────────────────────────────────────
            InputField("Email", email, "you@gmail.com", KeyboardType.Email) {
                email = it.trim().lowercase()
            }

            Spacer(Modifier.height(12.dp))

            // ── Password ─────────────────────────────────────────────────────
            PasswordField("Password", password, showPass, { password = it }, { showPass = !showPass })

            // ── Confirm password (new only) ───────────────────────────────────
            AnimatedVisibility(isNew) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    PasswordField("Confirm Password", confirmPass, showPass, { confirmPass = it }, { showPass = !showPass })
                }
            }

            // ── Password strength bar ─────────────────────────────────────────
            AnimatedVisibility(isNew && password.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    PasswordStrength(password)
                }
            }

            // ── Error message ─────────────────────────────────────────────────
            AnimatedVisibility(errorMsg.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(AccentRed.copy(0.12f)).border(1.dp, AccentRed.copy(0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(errorMsg, fontSize = 13.sp, color = AccentRed, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Security badges ───────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityBadge("🔒 AES-256")
                SecurityBadge("🛡 No data sale")
                SecurityBadge("✓ GDPR")
            }

            Spacer(Modifier.height(18.dp))

            // ── Primary CTA ───────────────────────────────────────────────────
            Button(
                onClick = {
                    errorMsg = ""
                    when {
                        isNew && name.isBlank() ->
                            errorMsg = "Please enter your full name"
                        !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                            errorMsg = "Please enter a valid email"
                        password.length < 6 ->
                            errorMsg = "Password must be at least 6 characters"
                        isNew && password != confirmPass ->
                            errorMsg = "Passwords don't match"
                        else -> onManualAuth(email, password, name.trim(), isNew)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(if (isNew) "Create Account" else "Sign In", fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.Black)
                }
            }

            Spacer(Modifier.height(14.dp))

            DividerRow("prefer voice?")

            Spacer(Modifier.height(14.dp))

            // ── Voice Signup ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = onVoice,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, AccentBlue)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Sign Up with Voice", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        Text("Just speak — Butler does the rest", fontSize = 11.sp, color = TextSecond)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "By continuing you agree to Butler's Terms and Privacy Policy. " +
                        "Your data is encrypted and never sold.",
                fontSize = 10.sp, color = TextSecond.copy(0.7f),
                textAlign = TextAlign.Center, lineHeight = 15.sp
            )

            Spacer(Modifier.height(36.dp))
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun UspPill(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, fontSize = 11.sp, color = TextSecond, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun DividerRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Divider(modifier = Modifier.weight(1f), color = BorderColor)
        Text("  $label  ", fontSize = 11.sp, color = TextSecond)
        Divider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}

@Composable
private fun AuthTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentTeal.copy(0.15f) else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) AccentTeal.copy(0.4f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AccentTeal else TextSecond)
    }
}

@Composable
private fun InputField(
    label: String, value: String, placeholder: String,
    keyboardType: KeyboardType, onValueChange: (String) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecond, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(BgCard).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = TextSecond.copy(0.5f))
                    inner()
                }
            }
        )
    }
}

@Composable
private fun PasswordField(
    label: String, value: String, show: Boolean,
    onValueChange: (String) -> Unit, onToggle: () -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecond, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(BgCard).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) Text("Min 6 characters", fontSize = 15.sp, color = TextSecond.copy(0.5f))
                            inner()
                        }
                        Text(if (show) "Hide" else "Show", fontSize = 12.sp, color = AccentBlue,
                            modifier = Modifier.clickable(onClick = onToggle))
                    }
                }
            }
        )
    }
}

@Composable
private fun PasswordStrength(password: String) {
    val strength = when {
        password.length < 6 -> 0
        password.length >= 8 && password.any { it.isDigit() } &&
                password.any { it.isUpperCase() } && password.any { !it.isLetterOrDigit() } -> 4
        password.length >= 8 && password.any { it.isDigit() } && password.any { it.isUpperCase() } -> 3
        password.length >= 8 && password.any { it.isDigit() } -> 2
        else -> 1
    }
    val (label, color) = when (strength) {
        0 -> "Too short" to AccentRed
        1 -> "Weak" to AccentRed
        2 -> "Fair" to AccentAmber
        3 -> "Good" to AccentBlue
        else -> "Strong 💪" to AccentTeal
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..4).forEach { i ->
                Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= strength) color else BorderColor))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun SecurityBadge(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BgCardAlt)
            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 10.sp, color = TextSecond) }
}