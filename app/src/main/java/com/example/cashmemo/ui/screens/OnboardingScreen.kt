package com.example.cashmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingPage(val icon: ImageVector, val color: Color, val title: String, val desc: String)

private val pages = listOf(
    OnboardingPage(Icons.Default.AccountBalanceWallet, Color(0xFF3B82F6),
        "Track Your Net Worth",
        "See your total assets, liabilities and net worth at a glance. Connects across all your devices in real time."),
    OnboardingPage(Icons.Default.Group, Color(0xFF10B981),
        "Manage Lending",
        "Track money you lend to others with exact interest calculations — Simple, Compound, or Reducing Balance."),
    OnboardingPage(Icons.Default.CreditCard, Color(0xFFEF4444),
        "Track Your Debts",
        "Know exactly what you owe, interest accrued per day, and estimated payoff timeline at your current payment rate."),
    OnboardingPage(Icons.Default.TrendingUp, Color(0xFFF59E0B),
        "Monitor Investments",
        "Add your gold, stocks, FDs and track their current value and growth over time."),
    OnboardingPage(Icons.Default.Sync, Color(0xFF8B5CF6),
        "Real-Time Sync",
        "Sign in with Google to sync your data across phone, tablet, and any other device instantly.")
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Surface(Modifier.size(120.dp), CircleShape, color = current.color.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(current.icon, null, Modifier.size(56.dp), tint = current.color)
                }
            }
            Spacer(Modifier.height(32.dp))

            AnimatedContent(targetState = page, label = "page") { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pages[p].title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(pages[p].desc,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(48.dp))

            // Dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(Modifier.size(if (i == page) 24.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == page) current.color else MaterialTheme.colorScheme.outlineVariant))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Buttons
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) { Text("Back") }
                } else {
                    Spacer(Modifier.width(80.dp))
                }

                Button(
                    onClick = { if (page < pages.size - 1) page++ else onComplete() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = current.color)
                ) {
                    Text(if (page == pages.lastIndex) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onComplete) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}