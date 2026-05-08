package app.fynlo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.ui.components.AddTransactionDialog
import app.fynlo.ui.components.DataPoint
import app.fynlo.ui.components.ProjectSwitcherChip

@Composable
fun HomeScreen(viewModel: FinanceViewModel, onNavigateToScreen: (String) -> Unit = {}) {
    val summary          by viewModel.financialSummary.collectAsState()
    val analytics        by viewModel.expenseAnalytics.collectAsState()
    val projects         by viewModel.projects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val currentProject   by viewModel.currentProject.collectAsState()
    val isSyncReady      by viewModel.isSyncReady.collectAsState()
    val haptic           = LocalHapticFeedback.current
    val locale           = java.util.Locale.getDefault()
    val currencySymbol   = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    var showAddTxn       by remember { mutableStateOf(false) }

    if (showAddTxn) {
        AddTransactionDialog(
            onDismiss = { showAddTxn = false },
            onConfirm = { txn -> viewModel.addTransaction(txn); showAddTxn = false }
        )
    }

    if (!isSyncReady) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = Color(0xFF059669))
                Text("Syncing your data...", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(12.dp))

        // Project switcher
        ProjectSwitcherChip(
            projects = projects, currentProjectId = currentProjectId,
            onSwitch = { viewModel.switchProject(it) },
            onManageClick = { onNavigateToScreen("projects") }
        )
        Spacer(Modifier.height(16.dp))

        // â”€â”€ Net Worth Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val isPositive = summary.netWorth >= 0
        val cardBg = if (isPositive) Color(0xFF059669) else Color(0xFFEF4444)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .clickable { onNavigateToScreen("net_worth_hist") }
                .padding(24.dp)
        ) {
            Column {
                Text("Total Net Worth", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))
                AnimatedContent(targetState = summary.netWorth, label = "nw",
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }
                ) { nw ->
                    Text(
                        text  = "$currencySymbol ${String.format(locale, "%,.0f", nw)}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Assets ${currencySymbol}${String.format(locale, "%,.0f", summary.totalAssets)}  ·  Liabilities ${currencySymbol}${String.format(locale, "%,.0f", summary.totalDebtPrincipal + summary.totalDebtInterest)}",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // â”€â”€ Quick Actions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Add Expense",   Icons.Default.Remove,        Color(0xFFEF4444), Modifier.weight(1f)) { showAddTxn = true }
            QuickAction("Add Income",    Icons.Default.Add,            Color(0xFF059669), Modifier.weight(1f)) { showAddTxn = true }
            QuickAction("Lend Money",    Icons.Default.Person,         Color(0xFF3B82F6), Modifier.weight(1f)) { onNavigateToScreen("lending") }
            QuickAction("History",       Icons.Default.History,        Color(0xFF6B7280), Modifier.weight(1f)) { onNavigateToScreen("history") }
        }

        Spacer(Modifier.height(20.dp))

        // â”€â”€ Wealth Breakdown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Text("Wealth & Assets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))

        // Summary metric cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Cash & Bank", "$currencySymbol${String.format(locale, "%,.0f", summary.totalCash)}",
                Color(0xFF059669), Modifier.weight(1f)) { onNavigateToScreen("history") }
            MetricCard("Investments", "$currencySymbol${String.format(locale, "%,.0f", summary.totalInvestments)}",
                Color(0xFFF59E0B), Modifier.weight(1f)) { onNavigateToScreen("invest") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Lending", "$currencySymbol${String.format(locale, "%,.0f", summary.totalReceivables)}",
                Color(0xFF3B82F6), Modifier.weight(1f)) { onNavigateToScreen("lending") }
            MetricCard("Debt Owed", "$currencySymbol${String.format(locale, "%,.0f", summary.totalDebtPrincipal + summary.totalDebtInterest)}",
                Color(0xFFEF4444), Modifier.weight(1f)) { onNavigateToScreen("debts") }
        }

        Spacer(Modifier.height(20.dp))

        // â”€â”€ Account Breakdown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Text("Accounts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
            CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                summary.accountBreakdown.forEach { (name, balance) ->
                    Row(Modifier.fillMaxWidth().clickable { onNavigateToScreen("statement/$name") }
                        .padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF059669).copy(alpha = 0.15f)),
                                Alignment.Center) {
                                Icon(Icons.Default.AccountBalance, null, Modifier.size(18.dp), tint = Color(0xFF059669))
                            }
                            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        }
                        Text("$currencySymbol ${String.format(locale, "%,.0f", balance)}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (balance >= 0) MaterialTheme.colorScheme.onSurface else Color(0xFFEF4444))
                    }
                    if (name != summary.accountBreakdown.keys.last()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // â”€â”€ Performance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceCard("Invest. Growth",
                "${if (summary.investmentGrowth >= 0) "+" else ""}$currencySymbol${String.format(locale, "%,.0f", summary.investmentGrowth)}",
                if (summary.investmentGrowth >= 0) Color(0xFF059669) else Color(0xFFEF4444),
                Modifier.weight(1f)) { onNavigateToScreen("invest") }
            PerformanceCard("Avg. Lending Rate",
                "${String.format(locale, "%.1f", summary.lendingYield)}% p.a.",
                Color(0xFF3B82F6), Modifier.weight(1f)) { onNavigateToScreen("lending") }
        }

        Spacer(Modifier.height(20.dp))

        // â”€â”€ Spending Breakdown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (analytics.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("This Month's Spending", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = { onNavigateToScreen("spend") }) { Text("See All") }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val catColors = listOf(Color(0xFF3B82F6),Color(0xFF059669),Color(0xFFF59E0B),Color(0xFFEF4444),Color(0xFF8B5CF6))
                    analytics.entries.sortedByDescending { it.value }.take(5).forEachIndexed { i, item ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(catColors[i % catColors.size]))
                                Text(item.key, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("$currencySymbol${String.format(locale, "%,.0f", item.value)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = catColors[i % catColors.size])
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)),
            Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                softWrap = false)
        }
    }
}

@Composable
fun PerformanceCard(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit = {}) {
    Card(modifier.clickable(onClick = onClick), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}










