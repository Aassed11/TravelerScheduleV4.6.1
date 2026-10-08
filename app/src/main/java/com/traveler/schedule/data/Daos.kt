package com.traveler.schedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE deletedAt = 0 ORDER BY startDate, time")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE deletedAt = 0 ORDER BY startDate, time")
    suspend fun getAllOnce(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules ORDER BY startDate, time")
    suspend fun getAllIncludingDeletedOnce(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    suspend fun getTrashOnce(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getByCloudId(cloudId: String): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduleEntity): Long

    @Update
    suspend fun update(item: ScheduleEntity)

    @Delete
    suspend fun delete(item: ScheduleEntity)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}

@Dao
interface TaskDao {
    @Query("""
        SELECT tasks.* FROM tasks
        INNER JOIN schedules ON schedules.id = tasks.scheduleId
        WHERE schedules.deletedAt = 0
        ORDER BY tasks.completed, tasks.dueDate, tasks.id
    """)
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("""
        SELECT tasks.* FROM tasks
        INNER JOIN schedules ON schedules.id = tasks.scheduleId
        WHERE schedules.deletedAt = 0
        ORDER BY tasks.completed, tasks.dueDate, tasks.id
    """)
    suspend fun getAllOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY completed, dueDate, id")
    suspend fun getAllIncludingDeletedOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE scheduleId = :scheduleId ORDER BY completed, dueDate, id")
    fun observeForSchedule(scheduleId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduleId = :scheduleId ORDER BY completed, dueDate, id")
    suspend fun getForScheduleOnce(scheduleId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getByCloudId(cloudId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TaskEntity): Long

    @Update
    suspend fun update(item: TaskEntity)

    @Delete
    suspend fun delete(item: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
