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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.ui.components.AccountGrowthIndicator
import app.fynlo.ui.components.WealthDistributionBar
import app.fynlo.ui.components.ProjectSwitcherChip
import app.fynlo.ui.components.AddTransactionDialog
import app.fynlo.ui.components.PortfolioBreakdownSheet
import app.fynlo.ui.theme.*

@Composable
fun HomeScreen(viewModel: FinanceViewModel, onNavigateToScreen: (String) -> Unit = {}) {
    val haptic           = LocalHapticFeedback.current
    val summary          by viewModel.financialSummary.collectAsState()
    val analytics        by viewModel.expenseAnalytics.collectAsState()
    val projects         by viewModel.projects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val currentProject   by viewModel.currentProject.collectAsState()
    val isSyncReady      by viewModel.isSyncReady.collectAsState()
    val locale           = java.util.Locale.getDefault()
    val currencySymbol   = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    var showAddTxn       by remember { mutableStateOf(false) }

    // Analytics Sheet State
    var activeBreakdownType by remember { mutableStateOf<BreakdownType?>(null) }

    if (showAddTxn) {
        AddTransactionDialog(
            onDismiss = { showAddTxn = false },
            onConfirm = { txn -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addTransaction(txn); showAddTxn = false }
        )
    }

    if (activeBreakdownType != null) {
        val sheetTitle = when(activeBreakdownType) {
            BreakdownType.IDLE_CASH -> "Idle Cash Breakdown"
            BreakdownType.GROWING_ASSETS -> "Growing Assets Breakdown"
            BreakdownType.HAND_LOANS -> "Hand Loans Breakdown"
            else -> ""
        }
        val sheetIcon = when(activeBreakdownType) {
            BreakdownType.IDLE_CASH -> Icons.Default.AccountBalance
            BreakdownType.GROWING_ASSETS -> Icons.Default.TrendingUp
            BreakdownType.HAND_LOANS -> Icons.Default.Handshake
            else -> Icons.Default.Info
        }
        val sheetColor = when(activeBreakdownType) {
            BreakdownType.IDLE_CASH -> SemanticBlue
            BreakdownType.GROWING_ASSETS -> SemanticAmber
            BreakdownType.HAND_LOANS -> Carbon500
            else -> Color.Gray
        }
        val sheetData = when(activeBreakdownType) {
            BreakdownType.IDLE_CASH -> summary.accountBreakdown
            BreakdownType.GROWING_ASSETS -> {
                // Merge investments and interest-bearing loans
                val combined = mutableMapOf<String, Double>()
                summary.investmentTypeBreakdown.forEach { (k, v) -> combined["$k (Invest)"] = v }
                summary.interestLendingBreakdown.forEach { (k, v) -> combined["$k (Loan)"] = v }
                combined
            }
            BreakdownType.HAND_LOANS -> summary.handLendingBreakdown
            else -> emptyMap()
        }

        PortfolioBreakdownSheet(
            title = sheetTitle,
            data = sheetData,
            currencySymbol = currencySymbol,
            icon = sheetIcon,
            iconColor = sheetColor,
            onDismiss = { activeBreakdownType = null }
        )
    }

    if (!isSyncReady) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = Emerald500)
                Text("Syncing your data...", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))

        // 1. Project Switcher
        ProjectSwitcherChip(
            projects = projects, currentProjectId = currentProjectId,
            onSwitch = { viewModel.switchProject(it) },
            onManageClick = { onNavigateToScreen("projects") }
        )
        
        Spacer(Modifier.height(16.dp))

        // 2. Net Worth Card (High Visibility)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Total Net Worth", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "$currencySymbol ${String.format(locale, "%,.0f", summary.netWorth)}",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Assets ${currencySymbol}${String.format(locale, "%,.0f", summary.totalAssets)} · Liabilities ${currencySymbol}${String.format(locale, "%,.0f", summary.totalDebtPrincipal + summary.totalDebtInterest)}",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f)
                )
                
                Spacer(Modifier.height(16.dp))
                WealthDistributionBar(
                    cash = summary.totalCash,
                    investments = summary.totalInvestments,
                    interestLoans = summary.totalInterestLoans,
                    handLoans = summary.totalHandLoans
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 3. Active Accounts (Top Priority for Business)
        Text("Active Business Accounts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        
        if (summary.accountBreakdown.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Text("No accounts found. Tap Quick Add to create one.", 
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            summary.accountBreakdown.forEach { (name, balance) ->
                val growth = summary.accountGrowthMap[name] ?: 0.0
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onNavigateToScreen("statement/$name") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Emerald500.copy(alpha = 0.12f)), Alignment.Center) {
                                Icon(Icons.Default.AccountBalance, null, Modifier.size(20.dp), tint = Emerald500)
                            }
                            Column {
                                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                AccountGrowthIndicator(growth, currencySymbol, locale)
                            }
                        }
                        Text("$currencySymbol ${String.format(locale, "%,.0f", balance)}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 4. Quick Actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Expense", Icons.Default.Remove, SemanticRed, Modifier.weight(1f)) { showAddTxn = true }
            QuickAction("Income", Icons.Default.Add, Emerald400, Modifier.weight(1f)) { showAddTxn = true }
            QuickAction("Lend", Icons.Default.Handshake, SemanticBlue, Modifier.weight(1f)) { onNavigateToScreen("lending") }
            QuickAction("History", Icons.Default.History, Carbon500, Modifier.weight(1f)) { onNavigateToScreen("history") }
        }

        Spacer(Modifier.height(24.dp))

        // 5. Portfolio Status
        Text("Portfolio Efficiency", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Idle Cash", "$currencySymbol${String.format(locale, "%,.0f", summary.totalCash)}", SemanticBlue, Modifier.weight(1f)) { activeBreakdownType = BreakdownType.IDLE_CASH }
            MetricCard("Growing Assets", "$currencySymbol${String.format(locale, "%,.0f", summary.totalInvestments + summary.totalInterestLoans)}", SemanticAmber, Modifier.weight(1f)) { activeBreakdownType = BreakdownType.GROWING_ASSETS }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Hand Loans", "$currencySymbol${String.format(locale, "%,.0f", summary.totalHandLoans)}", Carbon500, Modifier.weight(1f)) { activeBreakdownType = BreakdownType.HAND_LOANS }
            MetricCard("Total Owed", "$currencySymbol${String.format(locale, "%,.0f", summary.totalDebtPrincipal + summary.totalDebtInterest)}", SemanticRed, Modifier.weight(1f)) { onNavigateToScreen("debts") }
        }

        Spacer(Modifier.height(100.dp))
    }
}

enum class BreakdownType {
    IDLE_CASH, GROWING_ASSETS, HAND_LOANS
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
