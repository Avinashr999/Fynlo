package com.example.cashmemo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cashmemo.CashMemoApplication
import com.example.cashmemo.R
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID   = "cashmemo_reminders"
        const val CHANNEL_NAME = "Loan & Due Date Reminders"
    }

    override suspend fun doWork(): Result {
        createChannel()
        val app     = context.applicationContext as CashMemoApplication
        val dao     = app.database.dao()
        val today   = LocalDate.now()
        val fmt     = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var notifId = 1000

        // Check overdue borrowers
        val borrowers = dao.getAllBorrowers().first()
        borrowers.filter { it.status != "Cleared" && it.due.isNotBlank() }.forEach { b ->
            runCatching {
                val due     = LocalDate.parse(b.due, fmt)
                val daysLeft = ChronoUnit.DAYS.between(today, due)
                when {
                    daysLeft < 0 -> notify(notifId++, "Loan Overdue",
                        "${b.name}'s loan of Rs${b.amount.toLong()} is overdue by ${-daysLeft} days")
                    daysLeft <= 3 -> notify(notifId++, "Loan Due Soon",
                        "${b.name}'s loan of Rs${b.amount.toLong()} is due in $daysLeft days")
                    daysLeft == 7L -> notify(notifId++, "Loan Due in 1 Week",
                        "Reminder: ${b.name} owes Rs${b.amount.toLong()}, due on ${b.due}")
                }
            }
        }

        // Check overdue debts
        val debts = dao.getAllDebts().first()
        debts.filter { it.status != "Cleared" && it.due.isNotBlank() }.forEach { d ->
            runCatching {
                val due      = LocalDate.parse(d.due, fmt)
                val daysLeft = ChronoUnit.DAYS.between(today, due)
                when {
                    daysLeft < 0 -> notify(notifId++, "Debt Payment Overdue",
                        "Your debt to ${d.name} of Rs${d.amount.toLong()} is overdue by ${-daysLeft} days")
                    daysLeft <= 3 -> notify(notifId++, "Debt Payment Due",
                        "Pay Rs${d.amount.toLong()} to ${d.name} in $daysLeft days")
                }
            }
        }

        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Reminders for loan due dates and overdue payments"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun notify(id: Int, title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notif)
    }
}