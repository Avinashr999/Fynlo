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
        
        return if (isOverdue || intType == "Compound Interest" || intType == "Both") {
            val fullYears = totalDays / 365
            val remainingDays = totalDays % 365
            
            var currentTotal = effectivePrincipal
            repeat(fullYears.toInt()) {
                currentTotal += (currentTotal * rAnnual)
            }
            
            val siForPartialYear = (currentTotal * rAnnual * remainingDays.toDouble()) / 365.0
            val finalAmount = currentTotal + siForPartialYear
            Math.round(finalAmount - effectivePrincipal).toDouble()
        } else {
            val tYears = totalDays.toDouble() / 365.0
            Math.round(effectivePrincipal * rAnnual * tYears).toDouble()
        }
    }

    fun calcOutstanding(
        principal: Double,
        accruedInterest: Double,
        totalPaid: Double
    ): Double {
        // Here principal is the ORIGINAL amount
        return (principal + accruedInterest - totalPaid).coerceAtLeast(0.0)
    }
}