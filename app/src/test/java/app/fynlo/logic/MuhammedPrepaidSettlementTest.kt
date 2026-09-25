package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.3.1 — unused prepaid interest reduces what is owed (Full Settlement, payoff
 * check, Interest Only suggestion, shared balance). Real case: Muhammed.
 *
 * ₹14,00,000 (140,000,000 paise) @ 24% Simple (2400 bps), start 2026-09-01,
 * 2026-09-03 ₹30,378 'Interest Only', rows before the start are ignored, as of
 * 2026-09-25. Day denominator = 10,000 × 365 = 3,650,000.
 *
 *  1 Sep → 3 Sep (2 days): 140,000,000 × 2400 × 2 = 672,000,000,000
 *      / 3,650,000 = 184,109 paise, remainder 2,150,000
 *  3 Sep payment 3,037,800: 184,109 settles accrued interest,
 *      prepaid = 3,037,800 − 184,109 = 2,853,691
 *  3 Sep → 25 Sep (22 days): 140,000,000 × 2400 × 22 + 2,150,000
 *      = 7,392,002,150,000 / 3,650,000 = 2,025,206 paise
 *  interest accrued 1 → 25 Sep = 184,109 + 2,025,206 = 2,209,315 (₹22,093.15)
 *  prepaid left = 2,853,691 − 2,025,206 = 828,485 (₹8,284.85); interest due 0
 *  principal = 140,000,000 (never reduced by the Interest Only row)
 *  settlement total = 140,000,000 + 0 − 828,485 = 139,171,515 (₹13,91,715.15)
 *  Full Settlement (nearest ₹, half-up) = 139,171,500 (₹13,91,715)
 */
class MuhammedPrepaidSettlementTest {

    private val asOf = "2026-09-25"
    private val muhammed = Borrower(
        id = "M", name = "Muhammed", amount = 1_400_000.0, rate = 24.0,
        date = "2026-09-01", intType = "Simple Interest",
        // Stale aggregates (old whole-amount bug) must not matter for the replay.
        paid = 35_378.0, paidPrincipal = 35_378.0,
    )
    private val rows = listOf(
        // Pre-start rows: ignored by the balance and left untouched by re-split.
        Payment(id = "pre1", loanId = "M", name = "Muhammed", date = "2026-08-20", type = "Both", amount = 5_000.0, principal = 5_000.0, createdAt = 1),
        Payment(id = "pre2", loanId = "M", name = "Muhammed", date = "2026-08-31", type = "Interest Only", amount = 1_000.0, createdAt = 2),
        Payment(id = "io", loanId = "M", name = "Muhammed", date = "2026-09-03", type = "Interest Only", amount = 30_378.0, createdAt = 3),
    )

    @Test
    fun `muhammed principal interest prepaid and settlement as of 25 Sep 2026`() {
        val bal = InterestPolicy.paiseBalancesForBorrower(muhammed, asOf, rows)
        assertEquals(140_000_000L, bal.outstandingPrincipal)
        assertEquals(0L, bal.interestDue)
        assertEquals(828_485L, bal.prepaidInterest)
        assertEquals(139_171_515L, bal.outstanding)

        // Interest accrued 1 → 25 Sep with no payments = 2,209,315 paise.
        val noPay = InterestPolicy.paiseBalancesForBorrower(muhammed.copy(paid = 0.0, paidPrincipal = 0.0), asOf, emptyList())
        assertEquals(2_209_315L, noPay.interestDue)
        assertEquals(2_209_315L, 3_037_800L - 828_485L)

        val q = InterestPolicy.settlementQuoteForBorrower(muhammed, asOf, rows)
        assertEquals(139_171_515L, q.totalDuePaise)
        assertEquals(139_171_500L, q.fullSettlementPaise)
        assertEquals(1_391_715L, q.totalDueRupees)
        assertEquals(828_485L, q.prepaidInterestPaise)
        assertEquals(0L, q.interestOnlyPaise) // nothing to suggest for Interest Only

        val shared = InterestPolicy.borrowerBalance(muhammed, rows, asOf)
        assertEquals(1_400_000.0, shared.principal, 0.0)
        assertEquals(0.0, shared.interestDue, 0.0)
        assertEquals(828_485L, shared.prepaidInterestPaise)
        assertEquals(0.0, shared.netInterestDue, 0.0)
        assertEquals(1_391_715.15, shared.outstanding, 1e-9)

        // Debt twin gives the same numbers.
        val d = Debt(id = "M", name = "Muhammed", amount = 1_400_000.0, rate = 24.0, date = "2026-09-01", intType = "Simple Interest")
        val dRows = rows.map { DebtPayment(id = it.id, debtId = "M", name = it.name, date = it.date, type = it.type, amount = it.amount, principal = it.principal, createdAt = it.createdAt) }
        assertEquals(bal, InterestPolicy.paiseBalancesForDebt(d, asOf, dRows))
        assertEquals(shared, InterestPolicy.debtBalance(d, dRows, asOf))
        assertEquals(1_391_715.15, DebtLiabilityCalculator.outstanding(d, dRows, asOf).total, 1e-9)
    }

