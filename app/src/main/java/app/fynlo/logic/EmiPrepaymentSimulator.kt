package app.fynlo.logic

import kotlin.math.pow

/**
 * 3.2.80 — EMI prepayment what-if simulator.
 *
 * Two prepayment modes against a Reducing-Balance EMI baseline:
 *
 *   - **MonthlyExtra**: pay (baseline EMI + extra) every month.
 *   - **LumpSum**: pay one extra amount on top of the regular EMI in
 *     a specific month (1-based, clamped to the baseline tenure).
 *
 * Returns a [Result] carrying:
 *   - `totalMonths` — how long the loan now takes (could be > baseline if
 *     extra ≤ 0, == baseline if no prepayment fits, or < baseline when
 *     prepayments accelerate payoff).
 *   - `totalInterest` — total interest paid over the new payoff term.
 *   - `monthsSaved` / `interestSaved` — diffs vs the baseline. Floored at
 *     0 so a negative-extra scenario (e.g. user types `-100`) doesn't
 *     report "negative savings" — the math is symmetric but the UI copy
 *     reads cleanly with non-negative deltas.
 *
 * Reducing-Balance only — Simple and Compound EMI shapes don't have a
 * meaningful "prepayment shortens the term" semantic (Simple Interest is
 * P × r × t with t fixed; Compound is total = P × (1+r)^t paid in equal
 * installments). Caller is expected to guard the calculator's mode before
 * surfacing prepayment.
 *
 * Pure-Kotlin, no Android deps; covered by `EmiPrepaymentSimulatorDataIntegrityTest`.
 */
object EmiPrepaymentSimulator {

    sealed class Mode {
        /** Pay `amount` extra every month on top of the baseline EMI. */
        data class MonthlyExtra(val amount: Double) : Mode()
        /** Pay `amount` extra in `month` (1-based) on top of the baseline EMI. */
        data class LumpSum(val amount: Double, val month: Int) : Mode()
    }

    data class Result(
        val totalMonths:   Int,
        val totalInterest: Double,
        val monthsSaved:   Int,
        val interestSaved: Double,
        val feasible:      Boolean,
    )

    private const val MAX_MONTHS = 12 * 50  // 50-year guard, matches DebtPayoffPlanner

    /**
     * Baseline EMI for a Reducing-Balance loan.
     * @param principal in currency units, must be > 0
     * @param annualRatePct annual interest, e.g. 8.5 for 8.5%
     * @param tenureMonths must be > 0
     * @return Pair(monthly EMI, total interest under baseline)
     */
    fun baseline(principal: Double, annualRatePct: Double, tenureMonths: Int): Pair<Double, Double> {
        require(principal > 0 && tenureMonths > 0) { "principal and tenure must be positive" }
        val rMonthly = annualRatePct / 100.0 / 12.0
        val emi = if (rMonthly == 0.0) principal / tenureMonths
                  else principal * rMonthly * (1 + rMonthly).pow(tenureMonths) /
                       ((1 + rMonthly).pow(tenureMonths) - 1)
        val totalInterest = emi * tenureMonths - principal
        return emi to totalInterest
    }

    fun simulate(
        principal: Double,
        annualRatePct: Double,
        tenureMonths: Int,
        mode: Mode,
    ): Result {
        if (principal <= 0 || tenureMonths <= 0) {
            return Result(0, 0.0, 0, 0.0, feasible = false)
        }
        val (baseEmi, baseInterest) = baseline(principal, annualRatePct, tenureMonths)
        val rMonthly = annualRatePct / 100.0 / 12.0
        var balance = principal
        var interestPaid = 0.0
        var month = 0
        while (balance > 0.01 && month < MAX_MONTHS) {
            month++
            val accrued = balance * rMonthly
            interestPaid += accrued
            balance += accrued
            // Regular EMI payment.
            val payment = minOf(baseEmi, balance)
            balance -= payment
            // Prepayment on top.
            val extra = when (mode) {
                is Mode.MonthlyExtra -> mode.amount.coerceAtLeast(0.0)
                is Mode.LumpSum      -> if (month == mode.month) mode.amount.coerceAtLeast(0.0) else 0.0
            }
            if (extra > 0.0 && balance > 0.0) {
                val applied = minOf(extra, balance)
                balance -= applied
            }
        }
        val feasible = month < MAX_MONTHS
        val monthsSaved = (tenureMonths - month).coerceAtLeast(0)
        val interestSaved = (baseInterest - interestPaid).coerceAtLeast(0.0)
        return Result(
            totalMonths   = month,
            totalInterest = interestPaid,
            monthsSaved   = monthsSaved,
            interestSaved = interestSaved,
            feasible      = feasible,
        )
    }
}
