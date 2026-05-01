package com.example.cashmemo.logic

import com.example.cashmemo.data.model.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure function engine — no DB dependency, fully testable.
 * Analyses the last 60 days of transactions and returns
 * pattern-based suggestions for the wizard.
 */
object FlowRuleEngine {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Returns suggested (category -> account) mappings based on history.
     * e.g. "Fuel" always debited from "HDFC Bank" -> suggest that account.
     */
    fun suggestAccountForCategory(
        transactions: List<Transaction>,
        category: String
    ): String {
        val cutoff = LocalDate.now().minusDays(60)
        return transactions
            .filter { t ->
                t.category.equals(category, ignoreCase = true) &&
                runCatching { LocalDate.parse(t.date, fmt).isAfter(cutoff) }.getOrDefault(false)
            }
            .groupBy { it.fromAcct.ifBlank { it.toAcct } }
            .maxByOrNull { it.value.size }
            ?.key ?: ""
    }

    /**
     * Returns the top N most-used expense categories in the last 60 days.
     */
    fun topCategories(transactions: List<Transaction>, n: Int = 6): List<String> {
        val cutoff = LocalDate.now().minusDays(60)
        return transactions
            .filter { t ->
                t.type.equals("expense", ignoreCase = true) &&
                runCatching { LocalDate.parse(t.date, fmt).isAfter(cutoff) }.getOrDefault(false)
            }
            .groupBy { it.category }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    /**
     * For income flows: returns average monthly income amount
     * from the last 90 days (useful to pre-fill salary wizard).
     */
    fun averageMonthlyIncome(transactions: List<Transaction>): Double {
        val cutoff = LocalDate.now().minusDays(90)
        val total = transactions
            .filter { t ->
                t.type.equals("income", ignoreCase = true) &&
                runCatching { LocalDate.parse(t.date, fmt).isAfter(cutoff) }.getOrDefault(false)
            }
            .sumOf { it.amount }
        return if (total > 0) total / 3.0 else 0.0
    }

    /**
     * Determines the most-used source account for a given event type.
     */
    fun suggestSourceAccount(
        transactions: List<Transaction>,
        eventType: String
    ): String {
        val type = when (eventType) {
            "Spent", "Lent"    -> "expense"
            "Received","Borrowed" -> "income"
            else               -> "expense"
        }
        return transactions
            .filter { it.type.equals(type, ignoreCase = true) }
            .groupBy { if (type == "expense") it.fromAcct else it.toAcct }
            .maxByOrNull { it.value.size }
            ?.key ?: ""
    }
}