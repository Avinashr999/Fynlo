package app.fynlo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.DebtPayment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.DebtPayoffPlanner
import app.fynlo.logic.InterestEngine
import app.fynlo.logic.displayFromAcct
import app.fynlo.logic.displayToAcct
import app.fynlo.ui.components.AddDebtDialog
import app.fynlo.ui.components.PayDebtDialog
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.LedgerDetailTopBar
import app.fynlo.ui.theme.SemanticAmber
import app.fynlo.ui.theme.SemanticRed
import java.util.Locale

/**
 * Owed-side counterpart to [CustomerDetailScreen]. Same visual structure
 * (hero outstanding → action button → payment history → notes) so the
 * Lent and Owed detail surfaces feel like one design (UX_AUDIT §C12 fix #5).
 *
 * Hosts all per-debt actions per audit #6/#7: Pay (primary button), Edit
 * + Delete (TopBar). The card in [DebtScreen] is now action-free — every
 * action lives here as a proper labelled button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    debtId: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val haptic        = LocalHapticFeedback.current
    val debts         by viewModel.debts.collectAsState()
    val allPayments   by viewModel.debtPayments.collectAsState()
    val accounts      by viewModel.accounts.collectAsState()
    val transactions  by viewModel.transactions.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode  = currentProject?.currency ?: "INR"
    val locale        = LocalLocale.current.platformLocale

    val debt = debts.find { it.id == debtId }
    if (debt == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Debt not found")
        }
        return
    }

    val debtPayments = allPayments
        .filter { it.debtId == debtId }
        .sortedByDescending { it.date }

    val interest = InterestEngine.calcIntAccrued(
        debt.amount, debt.rate, debt.date, debt.intType, debt.due, debt.paid
    )
    val totalOutstanding = InterestEngine.calcOutstanding(debt.amount, interest, debt.paid)
    val accountIdToName = remember(accounts) { accounts.associate { it.id to it.name } }
    val receivedTxn = remember(transactions, debt.id) {
        transactions
            .filter { it.ref == debt.id && it.category.equals("Debt Received", ignoreCase = true) }
            .maxByOrNull { it.createdAt.takeIf { created -> created > 0L } ?: it.updatedAt }
    }
    val receivedInto = remember(receivedTxn, accountIdToName) {
        receivedTxn?.displayToAcct(accountIdToName).orEmpty()
    }

    var showEditDialog    by remember { mutableStateOf(false) }
    var showPayDialog     by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEffectiveRate by remember { mutableStateOf(false) }
    var showPaymentHistory by remember { mutableStateOf(false) }
    var deleteInProgress  by remember(debt.id) { mutableStateOf(false) }

    if (showEditDialog) {
        AddDebtDialog(
            viewModel    = viewModel,
            onDismiss    = { showEditDialog = false },
            onConfirm    = { updated, _ -> viewModel.updateDebt(updated); showEditDialog = false },
            initialDebt  = debt
        )
    }
    if (showPayDialog) {
        PayDebtDialog(
            debt      = debt,
            accounts  = accounts,
            onDismiss = { showPayDialog = false },
            onConfirm = { payment, source ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.payDebt(payment, source)
                showPayDialog = false
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete debt?") },
            text  = { Text("This will permanently delete \"${debt.name}\" and reverse the linked account entries. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!deleteInProgress) {
                            deleteInProgress = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteDebt(debt)
                            showDeleteConfirm = false
                            onNavigateBack()
                        }
                    },
                    enabled = !deleteInProgress,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            LedgerDetailTopBar(
                title = debt.name,
                subtitle = "Debt statement",
                onNavigateBack = onNavigateBack,
            ) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        "Current Outstanding",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        CurrencyFormatter.hero(totalOutstanding, currencyCode, locale),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalOutstanding > 0) MaterialTheme.colorScheme.error
                                    else Emerald500
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("Principal", CurrencyFormatter.detail(debt.amount, currencyCode, locale))
                        DetailItem("Interest",  CurrencyFormatter.detail(interest, currencyCode, locale))
                        DetailItem("Paid",      CurrencyFormatter.detail(debt.paid, currencyCode, locale))
                    }
                    if (receivedInto.isNotBlank() || debt.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(Emerald500.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Emerald500,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    if (receivedInto.isNotBlank()) {
                                        Text(
                                            "Received into $receivedInto",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (debt.notes.isNotBlank()) {
                                        Text(
                                            "Purpose: ${debt.notes}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (debt.rate > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Rate: ${debt.rate}% • ${app.fynlo.logic.InterestEngine.label(debt.intType)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (debt.due.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Due ${DateUtils.formatToDisplay(debt.due)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (totalOutstanding > 0) {
                item {
                    Button(
                        onClick = { showPayDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SemanticRed)
                    ) {
                        Icon(Icons.Default.Payment, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Make Payment")
                    }
                }

                // C22 (3.2.64) — extra-payment what-if scenario.
                item {
                    WhatIfSection(
                        debt = debt,
                        outstandingBalance = totalOutstanding,
                        currencyCode = currencyCode,
                        locale = locale,
                    )
                }
            }

            item {
                EmiPlanningSection(
                    debt = debt,
                    currencyCode = currencyCode,
                    locale = locale
                )
            }

            // 3.2.83 — XIRR (effective borrowing rate) on this debt.
            // Cashflows from borrower's perspective (mirror of Lending):
            //   - principal received on `debtDate` as a POSITIVE inflow
            //   - each DebtPayment made as a NEGATIVE outflow on its date
            //   - imputed current `totalOutstanding` at today as a
            //     NEGATIVE "what I still owe" cashflow, so XIRR stays
            //     meaningful for ongoing debts. Same mark-to-today logic
            //     as the lending XIRR.
            item {
                val xirr = remember(debtPayments, debt, totalOutstanding) {
                    val flows = mutableListOf<app.fynlo.logic.XirrCalculator.Cashflow>()
                    flows += app.fynlo.logic.XirrCalculator.Cashflow(
                        amount = debt.amount,
                        date   = debt.date,
                    )
                    debtPayments.forEach { p ->
                        flows += app.fynlo.logic.XirrCalculator.Cashflow(-p.amount, p.date)
                    }
                    if (totalOutstanding > 0.01) {
                        flows += app.fynlo.logic.XirrCalculator.Cashflow(
                            amount = -totalOutstanding,
                            date   = java.time.LocalDate.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        )
                    }
                    app.fynlo.logic.XirrCalculator.calc(flows)
                }
                val xirrColor = if (!xirr.isNaN() && xirr <= 0) Emerald500 else SemanticRed
                DebtExpandableSection(
                    title = "Effective rate",
                    subtitle = if (xirr.isNaN()) "Available after enough dated cashflows" else "Annualised borrowing cost (XIRR)",
                    expanded = showEffectiveRate,
                    onToggle = { showEffectiveRate = !showEffectiveRate },
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = xirrColor.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "XIRR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                app.fynlo.logic.XirrCalculator.format(xirr),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = xirrColor,
                            )
                        }
                    }
                }
            }

            item {
                DebtExpandableSection(
                    title = "Payment History",
                    subtitle = if (debtPayments.isEmpty()) "No payments made yet"
                               else app.fynlo.logic.pluralize(debtPayments.size, "payment"),
                    expanded = showPaymentHistory,
                    onToggle = { showPaymentHistory = !showPaymentHistory },
                ) {
                    if (debtPayments.isEmpty()) {
                        Text(
                            "No payments made yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            debtPayments.forEach { payment ->
                                val sourceTxn = transactions.firstOrNull {
                                    it.ref == debt.id &&
                                        it.category.equals("Debt Repayment", ignoreCase = true) &&
                                        it.date == payment.date &&
                                        kotlin.math.abs(it.amount - payment.amount) < 0.01
                                }
                                DebtPaymentItem(
                                    payment = payment,
                                    currencyCode = currencyCode,
                                    locale = locale,
                                    paidFrom = sourceTxn?.displayFromAcct(accountIdToName).orEmpty(),
                                )
                            }
                        }
                    }
                }
            }

            if (debt.notes.isNotBlank()) {
                item {
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(debt.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtExpandableSection(
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
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
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun DebtPaymentItem(
    payment: DebtPayment,
    currencyCode: String,
    locale: Locale,
    paidFrom: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SemanticRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CreditCard,
                contentDescription = null,
                tint = SemanticRed,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                payment.type.ifBlank { "Payment" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                DateUtils.formatToDisplay(payment.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (paidFrom.isNotBlank()) {
                Text(
                    "Paid from $paidFrom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (payment.notes.isNotBlank()) {
                Text(
                    payment.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "-${CurrencyFormatter.detail(payment.amount, currencyCode, locale)}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = SemanticRed
            )
        )
    }
}

/**
 * C22 (3.2.64) — extra-payment what-if. Reuses [DebtPayoffPlanner] with a
 * single-debt list to compare two scenarios:
 *   - **Baseline** — what happens at the user's current monthly pace
 *     (derived from `paid / monthsElapsed`, floored to a sensible
 *     minimum so a brand-new debt with no payment history still produces
 *     a number).
 *   - **Scenario** — baseline + a user-set extra-payment amount.
 *
 * Surfaces months saved + interest saved + new payoff month. Toggleable
 * (default OFF) so the detail screen stays focused on view + pay; users
 * who want to plan opt in.
 */
