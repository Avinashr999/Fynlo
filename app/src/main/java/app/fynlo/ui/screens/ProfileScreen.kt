@file:Suppress("DEPRECATION")

package app.fynlo.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FynloApplication
import app.fynlo.billing.BillingManager
import app.fynlo.data.Analytics
import app.fynlo.data.GoogleSignInHelper
import app.fynlo.data.PinManager
import app.fynlo.ui.components.FynloConfirmDialog
import app.fynlo.ui.screens.PinMode
import app.fynlo.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@SuppressLint("InlinedApi")
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onSignOut: () -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {},
    viewModel: app.fynlo.FinanceViewModel? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app     = context.applicationContext as FynloApplication
    val isPro by BillingManager.isPro.collectAsState()
    val pinManager = remember { PinManager(context) }
    var pinSet           by remember { mutableStateOf(pinManager.isPinSet) }
    var biometricEnabled by remember { mutableStateOf(pinManager.isBiometricEnabled) }
    var showPinSetup     by remember { mutableStateOf(false) }
    var showRemovePinConfirm by remember { mutableStateOf(false) }
    val authUser by app.authManager.user.collectAsState()
    val isGoogle = authUser?.providerData
        ?.any { it.providerId == com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID } == true
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf("") }
    var hasStartedSignIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!hasStartedSignIn) return@rememberLauncherForActivityResult
        scope.launch {
            signingIn = true
            signInError = ""
            runCatching {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken ?: run {
                    Log.w("FynloAuth", "Google sign-in returned no token for ${context.packageName}")
                    throw IllegalStateException("Missing Google token")
                }
                val signInResult = app.authManager.signInWithGoogle(idToken)
                if (signInResult.isSuccess) {
                    Analytics.signIn("google")
                    app.onGoogleSignInComplete(app.authManager.userId)
                    signInError = ""
                    android.widget.Toast
                        .makeText(context, "Signed in. Cloud backup is starting.", android.widget.Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Log.w(
                        "FynloAuth",
                        "Firebase Google sign-in failed for ${context.packageName}",
                        signInResult.exceptionOrNull()
                    )
                    signInError = profileSignInError(signInResult.exceptionOrNull())
                }
            }.onFailure { ex ->
                val status = (ex as? ApiException)?.statusCode
                Log.w(
                    "FynloAuth",
                    "Google sign-in failed for ${context.packageName}; status=$status; message=${ex.message}",
                    ex
                )
                signInError = profileSignInError(ex)
            }
            signingIn = false
            hasStartedSignIn = false
        }
    }

    // Full-screen PIN set/change flow (same as the lock screen).
    if (showPinSetup) {
        PinScreen(
            mode      = PinMode.SET,
            onSuccess = { pinSet = pinManager.isPinSet; showPinSetup = false },
            onSkip    = { showPinSetup = false }
        )
        return
    }

    // Remove PIN confirmation dialog
    if (showRemovePinConfirm) {
        FynloConfirmDialog(
            title = "Remove PIN?",
            message = "The app will no longer be locked when you switch away. Biometric unlock will also be disabled.",
            confirmText = "Remove",
            destructive = true,
            onDismiss = { showRemovePinConfirm = false },
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                pinManager.clearPin()
                pinSet = false
                biometricEnabled = false
                showRemovePinConfirm = false
            }
        )
    }

    val bioStatus = remember { biometricStatus(context) }
    val bioHardwareAvailable = bioStatus == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS ||
                               bioStatus == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    val onToggleBiometric = {
        when {
            !isPro -> { onNavigateToUpgrade() }
            !pinSet -> { showPinSetup = true }
            bioStatus == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val intent = android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                }
                try { context.startActivity(intent) } catch (e: Exception) {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
                }
            }
            else -> {
                val newVal = !biometricEnabled
                pinManager.isBiometricEnabled = newVal
                biometricEnabled = newVal
            }
        }
    }

    val email    = authUser?.email.orEmpty()
    val name     = authUser?.displayName.orEmpty()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        PremiumScreenHeader("Profile & Security", subtitle = "Identity, sync, and app lock")
        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Account card ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isGoogle) Icons.Default.AccountCircle else Icons.Default.Person,
                        contentDescription = null,
                        tint     = if (isGoogle) SemanticBlue else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isGoogle) "Google Account" else "Local Account",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (isGoogle) {
                            Text(name,  style = MaterialTheme.typography.bodyMedium)
                            Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(
                                if (signingIn) "Signing in..." else "Not signed in",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

        Spacer(Modifier.height(12.dp))

        // ── Sync status card ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SemanticBlue.copy(alpha = 0.08f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cloud Sync", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (isGoogle) "Syncing across all your devices via Google account"
                        else if (signingIn) "Opening Google sign-in..."
                        else "Device-only. Sign in to start cloud backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        if (!isGoogle) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (signingIn) return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    signInError = ""
                    signingIn = true
                    hasStartedSignIn = true
                    val client = GoogleSignInHelper.getClient(context)
                    signInLauncher.launch(client.signInIntent)
                },
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                }
            }
            if (signInError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        signInError,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    "Cloud backup active",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Security card (PIN + biometric) ───────────────────────────────────
        Text(
            "Security",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Emerald500,
            modifier = Modifier.align(Alignment.Start).padding(start = 2.dp, bottom = 8.dp)
        )
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
        Column(Modifier.animateContentSize()) {
            // PIN Lock — tap anywhere to toggle on/off
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pinSet) showRemovePinConfirm = true else showPinSetup = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(
                        if (pinSet) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    ), Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp),
                        tint = if (pinSet) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error)
                }
                Column(Modifier.weight(1f)) {
                    Text("PIN Lock",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (pinSet) "App locks when you switch away"
                        else "Tap to set a 4-digit PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pinSet,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.error,
                        uncheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                        uncheckedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                    )
                )
            }
            // Change PIN — only when a PIN is set
            if (pinSet) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showPinSetup = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change PIN")
                }
            }
            // Biometric Unlock — only when hardware is present
            if (bioHardwareAvailable) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleBiometric()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(
                            if (biometricEnabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ), Alignment.Center
                    ) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp),
                            tint = if (biometricEnabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Biometric Unlock",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            when {
                                !pinSet -> "Set a PIN first to enable biometric"
                                bioStatus == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                    "No biometrics enrolled — tap to set up"
                                biometricEnabled -> "Fingerprint / face unlock active"
                                else -> "Tap to enable fingerprint / face unlock"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!pinSet) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = null,
                        enabled = bioHardwareAvailable && pinSet,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
        }

        Spacer(Modifier.height(24.dp))

        // ── Sign out Google ───────────────────────────────────────────────────
        if (isGoogle) {
            OutlinedButton(
                onClick  = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    app.authManager.signOut()
                    GoogleSignInHelper.getClient(context).signOut()
                    onSignOut()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AccountCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign out of Google", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Lock app ──────────────────────────────────────────────────────────
        Button(
            onClick  = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLogout()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Logout & Lock App", fontWeight = FontWeight.Bold)
        }

        // ── Delete account (right-to-erasure) ─────────────────────────────────
        if (isGoogle && viewModel != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirm = true
                },
                enabled = !deleting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (deleting) "Deleting…" else "Delete my account & all data",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (showDeleteConfirm && viewModel != null) {
            FynloConfirmDialog(
                title = "Delete account permanently?",
                message = "This erases ALL your data from this device and the cloud, and deletes your Fynlo Ledger account. This cannot be undone. Export a backup first if you might want your data later.",
                confirmText = "Delete forever",
                destructive = true,
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirm = false
                    deleting = true
                    viewModel.deleteAccountPermanently(app.authManager) { fullyDeleted ->
                        deleting = false
                        if (fullyDeleted) {
                            android.widget.Toast.makeText(context, "Account deleted", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Data wiped. Please sign in again to finish deleting your account.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        app.authManager.signOut()
                        onLogout()
                    }
                }
            )
        }
        Spacer(Modifier.height(32.dp))
    }
    }
}


private fun profileSignInError(error: Throwable?): String {
    val api = error as? ApiException
    return when (api?.statusCode) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> ""
        GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
            "Sign-in is already in progress."
        GoogleSignInStatusCodes.SIGN_IN_FAILED ->
            "Sign-in failed. Please try again."
        else -> "Sign-in failed. Please try again."
    }
}
