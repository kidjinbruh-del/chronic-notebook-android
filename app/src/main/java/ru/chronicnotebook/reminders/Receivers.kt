package ru.chronicnotebook.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.chronicnotebook.App
import ru.chronicnotebook.sync.Scheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        when (intent.action) {
            AlarmScheduler.ACTION_INTAKE -> {
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
                if (id <= 0) return
                // Работа идёт внутри goAsync: иначе система успевает убить процесс
                // после возврата из onReceive, и напоминание теряется.
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        Escalator(app).handleIntake(id)
                    } finally {
                        pending.finish()
                    }
                }
            }
            AlarmScheduler.ACTION_MEASURE -> {
                Notifications.showMeasureHint(app, intent.getStringExtra(AlarmScheduler.EXTRA_SLOT) ?: "день")
            }
        }
    }
}

/**
 * После перезагрузки и после обновления приложения система снимает все
 * будильники. Без этого обработчика напоминания не вернулись бы до
 * следующего запуска приложения, а обновление поверх установленной версии
 * оставляло бы пользователя без напоминаний о лекарствах.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val app = context.applicationContext as App
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Scheduler.scheduleMeasureHints(app)
                Escalator(app).catchUp()
            } finally {
                pending.finish()
            }
        }
        // Разворачивание расписания на 14 дней быстрее делать в своём scope:
        // goAsync имеет ограничение примерно в 10 секунд.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ru.chronicnotebook.data.IntakeScheduler(app).rescheduleAll()
        }
    }
}

class IntakeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAKEN) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) return
        val app = context.applicationContext as App
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.db.intakeDao().markTaken(id, System.currentTimeMillis())
                AlarmScheduler.cancel(app, id)
                // Без этого уведомление оставалось в шторке после отметки
                // «Принял»: у него стоял autoCancel = false.
                Notifications.dismiss(app, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "ru.chronicnotebook.INTAKE_TAKEN"
        const val EXTRA_ID = AlarmScheduler.EXTRA_ID
    }
}
