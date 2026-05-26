package app.fynlo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.components.AccountGrowthIndicator
import app.fynlo.ui.components.WealthDistributionBar
import app.fynlo.ui.components.ProjectSwitcherChip
import app.fynlo.ui.components.AddTransactionDialog
import app.fynlo.ui.components.PortfolioBreakdownSheet
import app.fynlo.ui.theme.*

@Composable
fun HomeScreen(viewModel: FinanceViewModel, onNavigateToScreen: (String) -> Unit = {}) {
    LaunchedEffect(Unit) { app.fynlo.data.Analytics.screenView("Home") }
    val haptic           = LocalHapticFeedback.current
    val summary          by viewModel.financialSummary.collectAsState()
    val analytics        by viewModel.expenseAnalytics.collectAsState()
    val projects         by viewModel.projects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val currentProject   by viewModel.currentProject.collectAsState()
    val isSyncReady      by viewModel.isSyncReady.collectAsState()
    val locale           = java.util.Locale.getDefault()
    val currencyCode     = currentProject?.currency ?: "INR"
    val currencySymbol   = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    var showAddTxn       by remember { mutableStateOf(false) }
    val netWorthSnapshots by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())

    // Analytics Sheet State
    var activeBreakdownType by remember { mutableStateOf<BreakdownType?>(null) }

    if (showAddTxn) {
        AddTransactionDialog(
            onDismiss = { showAddTxn = false },
            onConfirm = { txn -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addTransaction(txn); showAddTxn = false },
            rememberLastCategory = { isIncome -> viewModel.rememberLastTransactionCategory(isIncome) },
            onRecordCategory = { isIncome, cat -> viewModel.recordTransactionCategory(isIncome, cat) },
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
            icon = sheetIcon,
            iconColor = sheetColor,
            onDismiss = { activeBreakdownType = null },
            currencyCode = currencyCode
        )
    }

    if (!isSyncReady) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
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
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1 + 2. Net Worth Card with integrated project switcher
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Emerald700)
        ) {
            Box {
                // Decorative circle top-right
                Box(
                    Modifier.size(120.dp)
                        .clip(CircleShape)
                        .background(Emerald600.copy(alpha = 0.4f))
                        .align(Alignment.TopEnd)
                )
                Box(
                    Modifier.size(80.dp)
                        .offset(x = 20.dp, y = (-20).dp)
                        .clip(CircleShape)
                        .background(Emerald500.copy(alpha = 0.2f))
                        .align(Alignment.TopEnd)
                )
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 20.dp)) {
                    // Project switcher row — compact, inside card
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            projects.forEach { project ->
                                val sel = project.id == currentProjectId
                                Surface(
                                    onClick = { viewModel.switchProject(project.id) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (sel) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, Color.White.copy(alpha = if (sel) 0f else 0.3f)
                                    )
                                ) {
                                    Text(
                                        project.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { onNavigateToScreen("projects") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("Manage", style = MaterialTheme.typography.labelSmall,
                                color = Emerald200.copy(alpha = 0.8f))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                        Column {
                            Text("Total Net Worth", style = MaterialTheme.typography.labelMedium,
                                color = Emerald200.copy(alpha = 0.8f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = CurrencyFormatter.hero(summary.netWorth, currencyCode, locale),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("Assets", style = MaterialTheme.typography.labelSmall,
                                color = Emerald200.copy(alpha = 0.6f))
                            Text(CurrencyFormatter.hero(summary.totalAssets, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.9f))
                        }
                        Text("·", color = Emerald200.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodySmall)
                        Column {
                            Text("Liabilities", style = MaterialTheme.typography.labelSmall,
                                color = Emerald200.copy(alpha = 0.6f))
                            Text(CurrencyFormatter.hero(summary.totalDebtPrincipal + summary.totalDebtInterest, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    WealthDistributionBar(
                        cash = summary.totalCash,
                        investments = summary.totalInvestments,
                        interestLoans = summary.totalInterestLoans,
                        handLoans = summary.totalHandLoans
                    )

                    // Sparkline — 7-day net worth trend
                    if (netWorthSnapshots.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        val recentSnaps = netWorthSnapshots.sortedBy { it.date }.takeLast(7)
                        val minVal = recentSnaps.minOf { it.netWorth }.toFloat()
                        val maxVal = recentSnaps.maxOf { it.netWorth }.toFloat()
                        val range  = (maxVal - minVal).takeIf { it > 0f } ?: 1f
                        val trend  = recentSnaps.last().netWorth - recentSnaps.first().netWorth
                        val sparkColor = if (trend >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.weight(1f).height(36.dp)) {
                                val pts = recentSnaps.mapIndexed { i, s ->
                                    val x = if (recentSnaps.size == 1) size.width / 2
                                            else i * size.width / (recentSnaps.size - 1)
                                    val y = size.height - ((s.netWorth.toFloat() - minVal) / range) * size.height
                                    androidx.compose.ui.geometry.Offset(x, y)
                                }
                                for (i in 0 until pts.size - 1) {
                                    drawLine(
                                        color = sparkColor.copy(alpha = 0.7f),
                                        start = pts[i], end = pts[i + 1],
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                                drawCircle(sparkColor, radius = 4.dp.toPx(), center = pts.last())
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val trendStr = if (trend >= 0) "+${CurrencyFormatter.hero(trend, currencyCode, locale)}"
                                               else            CurrencyFormatter.hero(trend, currencyCode, locale)
                                Text(trendStr, style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = sparkColor)
                                Text("${recentSnaps.size}d trend", style = MaterialTheme.typography.labelSmall, color = Emerald200.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
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
                                AccountGrowthIndicator(growth, currencyCode, locale)
                            }
                        }
                        Text(CurrencyFormatter.hero(balance, currencyCode, locale),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

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
            MetricCard("Idle Cash", CurrencyFormatter.hero(summary.totalCash, currencyCode, locale), SemanticBlue, Modifier.weight(1f)) { activeBreakdownType = BreakdownType.IDLE_CASH }
            MetricCard("Growing Assets", CurrencyFormatter.hero(summary.totalInvestments + summary.totalInterestLoans, currencyCode, locale), SemanticAmber, Modifier.weight(1f)) { activeBreakdownType = BreakdownType.GROWING_ASSETS }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Hand Loans", CurrencyFormatter.hero(summary.totalHandLoans, currencyCode, locale), Carbon500, Modifier.weight(1f)) { activeBreakdownType = BreakdownType.HAND_LOANS }
            MetricCard("Total Owed", CurrencyFormatter.hero(summary.totalDebtPrincipal + summary.totalDebtInterest, currencyCode, locale), SemanticRed, Modifier.weight(1f)) { onNavigateToScreen("debts") }
        }

        Spacer(Modifier.height(FabBottomPadding))
    }
}

enum class BreakdownType {
    IDLE_CASH, GROWING_ASSETS, HAND_LOANS
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = color)
            }
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(containerColor = color.copy(alpha = 0.07f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = color)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
