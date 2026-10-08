package com.traveler.schedule.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.traveler.schedule.data.ScheduleEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object TravelerWidgetUtils {
    const val EXTRA_OPEN_TAB = "widget_open_tab"
    const val EXTRA_SELECTED_DATE = "widget_selected_date"
    const val EXTRA_SCHEDULE_ID = "widget_schedule_id"
    const val EXTRA_AUTO_OPEN_DATE = "widget_auto_open_date"
    const val EXTRA_DATE_HAS_SCHEDULE = "widget_date_has_schedule"

    fun coversDate(item: ScheduleEntity, date: LocalDate): Boolean {
        val start = parseDate(item.startDate) ?: return false
        val end = parseDate(item.endDate) ?: start
        return !date.isBefore(start) && !date.isAfter(end)
    }

    fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    fun locationLabel(items: List<ScheduleEntity>): String {
        if (items.isEmpty()) return ""
        val first = items.first().location.ifBlank { items.first().title }
        return if (items.size > 1) "$first +${items.size - 1}" else first
    }

    fun openDatePendingIntent(context: Context, date: LocalDate, hasSchedule: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent().setClassName(context, "com.traveler.schedule.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, "calendar")
            putExtra(EXTRA_SELECTED_DATE, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            putExtra(EXTRA_AUTO_OPEN_DATE, true)
            putExtra(EXTRA_DATE_HAS_SCHEDULE, hasSchedule)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun openSchedulePendingIntent(context: Context, scheduleId: Long, requestCode: Int): PendingIntent {
        val intent = Intent().setClassName(context, "com.traveler.schedule.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun openCalendarPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent().setClassName(context, "com.traveler.schedule.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, "calendar")
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
