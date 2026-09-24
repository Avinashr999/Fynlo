package app.fynlo.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Interest + outstanding math for lending.
 *
 * Existing Double APIs stay for current call sites (including legacy "Both").
 * Integer-paise ledger helpers below are a temporary migration surface for lean v1
 * fixtures (monthly compound, khatha penalty) — not a parallel money engine.
 */
object InterestEngine {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * C12 (3.2.25) — display-label translator for stored interest-type codes.
     * The audit's fix #9 calls for renaming "Both" to "SI + CI" everywhere
     * the user sees it. The stored value stays "Both" (database rows + the
     * `when (intType)` branch on line ~73 below depend on it; migrating
     * the stored value would require a schema migration and breaks the
     * engine). So all UI display sites should route through this helper:
     *
     *   Text("Type: ${InterestEngine.label(debt.intType)}")
     *
     * and the dropdown pickers can use it to render menu items while
     * still saving the raw value back to storage.
     */
    fun label(storedType: String): String = when (storedType) {
        "Both" -> "SI + CI"
        "Compound Interest" -> "Compound Interest (monthly)"
        else   -> storedType
    }

    fun daysBetween(start: String, end: String): Long {
        return try {
            val startDate = LocalDate.parse(start, formatter)
            val endDate = LocalDate.parse(end, formatter)
            ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Precision Anniversary-Step Interest Engine (v1.3.3)
     * Now accounts for partial payments reducing the principal.
     */
    fun calcIntAccrued(
        amount: Double,
        rate: Double,
        loanDate: String,
        intType: String,
        dueDate: String = "",
        totalPaid: Double = 0.0,
        asOf: String = LocalDate.now().format(formatter)
    ): Double {
        if (rate == 0.0 || amount == 0.0 || loanDate.isEmpty()) return 0.0
        val totalDays = daysBetween(loanDate, asOf)
        if (totalDays <= 0) return 0.0

        val rAnnual = rate / 100.0

        // For Reducing Balance (EMI), payments reduce the outstanding principal.
        // For all other types (SI, CI, Both), interest is on the original amount —
        // payments go toward interest first, then principal. Using (amount - totalPaid)
        // for SI would undercount interest when borrowers pay interest installments.
        val principalForInterest = if (intType == "Reducing Balance")
            (amount - totalPaid).coerceAtLeast(0.0)
        else
            amount

        if (principalForInterest <= 0.0) return 0.0

        return when (intType) {
            // Reducing Balance — EMI method on remaining principal
            "Reducing Balance" -> {
                val rMonthly = rAnnual / 12.0
                val months   = totalDays / 30
                if (rMonthly == 0.0) 0.0
                else if (months == 0L) principalForInterest * rAnnual * totalDays.toDouble() / 365.0
                else {
                    val n = months.toDouble()
                    val totalPayable = principalForInterest * rMonthly * Math.pow(1 + rMonthly, n) /
                        (Math.pow(1 + rMonthly, n) - 1) * n
                    Math.round(totalPayable - principalForInterest).toDouble()
                }
            }
            // Compound Interest on original principal
            "Compound Interest" -> {
                val fullYears      = totalDays / 365
                val remainingDays  = totalDays % 365
                var currentTotal   = principalForInterest
                repeat(fullYears.toInt()) { currentTotal += (currentTotal * rAnnual) }
                val siForPartialYear = (currentTotal * rAnnual * remainingDays.toDouble()) / 365.0
                currentTotal + siForPartialYear - principalForInterest
            }
            // Both — SI from loan date to due date, then CI from due date onwards
            "Both" -> {
                if (dueDate.isEmpty()) {
                    val tYears = totalDays.toDouble() / 365.0
                    principalForInterest * rAnnual * tYears
                } else {
                    val daysTodue   = daysBetween(loanDate, dueDate).coerceAtLeast(0)
                    val daysOverdue = daysBetween(dueDate, asOf).coerceAtLeast(0)
                    val siInterest  = principalForInterest * rAnnual * (daysTodue.toDouble() / 365.0)
                    if (daysOverdue <= 0) {
                        siInterest
                    } else {
                        val baseForCI = principalForInterest + siInterest
                        val fullYears = daysOverdue / 365
                        val remDays   = daysOverdue % 365
                        var ciTotal   = baseForCI
                        repeat(fullYears.toInt()) { ciTotal += ciTotal * rAnnual }
                        ciTotal += ciTotal * rAnnual * (remDays.toDouble() / 365.0)
                        siInterest + (ciTotal - baseForCI)
                    }
                }
            }
            // Simple Interest — always on original amount (payments → interest first)
            else -> {
                val tYears = totalDays.toDouble() / 365.0
                principalForInterest * rAnnual * tYears
            }
        }
    }

    /**
     * Total outstanding the borrower owes RIGHT NOW.
     *
     * Formula: (principal - paidPrincipal) + max(0, accruedInterest - paidInterest - waivedInterest)
     *
     * paidPrincipal  — only principal repayments (reduces the loan base)
     * paidInterest   — interest already collected (reduces interest outstanding)
     * accruedInterest — interest earned so far (calculated on original principal)
     *
     * Legacy overload (single totalPaid) treats all paid as reducing principal.
     */
    fun calcOutstanding(
        principal: Double,
        accruedInterest: Double,
        paidPrincipal: Double,
        paidInterest: Double = 0.0,
        waivedInterest: Double = 0.0
    ): Double {
        val principalOutstanding = (principal - paidPrincipal).coerceAtLeast(0.0)
        val interestOutstanding  = (accruedInterest - paidInterest - waivedInterest).coerceAtLeast(0.0)
        return principalOutstanding + interestOutstanding
    }

    // Legacy overload — used by old code paths that only have totalPaid
    fun calcOutstanding(principal: Double, accruedInterest: Double, totalPaid: Double): Double {
        return (principal + accruedInterest - totalPaid).coerceAtLeast(0.0)
    }

    /**
     * For "Both" type loans: returns Pair(siPortion, ciPortion)
     * so UI can display them separately.
     */
    fun calcBothPortions(
        amount: Double,
        rate: Double,
        loanDate: String,
        dueDate: String,
        totalPaid: Double = 0.0,
        asOf: String = java.time.LocalDate.now().format(formatter)
    ): Pair<Double, Double> {
        if (amount <= 0.0 || dueDate.isEmpty()) return Pair(0.0, 0.0)
        val rAnnual     = rate / 100.0
        val daysTodue   = daysBetween(loanDate, dueDate).coerceAtLeast(0)
        val daysOverdue = daysBetween(dueDate, asOf).coerceAtLeast(0)
        // Use original amount — payments go toward interest first in Both type
        val siPortion   = amount * rAnnual * (daysTodue.toDouble() / 365.0)
        if (daysOverdue <= 0) return Pair(Math.round(siPortion).toDouble(), 0.0)
        val baseForCI = amount + siPortion
        val fullYears = daysOverdue / 365
        val remDays   = daysOverdue % 365
        var ciTotal   = baseForCI
        repeat(fullYears.toInt()) { ciTotal += ciTotal * rAnnual }
        ciTotal += ciTotal * rAnnual * (remDays.toDouble() / 365.0)
        return Pair(Math.round(siPortion).toDouble(), Math.round(ciTotal - baseForCI).toDouble())
    }

    // -------------------------------------------------------------------------
    // Temporary migration: integer-paise ledger (lean v1 money rules).
    // Lives on InterestEngine — not a second money stack. Call sites bind later.
    // -------------------------------------------------------------------------

    /** annualRateBps: 1200 = 12%. Compound = monthly capitalize only. */
    enum class PaiseMethod { SIMPLE, COMPOUND, REDUCING }

    data class PaiseLoanState(
        val method: PaiseMethod,
        val annualRateBps: Int,
        val startDate: LocalDate,
        val originalPrincipalPaise: Long,
        val outstandingPrincipalPaise: Long,
        val interestDuePaise: Long,
        val dayRemainder: Long = 0L,
        val compoundRemainder: Long = 0L,
        val lastAccrualDate: LocalDate,
        val penaltyPaise: Long = 0L,
    ) {
        val outstandingPaise: Long get() = outstandingPrincipalPaise + interestDuePaise
        val isCleared: Boolean
            get() = outstandingPrincipalPaise == 0L && interestDuePaise == 0L
    }

    data class PaisePaymentSplit(
        val towardInterest: Long,
        val towardPrincipal: Long,
        val penaltyPaise: Long,
    )

    data class PaiseBalances(
        val outstandingPrincipal: Long,
        val interestDue: Long,
    ) {
        val outstanding: Long get() = outstandingPrincipal + interestDue
    }

    private const val PAISE_DAY_DENOM = 10000L * 365L
    private const val PAISE_MONTH_DENOM = 12L * 10000L

    fun rupeesToPaise(rupees: Double): Long = Math.round(rupees * 100.0)
    fun paiseToRupees(paise: Long): Double = paise / 100.0

    fun ratePercentToBps(ratePercent: Double): Int = Math.round(ratePercent * 100.0).toInt()

    /** True for Simple / Compound / Reducing (and short aliases). False for Both/legacy. */
    fun isPaiseMethod(intType: String): Boolean = paiseMethodOrNull(intType) != null

    fun paiseMethodOrNull(intType: String): PaiseMethod? = when (intType.trim().lowercase()) {
        "simple interest", "simple", "si" -> PaiseMethod.SIMPLE
        "compound interest", "compound", "ci" -> PaiseMethod.COMPOUND
        "reducing balance", "reducing", "rb" -> PaiseMethod.REDUCING
        else -> null
    }

    fun openPaiseLoan(
        principalPaise: Long,
        annualRateBps: Int,
        startDate: LocalDate,
        method: PaiseMethod,
    ): PaiseLoanState {
        require(principalPaise >= 0L) { "principalPaise must be >= 0" }
        require(annualRateBps >= 0) { "annualRateBps must be >= 0" }
        return PaiseLoanState(
            method = method,
            annualRateBps = annualRateBps,
            startDate = startDate,
            originalPrincipalPaise = principalPaise,
            outstandingPrincipalPaise = principalPaise,
            interestDuePaise = 0L,
            dayRemainder = 0L,
            compoundRemainder = 0L,
            lastAccrualDate = startDate,
            penaltyPaise = 0L,
        )
    }

    /** Accrue to [asOf]. Cleared loans do not accrue further. */
    fun accruePaiseTo(state: PaiseLoanState, asOf: LocalDate): PaiseLoanState {
        if (asOf <= state.lastAccrualDate) return state
        if (state.isCleared) return state.copy(lastAccrualDate = asOf)
        if (state.annualRateBps == 0) return state.copy(lastAccrualDate = asOf)
        return when (state.method) {
            PaiseMethod.SIMPLE -> accruePaiseSimple(state, asOf)
            PaiseMethod.REDUCING -> accruePaiseReducing(state, asOf)
            PaiseMethod.COMPOUND -> accruePaiseCompound(state, asOf)
        }
    }

    fun paiseBalances(state: PaiseLoanState): PaiseBalances =
        PaiseBalances(state.outstandingPrincipalPaise, state.interestDuePaise)

    /**
     * Interest due first, then principal. Excess → penalty_paise on same khatha
     * (outstanding never negative).
     */
    fun allocatePaymentPaise(state: PaiseLoanState, paymentPaise: Long): PaisePaymentSplit =
        allocatePaymentPaise(
            outstandingPrincipal = state.outstandingPrincipalPaise,
            interestDue = state.interestDuePaise,
            paymentPaise = paymentPaise,
        )

    fun allocatePaymentPaise(
        outstandingPrincipal: Long,
        interestDue: Long,
        paymentPaise: Long,
    ): PaisePaymentSplit {
        require(paymentPaise >= 0L) { "paymentPaise must be >= 0" }
        require(outstandingPrincipal >= 0L && interestDue >= 0L)
        val towardInterest = minOf(paymentPaise, interestDue)
        var remaining = paymentPaise - towardInterest
        val towardPrincipal = minOf(remaining, outstandingPrincipal)
        remaining -= towardPrincipal
        return PaisePaymentSplit(towardInterest, towardPrincipal, remaining)
    }

    fun applyPaymentPaise(
        state: PaiseLoanState,
        paymentPaise: Long,
    ): Pair<PaiseLoanState, PaisePaymentSplit> {
        val split = allocatePaymentPaise(state, paymentPaise)
        val next = state.copy(
            interestDuePaise = state.interestDuePaise - split.towardInterest,
            outstandingPrincipalPaise = state.outstandingPrincipalPaise - split.towardPrincipal,
            penaltyPaise = state.penaltyPaise + split.penaltyPaise,
        )
        return next to split
    }

    private fun accruePaiseSimple(state: PaiseLoanState, asOf: LocalDate): PaiseLoanState {
        val days = ChronoUnit.DAYS.between(state.lastAccrualDate, asOf)
        if (days <= 0L) return state
        val principal = state.originalPrincipalPaise
        if (principal <= 0L) return state.copy(lastAccrualDate = asOf)
        val num = principal * state.annualRateBps.toLong() * days + state.dayRemainder
        return state.copy(
            interestDuePaise = state.interestDuePaise + num / PAISE_DAY_DENOM,
            dayRemainder = num % PAISE_DAY_DENOM,
            lastAccrualDate = asOf,
        )
    }

    private fun accruePaiseReducing(state: PaiseLoanState, asOf: LocalDate): PaiseLoanState {
        val days = ChronoUnit.DAYS.between(state.lastAccrualDate, asOf)
        if (days <= 0L) return state
        val principal = state.outstandingPrincipalPaise
        if (principal <= 0L) return state.copy(lastAccrualDate = asOf)
        val num = principal * state.annualRateBps.toLong() * days + state.dayRemainder
        return state.copy(
            interestDuePaise = state.interestDuePaise + num / PAISE_DAY_DENOM,
            dayRemainder = num % PAISE_DAY_DENOM,
            lastAccrualDate = asOf,
        )
    }

    private fun accruePaiseCompound(state: PaiseLoanState, asOf: LocalDate): PaiseLoanState {
        var s = state
        var anniversary = nextMonthAnniversaryPaise(s.startDate, s.lastAccrualDate)
        while (!anniversary.isAfter(asOf)) {
            if (s.interestDuePaise > 0L) {
                s = s.copy(
                    outstandingPrincipalPaise = s.outstandingPrincipalPaise + s.interestDuePaise,
                    interestDuePaise = 0L,
                )
            }
            if (s.outstandingPrincipalPaise > 0L && s.annualRateBps > 0) {
                val num = s.outstandingPrincipalPaise * s.annualRateBps.toLong() + s.compoundRemainder
                s = s.copy(
                    interestDuePaise = s.interestDuePaise + num / PAISE_MONTH_DENOM,
                    compoundRemainder = num % PAISE_MONTH_DENOM,
                )
            }
            s = s.copy(lastAccrualDate = anniversary)
            anniversary = anniversary.plusMonths(1)
        }
        if (s.lastAccrualDate < asOf) {
            s = s.copy(lastAccrualDate = asOf)
        }
        return s
    }

    /** First monthly anniversary of [start] strictly after [after]. */
    internal fun nextMonthAnniversaryPaise(start: LocalDate, after: LocalDate): LocalDate {
        var ann = start.plusMonths(1)
        while (!ann.isAfter(after)) {
            ann = ann.plusMonths(1)
        }
        return ann
    }
}

