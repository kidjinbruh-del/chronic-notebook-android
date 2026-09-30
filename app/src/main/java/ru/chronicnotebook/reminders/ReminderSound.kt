package ru.chronicnotebook.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import ru.chronicnotebook.App

/**
 * Выбор мелодии напоминания.
 *
 * Звук уведомления задаётся каналом, а настройки канала после создания
 * системой не меняются: новый звук молча игнорируется, и человек продолжает
 * слышать прежний. Поэтому на каждый вариант звука заводится свой канал
 * (`intake_v3`, `intake_v3_silent`, `intake_v3_<хеш>`), а при смене варианта
 * ненужные каналы удаляются, чтобы не копить их в настройках системы.
 *
 * Значение настройки — строка:
 *  - [DEFAULT] — стандартная мелодия будильника телефона;
 *  - [SILENT]  — без звука, только вибрация и уведомление;
 *  - иначе     — URI мелодии, выбранной пользователем.
 */
object ReminderSound {
    const val DEFAULT = "default"
    const val SILENT = "silent"

    /** Приводит значение из настроек к одному из известных вариантов. */
    fun normalize(raw: String?): String = when {
        raw.isNullOrBlank() -> DEFAULT
        raw == SILENT -> SILENT
        else -> raw
    }

    /**
     * Идентификатор канала для варианта звука.
     *
     * Хеш берётся беззнаковым: обычный hashCode может дать минус, а лишний
     * символ в идентификаторе канала выглядит как ошибка в настройках системы.
     */
    fun channelId(sound: String?): String {
        val value = normalize(sound)
        return when (value) {
            DEFAULT -> App.CHANNEL_INTAKE
            SILENT -> "${App.CHANNEL_INTAKE}_silent"
            else -> "${App.CHANNEL_INTAKE}_" + Integer.toHexString(value.hashCode())
        }
    }

    /**
     * URI мелодии. Выбранную пользователем проверяем: если файл удалён с телефона
     * или система больше его не отдаёт, молча возвращаем стандартный будильник,
     * иначе канал получит нечитаемый звук и напоминание станет беззвучным.
     */
    fun uri(context: Context, sound: String?): Uri? {
        val value = normalize(sound)
        if (value == SILENT) return null
        if (value != DEFAULT) {
            val picked = Uri.parse(value)
            val resolved = runCatching { RingtoneManager.getRingtone(context, picked) }.getOrNull()
            if (resolved != null) return picked
        }
        return defaultUri()
    }

    fun defaultUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /** Название варианта для интерфейса. */
    fun label(context: Context, sound: String?): String {
        val value = normalize(sound)
        if (value == SILENT) return "Без звука"
        if (value == DEFAULT) return "Стандартный будильник"
        val title = runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context)
        }.getOrNull()
        return title ?: "Своя мелодия (файл недоступен)"
    }

    /** Название канала: в настройках системы видно, какой звук сейчас выбран. */
    fun channelName(sound: String?): String = when (normalize(sound)) {
        SILENT -> "Приём препаратов — без звука"
        DEFAULT -> "Приём препаратов — срочно"
        else -> "Приём препаратов — своя мелодия"
    }

    /**
     * Атрибуты воспроизведения для канала напоминаний.
     *
     * Именно get(), а не val со значением: в объекте значение вычислялось бы при
     * первой же инициализации, а android.media.AudioAttributes в обычном
     * JVM-тесте без Robolectric падает с «Stub!». Из-за этого не запускался
     * весь объект целиком, включая normalize() и channelId(), которым Android
     * не нужен вовсе, и шесть тестов на выбор мелодии краснели.
     */
    val audioAttributes: AudioAttributes
        get() = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
}
