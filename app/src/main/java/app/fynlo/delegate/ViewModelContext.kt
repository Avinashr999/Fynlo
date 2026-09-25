package app.fynlo.delegate

import android.content.Context
import app.fynlo.data.FinanceRepository
import app.fynlo.data.RecalcCoordinator
import app.fynlo.data.RecentlyUsedTracker
import kotlinx.coroutines.CoroutineScope

class ViewModelContext(
    val repository: FinanceRepository,
    val recalcCoordinator: RecalcCoordinator,
    val recentlyUsedTracker: RecentlyUsedTracker,
    val context: Context,
    val scope: CoroutineScope,
    val showFeedback: (String) -> Unit,
    val runMoneyAction: (suspend () -> Unit) -> Unit,
    val currentProjectId: () -> String,
    val today: () -> String
)
