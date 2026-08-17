package app.fynlo.logic

import app.fynlo.data.model.FinancialSummary
import app.fynlo.data.model.NetWorthSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthSnapshotSafetyTest {
    @Test
    fun `ledger data blocks saving loading zero as real net worth history`() {
        val loadingSummary = FinancialSummary()

        assertTrue(NetWorthSnapshotSafety.shouldSkipSave(loadingSummary, hasLedgerData = true))
        assertFalse(NetWorthSnapshotSafety.shouldSkipSave(loadingSummary, hasLedgerData = false))
    }

    @Test
    fun `only all-zero snapshots are treated as removable placeholders`() {
        assertTrue(
            NetWorthSnapshotSafety.isEmptyPlaceholder(
                NetWorthSnapshot(date = "2026-08-17", netWorth = 0.0, totalAssets = 0.0, totalLiabilities = 0.0)
            )
        )
        assertFalse(
            NetWorthSnapshotSafety.isEmptyPlaceholder(
                NetWorthSnapshot(date = "2026-08-18", netWorth = 100.0, totalAssets = 100.0, totalLiabilities = 0.0)
            )
        )
    }
}
