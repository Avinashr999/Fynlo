package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object InterestPolicy {
    private val ledgerFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    const val OLD_PERIOD_INTEREST = "OLD_PERIOD_INTEREST"
    const val CURRENT_PERIOD_INTEREST = "CURRENT_PERIOD_INTEREST"
    const val ADVANCE_INTEREST = "ADVANCE_INTEREST"
    const val EXTRA_INTEREST = "EXTRA_INTEREST"
    const val PRINCIPAL_REPAYMENT = "PRINCIPAL_REPAYMENT"
    const val UNKNOWN_REVIEW = "UNKNOWN_REVIEW"

    data class InterestBreakdown(
        val accrued: Double,
        val paid: Double,
        val waived: Double,
        val due: Double,
        val paidAhead: Double,
        val totalInterestPaid: Double = paid,
        val oldPeriodInterest: Double = 0.0,
        val extraInterest: Double = 0.0,
        val unclearInterest: Double = 0.0,
    ) {
        val effectivePaid: Double get() = paid + waived
    }

    fun effectiveAsOf(
        dueDate: String,
        stopAfterDue: Boolean,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): String {
        if (!stopAfterDue || dueDate.isBlank()) return asOf
        return runCatching {
            val due = LocalDate.parse(dueDate, ledgerFormatter)
            val current = LocalDate.parse(asOf, ledgerFormatter)
            if (current.isAfter(due)) dueDate else asOf
        }.getOrDefault(asOf)
    }

    fun accruedForBorrower(
        borrower: Borrower,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): Double {
        if (borrower.status == "Defaulted" && borrower.frozenInterest > 0.0) {
            return borrower.frozenInterest
        }
        return InterestEngine.calcIntAccrued(
            borrower.amount,
            borrower.rate,
            borrower.date,
            borrower.intType,
            borrower.due,
            totalPaid = borrower.paidPrincipal,
            asOf = effectiveAsOf(borrower.due, borrower.stopInterestAfterDue, asOf),
        )
    }

    fun accruedForDebt(
        debt: Debt,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): Double = InterestEngine.calcIntAccrued(
        debt.amount,
        debt.rate,
        debt.date,
        debt.intType,
        debt.due,
        totalPaid = debt.paidPrincipal,
        asOf = effectiveAsOf(debt.due, debt.stopInterestAfterDue, asOf),
    )

    private fun earlierDate(first: String, second: String): String =
        runCatching {
            val a = LocalDate.parse(first, ledgerFormatter)
            val b = LocalDate.parse(second, ledgerFormatter)
            if (a.isAfter(b)) second else first
        }.getOrDefault(first)

    private fun parseLedgerDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value, ledgerFormatter) }.getOrNull()

    private fun laterDate(first: LocalDate, second: LocalDate): LocalDate =
        if (first.isAfter(second)) first else second

    private fun normalizedDueDateFor(startDate: String, dueDate: String): String {
        val start = parseLedgerDate(startDate) ?: return dueDate
        val due = parseLedgerDate(dueDate) ?: return dueDate
        return if (due.isAfter(start)) dueDate else ""
    }

    private fun previousMonthEnd(paymentDate: String): LocalDate? =
        parseLedgerDate(paymentDate)
            ?.withDayOfMonth(1)
            ?.minusDays(1)

    private fun previousMonthStart(paymentDate: String): LocalDate? =
        previousMonthEnd(paymentDate)?.withDayOfMonth(1)

    private fun settledOldInterestEndDate(
        allocationType: String,
        interestAmount: Double,
        paymentDate: String,
        periodEndDate: String,
    ): LocalDate? {
        if (interestAmount <= 0.01 || allocationType != OLD_PERIOD_INTEREST) return null
        return parseLedgerDate(periodEndDate) ?: previousMonthEnd(paymentDate)
    }

    fun borrowerCurrentInterestStartDate(
        borrower: Borrower,
        payments: List<Payment>,
    ): String {
        val loanStart = parseLedgerDate(borrower.date) ?: return borrower.date
        val lastSettledEnd = payments
            .asSequence()
            .filter { it.loanId == borrower.id }
            .mapNotNull {
                settledOldInterestEndDate(
                    allocationType = it.interestAllocationType,
                    interestAmount = paymentInterestAmount(it),
                    paymentDate = it.date,
                    periodEndDate = it.interestPeriodEndDate,
                )
            }
            .filter { !it.isBefore(loanStart) }
            .maxOrNull()
        return lastSettledEnd
            ?.plusDays(1)
            ?.let { laterDate(loanStart, it) }
            ?.format(ledgerFormatter)
            ?: borrower.date
    }

    fun debtCurrentInterestStartDate(
        debt: Debt,
        payments: List<DebtPayment>,
    ): String {
        val debtStart = parseLedgerDate(debt.date) ?: return debt.date
        val lastSettledEnd = payments
            .asSequence()
            .filter { it.debtId == debt.id }
            .mapNotNull {
                settledOldInterestEndDate(
                    allocationType = it.interestAllocationType,
                    interestAmount = debtPaymentInterestAmount(it),
                    paymentDate = it.date,
                    periodEndDate = it.interestPeriodEndDate,
                )
            }
            .filter { !it.isBefore(debtStart) }
            .maxOrNull()
        return lastSettledEnd
            ?.plusDays(1)
            ?.let { laterDate(debtStart, it) }
            ?.format(ledgerFormatter)
            ?: debt.date
    }

    private fun paymentAwareAccrued(
        principal: Double,
        rate: Double,
        startDate: String,
        accrualStartDate: String,
        interestType: String,
        dueDate: String,
        stopAfterDue: Boolean,
        principalPayments: List<Pair<String, Double>>,
        asOf: String,
    ): Double {
        if (principal <= 0.0 || rate == 0.0 || startDate.isBlank()) return 0.0
        val effectiveAsOf = effectiveAsOf(dueDate, stopAfterDue, asOf)
        val asOfDate = runCatching { LocalDate.parse(effectiveAsOf, ledgerFormatter) }.getOrNull() ?: return 0.0
        val loanStart = parseLedgerDate(startDate) ?: return 0.0
        val requestedStart = parseLedgerDate(accrualStartDate) ?: loanStart
        val periodStart = laterDate(loanStart, requestedStart)
        if (!periodStart.isBefore(asOfDate)) return 0.0
        val periodStartString = periodStart.format(ledgerFormatter)
        val dueDateForPeriod = normalizedDueDateFor(periodStartString, dueDate)
        var remainingPrincipal = principal
        var accrued = 0.0

        principalPayments
            .filter { (_, amount) -> amount > 0.0 }
            .mapNotNull { (date, amount) ->
                val paidOn = runCatching { LocalDate.parse(date, ledgerFormatter) }.getOrNull() ?: return@mapNotNull null
                if (paidOn.isAfter(asOfDate)) null else Triple(date, paidOn, amount)
            }
            .sortedBy { it.second }
            .forEach { (paidDate, _, rawAmount) ->
                val principalPortion = rawAmount.coerceAtMost(remainingPrincipal).coerceAtLeast(0.0)
                if (principalPortion > 0.0) {
                    val paidOn = parseLedgerDate(paidDate)
                    if (paidOn != null && paidOn.isAfter(periodStart)) {
                        accrued += InterestEngine.calcIntAccrued(
                            amount = principalPortion,
                            rate = rate,
                            loanDate = periodStartString,
                            intType = interestType,
                            dueDate = dueDateForPeriod,
                            totalPaid = 0.0,
                            asOf = earlierDate(paidDate, effectiveAsOf),
                        )
                    }
                    remainingPrincipal = (remainingPrincipal - principalPortion).coerceAtLeast(0.0)
                }
            }

        if (remainingPrincipal > 0.0) {
            accrued += InterestEngine.calcIntAccrued(
                amount = remainingPrincipal,
                rate = rate,
                loanDate = periodStartString,
                intType = interestType,
                dueDate = dueDateForPeriod,
                totalPaid = 0.0,
                asOf = effectiveAsOf,
            )
        }
        return accrued
    }

    fun borrowerInterestOutstanding(
        borrower: Borrower,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): Double =
        borrowerBreakdown(borrower, asOf).due

    fun debtInterestOutstanding(
        debt: Debt,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): Double =
        debtBreakdown(debt, asOf).due

    fun borrowerBreakdown(
        borrower: Borrower,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): InterestBreakdown {
        val accrued = accruedForBorrower(borrower, asOf)
        val currentPaid = borrower.paidInterest
        return InterestBreakdown(
            accrued = accrued,
            paid = currentPaid,
            waived = borrower.interestWaived,
            due = (accrued - currentPaid - borrower.interestWaived).coerceAtLeast(0.0),
            paidAhead = (currentPaid - accrued).coerceAtLeast(0.0),
            totalInterestPaid = borrower.paidInterest,
        )
    }

    fun borrowerBreakdown(
        borrower: Borrower,
        payments: List<Payment>,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): InterestBreakdown {
        val rows = payments.filter { it.loanId == borrower.id }
        val currentStartDate = borrowerCurrentInterestStartDate(borrower, payments)
        val accrued = if (borrower.status == "Defaulted" && borrower.frozenInterest > 0.0) {
            borrower.frozenInterest
        } else {
            paymentAwareAccrued(
                principal = borrower.amount,
                rate = borrower.rate,
                startDate = borrower.date,
                accrualStartDate = currentStartDate,
                interestType = borrower.intType,
                dueDate = borrower.due,
                stopAfterDue = borrower.stopInterestAfterDue,
                principalPayments = rows.map { it.date to it.principal },
                asOf = asOf,
            )
        }
        val currentPaid = rows
            .filter { isCurrentPeriodInterestPayment(it.interestAllocationType, paymentInterestAmount(it), it.interestPeriodStartDate, currentStartDate) }
            .sumOf { paymentInterestAmount(it) }
        val oldPaid = rows
            .filter { it.interestAllocationType == OLD_PERIOD_INTEREST }
            .sumOf { paymentInterestAmount(it) }
        val extraPaid = rows
            .filter { it.interestAllocationType == EXTRA_INTEREST }
            .sumOf { paymentInterestAmount(it) }
        val unclearPaid = rows
            .filter {
                val interest = paymentInterestAmount(it)
                isUnclearInterestPayment(it.interestAllocationType, interest) ||
                    isStaleCurrentPeriodInterest(it.interestAllocationType, interest, it.interestPeriodStartDate, currentStartDate)
            }
            .sumOf { paymentInterestAmount(it) }
        val totalPaid = rows.sumOf { paymentInterestAmount(it) }
        return InterestBreakdown(
            accrued = accrued,
            paid = currentPaid,
            waived = borrower.interestWaived,
            due = (accrued - currentPaid - borrower.interestWaived).coerceAtLeast(0.0),
            paidAhead = (currentPaid - accrued).coerceAtLeast(0.0),
            totalInterestPaid = totalPaid,
            oldPeriodInterest = oldPaid,
            extraInterest = extraPaid,
            unclearInterest = unclearPaid,
        )
    }

    fun debtBreakdown(
        debt: Debt,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): InterestBreakdown {
        val accrued = accruedForDebt(debt, asOf)
        val currentPaid = debt.paidInterest
        return InterestBreakdown(
            accrued = accrued,
            paid = currentPaid,
            waived = debt.interestWaived,
            due = (accrued - currentPaid - debt.interestWaived).coerceAtLeast(0.0),
            paidAhead = (currentPaid - accrued).coerceAtLeast(0.0),
            totalInterestPaid = debt.paidInterest,
        )
    }

    fun debtBreakdown(
        debt: Debt,
        payments: List<DebtPayment>,
        asOf: String = LocalDate.now().format(ledgerFormatter),
    ): InterestBreakdown {
        val rows = payments.filter { it.debtId == debt.id }
        val currentStartDate = debtCurrentInterestStartDate(debt, payments)
        val accrued = paymentAwareAccrued(
            principal = debt.amount,
            rate = debt.rate,
            startDate = debt.date,
            accrualStartDate = currentStartDate,
            interestType = debt.intType,
            dueDate = debt.due,
            stopAfterDue = debt.stopInterestAfterDue,
            principalPayments = rows.map { it.date to it.principal },
            asOf = asOf,
        )
        val currentPaid = rows
            .filter { isCurrentPeriodInterestPayment(it.interestAllocationType, debtPaymentInterestAmount(it), it.interestPeriodStartDate, currentStartDate) }
            .sumOf { debtPaymentInterestAmount(it) }
        val oldPaid = rows
            .filter { it.interestAllocationType == OLD_PERIOD_INTEREST }
            .sumOf { debtPaymentInterestAmount(it) }
        val extraPaid = rows
            .filter { it.interestAllocationType == EXTRA_INTEREST }
            .sumOf { debtPaymentInterestAmount(it) }
        val unclearPaid = rows
            .filter {
                val interest = debtPaymentInterestAmount(it)
                isUnclearInterestPayment(it.interestAllocationType, interest) ||
                    isStaleCurrentPeriodInterest(it.interestAllocationType, interest, it.interestPeriodStartDate, currentStartDate)
            }
            .sumOf { debtPaymentInterestAmount(it) }
        val totalPaid = rows.sumOf { debtPaymentInterestAmount(it) }
        return InterestBreakdown(
            accrued = accrued,
            paid = currentPaid,
            waived = debt.interestWaived,
            due = (accrued - currentPaid - debt.interestWaived).coerceAtLeast(0.0),
            paidAhead = (currentPaid - accrued).coerceAtLeast(0.0),
            totalInterestPaid = totalPaid,
            oldPeriodInterest = oldPaid,
            extraInterest = extraPaid,
            unclearInterest = unclearPaid,
        )
    }

    fun paymentInterestAmount(payment: Payment): Double = when {
        payment.type.equals("Interest Only", ignoreCase = true) && payment.interest == 0.0 -> payment.amount
        else -> payment.interest
    }

    fun debtPaymentInterestAmount(payment: DebtPayment): Double = when {
        payment.type.equals("Interest Only", ignoreCase = true) && payment.interest == 0.0 -> payment.amount
        else -> payment.interest
    }

    fun allocationFor(principal: Double, interest: Double, selectedInterestAllocation: String): String =
        if (interest <= 0.0) PRINCIPAL_REPAYMENT else selectedInterestAllocation

    fun periodStartFor(allocationType: String, currentStartDate: String): String = when (allocationType) {
        CURRENT_PERIOD_INTEREST, ADVANCE_INTEREST -> currentStartDate
        else -> ""
    }

    fun periodEndFor(allocationType: String, currentStartDate: String, paymentDate: String): String = when (allocationType) {
        OLD_PERIOD_INTEREST -> currentStartDate
        CURRENT_PERIOD_INTEREST -> paymentDate
        else -> ""
    }

    fun periodRangeFor(allocationType: String, currentStartDate: String, paymentDate: String): Pair<String, String> =
        when (allocationType) {
            OLD_PERIOD_INTEREST -> {
                val start = previousMonthStart(paymentDate)?.format(ledgerFormatter).orEmpty()
                val end = previousMonthEnd(paymentDate)?.format(ledgerFormatter).orEmpty()
                start to end
            }
            CURRENT_PERIOD_INTEREST -> currentStartDate to paymentDate
            ADVANCE_INTEREST -> currentStartDate to ""
            else -> "" to ""
        }

    fun isUnclearInterestPayment(allocationType: String, interestAmount: Double): Boolean =
        interestAmount > 0.01 && (allocationType.isBlank() || allocationType == UNKNOWN_REVIEW)

    fun isCurrentPeriodInterestPayment(
        allocationType: String,
        interestAmount: Double,
        periodStartDate: String,
        currentStartDate: String,
    ): Boolean =
        interestAmount > 0.01 &&
            allocationType in currentPeriodAllocations &&
            periodStartDate.isNotBlank() &&
            periodStartDate == currentStartDate

    fun isStaleCurrentPeriodInterest(
        allocationType: String,
        interestAmount: Double,
        periodStartDate: String,
        currentStartDate: String,
    ): Boolean =
        interestAmount > 0.01 &&
            allocationType in currentPeriodAllocations &&
            periodStartDate.isNotBlank() &&
            periodStartDate != currentStartDate

    private val currentPeriodAllocations = setOf(CURRENT_PERIOD_INTEREST, ADVANCE_INTEREST)

    // --- Temporary paise migration helpers (delegate to InterestEngine) ---

    fun usesPaiseMethod(intType: String): Boolean = InterestEngine.isPaiseMethod(intType)

    /**
     * Snapshot balances for a lean-eligible borrower at [asOf].
     * When [payments] is non-empty, replays each payment by date (required for
     * Reducing / Compound so interest uses outstanding after prior pays).
     * Empty [payments] keeps aggregate paid* netting (OK for Simple / no history).
     */
    fun paiseBalancesForBorrower(
        borrower: Borrower,
        asOf: String = LocalDate.now().format(ledgerFormatter),
        payments: List<Payment> = emptyList(),
    ): InterestEngine.PaiseBalances {
        val method = InterestEngine.paiseMethodOrNull(borrower.intType)
            ?: error("intType '${borrower.intType}' is not paise-eligible")
        val rows = payments.filter { it.loanId == borrower.id }
        return if (rows.isNotEmpty()) {
            paiseBalancesFromReplay(
                principalRupees = borrower.amount,
                ratePercent = borrower.rate,
                startDate = borrower.date,
                waivedInterestRupees = borrower.interestWaived,
                method = method,
                asOf = asOf,
                datedPaymentPaise = rows.map { it.date to InterestEngine.rupeesToPaise(it.amount) },
                compoundMonths = InterestEngine.compoundMonthsFor(borrower.compoundFrequency),
            )
        } else {
            paiseBalancesFromTerms(
                principalRupees = borrower.amount,
                ratePercent = borrower.rate,
                startDate = borrower.date,
                paidPrincipalRupees = borrower.paidPrincipal,
                paidInterestRupees = borrower.paidInterest,
                waivedInterestRupees = borrower.interestWaived,
                method = method,
                asOf = asOf,
                compoundMonths = InterestEngine.compoundMonthsFor(borrower.compoundFrequency),
            )
        }
    }

    fun paiseBalancesForDebt(
        debt: Debt,
        asOf: String = LocalDate.now().format(ledgerFormatter),
        payments: List<DebtPayment> = emptyList(),
    ): InterestEngine.PaiseBalances {
        val method = InterestEngine.paiseMethodOrNull(debt.intType)
            ?: error("intType '${debt.intType}' is not paise-eligible")
        val rows = payments.filter { it.debtId == debt.id }
        return if (rows.isNotEmpty()) {
            paiseBalancesFromReplay(
                principalRupees = debt.amount,
                ratePercent = debt.rate,
                startDate = debt.date,
                waivedInterestRupees = debt.interestWaived,
                method = method,
                asOf = asOf,
                datedPaymentPaise = rows.map { it.date to InterestEngine.rupeesToPaise(it.amount) },
                compoundMonths = InterestEngine.compoundMonthsFor(debt.compoundFrequency),
            )
        } else {
            paiseBalancesFromTerms(
                principalRupees = debt.amount,
                ratePercent = debt.rate,
                startDate = debt.date,
                paidPrincipalRupees = debt.paidPrincipal,
                paidInterestRupees = debt.paidInterest,
                waivedInterestRupees = debt.interestWaived,
                method = method,
                asOf = asOf,
                compoundMonths = InterestEngine.compoundMonthsFor(debt.compoundFrequency),
            )
        }
    }

    fun previewBorrowerPaymentPaise(
        borrower: Borrower,
        paymentPaise: Long,
        asOf: String = LocalDate.now().format(ledgerFormatter),
        payments: List<Payment> = emptyList(),
    ): InterestEngine.PaisePaymentSplit {
        val bal = paiseBalancesForBorrower(borrower, asOf, payments)
        return InterestEngine.allocatePaymentPaise(
            outstandingPrincipal = bal.outstandingPrincipal,
            interestDue = bal.interestDue,
            paymentPaise = paymentPaise,
        )
    }

    fun previewDebtPaymentPaise(
        debt: Debt,
        paymentPaise: Long,
        asOf: String = LocalDate.now().format(ledgerFormatter),
        payments: List<DebtPayment> = emptyList(),
    ): InterestEngine.PaisePaymentSplit {
        val bal = paiseBalancesForDebt(debt, asOf, payments)
        return InterestEngine.allocatePaymentPaise(
            outstandingPrincipal = bal.outstandingPrincipal,
            interestDue = bal.interestDue,
            paymentPaise = paymentPaise,
        )
    }

    private fun paiseBalancesFromTerms(
        principalRupees: Double,
        ratePercent: Double,
        startDate: String,
        paidPrincipalRupees: Double,
        paidInterestRupees: Double,
        waivedInterestRupees: Double,
        method: InterestEngine.PaiseMethod,
        asOf: String,
        compoundMonths: Int = 1,
    ): InterestEngine.PaiseBalances {
        val principalPaise = InterestEngine.rupeesToPaise(principalRupees)
        val start = LocalDate.parse(startDate, ledgerFormatter)
        val asOfDate = LocalDate.parse(asOf, ledgerFormatter)
        var state = InterestEngine.openPaiseLoan(
            principalPaise = principalPaise,
            annualRateBps = InterestEngine.ratePercentToBps(ratePercent),
            startDate = start,
            method = method,
            compoundMonths = compoundMonths,
        )
        state = InterestEngine.accruePaiseTo(state, asOfDate)
        // v3.3.0: start from the engine's principal so Compound keeps interest it
        // capitalised on compounding dates (Simple / Reducing: equals principalPaise).
        val outstandingPrincipal = (state.outstandingPrincipalPaise - InterestEngine.rupeesToPaise(paidPrincipalRupees))
            .coerceAtLeast(0L)
        val interestDue = (
            state.interestDuePaise -
                InterestEngine.rupeesToPaise(paidInterestRupees) -
                InterestEngine.rupeesToPaise(waivedInterestRupees)
            ).coerceAtLeast(0L)
        return InterestEngine.PaiseBalances(outstandingPrincipal, interestDue)
    }

    private fun paiseBalancesFromReplay(
        principalRupees: Double,
        ratePercent: Double,
        startDate: String,
        waivedInterestRupees: Double,
        method: InterestEngine.PaiseMethod,
        asOf: String,
        datedPaymentPaise: List<Pair<String, Long>>,
        compoundMonths: Int = 1,
    ): InterestEngine.PaiseBalances {
        val start = LocalDate.parse(startDate, ledgerFormatter)
        val asOfDate = LocalDate.parse(asOf, ledgerFormatter)
        val events = datedPaymentPaise.mapNotNull { (dateStr, amountPaise) ->
            val d = runCatching { LocalDate.parse(dateStr, ledgerFormatter) }.getOrNull() ?: return@mapNotNull null
            d to amountPaise
        }.sortedWith(compareBy({ it.first }))
        var state = InterestEngine.openPaiseLoan(
            principalPaise = InterestEngine.rupeesToPaise(principalRupees),
            annualRateBps = InterestEngine.ratePercentToBps(ratePercent),
            startDate = start,
            method = method,
            compoundMonths = compoundMonths,
        )
        state = InterestEngine.replayPaiseTo(state, events, asOfDate)
        val interestDue = (
            state.interestDuePaise - InterestEngine.rupeesToPaise(waivedInterestRupees)
            ).coerceAtLeast(0L)
        return InterestEngine.PaiseBalances(state.outstandingPrincipalPaise, interestDue)
    }

    /**
     * Align a posted Payment to the same paise preview Frontend shows.
     * principal + interest come from allocatePaymentPaise; excess stays on
     * [Payment.amount]. v3.3.0: settling within ₹1 (either side) closes the
     * loan with a signed [Payment.roundingPaise]; overpaying by ₹1+ closes it
     * with [Payment.penaltyPaise] = paid − rounded total. Invariant per row:
     * interest + principal + penaltyPaise + roundingPaise == amount (paise).
     * [priorPayments] must be existing rows only (exclude [payment] itself).
     */
    fun alignBorrowerPaymentToPaisePreview(
        borrower: Borrower,
        payment: Payment,
        priorPayments: List<Payment> = emptyList(),
        asOf: String = payment.date.ifBlank { LocalDate.now().format(ledgerFormatter) },
    ): Payment {
        if (!usesPaiseMethod(borrower.intType)) return payment
        val paymentPaise = InterestEngine.rupeesToPaise(payment.amount)
        val priors = priorPayments.filter { it.loanId == borrower.id && it.id != payment.id }
        val split = previewBorrowerPaymentPaise(borrower, paymentPaise, asOf, priors)
        val interest = InterestEngine.paiseToRupees(split.towardInterest)
        val principal = InterestEngine.paiseToRupees(split.towardPrincipal)
        val type = when {
            split.towardInterest > 0L && split.towardPrincipal > 0L -> "Both"
            split.towardInterest > 0L -> "Interest Only"
            split.towardPrincipal > 0L -> "Principal Only"
            else -> payment.type
        }
        return payment.copy(
            type = type,
            principal = principal,
            interest = interest,
            interestAllocationType = if (split.towardInterest > 0L) CURRENT_PERIOD_INTEREST else PRINCIPAL_REPAYMENT,
            notes = notesWithEngineTags(payment.notes, split),
            penaltyPaise = split.penaltyPaise,
            roundingPaise = split.roundingPaise,
        )
    }

    fun alignDebtPaymentToPaisePreview(
        debt: Debt,
        payment: DebtPayment,
        priorPayments: List<DebtPayment> = emptyList(),
        asOf: String = payment.date.ifBlank { LocalDate.now().format(ledgerFormatter) },
    ): DebtPayment {
        if (!usesPaiseMethod(debt.intType)) return payment
        val paymentPaise = InterestEngine.rupeesToPaise(payment.amount)
        val priors = priorPayments.filter { it.debtId == debt.id && it.id != payment.id }
        val split = previewDebtPaymentPaise(debt, paymentPaise, asOf, priors)
        val interest = InterestEngine.paiseToRupees(split.towardInterest)
        val principal = InterestEngine.paiseToRupees(split.towardPrincipal)
        val type = when {
            split.towardInterest > 0L && split.towardPrincipal > 0L -> "Both"
            split.towardInterest > 0L -> "Interest Only"
            split.towardPrincipal > 0L -> "Principal Only"
            else -> payment.type
        }
        return payment.copy(
            type = type,
            principal = principal,
            interest = interest,
            interestAllocationType = if (split.towardInterest > 0L) CURRENT_PERIOD_INTEREST else PRINCIPAL_REPAYMENT,
            notes = notesWithEngineTags(payment.notes, split),
            penaltyPaise = split.penaltyPaise,
            roundingPaise = split.roundingPaise,
        )
    }

    /**
     * Account penalty rupees derived from a posted row.
     * Round via integer paise so Double dust after paise→rupees never invents a penalty.
     * v3.3.0: an excess under ₹1 is rounding, not a penalty (returns 0).
     */
    fun khathaPenaltyRupees(amount: Double, principal: Double, interest: Double): Double {
        val penaltyPaise = (
            InterestEngine.rupeesToPaise(amount) -
                InterestEngine.rupeesToPaise(principal) -
                InterestEngine.rupeesToPaise(interest)
            ).coerceAtLeast(0L)
        return if (penaltyPaise < InterestEngine.ROUNDING_THRESHOLD_PAISE) 0.0
        else InterestEngine.paiseToRupees(penaltyPaise)
    }

    /**
     * v3.3.0 — penalty for display. Rows written by 3.3.0+ carry penaltyPaise /
     * roundingPaise; legacy rows (both 0) fall back to the derived value.
     */
    fun penaltyPaiseOf(payment: Payment): Long =
        if (payment.penaltyPaise != 0L || payment.roundingPaise != 0L) payment.penaltyPaise
        else InterestEngine.rupeesToPaise(khathaPenaltyRupees(payment.amount, payment.principal, payment.interest))

    fun penaltyPaiseOf(payment: DebtPayment): Long =
        if (payment.penaltyPaise != 0L || payment.roundingPaise != 0L) payment.penaltyPaise
        else InterestEngine.rupeesToPaise(khathaPenaltyRupees(payment.amount, payment.principal, payment.interest))

    // ── v3.3.0 ledger note tags (engine-written; re-derived on every re-split) ──

    const val PENALTY_NOTE_PREFIX = "Penalty on this account"
    const val ROUNDING_WRITE_OFF_NOTE_PREFIX = "Rounding write-off"
    const val ROUNDING_OVERPAY_NOTE_PREFIX = "Rounding adjustment"
    private val engineNotePrefixes = listOf(PENALTY_NOTE_PREFIX, ROUNDING_WRITE_OFF_NOTE_PREFIX, ROUNDING_OVERPAY_NOTE_PREFIX)

    /** User notes with previous engine tags removed, then current tags appended. */
    fun notesWithEngineTags(notes: String, split: InterestEngine.PaisePaymentSplit): String {
        val userLines = notes.lines().filterNot { line -> engineNotePrefixes.any { line.trimStart().startsWith(it) } }
        val tags = buildList {
            if (split.penaltyPaise > 0L) add("$PENALTY_NOTE_PREFIX ${InterestEngine.formatPenaltyRupees(split.penaltyPaise)}")
            if (split.roundingPaise > 0L) {
                add("$ROUNDING_OVERPAY_NOTE_PREFIX +${InterestEngine.formatPaiseRupees(split.roundingPaise)} (rounding, not a penalty)")
            }
            if (split.roundingPaise < 0L) {
                add("$ROUNDING_WRITE_OFF_NOTE_PREFIX ${InterestEngine.formatPaiseRupees(-split.roundingPaise)} (settled within ₹1, loan closed)")
            }
        }
        return (userLines + tags).joinToString("\n").trim('\n')
    }

    // ── v3.3.0 whole-rupee settlement quotes (UI helpers) ──

    fun settlementQuoteForBorrower(
        borrower: Borrower,
        asOf: String = LocalDate.now().format(ledgerFormatter),
        payments: List<Payment> = emptyList(),
    ): InterestEngine.SettlementQuote =
        InterestEngine.settlementQuote(paiseBalancesForBorrower(borrower, asOf, payments))

    fun settlementQuoteForDebt(
        debt: Debt,
        asOf: String = LocalDate.now().format(ledgerFormatter),
        payments: List<DebtPayment> = emptyList(),
    ): InterestEngine.SettlementQuote =
        InterestEngine.settlementQuote(paiseBalancesForDebt(debt, asOf, payments))

    // ── v3.3.0 payment-date validation ──

    /** Null when OK (future dates are allowed — UI confirms); otherwise a message the UI can show. */
    fun paymentDateError(startDate: String, paymentDate: String): String? =
        when (InterestEngine.checkPaymentDate(startDate, paymentDate)) {
            InterestEngine.PaymentDateCheck.BEFORE_START -> InterestEngine.PAYMENT_BEFORE_START_MESSAGE
            InterestEngine.PaymentDateCheck.INVALID -> "Payment date is not a valid date."
            else -> null
        }

    fun isFuturePaymentDate(paymentDate: String): Boolean = InterestEngine.isFutureDate(paymentDate)

    // ── v3.3.0 re-split after delete / undo / back-dated insert ──

    private fun <T> replayOrder(rows: List<T>, date: (T) -> String, createdAt: (T) -> Long, id: (T) -> String): List<T> =
        rows.sortedWith(compareBy<T>({ date(it) }, { createdAt(it) }, { id(it) }))

    /**
     * Re-aligns every payment of [borrower] in replay order (date, createdAt, id)
     * so each row's principal / interest / penalty / rounding tags match the
     * engine after earlier rows were deleted, undone or back-dated.
     * Returns rows in replay order. Non-paise types are returned unchanged.
     */
    fun resplitBorrowerPayments(borrower: Borrower, payments: List<Payment>): List<Payment> {
        val rows = replayOrder(payments.filter { it.loanId == borrower.id }, { it.date }, { it.createdAt }, { it.id })
        if (!usesPaiseMethod(borrower.intType)) return rows
        // Rows are the only truth here: never let stale paid* aggregates leak in
        // through the no-prior-rows (terms) path for the first row.
        val base = borrower.copy(paid = 0.0, paidPrincipal = 0.0, paidInterest = 0.0)
        val done = mutableListOf<Payment>()
        for (row in rows) {
            done += alignBorrowerPaymentToPaisePreview(base, row, priorPayments = done.toList(), asOf = row.date)
        }
        return done
    }

    fun resplitDebtPayments(debt: Debt, payments: List<DebtPayment>): List<DebtPayment> {
        val rows = replayOrder(payments.filter { it.debtId == debt.id }, { it.date }, { it.createdAt }, { it.id })
        if (!usesPaiseMethod(debt.intType)) return rows
        val base = debt.copy(paid = 0.0, paidPrincipal = 0.0, paidInterest = 0.0)
        val done = mutableListOf<DebtPayment>()
        for (row in rows) {
            done += alignDebtPaymentToPaisePreview(base, row, priorPayments = done.toList(), asOf = row.date)
        }
        return done
    }

}
