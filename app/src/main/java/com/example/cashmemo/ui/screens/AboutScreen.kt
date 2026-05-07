package com.example.cashmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.BuildConfig

@Composable
fun AboutScreen() {
    val green = Color(0xFF059669)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "About",
            style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // ── App identity card ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = green.copy(alpha = 0.08f))
        ) {
            Row(
                modifier          = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier        = Modifier.size(56.dp).clip(CircleShape).background(green.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(28.dp), tint = green)
                }
                Column {
                    Text("Cash Memo",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                    Text("Personal Finance Manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Surface(color = green.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text("v${BuildConfig.VERSION_NAME}",
                            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color    = green,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Feature highlights ───────────────────────────────────────────────
        Text("What Cash Memo Does",
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 10.dp))

        val features = listOf(
            Icons.Default.AccountBalance    to "Track accounts, cash, and bank balances",
            Icons.Default.Group             to "Manage loans to friends and family",
            Icons.Default.CreditCard        to "Track debts you owe to others",
            Icons.Default.TrendingUp        to "Monitor investments and growth",
            Icons.Default.PieChart          to "Set budgets and track spending",
            Icons.Default.Assessment        to "Reports: P&L, net worth, cash flow",
            Icons.Default.CloudDone         to "Auto-sync to Google Firestore",
            Icons.Default.Lock              to "PIN + biometric app lock"
        )

        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
            CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                features.forEach { (icon, desc) ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), tint = green)
                        Text(desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Privacy card ─────────────────────────────────────────────────────
        AboutInfoCard(
            icon  = Icons.Default.Shield,
            title = "Privacy & Security",
            body  = "Your data stays on your device and your personal Google Firestore. We do not collect, sell, or share your financial information with any third parties.",
            color = Color(0xFF3B82F6)
        )

        Spacer(Modifier.height(12.dp))

        // ── Legal disclaimer ─────────────────────────────────────────────────
        AboutInfoCard(
            icon  = Icons.Default.Gavel,
            title = "Legal Disclaimer",
            body  = "Cash Memo is a manual-entry personal finance ledger for personal use only. It is NOT a banking, lending, or investment advisory service. Always verify your data with official bank statements. The developer is not responsible for any financial decisions made using this app.",
            color = Color(0xFFEF4444)
        )

        Spacer(Modifier.height(20.dp))

        // ── Footer ───────────────────────────────────────────────────────────
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
            CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Favorite, null, Modifier.size(20.dp), tint = Color(0xFFEF4444))
                Text("Made with care for personal finance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun AboutInfoCard(
    icon: ImageVector,
    title: String,
    body: String,
    color: Color
) {
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = color)
                Text(title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = color)
            }
            Text(body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
