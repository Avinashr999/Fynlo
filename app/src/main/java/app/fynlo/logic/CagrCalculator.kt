package app.fynlo.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * C14 #5 (3.2.82) — CAGR (Compound Annual Growth Rate) helper.
 *
 * Formula: `((end / start) ^ (1 / years)) - 1`.
 *
 * Used by the Invest tab to surface annualized returns per holding and
 * portfolio-wide. Pure-Kotlin; covered by `CagrCalculatorDataIntegrityTest`.
 *
 * XIRR (irregular-cashflow internal rate of return) is intentionally NOT
 * implemented here — it needs Newton-Raphson iteration with proper
 * convergence guards. Deferred to its own slice when we have the test
 * scaffolding for iterative numerical methods.
 */
object CagrCalculator {

    /** Sentinel returned when CAGR is undefined (zero start, negative
     *  values, no time elapsed, parsing failure). UI should render as
     *  "—" rather than a misleading number. */
    const val UNDEFINED: Double = Double.NaN

    /**
     * Per-investment CAGR.
     *
     * @param startValue invested principal (must be > 0)
     * @param endValue current market value (any sign; negative interpreted as loss)
     * @param startDateIso ISO `yyyy-MM-dd` (the investment date)
     * @param endDate defaults to today
     * @return annualized rate as a fraction (e.g. 0.12 = 12% CAGR), or
     *         [UNDEFINED] for ill-defined inputs.
     */
    fun calc(
        startValue: Double,
        endValue: Double,
        startDateIso: String,
        endDate: LocalDate = LocalDate.now(),
    ): Double {
        if (startValue <= 0) return UNDEFINED
        val start = runCatching {
            LocalDate.parse(startDateIso, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }.getOrNull() ?: return UNDEFINED
        val days = ChronoUnit.DAYS.between(start, endDate).coerceAtLeast(0)
        if (days < 1) return UNDEFINED
        val years = days / 365.0
        // (end/start)^(1/years) - 1
        // For end < 0 the math is ill-defined (negative^fraction → complex);
        // fall back to a simple linearised return so the UI shows
        // something monotonic (the rare loss-to-zero edge).
        val ratio = endValue / startValue
        if (ratio <= 0.0) {
            // Total loss or worse — show "-100% annualised" as the worst
            // case; mathematically a fractional power of 0 is 0 → return -1.
            return -1.0
        }
        return ratio.pow(1.0 / years) - 1.0
    }

    /**
     * Portfolio-wide CAGR.
     *
     * Strategy: aggregate all investments to a single (totalInvested,
     * totalCurrent) pair and use the weighted-average duration (months
     * each ₹1 was invested, divided by total ₹) as the time horizon.
     * Not as rigorous as true XIRR (which would weight by individual
     * cashflow timing) but closer to "how the portfolio actually grew"
     * than naive end-vs-start at a single date.
     *
     * Inputs: a list of (invested, currentValue, startDateIso) tuples.
     * Returns CAGR fraction or [UNDEFINED].
     */
    fun portfolio(
        holdings: List<Triple<Double, Double, String>>,
        endDate: LocalDate = LocalDate.now(),
    ): Double {
        if (holdings.isEmpty()) return UNDEFINED
        val totalInvested = holdings.sumOf { it.first }
        val totalCurrent  = holdings.sumOf { it.second }
        if (totalInvested <= 0) return UNDEFINED

        // Weighted-average years: each holding's age weighted by its
        // invested principal. Gives a single representative duration.
        var weightedDays = 0.0
        for ((invested, _, dateIso) in holdings) {
            if (invested <= 0) continue
            val start = runCatching {
                LocalDate.parse(dateIso, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }.getOrNull() ?: continue
            val days = ChronoUnit.DAYS.between(start, endDate).coerceAtLeast(0)
            weightedDays += days * invested
        }
        val avgDays = weightedDays / totalInvested
        if (avgDays < 1.0) return UNDEFINED
        val years = avgDays / 365.0
        val ratio = totalCurrent / totalInvested
        if (ratio <= 0.0) return -1.0
        return ratio.pow(1.0 / years) - 1.0
    }

    /** Render helper: "12.4%" or "—" for [UNDEFINED]. */
    fun format(rate: Double): String {
        if (rate.isNaN()) return "—"
        val pct = rate * 100.0
        return "${"%.1f".format(pct)}%"
    }
}
