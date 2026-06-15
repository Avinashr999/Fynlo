package app.fynlo.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.billing.BillingManager
import app.fynlo.billing.ProProducts
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticAmber

private val PRO_BENEFITS = listOf(
    "Unlimited accounts, loans, debts & investments",
    "Real-time cloud sync & auto-backup",
    "Multiple books (projects)",
    "All reports + PDF / Excel export",
    "Recurring automation & advanced interest",
    "Biometric lock",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeProScreen(onNavigateBack: () -> Unit = {}) {
    val activity = LocalContext.current as? Activity
    val isPro by BillingManager.isPro.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fynlo Pro") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlock everything",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            )
            Text(
                "Get the full Fynlo - one tracker for every rupee you lend, owe, spend and grow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            PRO_BENEFITS.forEach { benefit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Emerald500.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = Emerald500)
                    }
                    Text(benefit, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isPro) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Emerald500.copy(alpha = 0.12f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "You're on Fynlo Pro",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Emerald500,
                    )
                }
            } else {
                PlanCard(
                    title = "Annual",
                    price = "Rs 999 / year",
                    note = "Best value - 7-day free trial",
                    highlighted = true,
                ) { activity?.let { BillingManager.launchSubscription(it, ProProducts.BASE_PLAN_ANNUAL) } }
                Spacer(Modifier.height(12.dp))
                PlanCard(
                    title = "Monthly",
                    price = "Rs 149 / month",
                    note = null,
                ) { activity?.let { BillingManager.launchSubscription(it, ProProducts.BASE_PLAN_MONTHLY) } }
                Spacer(Modifier.height(12.dp))
                PlanCard(
                    title = "Founding Lifetime",
                    price = "Rs 2,799 once",
                    note = "Limited to the first 100 users",
                ) { activity?.let { BillingManager.launchLifetime(it) } }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { BillingManager.refreshPurchases() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Restore purchases", color = Emerald500) }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    note: String?,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val border = if (highlighted) Emerald500 else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (highlighted) Emerald500.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .border(if (highlighted) 1.5.dp else 0.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (highlighted) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SemanticAmber.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "POPULAR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SemanticAmber,
                        )
                    }
                }
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            price,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = if (highlighted) Emerald500 else MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}
