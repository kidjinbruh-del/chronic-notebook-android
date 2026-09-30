package ru.chronicnotebook.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.chronicnotebook.App
import ru.chronicnotebook.data.IntakeScheduler
import ru.chronicnotebook.reminders.AlarmScheduler
import ru.chronicnotebook.reminders.Escalator
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class WeatherSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val container = app.container
        return try {
            val now = container.weather.fetchNow()
            if (now != null) container.db.weatherDao().upsert(now)
            val archive = container.weather.fetchArchive()
            if (archive.isNotEmpty()) container.db.weatherDao().upsertAll(archive)
            container.settings.setLastSync(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class DailyScheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        return try {
            IntakeScheduler(app).rescheduleAll()
            Escalator(app).catchUp()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ChronicNotebook", "Планировщик не отработал", e)
            Result.retry()
        }
    }
}

/**
 * Страховка доставки. Некоторые прошивки (Infinix XOS, HiSense, MIUI) вычищают
 * будильники и не доставляют точные alarm без открытия приложения. Раз в 15
 * минут (минимальный интервал WorkManager) перевзводим ближайшие приёмы и
 * досылаем просроченные, чтобы напоминание о лекарстве пришло в любом случае.
 */
class IntakeWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        return try {
            IntakeScheduler(app).rearmSoon()
            Escalator(app).catchUp()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ChronicNotebook", "Страховка будильников не отработала", e)
            Result.retry()
        }
    }
}

object Scheduler {
    private const val SYNC = "weather_sync"
    private const val DAILY = "daily_schedule"
    private const val WATCHDOG = "intake_watchdog"

    fun start(context: Context) {
        val manager = WorkManager.getInstance(context)
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        manager.enqueueUniquePeriodicWork(
            SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeatherSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(network)
                .setInitialDelay(Duration.ofMinutes(2))
                .build(),
        )
        manager.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyScheduleWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.ofMinutes(5))
                .build(),
        )
        // 15 минут — минимальный период, который разрешает WorkManager.
        // Именно KEEP, а не UPDATE: UPDATE сбрасывал бы отсчёт при каждом
        // запуске приложения, и при частом открытии страховка не срабатывала бы.
        manager.enqueueUniquePeriodicWork(
            WATCHDOG,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<IntakeWatchdogWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(Duration.ofMinutes(1))
                .build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$SYNC-once",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )
    }

    fun scheduleMeasureHints(context: Context) {
        AlarmScheduler.scheduleMeasureHint(context, "утро", System.currentTimeMillis() + untilMillis(8, 30))
        AlarmScheduler.scheduleMeasureHint(context, "вечер", System.currentTimeMillis() + untilMillis(20, 30))
    }

    /**
     * Milliseconds until the next occurrence of hour:minute, tomorrow if it passed.
     *
     * Раньше здесь была отдельная ветка «если уже после 21:00, поставить утро на
     * завтра». Она была лишней и вредной: [untilMillis] и так возвращает ближайшие
     * 08:30, то есть завтрашние, а добавка в сутки сдвигала утреннюю подсказку на
     * послезавтра. PendingIntent у обеих установок один и тот же, поэтому второе
     * значение просто перезаписывало первое, и вечером открытое приложение
     * молча отменяло напоминание о замере на следующее утро.
     */
    private fun untilMillis(hour: Int, minute: Int): Long {
        val target = LocalTime.of(hour, minute)
        val now = LocalTime.now()
        val diff = java.time.Duration.between(now, target)
        return if (diff.isNegative) Duration.ofDays(1).toMillis() + diff.toMillis() else diff.toMillis()
    }
}
