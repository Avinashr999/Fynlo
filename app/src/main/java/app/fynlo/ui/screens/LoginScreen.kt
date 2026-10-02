package app.fynlo.ui.screens

import androidx.compose.animation.core.*
import app.fynlo.data.Analytics
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.fynlo.BuildConfig
import app.fynlo.FynloApplication
import app.fynlo.data.GoogleSignInHelper
import app.fynlo.data.GoogleSignInResult
import app.fynlo.data.localLedgerSummary
import app.fynlo.ui.components.FynloBrandMark
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.launch
import app.fynlo.ui.theme.*

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val app     = context.applicationContext as FynloApplication
    val scope   = rememberCoroutineScope()
    var loading        by remember { mutableStateOf(false) }
    var error          by remember { mutableStateOf("") }
    var pendingGoogleAccount by remember { mutableStateOf<GoogleSignInResult?>(null) }
    var pendingLocalRecords by remember { mutableStateOf(0) }
    var showLocalBackupConfirm by remember { mutableStateOf(false) }

    fun clearPendingSignIn() {
        pendingGoogleAccount = null
        pendingLocalRecords = 0
        showLocalBackupConfirm = false
        loading = false
    }

    fun completeGoogleSignIn(account: GoogleSignInResult) {
        scope.launch {
            loading = true
            error = ""
            runCatching {
                val signInResult = app.authManager.signInWithGoogle(account.idToken)
                if (signInResult.isSuccess) {
                    Analytics.signIn("google")
                    app.onGoogleSignInComplete(app.authManager.userId)
                    clearPendingSignIn()
                    onSignedIn()
                } else {
                    error = signInResult.exceptionOrNull()?.let(::friendlyGoogleSignInError)
                        ?: "Google sign-in failed. Please try again."
                    clearPendingSignIn()
                }
            }.onFailure { ex ->
                error = friendlyGoogleSignInError(ex)
                clearPendingSignIn()
            }
        }
    }

    fun startGoogleSignIn() {
        if (loading) return
        scope.launch {
            loading = true; error = ""
            runCatching {
                val account = GoogleSignInHelper.signIn(context)
                val localSummary = app.dao.localLedgerSummary()
                if (localSummary.hasUserData) {
                    pendingGoogleAccount = account
                    pendingLocalRecords = localSummary.totalRecords
                    showLocalBackupConfirm = true
                    loading = false
                } else {
                    completeGoogleSignIn(account)
                }
            }.onFailure { ex ->
                error = friendlyGoogleSignInError(ex)
                loading = false
            }
        }
    }

    if (showLocalBackupConfirm) {
        val email = pendingGoogleAccount?.email.orEmpty().ifBlank { "this Google account" }
        AlertDialog(
            onDismissRequest = {
                scope.launch { GoogleSignInHelper.clearCredentialState(context) }
                clearPendingSignIn()
            },
            title = { Text("Back up this phone?") },
            text = {
                Text(
                    "This phone already has $pendingLocalRecords local records. If you continue, they will be backed up to $email. Continue only if this is your data."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocalBackupConfirm = false
                        pendingGoogleAccount?.let(::completeGoogleSignIn)
                    },
                    enabled = !loading
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { GoogleSignInHelper.clearCredentialState(context) }
                        clearPendingSignIn()
                    }
                ) { Text("Cancel") }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            FynloBrandMark(size = 96.dp)

            Spacer(Modifier.height(24.dp))

            // App name
            Text(
                "Fynlo Ledger",
                fontSize   = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.sp
            )

            Spacer(Modifier.height(8.dp))

            // Tagline
            Text(
                "Personal Finance Manager",
                fontSize  = 15.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Track loans, debts, investments & net worth\nall in one place.",
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(48.dp))

            // ── Google Sign-In button ─────────────────────────────────────
            Button(
                onClick = {
                    startGoogleSignIn()
                },
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
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
                        color       = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Box(
                        modifier         = Modifier.size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    "Continue without signing in",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary
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
                fontSize  = 12.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

private fun friendlyGoogleSignInError(error: Throwable): String {
    return when {
        error is GetCredentialCancellationException -> ""
        error.message?.contains("10:", ignoreCase = true) == true -> loginGoogleSetupMissingMessage()
        error.message?.contains("developer console", ignoreCase = true) == true -> loginGoogleSetupMissingMessage()
        else -> "Google sign-in failed. Please try again."
    }
}

private fun loginGoogleSetupMissingMessage(): String =
    if (BuildConfig.FLAVOR == "dev") {
        "Developer Google sign-in is not configured yet. Continue without signing in for now."
    } else {
        "Google sign-in is not ready in this build. Continue without signing in for now."
    }
