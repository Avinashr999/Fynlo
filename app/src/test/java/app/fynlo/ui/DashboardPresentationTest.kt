package app.fynlo.ui

import app.fynlo.data.model.Transaction
import org.junit.Assert.*
import org.junit.Test

class DashboardPresentationTest {
    private fun row(id: String, date: String, time: Long = 0L) = Transaction(
        id = id, date = date, type = "Expense", amount = 1250.75, category = "Lending", createdAt = time,
    )

    @Test fun `recent activity uses business date then recorded time and keeps financing`() {
        val rows = listOf(row("older", "2026-09-01", 900), row("second", "2026-10-01", 100),
            row("first", "2026-10-01", 200), row("third", "2026-09-30", 500))
        assertEquals(listOf("first", "second", "third"), dashboardRecentActivity(rows).map { it.id })
        assertEquals(1250.75, dashboardRecentActivity(rows).first().amount, 0.0)
    }

    @Test fun `journal duplicates excluded without deleting or mutating any record`() {
        val rows = listOf(row("funding", "2026-10-01"), row("journal", "2026-10-02").copy(tags = "journal_only"))
        val before = rows.map { it.copy() }
        assertEquals(listOf(rows.first()), dashboardRecentActivity(rows))
        assertEquals(before, rows)
    }

    @Test fun `empty ledger and fewer than three rows are supported`() {
        assertTrue(dashboardRecentActivity(emptyList()).isEmpty())
        val transfer = row("transfer", "2026-10-01").copy(type = "Transfer", category = "Transfer", fromAcct = "Family Cash", toAcct = "HDFC")
        assertEquals(listOf(transfer), dashboardRecentActivity(listOf(transfer)))
    }
}
