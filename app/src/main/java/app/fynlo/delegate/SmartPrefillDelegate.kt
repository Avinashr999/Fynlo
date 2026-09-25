package app.fynlo.delegate

import app.fynlo.data.RecentlyUsedTracker
import app.fynlo.data.BudgetSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import app.fynlo.data.model.Budget

class SmartPrefillDelegate(
    private val ctx: ViewModelContext,
    private val expenseAnalyticsFlow: StateFlow<Map<String, Double>>,
    private val budgetsFlow: StateFlow<List<Budget>>
) {

    suspend fun rememberLastTransactionCategory(isIncome: Boolean): String?{
        val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                      else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
        return ctx.recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.ADD_TRANSACTION,
            fieldId,
        )
    }

    fun recordTransactionCategory(isIncome: Boolean, category: String) {
        if (category.isBlank()) return
        ctx.scope.launch(Dispatchers.IO) {
            val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                          else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
            ctx.recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.ADD_TRANSACTION,
                fieldId,
                category,
            )
        }
    }

    suspend fun rememberLastRecurringCategory(isIncome: Boolean): String?{
        val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                      else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
        return ctx.recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.ADD_RECURRING,
            fieldId,
        )
    }

    fun recordRecurringCategory(isIncome: Boolean, category: String) {
        if (category.isBlank()) return
        ctx.scope.launch(Dispatchers.IO) {
            val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                          else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
            ctx.recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.ADD_RECURRING,
                fieldId,
                category,
            )
        }
    }

    fun suggestBudgetCategory(): String?=
        BudgetSuggestion.suggest(
            cappedCategories = budgetsFlow.value.map { it.category }.toSet(),
            expenseAnalytics = expenseAnalyticsFlow.value,
        )

    suspend fun rememberLastBudgetCategory(): String?=
        ctx.recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.ADD_BUDGET,
            RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE,
        )

    fun recordBudgetCategory(category: String) {
        if (category.isBlank()) return
        ctx.scope.launch(Dispatchers.IO) {
            ctx.recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.ADD_BUDGET,
                RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE,
                category,
            )
        }
    }

    suspend fun rememberLastCurrencyOrLocale(
        locale: java.util.Locale = java.util.Locale.getDefault(),
    ): String {
        ctx.recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.SETTINGS_CURRENCY,
            RecentlyUsedTracker.FieldIds.CURRENCY,
        )?.let { return it }
        return runCatching { java.util.Currency.getInstance(locale).currencyCode }
            .getOrDefault("INR")
    }

    fun recordCurrency(code: String) {
        if (code.isBlank()) return
        ctx.scope.launch(Dispatchers.IO) {
            ctx.recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.SETTINGS_CURRENCY,
                RecentlyUsedTracker.FieldIds.CURRENCY,
                code,
            )
        }
    }

    fun observeRecentCurrencies(n: Int = 5): Flow<List<String>> =
        ctx.recentlyUsedTracker.observeTopN(
            RecentlyUsedTracker.FormIds.SETTINGS_CURRENCY,
            RecentlyUsedTracker.FieldIds.CURRENCY,
            n,
        )
}
