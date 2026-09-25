package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import app.fynlo.logic.InterestEngine.PaiseEvent
import app.fynlo.logic.InterestEngine.PaiseMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v3.3.1 — explicit 'Interest Only' rows never reduce principal (excess becomes
 * prepaid interest that offsets later interest), re-split keeps them and skips
 * pre-start rows, and one shared balance function serves every screen.
 */
class InterestOnlyV331Test {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan31 = LocalDate.of(2026, 1, 31)
    private val mar2 = LocalDate.of(2026, 3, 2)
    private val principal = 1_000_000L // ₹10,000
    private val bps = 1200             // 12%

    private fun open(method: PaiseMethod) = InterestEngine.openPaiseLoan(principal, bps, jan1, method)

    // ── Engine ──────────────────────────────────────────────────────────────

    @Test
    fun `simple interest only excess is prepaid and offsets later interest, principal unchanged`() {
        val accrued = InterestEngine.accruePaiseTo(open(PaiseMethod.SIMPLE), jan31)
        assertEquals(9_863L, accrued.interestDuePaise)
        val (paid, split) = InterestEngine.applyInterestOnlyPaymentPaise(accrued, 15_000L)
        assertEquals(15_000L, split.towardInterest)
        assertEquals(0L, split.towardPrincipal)
        assertEquals(5_137L, split.prepaidInterestPaise)
        assertEquals(15_000L, split.towardInterest + split.towardPrincipal + split.penaltyPaise + split.roundingPaise)
        assertEquals(principal, paid.outstandingPrincipalPaise)
        assertEquals(0L, paid.interestDuePaise)
        assertEquals(5_137L, paid.prepaidInterestPaise)
        // 60 days of interest without any payment = 19,726; interest-only ₹150 leaves 4,726.
        val later = InterestEngine.accruePaiseTo(paid, mar2)
        assertEquals(principal, later.outstandingPrincipalPaise)
        assertEquals(19_726L - 15_000L, later.interestDuePaise)
        assertEquals(0L, later.prepaidInterestPaise)
        // Partly consumed: 10 days later only 3,287 accrued, 1,850 prepaid left.
        val tenDays = InterestEngine.accruePaiseTo(paid, jan31.plusDays(10))
        assertEquals(0L, tenDays.interestDuePaise)
        assertEquals(5_137L - 3_287L, tenDays.prepaidInterestPaise)
    }

    @Test
    fun `replay honours the interest only flag for every method`() {
        for (method in PaiseMethod.values()) {
            val io = InterestEngine.replayPaiseEventsTo(open(method), listOf(PaiseEvent(jan31, 15_000L, interestOnly = true)), mar2)
            val none = InterestEngine.replayPaiseTo(open(method), emptyList(), mar2)
            val normal = InterestEngine.replayPaiseTo(open(method), listOf(jan31 to 15_000L), mar2)
            assertEquals("$method principal", principal, io.outstandingPrincipalPaise)
            // Nothing capitalised while prepaid covered the interest, so the total paid
            // interest-only simply comes off the no-payment interest.
            if (method != PaiseMethod.COMPOUND) {
                assertEquals("$method interest", none.interestDuePaise - 15_000L, io.interestDuePaise)
            } else {
                assertTrue(io.interestDuePaise < none.interestDuePaise)
            }
            // A normal payment of the same amount does reduce principal.
            assertTrue("$method normal", normal.outstandingPrincipalPaise < principal)
        }
    }

    @Test
    fun `compound interest only prepaid is used before capitalisation`() {
        val c = InterestEngine.replayPaiseEventsTo(open(PaiseMethod.COMPOUND),
            listOf(PaiseEvent(jan31, 20_000L, interestOnly = true)), LocalDate.of(2026, 3, 1))
        // Jan31→Mar1 accrues ~9,534 < 10,137 prepaid, so nothing is due or capitalised.
        assertEquals(principal, c.outstandingPrincipalPaise)
        assertEquals(0L, c.interestDuePaise)
        assertEquals(0L, c.pendingCapitalPaise)
        assertTrue(c.prepaidInterestPaise > 0L)
    }

    // ── Policy: lend + debt ─────────────────────────────────────────────────

    private fun lender(intType: String = "Simple Interest") =
        Borrower(id = "L", name = "M", amount = 10_000.0, rate = 12.0, date = "2026-01-01", intType = intType)

