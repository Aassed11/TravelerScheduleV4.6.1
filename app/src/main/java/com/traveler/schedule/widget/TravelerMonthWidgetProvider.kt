package com.traveler.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.traveler.schedule.R
import com.traveler.schedule.data.AppDatabase
import com.traveler.schedule.data.ScheduleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class TravelerMonthWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val PREFS = "traveler_month_widget"
        private const val ACTION_PREV = "com.traveler.schedule.widget.PREV_MONTH"
        private const val ACTION_NEXT = "com.traveler.schedule.widget.NEXT_MONTH"
        private const val ACTION_TODAY = "com.traveler.schedule.widget.TODAY_MONTH"

        private val cellIds = arrayOf(
        R.id.widget_cell_1 to (R.id.widget_day_1 to R.id.widget_loc_1),
        R.id.widget_cell_2 to (R.id.widget_day_2 to R.id.widget_loc_2),
        R.id.widget_cell_3 to (R.id.widget_day_3 to R.id.widget_loc_3),
        R.id.widget_cell_4 to (R.id.widget_day_4 to R.id.widget_loc_4),
        R.id.widget_cell_5 to (R.id.widget_day_5 to R.id.widget_loc_5),
        R.id.widget_cell_6 to (R.id.widget_day_6 to R.id.widget_loc_6),
        R.id.widget_cell_7 to (R.id.widget_day_7 to R.id.widget_loc_7),
        R.id.widget_cell_8 to (R.id.widget_day_8 to R.id.widget_loc_8),
        R.id.widget_cell_9 to (R.id.widget_day_9 to R.id.widget_loc_9),
        R.id.widget_cell_10 to (R.id.widget_day_10 to R.id.widget_loc_10),
        R.id.widget_cell_11 to (R.id.widget_day_11 to R.id.widget_loc_11),
        R.id.widget_cell_12 to (R.id.widget_day_12 to R.id.widget_loc_12),
        R.id.widget_cell_13 to (R.id.widget_day_13 to R.id.widget_loc_13),
        R.id.widget_cell_14 to (R.id.widget_day_14 to R.id.widget_loc_14),
        R.id.widget_cell_15 to (R.id.widget_day_15 to R.id.widget_loc_15),
        R.id.widget_cell_16 to (R.id.widget_day_16 to R.id.widget_loc_16),
        R.id.widget_cell_17 to (R.id.widget_day_17 to R.id.widget_loc_17),
        R.id.widget_cell_18 to (R.id.widget_day_18 to R.id.widget_loc_18),
        R.id.widget_cell_19 to (R.id.widget_day_19 to R.id.widget_loc_19),
        R.id.widget_cell_20 to (R.id.widget_day_20 to R.id.widget_loc_20),
        R.id.widget_cell_21 to (R.id.widget_day_21 to R.id.widget_loc_21),
        R.id.widget_cell_22 to (R.id.widget_day_22 to R.id.widget_loc_22),
        R.id.widget_cell_23 to (R.id.widget_day_23 to R.id.widget_loc_23),
        R.id.widget_cell_24 to (R.id.widget_day_24 to R.id.widget_loc_24),
        R.id.widget_cell_25 to (R.id.widget_day_25 to R.id.widget_loc_25),
        R.id.widget_cell_26 to (R.id.widget_day_26 to R.id.widget_loc_26),
        R.id.widget_cell_27 to (R.id.widget_day_27 to R.id.widget_loc_27),
        R.id.widget_cell_28 to (R.id.widget_day_28 to R.id.widget_loc_28),
        R.id.widget_cell_29 to (R.id.widget_day_29 to R.id.widget_loc_29),
        R.id.widget_cell_30 to (R.id.widget_day_30 to R.id.widget_loc_30),
        R.id.widget_cell_31 to (R.id.widget_day_31 to R.id.widget_loc_31),
        R.id.widget_cell_32 to (R.id.widget_day_32 to R.id.widget_loc_32),
        R.id.widget_cell_33 to (R.id.widget_day_33 to R.id.widget_loc_33),
        R.id.widget_cell_34 to (R.id.widget_day_34 to R.id.widget_loc_34),
        R.id.widget_cell_35 to (R.id.widget_day_35 to R.id.widget_loc_35),
        R.id.widget_cell_36 to (R.id.widget_day_36 to R.id.widget_loc_36),
        R.id.widget_cell_37 to (R.id.widget_day_37 to R.id.widget_loc_37),
        R.id.widget_cell_38 to (R.id.widget_day_38 to R.id.widget_loc_38),
        R.id.widget_cell_39 to (R.id.widget_day_39 to R.id.widget_loc_39),
        R.id.widget_cell_40 to (R.id.widget_day_40 to R.id.widget_loc_40),
        R.id.widget_cell_41 to (R.id.widget_day_41 to R.id.widget_loc_41),
        R.id.widget_cell_42 to (R.id.widget_day_42 to R.id.widget_loc_42)
        )

        private fun monthKey(id: Int) = "month_$id"
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
        val action = intent.action
        if (action == ACTION_PREV || action == ACTION_NEXT || action == ACTION_TODAY) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val current = readMonth(prefs.getString(monthKey(id), null))
                val target = when (action) {
                    ACTION_PREV -> current.minusMonths(1)
                    ACTION_NEXT -> current.plusMonths(1)
                    else -> YearMonth.now()
                }
                prefs.edit().putString(monthKey(id), target.toString()).apply()
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove(monthKey(it)) }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        schedules: List<ScheduleEntity>
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val month = readMonth(prefs.getString(monthKey(appWidgetId), null))
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.setTextViewText(R.id.widget_month_title, month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)))

        views.setOnClickPendingIntent(R.id.widget_prev, actionPendingIntent(context, appWidgetId, ACTION_PREV, 1))
        views.setOnClickPendingIntent(R.id.widget_next, actionPendingIntent(context, appWidgetId, ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_today, actionPendingIntent(context, appWidgetId, ACTION_TODAY, 3))
        views.setOnClickPendingIntent(R.id.widget_month_title, TravelerWidgetUtils.openCalendarPendingIntent(context, appWidgetId * 1000 + 4))

        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7
        val gridStart = first.minusDays(leading.toLong())
        val today = LocalDate.now()

        cellIds.forEachIndexed { index, (cellId, ids) ->
            val (dayId, locId) = ids
            val date = gridStart.plusDays(index.toLong())
            val daySchedules = schedules.filter { TravelerWidgetUtils.coversDate(it, date) }
            val inMonth = YearMonth.from(date) == month
            val isToday = date == today

            views.setTextViewText(dayId, date.dayOfMonth.toString())
            views.setTextViewText(locId, TravelerWidgetUtils.locationLabel(daySchedules))
            views.setTextColor(dayId, when {
                isToday -> Color.parseColor("#C43130")
                !inMonth -> Color.parseColor("#B5BBC4")
                date.dayOfWeek.value == 7 -> Color.parseColor("#C43130")
                date.dayOfWeek.value == 6 -> Color.parseColor("#2C82C9")
                else -> Color.parseColor("#1D2939")
            })
            views.setTextColor(locId, if (daySchedules.isNotEmpty()) statusColor(daySchedules.first().status) else Color.TRANSPARENT)
            views.setInt(cellId, "setBackgroundResource", if (isToday) R.drawable.widget_today_bg else 0)
            views.setOnClickPendingIntent(
                cellId,
                TravelerWidgetUtils.openDatePendingIntent(context, date, daySchedules.isNotEmpty(), appWidgetId * 100 + index)
            )
        }

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun actionPendingIntent(context: Context, widgetId: Int, action: String, salt: Int): PendingIntent {
        val intent = Intent(context, TravelerMonthWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId * 10 + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun readMonth(value: String?): YearMonth = runCatching { YearMonth.parse(value) }.getOrDefault(YearMonth.now())

    private fun statusColor(status: String): Int = when (status) {
        "견적" -> Color.parseColor("#667085")
        "예약" -> Color.parseColor("#2C82C9")
        "확정" -> Color.parseColor("#7A5AF8")
        "진행중" -> Color.parseColor("#C43130")
        "완료" -> Color.parseColor("#168A5B")
        "취소" -> Color.parseColor("#98A2B3")
        else -> Color.parseColor("#667085")
    }
}
