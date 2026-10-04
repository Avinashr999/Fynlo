package app.fynlo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import app.fynlo.logic.InterestPolicy
import app.fynlo.ui.components.CollectPaymentDialog
import app.fynlo.ui.components.PayDebtDialog
import app.fynlo.ui.theme.FynloTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class PaymentPurposeUiTest {
    @get:Rule val compose = createComposeRule()
    private val start = LocalDate.now().withDayOfMonth(1).minusMonths(1)

    private fun choosePreviousMonthInterest(command: String) {
        compose.onNodeWithText("Amount").performScrollTo().performTextInput("29000")
        compose.onNode(hasText("$command\u20b9", substring = true) and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Payment for").performScrollTo().performClick()
        compose.onNodeWithText("Interest only").performClick()
        compose.onNodeWithText("Interest for").performScrollTo().performClick()
        val month = start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        compose.onNodeWithText("$month interest (settled)").performClick()
        // Editing the amount must not discard the chosen purpose.
        compose.onNodeWithText("Amount").performScrollTo().performTextReplacement("29000")
        compose.onNode(hasText("$command\u20b9", substring = true) and hasClickAction()).performScrollTo().performClick()
    }

    @Test fun borrowerSeptemberInterestKeepsPrincipalUntouched() {
        var saved: Payment? = null
        compose.setContent { FynloTheme {
            CollectPaymentDialog(Borrower(id = "ui-only", name = "Test", amount = 1_400_000.0,
                rate = 24.0, date = start.toString(), intType = "Simple Interest"), accounts = emptyList(),
                onDismiss = {}, onConfirm = { payment, _ -> saved = payment })
        } }
        choosePreviousMonthInterest("Record ")
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(0.0, saved!!.principal, 0.0)
            assertEquals(29_000.0, saved!!.interest, 0.0)
            assertEquals(InterestPolicy.OLD_PERIOD_INTEREST, saved!!.interestAllocationType)
            assertEquals(start.toString(), saved!!.interestPeriodStartDate)
            assertEquals(start.plusMonths(1).minusDays(1).toString(), saved!!.interestPeriodEndDate)
        }
    }

    @Test fun debtPreviousMonthInterestKeepsPrincipalUntouched() {
        var saved: DebtPayment? = null
        compose.setContent { FynloTheme {
            PayDebtDialog(Debt(id = "ui-only", name = "Test", amount = 1_400_000.0,
                rate = 24.0, date = start.toString(), intType = "Simple Interest"), accounts = emptyList(),
                onDismiss = {}, onConfirm = { payment, _ -> saved = payment })
        } }
        choosePreviousMonthInterest("Pay ")
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(0.0, saved!!.principal, 0.0)
            assertEquals(29_000.0, saved!!.interest, 0.0)
            assertEquals(InterestPolicy.OLD_PERIOD_INTEREST, saved!!.interestAllocationType)
        }
    }
}
