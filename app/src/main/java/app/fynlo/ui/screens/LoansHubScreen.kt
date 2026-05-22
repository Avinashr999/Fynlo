package app.fynlo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticRed
import app.fynlo.ui.theme.PremiumScreenHeader

/**
 * Combined Loans hub — one tab for "money lent out" (Lending) and "money owed"
 * (Debts), toggled with a segmented control. Embeds the existing screens
 * headerless so each keeps its own list, search and actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansHubScreen(
    viewModel: FinanceViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    initialTab: Int = 0
) {
    var tab by remember { mutableIntStateOf(initialTab) }

    Column(Modifier.fillMaxSize()) {
        PremiumScreenHeader(
            title = "Loans",
            subtitle = if (tab == 0) "Money you've lent out" else "Money you owe"
        )
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Emerald500.copy(alpha = 0.14f),
                    activeContentColor = Emerald500
                )
            ) { Text("Lent") }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
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
                DebtScreen(viewModel = viewModel, showHeader = false)
            }
        }
    }
}
