package app.fynlo.logic

/**
 * C22 (3.2.60) — Snowball / Avalanche debt-payoff planner.
 *
 * Simulates month-by-month repayment of multiple debts given a total
 * monthly budget. Two strategies:
 *
 *   - **SNOWBALL** — orders debts by smallest remaining balance first.
 *     The whole budget goes to the smallest debt until cleared, then
 *     rolls to the next. Behavioural win (quick early wins keep users
 *     motivated) but doesn't minimise interest cost.
 *
 *   - **AVALANCHE** — orders debts by highest interest rate first.
 *     Mathematically optimal — minimises total interest paid — but
 *     the first payoff can take longer if the highest-rate debt is
 *     also the largest balance.
 *
 * Pure-function, no Android deps. Driven by the existing Debt entity's
 * `amount`, `paid`, `rate` fields; no per-debt minimum-payment field is
 * required because the whole [monthlyBudget] is allocated across the
 * ordered list each month (matches how the "debt snowball method" is
 * commonly taught — pick one priority, throw everything at it).
 *
 * Interest accrues monthly at `rate / 12 / 100`. The planner doesn't try
 * to model compound timing within a month; close enough for a planning
 * surface. Infinite-loop guard caps simulation at 50 years.
 */
object DebtPayoffPlanner {

    enum class Strategy { SNOWBALL, AVALANCHE }

    /** Input shape — only the fields the planner actually reads. */
    data class DebtInput(
        val id: String,
        val name: String,
        val outstandingBalance: Double,  // amount + accrued - paid
        val annualRatePct: Double,        // 0.0 for zero-interest
    )

    /** Per-debt result inside a [StrategyResult]. */
    data class DebtPlan(
        val id: String,
        val name: String,
        val payoffMonth: Int,        // 1-indexed; -1 if not paid off within cap
        val totalInterestPaid: Double,
    )

    data class StrategyResult(
        val strategy: Strategy,
        val perDebt: List<DebtPlan>,           // in payoff order
        val totalMonths: Int,                  // -1 if not feasible
        val totalInterestPaid: Double,
        val feasible: Boolean,                 // false → monthlyBudget too small
    )

    private const val MAX_MONTHS = 12 * 50  // 50-year guard

    fun plan(
        debts: List<DebtInput>,
        monthlyBudget: Double,
        strategy: Strategy,
    ): StrategyResult {
        if (debts.isEmpty()) return StrategyResult(
            strategy = strategy, perDebt = emptyList(),
            totalMonths = 0, totalInterestPaid = 0.0, feasible = true,
        )

        // Working state — parallel arrays keyed by debt index for speed.
        val n = debts.size
        val balances = DoubleArray(n) { debts[it].outstandingBalance.coerceAtLeast(0.0) }
        val rates    = DoubleArray(n) { debts[it].annualRatePct.coerceAtLeast(0.0) }
        val interest = DoubleArray(n)
        val payoff   = IntArray(n) { -1 }

        // Order debts per strategy. Pre-compute the priority order once;
        // since Snowball/Avalanche use intrinsic properties (balance / rate)
        // the relative order between unpaid debts stays stable — only the
        // first-unpaid pointer moves forward as debts clear.
        val order: List<Int> = when (strategy) {
            // Smallest balance first; tie-break: highest rate (helps a tiny
            // 0% debt not pre-empt a small high-rate one, deterministic order).
            Strategy.SNOWBALL  -> (0 until n).sortedWith(
                compareBy<Int> { balances[it] }.thenByDescending { rates[it] }
            )
            // Highest rate first; tie-break: smallest balance (clear small
            // debts faster among same-rate ones).
            Strategy.AVALANCHE -> (0 until n).sortedWith(
                compareByDescending<Int> { rates[it] }.thenBy { balances[it] }
            )
        }

        // Feasibility precheck: if monthlyBudget can't even cover one
        // month's combined minimum-interest accrual, balances grow without
        // bound and we'd loop forever. Return early.
        val firstMonthInterest = (0 until n).sumOf { balances[it] * rates[it] / 12.0 / 100.0 }
        if (monthlyBudget <= firstMonthInterest && balances.sum() > 0.0) {
            return StrategyResult(
                strategy = strategy,
                perDebt = order.map { i -> DebtPlan(debts[i].id, debts[i].name, -1, 0.0) },
                totalMonths = -1,
                totalInterestPaid = 0.0,
                feasible = false,
            )
        }

        var month = 0
        while (month < MAX_MONTHS) {
            // Early-exit: every debt cleared.
            if (order.all { balances[it] <= 0.01 }) break

            month++

            // 1. Accrue monthly interest on each unpaid debt.
            for (i in 0 until n) {
                if (balances[i] > 0.0) {
                    val monthlyInt = balances[i] * rates[i] / 12.0 / 100.0
                    balances[i] += monthlyInt
                    interest[i] += monthlyInt
                }
            }

            // 2. Allocate the whole budget down the priority list, rolling
            //    surplus from cleared debts to the next priority.
            var budget = monthlyBudget
            for (i in order) {
                if (budget <= 0.0) break
                if (balances[i] <= 0.0) continue
                val pay = minOf(budget, balances[i])
                balances[i] -= pay
                budget      -= pay
                if (balances[i] <= 0.01 && payoff[i] < 0) {
                    payoff[i]  = month
                    balances[i] = 0.0
                }
            }
        }

        val perDebt = order.map { i ->
            DebtPlan(
                id = debts[i].id,
                name = debts[i].name,
                payoffMonth = payoff[i],
                totalInterestPaid = interest[i],
            )
        }
        val totalMonths = perDebt.maxOfOrNull { it.payoffMonth } ?: 0
        return StrategyResult(
            strategy          = strategy,
            perDebt           = perDebt,
            totalMonths       = totalMonths,
            totalInterestPaid = interest.sum(),
            feasible          = totalMonths > 0,
        )
    }
}
