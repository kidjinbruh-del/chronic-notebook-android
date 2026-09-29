package ru.chronicnotebook.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.chronicnotebook.data.IntakeEntity

/**
 * Обёртка над AlarmManager. На части прошивок (Infinix XOS, HiSense) вызов
 * setExactAndAllowWhileIdle бросает SecurityException даже после проверки
 * разрешения, поэтому любое исключение гасится и устройство переходит
 * на неточное расписание вместо падения.
 */
object AlarmScheduler {
    const val ACTION_INTAKE = "ru.chronicnotebook.ALARM_INTAKE"
    const val ACTION_MEASURE = "ru.chronicnotebook.ALARM_MEASURE"
    const val EXTRA_ID = "intake_id"
    const val EXTRA_SLOT = "slot"

    fun scheduleExact(context: Context, intake: IntakeEntity, delayMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000)
        try {
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, intake.id))
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pendingIntent(context, intake.id))
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Точный будильник недоступен, ставлю окно", e)
            runCatching {
                manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pendingIntent(context, intake.id))
            }
        }
    }

    fun scheduleMeasureHint(context: Context, slot: String, atMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEASURE
            putExtra(EXTRA_SLOT, slot)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            slot.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Будильник замера недоступен", e)
            runCatching { manager.set(AlarmManager.RTC_WAKEUP, atMillis, pending) }
        }
    }

    fun cancel(context: Context, intakeId: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(context, intakeId)) }
    }

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return canScheduleExact(manager)
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

    private fun pendingIntent(context: Context, intakeId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_INTAKE
            putExtra(EXTRA_ID, intakeId)
        }
        return PendingIntent.getBroadcast(
            context,
            intakeId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "ChronicNotebook"
    private const val WINDOW_MILLIS = 10L * 60_000
}
