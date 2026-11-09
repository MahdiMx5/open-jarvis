package com.openjarvis.automation

import android.content.Context
import androidx.room.*
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val command: String,
    val scheduleType: String,
    val scheduleHour: Int = 0,
    val scheduleMinute: Int = 0,
    val scheduleDayOfWeek: Int = 0,
    val scheduleIntervalMs: Long = 0,
    val enabled: Boolean = true,
    val lastRun: Long? = null,
    val lastResult: String? = null,
    val runCount: Int = 0
)

@Dao
interface AutomationDao {

    @Query("SELECT * FROM automations ORDER BY name")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: AutomationEntity)

    @Update
    suspend fun update(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [AutomationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AutomationDB : RoomDatabase() {

    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile
        private var INSTANCE: AutomationDB? = null

        fun getInstance(context: Context): AutomationDB {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutomationDB::class.java,
                    "automations.db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}

internal fun AutomationManager.Automation.toEntity(): AutomationEntity {
    val schedule = this.schedule

    return AutomationEntity(
        id = id,
        name = name,
        command = command,
        scheduleType = when (schedule) {
            is AutomationManager.AutomationSchedule.Daily -> "daily"
            is AutomationManager.AutomationSchedule.Weekly -> "weekly"
            is AutomationManager.AutomationSchedule.Interval -> "interval"
            is AutomationManager.AutomationSchedule.Once -> "once"
        },
        scheduleHour = when (schedule) {
            is AutomationManager.AutomationSchedule.Daily -> schedule.hour
            is AutomationManager.AutomationSchedule.Weekly -> schedule.hour
            else -> 0
        },
        scheduleMinute = when (schedule) {
            is AutomationManager.AutomationSchedule.Daily -> schedule.minute
            is AutomationManager.AutomationSchedule.Weekly -> schedule.minute
            else -> 0
        },
        scheduleDayOfWeek = when (schedule) {
            is AutomationManager.AutomationSchedule.Weekly -> schedule.dayOfWeek
            else -> 0
        },
        scheduleIntervalMs = when (schedule) {
            is AutomationManager.AutomationSchedule.Interval -> schedule.intervalMs
            is AutomationManager.AutomationSchedule.Once -> schedule.atMs
            else -> 0L
        },
        enabled = enabled,
        lastRun = lastRun,
        lastResult = lastResult,
        runCount = runCount
    )
}

internal fun AutomationEntity.toAutomation(): AutomationManager.Automation {
    val schedule = when (scheduleType) {
        "daily" -> AutomationManager.AutomationSchedule.Daily(
            scheduleHour,
            scheduleMinute
        )

        "weekly" -> AutomationManager.AutomationSchedule.Weekly(
            scheduleDayOfWeek,
            scheduleHour,
            scheduleMinute
        )

        "interval" -> AutomationManager.AutomationSchedule.Interval(
            scheduleIntervalMs
        )

        "once" -> AutomationManager.AutomationSchedule.Once(
            scheduleIntervalMs
        )

        else -> throw IllegalStateException(
            "Unknown automation schedule type: $scheduleType"
        )
    }

    return AutomationManager.Automation(
        id = id,
        name = name,
        command = command,
        schedule = schedule,
        enabled = enabled,
        lastRun = lastRun,
        lastResult = lastResult,
        runCount = runCount
    )
}

class AutomationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("automation_id")
            ?: return Result.failure()

        return try {
            val db = AutomationDB.getInstance(applicationContext)
            val dao = db.automationDao()

            val entity = dao.getById(id)
                ?: return Result.failure()

            val automation = entity.toAutomation()

            kotlinx.coroutines.delay(2000)

            val updated = automation.copy(
                lastRun = System.currentTimeMillis(),
                lastResult = "success",
                runCount = automation.runCount + 1
            )

            dao.update(updated.toEntity())

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}