package com.traveler.schedule.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "schedules", indices = [Index("cloudId")])
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val groupName: String = "",
    val location: String = "",
    val people: Int = 0,
    val startDate: String,
    val endDate: String,
    val time: String = "",
    val memo: String = "",
    val status: String = "견적",
    val priority: String = "일반",
    val reminderDays: Int = 1,
    val manager: String = "",
    val flight: String = "",
    val hotel: String = "",
    val guide: String = "",
    val vehicle: String = "",
    val landCompany: String = "",
    val customerPhone: String = "",
    val ticketDeadline: String = "",
    val hotelCancelDeadline: String = "",
    val balanceDueDate: String = "",
    val passportCheckDate: String = "",
    val favorite: Boolean = false,
    val iconKey: String = "flight",
    val colorKey: String = "blue",
    val deletedAt: Long = 0L,
    val cloudId: String = "",
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = ScheduleEntity::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("scheduleId"), Index("cloudId")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val title: String,
    val completed: Boolean = false,
    val category: String = "업무",
    val dueDate: String = "",
    val cloudId: String = "",
    val updatedAt: Long = 0L
)
