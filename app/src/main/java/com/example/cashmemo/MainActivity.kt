package com.example.cashmemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.cashmemo.ui.MainNavigation
import com.example.cashmemo.ui.theme.CashMemoTheme

class MainActivity : ComponentActivity() {

    // Lock the app when it goes to background
    private var appWentToBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.cashmemo.ui.theme.ThemeController.load(this)
        enableEdgeToEdge()

        val app = application as CashMemoApplication
        val viewModel = FinanceViewModel(app.repository)

        setContent {
            CashMemoTheme {
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
        // Mark that app went to background
        val pinManager = com.example.cashmemo.data.PinManager(this)
        if (pinManager.isPinSet) {
            appWentToBackground = true
            // Signal Navigation to re-lock
            AppLockState.lock()
        }
    }

    override fun onStart() {
        super.onStart()
        if (appWentToBackground) {
            appWentToBackground = false
            // AppLockState.isLocked is already true — Navigation will show PinScreen
        }
    }
}