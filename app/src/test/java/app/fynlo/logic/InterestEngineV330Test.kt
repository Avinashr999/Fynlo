package app.fynlo.logic

import app.fynlo.data.model.BackupData
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import app.fynlo.logic.InterestEngine.PaiseMethod
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v3.3.0 engine fixtures: compound frequency, whole-rupee settle / rounding /
 * penalty (locked spec), payment-date validation, re-split after delete.
 */
class InterestEngineV330Test {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val principal = 1_000_000L // ₹10,000
    private val bps = 1200             // 12%

    private fun assertInvariant(paid: Long, s: InterestEngine.PaisePaymentSplit) =
        assertEquals(paid, s.towardInterest + s.towardPrincipal + s.penaltyPaise + s.roundingPaise)

    // ── Reference + locked settle fixtures ──────────────────────────────────

    @Test
    fun `reference 10000 at 12 simple over 30 days is 9863 paise shown as 99`() {
        val s = InterestEngine.accruePaiseTo(
            InterestEngine.openPaiseLoan(principal, bps, jan1, PaiseMethod.SIMPLE), jan1.plusDays(30),
        )
        assertEquals(9_863L, s.interestDuePaise)
        assertEquals(99L, InterestEngine.wholeRupees(s.interestDuePaise))
    }

    /** ₹10,000 @12% Simple, day 30: pay ₹50 → interest owing 4863, principal 1,000,000. */
    private fun lockedState(): InterestEngine.PaiseLoanState {
        val day30 = jan1.plusDays(30)
        val accrued = InterestEngine.accruePaiseTo(
            InterestEngine.openPaiseLoan(principal, bps, jan1, PaiseMethod.SIMPLE), day30,
        )
        val (s, split) = InterestEngine.applyPaymentPaise(accrued, 5_000L)
        assertInvariant(5_000L, split)
        assertEquals(0L, split.roundingPaise)
        assertEquals(4_863L, s.interestDuePaise)
        assertEquals(1_000_000L, s.outstandingPrincipalPaise)
        val q = InterestEngine.settlementQuote(InterestEngine.paiseBalances(s))
        assertEquals(1_004_863L, q.totalDuePaise)
        assertEquals(10_049L, q.totalDueRupees)
        assertEquals(1_004_900L, q.fullSettlementPaise)
        return s
    }

    @Test
    fun `locked pay 10048 closes with rounding minus 63`() {
        val (s, split) = InterestEngine.applyPaymentPaise(lockedState(), 1_004_800L)
        assertInvariant(1_004_800L, split)
        assertTrue(split.closesLoan)
        assertEquals(0L, split.penaltyPaise)
        assertEquals(-63L, split.roundingPaise)
        assertTrue(s.isCleared)
    }

    @Test
    fun `locked pay 10047 stays open with 163 paise due shown as 2 rupees`() {
        // Spec text said 201 paise; the stated rules give exact − paid = 1,004,863 − 1,004,700 = 163.
        val (s, split) = InterestEngine.applyPaymentPaise(lockedState(), 1_004_700L)
        assertInvariant(1_004_700L, split)
        assertFalse(split.closesLoan)
        assertEquals(0L, split.roundingPaise)
        assertEquals(0L, split.penaltyPaise)
        assertEquals(163L, s.outstandingPaise)
        assertEquals(2L, InterestEngine.wholeRupees(s.outstandingPaise))
        assertFalse(s.isCleared)
    }

    @Test
    fun `locked pay 10050 closes with penalty 100 and rounding plus 37`() {
        val (s, split) = InterestEngine.applyPaymentPaise(lockedState(), 1_005_000L)
        assertInvariant(1_005_000L, split)
        assertEquals(100L, split.penaltyPaise)
        assertEquals(37L, split.roundingPaise)
        assertTrue(s.isCleared)
    }

    @Test
    fun `locked pay 10049 closes with penalty 0 and rounding plus 37`() {
        val (s, split) = InterestEngine.applyPaymentPaise(lockedState(), 1_004_900L)
        assertInvariant(1_004_900L, split)
        assertEquals(0L, split.penaltyPaise)
        assertEquals(37L, split.roundingPaise)
        assertTrue(s.isCleared)
    }

