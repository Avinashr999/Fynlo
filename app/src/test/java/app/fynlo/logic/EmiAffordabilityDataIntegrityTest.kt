package app.fynlo.logic

import app.fynlo.logic.EmiAffordability.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmiAffordabilityDataIntegrityTest {

    @Test
    fun comfortableAtTwentyPercent() {
        val a = EmiAffordability.assess(monthlyEmi = 10_000.0, netMonthlyIncome = 50_000.0)
        assertEquals(20.0, a.burdenPct, 0.01)
        assertEquals(Verdict.COMFORTABLE, a.verdict)
    }

    @Test
    fun comfortableEdgeJustBelow30() {
        val a = EmiAffordability.assess(2_999.0, 10_000.0)
        assertEquals(Verdict.COMFORTABLE, a.verdict)
    }

    @Test
    fun manageableAtThirtyPercent() {
        val a = EmiAffordability.assess(3_000.0, 10_000.0)
        assertEquals(Verdict.MANAGEABLE, a.verdict)
    }

    @Test
    fun stretchedAtFortyPercent() {
        val a = EmiAffordability.assess(4_000.0, 10_000.0)
        assertEquals(Verdict.STRETCHED, a.verdict)
    }

    @Test
    fun riskyAtFiftyFivePercent() {
        val a = EmiAffordability.assess(5_500.0, 10_000.0)
        assertEquals(Verdict.RISKY, a.verdict)
    }

    @Test
    fun riskyWhenEmiExceedsIncome() {
        val a = EmiAffordability.assess(15_000.0, 10_000.0)
        assertEquals(Verdict.RISKY, a.verdict)
        assertTrue(a.burdenPct > 100)
    }

    @Test
    fun zeroIncomeReturnsInvalidInput() {
        val a = EmiAffordability.assess(1_000.0, 0.0)
        assertEquals(Verdict.INVALID_INPUT, a.verdict)
    }

    @Test
    fun negativeIncomeReturnsInvalidInput() {
        val a = EmiAffordability.assess(1_000.0, -5_000.0)
        assertEquals(Verdict.INVALID_INPUT, a.verdict)
    }

    @Test
    fun negativeEmiReturnsInvalidInput() {
        // Nonsense input; defensive.
        val a = EmiAffordability.assess(-100.0, 10_000.0)
        assertEquals(Verdict.INVALID_INPUT, a.verdict)
    }

    @Test
    fun zeroEmiIsComfortable() {
        // No EMI = 0% burden → comfortable.
        val a = EmiAffordability.assess(0.0, 10_000.0)
        assertEquals(Verdict.COMFORTABLE, a.verdict)
        assertEquals(0.0, a.burdenPct, 0.001)
    }
}
