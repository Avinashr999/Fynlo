package app.fynlo.logic

import app.fynlo.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionOrderingDataIntegrityTest {

    @Test
    fun `newestFirst orders by business date then creation time then id`() {
        val olderDay = txn(id = "older-day", date = "2026-06-18", createdAt = 9000L)
        val sameDayEarlier = txn(id = "same-day-earlier", createdAt = 1000L)
        val sameDayLater = txn(id = "same-day-later", createdAt = 2000L)
        val tieA = txn(id = "tie-a", createdAt = 3000L)
        val tieB = txn(id = "tie-b", createdAt = 3000L)

        val ordered = TransactionOrdering.newestFirst(
            listOf(sameDayEarlier, olderDay, tieA, sameDayLater, tieB)
        )

        assertEquals(
            listOf("tie-b", "tie-a", "same-day-later", "same-day-earlier", "older-day"),
            ordered.map { it.id },
        )
    }

    @Test
    fun `eventMillis falls back to updatedAt for legacy rows without createdAt`() {
        val legacy = txn(id = "legacy", createdAt = 0L, updatedAt = 1234L)

        assertEquals(1234L, TransactionOrdering.eventMillis(legacy))
    }

    private fun txn(
        id: String,
        date: String = "2026-06-19",
        createdAt: Long = 1L,
        updatedAt: Long = createdAt,
    ) = Transaction(
        id = id,
        date = date,
        type = "Expense",
        amount = 1.0,
        fromAcct = "Cash",
        category = "Food",
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