    @Test
    fun `settle and penalty rule runs on the net total`() {
        fun pay(rupees: Double) = InterestPolicy.previewBorrowerPaymentPaise(
            muhammed, InterestEngine.rupeesToPaise(rupees), asOf, rows,
        )
        // Full Settlement ₹13,91,715 closes; prepaid covers 828,485 of principal.
        val full = pay(1_391_715.0)
        assertTrue(full.closesLoan)
        assertEquals(0L, full.towardInterest)
        assertEquals(139_171_515L, full.towardPrincipal)
        assertEquals(0L, full.penaltyPaise)
        assertEquals(-15L, full.roundingPaise)
        assertEquals(139_171_500L, full.towardInterest + full.towardPrincipal + full.penaltyPaise + full.roundingPaise)
        // ₹1 over the rounded total → ₹1 penalty.
        val over = pay(1_391_716.0)
        assertTrue(over.closesLoan)
        assertEquals(100L, over.penaltyPaise)
        assertEquals(-15L, over.roundingPaise)
        // ₹1 short of the rounded total is more than ₹1 below exact → partial, prepaid kept.
        val short = pay(1_391_714.0)
        assertFalse(short.closesLoan)
        assertEquals(139_171_400L, short.towardPrincipal)

        // Posting the Full Settlement row leaves nothing owed and no prepaid.
        val settle = Payment(id = "s", loanId = "M", name = "Muhammed", date = asOf, type = "Both", amount = 1_391_715.0, createdAt = 4)
        val split = InterestPolicy.resplitBorrowerPayments(muhammed, rows + settle)
        val posted = split.single { it.id == "s" }
        assertEquals(1_391_715.15, posted.principal, 1e-9)
        assertEquals(-15L, posted.roundingPaise)
        val after = InterestPolicy.paiseBalancesForBorrower(muhammed, asOf, split)
        assertEquals(0L, after.outstanding)
        assertEquals(0L, after.prepaidInterest)
        // Pre-start rows are untouched by the re-split.
        assertEquals(rows[0], split.single { it.id == "pre1" })
        assertEquals(rows[1], split.single { it.id == "pre2" })
        val io = split.single { it.id == "io" }
        assertEquals("Interest Only", io.type)
        assertEquals(0.0, io.principal, 0.0)
        assertEquals(30_378.0, io.interest, 0.0)
    }

    @Test
    fun `interest only preview equals the posted row`() {
        // Preview with the dialog's interestOnly flag == what align/post saves.
        val amount = 30_378.0
        val priors = rows.filter { it.id != "io" }
        val preview = InterestPolicy.previewBorrowerPaymentPaise(
            muhammed, InterestEngine.rupeesToPaise(amount), "2026-09-03", priors, interestOnly = true,
        )
        val posted = InterestPolicy.alignBorrowerPaymentToPaisePreview(muhammed, rows.single { it.id == "io" }, priors)
        assertEquals(InterestEngine.paiseToRupees(preview.towardInterest), posted.interest, 0.0)
        assertEquals(InterestEngine.paiseToRupees(preview.towardPrincipal), posted.principal, 0.0)
        assertEquals(preview.penaltyPaise, posted.penaltyPaise)
        assertEquals(preview.roundingPaise, posted.roundingPaise)
        assertEquals(2_853_691L, preview.prepaidInterestPaise)
        assertEquals("Interest Only", posted.type)
    }
}
