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
import androidx.compose.ui.graphics.Color
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

@Composable
fun PinScreen(
    mode: PinMode = PinMode.ENTER,
    onSuccess: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val context  = LocalContext.current
    val haptic   = LocalHapticFeedback.current
    val pinMgr   = remember { PinManager(context) }

    var pin        by remember { mutableStateOf("") }
    var firstPin   by remember { mutableStateOf("") }
    var error      by remember { mutableStateOf("") }
    var attempts   by remember { mutableIntStateOf(0) }
    var currentMode by remember { mutableStateOf(mode) }

    // Biometric auth — auto-trigger on ENTER mode
    val canUseBiometric = remember {
        mode == PinMode.ENTER &&
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                // user cancelled or no biometric — fall back to PIN
            }
        }
        BiometricPrompt(activity, executor, callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Fynlo")
                .setSubtitle("Use fingerprint or face to unlock")
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }
    LaunchedEffect(Unit) { if (canUseBiometric) triggerBiometric() }

    // Shake animation on wrong PIN
    val shakeOffset = remember { Animatable(0f) }
    suspend fun shake() {
        shakeOffset.animateTo(10f, tween(50))
        shakeOffset.animateTo(-10f, tween(50))
        shakeOffset.animateTo(8f, tween(50))
        shakeOffset.animateTo(-8f, tween(50))
        shakeOffset.animateTo(0f, tween(50))
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
                        error = if (attempts >= 5) "Too many attempts. Try again later." else "Wrong PIN. ${5 - attempts} attempts left."
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
        PinMode.ENTER   -> "Enter your 4-digit PIN to unlock"
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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // PIN dots
            AnimatedContent(targetState = shakeOffset.value, label = "dots") { _ ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.offset(x = shakeOffset.value.dp)
                ) {
                    repeat(4) { i ->
                        val filled = i < pin.length
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // Number pad
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    listOf("1","2","3"),
                    listOf("4","5","6"),
                    listOf("7","8","9"),
                    listOf("","0","<")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "" -> if (canUseBiometric) {
                                    FilledTonalIconButton(
                                        onClick = { triggerBiometric() },
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) { Icon(Icons.Default.Fingerprint, null, Modifier.size(32.dp), tint = Emerald500) }
                                } else Box(Modifier.size(72.dp))
                                "<" -> FilledTonalIconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    },
                                    modifier = Modifier.size(72.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Icon(Icons.AutoMirrored.Filled.Backspace, null) }
                                else -> Button(
                                    onClick = { onKey(key) },
                                    modifier = Modifier.size(72.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) { Text(key, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
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








