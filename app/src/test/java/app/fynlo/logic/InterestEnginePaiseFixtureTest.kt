package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Golden fixtures A–D for lean v1 paise rules, exercised through InterestEngine
 * (single money path — no parallel LeanV1* engine).
 */
class InterestEnginePaiseFixtureTest {

    private val start = LocalDate.of(2026, 1, 1)
    private val feb1 = LocalDate.of(2026, 2, 1)
    private val mar1 = LocalDate.of(2026, 3, 1)
    private val principal = 1_000_000L
    private val rateBps = 1200

    private fun open(method: InterestEngine.PaiseMethod) =
        InterestEngine.openPaiseLoan(principal, rateBps, start, method)

    @Test
    fun `fixture A simple at Feb 1`() {
        val s = InterestEngine.accruePaiseTo(open(InterestEngine.PaiseMethod.SIMPLE), feb1)
        assertEquals(1_000_000L, s.outstandingPrincipalPaise)
        assertEquals(10_191L, s.interestDuePaise)
        assertEquals(1_010_191L, s.outstandingPaise)
    }

    @Test
    fun `fixture A compound at Feb 1`() {
        val s = InterestEngine.accruePaiseTo(open(InterestEngine.PaiseMethod.COMPOUND), feb1)
        assertEquals(1_000_000L, s.outstandingPrincipalPaise)
        assertEquals(10_000L, s.interestDuePaise)
        assertEquals(1_010_000L, s.outstandingPaise)
    }

    @Test
    fun `fixture A reducing at Feb 1`() {
        val s = InterestEngine.accruePaiseTo(open(InterestEngine.PaiseMethod.REDUCING), feb1)
        assertEquals(1_000_000L, s.outstandingPrincipalPaise)
        assertEquals(10_191L, s.interestDuePaise)
        assertEquals(1_010_191L, s.outstandingPaise)
    }

    private fun afterB(method: InterestEngine.PaiseMethod): Pair<InterestEngine.PaiseLoanState, InterestEngine.PaisePaymentSplit> {
        val accrued = InterestEngine.accruePaiseTo(open(method), feb1)
        return InterestEngine.applyPaymentPaise(accrued, 50_000L)
    }

    @Test
    fun `fixture B simple repay 50000`() {
        val (s, a) = afterB(InterestEngine.PaiseMethod.SIMPLE)
        assertEquals(10_191L, a.towardInterest)
        assertEquals(39_809L, a.towardPrincipal)
        assertEquals(960_191L, s.outstandingPrincipalPaise)
        assertEquals(0L, s.interestDuePaise)
    }

    @Test
    fun `fixture B compound repay 50000`() {
        val (s, a) = afterB(InterestEngine.PaiseMethod.COMPOUND)
        assertEquals(10_000L, a.towardInterest)
        assertEquals(40_000L, a.towardPrincipal)
        assertEquals(960_000L, s.outstandingPrincipalPaise)
        assertEquals(0L, s.interestDuePaise)
    }

    @Test
    fun `fixture B reducing repay 50000`() {
        val (s, a) = afterB(InterestEngine.PaiseMethod.REDUCING)
        assertEquals(10_191L, a.towardInterest)
        assertEquals(39_809L, a.towardPrincipal)
        assertEquals(960_191L, s.outstandingPrincipalPaise)
        assertEquals(0L, s.interestDuePaise)
    }

    @Test
    fun `fixture C simple at Mar 1`() {
        val (afterPay, _) = afterB(InterestEngine.PaiseMethod.SIMPLE)
        val s = InterestEngine.accruePaiseTo(afterPay, mar1)
        assertEquals(960_191L, s.outstandingPrincipalPaise)
        assertEquals(9_206L, s.interestDuePaise)
        assertEquals(969_397L, s.outstandingPaise)
    }

    @Test
    fun `fixture C compound at Mar 1`() {
        val (afterPay, _) = afterB(InterestEngine.PaiseMethod.COMPOUND)
        val s = InterestEngine.accruePaiseTo(afterPay, mar1)
        assertEquals(960_000L, s.outstandingPrincipalPaise)
        assertEquals(9_600L, s.interestDuePaise)
        assertEquals(969_600L, s.outstandingPaise)
    }

    @Test
    fun `fixture C reducing at Mar 1`() {
        val (afterPay, _) = afterB(InterestEngine.PaiseMethod.REDUCING)
        val s = InterestEngine.accruePaiseTo(afterPay, mar1)
        assertEquals(960_191L, s.outstandingPrincipalPaise)
        assertEquals(8_839L, s.interestDuePaise)
        assertEquals(969_030L, s.outstandingPaise)
    }

    @Test
    fun `fixture D simple overpay 1000000 after B`() {
        val (afterPay, _) = afterB(InterestEngine.PaiseMethod.SIMPLE)
        val (s, a) = InterestEngine.applyPaymentPaise(afterPay, 1_000_000L)
        assertEquals(0L, a.towardInterest)
        assertEquals(960_191L, a.towardPrincipal)
        assertEquals(39_809L, a.penaltyPaise)
        assertEquals(0L, s.outstandingPrincipalPaise)
        assertEquals(0L, s.interestDuePaise)
        assertTrue(s.isCleared)
        assertEquals(39_809L, s.penaltyPaise)
    }

    @Test
    fun `hand loan rate 0 interest always 0 excess is penalty`() {
        val s0 = InterestEngine.openPaiseLoan(100_000L, 0, start, InterestEngine.PaiseMethod.SIMPLE)
        val s1 = InterestEngine.accruePaiseTo(s0, feb1)
        assertEquals(0L, s1.interestDuePaise)
        val (s2, a) = InterestEngine.applyPaymentPaise(s1, 150_000L)
        assertEquals(0L, a.towardInterest)
        assertEquals(100_000L, a.towardPrincipal)
        assertEquals(50_000L, a.penaltyPaise)
        assertTrue(s2.isCleared)
        val s3 = InterestEngine.accruePaiseTo(s2, mar1)
        assertEquals(0L, s3.interestDuePaise)
    }

    @Test
    fun `isPaiseMethod accepts SI CI RB rejects Both`() {
        assertTrue(InterestEngine.isPaiseMethod("Simple Interest"))
        assertTrue(InterestEngine.isPaiseMethod("CI"))
        assertTrue(InterestEngine.isPaiseMethod("Reducing Balance"))
        assertFalse(InterestEngine.isPaiseMethod("Both"))
        assertFalse(InterestPolicy.usesPaiseMethod("Both"))
        assertTrue(InterestPolicy.usesPaiseMethod("Simple Interest"))
    }

    @Test
    fun `InterestPolicy borrower balances fixture A simple`() {
        val b = Borrower(
            id = "t1",
            name = "Test",
            amount = 10_000.0,
            rate = 12.0,
            date = "2026-01-01",
            intType = "Simple Interest",
        )
        val bal = InterestPolicy.paiseBalancesForBorrower(b, asOf = "2026-02-01")
        assertEquals(1_000_000L, bal.outstandingPrincipal)
        assertEquals(10_191L, bal.interestDue)
    }

    @Test
    fun `InterestPolicy debt preview overpay yields penalty`() {
        val d = Debt(
            id = "d1",
            name = "Lender",
            amount = 100.0,
            rate = 0.0,
            date = "2026-01-01",
            intType = "Simple Interest",
        )
        val preview = InterestPolicy.previewDebtPaymentPaise(d, 15_000L, asOf = "2026-02-01")
        assertEquals(0L, preview.towardInterest)
        assertEquals(10_000L, preview.towardPrincipal)
        assertEquals(5_000L, preview.penaltyPaise)
    }
}
