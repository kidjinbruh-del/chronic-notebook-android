package ru.chronicnotebook.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ru.chronicnotebook.data.IntakeEntity

/**
 * Обёртка над AlarmManager.
 *
 * Раньше здесь был вызов setExactAndAllowWhileIdle, а при отказе — setWindow на
 * 10 минут. На агрессивных прошивках (Infinix XOS, HiSense) точные будильники
 * запрещены по умолчанию, и setWindow растягивался системой на часы: будильник
 * не срабатывал, пока пользователь сам не открывал приложение (тогда
 * rescheduleAll ставил просроченному приёму alarm через секунду).
 *
 * Теперь основной механизм — setAlarmClock. Это единственный тип будильника,
 * который гарантированно срабатывает в Doze, показывает значок будильника в
 * строке состояния и не зависит от оптимизации батареи.
 */
object AlarmScheduler {
    const val ACTION_INTAKE = "ru.chronicnotebook.ALARM_INTAKE"
    const val ACTION_MEASURE = "ru.chronicnotebook.ALARM_MEASURE"
    const val EXTRA_ID = "intake_id"
    const val EXTRA_SLOT = "slot"

    fun scheduleExact(context: Context, intake: IntakeEntity, delayMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000)
        val pending = pendingIntent(context, intake.id)

        // 1. setAlarmClock — максимальная надёжность, работает в Doze.
        if (runCatching {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(context)), pending)
            }.isSuccess
        ) {
            return
        }
        // 2. Точный будильник, если прошивка разрешает.
        if (canScheduleExact(manager)) {
            if (runCatching {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }.isSuccess
            ) {
                return
            }
        }
        // 3. Неточный, но с будильным приоритетом: работает в Doze на старых
        // версиях, где setAlarmClock может быть недоступен.
        if (runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }.isSuccess
        ) {
            Log.w(TAG, "Использован неточный будильник, система может задержать")
            return
        }
        // 4. Последний вариант — обычный будильник без гарантии времени.
        runCatching { manager.set(AlarmManager.RTC_WAKEUP, at, pending) }
            .onFailure { Log.w(TAG, "Не удалось поставить будильник", it) }
    }

    /** Значок будильника в строке состояния: по нему видно, что напоминание активно. */
    private fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, ru.chronicnotebook.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
}
