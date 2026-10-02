package app.fynlo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
fun LedgerDashboardSummary(
    netWorth: String,
    assets: String,
    debts: String,
    freshness: String,
    onNetWorth: () -> Unit,
    onAssets: () -> Unit,
    onDebts: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onNetWorth).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Net worth", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.Info, "Net worth history", Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Keep the exact formatter's value; only the fractional part has quieter type.
        val separator = netWorth.lastIndexOfAny(charArrayOf('.', ','))
        val hasFraction = separator >= 0 && netWorth.length - separator == 3
        val amount = buildAnnotatedString {
            if (hasFraction) {
                append(netWorth.substring(0, separator))
                withStyle(SpanStyle(fontSize = 22.sp)) { append(netWorth.substring(separator)) }
            } else append(netWorth)
        }
        Text(amount, Modifier.fillMaxWidth().clickable(onClick = onNetWorth), style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryValue("Assets", assets, Modifier.weight(1f), onAssets)
            SummaryValue("Debts", debts, Modifier.weight(1f), onDebts)
        }
        // Freshness remains available for accessibility without becoming another dashboard banner.
        Text(freshness, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).heightIn(min = 56.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LedgerInterestToday(earned: String, owed: String, net: String) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { showDetails = true }.heightIn(min = 64.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Percent, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Interest today", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Earned $earned  /  Owed $owed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, "Interest details", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    if (showDetails) {
        FormDialog(title = "Interest today", onDismiss = { showDetails = false }) {
            Text("Interest added today to your loans and debts. This is not money received or paid from an account.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text("Earned: $earned", style = MaterialTheme.typography.bodyLarge)
            Text("Owed: $owed", style = MaterialTheme.typography.bodyLarge)
            Text("Net worth change: $net", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
