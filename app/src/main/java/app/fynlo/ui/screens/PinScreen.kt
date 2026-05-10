package app.fynlo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.fynlo.data.PinManager
import app.fynlo.ui.theme.*

enum class PinMode { ENTER, SET, CONFIRM }

/** Returns the BiometricManager availability code using WEAK authenticators.
 *  OPPO/Realme optical fingerprints are classified as BIOMETRIC_WEAK, not STRONG.
 *  Checking STRONG causes BIOMETRIC_ERROR_NONE_ENROLLED on these devices. */
fun biometricStatus(context: android.content.Context): Int {
    val mgr = BiometricManager.from(context)
    // Try WEAK first (covers optical fingerprint, face on most devices)
    val weak = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (weak == BiometricManager.BIOMETRIC_SUCCESS ||
        weak == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) return weak
    // Fall back to STRONG (covers secure fingerprint / iris on some OEMs)
    return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
}

@Composable
fun PinScreen(
    mode: PinMode = PinMode.ENTER,
    onSuccess: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val pinMgr  = remember { PinManager(context) }

    var pin         by remember { mutableStateOf("") }
    var firstPin    by remember { mutableStateOf("") }
    var error       by remember { mutableStateOf("") }
    var attempts    by remember { mutableIntStateOf(0) }
    var currentMode by remember { mutableStateOf(mode) }

    // Only show biometric button when:
    // 1. We're in ENTER mode (not setting a new PIN)
    // 2. User has explicitly enabled biometric in settings
    // 3. Device has biometric hardware with enrolled biometrics
    val bioStatus = remember { biometricStatus(context) }
    val canUseBiometric = remember(mode) {
        mode == PinMode.ENTER &&
        pinMgr.isBiometricEnabled &&
        bioStatus == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                // Cancelled / lockout / no hardware — show PIN, clear error
                error = when (code) {
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric locked out. Use PIN."
                    BiometricPrompt.ERROR_NO_BIOMETRICS     -> "No biometrics enrolled. Use PIN."
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE    -> "Biometric unavailable. Use PIN."
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON   -> "" // user chose PIN — silent
                    else                                    -> ""
                }
            }
            override fun onAuthenticationFailed() {
                error = "Biometric not recognised. Try again or use PIN."
            }
        }
        BiometricPrompt(activity, executor, callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Fynlo")
                .setSubtitle("Use fingerprint or face unlock")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .setNegativeButtonText("Use PIN instead")
                .setConfirmationRequired(false)
                .build()
        )
    }

    // Auto-trigger on launch only if user has opted in
    LaunchedEffect(Unit) {
        if (canUseBiometric) triggerBiometric()
    }

    // Shake animation on wrong PIN
    val shakeOffset = remember { Animatable(0f) }
    suspend fun shake() {
        shakeOffset.animateTo(10f, tween(50)); shakeOffset.animateTo(-10f, tween(50))
        shakeOffset.animateTo(8f,  tween(50)); shakeOffset.animateTo(-8f,  tween(50))
        shakeOffset.animateTo(0f,  tween(50))
    }

    fun onKey(key: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (pin.length >= 4) return
        val newPin = pin + key
        pin = newPin
        error = ""

        if (newPin.length == 4) {
            when (currentMode) {
                PinMode.ENTER -> {
                    if (pinMgr.verifyPin(newPin)) {
                        attempts = 0
                        onSuccess()
                    } else {
                        attempts++
                        error = if (attempts >= 5) "Too many attempts. Try again later."
                                else "Wrong PIN. ${5 - attempts} attempt${if (5 - attempts != 1) "s" else ""} left."
                        pin = ""
                    }
                }
                PinMode.SET -> {
                    firstPin = newPin
                    currentMode = PinMode.CONFIRM
                    pin = ""
                }
                PinMode.CONFIRM -> {
                    if (newPin == firstPin) {
                        pinMgr.setPin(newPin)
                        onSuccess()
                    } else {
                        error = "PINs do not match. Try again."
                        firstPin = ""
                        currentMode = PinMode.SET
                        pin = ""
                    }
                }
            }
        }
    }

    val title = when (currentMode) {
        PinMode.ENTER   -> "Enter PIN"
        PinMode.SET     -> "Set a new PIN"
        PinMode.CONFIRM -> "Confirm PIN"
    }
    val subtitle = when (currentMode) {
        PinMode.ENTER   -> if (canUseBiometric) "Enter PIN or use biometric" else "Enter your 4-digit PIN to unlock"
        PinMode.SET     -> "Choose a 4-digit PIN"
        PinMode.CONFIRM -> "Re-enter the same PIN"
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))

            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // PIN dots with shake animation
            AnimatedContent(targetState = shakeOffset.value, label = "dots") { _ ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.offset(x = shakeOffset.value.dp)
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(
                                if (i < pin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(visible = error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // Number pad
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("bio", "0", "<")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "bio" -> if (canUseBiometric) {
                                    FilledTonalIconButton(
                                        onClick  = { triggerBiometric() },
                                        modifier = Modifier.size(72.dp),
                                        shape    = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Fingerprint, "Use biometric",
                                            Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Box(Modifier.size(72.dp))   // empty cell if biometric disabled
                                }
                                "<" -> FilledTonalIconButton(
                                    onClick  = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    },
                                    modifier = Modifier.size(72.dp),
                                    shape    = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, "Delete")
                                }
                                else -> Button(
                                    onClick  = { onKey(key) },
                                    modifier = Modifier.size(72.dp),
                                    shape    = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(key, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            if (onSkip != null) {
                TextButton(onClick = onSkip) {
                    Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