    @Test
    fun `over exact by 100 but under rounded total plus 100 closes with rounding and no penalty`() {
        // exact 1,004,863; roundedTotal 1,004,900; paid 1,004,963 < 1,005,000 → no penalty, rounding +100
        val (s, split) = InterestEngine.applyPaymentPaise(lockedState(), 1_004_963L)
        assertInvariant(1_004_963L, split)
        assertEquals(0L, split.penaltyPaise)
        assertEquals(100L, split.roundingPaise)
        assertTrue(split.closesLoan)
        assertTrue(s.isCleared)
    }

    /** exact 1,004,840 (interest 4,840 + principal 1,000,000), shown ₹10,048 (roundedTotal 1,004,800). */
    private fun exact1004840() = lockedState().copy(interestDuePaise = 4_840L)

    @Test
    fun `exact 1004840 pay 10049 gives penalty 100 and rounding minus 40`() {
        val st = exact1004840()
        assertEquals(1_004_840L, st.outstandingPaise)
        assertEquals(10_048L, InterestEngine.wholeRupees(st.outstandingPaise))
        val (s, split) = InterestEngine.applyPaymentPaise(st, 1_004_900L)
        assertInvariant(1_004_900L, split)
        assertEquals(4_840L, split.towardInterest)
        assertEquals(1_000_000L, split.towardPrincipal)
        assertEquals(100L, split.penaltyPaise)
        assertEquals(-40L, split.roundingPaise)
        assertTrue(s.isCleared)
        // Same through the stateless overload.
        assertEquals(split, InterestEngine.allocatePaymentPaise(1_000_000L, 4_840L, 1_004_900L))
    }

    @Test
    fun `exact 1004840 pay 10048 closes with rounding minus 40 and no penalty`() {
        val (s, split) = InterestEngine.applyPaymentPaise(exact1004840(), 1_004_800L)
        assertInvariant(1_004_800L, split)
        assertEquals(0L, split.penaltyPaise)
        assertEquals(-40L, split.roundingPaise)
        assertTrue(split.closesLoan)
        assertTrue(s.isCleared)
    }

    @Test
    fun `exact 1004840 pay 10047 is a partial payment`() {
        val (s, split) = InterestEngine.applyPaymentPaise(exact1004840(), 1_004_700L)
        assertInvariant(1_004_700L, split)
        assertFalse(split.closesLoan)
        assertEquals(0L, split.penaltyPaise)
        assertEquals(0L, split.roundingPaise)
        assertEquals(4_840L, split.towardInterest)
        assertEquals(999_860L, split.towardPrincipal)
        assertEquals(140L, s.outstandingPaise)
        assertFalse(s.isCleared)
    }

    @Test
    fun `interest only 99 against 98_63 puts 37 paise on principal`() {
        val s = InterestEngine.accruePaiseTo(
            InterestEngine.openPaiseLoan(principal, bps, jan1, PaiseMethod.SIMPLE), jan1.plusDays(30),
        )
        val q = InterestEngine.settlementQuote(InterestEngine.paiseBalances(s))
        assertEquals(9_900L, q.interestOnlyPaise)
        val (_, split) = InterestEngine.applyPaymentPaise(s, q.interestOnlyPaise)
        assertInvariant(9_900L, split)
        assertEquals(9_863L, split.towardInterest)
        assertEquals(37L, split.towardPrincipal)
        assertEquals(0L, split.roundingPaise)
    }

    @Test
    fun `interest only rounded down leaves paise owing and carrying forward`() {
        // 11 days: 1,000,000*1200*11/3,650,000 = 3616 paise → rounds to ₹36
        val s = InterestEngine.accruePaiseTo(
            InterestEngine.openPaiseLoan(principal, bps, jan1, PaiseMethod.SIMPLE), jan1.plusDays(11),
        )
        assertEquals(3_616L, s.interestDuePaise)
        val q = InterestEngine.settlementQuote(InterestEngine.paiseBalances(s))
        assertEquals(3_600L, q.interestOnlyPaise)
        val (after, split) = InterestEngine.applyPaymentPaise(s, q.interestOnlyPaise)
        assertInvariant(3_600L, split)
        assertEquals(16L, after.interestDuePaise)
    }

