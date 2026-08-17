package app.fynlo.logic

import app.fynlo.data.model.FinancialSummary
import app.fynlo.data.model.NetWorthSnapshot
import kotlin.math.abs

object NetWorthSnapshotSafety {
    private const val ZERO_TOLERANCE = 0.0001

    fun isEmptyPlaceholder(snapshot: NetWorthSnapshot): Boolean =
        isZero(snapshot.netWorth) &&
            isZero(snapshot.totalAssets) &&
            isZero(snapshot.totalLiabilities)

    fun shouldSkipSave(summary: FinancialSummary, hasLedgerData: Boolean): Boolean =
        hasLedgerData &&
            isZero(summary.netWorth) &&
            isZero(summary.totalAssets) &&
            isZero(summary.totalDebtPrincipal + summary.totalDebtInterest)

    private fun isZero(value: Double): Boolean = abs(value) < ZERO_TOLERANCE
}
