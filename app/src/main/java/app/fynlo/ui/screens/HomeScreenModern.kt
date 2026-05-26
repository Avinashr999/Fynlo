package app.fynlo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.components.AddTransactionDialog
import app.fynlo.ui.components.PortfolioBreakdownSheet
import app.fynlo.ui.theme.*
import java.util.Locale

/**
 * Modern neo-bank styled dashboard (prototype).
 * Airy layout, big hero number on a clean surface, soft cards, restrained
 * color. Reuses the exact data bindings and interactions of HomeScreen.
 */
@Composable
fun HomeScreenModern(viewModel: FinanceViewModel, onNavigateToScreen: (String) -> Unit = {}) {
    LaunchedEffect(Unit) { app.fynlo.data.Analytics.screenView("Home") }
    val haptic            = LocalHapticFeedback.current
    val summary           by viewModel.financialSummary.collectAsState()
    val accounts          by viewModel.accounts.collectAsState()
    val projects          by viewModel.projects.collectAsState()
    val currentProjectId  by viewModel.currentProjectId.collectAsState()
    val currentProject    by viewModel.currentProject.collectAsState()
    val isSyncReady       by viewModel.isSyncReady.collectAsState()
    val locale            = Locale.getDefault()
    val currencyCode      = currentProject?.currency ?: "INR"
    val cs                = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    var showAddTxn        by remember { mutableStateOf(false) }
    var addTxnIncome      by remember { mutableStateOf(false) }
    val netWorthSnapshots by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    var activeBreakdownType by remember { mutableStateOf<BreakdownType?>(null) }

    // C02 step 3: surface lastRecalcAt as a small "Last updated X ago"
    // subtitle below the hero net-worth number. Tells the user the figures
    // are fresh; reassures them the auto-recalc-on-launch (Stage 1) actually
    // ran. 0L means "no recalc has ever run" — rendered as "Not recalculated yet".
    val context = LocalContext.current
    val lastRecalcAt by app.fynlo.data.UserPreferences
        .lastRecalcAt(context).collectAsState(initial = 0L)

    fun fmt(v: Double) = CurrencyFormatter.hero(v, currencyCode, locale)

    // Map account name → an icon based on its type (Bank/Cash/UPI/Trading)
    val typeByName = remember(accounts) { accounts.associate { it.name to it.type.lowercase() } }
    val iconFor: (String) -> ImageVector = { name ->
        when (typeByName[name]) {
            "cash"    -> Icons.Default.Payments
            "bank"    -> Icons.Default.AccountBalance
            "upi"     -> Icons.Default.Smartphone
            "trading" -> Icons.AutoMirrored.Filled.TrendingUp
            else      -> Icons.Default.AccountBalanceWallet
        }
    }

    if (showAddTxn) {
        AddTransactionDialog(
            onDismiss = { showAddTxn = false },
            onConfirm = { txn -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addTransaction(txn); showAddTxn = false },
            rememberLastCategory = { isIncome -> viewModel.rememberLastTransactionCategory(isIncome) },
            onRecordCategory = { isIncome, cat -> viewModel.recordTransactionCategory(isIncome, cat) },
            initialIsIncome = addTxnIncome
        )
    }

    if (activeBreakdownType != null) {
        val title = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> "Idle Cash Breakdown"
            BreakdownType.GROWING_ASSETS -> "Growing Assets Breakdown"
            BreakdownType.HAND_LOANS -> "Hand Loans Breakdown"
            else -> ""
        }
        val icon = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> Icons.Default.AccountBalance
            BreakdownType.GROWING_ASSETS -> Icons.Default.TrendingUp
            BreakdownType.HAND_LOANS -> Icons.Default.Handshake
            else -> Icons.Default.Info
        }
        val color = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> SemanticBlue
            BreakdownType.GROWING_ASSETS -> SemanticAmber
            BreakdownType.HAND_LOANS -> Carbon500
            else -> Color.Gray
        }
        val data = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> summary.accountBreakdown
            BreakdownType.GROWING_ASSETS -> {
                val combined = mutableMapOf<String, Double>()
                summary.investmentTypeBreakdown.forEach { (k, v) -> combined["$k (Invest)"] = v }
                summary.interestLendingBreakdown.forEach { (k, v) -> combined["$k (Loan)"] = v }
                combined
            }
            BreakdownType.HAND_LOANS -> summary.handLendingBreakdown
            else -> emptyMap()
        }
        PortfolioBreakdownSheet(title, data, cs, icon, color) { activeBreakdownType = null }
    }

    if (!isSyncReady) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = Emerald500)
                Text("Syncing your data…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val greeting = remember {
        when (java.time.LocalTime.now().hour) {
            in 5..11  -> "Good morning ☀️"
            in 12..16 -> "Good afternoon 🌤️"
            in 17..20 -> "Good evening 🌆"
            else      -> "Good night 🌙"
        }
    }

    app.fynlo.ui.components.PullRefresh(viewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Greeting + project switcher ───────────────────────────────────────
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (projects.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    projects.forEach { project ->
                        val sel = project.id == currentProjectId
                        Surface(
                            onClick = { viewModel.switchProject(project.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (sel) Emerald500.copy(alpha = 0.12f) else Color.Transparent,
                            border = if (sel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Text(project.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal),
                                color = if (sel) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Hero: big net worth number — tap to open Net Worth History ────────
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNavigateToScreen("net_worth_hist") }
                .padding(vertical = 2.dp)
        ) {
            Text("Total net worth", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                text = CurrencyFormatter.hero(summary.netWorth, currencyCode, locale),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // C02 step 3: "Last updated 2 min ago" subtitle. Uses Android's
            // DateUtils.getRelativeTimeSpanString — returns "2 min ago",
            // "Yesterday", "3 days ago" etc. with appropriate granularity.
            Spacer(Modifier.height(4.dp))
            val lastUpdatedLabel = if (lastRecalcAt > 0L) {
                "Last updated " + android.text.format.DateUtils
                    .getRelativeTimeSpanString(
                        lastRecalcAt,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    )
                    .toString()
                    .replaceFirstChar { it.lowercase(locale) }
            } else {
                "Not recalculated yet"
            }
            Text(
                text  = lastUpdatedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Trend pill + sparkline
        if (netWorthSnapshots.size >= 2) {
            val recent = netWorthSnapshots.sortedBy { it.date }.takeLast(7)
            val minV = recent.minOf { it.netWorth }.toFloat()
            val maxV = recent.maxOf { it.netWorth }.toFloat()
            val range = (maxV - minV).takeIf { it > 0f } ?: 1f
            val trend = recent.last().netWorth - recent.first().netWorth
            val up = trend >= 0
            val trendColor = if (up) Emerald500 else SemanticRed
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = trendColor.copy(alpha = 0.12f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            null, Modifier.size(13.dp), tint = trendColor)
                        Text(
                            if (up) "+${CurrencyFormatter.hero(trend, currencyCode, locale)}"
                            else    CurrencyFormatter.hero(trend, currencyCode, locale),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = trendColor)
                    }
                }
                Canvas(Modifier.weight(1f).height(32.dp)) {
                    val pts = recent.mapIndexed { i, s ->
                        val x = if (recent.size == 1) size.width / 2 else i * size.width / (recent.size - 1)
                        val y = size.height - ((s.netWorth.toFloat() - minV) / range) * size.height
                        Offset(x, y)
                    }
                    for (i in 0 until pts.size - 1) {
                        drawLine(trendColor.copy(alpha = 0.6f), pts[i], pts[i + 1],
                            strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                    if (pts.isNotEmpty()) drawCircle(trendColor, radius = 3.5.dp.toPx(), center = pts.last())
                }
                Text("7d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Assets / Liabilities thin summary row (each tappable)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(24.dp)) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onNavigateToScreen("reports_hub") }.padding(vertical = 2.dp, horizontal = 2.dp)) {
                Text("Assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(summary.totalAssets), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onNavigateToScreen("loans_hub?tab=1") }.padding(vertical = 2.dp, horizontal = 2.dp)) {
                Text("Liabilities", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(summary.totalDebtPrincipal + summary.totalDebtInterest),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Net-worth trend chart card (tap → full history) ───────────────────
        NetWorthTrendCard(netWorthSnapshots.sortedBy { it.date }, cs, locale) { onNavigateToScreen("net_worth_hist") }

        Spacer(Modifier.height(28.dp))

        // ── Quick actions ─────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            NeoAction("Expense", Icons.Default.Remove, SemanticRed, Modifier.weight(1f)) { addTxnIncome = false; showAddTxn = true }
            NeoAction("Income", Icons.Default.Add, Emerald500, Modifier.weight(1f)) { addTxnIncome = true; showAddTxn = true }
            NeoAction("Lend", Icons.Default.Handshake, SemanticBlue, Modifier.weight(1f)) { onNavigateToScreen("loans_hub") }
            NeoAction("History", Icons.Default.History, Carbon500, Modifier.weight(1f)) { onNavigateToScreen("history") }
        }

        Spacer(Modifier.height(28.dp))

        // ── Accounts ──────────────────────────────────────────────────────────
        SectionHeader("Accounts")
        Spacer(Modifier.height(4.dp))
        if (summary.accountBreakdown.isEmpty()) {
            Text("No accounts yet. Tap + to add one.", Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            summary.accountBreakdown.entries.forEachIndexed { idx, entry ->
                if (idx > 0) HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                NeoAccountRow(entry.key, fmt(entry.value), iconFor(entry.key)) { onNavigateToScreen("statement/${entry.key}") }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Insights ──────────────────────────────────────────────────────────
        SectionHeader("Insights")
        Spacer(Modifier.height(4.dp))
        NeoInsightRow("Idle Cash", fmt(summary.totalCash), SemanticBlue) { activeBreakdownType = BreakdownType.IDLE_CASH }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        NeoInsightRow("Growing Assets", fmt(summary.totalInvestments + summary.totalInterestLoans), SemanticAmber) { activeBreakdownType = BreakdownType.GROWING_ASSETS }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        NeoInsightRow("Hand Loans", fmt(summary.totalHandLoans), Carbon500) { activeBreakdownType = BreakdownType.HAND_LOANS }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        NeoInsightRow("Total Owed", fmt(summary.totalDebtPrincipal + summary.totalDebtInterest), SemanticRed) { onNavigateToScreen("loans_hub?tab=1") }

        Spacer(Modifier.height(120.dp))
    }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
}

@Composable
private fun NeoAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NeoAccountRow(name: String, balance: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Emerald500.copy(alpha = 0.10f)), Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = Emerald500)
            }
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        }
        Text(balance, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun NeoInsightRow(label: String, value: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        }
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun NetWorthTrendCard(
    snaps: List<app.fynlo.data.model.NetWorthSnapshot>,
    cs: String,
    locale: Locale,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Net worth trend", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            if (snaps.size < 2) {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    Text("Your trend appears as your balances change over time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val pts = snaps.takeLast(30)
                val minV = pts.minOf { it.netWorth }.toFloat()
                val maxV = pts.maxOf { it.netWorth }.toFloat()
                val range = (maxV - minV).takeIf { it > 0f } ?: 1f
                val up = pts.last().netWorth >= pts.first().netWorth
                val lineColor = if (up) Emerald500 else SemanticRed
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val offs = pts.mapIndexed { i, s ->
                        val x = i * size.width / (pts.size - 1)
                        val y = size.height - ((s.netWorth.toFloat() - minV) / range) * size.height * 0.9f - size.height * 0.05f
                        Offset(x, y)
                    }
                    // filled area under the line
                    val area = androidx.compose.ui.graphics.Path().apply {
                        moveTo(offs.first().x, size.height)
                        offs.forEach { lineTo(it.x, it.y) }
                        lineTo(offs.last().x, size.height)
                        close()
                    }
                    drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f))))
                    for (i in 0 until offs.size - 1) {
                        drawLine(lineColor, offs[i], offs[i + 1], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = offs.last())
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("$cs${String.format(locale, "%,.0f", pts.first().netWorth)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$cs${String.format(locale, "%,.0f", pts.last().netWorth)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = lineColor)
                }
            }
        }
    }
}