    @Test
    fun `settlement within 1 rupee short closes with write off and ledger sums exactly`() {
        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01", intType = "Simple Interest")
        val first = Payment(id = "p1", loanId = "L", name = "L", date = "2026-01-31", type = "Both", amount = 50.0, createdAt = 1)
        val p1 = InterestPolicy.alignBorrowerPaymentToPaisePreview(b, first)
        val quote = InterestPolicy.settlementQuoteForBorrower(b, "2026-01-31", listOf(p1))
        assertEquals(1_004_863L, quote.totalDuePaise)
        val second = Payment(id = "p2", loanId = "L", name = "L", date = "2026-01-31", type = "Full Settlement", amount = 10_048.0, createdAt = 2)
        val p2 = InterestPolicy.alignBorrowerPaymentToPaisePreview(b, second, priorPayments = listOf(p1))
        assertEquals(-63L, p2.roundingPaise)
        assertEquals(0L, p2.penaltyPaise)
        assertTrue(p2.notes.contains("Rounding write-off ₹0.63"))
        val rows = listOf(p1, p2)
        rows.forEach { r ->
            assertEquals(
                InterestEngine.rupeesToPaise(r.amount),
                InterestEngine.rupeesToPaise(r.interest) + InterestEngine.rupeesToPaise(r.principal) + r.penaltyPaise + r.roundingPaise,
            )
        }
        // Absorbed principal == loan principal; absorbed interest == interest accrued (9863).
        assertEquals(1_000_000L, rows.sumOf { InterestEngine.rupeesToPaise(it.principal) })
        assertEquals(9_863L, rows.sumOf { InterestEngine.rupeesToPaise(it.interest) })
        // Closed, and stays closed later (no accrual on a cleared loan).
        assertEquals(0L, InterestPolicy.paiseBalancesForBorrower(b, "2026-01-31", rows).outstanding)
        assertEquals(0L, InterestPolicy.paiseBalancesForBorrower(b, "2026-12-31", rows).outstanding)
    }

    @Test
    fun `paying less than 1 rupee over creates no penalty`() {
        val d = Debt(id = "D", name = "D", amount = 100.0, rate = 0.0, date = "2026-01-01", intType = "Simple Interest")
        val row = InterestPolicy.alignDebtPaymentToPaisePreview(
            d, DebtPayment(id = "x", debtId = "D", name = "D", date = "2026-02-01", type = "Both", amount = 100.99),
        )
        assertEquals(0L, row.penaltyPaise)
        assertEquals(99L, row.roundingPaise)
        assertFalse(row.notes.contains("Penalty"))
        assertEquals(0.0, InterestPolicy.khathaPenaltyRupees(100.5, 100.0, 0.0), 0.0)
    }

    @Test
    fun `paying 1 rupee or more over creates penaltyPaise with whole rupee note`() {
        val b = Borrower(id = "L", name = "L", amount = 100.0, rate = 0.0, date = "2026-01-01", intType = "Simple Interest")
        val row = InterestPolicy.alignBorrowerPaymentToPaisePreview(
            b, Payment(id = "x", loanId = "L", name = "L", date = "2026-02-01", type = "Both", amount = 101.0, notes = "cash"),
        )
        assertEquals(100L, row.penaltyPaise)
        assertEquals(0L, row.roundingPaise)
        assertEquals("cash\nPenalty on this account ₹1", row.notes)
        assertEquals(100L, InterestPolicy.penaltyPaiseOf(row))
        // Re-aligning never duplicates engine tags.
        val again = InterestPolicy.alignBorrowerPaymentToPaisePreview(b, row)
        assertEquals(row.notes, again.notes)
    }

    // ── Compound frequency ──────────────────────────────────────────────────

    @Test
    fun `frequency normalisation defaults to Monthly`() {
        assertEquals("Monthly", InterestEngine.normalizeCompoundFrequency(null))
        assertEquals("Monthly", InterestEngine.normalizeCompoundFrequency(""))
        assertEquals("Monthly", InterestEngine.normalizeCompoundFrequency("weekly"))
        assertEquals("Quarterly", InterestEngine.normalizeCompoundFrequency("quarterly"))
        assertEquals(1, InterestEngine.compoundMonthsFor("Monthly"))
        assertEquals(3, InterestEngine.compoundMonthsFor("Quarterly"))
        assertEquals(12, InterestEngine.compoundMonthsFor("Yearly"))
        assertEquals("Monthly", Borrower(id = "a", name = "a", amount = 1.0, rate = 1.0, date = "2026-01-01").compoundFrequency)
        assertEquals("Monthly", Debt(id = "a", name = "a", amount = 1.0, rate = 1.0, date = "2026-01-01").compoundFrequency)
    }

