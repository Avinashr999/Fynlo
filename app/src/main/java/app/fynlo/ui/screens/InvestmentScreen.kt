package app.fynlo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Investment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.ui.components.AddInvestmentDialog

import app.fynlo.ui.components.InvestmentSaveRequest
import java.util.Locale
import java.util.*
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentScreen(viewModel: FinanceViewModel) {
    val investments    by viewModel.investments.collectAsState()
    val summary        by viewModel.financialSummary.collectAsState()
    val isPrivacy      by viewModel.isPrivacyMode.collectAsState()
    val accounts       by viewModel.accounts.collectAsState()
    val debts          by viewModel.debts.collectAsState()
        val haptic = LocalHapticFeedback.current
val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode   = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)

    var editingInvest  by remember { mutableStateOf<Investment?>(null) }
    var updatingInvest by remember { mutableStateOf<Investment?>(null) }

    var deletingInvest  by remember { mutableStateOf<Investment?>(null) }
    var withdrawingInvest by remember { mutableStateOf<Investment?>(null) }
    var viewingHistory by remember { mutableStateOf<Investment?>(null) }
    var pendingDeleteIds by remember { mutableStateOf(emptySet<String>()) }
    val visibleInvestments = investments.filterNot { it.id in pendingDeleteIds }

    // ── Edit dialog ────────────────────────────────────────────────────────────
    if (editingInvest != null) {
        AddInvestmentDialog(
            accounts = accounts,
            debts    = debts,
            currencyCode = currencyCode,
            onDismiss = { editingInvest = null },
            onConfirm = { req: InvestmentSaveRequest ->
                if (editingInvest?.id?.isNotBlank() == true) {
                    viewModel.updateInvestment(req.investment)
                    viewModel.showFeedback("Investment updated")
                } else {
                    when (req.sourceType) {
                        "account"       -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addInvestmentFundedByAccount(req.investment, req.sourceAccountName, req.sourceAccountId) }
                        "existing_debt" -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); req.sourceDebt?.let { viewModel.addInvestmentFundedByExistingDebt(req.investment, it) } }
                        "new_loan"      -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); req.newLoan?.let { viewModel.addInvestmentFundedByNewLoan(req.investment, it) } }
                        else            -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.addInvestmentWithSource(req.investment, req.sourceAccountName) }
                    }
                    viewModel.showFeedback("Investment added")
                }
                editingInvest = null
            },
            initialInvestment = editingInvest
        )
    }

    // ── Update value dialog ────────────────────────────────────────────────────
    updatingInvest?.let { invest ->
        UpdateInvestmentValueDialog(
            investment = invest,
            currencySymbol = currencySymbol,
            currencyCode = currencyCode,
            onDismiss  = { updatingInvest = null },
            onConfirm  = { newVal, date, notes ->
                viewModel.addValuation(
                    app.fynlo.data.model.InvestmentValuation(
                        id = app.fynlo.logic.Ids.newId(),
                        investmentId = invest.id,
                        date = DateUtils.parseInput(date),
                        value = newVal,
                        notes = notes
                    )
                )
                viewModel.showFeedback("Valuation updated")
                updatingInvest = null
            }
        )
    }


    // ── Smart delete confirmation — shows options based on sourceType ──────────
    // ── Withdraw Dialog ─────────────────────────────────────────────────────
    withdrawingInvest?.let { inv ->
        var withdrawAmt by remember { mutableStateOf("") }
        var withdrawExpanded by remember { mutableStateOf(false) }
        val withdrawAccountOptions = if (accounts.isNotEmpty()) accounts
        else listOf(app.fynlo.data.model.Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 0.0))
        val preferredWithdrawAccount = remember(withdrawAccountOptions) {
            withdrawAccountOptions.firstOrNull { it.name.equals("Personal Cash", ignoreCase = true) }
                ?: withdrawAccountOptions
                    .filter { it.type.equals("Cash", true) || it.type.equals("Bank", true) }
                    .maxByOrNull { it.balance }
                ?: withdrawAccountOptions.first()
        }
        var withdrawAccount by remember(withdrawAccountOptions) { mutableStateOf(preferredWithdrawAccount) }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { withdrawingInvest = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Withdraw from Investment", style = MaterialTheme.typography.headlineSmall)
                    Text("${inv.name} - Current Value: ${CurrencyFormatter.detail(inv.currentVal, currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = withdrawAmt, onValueChange = { withdrawAmt = it },
                        label = { Text("Withdrawal Amount") }, prefix = { Text(currencySymbol) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(expanded = withdrawExpanded, onExpandedChange = { withdrawExpanded = !withdrawExpanded }) {
                        OutlinedTextField(
                            value = withdrawAccount.name, onValueChange = {}, readOnly = true,
                            label = { Text("Credit to account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = withdrawExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = withdrawExpanded, onDismissRequest = { withdrawExpanded = false }) {
                            withdrawAccountOptions.forEach { acct ->
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.fillMaxWidth(),
                                            androidx.compose.foundation.layout.Arrangement.SpaceBetween
                                        ) {
                                            Text(acct.name)
                                            Text(CurrencyFormatter.detail(acct.balance, currencyCode),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = app.fynlo.ui.theme.Emerald500)
                                        }
                                    },
                                    onClick = { withdrawAccount = acct; withdrawExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { withdrawingInvest = null }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val amt = withdrawAmt.toDoubleOrNull() ?: 0.0
                                if (amt > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.withdrawFromInvestment(inv, amt, withdrawAccount.name)
                                    viewModel.showFeedback("Withdrawal recorded")
                                    withdrawingInvest = null
                                }
                            },
                            enabled = withdrawAmt.toDoubleOrNull().let { it != null && it > 0.0 }
                        ) { Text("Withdraw") }
                    }
                }
            }
        }
    }

    deletingInvest?.let { inv ->
        var deleteInProgress by remember(inv.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deletingInvest = null },
            title = { Text("Delete Investment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${inv.name}  •  ${CurrencyFormatter.detail(inv.invested, currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    when (inv.sourceType) {
                        "account"  -> Text(
                            "This was funded from ${inv.fundingSource}. Do you want to restore ${CurrencyFormatter.detail(inv.invested, currencyCode)} back to that account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        "new_loan" -> Text(
                            "This investment has a linked loan (${inv.fundingSource}). Do you want to delete the loan record too?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            "This will permanently remove the investment record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Action buttons inside text area to avoid overlap
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (inv.sourceType) {
                            "account" -> Button(
                                onClick = {
                                    if (!deleteInProgress) {
                                        deleteInProgress = true
                                        pendingDeleteIds = pendingDeleteIds + inv.id
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.deleteInvestmentAndReverseAccount(inv)
                                        viewModel.showFeedback("Investment deleted and amount restored")
                                        deletingInvest = null
                                    }
                                },
                                enabled = !deleteInProgress,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Delete + Restore ${CurrencyFormatter.detail(inv.invested, currencyCode)} to ${inv.fundingSource.take(14)}")
                            }
                            "new_loan" -> Button(
                                onClick = {
                                    if (!deleteInProgress) {
                                        deleteInProgress = true
                                        pendingDeleteIds = pendingDeleteIds + inv.id
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.deleteInvestmentAndLinkedLoan(inv)
                                        viewModel.showFeedback("Investment and linked loan deleted")
                                        deletingInvest = null
                                    }
                                },
                                enabled = !deleteInProgress,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete Investment + Loan")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (!deleteInProgress) {
                                    deleteInProgress = true
                                    pendingDeleteIds = pendingDeleteIds + inv.id
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteInvestment(inv)
                                    viewModel.showFeedback("Investment deleted")
                                    deletingInvest = null
                                }
                            },
                            enabled = !deleteInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Delete Record Only")
                        }
                        TextButton(
                            onClick = { deletingInvest = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            },
            confirmButton = {},  // all actions in text area
            dismissButton = {}
        )
    }

    viewingHistory?.let { invest ->
        ValuationHistoryDialog(
            investment = invest,
            currencyCode = currencyCode,
            valuations = viewModel.getValuationsForInvestment(invest.id).collectAsState(initial = emptyList()).value,
            onDismiss = { viewingHistory = null }
        )
    }

    // C14 (3.2.24) — Home archetype migration: portfolio-level metrics
    // computed once so the hero block + allocation bar can render them
    // without re-walking the list. `netInvested` is invested minus
    // withdrawn (the user's money still in the market) — that's the
    // right denominator for "growth %" since pulled-out money doesn't
    // need to grow to break even.
    val portfolioValue   = visibleInvestments.sumOf { it.currentVal }
    val netInvested      = visibleInvestments.sumOf { it.invested - it.withdrawn }
    val portfolioGrowth  = portfolioValue - netInvested
    val growthPct        = if (netInvested > 0) (portfolioGrowth / netInvested) * 100 else 0.0
    val isPortfolioUp    = portfolioGrowth >= 0
    // Allocation by type (Stocks / Gold / FD / etc.) for the stacked bar.
    val allocation = visibleInvestments
        .groupBy { it.type.ifBlank { "Other" } }
        .mapValues { e -> e.value.sumOf { it.currentVal } }
        .entries.sortedByDescending { it.value }
    val locale = LocalLocale.current.platformLocale

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PremiumScreenHeader(
            title = "Invest",
            subtitle = "Track value, allocation, and returns without crowding the screen",
            action = {
                FilledIconButton(
                    onClick = {
                        editingInvest = Investment(id = "", name = "", type = "", invested = 0.0, currentVal = 0.0, date = "", notes = "", projectId = "")
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Emerald500,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Investment")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
        if (visibleInvestments.isEmpty()) {
            EmptyInvestState(onAdd = { editingInvest = Investment(id = "", name = "", type = "", invested = 0.0, currentVal = 0.0, date = "", notes = "", projectId = "") })
        } else {
            app.fynlo.ui.components.PullRefresh(viewModel) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = FabBottomPadding)
            ) {
                // C14 hero (audit #1): Portfolio Value + growth ₹ + growth %.
                // The growth amount + percent share the colour semantic
                // (Emerald = profit, Red = loss). Currently-Invested subtitle
                // gives context for the growth denominator so the percentage
                // isn't ambiguous about "of what."
                item {
                    LedgerHeroPanel(
                        label = "Portfolio value",
                        value = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(portfolioValue, currencyCode, locale),
                        subtitle = "${if (isPrivacy) "Hidden" else CurrencyFormatter.detail(netInvested, currencyCode, locale)} invested - ${app.fynlo.logic.pluralize(visibleInvestments.size, "holding")}",
                        containerColor = Emerald700,
                        modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                    ) {
                        if (netInvested > 0) {
                            val portfolioCagr = summary.investmentCagr
                            val portfolioXirr = summary.investmentXirr
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PortfolioMiniMetric(
                                    label = "Gain",
                                    value = if (isPrivacy) "Hidden" else if (isPortfolioUp) "+${CurrencyFormatter.detail(portfolioGrowth, currencyCode, locale)}" else CurrencyFormatter.negative(portfolioGrowth, currencyCode, locale),
                                    modifier = Modifier.weight(1f),
                                )
                                PortfolioMiniMetric(
                                    label = "Return",
                                    value = if (isPrivacy) "Hidden" else "${if (isPortfolioUp) "+" else ""}${String.format(locale, "%.1f", growthPct)}%",
                                    modifier = Modifier.weight(1f),
                                )
                                PortfolioMiniMetric(
                                    label = "Holdings",
                                    value = visibleInvestments.size.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (!portfolioCagr.isNaN()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "CAGR ${app.fynlo.logic.CagrCalculator.format(portfolioCagr)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (portfolioCagr >= 0) Emerald100 else Color(0xFFFFD8D8),
                                    )
                                    if (!portfolioXirr.isNaN()) {
                                        Text(
                                            "XIRR ${app.fynlo.logic.XirrCalculator.format(portfolioXirr)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (portfolioXirr >= 0) Emerald100 else Color(0xFFFFD8D8),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // C14 allocation visual (audit #2): stacked horizontal bar
                // by investment type with a legend below. Each segment's
                // width is proportional to its currentVal share of the
                // portfolio. Hidden when only one holding type exists
                // (the bar would just be a single block — no information).
                if (allocation.size >= 2 && portfolioValue > 0) {
                    item {
                        LedgerPanel(Modifier.padding(bottom = 16.dp)) {
                            LedgerSectionTitle("Allocation", count = "${allocation.size} types")
                            Spacer(Modifier.height(10.dp))
                            // Stacked bar
                            Row(
                                Modifier.fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                allocation.forEachIndexed { i, (_, value) ->
                                    val frac = (value / portfolioValue).toFloat().coerceIn(0f, 1f)
                                    if (frac > 0f) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .weight(frac)
                                                .background(ChartColors[i % ChartColors.size])
                                        )
                                    }
                                }
                            }
                            // Legend
                            Spacer(Modifier.height(8.dp))
                            allocation.forEachIndexed { i, (type, value) ->
                                    val pct = (value / portfolioValue * 100).toInt()
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    Arrangement.SpaceBetween,
                                    Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            Modifier.size(10.dp).clip(CircleShape)
                                                .background(ChartColors[i % ChartColors.size])
                                        )
                                        Text(type,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                    val rowValueText = if (isPrivacy) "Hidden" else "${CurrencyFormatter.detail(value, currencyCode, locale)} - $pct%"
                                    Text(
                                        rowValueText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Holdings list — section header to mark the transition
                // from portfolio-level to per-holding info.
                item {
                    Text("Holdings",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                }

                itemsIndexed(visibleInvestments, key = { _, i -> i.id }) { index, invest ->
                    InvestmentCard(
                        invest   = invest,
                        currencyCode = currencyCode,
                        isPrivacy = isPrivacy,
                        onDelete    = { deletingInvest = invest },
                        onWithdraw  = { withdrawingInvest = invest },
                        onEdit   = { editingInvest = invest },
                        onUpdate = { updatingInvest = invest },
                        onViewHistory = { viewingHistory = invest }
                    )
                    if (index < visibleInvestments.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun PortfolioMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.13f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun InvestmentCard(invest: Investment, currencyCode: String = "INR", isPrivacy: Boolean = false, onDelete: () -> Unit, onEdit: () -> Unit, onUpdate: () -> Unit, onViewHistory: () -> Unit, onWithdraw: () -> Unit = {}) {
    val growth = invest.currentVal - (invest.invested - invest.withdrawn)
    val growthPercent = if (invest.invested > 0) (growth / invest.invested) * 100 else 0.0
    val isProfit = growth >= 0
    val typeAccent = remember(invest.type) { investmentTypeAccent(invest.type) }
    val fundingLabel = remember(invest.sourceType, invest.fundingSource) {
        investmentFundingLabel(invest)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var showReturnDetails by remember { mutableStateOf(false) }
    val cagr = remember(invest) {
        app.fynlo.logic.CagrCalculator.calc(invest.invested, invest.currentVal, invest.date)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(typeAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(23.dp), tint = typeAccent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            invest.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            InvestmentMetaChip(invest.type.ifBlank { "Other" })
                            InvestmentMetaChip("Since ${DateUtils.formatToDisplay(invest.date)}")
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, Modifier.size(34.dp)) {
                        Icon(Icons.Default.MoreVert, "More", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Valuation History") }, onClick = { menuOpen = false; onViewHistory() })
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete", color = SemanticRed) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestmentValueTile(
                    label = "Invested",
                    value = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(invest.invested, currencyCode),
                    modifier = Modifier.weight(1f),
                )
                val gainLossText = if (isPrivacy) "Hidden"
                                   else if (isProfit) "+${CurrencyFormatter.hero(growth, currencyCode)}"
                                   else CurrencyFormatter.negative(growth, currencyCode)
                InvestmentValueTile(
                    label = "Gain / Loss",
                    value = gainLossText,
                    valueColor = if (isProfit) Emerald600 else SemanticRed,
                    modifier = Modifier.weight(1f),
                )
                InvestmentValueTile(
                    label = "Value",
                    value = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(invest.currentVal, currencyCode),
                    valueColor = if (isProfit) Emerald600 else SemanticRed,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isProfit) Emerald500.copy(alpha = 0.12f) else SemanticRed.copy(alpha = 0.12f),
                ) {
                    val growthPctText = if (isPrivacy) "Hidden" else "${if (isProfit) "+" else ""}${String.format(LocalLocale.current.platformLocale, "%.1f", growthPercent)}%"
                    Text(
                        growthPctText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (isProfit) Emerald700 else SemanticRed,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                if (!cagr.isNaN()) {
                    Text(
                        if (isPrivacy) "CAGR hidden" else "CAGR ${app.fynlo.logic.CagrCalculator.format(cagr)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (cagr >= 0) Emerald700 else SemanticRed,
                    )
                }
            }

            if (fundingLabel.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        fundingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            if (invest.withdrawn > 0 || invest.notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (invest.withdrawn > 0) {
                        Text("Withdrawn: ${if (isPrivacy) "Hidden" else CurrencyFormatter.detail(invest.withdrawn, currencyCode)}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (invest.notes.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(invest.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TemplateAction),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Update, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Update", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
                if (invest.currentVal > 0) {
                    OutlinedButton(
                        onClick = onWithdraw,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallMade, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Withdraw", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
    }
}
}

private fun investmentTypeAccent(type: String): Color = when (type.lowercase(Locale.getDefault())) {
    "stocks" -> Emerald600
    "mutual funds" -> SemanticBlue
    "gold" -> SemanticAmber
    "fixed deposit" -> Carbon500
    "business" -> Color(0xFF7C3AED)
    "real estate" -> Color(0xFF0F766E)
    "crypto" -> Color(0xFFF97316)
    else -> Emerald500
}

private fun investmentFundingLabel(invest: Investment): String {
    val source = invest.fundingSource.trim()
    if (source.isBlank()) return ""
    val prefix = when (invest.sourceType) {
        "account" -> "Funded from"
        "existing_debt" -> "Funded by debt"
        "new_loan" -> "Funded by new loan"
        else -> "Funded from"
    }
    return "$prefix $source"
}

@Composable
private fun InvestmentMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun InvestmentValueTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InvestmentExpandableSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
fun EmptyInvestState(onAdd: () -> Unit = {}) {
    app.fynlo.ui.components.EmptyStateIllustration(
        type        = app.fynlo.ui.components.EmptyStateType.INVESTMENTS,
        onAction    = onAdd,
        actionLabel = "Add First Investment"
    )
}

@Composable
fun UpdateInvestmentValueDialog(
    investment: Investment,
    currencySymbol: String = "₹",
    currencyCode: String = "INR",
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var newValue by remember { mutableStateOf(investment.currentVal.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }

    val parsed   = newValue.toDoubleOrNull()
    val growth   = parsed?.let { it - investment.invested }
    val locale   = LocalLocale.current.platformLocale

    // C22 dialog universalization (3.2.54) — migrated to canonical FormDialog.
    app.fynlo.ui.components.FormDialog(
        title = "Log New Valuation",
        onDismiss = onDismiss,
    ) {
        Text(investment.name, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("New market value ($currencySymbol)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = newValue, onValueChange = { newValue = it },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Valuation date")
        Spacer(Modifier.height(6.dp))
        app.fynlo.ui.components.DatePickerField(value = date, onValueChange = { date = it }, label = "")

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Notes (optional)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            placeholder = { Text("Why did the value change?") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        if (growth != null) {
            // C14 #7 (3.2.82) — celebration line: delta vs the PREVIOUS
            // valuation (not vs original invested). When the new value
            // grows vs last-known, surface a green "+₹X since last update"
            // line so the win moment is visible. Negative shows a muted
            // red. Zero hides the line.
            val prevVal = investment.currentVal
            val deltaSinceLast = parsed - prevVal
            if (kotlin.math.abs(deltaSinceLast) > 0.005 && newValue.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val deltaStr = if (deltaSinceLast >= 0)
                    "+${CurrencyFormatter.detail(deltaSinceLast, currencyCode, locale)}"
                else
                    CurrencyFormatter.negative(deltaSinceLast, currencyCode, locale)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (deltaSinceLast >= 0) Emerald500.copy(alpha = 0.12f)
                            else SemanticRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$deltaStr since last update",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (deltaSinceLast >= 0) Emerald500 else SemanticRed,
                            )
                            Text(
                                "Previously ${CurrencyFormatter.detail(prevVal, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val pct = if (investment.invested > 0) (growth / investment.invested) * 100 else 0.0
            val growthStr = if (growth >= 0) "+${CurrencyFormatter.detail(growth, currencyCode, locale)}"
                            else            CurrencyFormatter.negative(growth, currencyCode, locale)
            Text(
                "Total Growth: $growthStr (${String.format(locale, "%.1f", pct)}%)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (growth >= 0) Emerald500 else SemanticRed
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { parsed?.let { onConfirm(it, date, notes) } },
            enabled = parsed != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Log Valuation", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        app.fynlo.ui.components.DisabledButtonHint(
            if (parsed == null) "Enter a valid market value to continue" else null
        )
    }
}

@Composable
fun ValuationHistoryDialog(
    investment: Investment,
    currencyCode: String = "INR",
    valuations: List<app.fynlo.data.model.InvestmentValuation>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("Valuation History") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(investment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                if (valuations.isEmpty()) {
                    // C19 (3.2.43) — was "No records found" plain text; the
                    // audit called out Valuation History as having no
                    // illustration. AlertDialog body is space-constrained,
                    // so use a compact icon + 2-line body rather than the
                    // full shared EmptyState component.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            "No valuation history yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap \"Update Value\" on the investment to start tracking how its value changes over time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // C14 #9 (3.2.82) — line chart above the list. Sorted
                    // chronologically (oldest → newest); polyline through
                    // the value points with min/max labelled. Uses min-max
                    // normalisation against the visible value range so even
                    // small percentage changes are readable. Hidden when
                    // there are fewer than 2 points (a single dot isn't a
                    // chart).
                    val sorted = remember(valuations) { valuations.sortedBy { it.date } }
                    if (sorted.size >= 2) {
                        ValuationHistoryChart(
                            valuations = sorted,
                            currencyCode = currencyCode,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(valuations) { v ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(DateUtils.formatToDisplay(v.date), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    if (v.notes.isNotBlank()) {
                                        Text(v.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(CurrencyFormatter.detail(v.value, currencyCode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * C14 #9 (3.2.82) — line chart for Valuation History.
 *
 * Renders a normalised polyline of valuation values over time using a
 * Compose `Canvas`. Min-max normalisation keeps small percentage changes
 * readable (a 2% gain over the full year would be invisible against a
 * y=0 baseline). Min / current / max value labels overlay the chart;
 * dates on the x-axis are derived from first + last valuations.
 *
 * Same chart shape used by NetWorthHistoryScreen / ProfitLossScreen —
 * the audit asked for "Report archetype within Invest" and this matches
 * the §9.14 line-chart pattern.
 */
@Composable
private fun ValuationHistoryChart(
    valuations: List<app.fynlo.data.model.InvestmentValuation>,
    currencyCode: String,
) {
    val locale = LocalLocale.current.platformLocale
    val values = valuations.map { it.value }
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).coerceAtLeast(1.0)  // avoid div-by-zero on flat history

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
    ) {
        // Top row: min + current label
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                "Min: ${CurrencyFormatter.detail(minV, currencyCode, locale)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Max: ${CurrencyFormatter.detail(maxV, currencyCode, locale)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Emerald500,
            )
        }
        Spacer(Modifier.height(8.dp))

        val lineColor = Emerald500
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
            val n = values.size
            if (n < 2) return@Canvas
            val w = size.width
            val h = size.height
            val padding = 6f
            val plotW = w - padding * 2
            val plotH = h - padding * 2
            // 3-line subtle grid (top / mid / bottom)
            for (i in 0..2) {
                val y = padding + plotH * i / 2f
                drawLine(
                    color = gridColor,
                    start = androidx.compose.ui.geometry.Offset(padding, y),
                    end   = androidx.compose.ui.geometry.Offset(w - padding, y),
                    strokeWidth = 1f,
                )
            }
            // Polyline
            val points = values.mapIndexed { i, v ->
                val x = padding + plotW * (i / (n - 1).toFloat())
                val yNorm = ((v - minV) / range).toFloat()
                val y = padding + plotH * (1f - yNorm)
                androidx.compose.ui.geometry.Offset(x, y)
            }
            for (i in 1 until points.size) {
                drawLine(
                    color = lineColor,
                    start = points[i - 1],
                    end   = points[i],
                    strokeWidth = 4f,
                    cap   = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            // Endpoint dot at the latest value so the "now" position reads at a glance.
            drawCircle(
                color  = lineColor,
                radius = 5f,
                center = points.last(),
            )
        }

        Spacer(Modifier.height(6.dp))
        // x-axis date labels (first + last)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                DateUtils.formatToDisplay(valuations.first().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                DateUtils.formatToDisplay(valuations.last().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
