package ru.chronicnotebook.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalTime

class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("chronic", Context.MODE_PRIVATE)

    var lat: Double
        // Раньше здесь стоял Double.parseDouble без перехвата: испорченное
        // значение в настройках роняло и синхронизацию погоды, и карточку
        // диагностики, потому что читалось оно на каждом экране.
        get() = prefs.getString(KEY_LAT, "55.7167")?.toDoubleOrNull() ?: 55.7167
        set(v) = prefs.edit().putString(KEY_LAT, v.toString()).apply()

    var lon: Double
        get() = prefs.getString(KEY_LON, "39.7083")?.toDoubleOrNull() ?: 39.7083
        set(v) = prefs.edit().putString(KEY_LON, v.toString()).apply()

    var placeName: String
        get() = prefs.getString(KEY_PLACE, "Рязань") ?: "Рязань"
        set(v) = prefs.edit().putString(KEY_PLACE, v).apply()

    var timezone: String
        get() = prefs.getString(KEY_TZ, java.util.TimeZone.getDefault().id) ?: java.util.TimeZone.getDefault().id
        set(v) = prefs.edit().putString(KEY_TZ, v).apply()

    var escalate: Boolean
        get() = prefs.getBoolean(KEY_ESCALATE, true)
        set(v) = prefs.edit().putBoolean(KEY_ESCALATE, v).apply()

    var reminderLeadMin: Int
        get() = prefs.getInt(KEY_LEAD, 0)
        set(v) = prefs.edit().putInt(KEY_LEAD, v).apply()

    /**
     * Мелодия напоминания: [ru.chronicnotebook.reminders.ReminderSound.DEFAULT],
     * [ru.chronicnotebook.reminders.ReminderSound.SILENT] или URI, выбранный
     * пользователем. Отдельная строка, а не boolean: вариантов больше двух, и
     * «выключить обратно» не должно затирать выбранный файл.
     */
    var alarmSound: String
        get() = ru.chronicnotebook.reminders.ReminderSound.normalize(
            prefs.getString(KEY_SOUND, ru.chronicnotebook.reminders.ReminderSound.DEFAULT)
        )
        set(v) = prefs.edit().putString(KEY_SOUND, ru.chronicnotebook.reminders.ReminderSound.normalize(v)).apply()

    var baselineSys: Int?
        get() = prefs.getInt(KEY_BASE_SYS, -1).takeIf { it > 0 }
        set(v) = prefs.edit().putInt(KEY_BASE_SYS, v ?: -1).apply()

    var baselineDia: Int?
        get() = prefs.getInt(KEY_BASE_DIA, -1).takeIf { it > 0 }
        set(v) = prefs.edit().putInt(KEY_BASE_DIA, v ?: -1).apply()

    fun lastSync(): Long = prefs.getLong(KEY_SYNC, 0L)

    fun setLastSync(v: Long) = prefs.edit().putLong(KEY_SYNC, v).apply()

    companion object {
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_PLACE = "place"
        private const val KEY_TZ = "tz"
        private const val KEY_ESCALATE = "escalate"
        private const val KEY_LEAD = "lead"
        private const val KEY_SOUND = "alarm_sound"
        private const val KEY_BASE_SYS = "base_sys"
        private const val KEY_BASE_DIA = "base_dia"
        private const val KEY_SYNC = "last_sync"

        fun today(): String = LocalDate.now().toString()
        fun nowMinutes(): Int {
            val t = LocalTime.now()
            return t.hour * 60 + t.minute
        }
    }
}
