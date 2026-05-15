package app.fynlo

import androidx.lifecycle.ViewModel
import app.fynlo.data.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import app.fynlo.data.model.NetWorthSnapshot
import javax.inject.Inject

/**
 * Dashboard-specific ViewModel.
 *
 * Thin layer over FinanceViewModel for dashboard-specific concerns.
 * The HomeScreen continues to use FinanceViewModel directly for broad
 * financial data; this VM handles report-specific queries.
 *
 * NOTE: Full dashboard state migration from FinanceViewModel is planned
 * for v3.3 once all feature ViewModels are stable.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val reportRepository: ReportRepository
) : ViewModel() {

    /** Real-time net worth history for the sparkline chart */
    fun getSnapshotsForProject(projectId: String): Flow<List<NetWorthSnapshot>> =
        reportRepository.getSnapshots(projectId)
}
