package app.fynlo.logic

import app.fynlo.data.model.Transaction

/**
 * 3.2.59 — orphan-account bug surface.
 *
 * An orphan transaction is one whose `fromAcct` (Expense / Transfer) or
 * `toAcct` (Income / Transfer) points to a name that doesn't match any
 * existing account. Pre-3.2.59 the `AddTransactionDialog` accepted free
 * text for the bank / investment / debt field, so users could type
 * "hdfc" while the account row was "HDFC Bank" — the transaction saved,
 * but the balance-update `WHERE name = :name` matched zero rows.
 *
 * The scanner is the read-side data-repair: given the current
 * transaction list and the canonical account names, return the
 * subset whose linked account doesn't exist. The UI surfaces them so
 * the user can re-point each to a real account.
 *
 * Investments / debts intentionally NOT checked here — the legacy
 * AddTransactionDialog allowed referencing an investment or debt by
 * typed name, but those entities live in separate tables and the
 * balance impact of mismatch is different (the transaction still
 * exists in history; only the secondary linking to the Investment /
 * Debt row would be soft). Phase 2 if we extend.
 */
object OrphanTransactionsScanner {

    /** A single orphan with the offending field surfaced. */
    data class Orphan(
        val transaction: Transaction,
        val typedName:   String,   // the value that didn't match any account
        val side:        Side,     // which field was the offender
    )
    enum class Side { FROM, TO }

    /**
     * Return every transaction whose Expense/Transfer fromAcct or
     * Income/Transfer toAcct is non-blank and doesn't match any name in
     * [accountNames]. Comparison is case-insensitive and ignores leading/
     * trailing whitespace — both should be impossible via the canonical
     * pickers but legacy / imported data may carry them.
     *
     * Transactions with blank account fields are skipped — those are
     * structurally invalid in a different way (and the repository
     * sanitises type=Transfer with empty sides on insert).
     */
    fun scan(transactions: List<Transaction>, accountNames: List<String>): List<Orphan> {
        val canonical: Set<String> = accountNames.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val out = mutableListOf<Orphan>()
        for (t in transactions) {
            when (t.type.lowercase()) {
                "expense" -> {
                    val name = t.fromAcct
                    if (name.isNotBlank() && name.trim().lowercase() !in canonical) {
                        out += Orphan(t, name, Side.FROM)
                    }
                }
                "income" -> {
                    val name = t.toAcct
                    if (name.isNotBlank() && name.trim().lowercase() !in canonical) {
                        out += Orphan(t, name, Side.TO)
                    }
                }
                "transfer" -> {
                    if (t.fromAcct.isNotBlank() && t.fromAcct.trim().lowercase() !in canonical) {
                        out += Orphan(t, t.fromAcct, Side.FROM)
                    }
                    if (t.toAcct.isNotBlank() && t.toAcct.trim().lowercase() !in canonical) {
                        out += Orphan(t, t.toAcct, Side.TO)
                    }
                }
            }
        }
        return out
    }
}
