package app.fynlo.data

/**
 * C05 — typed category lists (UX_AUDIT §C05).
 *
 * The audit's bug: `AddTransactionDialog` and `EditTransactionDialog`
 * showed a **single flat list** of categories — Food/Rent/Fuel/Shopping
 * (expense) mixed with Salary/Investment/Lending (income) — regardless of
 * the Income/Expense toggle. Users would pick "Food" while on Income tab,
 * the transaction got logged with `type=Income, category=Food`, and P&L
 * grouping broke.
 *
 * Fix: two static lists, switched by [forType]. The chip-picker in each
 * dialog reads from the correct list for the current toggle, and the
 * selection is reset to `""` when the toggle flips.
 *
 * The audit notes a "future migration path to user-customizable" —
 * intentionally not implemented here. When that work lands it can either
 * (a) read from a `categories` Room table seeded with these defaults, or
 * (b) keep these as the curated set and append user-defined entries.
 *
 * Pure constants — no Context, no I/O, no Room. Unit-testable from JVM.
 */
object Categories {
    const val ACCOUNT_TRANSFER: String = "Account Transfer"

    /**
     * Cash-inflow categories. Used when the transaction type is `Income`.
     * Ordered roughly by expected frequency for the typical individual
     * user (salary → freelance → passive returns → one-offs).
     */
    val INCOME: List<String> = listOf(
        "Salary",
        "Freelance",
        "Interest",
        "Dividend",
        "Loan Repayment",   // money coming back from a borrower
        "Refund",
        "Gift Received",
        "Other Income",
    )

    /**
     * Cash-outflow categories. Used when the transaction type is `Expense`.
     * Ordered roughly by expected frequency.
     *
     * `Lending` (outbound loan), `Investment` (buying an asset), and
     * `Subscriptions` are tracked separately from generic spend so the
     * user can split discretionary vs. mandatory in reports later.
     */
    val EXPENSE: List<String> = listOf(
        "Food",
        "Fuel",
        "Shopping",
        "Bills",
        "Rent",
        "Healthcare",
        "Education",
        "Entertainment",
        "Transport",
        "Subscriptions",
        "Lending",          // outbound loan to a borrower
        "Investment",       // purchase of an asset (stocks, gold, FD, etc.)
        "Other Expense",
    )

    /**
     * The single category used for Transfer transactions. Transfers move
     * money between the user's own accounts and don't belong on either
     * Income or Expense side of the P&L — they're balance-sheet only.
     */
    val TRANSFER: List<String> = listOf(ACCOUNT_TRANSFER)

    /**
     * Returns the appropriate category list for the given transaction
     * `type`. Matching is **case-insensitive** on the three canonical
     * literals; any unknown / blank type falls back to [EXPENSE] —
     * conservatively, because:
     *   1. The historical default before C05 was an expense-leaning mix,
     *      so falling back to EXPENSE preserves intuitive behaviour for
     *      anyone seeing an unexpected type value.
     *   2. Falling back to INCOME would risk income-flavour categories
     *      bleeding into outflow-shaped data, which is the exact bug C05
     *      is fixing.
     */
    fun forType(type: String): List<String> = when (type.lowercase()) {
        "income"   -> INCOME
        "expense"  -> EXPENSE
        "transfer" -> TRANSFER
        else       -> EXPENSE
    }
}
