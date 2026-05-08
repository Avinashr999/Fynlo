package app.fynlo.logic

/**
 * Currency utilities for multi-currency support.
 * Used throughout the app to format amounts based on project currency.
 */
object CurrencyUtils {

    data class CurrencyInfo(val code: String, val symbol: String, val name: String)

    val supported = listOf(
        CurrencyInfo("INR", "₹",  "Indian Rupee"),
        CurrencyInfo("USD", "$",  "US Dollar"),
        CurrencyInfo("EUR", "€",  "Euro"),
        CurrencyInfo("GBP", "£",  "British Pound"),
        CurrencyInfo("AED", "د.إ","UAE Dirham"),
        CurrencyInfo("SGD", "S$", "Singapore Dollar"),
        CurrencyInfo("AUD", "A$", "Australian Dollar"),
        CurrencyInfo("CAD", "C$", "Canadian Dollar"),
        CurrencyInfo("JPY", "¥",  "Japanese Yen"),
        CurrencyInfo("SAR", "﷼",  "Saudi Riyal"),
        CurrencyInfo("QAR", "﷼",  "Qatari Riyal"),
        CurrencyInfo("MYR", "RM", "Malaysian Ringgit"),
        CurrencyInfo("NPR", "Rs", "Nepalese Rupee"),
        CurrencyInfo("LKR", "Rs", "Sri Lankan Rupee"),
        CurrencyInfo("BDT", "৳",  "Bangladeshi Taka"),
    )

    /** Get symbol for a currency code. Defaults to ₹ */
    fun symbolFor(code: String): String =
        supported.find { it.code.equals(code, ignoreCase = true) }?.symbol ?: "₹"

    /** Format an amount with the correct symbol */
    fun format(amount: Double, currencyCode: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val symbol = symbolFor(currencyCode)
        return "$symbol ${String.format(locale, "%,.2f", amount)}"
    }

    /** Format without decimal for large round numbers */
    fun formatCompact(amount: Double, currencyCode: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val symbol = symbolFor(currencyCode)
        return when {
            amount >= 10_000_000 -> "$symbol ${String.format(locale, "%.1f", amount / 10_000_000)}Cr"
            amount >= 100_000   -> "$symbol ${String.format(locale, "%.1f", amount / 100_000)}L"
            amount >= 1_000     -> "$symbol ${String.format(locale, "%.1f", amount / 1_000)}K"
            else                -> "$symbol ${String.format(locale, "%,.0f", amount)}"
        }
    }
}
