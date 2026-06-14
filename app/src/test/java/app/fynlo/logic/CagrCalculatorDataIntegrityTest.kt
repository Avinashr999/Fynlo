package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CagrCalculatorDataIntegrityTest {

    private val endDate = LocalDate.of(2026, 5, 28)

    @Test
    fun simpleDoubleOverOneYearIsApproximately100Percent() {
        // 1000 → 2000 over exactly 365 days = 100% CAGR
        val cagr = CagrCalculator.calc(1000.0, 2000.0, "2025-05-28", endDate)
        assertEquals(1.0, cagr, 0.01)
    }

    @Test
    fun doubleOverTwoYearsIsApproximately41Percent() {
        // 1000 → 2000 over 2 years = √2 - 1 ≈ 0.414
        val cagr = CagrCalculator.calc(1000.0, 2000.0, "2024-05-28", endDate)
        assertEquals(0.414, cagr, 0.01)
    }

    @Test
    fun noGrowthOverAnyDurationIsZero() {
        val cagr = CagrCalculator.calc(1000.0, 1000.0, "2024-01-01", endDate)
        assertEquals(0.0, cagr, 0.001)
    }

    @Test
    fun lossYieldsNegativeCagr() {
        // 1000 → 500 over 1 year ≈ -50%
        val cagr = CagrCalculator.calc(1000.0, 500.0, "2025-05-28", endDate)
        assertTrue("CAGR for a loss should be negative", cagr < 0)
        assertEquals(-0.5, cagr, 0.01)
    }

    @Test
    fun zeroStartReturnsUndefined() {
        assertTrue(CagrCalculator.calc(0.0, 1000.0, "2025-01-01", endDate).isNaN())
    }

    @Test
    fun negativeStartReturnsUndefined() {
        assertTrue(CagrCalculator.calc(-100.0, 1000.0, "2025-01-01", endDate).isNaN())
    }

    @Test
    fun unparseableStartDateReturnsUndefined() {
        assertTrue(CagrCalculator.calc(1000.0, 2000.0, "not-a-date", endDate).isNaN())
        assertTrue(CagrCalculator.calc(1000.0, 2000.0, "", endDate).isNaN())
    }

    @Test
    fun zeroDurationReturnsUndefined() {
        // Same start and end day → can't annualise; return undefined.
        assertTrue(CagrCalculator.calc(1000.0, 2000.0, endDate.toString(), endDate).isNaN())
    }

    @Test
    fun totalLossReturnsMinusOneHundredPercent() {
        // 1000 → 0 over 1 year. ratio = 0 → can't take fractional power
        // sensibly; helper returns -1.0 (representable as "-100%").
        val cagr = CagrCalculator.calc(1000.0, 0.0, "2025-05-28", endDate)
        assertEquals(-1.0, cagr, 0.001)
    }

    // ── Portfolio ────────────────────────────────────────────────────

    @Test
    fun emptyPortfolioIsUndefined() {
        assertTrue(CagrCalculator.portfolio(emptyList(), endDate).isNaN())
    }

    @Test
    fun singleHoldingPortfolioMatchesPerHolding() {
        val p = CagrCalculator.portfolio(
            listOf(Triple(1000.0, 2000.0, "2025-05-28")), endDate)
        assertEquals(1.0, p, 0.01)
    }

    @Test
    fun portfolioWeightsByInvestedPrincipal() {
        // 1000 doubled over 2 years + 1000 unchanged over 2 years
        // → total: 2000 → 3000 over 2 years = 1.5^(1/2) - 1 ≈ 0.225
        val p = CagrCalculator.portfolio(
            listOf(
                Triple(1000.0, 2000.0, "2024-05-28"),
                Triple(1000.0, 1000.0, "2024-05-28"),
            ),
            endDate,
        )
        assertEquals(0.225, p, 0.02)
    }

    @Test
    fun portfolioWithMixedDurationsUsesWeightedAvg() {
        // 1000 invested 2 years ago + 1000 invested 1 year ago,
        // both currently 1500 each → total 2000 → 3000.
        // Weighted-avg duration ~1.5 years; CAGR ~= (1.5)^(1/1.5) - 1 ≈ 31%
        val p = CagrCalculator.portfolio(
            listOf(
                Triple(1000.0, 1500.0, "2024-05-28"),
                Triple(1000.0, 1500.0, "2025-05-28"),
            ),
            endDate,
        )
        assertTrue("Portfolio CAGR should be positive", p > 0)
        // Don't pin to an exact value — weighting math has free parameters.
        // Just assert it's in a reasonable range for a 50% portfolio gain
        // over ~1.5 years averaged.
        assertTrue("Expected 25–40% range, got ${p * 100}%", p in 0.20..0.40)
    }

    // ── format helper ────────────────────────────────────────────────

    @Test
    fun formatRendersPercentageWithOneDecimal() {
        assertEquals("12.4%", CagrCalculator.format(0.1235))
        assertEquals("-50.0%", CagrCalculator.format(-0.5))
        assertEquals("100.0%", CagrCalculator.format(1.0))
    }

    @Test
    fun formatRendersUndefinedAsDash() {
        assertEquals("—", CagrCalculator.format(CagrCalculator.UNDEFINED))
    }
}
