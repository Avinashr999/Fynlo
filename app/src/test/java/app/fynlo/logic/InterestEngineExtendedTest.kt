package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InterestEngineExtendedTest {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun today(): String = LocalDate.now().format(fmt)
    private fun daysAgo(n: Long): String = LocalDate.now().minusDays(n).format(fmt)
    private fun daysFromNow(n: Long): String = LocalDate.now().plusDays(n).format(fmt)

    // ── Simple Interest ──────────────────────────────────────────────────

    @Test
    fun `SI - basic 10 percent on 10000 for 365 days`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 10.0,
            loanDate = daysAgo(365), intType = "Simple Interest",
            dueDate = "", totalPaid = 0.0, asOf = today()
        )
        assertEquals(1000.0, interest, 1.0)
    }

    @Test
    fun `SI - zero rate returns zero`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 50000.0, rate = 0.0,
            loanDate = daysAgo(365), intType = "Simple Interest"
        )
        assertEquals(0.0, interest, 0.0)
    }

    @Test
    fun `SI - zero principal returns zero`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 0.0, rate = 12.0,
            loanDate = daysAgo(365), intType = "Simple Interest"
        )
        assertEquals(0.0, interest, 0.0)
    }

    @Test
    fun `SI - empty loan date returns zero`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 10.0,
            loanDate = "", intType = "Simple Interest"
        )
        assertEquals(0.0, interest, 0.0)
    }

    @Test
    fun `SI - future loan date returns zero`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 10.0,
            loanDate = daysFromNow(30), intType = "Simple Interest",
            asOf = today()
        )
        assertEquals(0.0, interest, 0.0)
    }

    @Test
    fun `SI - partial year 182 days`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 12.0,
            loanDate = daysAgo(182), intType = "Simple Interest",
            asOf = today()
        )
        // 10000 * 0.12 * (182/365) ≈ 598
        val expected = 10000.0 * 0.12 * (182.0 / 365.0)
        assertEquals(expected, interest, 1.0)
    }

    // ── Compound Interest ────────────────────────────────────────────────

    @Test
    fun `CI - 10 percent on 10000 for exactly 1 year`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 10.0,
            loanDate = daysAgo(365), intType = "Compound Interest",
            asOf = today()
        )
        assertEquals(1000.0, interest, 1.0)
    }

    @Test
    fun `CI - 10 percent on 10000 for 2 years compounds`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 10.0,
            loanDate = daysAgo(730), intType = "Compound Interest",
            asOf = today()
        )
        // Year 1: 10000 + 1000 = 11000. Year 2: 11000 + 1100 = 12100. Interest = 2100
        assertEquals(2100.0, interest, 1.0)
    }

    // ── Reducing Balance ─────────────────────────────────────────────────

    @Test
    fun `RB - payments reduce principal for interest calculation`() {
        val withoutPayment = InterestEngine.calcIntAccrued(
            amount = 100000.0, rate = 12.0,
            loanDate = daysAgo(365), intType = "Reducing Balance",
            totalPaid = 0.0, asOf = today()
        )
        val withPayment = InterestEngine.calcIntAccrued(
            amount = 100000.0, rate = 12.0,
            loanDate = daysAgo(365), intType = "Reducing Balance",
            totalPaid = 50000.0, asOf = today()
        )
        assertTrue("Payment should reduce interest", withPayment < withoutPayment)
    }

    @Test
    fun `RB - fully paid principal returns zero interest`() {
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 12.0,
            loanDate = daysAgo(365), intType = "Reducing Balance",
            totalPaid = 10000.0, asOf = today()
        )
        assertEquals(0.0, interest, 0.0)
    }

    // ── Outstanding Calculation ──────────────────────────────────────────

    @Test
    fun `outstanding - basic principal plus interest minus payments`() {
        val outstanding = InterestEngine.calcOutstanding(
            principal = 10000.0,
            accruedInterest = 1000.0,
            paidPrincipal = 3000.0,
            paidInterest = 500.0
        )
        // (10000 - 3000) + (1000 - 500) = 7500
        assertEquals(7500.0, outstanding, 0.01)
    }

    @Test
    fun `outstanding - overpaid principal clamps to zero`() {
        val outstanding = InterestEngine.calcOutstanding(
            principal = 10000.0,
            accruedInterest = 500.0,
            paidPrincipal = 12000.0,
            paidInterest = 0.0
        )
        // (10000-12000).coerceAtLeast(0) + (500-0) = 0 + 500 = 500
        assertEquals(500.0, outstanding, 0.01)
    }

    @Test
    fun `outstanding - fully paid returns zero`() {
        val outstanding = InterestEngine.calcOutstanding(
            principal = 10000.0,
            accruedInterest = 1000.0,
            paidPrincipal = 10000.0,
            paidInterest = 1000.0
        )
        assertEquals(0.0, outstanding, 0.01)
    }

    // ── Both (SI then CI) ────────────────────────────────────────────────

    @Test
    fun `Both - before due date only SI accrues`() {
        val loanDate = daysAgo(100)
        val dueDate = daysFromNow(100) // still before due
        val interest = InterestEngine.calcIntAccrued(
            amount = 10000.0, rate = 12.0,
            loanDate = loanDate, intType = "Both",
            dueDate = dueDate, asOf = today()
        )
        // "Both" calculates SI for full loanDate-to-dueDate period (200 days)
        // even if asOf is before dueDate, because daysOverdue <= 0
        val daysTodue = InterestEngine.daysBetween(loanDate, dueDate)
        val expectedSI = Math.round(10000.0 * 0.12 * (daysTodue.toDouble() / 365.0)).toDouble()
        assertEquals(expectedSI, interest, 1.0)
    }

    // ── daysBetween ──────────────────────────────────────────────────────

    @Test
    fun `daysBetween - same date returns zero`() {
        val days = InterestEngine.daysBetween("2024-01-01", "2024-01-01")
        assertEquals(0, days)
    }
}
