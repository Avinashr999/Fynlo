package app.fynlo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddDebtDialog
import app.fynlo.ui.components.AddLendingDialog
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.LedgerMetric
import app.fynlo.ui.theme.LedgerMetricBand
import app.fynlo.ui.theme.SemanticRed
import app.fynlo.ui.theme.PremiumScreenHeader
import app.fynlo.ui.theme.TemplatePill
import app.fynlo.ui.theme.TemplateScreenPadding

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
    var tab by remember { mutableIntStateOf(initialTab) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    val summary by viewModel.financialSummary.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
    val borrowers by viewModel.borrowers.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale

    // Active-borrower count — mirrors `LendingScreen.isActive`: not settled,
    // not written off, still has outstanding balance (hand loans use `paid`,
    // interest loans use `paidPrincipal`).
    val activeBorrowers = remember(borrowers) {
        borrowers.filter { b ->
            b.status !in listOf("Settled", "WrittenOff") && (
                if (b.rate <= 0) b.paid < b.amount
                else b.paidPrincipal < b.amount
            )
        }
    }
    val activeLentCount = activeBorrowers.size
    val activeOwedCount = remember(debts) {
        debts.count { it.paid < it.amount }
    }
    val borrowerPrincipal = remember(activeBorrowers) {
        activeBorrowers.sumOf { b ->
            if (b.rate <= 0) (b.amount - b.paid).coerceAtLeast(0.0)
            else (b.amount - b.paidPrincipal).coerceAtLeast(0.0)
        }
    }
    val borrowerInterest = remember(activeBorrowers) {
        activeBorrowers.sumOf { b ->
            if (b.rate <= 0) 0.0
            else {
                val accrued = if (b.status == "Defaulted" && b.frozenInterest > 0.0) {
                    b.frozenInterest
                } else {
                    InterestEngine.calcIntAccrued(
                        amount = b.amount,
                        rate = b.rate,
                        loanDate = b.date,
                        intType = b.intType,
                        dueDate = b.due,
                        totalPaid = b.paidPrincipal,
                    )
                }
                (accrued - b.paidInterest - b.interestWaived).coerceAtLeast(0.0)
            }
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

    Column(Modifier.fillMaxSize()) {
        PremiumScreenHeader(
            title = "Loans",
            subtitle = if (tab == 0) "Money you've lent out" else "Money you owe",
            action = {
                FilledIconButton(
                    onClick = {
                        if (tab == 0) showAddLoanDialog = true else showAddDebtDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (tab == 0) Emerald500 else SemanticRed,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (tab == 0) "Add Loan" else "Add Debt",
                    )
                }
            },
        )

        // C12 Stage 1 hero block — Total Outstanding for the active tab,
        // hidden when the tab has zero entries (the Lent/Owed segmented +
        // empty-state messaging in the child screen handles that case).
        LedgerMetricBand(
            metrics = listOf(
                LedgerMetric(
                    label = if (tab == 0) "Total Borrowers" else "Total Debtors",
                    value = heroCount.toString(),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                ),
                LedgerMetric(
                    label = "Principal",
                    value = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(principalAmount, currencyCode, locale),
                    valueColor = if (tab == 0) Emerald500 else SemanticRed,
                ),
                LedgerMetric(
                    label = "Interest",
                    value = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(interestAmount, currencyCode, locale),
                    valueColor = if (interestAmount > 0.0) SemanticRed else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ),
            modifier = Modifier.padding(horizontal = TemplateScreenPadding, vertical = 8.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = TemplateScreenPadding, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            TemplatePill(
                text = "Lent",
                selected = tab == 0,
                onClick = { tab = 0 },
                modifier = Modifier.weight(1f),
            )
            TemplatePill(
                text = "Owed",
                selected = tab == 1,
                onClick = { tab = 1 },
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                LendingScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToCalendar = onNavigateToCalendar,
                    showHeader = false
                )
            } else {
                DebtScreen(
                    viewModel = viewModel,
                    onNavigateToDetail     = onNavigateToDebtDetail,
                    onNavigateToPayoffPlan = onNavigateToPayoffPlan,
                    showHeader             = false,
                )
            }
        }
    }
}
