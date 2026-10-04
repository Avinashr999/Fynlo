package app.fynlo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.fynlo.data.model.Transaction
import app.fynlo.ui.components.EditTransactionDialog
import app.fynlo.ui.theme.FynloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class RepaymentHistoryUiTest {
    @get:Rule val compose = createComposeRule()

    private fun verifyEdit(category: String, type: String) {
        val original = Transaction(id = "synthetic-receipt", date = "2026-02-03", type = type,
            amount = 200.0, category = category, ref = "synthetic-loan", notes = "Original note",
            fromAcct = if (type == "Expense") "Cash" else "",
            toAcct = if (type == "Income") "Cash" else "")
        var saved: Transaction? = null
        compose.setContent { FynloTheme {
            EditTransactionDialog(original, onDismiss = {}, onConfirm = { saved = it })
        } }
        compose.onNodeWithText("Edit payment description").assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("Amount").assertDoesNotExist()
        compose.onNodeWithText("Date").assertDoesNotExist()
        compose.onNodeWithText("Notes").performTextReplacement("Updated reference")
        compose.onNodeWithText("Save changes").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(original.copy(notes = "Updated reference"), saved!!.copy(updatedAt = original.updatedAt))
        }
    }

    @Test fun borrowerReceiptOnlyEditsDescriptionAndNotes() = verifyEdit("Loan Repayment", "Income")
    @Test fun debtReceiptOnlyEditsDescriptionAndNotes() = verifyEdit("Debt Repayment", "Expense")
}