    private fun compound(months: Int, start: LocalDate = jan1) =
        InterestEngine.openPaiseLoan(principal, bps, start, PaiseMethod.COMPOUND, compoundMonths = months)

    @Test
    fun `quarterly compound fixture engine lend and debt`() {
        // Daily accrual. Jan 1→Apr 1 (90 d): 1,000,000×1200×90 = 108,000,000,000 / 3,650,000 = 29,589 rem 150,000
        val apr1 = InterestEngine.accruePaiseTo(compound(3), LocalDate.of(2026, 4, 1))
        assertEquals(1_000_000L, apr1.outstandingPrincipalPaise)
        assertEquals(29_589L, apr1.interestDuePaise)
        val may1 = InterestEngine.accruePaiseTo(compound(3), LocalDate.of(2026, 5, 1))
        assertEquals(1_000_000L, may1.outstandingPrincipalPaise)
        // Q1 interest (unpaid) earns interest after Apr 1:
        // Apr 1→May 1 (30 d): 1,029,589×1200×30 + 150,000 = 37,065,354,000 / 3,650,000 = 10,154
        assertEquals(29_589L + 10_154L, may1.interestDuePaise)
        // Apr 1→Jul 1 (91 d): 1,029,589×1200×91 + 150,000 = 112,431,268,800 / 3,650,000 = 30,803; Q1 capitalised
        val jul1 = InterestEngine.accruePaiseTo(compound(3), LocalDate.of(2026, 7, 1))
        assertEquals(1_029_589L, jul1.outstandingPrincipalPaise)
        assertEquals(30_803L, jul1.interestDuePaise)

        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Quarterly")
        val d = Debt(id = "D", name = "D", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Quarterly")
        for (asOf in listOf("2026-04-01", "2026-05-01", "2026-07-01")) {
            val e = InterestEngine.accruePaiseTo(compound(3), LocalDate.parse(asOf))
            val lb = InterestPolicy.paiseBalancesForBorrower(b, asOf)
            val db = InterestPolicy.paiseBalancesForDebt(d, asOf)
            assertEquals(e.outstandingPrincipalPaise, lb.outstandingPrincipal)
            assertEquals(e.interestDuePaise, lb.interestDue)
            assertEquals(lb, db)
        }
        // Monthly (default) differs from quarterly at Jul 1.
        assertNotEquals(
            InterestPolicy.paiseBalancesForBorrower(b.copy(compoundFrequency = "Monthly"), "2026-07-01"),
            InterestPolicy.paiseBalancesForBorrower(b, "2026-07-01"),
        )
    }

    @Test
    fun `yearly compound fixture engine lend and debt`() {
        val y1 = InterestEngine.accruePaiseTo(compound(12), LocalDate.of(2027, 1, 1))
        assertEquals(1_000_000L, y1.outstandingPrincipalPaise)
        assertEquals(120_000L, y1.interestDuePaise)
        val y2 = InterestEngine.accruePaiseTo(compound(12), LocalDate.of(2028, 1, 1))
        assertEquals(1_120_000L, y2.outstandingPrincipalPaise)
        assertEquals(134_400L, y2.interestDuePaise)
        val mid = InterestEngine.accruePaiseTo(compound(12), LocalDate.of(2026, 7, 1))
        assertEquals(59_506L, mid.interestDuePaise) // 181 days daily stub: 1e6*1200*181/3,650,000

        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Yearly")
        val d = Debt(id = "D", name = "D", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Yearly")
        val lb = InterestPolicy.paiseBalancesForBorrower(b, "2028-01-01")
        assertEquals(1_120_000L, lb.outstandingPrincipal)
        assertEquals(134_400L, lb.interestDue)
        assertEquals(lb, InterestPolicy.paiseBalancesForDebt(d, "2028-01-01"))
    }

    @Test
    fun `31 Jan quarterly compounds on 30 Apr then 31 Jul`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 4, 30), InterestEngine.compoundDatePaise(jan31, 1, 3))
        assertEquals(LocalDate.of(2026, 7, 31), InterestEngine.compoundDatePaise(jan31, 2, 3))
        assertEquals(LocalDate.of(2026, 4, 30), InterestEngine.nextCompoundDatePaise(jan31, jan31, 3))
        assertEquals(LocalDate.of(2026, 7, 31), InterestEngine.nextCompoundDatePaise(jan31, LocalDate.of(2026, 4, 30), 3))
        // Engine: 30 Apr is the compounding date. Jan 31→Apr 30 (89 d):
        //   1,000,000×1200×89 = 106,800,000,000 / 3,650,000 = 29,260 rem 1,000,000
        val apr30 = InterestEngine.accruePaiseTo(compound(3, jan31), LocalDate.of(2026, 4, 30))
        assertEquals(29_260L, apr30.interestDuePaise)
        assertEquals(LocalDate.of(2026, 4, 30), apr30.lastAccrualDate)
        val apr29 = InterestEngine.accruePaiseTo(compound(3, jan31), LocalDate.of(2026, 4, 29))
        assertEquals(28_931L, apr29.interestDuePaise) // 88 stub days
        // Apr 30→Jul 31 (92 d): 1,029,260×1200×92 + 1,000,000 = 113,631,304,000 / 3,650,000 = 31,131
        val jul31 = InterestEngine.accruePaiseTo(compound(3, jan31), LocalDate.of(2026, 7, 31))
        assertEquals(1_029_260L, jul31.outstandingPrincipalPaise)
        assertEquals(31_131L, jul31.interestDuePaise)
        val jul30 = InterestEngine.accruePaiseTo(compound(3, jan31), LocalDate.of(2026, 7, 30))
        // Q1 interest is capitalised only on the next compounding date (31 Jul).
        assertEquals(1_000_000L, jul30.outstandingPrincipalPaise)
    }

    @Test
    fun `monthly compound from 31 Jan is not chained`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), InterestEngine.compoundDatePaise(jan31, 1, 1))
        assertEquals(LocalDate.of(2026, 3, 31), InterestEngine.compoundDatePaise(jan31, 2, 1))
        assertEquals(LocalDate.of(2026, 3, 31), InterestEngine.nextMonthAnniversaryPaise(jan31, LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `29 Feb yearly compounds on 28 Feb in non leap years`() {
        val feb29 = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2029, 2, 28), InterestEngine.compoundDatePaise(feb29, 1, 12))
        assertEquals(LocalDate.of(2030, 2, 28), InterestEngine.compoundDatePaise(feb29, 2, 12))
        assertEquals(LocalDate.of(2032, 2, 29), InterestEngine.compoundDatePaise(feb29, 4, 12))
        val y1 = InterestEngine.accruePaiseTo(compound(12, feb29), LocalDate.of(2029, 2, 28))
        assertEquals(120_000L, y1.interestDuePaise)
        assertEquals(LocalDate.of(2029, 2, 28), y1.lastAccrualDate)
        val y2 = InterestEngine.accruePaiseTo(compound(12, feb29), LocalDate.of(2030, 2, 28))
        assertEquals(1_120_000L, y2.outstandingPrincipalPaise)
        assertEquals(134_400L, y2.interestDuePaise)
    }

    @Test
    fun `payment exactly on a compounding date`() {
        val apr1 = LocalDate.of(2026, 4, 1)
        val s = InterestEngine.accruePaiseTo(compound(3), apr1)
        val (after, split) = InterestEngine.applyPaymentPaise(s, 50_000L)
        assertInvariant(50_000L, split)
        assertEquals(29_589L, split.towardInterest) // Jan 1→Apr 1, 90 d daily (rem 150,000)
        assertEquals(20_411L, split.towardPrincipal)
        // Apr 1→Jul 1 (91 d): 979,589×1200×91 + 150,000 = 106,971,268,800 / 3,650,000 = 29,307
        val jul1 = InterestEngine.accruePaiseTo(after, LocalDate.of(2026, 7, 1))
        assertEquals(979_589L, jul1.outstandingPrincipalPaise)
        assertEquals(29_307L, jul1.interestDuePaise)
        // Same through replay and through InterestPolicy (lend + debt).
        val replay = InterestEngine.replayPaiseTo(compound(3), listOf(apr1 to 50_000L), LocalDate.of(2026, 7, 1))
        assertEquals(jul1.outstandingPrincipalPaise, replay.outstandingPrincipalPaise)
        assertEquals(jul1.interestDuePaise, replay.interestDuePaise)
        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Quarterly")
        val bal = InterestPolicy.paiseBalancesForBorrower(b, "2026-07-01",
            listOf(Payment(id = "p", loanId = "L", name = "L", date = "2026-04-01", type = "Both", amount = 500.0)))
        assertEquals(979_589L, bal.outstandingPrincipal)
        assertEquals(29_307L, bal.interestDue)
        val d = Debt(id = "D", name = "D", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Quarterly")
        val dbal = InterestPolicy.paiseBalancesForDebt(d, "2026-07-01",
            listOf(DebtPayment(id = "p", debtId = "D", name = "D", date = "2026-04-01", type = "Both", amount = 500.0)))
        assertEquals(bal, dbal)
    }

    // ── Mid-period payment: no double charge (lend + debt) ──────────────────

    @Test
    fun `quarterly compound with mid-period payment has no double charge`() {
        val feb15 = LocalDate.of(2026, 2, 15)
        var st = InterestEngine.accruePaiseTo(compound(3), feb15)
        // Jan 1→Feb 15 (45 d): 1,000,000×1200×45 = 54,000,000,000 / 3,650,000 = 14,794 rem 1,900,000
        assertEquals(14_794L, st.interestDuePaise)
        val (afterPay, split) = InterestEngine.applyPaymentPaise(st, 100_000L)
        assertInvariant(100_000L, split)
        assertEquals(14_794L, split.towardInterest)
        assertEquals(85_206L, split.towardPrincipal)
        st = InterestEngine.accruePaiseTo(afterPay, LocalDate.of(2026, 4, 1))
        // Feb 15→Apr 1 (45 d): 914,794×1200×45 + 1,900,000 = 49,400,776,000 / 3,650,000 = 13,534
        // (old code: full quarter 914,794×3600/120,000 = 27,443 — re-charged Jan 1→Feb 15)
        assertEquals(914_794L, st.outstandingPrincipalPaise)
        assertEquals(13_534L, st.interestDuePaise)
        assertEquals(14_794L + 13_534L, split.towardInterest + st.interestDuePaise) // = daily SI on actual balances
        st = InterestEngine.accruePaiseTo(st, LocalDate.of(2026, 7, 1))
        // Apr 1→Jul 1 (91 d) on principal + unpaid Q1 interest (rem 1,676,000):
        //   928,328×1200×91 + 1,676,000 = 101,375,093,600 / 3,650,000 = 27,773; Q1 capitalised
        assertEquals(928_328L, st.outstandingPrincipalPaise)
        assertEquals(27_773L, st.interestDuePaise)

        val payments = listOf(Payment(id = "p", loanId = "L", name = "L", date = "2026-02-15", type = "Both", amount = 1_000.0))
        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Quarterly")
        val lb = InterestPolicy.paiseBalancesForBorrower(b, "2026-07-01", payments)
        assertEquals(928_328L, lb.outstandingPrincipal)
        assertEquals(27_773L, lb.interestDue)
        val d = Debt(id = "D", name = "D", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = "Compound Interest", compoundFrequency = "Quarterly")
        val db = InterestPolicy.paiseBalancesForDebt(d, "2026-07-01",
            listOf(DebtPayment(id = "p", debtId = "D", name = "D", date = "2026-02-15", type = "Both", amount = 1_000.0)))
        assertEquals(lb, db)
        // Posted split obeys the invariant too.
        val posted = InterestPolicy.alignBorrowerPaymentToPaisePreview(b, payments.single())
        assertEquals(100_000L, InterestEngine.rupeesToPaise(posted.interest) + InterestEngine.rupeesToPaise(posted.principal) +
            posted.penaltyPaise + posted.roundingPaise)
    }

    @Test
    fun `monthly compound with mid-period payment has no double charge`() {
        val jan15 = LocalDate.of(2026, 1, 15)
        val (afterPay, split) = InterestEngine.applyPaymentPaise(
            InterestEngine.accruePaiseTo(compound(1), jan15), 50_000L,
        )
        assertInvariant(50_000L, split)
        assertEquals(4_602L, split.towardInterest) // Jan 1→15: 14 d on 1,000,000
        val feb1 = InterestEngine.accruePaiseTo(afterPay, LocalDate.of(2026, 2, 1))
        assertEquals(954_602L, feb1.outstandingPrincipalPaise)
        assertEquals(5_336L, feb1.interestDuePaise) // Jan 15→Feb 1: 17 d on 954,602 (not 9,546)
        // January total = 4,602 + 5,336 = 9,938 = daily SI on actual balances; nothing charged twice.
        val mar1 = InterestEngine.accruePaiseTo(feb1, LocalDate.of(2026, 3, 1))
        // Feb 1→Mar 1 (28 d) on 954,602 + 5,336 = 959,938 (rem 180,800):
        //   959,938×1200×28 + 180,800 = 32,254,097,600 / 3,650,000 = 8,836; Jan interest capitalised
        assertEquals(959_938L, mar1.outstandingPrincipalPaise)
        assertEquals(8_836L, mar1.interestDuePaise)
        // Second mid-period payment (Feb 10) that clears the pending Jan interest first.
        val feb10 = InterestEngine.accruePaiseTo(feb1, LocalDate.of(2026, 2, 10))
        val (afterPay2, split2) = InterestEngine.applyPaymentPaise(feb10, 20_000L)
        assertInvariant(20_000L, split2)
        assertEquals(0L, afterPay2.pendingCapitalPaise)
        val mar1b = InterestEngine.accruePaiseTo(afterPay2, LocalDate.of(2026, 3, 1))
        // Replay from scratch must agree with stepwise, lend and debt.
        val replay = InterestEngine.replayPaiseTo(compound(1),
            listOf(jan15 to 50_000L, LocalDate.of(2026, 2, 10) to 20_000L), LocalDate.of(2026, 3, 1))
        assertEquals(mar1b.outstandingPrincipalPaise, replay.outstandingPrincipalPaise)
        assertEquals(mar1b.interestDuePaise, replay.interestDuePaise)
        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01", intType = "Compound Interest")
        val rows = listOf(
            Payment(id = "a", loanId = "L", name = "L", date = "2026-01-15", type = "Both", amount = 500.0),
            Payment(id = "b", loanId = "L", name = "L", date = "2026-02-10", type = "Both", amount = 200.0),
        )
        val lb = InterestPolicy.paiseBalancesForBorrower(b, "2026-03-01", rows)
        assertEquals(replay.outstandingPrincipalPaise, lb.outstandingPrincipal)
        assertEquals(replay.interestDuePaise, lb.interestDue)
        val d = Debt(id = "D", name = "D", amount = 10_000.0, rate = 12.0, date = "2026-01-01", intType = "Compound Interest")
        assertEquals(lb, InterestPolicy.paiseBalancesForDebt(d, "2026-03-01", rows.map {
            DebtPayment(id = it.id, debtId = "D", name = "D", date = it.date, type = "Both", amount = it.amount)
        }))
        InterestPolicy.resplitBorrowerPayments(b, rows).forEach { r ->
            assertEquals(InterestEngine.rupeesToPaise(r.amount), InterestEngine.rupeesToPaise(r.interest) +
                InterestEngine.rupeesToPaise(r.principal) + r.penaltyPaise + r.roundingPaise)
        }
    }

    // ── Re-split after delete / undo ────────────────────────────────────────

    private fun resplitCase(intType: String, freq: String) {
        val b = Borrower(id = "L", name = "L", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = intType, compoundFrequency = freq)
        val raw = listOf(
            Payment(id = "p1", loanId = "L", name = "L", date = "2026-01-15", type = "Both", amount = 500.0, createdAt = 1),
            Payment(id = "p2", loanId = "L", name = "L", date = "2026-02-01", type = "Both", amount = 200.0, createdAt = 2),
            Payment(id = "p3", loanId = "L", name = "L", date = "2026-03-10", type = "Both", amount = 300.0, createdAt = 3),
        )
        val all = InterestPolicy.resplitBorrowerPayments(b, raw)
        // Delete p1 → every later row must match a fresh engine replay without p1.
        val after = InterestPolicy.resplitBorrowerPayments(b, all.filter { it.id != "p1" })
        val method = InterestEngine.paiseMethodOrNull(intType)!!
        var st = InterestEngine.openPaiseLoan(principal, bps, jan1, method, InterestEngine.compoundMonthsFor(freq))
        for (row in after) {
            st = InterestEngine.accruePaiseTo(st, LocalDate.parse(row.date))
            val (next, split) = InterestEngine.applyPaymentPaise(st, InterestEngine.rupeesToPaise(row.amount))
            st = next
            assertEquals(InterestEngine.paiseToRupees(split.towardInterest), row.interest, 0.0)
            assertEquals(InterestEngine.paiseToRupees(split.towardPrincipal), row.principal, 0.0)
        }
        // ...and the old splits were genuinely different (so re-split mattered).
        val oldP2 = all.single { it.id == "p2" }
        val newP2 = after.single { it.id == "p2" }
        assertNotEquals(oldP2.interest, newP2.interest, 0.0)
        // Re-split is idempotent.
        assertEquals(after, InterestPolicy.resplitBorrowerPayments(b, after))
        // Debt side re-splits identically.
        val d = Debt(id = "D", name = "D", amount = 10_000.0, rate = 12.0, date = "2026-01-01",
            intType = intType, compoundFrequency = freq)
        val dAfter = InterestPolicy.resplitDebtPayments(d, after.map {
            DebtPayment(id = it.id, debtId = "D", name = "D", date = it.date, type = "Both", amount = it.amount, createdAt = it.createdAt)
        })
        assertEquals(after.map { it.interest to it.principal }, dAfter.map { it.interest to it.principal })
    }

    @Test fun `deleting older payment re-splits later payments reducing`() = resplitCase("Reducing Balance", "Monthly")
    @Test fun `deleting older payment re-splits later payments compound monthly`() = resplitCase("Compound Interest", "Monthly")
    @Test fun `deleting older payment re-splits later payments compound quarterly`() = resplitCase("Compound Interest", "Quarterly")

    // ── Payment date validation ─────────────────────────────────────────────

    @Test
    fun `payment dated before start is rejected and future dates are flagged not rejected`() {
        val today = LocalDate.of(2026, 9, 25)
        assertEquals(InterestEngine.PaymentDateCheck.BEFORE_START,
            InterestEngine.checkPaymentDate("2026-03-01", "2026-02-28", today))
        assertEquals(InterestEngine.PaymentDateCheck.OK, InterestEngine.checkPaymentDate("2026-03-01", "2026-03-01", today))
        assertEquals(InterestEngine.PaymentDateCheck.FUTURE, InterestEngine.checkPaymentDate("2026-03-01", "2026-10-01", today))
        assertTrue(InterestEngine.isFutureDate("2026-09-26", today))
        assertFalse(InterestEngine.isFutureDate("2026-09-25", today))
        assertEquals(InterestEngine.PAYMENT_BEFORE_START_MESSAGE, InterestPolicy.paymentDateError("2026-03-01", "2026-02-28"))
        assertNull(InterestPolicy.paymentDateError("2026-03-01", "2099-01-01"))
    }

    // ── Backup: missing fields read as defaults, hash unaffected ────────────

    @Test
    fun `old backup json without new fields restores Monthly and zero penalty rounding`() {
        val json = """{"schemaVersion":1,
            "borrowers":[{"id":"b","name":"n","amount":1.0,"rate":1.0,"date":"2026-01-01","type":"Compound Interest"}],
            "debts":[{"id":"d","name":"n","amount":1.0,"rate":1.0,"date":"2026-01-01"}],
            "payments":[{"id":"p","loanId":"b","name":"n","date":"2026-01-02","type":"Both","amount":1.0}],
            "debtPayments":[{"id":"q","debtId":"d","name":"n","date":"2026-01-02","type":"Both","amount":1.0}]}"""
        val data = Json.decodeFromString<BackupData>(json)
        assertEquals("Monthly", data.borrowers.single().compoundFrequency)
        assertEquals("Monthly", data.debts.single().compoundFrequency)
        assertEquals(0L, data.payments.single().penaltyPaise)
        assertEquals(0L, data.payments.single().roundingPaise)
        assertEquals(0L, data.debtPayments.single().penaltyPaise)
        assertEquals(0L, data.debtPayments.single().roundingPaise)
        // New values round-trip through backup JSON.
        val withValues = data.copy(
            borrowers = data.borrowers.map { it.copy(compoundFrequency = "Quarterly") },
            payments = data.payments.map { it.copy(penaltyPaise = 137L, roundingPaise = -63L) },
        )
        val back = Json.decodeFromString<BackupData>(Json.encodeToString(BackupData.serializer(), withValues))
        assertEquals("Quarterly", back.borrowers.single().compoundFrequency)
        assertEquals(137L, back.payments.single().penaltyPaise)
        assertEquals(-63L, back.payments.single().roundingPaise)
        // Default-valued new fields are omitted, so pre-3.3.0 v2 backup hashes still verify.
        assertFalse(Json.encodeToString(BackupData.serializer(), data).contains("compoundFrequency"))
    }
}