    private fun debt(intType: String = "Simple Interest") =
        Debt(id = "D", name = "M", amount = 10_000.0, rate = 12.0, date = "2026-01-01", intType = intType)

    private fun io(id: String, date: String, amount: Double, createdAt: Long = 0L) =
        Payment(id = id, loanId = "L", name = "M", date = date, type = "Interest Only", amount = amount, createdAt = createdAt)

    private fun Payment.toDebt() = DebtPayment(id = id, debtId = "D", name = "M", date = date, type = type,
        amount = amount, principal = principal, interest = interest, createdAt = createdAt, notes = notes)

    @Test
    fun `lend and debt balances keep principal on interest only excess`() {
        for (t in listOf("Simple Interest", "Reducing Balance", "Compound Interest")) {
            val rows = listOf(io("a", "2026-01-31", 150.0))
            val lb = InterestPolicy.paiseBalancesForBorrower(lender(t), "2026-03-02", rows)
            assertEquals(t, principal, lb.outstandingPrincipal)
            val db = InterestPolicy.paiseBalancesForDebt(debt(t), "2026-03-02", rows.map { it.toDebt() })
            assertEquals(t, lb, db)
            val shared = InterestPolicy.borrowerBalance(lender(t), rows, "2026-03-02")
            assertEquals(10_000.0, shared.principal, 0.0)
            assertEquals(InterestEngine.paiseToRupees(lb.interestDue), shared.interestDue, 0.0)
            val sharedDebt = InterestPolicy.debtBalance(debt(t), rows.map { it.toDebt() }, "2026-03-02")
            assertEquals(shared, sharedDebt)
        }
        val simple = InterestPolicy.paiseBalancesForBorrower(lender(), "2026-03-02", listOf(io("a", "2026-01-31", 150.0)))
        assertEquals(4_726L, simple.interestDue)
    }

    @Test
    fun `align keeps interest only type with principal 0 and tags prepaid`() {
        val row = InterestPolicy.alignBorrowerPaymentToPaisePreview(lender(), io("a", "2026-01-31", 150.0))
        assertEquals("Interest Only", row.type)
        assertEquals(0.0, row.principal, 0.0)
        assertEquals(150.0, row.interest, 0.0)
        assertEquals(0L, row.penaltyPaise)
        assertEquals(0L, row.roundingPaise)
        assertTrue(row.notes, row.notes.contains(InterestPolicy.PREPAID_INTEREST_NOTE_PREFIX))
        val drow = InterestPolicy.alignDebtPaymentToPaisePreview(debt(), io("a", "2026-01-31", 150.0).toDebt())
        assertEquals("Interest Only", drow.type)
        assertEquals(0.0, drow.principal, 0.0)
        assertEquals(150.0, drow.interest, 0.0)
        // Re-aligning does not duplicate the tag.
        val again = InterestPolicy.alignBorrowerPaymentToPaisePreview(lender(), row)
        assertEquals(row.notes, again.notes)
    }

    @Test
    fun `resplit never flips interest only into principal and skips pre-start rows`() {
        val preStart = Payment(id = "p0", loanId = "L", name = "M", date = "2025-12-20", type = "Both",
            amount = 100.0, principal = 60.0, interest = 40.0, notes = "old", createdAt = 0)
        val raw = listOf(
            preStart,
            io("p1", "2026-01-31", 150.0, createdAt = 1),
            Payment(id = "p2", loanId = "L", name = "M", date = "2026-03-02", type = "Both", amount = 500.0, createdAt = 2),
        )
        for (t in listOf("Simple Interest", "Reducing Balance", "Compound Interest")) {
            val out = InterestPolicy.resplitBorrowerPayments(lender(t), raw)
            assertEquals(preStart, out.single { it.id == "p0" })
            val p1 = out.single { it.id == "p1" }
            assertEquals("Interest Only", p1.type)
            assertEquals(0.0, p1.principal, 0.0)
            assertEquals(150.0, p1.interest, 0.0)
            // A back-dated insert before p1 still leaves p1 interest-only.
            val backDated = Payment(id = "p3", loanId = "L", name = "M", date = "2026-01-10", type = "Both", amount = 2_000.0, createdAt = 3)
            val out2 = InterestPolicy.resplitBorrowerPayments(lender(t), out + backDated)
            val p1b = out2.single { it.id == "p1" }
            assertEquals("Interest Only", p1b.type)
            assertEquals(0.0, p1b.principal, 0.0)
            assertEquals(preStart, out2.single { it.id == "p0" })
            // Idempotent.
            assertEquals(out2, InterestPolicy.resplitBorrowerPayments(lender(t), out2))
            // Debt side behaves the same.
            val dOut = InterestPolicy.resplitDebtPayments(debt(t), raw.map { it.toDebt() })
            assertEquals(preStart.toDebt(), dOut.single { it.id == "p0" })
            val d1 = dOut.single { it.id == "p1" }
            assertEquals("Interest Only", d1.type)
            assertEquals(0.0, d1.principal, 0.0)
            // Every row keeps the paise invariant.
            (out2 - preStart).forEach { r ->
                assertEquals(InterestEngine.rupeesToPaise(r.amount), InterestEngine.rupeesToPaise(r.interest) +
                    InterestEngine.rupeesToPaise(r.principal) + r.penaltyPaise + r.roundingPaise)
            }
        }
    }

