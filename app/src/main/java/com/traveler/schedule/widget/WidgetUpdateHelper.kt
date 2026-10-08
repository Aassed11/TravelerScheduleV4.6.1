package com.traveler.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object WidgetUpdateHelper {
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        listOf(
            TravelerMonthWidgetProvider::class.java,
            TravelerUpcomingWidgetProvider::class.java
        ).forEach { providerClass ->
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, providerClass)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(appContext, providerClass).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                appContext.sendBroadcast(intent)
            }
        }
    }
}
