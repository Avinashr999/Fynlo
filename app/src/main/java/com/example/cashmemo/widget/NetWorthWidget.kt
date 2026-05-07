package com.example.cashmemo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.cashmemo.CashMemoApplication
import com.example.cashmemo.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale

class NetWorthWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app     = context.applicationContext as CashMemoApplication
                val accounts = app.database.dao().getAllAccounts().first()
                val totalCash = accounts.sumOf { it.balance }
                val locale   = Locale.getDefault()
                val formatted = "₹${String.format(locale, "%,.0f", totalCash)}"

                val views = RemoteViews(context.packageName, R.layout.widget_net_worth)
                views.setTextViewText(R.id.widget_net_worth_value, formatted)
                views.setTextViewText(R.id.widget_label, "Net Worth")

                manager.updateAppWidget(id, views)
            } catch (e: Exception) {
                android.util.Log.e("Widget", "Update failed: ${e.message}")
            }
        }
    }
}
