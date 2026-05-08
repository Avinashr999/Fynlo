package app.fynlo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global app lock state. When isLocked = true, Navigation shows PinScreen.
 * Set to true by MainActivity.onStop() when PIN is enabled.
 */
object AppLockState {
    var isLocked by mutableStateOf(false)
        private set

    fun lock()   { isLocked = true  }
    fun unlock() { isLocked = false }
}
