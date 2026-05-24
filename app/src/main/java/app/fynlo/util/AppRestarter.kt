package app.fynlo.util

import android.content.Context
import android.content.Intent
import app.fynlo.MainActivity

/**
 * Cleanly relaunches the app from scratch. Used after Reset All Data so that
 * all in-memory state (Hilt singletons, Firestore listeners, ViewModels) is
 * rebuilt from the now-empty database and preferences.
 *
 * Starts a fresh [MainActivity] task (clearing the back stack) and then kills
 * the current process — the equivalent of ProcessPhoenix without the extra
 * dependency.
 */
object AppRestarter {
    fun restart(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
