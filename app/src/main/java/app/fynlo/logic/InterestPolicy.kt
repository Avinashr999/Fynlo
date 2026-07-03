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
        val accrued = accruedForBorrower(borrower, asOf)
        val rows = payments.filter { it.loanId == borrower.id }
        val currentPaid = rows
            .filter { isCurrentPeriodInterestPayment(it.interestAllocationType, paymentInterestAmount(it), it.interestPeriodStartDate, borrower.date) }
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
                    isStaleCurrentPeriodInterest(it.interestAllocationType, interest, it.interestPeriodStartDate, borrower.date)
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
        val accrued = accruedForDebt(debt, asOf)
        val rows = payments.filter { it.debtId == debt.id }
        val currentPaid = rows
            .filter { isCurrentPeriodInterestPayment(it.interestAllocationType, debtPaymentInterestAmount(it), it.interestPeriodStartDate, debt.date) }
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
                    isStaleCurrentPeriodInterest(it.interestAllocationType, interest, it.interestPeriodStartDate, debt.date)
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
}
