package com.traveler.schedule.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseCloudSync(
    private val repository: ScheduleRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    data class Result(val schedules: Int, val tasks: Int)

    private fun uid(): String = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
    private fun userRef() = firestore.collection("users").document(uid())

    suspend fun syncAll(): Result {
        applyTombstones()
        ensureLocalCloudIds()
        pullRemoteSnapshot()
        pushLocalSnapshot()
        val schedules = repository.scheduleDao.getAllOnce().size
        val tasks = repository.taskDao.getAllOnce().size
        return Result(schedules, tasks)
    }

    suspend fun pushSchedule(schedule: ScheduleEntity) {
        if (schedule.cloudId.isBlank()) return
        userRef().collection("schedules").document(schedule.cloudId)
            .set(schedule.toCloudMap()).await()
    }

    suspend fun pushTask(task: TaskEntity) {
        if (task.cloudId.isBlank()) return
        val schedule = repository.scheduleDao.getById(task.scheduleId) ?: return
        if (schedule.cloudId.isBlank()) return
        userRef().collection("schedules").document(schedule.cloudId)
            .collection("tasks").document(task.cloudId)
            .set(task.toCloudMap()).await()
    }

    suspend fun deleteTask(task: TaskEntity) {
        if (task.cloudId.isBlank()) return
        val schedule = repository.scheduleDao.getById(task.scheduleId) ?: return
        if (schedule.cloudId.isBlank()) return
        userRef().collection("deletedTasks").document(task.cloudId)
            .set(mapOf("scheduleCloudId" to schedule.cloudId, "updatedAt" to System.currentTimeMillis())).await()
        userRef().collection("schedules").document(schedule.cloudId)
            .collection("tasks").document(task.cloudId)
            .delete().await()
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        if (schedule.cloudId.isBlank()) return
        userRef().collection("deletedSchedules").document(schedule.cloudId)
            .set(mapOf("updatedAt" to System.currentTimeMillis())).await()
        val ref = userRef().collection("schedules").document(schedule.cloudId)
        val taskDocs = ref.collection("tasks").get().await()
        taskDocs.documents.forEach { it.reference.delete().await() }
        ref.delete().await()
    }


    private suspend fun applyTombstones() {
        val deletedSchedules = userRef().collection("deletedSchedules").get().await()
        for (doc in deletedSchedules.documents) {
            repository.scheduleDao.getByCloudId(doc.id)?.let { repository.scheduleDao.delete(it) }
        }
        val deletedTasks = userRef().collection("deletedTasks").get().await()
        for (doc in deletedTasks.documents) {
            repository.taskDao.getByCloudId(doc.id)?.let { repository.taskDao.delete(it) }
        }
    }
    private suspend fun ensureLocalCloudIds() {
        val now = System.currentTimeMillis()
        repository.scheduleDao.getAllIncludingDeletedOnce().forEach { item ->
            if (item.cloudId.isBlank()) {
                repository.scheduleDao.update(item.copy(cloudId = UUID.randomUUID().toString(), updatedAt = now))
            }
        }
        repository.taskDao.getAllIncludingDeletedOnce().forEach { item ->
            if (item.cloudId.isBlank()) {
                repository.taskDao.update(item.copy(cloudId = UUID.randomUUID().toString(), updatedAt = now))
            }
        }
    }

    private suspend fun pushLocalSnapshot() {
        repository.scheduleDao.getAllIncludingDeletedOnce().forEach { schedule ->
            pushSchedule(schedule)
            repository.taskDao.getForScheduleOnce(schedule.id).forEach { pushTask(it) }
        }
    }

    private suspend fun pullRemoteSnapshot() {
        val remoteSchedules = userRef().collection("schedules").get().await()
        for (doc in remoteSchedules.documents) {
            val cloudId = doc.id
            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
            val existing = repository.scheduleDao.getByCloudId(cloudId)
            val incoming = doc.toScheduleEntity(existing?.id ?: 0L, cloudId)
            val localId = when {
                existing == null -> repository.scheduleDao.insert(incoming)
                remoteUpdatedAt > existing.updatedAt -> {
                    repository.scheduleDao.update(incoming.copy(id = existing.id))
                    existing.id
                }
                else -> existing.id
            }

            val taskDocs = doc.reference.collection("tasks").get().await()
            for (taskDoc in taskDocs.documents) {
                val taskCloudId = taskDoc.id
                val taskUpdatedAt = taskDoc.getLong("updatedAt") ?: 0L
                val localTask = repository.taskDao.getByCloudId(taskCloudId)
                val incomingTask = taskDoc.toTaskEntity(localTask?.id ?: 0L, localId, taskCloudId)
                when {
                    localTask == null -> repository.taskDao.insert(incomingTask)
                    taskUpdatedAt > localTask.updatedAt -> repository.taskDao.update(incomingTask.copy(id = localTask.id))
                }
            }
        }
    }

    private fun ScheduleEntity.toCloudMap(): Map<String, Any> = mapOf(
        "title" to title,
        "groupName" to groupName,
        "location" to location,
        "people" to people,
        "startDate" to startDate,
        "endDate" to endDate,
        "time" to time,
        "memo" to memo,
        "status" to status,
        "priority" to priority,
        "reminderDays" to reminderDays,
        "manager" to manager,
        "flight" to flight,
        "hotel" to hotel,
        "guide" to guide,
        "vehicle" to vehicle,
        "landCompany" to landCompany,
        "customerPhone" to customerPhone,
        "ticketDeadline" to ticketDeadline,
        "hotelCancelDeadline" to hotelCancelDeadline,
        "balanceDueDate" to balanceDueDate,
        "passportCheckDate" to passportCheckDate,
        "favorite" to favorite,
        "iconKey" to iconKey,
        "colorKey" to colorKey,
        "deletedAt" to deletedAt,
        "updatedAt" to updatedAt
    )

    private fun TaskEntity.toCloudMap(): Map<String, Any> = mapOf(
        "title" to title,
        "completed" to completed,
        "category" to category,
        "dueDate" to dueDate,
        "updatedAt" to updatedAt
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toScheduleEntity(id: Long, cloudId: String): ScheduleEntity =
        ScheduleEntity(
            id = id,
            title = getString("title").orEmpty(),
            groupName = getString("groupName").orEmpty(),
            location = getString("location").orEmpty(),
            people = (getLong("people") ?: 0L).toInt(),
            startDate = getString("startDate").orEmpty(),
            endDate = getString("endDate").orEmpty(),
            time = getString("time").orEmpty(),
            memo = getString("memo").orEmpty(),
            status = getString("status") ?: "견적",
            priority = getString("priority") ?: "일반",
            reminderDays = (getLong("reminderDays") ?: 1L).toInt(),
            manager = getString("manager").orEmpty(),
            flight = getString("flight").orEmpty(),
            hotel = getString("hotel").orEmpty(),
            guide = getString("guide").orEmpty(),
            vehicle = getString("vehicle").orEmpty(),
            landCompany = getString("landCompany").orEmpty(),
            customerPhone = getString("customerPhone").orEmpty(),
            ticketDeadline = getString("ticketDeadline").orEmpty(),
            hotelCancelDeadline = getString("hotelCancelDeadline").orEmpty(),
            balanceDueDate = getString("balanceDueDate").orEmpty(),
            passportCheckDate = getString("passportCheckDate").orEmpty(),
            favorite = getBoolean("favorite") ?: false,
            iconKey = getString("iconKey") ?: "flight",
            colorKey = getString("colorKey") ?: "blue",
            deletedAt = getLong("deletedAt") ?: 0L,
            cloudId = cloudId,
            updatedAt = getLong("updatedAt") ?: 0L
        )

    private fun com.google.firebase.firestore.DocumentSnapshot.toTaskEntity(id: Long, scheduleId: Long, cloudId: String): TaskEntity =
        TaskEntity(
            id = id,
            scheduleId = scheduleId,
            title = getString("title").orEmpty(),
            completed = getBoolean("completed") ?: false,
            category = getString("category") ?: "업무",
            dueDate = getString("dueDate").orEmpty(),
            cloudId = cloudId,
            updatedAt = getLong("updatedAt") ?: 0L
        )
}
