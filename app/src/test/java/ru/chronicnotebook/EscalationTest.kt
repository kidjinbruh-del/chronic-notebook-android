package ru.chronicnotebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.chronicnotebook.reminders.AlarmScheduler
import ru.chronicnotebook.reminders.Escalator

/**
 * Расписание ступеней эскалации напоминаний о приёме лекарства.
 *
 * Специально проверяется сценарий сторожевого воркера: раньше он отправлял
 * следующую ступень сразу, как только её видел, и весь ряд сдвигался, а
 * последнее напоминание через два часа не приходило никогда.
 */
class EscalationTest {

    private fun min(m: Long) = m * 60_000

    @Test
    fun stepsFollowTheDocumentedSchedule() {
        assertEquals(listOf(0L, 15L, 45L, 120L), Escalator.STEPS.map { it / 60_000 })
    }

    @Test
    fun nothingIsDueBeforeTheIntakeTime() {
        assertEquals(-1, Escalator.dueStep(-1))
    }

    @Test
    fun firstStepIsDueExactlyOnTime() {
        assertEquals(0, Escalator.dueStep(0))
    }

    @Test
    fun stepChangesOnlyAtItsOwnMoment() {
        // Первая ступень держится все 15 минут, вторая не наступает на ровно
        // 15-й минуте по «ещё рано», но наступает сразу после.
        assertEquals(0, Escalator.dueStep(min(14)))
        assertEquals(1, Escalator.dueStep(min(15)))
        assertEquals(1, Escalator.dueStep(min(44)))
        assertEquals(2, Escalator.dueStep(min(45)))
        assertEquals(2, Escalator.dueStep(min(119)))
        assertEquals(3, Escalator.dueStep(min(120)))
    }

    @Test
    fun lastStepDoesNotRepeat() {
        assertEquals(3, Escalator.dueStep(min(600)))
    }

    @Test
    fun escalationOffLeavesOnlyTheOnTimeStep() {
        assertEquals(0, Escalator.maxStep(escalate = false))
        assertEquals(3, Escalator.maxStep(escalate = true))
    }

    /**
     * Сторож бежит раз в 15 минут. За два часа он обязан пройти весь ряд
     * 0 → 1 → 2 → 3 ровно по одному разу: раньше он слал следующую ступень
     * сразу и упирался в конец ряда к 45-й минуте, не дожидаясь двухчасовой.
     */
    @Test
    fun watchdogWalksTheScheduleWithoutRepeating() {
        val sent = mutableListOf<Int>()
        var lastSent = -1
        var check = 0L
        while (check <= min(200)) {
            val due = Escalator.dueStep(check)
            if (due > lastSent && due <= Escalator.maxStep(escalate = true)) {
                lastSent = due
                sent += due
            }
            check += min(15)
        }
        assertEquals(listOf(0, 1, 2, 3), sent)
    }

    /**
     * Идентификатор будильника. При requestCode из голого Long.toInt() препараты
     * с id 1 и id 2^32+1 получали один и тот же код и сносили будильник друг друга.
     */
    @Test
    fun requestCodeDoesNotCollide() {
        assertEquals(5, AlarmScheduler.requestCode(5))
        assertNotEquals(
            AlarmScheduler.requestCode(1),
            AlarmScheduler.requestCode(1 + (1L shl 32)),
        )
    }
}
