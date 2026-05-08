package app.fynlo.notifications

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK_NAME           = "cashmemo_daily_reminder"
    private const val RECURRING_WORK_NAME = "cashmemo_recurring"

    fun schedule(context: Context) {
        // Daily reminders (loan dues, budget alerts)
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, reminderRequest
        )

        // Recurring transactions (runs every morning at next opportunity)
        val recurringRequest = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(0, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECURRING_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, recurringRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(RECURRING_WORK_NAME)
    }
}