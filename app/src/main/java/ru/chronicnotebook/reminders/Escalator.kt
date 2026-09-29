package ru.chronicnotebook.reminders

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.chronicnotebook.App
import ru.chronicnotebook.data.IntakeEntity
import ru.chronicnotebook.data.ReminderLogEntity

/**
 * Ступенчатые напоминания: 0 — вовремя, дальше +15, +45, +120 минут.
 * Каждый отправленный шаг пишется в БД, чтобы он не повторился после перезапуска процесса.
 */
class Escalator(context: Context) {
    private val app = context.applicationContext as App
    private val db = app.container.db
    private val scope = CoroutineScope(Dispatchers.IO)

    fun handleIntake(intakeId: Long) {
        scope.launch {
            val current = db.intakeDao().byId(intakeId) ?: return@launch
            if (current.status != IntakeEntity.STATUS_DUE &&
                current.status != IntakeEntity.STATUS_SNOOZED
            ) return@launch

            val med = db.medDao().med(current.medId) ?: return@launch
            val dose = listOf(med.dose, med.unit).filter { it.isNotBlank() }.joinToString(" ")

            val next = current.lastStepSent + 1
            if (next >= STEPS.size) return@launch

            val elapsed = System.currentTimeMillis() - current.dueAt
            val wait = STEPS[next] - elapsed
            if (wait > 0) {
                AlarmScheduler.scheduleExact(app, current, wait)
                return@launch
            }

            Notifications.showIntake(app, current, med.name, dose, next)
            db.intakeDao().setStep(current.id, next)
            db.reminderLogDao().insert(
                ReminderLogEntity(
                    intakeId = current.id,
                    step = next,
                    sentAt = System.currentTimeMillis(),
                    detail = "${med.name} $dose",
                )
            )
            if (next + 1 < STEPS.size) {
                AlarmScheduler.scheduleExact(app, current, STEPS[next + 1] - elapsed)
            }
        }
    }

    fun catchUp(enabled: Boolean) {
        if (!enabled) return
        scope.launch {
            val now = System.currentTimeMillis()
            val meds = db.medDao().activeMedsOnce().associateBy { it.id }
            for (intake in db.intakeDao().dueNow(now)) {
                val med = meds[intake.medId] ?: continue
                val next = intake.lastStepSent + 1
                if (next >= STEPS.size) continue
                val dose = listOf(med.dose, med.unit).filter { it.isNotBlank() }.joinToString(" ")
                Notifications.showIntake(app, intake, med.name, dose, next)
                db.intakeDao().setStep(intake.id, next)
            }
            db.intakeDao().staleBefore(now - DAY).forEach { db.intakeDao().markMissed(it.id) }
        }
    }

    companion object {
        val STEPS = listOf(0L, 15L * 60_000, 45L * 60_000, 120L * 60_000)
        private const val DAY = 24L * 60 * 60_000
    }
}
