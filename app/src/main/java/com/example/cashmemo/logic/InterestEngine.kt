package com.example.cashmemo.logic

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
        
        // Effective principal for interest calculation
        val effectivePrincipal = (amount - totalPaid).coerceAtLeast(0.0)
        if (effectivePrincipal <= 0.0) return 0.0

        val rAnnual = rate / 100.0

        val isOverdue = dueDate.isNotEmpty() && daysBetween(dueDate, asOf) > 0

        return when {
            // Reducing Balance — EMI method, always reducing regardless of overdue
            intType == "Reducing Balance" -> {
                val rMonthly = rAnnual / 12.0
                val months   = totalDays / 30
                if (rMonthly == 0.0 || months == 0L) 0.0
                else {
                    val n = months.toDouble()
                    val totalPayable = effectivePrincipal * rMonthly * Math.pow(1 + rMonthly, n) / (Math.pow(1 + rMonthly, n) - 1) * n
                    Math.round(totalPayable - effectivePrincipal).toDouble()
                }
            }
            // Compound Interest — always compound regardless of overdue
            intType == "Compound Interest" -> {
                val fullYears = totalDays / 365
                val remainingDays = totalDays % 365
                var currentTotal = effectivePrincipal
                repeat(fullYears.toInt()) { currentTotal += (currentTotal * rAnnual) }
                val siForPartialYear = (currentTotal * rAnnual * remainingDays.toDouble()) / 365.0
                Math.round(currentTotal + siForPartialYear - effectivePrincipal).toDouble()
            }
            // Both — SI from loan date to due date, then CI from due date onwards
            intType == "Both" -> {
                if (dueDate.isEmpty()) {
                    // No due date set — treat as simple interest
                    val tYears = totalDays.toDouble() / 365.0
                    Math.round(effectivePrincipal * rAnnual * tYears).toDouble()
                } else {
                    val daysTodue   = daysBetween(loanDate, dueDate).coerceAtLeast(0)
                    val daysOverdue = daysBetween(dueDate, asOf).coerceAtLeast(0)

                    // Phase 1: SI from loan date to due date
                    val siInterest = effectivePrincipal * rAnnual * (daysTodue.toDouble() / 365.0)

                    if (daysOverdue <= 0) {
                        // Not yet overdue — only SI so far
                        Math.round(siInterest).toDouble()
                    } else {
                        // Phase 2: CI from due date onwards on (principal + SI accrued)
                        val baseForCI  = effectivePrincipal + siInterest
                        val fullYears  = daysOverdue / 365
                        val remDays    = daysOverdue % 365
                        var ciTotal    = baseForCI
                        repeat(fullYears.toInt()) { ciTotal += ciTotal * rAnnual }
                        ciTotal += ciTotal * rAnnual * (remDays.toDouble() / 365.0)
                        // Total interest = SI portion + CI portion
                        Math.round(siInterest + (ciTotal - baseForCI)).toDouble()
                    }
                }
            }
            // Simple Interest (default) — always flat SI, even when overdue
            else -> {
                val tYears = totalDays.toDouble() / 365.0
                Math.round(effectivePrincipal * rAnnual * tYears).toDouble()
            }
        }
    }

    fun calcOutstanding(
        principal: Double,
        accruedInterest: Double,
        totalPaid: Double
    ): Double {
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
        val effectivePrincipal = (amount - totalPaid).coerceAtLeast(0.0)
        if (effectivePrincipal <= 0.0 || dueDate.isEmpty()) return Pair(0.0, 0.0)
        val rAnnual     = rate / 100.0
        val daysTodue   = daysBetween(loanDate, dueDate).coerceAtLeast(0)
        val daysOverdue = daysBetween(dueDate, asOf).coerceAtLeast(0)
        val siPortion   = effectivePrincipal * rAnnual * (daysTodue.toDouble() / 365.0)
        if (daysOverdue <= 0) return Pair(Math.round(siPortion).toDouble(), 0.0)
        val baseForCI = effectivePrincipal + siPortion
        val fullYears = daysOverdue / 365
        val remDays   = daysOverdue % 365
        var ciTotal   = baseForCI
        repeat(fullYears.toInt()) { ciTotal += ciTotal * rAnnual }
        ciTotal += ciTotal * rAnnual * (remDays.toDouble() / 365.0)
        return Pair(Math.round(siPortion).toDouble(), Math.round(ciTotal - baseForCI).toDouble())
    }
}