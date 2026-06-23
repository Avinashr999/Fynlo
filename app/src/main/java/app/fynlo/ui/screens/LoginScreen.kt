package app.fynlo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import app.fynlo.data.Analytics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FynloApplication
import app.fynlo.data.GoogleSignInHelper
import app.fynlo.ui.components.FynloBrandMark
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import app.fynlo.ui.theme.*

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val app     = context.applicationContext as FynloApplication
    val scope   = rememberCoroutineScope()
    var loading        by remember { mutableStateOf(false) }
    var error          by remember { mutableStateOf("") }
    var hasTriedSignIn by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!hasTriedSignIn) return@rememberLauncherForActivityResult
        scope.launch {
            loading = true; error = ""
            runCatching {
                val task    = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken ?: throw Exception("No ID token")
                val signInResult = app.authManager.signInWithGoogle(idToken)
                if (signInResult.isSuccess) {
                    Analytics.signIn("google")
                    app.onGoogleSignInComplete(app.authManager.userId)
                    onSignedIn()
                } else {
                    error = signInResult.exceptionOrNull()?.let(::friendlyGoogleSignInError)
                        ?: "Google sign-in failed. Please try again."
                }
            }.onFailure { ex ->
                error = friendlyGoogleSignInError(ex)
            }
            loading = false
        }
    }

    // ── Root: emerald gradient matching the app's PremiumScreenHeader ─────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF022C22), // very dark emerald top
                        Emerald900,
                        Emerald800,
                        Emerald700        // same as PremiumScreenHeader
                    )
                )
            )
    ) {
        // ── Decorative circles (identical to PremiumScreenHeader) ─────────
        Box(
            Modifier
                .size(220.dp)
                .offset(x = 160.dp, y = (-60).dp)
                .clip(CircleShape)
                .background(Emerald600.copy(alpha = 0.35f))
                .align(Alignment.TopEnd)
        )
        Box(
            Modifier
                .size(140.dp)
                .offset(x = (-50).dp, y = 120.dp)
                .clip(CircleShape)
                .background(Emerald600.copy(alpha = 0.2f))
                .align(Alignment.TopStart)
        )
        Box(
            Modifier
                .size(180.dp)
                .offset(x = 60.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Emerald500.copy(alpha = 0.12f))
                .align(Alignment.BottomStart)
        )

        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            FynloBrandMark(size = 96.dp)

            Spacer(Modifier.height(24.dp))

            // App name
            Text(
                "Fynlo",
                fontSize   = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White,
                letterSpacing = 0.sp
            )

            Spacer(Modifier.height(8.dp))

            // Tagline
            Text(
                "Personal Finance Manager",
                fontSize  = 15.sp,
                color     = Emerald200.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Track loans, debts, investments & net worth\nall in one place.",
                fontSize   = 14.sp,
                color      = Emerald200.copy(alpha = 0.65f),
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(48.dp))

            // ── Google Sign-In button ─────────────────────────────────────
            Button(
                onClick = {
                    hasTriedSignIn = true
                    val client = GoogleSignInHelper.getClient(context)
                    launcher.launch(client.signInIntent)
                },
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Carbon900
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        color       = Emerald600,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Box(
                        modifier         = Modifier.size(22.dp).clip(CircleShape)
                            .background(Emerald600),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Continue with Google",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Carbon900
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Skip option ───────────────────────────────────────────────
            OutlinedButton(
                onClick  = onSignedIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Emerald200),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.5f))
            ) {
                Text(
                    "Continue without signing in",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color      = Emerald200
                )
            }

            // Error
            if (error.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Bottom privacy note ───────────────────────────────────────
            Text(
                "Your data is stored securely on your device\nand optionally synced to your Google account.",
                fontSize  = 11.sp,
                color     = Emerald200.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

private fun friendlyGoogleSignInError(error: Throwable): String {
    val api = error as? ApiException
    return when (api?.statusCode) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> ""
        GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
            "Google sign-in is already in progress."
        GoogleSignInStatusCodes.SIGN_IN_FAILED ->
            "Google sign-in failed. Please try again."
        10 -> "Google sign-in setup is missing for this Play build. Continue without signing in for now."
        else -> when {
            error.message?.contains("cancel", ignoreCase = true) == true -> ""
            error.message?.startsWith("10") == true ->
                "Google sign-in setup is missing for this Play build. Continue without signing in for now."
            else -> "Google sign-in failed. Please try again."
        }
    }
}
