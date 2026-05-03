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

    private var backgroundTime = 0L
    private val LOCK_DELAY_MS = 1500L // Only lock if background for >1.5 seconds

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
        backgroundTime = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val elapsed = System.currentTimeMillis() - backgroundTime
        if (backgroundTime > 0 && elapsed > LOCK_DELAY_MS) {
            val pinManager = com.example.cashmemo.data.PinManager(this)
            if (pinManager.isPinSet) {
                AppLockState.lock()
            }
        }
        backgroundTime = 0L
    }
}