    // ── Shared balance function ─────────────────────────────────────────────

    @Test
    fun `shared balance matches legacy formula for non-paise loans`() {
        val b = Borrower(id = "L", name = "M", amount = 50_000.0, rate = 24.0, date = "2026-01-01",
            intType = "Flat", paid = 12_000.0, paidPrincipal = 9_000.0, paidInterest = 3_000.0)
        val rows = listOf(
            Payment(id = "a", loanId = "L", name = "M", date = "2026-03-01", type = "Both", amount = 12_000.0,
                principal = 9_000.0, interest = 3_000.0, interestAllocationType = InterestPolicy.CURRENT_PERIOD_INTEREST,
                interestPeriodStartDate = "2026-01-01"),
        )
        val bal = InterestPolicy.borrowerBalance(b, rows, "2026-06-01")
        assertEquals(41_000.0, bal.principal, 0.0)
        assertEquals(InterestPolicy.borrowerBreakdown(b, rows, "2026-06-01").due, bal.interestDue, 0.0)
        // 0% hand loan keeps amount − paid.
        val hand = b.copy(rate = 0.0, paid = 12_500.0)
        assertEquals(37_500.0, InterestPolicy.borrowerBalance(hand, rows).principal, 0.0)
        assertEquals(0.0, InterestPolicy.borrowerBalance(hand, rows).interestDue, 0.0)
        val d = Debt(id = "D", name = "M", amount = 50_000.0, rate = 24.0, date = "2026-01-01",
            intType = "Flat", paid = 12_000.0, paidPrincipal = 9_000.0, paidInterest = 3_000.0)
        val dRows = rows.map { it.toDebt().copy(interestAllocationType = it.interestAllocationType, interestPeriodStartDate = it.interestPeriodStartDate) }
        val dBal = InterestPolicy.debtBalance(d, dRows, "2026-06-01")
        assertEquals(41_000.0, dBal.principal, 0.0)
        assertEquals(InterestPolicy.debtBreakdown(d, dRows, "2026-06-01").due, dBal.interestDue, 0.0)
    }

    @Test
    fun `shared balance matches replay for paise methods and ignores stale aggregates`() {
        val rows = listOf(
            Payment(id = "a", loanId = "L", name = "M", date = "2026-01-31", type = "Both", amount = 500.0),
            io("b", "2026-02-15", 50.0),
        )
        for (t in listOf("Simple Interest", "Reducing Balance", "Compound Interest")) {
            // Stale paidPrincipal (e.g. the old whole-amount bug) must not matter.
            val b = lender(t).copy(paid = 9_999.0, paidPrincipal = 9_999.0)
            val replay = InterestPolicy.paiseBalancesForBorrower(b, "2026-04-01", rows)
            val bal = InterestPolicy.borrowerBalance(b, rows, "2026-04-01")
            assertEquals(t, InterestEngine.paiseToRupees(replay.outstandingPrincipal), bal.principal, 0.0)
            assertEquals(t, InterestEngine.paiseToRupees(replay.interestDue), bal.interestDue, 0.0)
        }
    }

    @Test
    fun `defaulted with frozen interest uses the breakdown for interest`() {
        val b = lender().copy(status = "Defaulted", frozenInterest = 777.0)
        val bal = InterestPolicy.borrowerBalance(b, emptyList(), "2026-06-01")
        assertEquals(InterestPolicy.borrowerBreakdown(b, emptyList(), "2026-06-01").due, bal.interestDue, 0.0)
        assertEquals(777.0, bal.interestDue, 0.0)
    }
}
