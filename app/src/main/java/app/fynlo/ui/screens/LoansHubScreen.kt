package app.fynlo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.components.AddDebtDialog
import app.fynlo.ui.components.AddLendingDialog
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticRed
import app.fynlo.ui.theme.PremiumScreenHeader
import app.fynlo.ui.theme.TemplatePill
import app.fynlo.ui.theme.Emerald100
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Combined Loans hub — one tab for "money lent out" (Lending) and "money owed"
 * (Debts), toggled with a segmented control. Embeds the existing screens
 * headerless so each keeps its own list, search and actions.
 *
 * C12 Stage 1 (3.2.25) — added the Home-archetype hero per audit §C12 fix
 * #1 + #2: "Total Outstanding ₹X · Across Y loans/debts" sitting above
 * the Lent/Owed segmented row. The number is computed from
 * `financialSummary` (already-precomputed `totalReceivables` for Lent;
 * `totalDebtPrincipal + totalDebtInterest` for Owed) so no extra work on
 * this screen. The active count is the audit's "across Y" pluralisation
 * — derived from the same isActive predicates LendingScreen / DebtScreen
 * apply to their lists. Colour semantic: Lent = Emerald (asset),
 * Owed = SemanticRed (liability).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansHubScreen(
    viewModel: FinanceViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToDebtDetail: (String) -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    // 3.2.63 — was missing, so the "Payoff plan" tile inside the embedded
    // DebtScreen (Owed tab) silently fell back to the no-op default and
    // ignored taps. Surfaces here, plumbed at the call site in Navigation.kt.
    onNavigateToPayoffPlan: () -> Unit = {},
    initialTab: Int = 0
) {
    val haptic = LocalHapticFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    val summary by viewModel.financialSummary.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
    val borrowers by viewModel.borrowers.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale

    // Active-borrower count mirrors `LendingScreen.isActive`: not settled,
    // not written off, still has principal outstanding from guarded payment rows.
    val paymentsByLoan = remember(payments) { payments.groupBy { it.loanId } }
    val activeBorrowers = remember(borrowers, paymentsByLoan) {
        borrowers.filter { b ->
            b.status !in listOf("Settled", "WrittenOff") &&
                app.fynlo.logic.InterestPolicy.borrowerPrincipalOutstanding(b, paymentsByLoan[b.id].orEmpty()) > 0.01
        }
    }
    val activeLentCount = activeBorrowers.size
    val debtPayments by viewModel.debtPayments.collectAsState()
    val paymentsByDebt = remember(debtPayments) { debtPayments.groupBy { it.debtId } }
    val activeOwedCount = remember(debts, paymentsByDebt) {
        debts.count { app.fynlo.logic.InterestPolicy.debtPrincipalOutstanding(it, paymentsByDebt[it.id].orEmpty()) > 0.01 }
    }
    val borrowerPrincipal = remember(activeBorrowers, paymentsByLoan) {
        activeBorrowers.sumOf { b ->
            app.fynlo.logic.InterestPolicy.borrowerPrincipalOutstanding(b, paymentsByLoan[b.id].orEmpty())
        }
    }
    val borrowerInterest = remember(activeBorrowers, paymentsByLoan) {
        activeBorrowers.sumOf { b ->
            if (b.rate <= 0) 0.0 else app.fynlo.logic.InterestPolicy.borrowerSnapshot(b, paymentsByLoan[b.id].orEmpty()).interestDue
        }
    }
    val owedPrincipal = summary.totalDebtPrincipal
    val owedInterest = summary.totalDebtInterest

    val principalAmount = if (tab == 0) borrowerPrincipal else owedPrincipal
    val interestAmount = if (tab == 0) borrowerInterest else owedInterest
    val heroCount  = if (tab == 0) activeLentCount else activeOwedCount

    if (showAddLoanDialog) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { showAddLoanDialog = false },
            onConfirm = { borrower, source ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addBorrowerWithSource(borrower, source)
                viewModel.showFeedback("Loan added")
                showAddLoanDialog = false
            },
            initialBorrower = null,
        )
    }

    if (showAddDebtDialog) {
        AddDebtDialog(
            viewModel = viewModel,
            onDismiss = { showAddDebtDialog = false },
            onConfirm = { debt, destination ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addDebtWithDestination(debt, destination)
                viewModel.showFeedback("Debt added")
                showAddDebtDialog = false
            },
            initialDebt = null,
        )
    }

    val hubTopContent: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            app.fynlo.ui.components.LoanFilterTabs(
                options = listOf("Lent", "Owed"), selectedIndex = tab, onSelected = { tab = it },
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { if (tab == 0) showAddLoanDialog = true else showAddDebtDialog = true },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Icon(Icons.Default.Add, if (tab == 0) "Add Loan" else "Add Debt") }
        }
        LoansReadableSummary(
            countLabel = if (tab == 0) "borrowers" else "debts",
            count = heroCount,
            principalLabel = "Principal remaining",
            principalValue = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(principalAmount, currencyCode, locale),
            principalColor = MaterialTheme.colorScheme.onSurface,
            interestLabel = if (tab == 0) "Interest due" else "Interest payable",
            interestValue = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(interestAmount, currencyCode, locale),
            interestColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                LendingScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToCalendar = onNavigateToCalendar,
                    showHeader = false,
                    topContent = hubTopContent,
                )
            } else {
                DebtScreen(
                    viewModel = viewModel,
                    onNavigateToDetail     = onNavigateToDebtDetail,
                    onNavigateToPayoffPlan = onNavigateToPayoffPlan,
                    showHeader             = false,
                    topContent             = hubTopContent,
                )
            }
        }
    }
}

@Composable
private fun LoansReadableSummary(
    countLabel: String,
    count: Int,
    principalLabel: String,
    principalValue: String,
    principalColor: Color,
    interestLabel: String,
    interestValue: String,
    interestColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (countLabel == "borrowers") "Money to collect" else "Money to repay",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Transparent,
                ) {
                    Text(
                        "$count ${if (count == 1) countLabel.removeSuffix("s") else countLabel}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoanMoneyStat(
                    label = principalLabel,
                    value = principalValue,
                    color = principalColor,
                    modifier = Modifier.weight(1f),
                )
                LoanMoneyStat(
                    label = interestLabel,
                    value = interestValue,
                    color = interestColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LoanMoneyStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}
