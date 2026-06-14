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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Account
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
    val isPrivacy         by viewModel.isPrivacyMode.collectAsState()
    val locale            = LocalLocale.current.platformLocale
    val currencyCode      = currentProject?.currency ?: "INR"
    val cs                = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    var showAddTxn        by remember { mutableStateOf(false) }
    var addTxnIncome      by remember { mutableStateOf(false) }
    var accountDialogInitial by remember { mutableStateOf<Account?>(null) }
    var accountDialogDefaultType by remember { mutableStateOf("Bank") }
    var showAccountDialog by remember { mutableStateOf(false) }
    var transferFromAccount by remember { mutableStateOf<Account?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    val netWorthSnapshots by viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())
    var activeBreakdownType by remember { mutableStateOf<BreakdownType?>(null) }

    // C02 step 3: surface lastRecalcAt as a small "Last updated X ago"
    // subtitle below the hero net-worth number. Tells the user the figures
    // are fresh; reassures them the auto-recalc-on-launch (Stage 1) actually
    // ran. 0L means "no recalc has ever run" — rendered as "Not recalculated yet".
    val context = LocalContext.current
    val lastRecalcAt by app.fynlo.data.UserPreferences
        .lastRecalcAt(context).collectAsState(initial = 0L)

    fun fmt(v: Double) = if (isPrivacy) "••••" else CurrencyFormatter.hero(v, currencyCode, locale)

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

    // 3.2.59 — collect account/transaction lists once for both AddTxn dialog
    // and the orphan-repair banner.
    val allInvestmentsHome by viewModel.investments.collectAsState()
    val allDebtsHome by viewModel.debts.collectAsState()
    val allBorrowersHome by viewModel.borrowers.collectAsState()
    val allTransactionsHome by viewModel.transactions.collectAsState()
    val activeAccounts = remember(accounts) { accounts.filterNot { it.isClosedAccount() } }
    val activeAccountNames = remember(activeAccounts) { activeAccounts.map { it.name } }
    val cashInHandBreakdown = remember(activeAccounts) {
        activeAccounts
            .filter { it.isCashInHandAccount() }
            .associate { it.name to it.balance }
    }
    val bankCashBreakdown = remember(activeAccounts, cashInHandBreakdown) {
        val cashAccountIds = activeAccounts
            .filter { it.isCashInHandAccount() }
            .map { it.id }
            .toSet()
        activeAccounts
            .filterNot { it.id in cashAccountIds }
            .associate { it.name to it.balance }
    }
    val cashInHandTotal = remember(cashInHandBreakdown) { cashInHandBreakdown.values.sum() }
    val bankCashTotal = remember(bankCashBreakdown) { bankCashBreakdown.values.sum() }
    val accountNames = activeAccountNames
    val orphans = remember(allTransactionsHome, accountNames) {
        app.fynlo.logic.OrphanTransactionsScanner.scan(allTransactionsHome, accountNames)
    }
    val today = remember { java.time.LocalDate.now() }
    val recentActivityCount = remember(allTransactionsHome, today) {
        allTransactionsHome.count { txn ->
            runCatching { java.time.LocalDate.parse(txn.date) }
                .getOrNull()
                ?.let { !it.isBefore(today.minusDays(7)) && !it.isAfter(today) } == true
        }
    }
    val dueSoonCount = remember(allBorrowersHome, allDebtsHome, today) {
        fun isDueSoon(due: String): Boolean {
            val date = runCatching { java.time.LocalDate.parse(due) }.getOrNull() ?: return false
            return !date.isBefore(today) && !date.isAfter(today.plusDays(7))
        }
        allBorrowersHome.count { it.status != "Cleared" && isDueSoon(it.due) } +
            allDebtsHome.count { it.status != "Cleared" && isDueSoon(it.due) }
    }
    val isFreshBook = activeAccounts.isEmpty() &&
        allTransactionsHome.isEmpty() &&
        allInvestmentsHome.isEmpty() &&
        allDebtsHome.isEmpty() &&
        allBorrowersHome.isEmpty()
    var showOrphanDialog by remember { mutableStateOf(false) }

    if (showAddTxn) {
        AddTransactionDialog(
            onDismiss = { showAddTxn = false },
            onConfirm = { txn -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addTransaction(txn); showAddTxn = false },
            rememberLastCategory = { isIncome -> viewModel.rememberLastTransactionCategory(isIncome) },
            onRecordCategory = { isIncome, cat -> viewModel.recordTransactionCategory(isIncome, cat) },
            // 3.2.81 (C13 #5) — "Repeat monthly?" → also create a recurring template.
            onRepeatMonthly = { txn -> viewModel.addRecurringTransaction(app.fynlo.logic.toRecurringTemplate(txn)) },
            initialIsIncome = addTxnIncome,
            bankAccounts    = activeAccountNames,
            investmentNames = allInvestmentsHome.map { it.name },
            debtNames       = allDebtsHome.map { it.name },
        )
    }

    if (showAccountDialog) {
        val initial = accountDialogInitial
        val linkedCount = remember(initial, allTransactionsHome) {
            initial?.let { account ->
                allTransactionsHome.count { it.linksAccount(account) }
            } ?: 0
        }
        val canClose = initial != null && kotlin.math.abs(initial.balance) <= 0.005
        val canDelete = initial != null && linkedCount == 0
        AccountManageDialog(
            initial = initial,
            defaultType = accountDialogDefaultType,
            currencyCode = currencyCode,
            linkedTransactionCount = linkedCount,
            canTransfer = initial != null && activeAccounts.any { it.id != initial.id },
            canClose = canClose,
            canDelete = canDelete,
            onDismiss = { showAccountDialog = false },
            onConfirm = { account ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.saveAccount(account)
                showAccountDialog = false
            },
            onTransfer = initial?.let { account ->
                {
                    transferFromAccount = account
                    showAccountDialog = false
                    showTransferDialog = true
                }
            },
            onClose = initial?.let { account ->
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.closeAccount(account)
                    showAccountDialog = false
                }
            },
            onDelete = initial?.let { account ->
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteUnusedAccount(account)
                    showAccountDialog = false
                }
            },
        )
    }

    if (showTransferDialog) {
        val from = transferFromAccount
        if (from != null) {
            AccountTransferDialog(
                from = from,
                accounts = activeAccounts.filter { it.id != from.id },
                currencyCode = currencyCode,
                onDismiss = { showTransferDialog = false },
                onConfirm = { to, amount ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.transferBetweenAccounts(from, to, amount)
                    showTransferDialog = false
                },
            )
        }
    }

    if (showOrphanDialog) {
        app.fynlo.ui.components.OrphanRepairDialog(
            orphans      = orphans,
            accounts     = accountNames,
            currencyCode = currencyCode,
            onFix        = { old, new -> viewModel.editTransaction(old, new) },
            onDismiss    = { showOrphanDialog = false },
        )
    }

    if (activeBreakdownType != null) {
        val title = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> "Office & Petty Cash Breakdown"
            BreakdownType.BANK_CASH -> "Bank Cash Breakdown"
            BreakdownType.CASH_IN_HAND -> "Cash in Hand Breakdown"
            BreakdownType.GROWING_ASSETS -> "Growing Assets Breakdown"
            BreakdownType.HAND_LOANS -> "Hand Loans Breakdown"
            else -> ""
        }
        val icon = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> Icons.Default.AccountBalance
            BreakdownType.BANK_CASH -> Icons.Default.AccountBalance
            BreakdownType.CASH_IN_HAND -> Icons.Default.Payments
            BreakdownType.GROWING_ASSETS -> Icons.Default.TrendingUp
            BreakdownType.HAND_LOANS -> Icons.Default.Handshake
            else -> Icons.Default.Info
        }
        // 3.2.65 — fallback was Color.Gray; theme-aware now.
        val unknownTypeTint = MaterialTheme.colorScheme.onSurfaceVariant
        val color = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> SemanticBlue
            BreakdownType.BANK_CASH -> SemanticBlue
            BreakdownType.CASH_IN_HAND -> Emerald500
            BreakdownType.GROWING_ASSETS -> SemanticAmber
            BreakdownType.HAND_LOANS -> Carbon500
            else -> unknownTypeTint
        }
        val data = when (activeBreakdownType) {
            BreakdownType.IDLE_CASH -> summary.accountBreakdown
            BreakdownType.BANK_CASH -> bankCashBreakdown
            BreakdownType.CASH_IN_HAND -> cashInHandBreakdown
            BreakdownType.GROWING_ASSETS -> {
                val combined = mutableMapOf<String, Double>()
                summary.investmentTypeBreakdown.forEach { (k, v) -> combined["$k (Invest)"] = v }
                summary.interestLendingBreakdown.forEach { (k, v) -> combined["$k (Loan)"] = v }
                combined
            }
            BreakdownType.HAND_LOANS -> summary.handLendingBreakdown
            else -> emptyMap()
        }
        PortfolioBreakdownSheet(title, data, icon, color, { activeBreakdownType = null }, currencyCode = currencyCode)
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

        // 3.2.59 — orphan-transaction banner. Pre-3.2.59 the AddTransaction
        // dialog accepted free-text bank names that could miss the
        // canonical account row; surface the count + a one-tap repair
        // action so users can re-attach orphans to real accounts (which
        // then reflects in balances + net worth on next recalc).
        if (orphans.isNotEmpty()) {
            Surface(
                onClick = { showOrphanDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SemanticAmber.copy(alpha = 0.12f),
                border = BorderStroke(0.5.dp, SemanticAmber.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.WarningAmber, null, tint = SemanticAmber, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${orphans.size} transaction${if (orphans.size == 1) "" else "s"} not linked to any account",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = SemanticAmber,
                        )
                        Text(
                            "Tap to re-point them so your balances reflect the spend.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Default.ArrowForward, null, tint = SemanticAmber, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

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
        if (isFreshBook) {
            FreshStartCard(
                projectName = currentProject?.name ?: "Personal",
                onAddAccount = {
                    accountDialogInitial = null
                    accountDialogDefaultType = "Bank"
                    showAccountDialog = true
                },
                onAddTransaction = {
                    addTxnIncome = false
                    showAddTxn = true
                },
                onCreateProject = { onNavigateToScreen("projects") },
            )
            Spacer(Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNavigateToScreen("net_worth_hist") }
                .padding(vertical = 2.dp)
        ) {
            Text("Total net worth", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            val nwText = if (isPrivacy) "••••" else CurrencyFormatter.hero(summary.netWorth, currencyCode, locale)
            Text(
                text = nwText,
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
                        val trendText = if (isPrivacy) "••••"
                                        else (if (up) "+" else "") + CurrencyFormatter.hero(trend, currencyCode, locale)
                        Text(
                            text = trendText,
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
        NetWorthTrendCard(netWorthSnapshots.sortedBy { it.date }, cs, locale, isPrivacy) { onNavigateToScreen("net_worth_hist") }

        Spacer(Modifier.height(28.dp))

        // ── Quick actions ─────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            NeoAction("Expense", Icons.Default.Remove, SemanticRed, Modifier.weight(1f)) { addTxnIncome = false; showAddTxn = true }
            NeoAction("Income", Icons.Default.Add, Emerald500, Modifier.weight(1f)) { addTxnIncome = true; showAddTxn = true }
            NeoAction("Lend", Icons.Default.Handshake, SemanticBlue, Modifier.weight(1f)) { onNavigateToScreen("loans_hub") }
            NeoAction("History", Icons.Default.History, Carbon500, Modifier.weight(1f)) { onNavigateToScreen("history") }
        }

        Spacer(Modifier.height(18.dp))

        if (!isFreshBook) {
            DashboardNudges(
                dueSoonCount = dueSoonCount,
                recentActivityCount = recentActivityCount,
                onOpenDues = { onNavigateToScreen("loans_hub") },
                onAddTransaction = {
                    addTxnIncome = false
                    showAddTxn = true
                },
                onOpenSettings = { onNavigateToScreen("settings") },
            )
            Spacer(Modifier.height(22.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(28.dp))

        // ── Accounts ──────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            SectionHeader("Accounts")
            IconButton(onClick = {
                accountDialogInitial = null
                accountDialogDefaultType = "Bank"
                showAccountDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Account", tint = Emerald500)
            }
        }
        Spacer(Modifier.height(4.dp))
        val activeAccountByName = activeAccounts.associateBy { it.name }
        val visibleAccountEntries = summary.accountBreakdown.entries.filter { activeAccountByName[it.key] != null }
        if (visibleAccountEntries.isEmpty()) {
            EmptyAccountsCard(
                onAddAccount = {
                    accountDialogInitial = null
                    accountDialogDefaultType = "Bank"
                    showAccountDialog = true
                },
                onAddCash = {
                    accountDialogInitial = null
                    accountDialogDefaultType = "Cash"
                    showAccountDialog = true
                },
            )
        } else {
            visibleAccountEntries.forEachIndexed { idx, entry ->
                if (idx > 0) HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                val account = activeAccountByName[entry.key]
                NeoAccountRow(
                    name = entry.key,
                    balance = fmt(entry.value),
                    icon = iconFor(entry.key),
                    onClick = { onNavigateToScreen("statement/${entry.key}") },
                    onEdit = account?.let {
                        {
                            accountDialogInitial = it
                            showAccountDialog = true
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Insights ──────────────────────────────────────────────────────────
        SectionHeader("Insights")
        Spacer(Modifier.height(4.dp))
        NeoInsightRow("Bank Cash", fmt(bankCashTotal), SemanticBlue) { activeBreakdownType = BreakdownType.BANK_CASH }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        NeoInsightRow("Cash in Hand", fmt(cashInHandTotal), Emerald500) { activeBreakdownType = BreakdownType.CASH_IN_HAND }
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

private const val CLOSED_ACCOUNT_MARKER = "[fynlo:closed-account]"

private fun Account.isClosedAccount(): Boolean = notes.contains(CLOSED_ACCOUNT_MARKER)

private fun Account.isCashInHandAccount(): Boolean {
    val normalizedType = type.trim().lowercase()
    val normalizedName = name.trim().lowercase()
    return normalizedType == "cash" ||
        "cash in hand" in normalizedName ||
        "personal cash" in normalizedName ||
        "petty cash" in normalizedName
}

private fun app.fynlo.data.model.Transaction.linksAccount(account: Account): Boolean =
    fromAcctId == account.id ||
        toAcctId == account.id ||
        (fromAcctId.isBlank() && fromAcct == account.name) ||
        (toAcctId.isBlank() && toAcct == account.name)

@Composable
private fun FreshStartCard(
    projectName: String,
    onAddAccount: () -> Unit,
    onAddTransaction: () -> Unit,
    onCreateProject: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Emerald500.copy(alpha = 0.10f),
        border = BorderStroke(0.5.dp, Emerald500.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                app.fynlo.ui.components.FynloBrandMark(size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "$projectName is ready",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "Start with one account, then log the first money move.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuidedActionChip("Account", Icons.Default.AccountBalance, Emerald500, Modifier.weight(1f), onAddAccount)
                GuidedActionChip("Transaction", Icons.Default.ReceiptLong, SemanticBlue, Modifier.weight(1f), onAddTransaction)
                GuidedActionChip("Project", Icons.Default.Folder, SemanticAmber, Modifier.weight(1f), onCreateProject)
            }
        }
    }
}

@Composable
private fun DashboardNudges(
    dueSoonCount: Int,
    recentActivityCount: Int,
    onOpenDues: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val items = buildList {
        if (dueSoonCount > 0) {
            add(
                NudgeItem(
                    title = "$dueSoonCount due soon",
                    subtitle = "Review loans and debts due in 7 days",
                    icon = Icons.Default.Event,
                    color = SemanticAmber,
                    onClick = onOpenDues,
                )
            )
        }
        if (recentActivityCount == 0) {
            add(
                NudgeItem(
                    title = "No activity this week",
                    subtitle = "Log one entry to keep this book fresh",
                    icon = Icons.Default.Edit,
                    color = SemanticBlue,
                    onClick = onAddTransaction,
                )
            )
        }
        add(
            NudgeItem(
                title = "Keep a backup",
                subtitle = "Export from Settings after important changes",
                icon = Icons.Default.Cloud,
                color = Emerald500,
                onClick = onOpenSettings,
            )
        )
    }.take(2)

    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            SmartNudgeCard(item)
        }
    }
}

private data class NudgeItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SmartNudgeCard(item: NudgeItem) {
    Surface(
        onClick = item.onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(item.color.copy(alpha = 0.12f)), Alignment.Center) {
                Icon(item.icon, null, Modifier.size(20.dp), tint = item.color)
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Default.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyAccountsCard(
    onAddAccount: () -> Unit,
    onAddCash: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Emerald500.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(22.dp), tint = Emerald500)
                }
                Column(Modifier.weight(1f)) {
                    Text("No accounts yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Add cash or a bank account to start tracking balances.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuidedActionChip("Cash", Icons.Default.Payments, Emerald500, Modifier.weight(1f), onAddCash)
                GuidedActionChip("Bank", Icons.Default.AccountBalance, SemanticBlue, Modifier.weight(1f), onAddAccount)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GuidedActionChip(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(17.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
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
private fun NeoAccountRow(
    name: String,
    balance: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(balance, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Account", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManageDialog(
    initial: Account?,
    defaultType: String,
    currencyCode: String,
    linkedTransactionCount: Int,
    canTransfer: Boolean,
    canClose: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Account) -> Unit,
    onTransfer: (() -> Unit)?,
    onClose: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var type by remember(initial, defaultType) { mutableStateOf(initial?.type ?: defaultType) }
    var balance by remember(initial) {
        mutableStateOf(initial?.balance?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var expanded by remember { mutableStateOf(false) }
    val accountTypes = listOf("Bank", "Cash", "UPI", "Trading")
    val amount = balance.toDoubleOrNull()
    val isValid = name.isNotBlank() && amount != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        accountTypes.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    type = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    label = { Text("Balance (${app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    Text(
                        "Account actions",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    OutlinedButton(
                        enabled = canTransfer && onTransfer != null,
                        onClick = { onTransfer?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Transfer balance")
                    }
                    OutlinedButton(
                        enabled = canClose && onClose != null,
                        onClick = { onClose?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Close account")
                    }
                    Text(
                        if (canClose) "Closed accounts are hidden from new entries but history stays intact."
                        else "Transfer the remaining balance before closing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        enabled = canDelete && onDelete != null,
                        onClick = { onDelete?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticRed),
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete account")
                    }
                    Text(
                        if (canDelete) "Delete is available because this account has no transactions."
                        else "$linkedTransactionCount linked transaction${if (linkedTransactionCount == 1) "" else "s"} found. Close it instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        Account(
                            id = initial?.id?.takeIf { it.isNotBlank() } ?: app.fynlo.logic.Ids.newId(),
                            name = name.trim(),
                            type = type,
                            balance = amount ?: 0.0,
                            icon = initial?.icon ?: "",
                            color = initial?.color ?: "#3b82f6",
                            notes = initial?.notes ?: "",
                            projectId = initial?.projectId ?: "personal",
                            createdAt = initial?.createdAt ?: 0L,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTransferDialog(
    from: Account,
    accounts: List<Account>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onConfirm: (Account, Double) -> Unit,
) {
    var target by remember(from, accounts) { mutableStateOf(accounts.firstOrNull()) }
    var amountText by remember(from) {
        mutableStateOf(kotlin.math.abs(from.balance).toBigDecimal().stripTrailingZeros().toPlainString())
    }
    var expanded by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
    val isValid = target != null && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "From ${from.name}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = target?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    target = account
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (${app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = { target?.let { onConfirm(it, amount ?: 0.0) } },
            ) { Text("Transfer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
    isPrivacy: Boolean,
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
                    val startText = if (isPrivacy) "••••" else "$cs${String.format(locale, "%,.0f", pts.first().netWorth)}"
                    val endText   = if (isPrivacy) "••••" else "$cs${String.format(locale, "%,.0f", pts.last().netWorth)}"
                    Text(startText,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(endText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = lineColor)
                }
            }
        }
    }
}
