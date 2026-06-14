package app.fynlo.logic

import app.fynlo.logic.BankStatementImport.ColumnMap
import app.fynlo.logic.BankStatementImport.RowResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C22 (3.2.67) — BankStatementImport unit tests.
 */
class BankStatementImportDataIntegrityTest {

    // ── tryParseDate ────────────────────────────────────────────────

    @Test
    fun parsesIsoDate() {
        assertEquals("2026-05-28", BankStatementImport.tryParseDate("2026-05-28"))
    }

    @Test
    fun parsesIndianDdMmYyyy() {
        assertEquals("2026-05-28", BankStatementImport.tryParseDate("28-05-2026"))
        assertEquals("2026-05-28", BankStatementImport.tryParseDate("28/05/2026"))
    }

    @Test
    fun parsesUsMmDdYyyy() {
        // 05/28/2026 — May 28. Note this collides with 28/05 layouts; the
        // matcher tries dd/MM first so this only resolves under MM/dd
        // when day > 12. "05/28" → day=28 (>12) → MM/dd wins.
        assertEquals("2026-05-28", BankStatementImport.tryParseDate("05/28/2026"))
    }

    @Test
    fun parsesBlankAsNull() {
        assertNull(BankStatementImport.tryParseDate(""))
        assertNull(BankStatementImport.tryParseDate("   "))
    }

    @Test
    fun parsesGibberishAsNull() {
        assertNull(BankStatementImport.tryParseDate("not a date"))
        assertNull(BankStatementImport.tryParseDate("99-99-9999"))
    }

    // ── tryParseAmount ──────────────────────────────────────────────

    @Test
    fun parsesPlainNumber() {
        assertEquals(1234.56, BankStatementImport.tryParseAmount("1234.56")!!, 0.001)
        assertEquals(-50.0,   BankStatementImport.tryParseAmount("-50")!!,     0.001)
    }

    @Test
    fun stripsCurrencySymbolsAndCommas() {
        assertEquals(1234.56, BankStatementImport.tryParseAmount("₹1,234.56")!!, 0.001)
        assertEquals(1234.56, BankStatementImport.tryParseAmount("$1,234.56")!!, 0.001)
        assertEquals(1234.56, BankStatementImport.tryParseAmount("€ 1 234.56")!!, 0.001)
    }

    @Test
    fun parsesParenthesesAsNegative() {
        // Accounting convention: (1234.56) = -1234.56
        assertEquals(-1234.56, BankStatementImport.tryParseAmount("(1234.56)")!!, 0.001)
        assertEquals(-1234.56, BankStatementImport.tryParseAmount("(₹1,234.56)")!!, 0.001)
    }

    @Test
    fun parsesIndianDrCrSuffix() {
        assertEquals(-500.0, BankStatementImport.tryParseAmount("500 Dr")!!, 0.001)
        assertEquals(500.0,  BankStatementImport.tryParseAmount("500 Cr")!!, 0.001)
        assertEquals(-500.0, BankStatementImport.tryParseAmount("500.00Dr")!!, 0.001)
    }

    @Test
    fun unparseableReturnsNull() {
        assertNull(BankStatementImport.tryParseAmount(""))
        assertNull(BankStatementImport.tryParseAmount("not a number"))
    }

    // ── mapRows ────────────────────────────────────────────────────

    private val header = listOf("Date", "Description", "Amount")

    @Test
    fun mapsHeaderAndPositiveAmountAsIncome() {
        val rows = listOf(
            header,
            listOf("2026-05-28", "Salary", "50000"),
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank")
        assertEquals(1, results.size)
        val ok = results[0] as RowResult.Ok
        val txn = ok.transaction
        assertEquals("Income", txn.type)
        assertEquals("HDFC Bank", txn.toAcct)
        assertEquals("", txn.fromAcct)
        assertEquals(50000.0, txn.amount, 0.001)
        assertEquals("Salary", txn.desc)
        assertEquals("Uncategorized", txn.category)
    }

    @Test
    fun mapsNegativeAmountAsExpense() {
        val rows = listOf(
            header,
            listOf("28-05-2026", "Coffee", "-150"),
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank")
        val txn = (results[0] as RowResult.Ok).transaction
        assertEquals("Expense", txn.type)
        assertEquals("HDFC Bank", txn.fromAcct)
        assertEquals("", txn.toAcct)
        assertEquals(150.0, txn.amount, 0.001)
    }

    @Test
    fun mapsCategoryColumnWhenProvided() {
        val rows = listOf(
            listOf("Date", "Description", "Amount", "Category"),
            listOf("2026-05-28", "Coffee", "-150", "Food"),
        )
        val results = BankStatementImport.mapRows(
            rows,
            ColumnMap(dateCol = 0, descriptionCol = 1, amountCol = 2, categoryCol = 3),
            targetAccount = "HDFC Bank",
        )
        val txn = (results[0] as RowResult.Ok).transaction
        assertEquals("Food", txn.category)
    }

    @Test
    fun skipsRowsWithBadDate() {
        val rows = listOf(
            header,
            listOf("not-a-date", "Bad row", "100"),
            listOf("2026-05-28", "Good row", "200"),
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank")
        assertEquals(2, results.size)
        assertTrue(results[0] is RowResult.Skip)
        assertTrue(results[1] is RowResult.Ok)
        val skip = results[0] as RowResult.Skip
        assertTrue("Reason should mention date", skip.reason.contains("date", ignoreCase = true))
    }

    @Test
    fun skipsRowsWithBadAmount() {
        val rows = listOf(
            header,
            listOf("2026-05-28", "Garbage amount", "not-a-number"),
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank")
        assertTrue(results[0] is RowResult.Skip)
        assertTrue((results[0] as RowResult.Skip).reason.contains("amount", ignoreCase = true))
    }

    @Test
    fun skipsZeroAmountRows() {
        val rows = listOf(
            header,
            listOf("2026-05-28", "Zero", "0"),
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank")
        assertTrue(results[0] is RowResult.Skip)
    }

    @Test
    fun skipsRowsWithTooFewColumns() {
        val rows = listOf(
            header,
            listOf("2026-05-28", "Truncated"),  // only 2 columns; amount missing
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank")
        assertTrue(results[0] is RowResult.Skip)
    }

    @Test
    fun hasHeaderFalseConsumesFirstRowAsData() {
        val rows = listOf(
            listOf("2026-05-28", "Coffee", "-150"),
        )
        val results = BankStatementImport.mapRows(rows, ColumnMap(0, 1, 2), "HDFC Bank", hasHeader = false)
        assertEquals(1, results.size)
        assertTrue(results[0] is RowResult.Ok)
    }
}
