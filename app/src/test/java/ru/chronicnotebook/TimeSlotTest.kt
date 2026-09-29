package ru.chronicnotebook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSlotTest {
    @Test
    fun clockRoundTrip() {
        assertEquals("00:00", 0.toClock())
        assertEquals("08:30", 510.toClock())
        assertEquals("13:05", 785.toClock())
        assertEquals("23:59", 1439.toClock())
    }

    @Test
    fun parsesValidInput() {
        assertEquals(510, "08:30".toMinutes())
        assertEquals(0, "0:00".toMinutes())
        assertEquals(1439, "23:59".toMinutes())
        assertEquals(510, " 8:30 ".toMinutes())
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull("".toMinutes())
        assertNull("25:00".toMinutes())
        assertNull("12:60".toMinutes())
        assertNull("12".toMinutes())
        assertNull("ab:cd".toMinutes())
        assertNull("12:30:00".toMinutes())
        assertNull("-1:00".toMinutes())
    }

    @Test
    fun manySlotsSurviveEditing() {
        var times = listOf(510)
        times = times.minus(510) + 1230
        assertEquals(listOf(1230), times)
        times = times + 60
        assertEquals(listOf(60, 1230), times.sorted())
        times = times - 1230
        assertEquals(listOf(60), times)
        times = times + 510 + 60
        assertEquals(listOf(60, 60, 510), times.sorted())
    }
}
