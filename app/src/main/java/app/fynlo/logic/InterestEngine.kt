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

    /** v3.3.0 — like [label] but names the stored compound frequency. */
    fun label(storedType: String, compoundFrequency: String): String =
        if (paiseMethodOrNull(storedType) == PaiseMethod.COMPOUND) {
            "Compound Interest (${normalizeCompoundFrequency(compoundFrequency).lowercase()})"
        } else label(storedType)

    // ── v3.3.0 compound frequency ──────────────────────────────────────────
    const val COMPOUND_MONTHLY = "Monthly"
    const val COMPOUND_QUARTERLY = "Quarterly"
    const val COMPOUND_YEARLY = "Yearly"
    val COMPOUND_FREQUENCIES: List<String> = listOf(COMPOUND_MONTHLY, COMPOUND_QUARTERLY, COMPOUND_YEARLY)

    /** Unknown / blank / legacy values read as "Monthly" (pre-3.3.0 behaviour). */
    fun normalizeCompoundFrequency(value: String?): String = when (value?.trim()?.lowercase()) {
        "quarterly" -> COMPOUND_QUARTERLY
        "yearly", "annually", "annual" -> COMPOUND_YEARLY
        else -> COMPOUND_MONTHLY
    }

    /** Months per compounding step: Monthly 1, Quarterly 3, Yearly 12. */
    fun compoundMonthsFor(frequency: String?): Int = when (normalizeCompoundFrequency(frequency)) {
        COMPOUND_QUARTERLY -> 3
        COMPOUND_YEARLY -> 12
        else -> 1
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
        /** v3.3.0 — compounding step in months (1 / 3 / 12). Compound method only. */
        val compoundMonths: Int = 1,
        /** v3.3.0 — signed sum of payment rounding (+ small gain, − write-off). */
        val roundingPaise: Long = 0L,
    ) {
        val outstandingPaise: Long get() = outstandingPrincipalPaise + interestDuePaise
        val isCleared: Boolean
            get() = outstandingPrincipalPaise == 0L && interestDuePaise == 0L
    }

    /**
     * Ledger identity (v3.3.0, every payment, to the paisa):
     *   towardInterest + towardPrincipal + penaltyPaise + roundingPaise == payment
     * roundingPaise is signed: positive = small gain (overpay under ₹1),
     * negative = write-off (shortfall under ₹1). One rounding value per payment.
     */
    data class PaisePaymentSplit(
        val towardInterest: Long,
        val towardPrincipal: Long,
        val penaltyPaise: Long,
        val roundingPaise: Long = 0L,
        /** True when this payment settles the loan (interest + principal absorb exactly the due). */
        val closesLoan: Boolean = false,
    )

    data class PaiseBalances(
        val outstandingPrincipal: Long,
        val interestDue: Long,
    ) {
        val outstanding: Long get() = outstandingPrincipal + interestDue
    }

    private const val PAISE_DAY_DENOM = 10000L * 365L
    private const val PAISE_MONTH_DENOM = 12L * 10000L

    /** v3.3.0 — whole-rupee threshold for rounding write-off / rounding overpay. */
    const val ROUNDING_THRESHOLD_PAISE = 100L

    /** Nearest whole rupee, in paise (half-up: 50 paise rounds up). */
    fun roundToRupeePaise(paise: Long): Long {
        require(paise >= 0L) { "paise must be >= 0" }
        return ((paise + 50L) / 100L) * 100L
    }

    /** Nearest whole rupee (for display). */
    fun wholeRupees(paise: Long): Long = roundToRupeePaise(paise) / 100L

    /**
     * Whole-rupee quotes the UI shows (engine keeps exact paise).
     * - [totalDuePaise]: exact.
     * - [fullSettlementPaise]: total due rounded to nearest ₹ (paying it always closes the
     *   loan: the < ₹1 difference is the payment's signed roundingPaise).
     * - [interestOnlyPaise]: interest due rounded to nearest ₹ (a round-down leaves the
     *   paise owing and carrying forward; a round-up spills < ₹1 onto principal).
     */
    data class SettlementQuote(
        val totalDuePaise: Long,
        val interestDuePaise: Long,
        val outstandingPrincipalPaise: Long,
    ) {
        val totalDueRupees: Long get() = wholeRupees(totalDuePaise)
        val fullSettlementPaise: Long get() = roundToRupeePaise(totalDuePaise)
        val interestOnlyPaise: Long get() = roundToRupeePaise(interestDuePaise)
        val interestOnlyRupees: Long get() = wholeRupees(interestDuePaise)
    }

    fun settlementQuote(balances: PaiseBalances): SettlementQuote =
        SettlementQuote(balances.outstanding, balances.interestDue, balances.outstandingPrincipal)

    /** "₹1" for whole rupees, else "₹1.37" (used for penalty notes). */
    fun formatPenaltyRupees(paise: Long): String =
        if (paise % 100L == 0L) "₹${paise / 100L}" else formatPaiseRupees(paise)

    /** "₹1.37" — exact paise, 2 decimals (used in ledger notes). */
    fun formatPaiseRupees(paise: Long): String {
        val sign = if (paise < 0L) "-" else ""
        val abs = kotlin.math.abs(paise)
        return "$sign₹${abs / 100L}.${(abs % 100L).toString().padStart(2, '0')}"
    }

    // ── v3.3.0 payment-date validation ─────────────────────────────────────
    enum class PaymentDateCheck { OK, BEFORE_START, FUTURE, INVALID }

    const val PAYMENT_BEFORE_START_MESSAGE =
        "Payment date can't be before the loan start date."

    /** BEFORE_START is a hard reject; FUTURE is allowed (UI asks to confirm). */
    fun checkPaymentDate(
        startDate: LocalDate,
        paymentDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): PaymentDateCheck = when {
        paymentDate.isBefore(startDate) -> PaymentDateCheck.BEFORE_START
        paymentDate.isAfter(today) -> PaymentDateCheck.FUTURE
        else -> PaymentDateCheck.OK
    }

    fun checkPaymentDate(
        startDate: String,
        paymentDate: String,
        today: LocalDate = LocalDate.now(),
    ): PaymentDateCheck {
        val s = runCatching { LocalDate.parse(startDate, formatter) }.getOrNull()
        val p = runCatching { LocalDate.parse(paymentDate, formatter) }.getOrNull()
            ?: return PaymentDateCheck.INVALID
        if (s == null) return if (p.isAfter(today)) PaymentDateCheck.FUTURE else PaymentDateCheck.OK
        return checkPaymentDate(s, p, today)
    }

    fun isFutureDate(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean = date.isAfter(today)

    fun isFutureDate(date: String, today: LocalDate = LocalDate.now()): Boolean =
        runCatching { LocalDate.parse(date, formatter) }.getOrNull()?.isAfter(today) ?: false

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
        compoundMonths: Int = 1,
    ): PaiseLoanState {
        require(principalPaise >= 0L) { "principalPaise must be >= 0" }
        require(annualRateBps >= 0) { "annualRateBps must be >= 0" }
        require(compoundMonths in setOf(1, 3, 12)) { "compoundMonths must be 1, 3 or 12" }
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
            compoundMonths = compoundMonths,
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
     * Interest due first, then principal (outstanding never negative).
     * v3.3.0 settle / rounding rule (exact = interestDue + principal, roundedTotal =
     * exact rounded to nearest ₹):
     *  - |paid − exact| < 100 → closes; interest + principal absorb exact;
     *    rounding = paid − exact (signed); no penalty.
     *  - paid − exact ≥ 100 → closes; interest + principal absorb exact;
     *    penalty = paid − roundedTotal; rounding = roundedTotal − exact.
     *    (This single branch covers both "paid ≥ roundedTotal + 100" and the gap
     *    exact + 100 ≤ paid < roundedTotal + 100; in the gap the penalty is
     *    always ≥ 51 paise because roundedTotal ≤ exact + 49.)
     *  - paid ≤ exact − 100 → normal partial payment; no rounding.
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
        val remaining = paymentPaise - towardInterest
        val towardPrincipal = minOf(remaining, outstandingPrincipal)
        if (paymentPaise == 0L) return PaisePaymentSplit(0L, 0L, 0L)
        val exact = interestDue + outstandingPrincipal
        val diff = paymentPaise - exact
        return when {
            diff <= -ROUNDING_THRESHOLD_PAISE ->
                PaisePaymentSplit(towardInterest, towardPrincipal, 0L)
            diff < ROUNDING_THRESHOLD_PAISE ->
                PaisePaymentSplit(interestDue, outstandingPrincipal, 0L, roundingPaise = diff, closesLoan = true)
            else -> {
                val roundedTotal = roundToRupeePaise(exact)
                PaisePaymentSplit(
                    towardInterest = interestDue,
                    towardPrincipal = outstandingPrincipal,
                    penaltyPaise = paymentPaise - roundedTotal,
                    roundingPaise = roundedTotal - exact,
                    closesLoan = true,
                )
            }
        }
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
            roundingPaise = state.roundingPaise + split.roundingPaise,
        )
        return next to split
    }

    /**
     * Replay dated payments (amount paise), then accrue to [asOf].
     * Day-count uses ChronoUnit between lastAccrual and each event — payment day
     * is not counted (same-day open + pay → ₹0 interest).
     * Payments after [asOf] are ignored. Same-date payments keep list order.
     */
    fun replayPaiseTo(
        state: PaiseLoanState,
        payments: List<Pair<LocalDate, Long>>,
        asOf: LocalDate,
    ): PaiseLoanState {
        var s = state
        val ordered = payments.filter { (date, amount) ->
            !date.isAfter(asOf) && amount >= 0L
        }
        for ((date, payPaise) in ordered) {
            s = accruePaiseTo(s, date)
            if (payPaise > 0L) {
                s = applyPaymentPaise(s, payPaise).first
            }
        }
        return accruePaiseTo(s, asOf)
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
        val n = s.compoundMonths.coerceAtLeast(1)
        // v3.3.0: every compounding date is start.plusMonths(k*n) — never chained —
        // so month-end starts don't drift (31 Jan quarterly → 30 Apr, 31 Jul).
        var k = nextCompoundIndexPaise(s.startDate, s.lastAccrualDate, n)
        var anniversary = compoundDatePaise(s.startDate, k, n)
        while (!anniversary.isAfter(asOf)) {
            if (s.interestDuePaise > 0L) {
                s = s.copy(
                    outstandingPrincipalPaise = s.outstandingPrincipalPaise + s.interestDuePaise,
                    interestDuePaise = 0L,
                )
            }
            if (s.outstandingPrincipalPaise > 0L && s.annualRateBps > 0) {
                val num = s.outstandingPrincipalPaise * s.annualRateBps.toLong() * n + s.compoundRemainder
                s = s.copy(
                    interestDuePaise = s.interestDuePaise + num / PAISE_MONTH_DENOM,
                    compoundRemainder = num % PAISE_MONTH_DENOM,
                )
            }
            s = s.copy(lastAccrualDate = anniversary)
            k += 1
            anniversary = compoundDatePaise(s.startDate, k, n)
        }
        // Mid-period stub: daily simple interest on outstanding principal until asOf
        // (no capitalization until the next compounding date). Full-period paths unchanged.
        if (s.lastAccrualDate < asOf) {
            s = accruePaiseReducing(s, asOf)
        }
        return s
    }

    /** First monthly anniversary of [start] strictly after [after]. */
    internal fun nextMonthAnniversaryPaise(start: LocalDate, after: LocalDate): LocalDate =
        nextCompoundDatePaise(start, after, 1)

    /** k-th compounding date = start.plusMonths(k * months) (computed from start, not chained). */
    fun compoundDatePaise(start: LocalDate, k: Int, months: Int): LocalDate =
        start.plusMonths(k.toLong() * months.toLong())

    /** First compounding date of [start] strictly after [after]. */
    fun nextCompoundDatePaise(start: LocalDate, after: LocalDate, months: Int): LocalDate =
        compoundDatePaise(start, nextCompoundIndexPaise(start, after, months), months)

    /** Smallest k >= 1 with start.plusMonths(k*months) strictly after [after]. */
    private fun nextCompoundIndexPaise(start: LocalDate, after: LocalDate, months: Int): Int {
        val m = months.coerceAtLeast(1)
        // Jump close to the answer, then settle (plusMonths clamps month-end days).
        val approx = (ChronoUnit.MONTHS.between(start, after) / m).toInt().coerceAtLeast(1)
        var k = (approx - 1).coerceAtLeast(1)
        while (!compoundDatePaise(start, k, m).isAfter(after)) k++
        return k
    }
}

