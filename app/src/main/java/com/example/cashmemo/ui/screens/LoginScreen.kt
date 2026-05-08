package com.example.cashmemo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cashmemo.CashMemoApplication
import com.example.cashmemo.R
import com.example.cashmemo.data.GoogleSignInHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val app     = context.applicationContext as CashMemoApplication
    val scope   = rememberCoroutineScope()
    var loading   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf("") }
    var hasTriedSignIn by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!hasTriedSignIn) return@rememberLauncherForActivityResult  // ignore stale results
        scope.launch {
            loading = true
            error   = ""
            runCatching {
                val task    = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken ?: throw Exception("No ID token")
                val signInResult = app.authManager.signInWithGoogle(idToken)
                if (signInResult.isSuccess) {
                    val uid = app.authManager.userId
                    app.onGoogleSignInComplete(uid)
                    onSignedIn()
                } else {
                    error = signInResult.exceptionOrNull()?.message ?: "Sign-in failed"
                }
            }.onFailure { ex ->
                error = when {
                    ex.message?.startsWith("10") == true -> "Sign-in setup error. Please ensure SHA-1 is added in Firebase console."
                    ex.message?.contains("cancel", ignoreCase = true) == true -> ""
                    else -> ex.message ?: "Sign-in failed. Please try again."
                }
            }
            loading = false
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF0D47A1), Color(0xFF01579B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(24.dp),
            modifier              = Modifier.padding(32.dp)
        ) {
                androidx.compose.foundation.layout.Box(
                    modifier         = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF059669)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Fynlo",
                        modifier           = Modifier.size(90.dp)
                    )
                }

            Text(
                "Fynlo",
                fontSize   = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White
            )

            Text(
                "Your personal finance manager.\nSign in to sync across all your devices.",
                fontSize   = 15.sp,
                color      = Color.White.copy(alpha = 0.8f),
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(8.dp))

            // Google Sign-In button
            Button(
                onClick = {
                    hasTriedSignIn = true
                    val client = GoogleSignInHelper.getClient(context)
                    launcher.launch(client.signInIntent)
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Color(0xFF1A1A1A)
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(22.dp),
                        color     = Color(0xFF3B82F6),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Google "G" logo colours
                    Box(
                        modifier         = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Continue with Google",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Skip option — keeps anonymous auth
            TextButton(onClick = onSignedIn) {
                Text(
                    "Continue without signing in",
                    color    = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
            }

            if (error.isNotBlank()) {
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
        }
    }
}








