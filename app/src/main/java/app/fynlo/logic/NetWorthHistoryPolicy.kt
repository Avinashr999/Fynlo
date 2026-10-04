package app.fynlo.logic

import app.fynlo.data.model.NetWorthSnapshot
import java.time.LocalDate
import kotlin.math.abs

/** Provenance, not amount or timestamp alone, determines whether history is comparable. */
object NetWorthHistoryPolicy {
    const val LIVE = "COMPLETE_LEDGER_V1"
    const val RECOVERED = "DATED_BACKUP_V1"

    fun isTrusted(row: NetWorthSnapshot): Boolean =
        row.captureSource in setOf(LIVE, RECOVERED) &&
            row.sourceReference.isNotBlank() && row.createdAt > 0 &&
            runCatching { LocalDate.parse(row.date).toString() == row.date }.getOrDefault(false) &&
            listOf(row.netWorth, row.totalAssets, row.totalLiabilities).all { it.isFinite() } &&
            abs(row.netWorth - (row.totalAssets - row.totalLiabilities)) < 0.011

    fun trusted(rows: List<NetWorthSnapshot>): List<NetWorthSnapshot> =
        rows.filter(::isTrusted).sortedBy { it.date }

    // Never substitute a much older point for a missing monthly comparison date.
    fun at(rows: List<NetWorthSnapshot>, date: LocalDate): NetWorthSnapshot? =
        rows.singleOrNull { it.date == date.toString() && isTrusted(it) }

    fun change(rows: List<NetWorthSnapshot>, from: LocalDate, to: LocalDate): Double? {
        val first = at(rows, from) ?: return null
        val last = at(rows, to) ?: return null
        return last.netWorth - first.netWorth
    }
}
