package app.fynlo.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.fynlo.FinanceViewModel

/**
 * Wraps a scrollable list/column with Material 3 pull-to-refresh.
 * Calls [FinanceViewModel.refresh] (which surfaces the live sync status) and
 * clears the indicator when it completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            viewModel.refresh { refreshing = false }
        },
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}
