package app.fynlo.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * 3.2.83 — XIRR (Extended Internal Rate of Return) for irregular cashflows.
 *
 * Finds `r` such that  Σ amount_i × (1 + r)^(-days_i / 365) = 0
 * where `days_i` is the day-offset of cashflow `i` from the earliest
 * cashflow in the series.
 *
 * Sign convention follows Excel / Sheets `XIRR`:
 *   - **Negative** amount → outflow from your pocket (loan disbursed, debt taken on)
 *   - **Positive** amount → inflow to your pocket (repayment received, debt paid back)
 *
 * Returns the annualised rate as a fraction (0.12 = 12% XIRR), or
 * [UNDEFINED] when the input is malformed or the solver fails to converge.
 *
 * Algorithm: Newton-Raphson with damped step size and a 100-iteration
 * cap. Starts from 0.1 (10%) which is a reasonable initial guess for the
 * domain (consumer loans / personal lending). Converges in ~5-10 iters
 * for typical inputs; the 100 cap is paranoia.
 *
 * Pure-Kotlin, no Android deps; covered by `XirrCalculatorDataIntegrityTest`.
 */
object XirrCalculator {

    /** Sentinel — caller should render as "—". */
    const val UNDEFINED: Double = Double.NaN

    /** One cashflow event. Date is ISO `yyyy-MM-dd`. */
    data class Cashflow(val amount: Double, val date: String)

    private const val MAX_ITERATIONS = 100
    private const val TOLERANCE = 1e-7
    private const val INITIAL_GUESS = 0.1

    fun calc(cashflows: List<Cashflow>): Double {
        // Must have at least one positive and one negative cashflow,
        // otherwise no root exists in the standard XIRR sense.
        if (cashflows.size < 2) return UNDEFINED
        val hasPositive = cashflows.any { it.amount > 0 }
        val hasNegative = cashflows.any { it.amount < 0 }
        if (!hasPositive || !hasNegative) return UNDEFINED

        // Parse dates once; reject the whole series on any parse failure
        // rather than silently dropping rows (would yield a misleading
        // result that didn't reflect the input).
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val parsed: List<Pair<Double, LocalDate>> = cashflows.map {
            val d = runCatching { LocalDate.parse(it.date, fmt) }.getOrNull()
                ?: return UNDEFINED
            it.amount to d
        }
        val earliest = parsed.minOf { it.second }
        // Convert each cashflow to (amount, days_offset_from_earliest).
        val offsets: List<Pair<Double, Double>> = parsed.map { (amt, d) ->
            amt to ChronoUnit.DAYS.between(earliest, d).toDouble()
        }

        // Newton-Raphson loop.
        var r = INITIAL_GUESS
        repeat(MAX_ITERATIONS) {
            // (1 + r) must stay > 0 — otherwise fractional powers blow up.
            // Clamp r to avoid stepping into the impossible region.
            if (r <= -0.999_999) r = -0.999_999
            val f  = npv(offsets, r)
            val fp = dnpv(offsets, r)
            if (abs(fp) < 1e-12) return UNDEFINED  // derivative ~0 → can't divide
            val rNext = r - f / fp
            if (abs(rNext - r) < TOLERANCE) return rNext
            r = rNext
        }
        // Did not converge in MAX_ITERATIONS. Report undefined rather
        // than a stale partial answer.
        return UNDEFINED
    }

    /** NPV at rate `r`: Σ amount × (1 + r)^(-days/365). */
    private fun npv(offsets: List<Pair<Double, Double>>, r: Double): Double {
        val base = 1.0 + r
        return offsets.sumOf { (amt, days) ->
            amt * base.pow(-days / 365.0)
        }
    }

    /** dNPV/dr: Σ amount × (-days/365) × (1 + r)^(-days/365 - 1). */
    private fun dnpv(offsets: List<Pair<Double, Double>>, r: Double): Double {
        val base = 1.0 + r
        return offsets.sumOf { (amt, days) ->
            val n = -days / 365.0
            amt * n * base.pow(n - 1.0)
        }
    }

    /** Render helper: "12.4%" or "—" for [UNDEFINED]. */
    fun format(rate: Double): String {
        if (rate.isNaN()) return "—"
        return "${"%.1f".format(rate * 100.0)}%"
    }
}
