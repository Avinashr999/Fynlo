package app.fynlo.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.fynlo.data.SyncStatus
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.ui.components.CollectPaymentDialog
import app.fynlo.ui.components.PayDebtDialog
import app.fynlo.ui.components.SyncStatusBadge
import app.fynlo.ui.theme.FynloTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class LoanStartDayAndCloudUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cloudBadgeTracksGuestAndLinkedAccountWithoutRestart() {
        val linked = mutableStateOf(false)
        compose.setContent {
            FynloTheme { SyncStatusBadge(SyncStatus.Initialising, hasCloudAccount = linked.value) }
        }
        compose.onNodeWithContentDescription("Local only").assertIsDisplayed()
        compose.runOnIdle { linked.value = true }
        compose.onNodeWithContentDescription("Connecting").assertIsDisplayed()
        compose.runOnIdle { linked.value = false }
        compose.onNodeWithContentDescription("Local only").assertIsDisplayed()
    }

    @Test fun sameDayBorrowerPaymentShowsAccruedAndDue493() {
        val loan = Borrower(id = "ui-only", name = "UI Test", amount = 1_000_000.0,
            rate = 18.0, date = LocalDate.now().toString(), intType = "Simple Interest")
        compose.setContent {
            FynloTheme {
                CollectPaymentDialog(loan, accounts = emptyList(), onDismiss = {}, onConfirm = { _, _ -> })
            }
        }
        compose.onNodeWithText("Accrued interest").assertIsDisplayed()
        compose.onNodeWithText("Interest Due").assertIsDisplayed()
        compose.onAllNodesWithText("\u20b9493").assertCountEquals(2)
    }

    @Test fun sameDayDebtPaymentShowsAccruedAndDue493() {
        val debt = Debt(id = "ui-only", name = "UI Test", amount = 1_000_000.0,
            rate = 18.0, date = LocalDate.now().toString(), intType = "Simple Interest")
        compose.setContent {
            FynloTheme {
                PayDebtDialog(debt, accounts = emptyList(), onDismiss = {}, onConfirm = { _, _ -> })
            }
        }
        compose.onNodeWithText("Accrued interest").assertIsDisplayed()
        compose.onNodeWithText("Interest Due").assertIsDisplayed()
        compose.onAllNodesWithText("\u20b9493").assertCountEquals(2)
    }
}
