package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.NetWorthSnapshot
import app.fynlo.data.model.FinancialSummary
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for reporting and historical snapshots.
 * Owns: NetWorthSnapshots
 */
@Singleton
class ReportRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    fun getSnapshots(projectId: String): Flow<List<NetWorthSnapshot>> =
        dao.getNetWorthSnapshots(projectId)

    /**
     * Saves today's net worth snapshot (one per day — overwrites if already exists today).
     */
    suspend fun saveSnapshot(summary: FinancialSummary, projectId: String) =
        withContext(Dispatchers.IO) {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val snapshot = NetWorthSnapshot(
                date             = today,
                netWorth         = summary.netWorth,
                totalAssets      = summary.totalAssets,
                totalLiabilities = summary.totalDebtPrincipal + summary.totalDebtInterest,
                projectId        = projectId
            )
            dao.insertOrUpdateNetWorthSnapshot(snapshot)
            firestore.upsertSnapshot(snapshot)
        }

    /** Returns last 12 months of snapshots for a project */
    suspend fun getRecentSnapshots(projectId: String, months: Int = 12): List<NetWorthSnapshot> =
        withContext(Dispatchers.IO) {
            dao.getNetWorthSnapshotsOnce(projectId)
                .sortedByDescending { it.date }
                .take(months * 31) // rough upper bound
        }
}
