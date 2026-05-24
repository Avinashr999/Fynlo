package app.fynlo

import android.os.Bundle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import app.fynlo.ui.MainNavigation
import app.fynlo.ui.theme.FynloTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var backgroundTime = 0L
    private val LOCK_DELAY_MS = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.fynlo.ui.theme.ThemeController.load(this)
        enableEdgeToEdge()

        val viewModel: FinanceViewModel by viewModels()

        setContent {
            FynloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Signals to Macrobenchmark that the app is "fully drawn"
                    // for TTFD measurement — fires once initial data has loaded.
                    val ready by viewModel.isSyncReady.collectAsState()
                    ReportDrawnWhen { ready }

                    MainNavigation(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundTime = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val elapsed = System.currentTimeMillis() - backgroundTime
        if (backgroundTime > 0 && elapsed > LOCK_DELAY_MS) {
            val pinManager = app.fynlo.data.PinManager(this)
            if (pinManager.isPinSet) {
                AppLockState.lock()
            }
        }
        backgroundTime = 0L
    }
}
