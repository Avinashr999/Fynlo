package app.fynlo.logic

import app.fynlo.data.model.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * C22 (3.2.67) — bank-statement CSV row mapper.
 *
 * Bank CSV layouts vary wildly. The mapper takes a per-file column
 * mapping (which header is the date, which is the amount, etc.) and
 * produces canonical [Transaction] rows. Sign convention follows the
 * common bank-statement contract:
 *
 *   - amount > 0  → Income (deposit)         → toAcct = targetAccount
 *   - amount < 0  → Expense (withdrawal)     → fromAcct = targetAccount
 *
 * All rows land on a single user-picked [targetAccount] so the orphan-
 * account bug from 3.2.59 can't recur. If the bank file mixes multiple
 * accounts, the user runs the importer once per account.
 *
 * Pure-Kotlin; covered by `BankStatementImportDataIntegrityTest`.
 */
object BankStatementImport {

    /**
     * Column indices into a parsed CSV row. `category` is optional — when
     * `null`, all imported rows default to "Uncategorized" (matching the
     * C03a sentinel used elsewhere for un-set categories).
     */
    data class ColumnMap(
        val dateCol:        Int,
        val descriptionCol: Int,
        val amountCol:      Int,
        val categoryCol:    Int? = null,
    )

    /**
     * Single mapper result — successful row OR a parse error with the
     * row index so the importer UI can surface "Row 12: bad date".
     */
    sealed class RowResult {
        data class Ok(val transaction: Transaction, val isDuplicate: Boolean = false) : RowResult()
        data class Skip(val rowIndex: Int, val reason: String) : RowResult()
    }

    /**
     * Map every CSV row (header excluded by [hasHeader]) to a Transaction.
     * Rows that can't be parsed (bad date, blank amount, etc.) return a
     * [RowResult.Skip] so the UI can show "imported N, skipped M".
     */
    fun mapRows(
        rows:          List<List<String>>,
        columns:       ColumnMap,
        targetAccount: String,
        existingTransactions: List<Transaction> = emptyList(),
        projectId:     String = "personal",
        hasHeader:     Boolean = true,
    ): List<RowResult> {
        val out = mutableListOf<RowResult>()
        val data = if (hasHeader) rows.drop(1) else rows
        data.forEachIndexed { i, row ->
            val displayRowIndex = i + 1 + if (hasHeader) 1 else 0  // 1-based, accounts for header
            val maxIdx = listOfNotNull(columns.dateCol, columns.descriptionCol, columns.amountCol, columns.categoryCol).max()
            if (row.size <= maxIdx) {
                out += RowResult.Skip(displayRowIndex, "Too few columns")
                return@forEachIndexed
            }
            val rawDate   = row[columns.dateCol].trim()
            val rawDesc   = row[columns.descriptionCol].trim()
            val rawAmount = row[columns.amountCol].trim()
            val rawCat    = columns.categoryCol?.let { row[it].trim() }.orEmpty()

            val parsedDate = tryParseDate(rawDate)
                ?: run { out += RowResult.Skip(displayRowIndex, "Unrecognised date \"$rawDate\""); return@forEachIndexed }
            val parsedAmount = tryParseAmount(rawAmount)
                ?: run { out += RowResult.Skip(displayRowIndex, "Unparseable amount \"$rawAmount\""); return@forEachIndexed }
            if (parsedAmount == 0.0) {
                out += RowResult.Skip(displayRowIndex, "Zero amount — skipped")
                return@forEachIndexed
            }

            val isIncome = parsedAmount > 0
            val absAmount = kotlin.math.abs(parsedAmount)
            var category = rawCat.ifBlank { "Uncategorized" }
            
            // C22 (3.2.68) — auto-categorization logic.
            if (category == "Uncategorized") {
                category = autoCategorize(rawDesc, existingTransactions)
            }

            val transaction = Transaction(
                id        = Ids.newId(),
                date      = parsedDate,
                type      = if (isIncome) "Income" else "Expense",
                amount    = absAmount,
                fromAcct  = if (isIncome) "" else targetAccount,
                toAcct    = if (isIncome) targetAccount else "",
                category  = category,
                desc      = rawDesc,
                projectId = projectId,
            )

            // C22 (3.2.69) — duplicate detection.
            val isDup = isDuplicate(transaction, existingTransactions)
            
            out += RowResult.Ok(transaction, isDup)
        }
        return out
    }

