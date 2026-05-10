package app.fynlo.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object InterestEngine {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
                if (rMonthly == 0.0 || months == 0L) 0.0
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
                Math.round(currentTotal + siForPartialYear - principalForInterest).toDouble()
            }
            // Both — SI from loan date to due date, then CI from due date onwards
            "Both" -> {
                if (dueDate.isEmpty()) {
                    val tYears = totalDays.toDouble() / 365.0
                    Math.round(principalForInterest * rAnnual * tYears).toDouble()
                } else {
                    val daysTodue   = daysBetween(loanDate, dueDate).coerceAtLeast(0)
                    val daysOverdue = daysBetween(dueDate, asOf).coerceAtLeast(0)
                    val siInterest  = principalForInterest * rAnnual * (daysTodue.toDouble() / 365.0)
                    if (daysOverdue <= 0) {
                        Math.round(siInterest).toDouble()
                    } else {
                        val baseForCI = principalForInterest + siInterest
                        val fullYears = daysOverdue / 365
                        val remDays   = daysOverdue % 365
                        var ciTotal   = baseForCI
                        repeat(fullYears.toInt()) { ciTotal += ciTotal * rAnnual }
                        ciTotal += ciTotal * rAnnual * (remDays.toDouble() / 365.0)
                        Math.round(siInterest + (ciTotal - baseForCI)).toDouble()
                    }
                }
            }
            // Simple Interest — always on original amount (payments → interest first)
            else -> {
                val tYears = totalDays.toDouble() / 365.0
                Math.round(principalForInterest * rAnnual * tYears).toDouble()
            }
        }
    }

    /**
     * Total outstanding the borrower owes RIGHT NOW.
     *
     * Formula: (principal - paidPrincipal) + max(0, accruedInterest - paidInterest)
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
        paidInterest: Double = 0.0
    ): Double {
        val principalOutstanding = (principal - paidPrincipal).coerceAtLeast(0.0)
        val interestOutstanding  = (accruedInterest - paidInterest).coerceAtLeast(0.0)
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
}