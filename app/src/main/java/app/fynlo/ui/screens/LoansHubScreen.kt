package app.fynlo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.LedgerMetric
import app.fynlo.ui.theme.LedgerMetricBand
import app.fynlo.ui.theme.SemanticRed
import app.fynlo.ui.theme.PremiumScreenHeader

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
    var tab by remember { mutableIntStateOf(initialTab) }
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
    val activeLentCount = remember(borrowers) {
        borrowers.count { b ->
            b.status !in listOf("Settled", "WrittenOff") && (
                if (b.rate <= 0) b.paid < b.amount
                else b.paidPrincipal < b.amount
            )
        }
    }
    val activeOwedCount = remember(debts) {
        debts.count { it.paid < it.amount }
    }

    // Pick the hero metrics based on which tab is active. financialSummary's
    // totalReceivables already excludes written-off borrowers; debt total is
    // principal-remaining + interest-accrued (full liability).
    val heroAmount = if (tab == 0) summary.totalReceivables
                     else (summary.totalDebtPrincipal + summary.totalDebtInterest)
    val heroCount  = if (tab == 0) activeLentCount else activeOwedCount
    Column(Modifier.fillMaxSize()) {
        PremiumScreenHeader(
            title = "Loans",
            subtitle = if (tab == 0) "Money you've lent out" else "Money you owe"
        )

        // C12 Stage 1 hero block — Total Outstanding for the active tab,
        // hidden when the tab has zero entries (the Lent/Owed segmented +
        // empty-state messaging in the child screen handles that case).
        LedgerMetricBand(
            metrics = listOf(
                LedgerMetric(
                    label = if (tab == 0) "Lent" else "Owed",
                    value = if (isPrivacy) "Hidden" else CurrencyFormatter.detail(heroAmount, currencyCode, locale),
                    valueColor = if (tab == 0) Emerald500 else SemanticRed,
                ),
                LedgerMetric(
                    label = "Active",
                    value = heroCount.toString(),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                ),
                LedgerMetric(
                    label = "Type",
                    value = if (tab == 0) "Lent" else "Owed",
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Emerald500.copy(alpha = 0.14f),
                    activeContentColor = Emerald500
                )
            ) { Text("Lent") }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = SemanticRed.copy(alpha = 0.14f),
                    activeContentColor = SemanticRed
                )
            ) { Text("Owed") }
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
