package com.openjarvis.automation

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AutomationManager(private val context: Context) {

    private val db = AutomationDB.getInstance(context)
    private val dao = db.automationDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _automationsFlow =
        MutableStateFlow<List<Automation>>(emptyList())

    val automationsFlow: StateFlow<List<Automation>> = _automationsFlow

    suspend fun loadAutomations() {
        _automationsFlow.value =
            dao.getAll().map { it.toAutomation() }
    }

    suspend fun createAutomation(automation: Automation): String =
        withContext(Dispatchers.IO) {
            dao.insert(automation.toEntity())

            if (automation.enabled) {
                scheduleAutomation(automation)
            }

            _automationsFlow.value =
                dao.getAll().map { it.toAutomation() }

            automation.id
        }

    suspend fun updateAutomation(automation: Automation) =
        withContext(Dispatchers.IO) {
            dao.update(automation.toEntity())
            cancelAutomation(automation.id)

            if (automation.enabled) {
                scheduleAutomation(automation)
            }

            _automationsFlow.value =
                dao.getAll().map { it.toAutomation() }
        }

    suspend fun deleteAutomation(id: String) =
        withContext(Dispatchers.IO) {
            cancelAutomation(id)
            dao.delete(id)

            _automationsFlow.value =
                dao.getAll().map { it.toAutomation() }
        }

    suspend fun toggleAutomation(id: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val automation =
                dao.getById(id)?.toAutomation() ?: return@withContext

            val updated = automation.copy(enabled = enabled)
            dao.update(updated.toEntity())

            if (enabled) {
                scheduleAutomation(updated)
            } else {
                cancelAutomation(id)
            }

            _automationsFlow.value =
                dao.getAll().map { it.toAutomation() }
        }

    suspend fun runNow(id: String) =
        withContext(Dispatchers.IO) {
            val automation =
                dao.getById(id)?.toAutomation() ?: return@withContext

            executeAutomation(automation)
        }

    private suspend fun scheduleAutomation(automation: Automation) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val inputData = workDataOf(
            "automation_id" to automation.id,
            "automation_command" to automation.command
        )

        when (val schedule = automation.schedule) {

            is AutomationSchedule.Daily -> {
                val request =
                    PeriodicWorkRequestBuilder<AutomationWorker>(
                        24, TimeUnit.HOURS,
                        15, TimeUnit.MINUTES
                    )
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .setInitialDelay(
                            calculateDelay(schedule.hour, schedule.minute),
                            TimeUnit.MILLISECONDS
                        )
                        .addTag(automation.id)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        automation.id,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
            }

            is AutomationSchedule.Weekly -> {
                val request =
                    PeriodicWorkRequestBuilder<AutomationWorker>(
                        7, TimeUnit.DAYS,
                        15, TimeUnit.MINUTES
                    )
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .setInitialDelay(
                            calculateWeeklyDelay(
                                schedule.dayOfWeek,
                                schedule.hour,
                                schedule.minute
                            ),
                            TimeUnit.MILLISECONDS
                        )
                        .addTag(automation.id)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        automation.id,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
            }

            is AutomationSchedule.Interval -> {
                val interval = maxOf(
                    schedule.intervalMs,
                    15 * 60 * 1000L
                )

                val request =
                    PeriodicWorkRequestBuilder<AutomationWorker>(
                        interval,
                        TimeUnit.MILLISECONDS
                    )
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .addTag(automation.id)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        automation.id,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
            }

            is AutomationSchedule.Once -> {
                val delay =
                    schedule.atMs - System.currentTimeMillis()

                if (delay <= 0) return

                val request =
                    OneTimeWorkRequestBuilder<AutomationWorker>()
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(automation.id)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        automation.id,
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
            }
        }
    }

    private fun cancelAutomation(id: String) {
        WorkManager.getInstance(context)
            .cancelAllWorkByTag(id)
    }

    private suspend fun executeAutomation(automation: Automation) {
        val result = try {
            "success"
        } catch (e: Exception) {
            "error: ${e.message}"
        }

        val updated = automation.copy(
            lastRun = System.currentTimeMillis(),
            lastResult = result,
            runCount = automation.runCount + 1
        )

        dao.update(updated.toEntity())
    }

    private fun calculateDelay(
        targetHour: Int,
        targetMinute: Int
    ): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, targetHour)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        var delay = calendar.timeInMillis - now

        if (delay <= 0) {
            delay += 24 * 60 * 60 * 1000L
        }

        return delay
    }

    private fun calculateWeeklyDelay(
        dayOfWeek: Int,
        targetHour: Int,
        targetMinute: Int
    ): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, targetHour)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        var delay = calendar.timeInMillis - now

        if (delay <= 0) {
            delay += 7 * 24 * 60 * 60 * 1000L
        }

        return delay
    }

    fun parseSchedule(input: String): AutomationSchedule? {
        val lower = input.lowercase()

        val dailyMatch = Regex(
            """every day at (\d{1,2})(?::(\d{2}))?\s*(am|pm)?""",
            RegexOption.IGNORE_CASE
        ).find(lower)

        if (dailyMatch != null) {
            var hour = dailyMatch.groupValues[1].toInt()
            val minute =
                dailyMatch.groupValues[2].toIntOrNull() ?: 0

            val isPM =
                dailyMatch.groupValues[3].lowercase() == "pm"

            if (isPM && hour != 12) hour += 12
            if (!isPM && hour == 12) hour = 0

            if (hour !in 0..23 || minute !in 0..59) {
                return null
            }

            return AutomationSchedule.Daily(hour, minute)
        }

        val intervalMatch = Regex(
            """every (\d+)\s*(minute|hour|day)s?""",
            RegexOption.IGNORE_CASE
        ).find(lower)

        if (intervalMatch != null) {
            val value =
                intervalMatch.groupValues[1].toLongOrNull()
                    ?: return null

            if (value <= 0) return null

            val unit = intervalMatch.groupValues[2]

            val multiplier = when (unit) {
                "minute" -> 60 * 1000L
                "hour" -> 60 * 60 * 1000L
                "day" -> 24 * 60 * 60 * 1000L
                else -> return null
            }

            val ms = value * multiplier

            return AutomationSchedule.Interval(ms)
        }

        return null
    }

    data class Automation(
        val id: String,
        val name: String,
        val command: String,
        val schedule: AutomationSchedule,
        val enabled: Boolean = true,
        val lastRun: Long? = null,
        val lastResult: String? = null,
        val runCount: Int = 0
    )

    sealed class AutomationSchedule {

        data class Daily(
            val hour: Int,
            val minute: Int
        ) : AutomationSchedule()

        data class Weekly(
            val dayOfWeek: Int,
            val hour: Int,
            val minute: Int
        ) : AutomationSchedule()

        data class Interval(
            val intervalMs: Long
        ) : AutomationSchedule()

        data class Once(
            val atMs: Long
        ) : AutomationSchedule()
    }
}