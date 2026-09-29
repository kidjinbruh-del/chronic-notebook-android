package ru.chronicnotebook.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.chronicnotebook.App
import ru.chronicnotebook.data.IntakeScheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        when (intent.action) {
            AlarmScheduler.ACTION_INTAKE -> {
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
                if (id <= 0) return
                val pending = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val intake = app.container.db.intakeDao().byId(id)
                    val med = intake?.let { app.container.db.medDao().med(it.medId) }
                    if (intake != null && med != null) {
                        Escalator(app).handleIntake(intake.id)
                    }
                    pending.finish()
                }
            }
            AlarmScheduler.ACTION_MEASURE -> {
                Notifications.showMeasureHint(app, intent.getStringExtra(AlarmScheduler.EXTRA_SLOT) ?: "день")
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as App
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            IntakeScheduler(app).rescheduleAll()
            Escalator(app).catchUp(app.container.settings.escalate)
            pending.finish()
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
        CoroutineScope(Dispatchers.IO).launch {
            app.container.db.intakeDao().markTaken(id, System.currentTimeMillis())
            AlarmScheduler.cancel(app, id)
            pending.finish()
        }
    }

    companion object {
        const val ACTION_TAKEN = "ru.chronicnotebook.INTAKE_TAKEN"
        const val EXTRA_ID = AlarmScheduler.EXTRA_ID
    }
}
