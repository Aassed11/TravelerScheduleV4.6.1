package com.traveler.schedule.data

class ScheduleRepository(
    val scheduleDao: ScheduleDao,
    val taskDao: TaskDao
)
