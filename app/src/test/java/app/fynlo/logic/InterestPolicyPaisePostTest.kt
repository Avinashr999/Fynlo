package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preview ≡ post for lean v1 paise methods (ship gate). */
class InterestPolicyPaisePostTest {

    private fun borrower(
        amount: Double = 10_000.0,
        rate: Double = 12.0,
        intType: String = "Simple Interest",
        paidPrincipal: Double = 0.0,
        paidInterest: Double = 0.0,
        date: String = "2026-01-01",
    ) = Borrower(
        id = "loan-1",
        name = "Test",
        amount = amount,
        rate = rate,
        date = date,
        due = "2026-02-01",
        intType = intType,
        paidPrincipal = paidPrincipal,
        paidInterest = paidInterest,
    )

    @Test
    fun `alignBorrowerPayment matches previewBorrowerPaymentPaise`() {
        val b = borrower()
        val asOf = "2026-02-01"
        val amount = 500.0
        val paymentPaise = InterestEngine.rupeesToPaise(amount)
        val preview = InterestPolicy.previewBorrowerPaymentPaise(b, paymentPaise, asOf)
        val posted = InterestPolicy.alignBorrowerPaymentToPaisePreview(
            b,
            Payment(
                id = "p1",
                loanId = b.id,
                name = b.name,
                date = asOf,
                type = "Both",
                amount = amount,
                principal = 999.0, // garbage — must be overwritten
                interest = 999.0,
            ),
            asOf = asOf,
        )
        assertEquals(InterestEngine.paiseToRupees(preview.towardInterest), posted.interest, 0.0)
        assertEquals(InterestEngine.paiseToRupees(preview.towardPrincipal), posted.principal, 0.0)
        assertEquals(0.0, InterestPolicy.khathaPenaltyRupees(posted.amount, posted.principal, posted.interest), 0.0)
    }

    @Test
    fun `fixture D overpay posts penalty as amount minus principal minus interest`() {
        // After a prior ₹500 payment on simple (fixture B state): principal remaining 9601.91
        val b = borrower(paidPrincipal = 398.09, paidInterest = 101.91)
        val asOf = "2026-02-01"
        val amount = 10_000.0
        val preview = InterestPolicy.previewBorrowerPaymentPaise(
            b, InterestEngine.rupeesToPaise(amount), asOf
        )
        assertTrue(preview.penaltyPaise > 0L)
        val posted = InterestPolicy.alignBorrowerPaymentToPaisePreview(
            b,
            Payment(
                id = "p2",
                loanId = b.id,
                name = b.name,
                date = asOf,
                type = "Both",
                amount = amount,
            ),
            asOf = asOf,
        )
        // v3.3.0: penalty is a stored column; amount − principal − interest = penalty + rounding.
        assertEquals(preview.penaltyPaise, posted.penaltyPaise)
        assertEquals(preview.roundingPaise, posted.roundingPaise)
        assertEquals(
            InterestEngine.rupeesToPaise(posted.amount),
            InterestEngine.rupeesToPaise(posted.principal) + InterestEngine.rupeesToPaise(posted.interest) +
                posted.penaltyPaise + posted.roundingPaise,
        )
        assertTrue(posted.notes.contains("Penalty on this account"))
    }

    @Test
    fun `reducing align uses prior payment date not original principal life`() {
        val b = borrower(intType = "Reducing Balance")
        val prior = listOf(
            Payment(
                id = "prior-1",
                loanId = b.id,
                name = b.name,
                date = "2026-01-15",
                type = "Both",
                amount = 500.0,
                principal = 453.98,
                interest = 46.02,
            ),
        )
        val asOf = "2026-02-01"
        val amount = 200.0
        val preview = InterestPolicy.previewBorrowerPaymentPaise(
            b, InterestEngine.rupeesToPaise(amount), asOf, prior,
        )
        // Personal-ledger inclusive dates: Jan 1..15 and Jan 16..Feb 1 are both counted.
        assertEquals(5_337L, preview.towardInterest)
        assertEquals(14_663L, preview.towardPrincipal)
        assertEquals(0L, preview.penaltyPaise)

        val posted = InterestPolicy.alignBorrowerPaymentToPaisePreview(
            b,
            Payment(
                id = "p-new",
                loanId = b.id,
                name = b.name,
                date = asOf,
                type = "Both",
                amount = amount,
            ),
            priorPayments = prior,
            asOf = asOf,
        )
        assertEquals(InterestEngine.paiseToRupees(preview.towardInterest), posted.interest, 0.0)
        assertEquals(InterestEngine.paiseToRupees(preview.towardPrincipal), posted.principal, 0.0)

        val debt = Debt(
            id = "debt-1",
            name = "L",
            amount = 10_000.0,
            rate = 12.0,
            date = "2026-01-01",
            intType = "Reducing Balance",
        )
        val debtPrior = listOf(
            DebtPayment(
                id = "dprior",
                debtId = debt.id,
                name = debt.name,
                date = "2026-01-15",
                type = "Both",
                amount = 500.0,
            ),
        )
        val debtPreview = InterestPolicy.previewDebtPaymentPaise(
            debt, InterestEngine.rupeesToPaise(amount), asOf, debtPrior,
        )
        assertEquals(preview.towardInterest, debtPreview.towardInterest)
        assertEquals(preview.towardPrincipal, debtPreview.towardPrincipal)
    }

}
