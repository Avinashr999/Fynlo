package app.fynlo

import android.os.Bundle
import android.os.Build
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import app.fynlo.ui.MainNavigation
import app.fynlo.ui.theme.FynloTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var backgroundTime = 0L
    private val LOCK_DELAY_MS = 1500L

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result â€” notifications will work if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.fynlo.ui.theme.ThemeController.load(this)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val viewModel: FinanceViewModel by viewModels()

        setContent {
            FynloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
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