@Composable
private fun WhatIfSection(
    debt: app.fynlo.data.model.Debt,
    outstandingBalance: Double,
    currencyCode: String,
    locale: Locale,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("What if I pay extra?",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("See months saved + interest saved at a higher monthly pace.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 3.2.63 lesson — explicit unchecked colors so the Switch
                // is visible on this tinted background in light mode too.
                Switch(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = Color.White,
                        checkedTrackColor    = Emerald500,
                        uncheckedThumbColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor  = MaterialTheme.colorScheme.surface,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                // Baseline monthly pace — start from what the user is
                // actually doing. If no payments yet, fall back to a
                // sensible "minimum that covers interest + chips away".
                val baseline = remember(debt) {
                    val today = java.time.LocalDate.now()
                    val loanDate = runCatching {
                        java.time.LocalDate.parse(debt.date,
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    }.getOrDefault(today)
                    val months = (java.time.temporal.ChronoUnit.DAYS.between(loanDate, today) / 30.0)
                        .coerceAtLeast(1.0)
                    val actual = debt.paid / months
                    // Fallback when no payment history: monthly interest +
                    // 2% of outstanding principal. Always positive so the
                    // planner doesn't trip its infeasibility guard on a
                    // fresh debt.
                    val fallback = outstandingBalance * (debt.rate / 12.0 / 100.0) +
                                   outstandingBalance * 0.02
                    maxOf(actual, fallback).coerceAtLeast(100.0)
                }

                var extraText by remember(debt.id) {
                    // Default extra = 25% of baseline so the first render
                    // shows a meaningful diff, not a zero-extra no-op.
                    mutableStateOf((baseline * 0.25).toLong().toString())
                }
                val extra = extraText.toDoubleOrNull() ?: 0.0

                val baselineInput = listOf(DebtPayoffPlanner.DebtInput(
                    id = debt.id, name = debt.name,
                    outstandingBalance = outstandingBalance,
                    annualRatePct = debt.rate,
                ))
                val basePlan     = remember(baselineInput, baseline) {
                    DebtPayoffPlanner.plan(baselineInput, baseline, DebtPayoffPlanner.Strategy.AVALANCHE)
                }
                val scenarioPlan = remember(baselineInput, baseline, extra) {
                    DebtPayoffPlanner.plan(baselineInput, baseline + extra, DebtPayoffPlanner.Strategy.AVALANCHE)
                }

                // Hero of baseline.
                Text(
                    "At current pace (${CurrencyFormatter.detail(baseline, currencyCode, locale)}/mo): " +
                    if (basePlan.feasible) "paid off in ${basePlan.totalMonths} mo, " +
                        "${CurrencyFormatter.detail(basePlan.totalInterestPaid, currencyCode, locale)} interest"
                    else "interest is outpacing payments — never paid off",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = extraText,
                    onValueChange = { extraText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Extra payment / month (${app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(10.dp))

                if (!scenarioPlan.feasible) {
                    Text("Even with the extra, your payments don't cover interest. Try a larger amount.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticRed)
                } else {
                    val newMonths = scenarioPlan.totalMonths
                    val newInterest = scenarioPlan.totalInterestPaid
                    val monthsSaved   = (basePlan.totalMonths - newMonths).coerceAtLeast(0)
                    val interestSaved = (basePlan.totalInterestPaid - newInterest).coerceAtLeast(0.0)
                    val payoffDate = remember(newMonths) {
                        java.time.LocalDate.now().plusMonths(newMonths.toLong())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", locale))
                    }

                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        WhatIfStat(
                            label = "Paid off in",
                            value = "$newMonths mo",
                            sub   = "(by $payoffDate)",
                            color = Emerald500,
                            modifier = Modifier.weight(1f),
                        )
                        WhatIfStat(
                            label = "Months saved",
                            value = "$monthsSaved",
                            sub   = if (monthsSaved > 0) "sooner" else "no change",
                            color = if (monthsSaved > 0) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        WhatIfStat(
                            label = "Interest saved",
                            value = CurrencyFormatter.detail(interestSaved, currencyCode, locale),
                            sub   = if (interestSaved > 1.0) "vs baseline" else "no change",
                            color = if (interestSaved > 1.0) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (!basePlan.feasible && scenarioPlan.feasible) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The extra pushes you over the line — without it, " +
                            "interest grows faster than payments and the debt never clears.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticAmber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmiPlanningSection(
    debt: app.fynlo.data.model.Debt,
    currencyCode: String,
    locale: Locale,
) {
    var expanded by remember { mutableStateOf(false) }
    val isReducingBalance = debt.intType.contains("Reducing", ignoreCase = true)
    val canCalculate = debt.amount > 0.0 && debt.tenure > 0 && isReducingBalance
    val emiResult = remember(debt, canCalculate) {
        if (canCalculate) {
            runCatching {
                app.fynlo.logic.EmiPrepaymentSimulator.baseline(debt.amount, debt.rate, debt.tenure)
            }.getOrNull()
        } else {
            null
        }
    }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EMI & Amortization",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (canCalculate) "Original loan schedule and monthly EMI"
                        else "Add tenure and Reducing Balance type to calculate EMI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                val result = emiResult
                if (result == null) {
                    val reason = when {
                        debt.tenure <= 0 -> "Edit this debt and enter Tenure (mo)."
                        !isReducingBalance -> "Edit this debt and set Interest Type to Reducing Balance."
                        else -> "Enter a valid amount, rate, and tenure."
                    }
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val (emi, totalInterest) = result
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        WhatIfStat(
                            label = "Monthly EMI",
                            value = CurrencyFormatter.detail(emi, currencyCode, locale),
                            sub   = "Reducing Bal.",
                            color = Emerald500,
                            modifier = Modifier.weight(1f),
                        )
                        WhatIfStat(
                            label = "Total Interest",
                            value = CurrencyFormatter.detail(totalInterest, currencyCode, locale),
                            sub   = "over ${debt.tenure} mo",
                            color = SemanticAmber,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This is the baseline schedule for a ₹${String.format(locale, "%,.0f", debt.amount)} loan at ${debt.rate}% for ${debt.tenure} months.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatIfStat(label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(12.dp), color = color.copy(alpha = 0.1f)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
