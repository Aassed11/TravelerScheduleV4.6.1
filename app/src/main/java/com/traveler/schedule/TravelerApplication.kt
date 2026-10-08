package com.traveler.schedule

import android.app.Application
import com.traveler.schedule.data.AppDatabase
import com.traveler.schedule.data.ScheduleRepository

class TravelerApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { ScheduleRepository(database.scheduleDao(), database.taskDao()) }
}
