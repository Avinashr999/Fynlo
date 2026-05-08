package app.fynlo.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

object ThemeController {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    var darkModeOverride: Boolean? by mutableStateOf(null)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DARK_MODE)) {
            darkModeOverride = prefs.getBoolean(KEY_DARK_MODE, false)
        } else {
            darkModeOverride = null
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            val current = darkModeOverride
            if (current == null) {
                remove(KEY_DARK_MODE)
            } else {
                putBoolean(KEY_DARK_MODE, current)
            }
        }
    }
}
