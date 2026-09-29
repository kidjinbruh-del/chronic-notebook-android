package ru.chronicnotebook.data

import android.content.Context
import ru.chronicnotebook.reminders.AlarmScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

/** Разворачивает расписание на 14 дней вперёд и переносит всё на следующий запуск. */
class IntakeScheduler(private val context: Context) {
    private val db = (context.applicationContext as ru.chronicnotebook.App).container.db
    private val settings = (context.applicationContext as ru.chronicnotebook.App).container.settings

    suspend fun rescheduleAll() {
        val meds = db.medDao().activeMedsOnce().associateBy { it.id }
        val schedules = db.medDao().allSchedulesOnce().filter { it.active }
        val zone = ZoneId.systemDefault()

        for (dayOffset in 0 until 14) {
            val day = LocalDate.now().plusDays(dayOffset.toLong())
            for (schedule in schedules) {
                val med = meds[schedule.medId] ?: continue
                if (!med.activeOn(day)) continue
                for (minutes in timesOf(schedule)) {
                    val due = day.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
                    if (due < System.currentTimeMillis()) continue
                    db.intakeDao().insert(
                        IntakeEntity(medId = med.id, dueAt = due, status = IntakeEntity.STATUS_DUE)
                    )
                }
            }
        }

        val pending = db.intakeDao().since(System.currentTimeMillis())
        val known = pending.associateBy { it.id }
        for (intake in pending) {
            if (intake.status != IntakeEntity.STATUS_DUE) continue
            AlarmScheduler.scheduleExact(context, intake, intake.dueAt - System.currentTimeMillis())
        }
        for (row in db.intakeDao().staleBefore(System.currentTimeMillis() - 3_600_000)) {
            if (row.status == IntakeEntity.STATUS_DUE && row.id !in known) db.intakeDao().markMissed(row.id)
        }
    }

    fun timesOf(schedule: ScheduleEntity): List<Int> =
        schedule.times.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..(24 * 60 - 1) }
            .distinct()
}

fun MedEntity.activeOn(day: LocalDate): Boolean {
    if (startsOn != null && day.toString() < startsOn) return false
    if (endsOn != null && day.toString() > endsOn) return false
    return true
}
