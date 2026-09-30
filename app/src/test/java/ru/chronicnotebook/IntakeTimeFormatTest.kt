package ru.chronicnotebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.chronicnotebook.ui.slotTimes
import ru.chronicnotebook.ui.toClock
import ru.chronicnotebook.ui.toMinutes

/**
 * Формат индивидуального времени приёма.
 *
 * Слот хранится минутами от полуночи, на карточке показывается как «ЧЧ:ММ».
 * Раньше время выбиралось системным диалогом, который на части прошивок
 * возвращал час с нулями минут: выбранные 12:45 сохранялись как 12:00.
 * Теперь минуты задаются явно, и round-trip не должен терять их.
 */
class IntakeTimeFormatTest {

    @Test
    fun minutesSurviveRoundTrip() {
        for (minutes in listOf(0, 1, 9, 45, 59, 60, 745, 720, 1439)) {
            assertEquals("минуты потерялись: $minutes", minutes, minutes.toClock().toMinutes())
        }
    }

    @Test
    fun quarterPastNoonIsNotNoon() {
        val stored = "12:45".toMinutes()
        assertEquals(12 * 60 + 45, stored)
        assertEquals("12:45", stored!!.toClock())
    }

    @Test
    fun clockIsPaddedToTwoDigits() {
        assertEquals("00:00", 0.toClock())
        assertEquals("00:05", 5.toClock())
        assertEquals("09:07", (9 * 60 + 7).toClock())
        assertEquals("23:59", (23 * 60 + 59).toClock())
    }

    @Test
    fun everyMinuteOfTheDayRoundTrips() {
        for (minutes in 0 until 24 * 60) {
            assertEquals(minutes, minutes.toClock().toMinutes())
        }
    }

    @Test
    fun wrongInputIsRejected() {
        assertNull("12".toMinutes())
        assertNull("12:".toMinutes())
        assertNull("24:00".toMinutes())
        assertNull("12:60".toMinutes())
        assertNull("abc:de".toMinutes())
        assertNull("".toMinutes())
    }

    @Test
    fun leadingAndTrailingSpacesAreIgnored() {
        assertEquals(12 * 60 + 45, " 12:45 ".toMinutes())
    }

    @Test
    fun addingSlotKeepsSingleValue() {
        assertEquals(listOf(765), slotTimes(emptyList(), null, 12, 45))
        assertEquals(listOf("12:45"), slotTimes(emptyList(), null, 12, 45).map { it.toClock() })
    }

    @Test
    fun addingSlotDoesNotSplitHourAndMinutes() {
        // Регресс: `times + hour * 60 + minute` давало [720, 45],
        // то есть «12:00» и «00:45» вместо одного «12:45».
        val slots = slotTimes(emptyList(), null, 12, 45)
        assertEquals(1, slots.size)
        assertEquals("12:45", slots.single().toClock())
    }

    @Test
    fun editingReplacesOnlyTargetSlot() {
        val slots = slotTimes(listOf(480, 765), 480, 9, 5)
        assertEquals(listOf(545, 765), slots)
    }

    @Test
    fun duplicateSlotIsNotAddedTwice() {
        assertEquals(listOf(765), slotTimes(listOf(765), null, 12, 45))
    }

    @Test
    fun slotsAreSorted() {
        assertEquals(listOf(60, 765, 1200), slotTimes(listOf(765, 1200), null, 1, 0))
        assertEquals(listOf(60, 765), slotTimes(listOf(765, 1200), 1200, 1, 0))
    }
}
