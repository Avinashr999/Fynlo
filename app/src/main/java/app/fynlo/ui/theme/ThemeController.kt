package app.fynlo.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fynlo.data.UserPreferences
import kotlinx.coroutines.runBlocking

object ThemeController {
    var darkModeOverride: Boolean? by mutableStateOf(null)

    fun load(context: Context) {
        val mode = UserPreferences.getDarkModeSync(context)
        darkModeOverride = when (mode) {
            "light" -> false
            "dark" -> true
            else -> null
        }
    }

    fun save(context: Context) {
        val mode = when (darkModeOverride) {
            false -> "light"
            true -> "dark"
            null -> "system"
        }
        runBlocking { UserPreferences.setDarkMode(context, mode) }
    }
}
