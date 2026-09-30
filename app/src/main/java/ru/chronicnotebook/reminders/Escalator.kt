package ru.chronicnotebook.reminders

import android.content.Context
import ru.chronicnotebook.App
import ru.chronicnotebook.data.IntakeEntity
import ru.chronicnotebook.data.ReminderLogEntity

/**
 * Ступенчатые напоминания: 0 — вовремя, дальше +15, +45, +120 минут.
 * Каждый отправленный шаг пишется в БД, чтобы он не повторился после перезапуска процесса.
 *
 * Ступени привязаны к времени приёма, а не к моменту проверки. Раньше сторож
 * (раз в 15 минут) отправлял следующую ступень сразу, как только её видел, и
 * весь ряд сдвигался: вместо 0/+15/+45/+120 выходило 0/+15/+30/+45, а
 * последнее напоминание через два часа не приходило вовсе.
 */
class Escalator(context: Context) {
    private val app = context.applicationContext as App
    private val db = app.container.db

    private val settings get() = app.container.settings

    /** Обработка срабатывания будильника. Вызывается в scope вызывающего кода. */
    suspend fun handleIntake(intakeId: Long) {
        val current = db.intakeDao().byId(intakeId) ?: return
        if (current.status != IntakeEntity.STATUS_DUE &&
            current.status != IntakeEntity.STATUS_SNOOZED
        ) return

        val med = db.medDao().med(current.medId) ?: return
        val next = current.lastStepSent + 1
        if (next > maxStep()) return

        val elapsed = System.currentTimeMillis() - current.dueAt
        val wait = STEPS[next] - elapsed
        if (wait > 0) {
            // Будильник прозвенел раньше срока: переносим на нужный момент.
            AlarmScheduler.scheduleExact(app, current, wait)
            return
        }

        send(current, med.name, doseOf(med.dose, med.unit), next)
    }

    /**
     * Досылка просроченных после перезагрузки, обновления приложения и по
     * страховочному воркеру. Отправляется только та ступень, время которой
     * действительно наступило, и не больше одной за проход.
     */
    suspend fun catchUp() {
        val now = System.currentTimeMillis()
        val limit = maxStep()
        val meds = db.medDao().activeMedsOnce().associateBy { it.id }
        for (intake in db.intakeDao().dueNow(now)) {
            val med = meds[intake.medId]
            if (med == null) {
                // Препарат удалён или деактивирован: напоминать больше не о чем.
                db.intakeDao().markMissed(intake.id)
                continue
            }
            val due = stepDueAt(intake, now)
            if (due < 0) continue // время приёма ещё не наступило
            val dose = doseOf(med.dose, med.unit)
            when {
                due > limit -> if (intake.lastStepSent < 0) send(intake, med.name, dose, 0)
                due > intake.lastStepSent -> send(intake, med.name, dose, due)
                intake.lastStepSent + 1 < STEPS.size -> {
                    // Положенные ступени уже отправлены: вернуть будильник на следующую.
                    val delay = STEPS[intake.lastStepSent + 1] - (now - intake.dueAt)
                    if (delay > 0) AlarmScheduler.scheduleExact(app, intake, delay)
                }
            }
        }

        // Просроченные больше чем на сутки закрываем независимо от настройки
        // эскалации: при выключенных повторах они иначе навсегда оставались бы
        // в списке невыполненных на главном экране.
        db.intakeDao().staleBefore(now - DAY).forEach { db.intakeDao().markMissed(it.id) }
    }

    /** Последняя ступень, время которой наступило к моменту now, либо -1. */
    private fun stepDueAt(intake: IntakeEntity, now: Long): Int = dueStep(now - intake.dueAt)

    private fun maxStep(): Int = maxStep(settings.escalate)

    private suspend fun send(
        intake: IntakeEntity,
        medName: String,
        dose: String,
        step: Int,
    ) {
        val expected = intake.lastStepSent
        if (db.intakeDao().claimStep(intake.id, expected, step) != 1) return
        try {
            Notifications.showIntake(app, intake, medName, dose, step)
            db.reminderLogDao().insert(
                ReminderLogEntity(
                    intakeId = intake.id,
                    step = step,
                    sentAt = System.currentTimeMillis(),
                    detail = "$medName $dose",
                )
            )
        } catch (e: Exception) {
            // Ступень занята, но уведомление не показалось: возвращаем её,
            // иначе напоминание молча потеряется.
            db.intakeDao().setStep(intake.id, expected)
            android.util.Log.w("ChronicNotebook", "Не удалось отправить напоминание", e)
            return
        }
        val elapsed = System.currentTimeMillis() - intake.dueAt
        val nextDelay = STEPS.getOrNull(step + 1)?.minus(elapsed)
        // Дальше по цепочке идём только при включённой эскалации. При
        // выключенной положена одна ступень, но будильник на следующую всё
        // равно ставился: телефон через 15 минут просыпался, handleIntake
        // видел, что ступень не положена, и уходил, ничего не отправив.
        if (nextDelay != null && nextDelay > 0 && settings.escalate) {
            AlarmScheduler.scheduleExact(app, intake, nextDelay)
        }
    }

    private fun doseOf(dose: String, unit: String) =
        listOf(dose, unit).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        val STEPS = listOf(0L, 15L * 60_000, 45L * 60_000, 120L * 60_000)
        private const val DAY = 24L * 60 * 60_000

        /**
         * Номер ступени, положенной к моменту «прошло elapsed от времени приёма».
         * -1 означает, что срок ещё не наступил. Вынесено отдельно от Android,
         * чтобы расписание можно было проверить обычным тестом.
         */
        fun dueStep(elapsed: Long): Int = STEPS.indexOfLast { elapsed >= it }

        /** Сколько ступеней положено при выключенной эскалации. */
        fun maxStep(escalate: Boolean): Int = if (escalate) STEPS.lastIndex else 0
    }
}
