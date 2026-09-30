package ru.chronicnotebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.chronicnotebook.App
import ru.chronicnotebook.reminders.ReminderSound

/**
 * Выбор мелодии напоминания.
 *
 * Звук задаётся каналом уведомлений, а его настройки после создания системой не
 * меняются. Поэтому на каждый вариант звука обязан быть свой канал: если бы все
 * варианты писались в один канал, выбор мелодии молча ничего не делал бы, и
 * человек продолжал бы слышать прежнюю.
 */
class ReminderSoundTest {

    @Test
    fun missingValueMeansDefaultSound() {
        assertEquals(ReminderSound.DEFAULT, ReminderSound.normalize(null))
        assertEquals(ReminderSound.DEFAULT, ReminderSound.normalize(""))
        assertEquals(ReminderSound.DEFAULT, ReminderSound.normalize("   "))
    }

    @Test
    fun defaultAndSilentUseDistinctChannels() {
        assertEquals(App.CHANNEL_INTAKE, ReminderSound.channelId(ReminderSound.DEFAULT))
        assertNotEquals(
            ReminderSound.channelId(ReminderSound.DEFAULT),
            ReminderSound.channelId(ReminderSound.SILENT),
        )
    }

    @Test
    fun eachCustomSoundGetsItsOwnChannel() {
        val a = ReminderSound.channelId("content://media/internal/audio/media/12")
        val b = ReminderSound.channelId("content://media/internal/audio/media/99")
        assertNotEquals(a, b)
        assertNotEquals(App.CHANNEL_INTAKE, a)
        assertTrue(a.startsWith(App.CHANNEL_INTAKE))
    }

    @Test
    fun sameSoundAlwaysResolvesToTheSameChannel() {
        val uri = "content://media/external/audio/media/7"
        assertEquals(ReminderSound.channelId(uri), ReminderSound.channelId(uri))
    }

    @Test
    fun channelIdNeverContainsMinusSign() {
        // Отрицательный hashCode дал бы в идентификаторе канала знак минус:
        // в настройках системы это выглядит как поломка.
        val ids = (1..500).map { ReminderSound.channelId("content://ringtone/$it") }
        ids.forEach { assertTrue("плохой id: $it", it.matches(Regex("[A-Za-z0-9_]+"))) }
    }

    @Test
    fun channelNamesDifferSoSettingsAreNotConfusing() {
        assertNotEquals(
            ReminderSound.channelName(ReminderSound.SILENT),
            ReminderSound.channelName(ReminderSound.DEFAULT),
        )
        assertNotEquals(
            ReminderSound.channelName(ReminderSound.DEFAULT),
            ReminderSound.channelName("content://media/internal/audio/media/12"),
        )
    }
}
