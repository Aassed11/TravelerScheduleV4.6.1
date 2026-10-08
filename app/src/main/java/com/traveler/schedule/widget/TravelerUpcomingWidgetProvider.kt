package com.traveler.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.traveler.schedule.R
import com.traveler.schedule.data.AppDatabase
import com.traveler.schedule.data.ScheduleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TravelerUpcomingWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_REFRESH = "com.traveler.schedule.widget.REFRESH_UPCOMING"
        private val rowIds = intArrayOf(
            R.id.upcoming_row_1, R.id.upcoming_row_2, R.id.upcoming_row_3,
            R.id.upcoming_row_4, R.id.upcoming_row_5
        )
        private val titleIds = intArrayOf(
            R.id.upcoming_title_1, R.id.upcoming_title_2, R.id.upcoming_title_3,
            R.id.upcoming_title_4, R.id.upcoming_title_5
        )
        private val subtitleIds = intArrayOf(
            R.id.upcoming_subtitle_1, R.id.upcoming_subtitle_2, R.id.upcoming_subtitle_3,
            R.id.upcoming_subtitle_4, R.id.upcoming_subtitle_5
        )
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = AppDatabase.getInstance(context).scheduleDao().getAllOnce()
                appWidgetIds.forEach { id -> updateWidget(context, manager, id, schedules) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val schedules = AppDatabase.getInstance(context).scheduleDao().getAllOnce()
                        updateWidget(context, AppWidgetManager.getInstance(context), id, schedules)
                    } finally {
                        pending.finish()
                    }
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        schedules: List<ScheduleEntity>
    ) {
        val today = LocalDate.now()
        val upcoming = schedules
            .filter { item ->
                item.status !in listOf("완료", "취소") &&
                    (TravelerWidgetUtils.parseDate(item.endDate) ?: TravelerWidgetUtils.parseDate(item.startDate))
                        ?.let { !it.isBefore(today) } == true
            }
            .sortedBy { TravelerWidgetUtils.parseDate(it.startDate) ?: LocalDate.MAX }
            .take(5)

        val views = RemoteViews(context.packageName, R.layout.widget_upcoming)
        views.setOnClickPendingIntent(R.id.upcoming_header, TravelerWidgetUtils.openCalendarPendingIntent(context, appWidgetId * 1000 + 50))
        views.setOnClickPendingIntent(R.id.upcoming_refresh, refreshPendingIntent(context, appWidgetId))
        views.setViewVisibility(R.id.upcoming_empty, if (upcoming.isEmpty()) View.VISIBLE else View.GONE)
        if (upcoming.isEmpty()) {
            views.setOnClickPendingIntent(R.id.upcoming_empty, TravelerWidgetUtils.openCalendarPendingIntent(context, appWidgetId * 1000 + 51))
        }

        rowIds.indices.forEach { index ->
            val item = upcoming.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(rowIds[index], View.GONE)
            } else {
                views.setViewVisibility(rowIds[index], View.VISIBLE)
                val date = TravelerWidgetUtils.parseDate(item.startDate)
                val dateText = date?.format(DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)) ?: item.startDate
                views.setTextViewText(titleIds[index], "$dateText  ${item.title}")
                val details = listOf(item.location, item.status).filter { it.isNotBlank() }.joinToString(" · ")
                views.setTextViewText(subtitleIds[index], details)
                views.setOnClickPendingIntent(
                    rowIds[index],
                    TravelerWidgetUtils.openSchedulePendingIntent(context, item.id, appWidgetId * 100 + index + 60)
                )
            }
        }

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun refreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, TravelerUpcomingWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId * 10 + 9,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
