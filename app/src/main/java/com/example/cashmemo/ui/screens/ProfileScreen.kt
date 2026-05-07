package com.example.cashmemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.CashMemoApplication
import com.example.cashmemo.data.PinManager
import com.example.cashmemo.ui.screens.PinMode

@Composable
fun ProfileScreen(onLogout: () -> Unit, onSignOut: () -> Unit = {}) {
    val context = LocalContext.current
    val app     = context.applicationContext as CashMemoApplication
    val pinManager = remember { PinManager(context) }
    var showPinSetup by remember { mutableStateOf(false) }
    val isGoogle = app.authManager.isSignedInWithGoogle

    if (showPinSetup) {
        PinScreen(
            mode      = if (pinManager.isPinSet) PinMode.SET else PinMode.SET,
            onSuccess = { showPinSetup = false },
            onSkip    = { showPinSetup = false }
        )
        return
    }
    val email    = app.authManager.userEmail
    val name     = app.authManager.userName
    val uid      = app.authManager.userId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Profile & Security",
            style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp).align(Alignment.Start)
        )

        // ── Account card ──────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(
                containerColor = if (isGoogle)
                    Color(0xFF3B82F6).copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isGoogle) Icons.Default.AccountCircle else Icons.Default.Person,
                        contentDescription = null,
                        tint     = if (isGoogle) Color(0xFF3B82F6) else MaterialTheme.colorScheme.primary,
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
                            Text("UID: ${uid.take(12)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Not signed in with Google", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Sync status card ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cloud Sync", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (isGoogle) "✓ Syncing across all your devices via Google account"
                        else "⚠ Syncing to this device only — sign in with Google for cross-device sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Security card ─────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text("Security", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(12.dp))
                Text("Current security mode is PIN protected.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showPinSetup = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp)
                ) { Text("Change Login PIN") }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Sign out Google ───────────────────────────────────────────────────
        if (isGoogle) {
            OutlinedButton(
                onClick  = {
                    app.authManager.signOut()
                    onSignOut()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Text("Sign out of Google", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Lock app ──────────────────────────────────────────────────────────
        Button(
            onClick  = onLogout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout & Lock App", fontWeight = FontWeight.Bold)
        }
    }
}