    /**
     * Finds a likely category based on historical descriptions or keywords.
     */
    private fun autoCategorize(description: String, history: List<Transaction>): String {
        // 1. Exact or near-exact match in history (weighted by frequency)
        val historicalMatch = history
            .filter { it.desc.contains(description, ignoreCase = true) || description.contains(it.desc, ignoreCase = true) }
            .filter { it.category != "Uncategorized" }
            .groupBy { it.category }
            .maxByOrNull { it.value.size }?.key
            
        if (historicalMatch != null) return historicalMatch

        // 2. Keyword heuristic (hardcoded common merchants)
        val keywords = mapOf(
            "ZOMATO" to "Food", "SWIGGY" to "Food", "RESTAURANT" to "Food",
            "AMAZON" to "Shopping", "FLIPKART" to "Shopping", "MYNTRA" to "Shopping",
            "UBER" to "Transport", "OLA" to "Transport", "METRO" to "Transport",
            "AIRTEL" to "Bills", "JIO" to "Bills", "VODAFONE" to "Bills", "ELECTRICITY" to "Bills",
            "NETFLIX" to "Entertainment", "SPOTIFY" to "Entertainment", "HOTSTAR" to "Entertainment",
            "HDFC" to "Bank Fees", "ICICI" to "Bank Fees", "SBIN" to "Bank Fees",
            "INVEST" to "Investment", "ZERODHA" to "Investment", "GROWW" to "Investment"
        )
        for ((kw, cat) in keywords) {
            if (description.contains(kw, ignoreCase = true)) return cat
        }
        
        return "Uncategorized"
    }

    /**
     * Checks if a transaction likely already exists in the system.
     */
    private fun isDuplicate(txn: Transaction, history: List<Transaction>): Boolean {
        return history.any { old ->
            old.date == txn.date &&
            kotlin.math.abs(old.amount - txn.amount) < 0.01 &&
            old.toAcct == txn.toAcct &&
            old.fromAcct == txn.fromAcct &&
            (old.desc.contains(txn.desc, ignoreCase = true) || txn.desc.contains(old.desc, ignoreCase = true))
        }
    }

    // Accepted date formats — ordered by likelihood across Indian / US /
    // EU bank statement exports. ISO comes first as the safest default;
    // dd-MM and dd/MM are unambiguous Indian formats; MM/dd/yyyy is US;
    // yyyy/MM/dd is occasional. We persist as yyyy-MM-dd internally
    // (matches Transaction.date format used elsewhere).
    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
        "yyyy/MM/dd", "dd-MMM-yyyy", "d MMM yyyy", "d-MMM-yy",
    ).map { DateTimeFormatter.ofPattern(it) }

    /** First-match-wins across [DATE_FORMATS]; returns ISO yyyy-MM-dd on
     *  success, null when no pattern parses cleanly. */
    fun tryParseDate(raw: String): String? {
        if (raw.isBlank()) return null
        for (fmt in DATE_FORMATS) {
            try {
                val d = LocalDate.parse(raw, fmt)
                return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (_: DateTimeParseException) { /* try next */ }
        }
        return null
    }

    /** Parse an amount that may carry currency symbols, thousand separators,
     *  parentheses for negatives (accounting convention), or Dr/Cr suffixes
     *  (Indian bank format). Returns null when nothing usable is found. */
    fun tryParseAmount(raw: String): Double? {
        if (raw.isBlank()) return null
        var s = raw.trim()
        // Strip common currency symbols + thousand separators + spaces.
        s = s.replace(Regex("[₹$€£,\\s]"), "")
        var negative = false
        // Parentheses around the number = negative (accounting style).
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        // Indian "Dr" = debit (negative), "Cr" = credit (positive).
        when {
            s.endsWith("Dr", ignoreCase = true) -> { negative = true; s = s.dropLast(2) }
            s.endsWith("Cr", ignoreCase = true) -> { s = s.dropLast(2) }
        }
        val parsed = s.toDoubleOrNull() ?: return null
        return if (negative) -kotlin.math.abs(parsed) else parsed
    }
}
