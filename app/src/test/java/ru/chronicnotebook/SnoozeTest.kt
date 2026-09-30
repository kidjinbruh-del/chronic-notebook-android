package ru.chronicnotebook

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.chronicnotebook.domain.snoozedDueAt

/**
 * Откладывание приёма кнопкой «через N минут».
 *
 * Список «Ожидают отметки» показывает приёмы на ближайший час, включая ещё не
 * наступившие. Раньше новый срок всегда считался как «сейчас + N», и нажатие
 * «через 15 мин» на приёме в 00:02 в 23:47 переносило напоминание на 00:02
 * минус сорок пять минут — то есть будильник звонил раньше назначенного приёма.
 */
class SnoozeTest {

    private val min = 60_000L

    @Test
    fun dueIntakeIsPushedForwardFromNow() {
        // Приём был в 23:00, кнопку нажали в 23:47 — ждём 00:02.
        val dueAt = 23 * 60 * min
        val now = 23 * 60 * min + 47 * min
        assertEquals(24 * 60 * min + 2 * min, snoozedDueAt(dueAt, now, 15))
    }

    @Test
    fun futureIntakeIsNeverPulledEarlier() {
        // Приём в 00:02, кнопку нажали в 23:47: 00:17, а не 00:02.
        val dueAt = 24 * 60 * min + 2 * min
        val now = 23 * 60 * min + 47 * min
        val snoozed = snoozedDueAt(dueAt, now, 15)
        assertEquals(24 * 60 * min + 17 * min, snoozed)
        assert(snoozed > dueAt) { "откладывание не должно двигать приём назад" }
    }

    @Test
    fun intakingRightAtTheTimeKeepsTheSameResult() {
        val dueAt = 10 * 60 * min
        assertEquals(dueAt + 15 * min, snoozedDueAt(dueAt, dueAt, 15))
    }
}
