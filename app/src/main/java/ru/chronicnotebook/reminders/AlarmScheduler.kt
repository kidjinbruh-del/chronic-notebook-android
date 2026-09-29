package ru.chronicnotebook.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.chronicnotebook.data.IntakeEntity

object AlarmScheduler {
    const val ACTION_INTAKE = "ru.chronicnotebook.ALARM_INTAKE"
    const val ACTION_MEASURE = "ru.chronicnotebook.ALARM_MEASURE"
    const val EXTRA_ID = "intake_id"
    const val EXTRA_SLOT = "slot"

    fun scheduleExact(context: Context, intake: IntakeEntity, delayMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000)
        val pending = pendingIntent(context, intake.id, ACTION_INTAKE)
        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pending)
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
        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancel(context: Context, intakeId: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, intakeId, ACTION_INTAKE))
    }

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return canScheduleExact(manager)
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun pendingIntent(context: Context, intakeId: Long, action: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, intakeId)
        }
        return PendingIntent.getBroadcast(
            context,
            intakeId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
