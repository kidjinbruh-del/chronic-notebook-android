package ru.chronicnotebook.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurement",
    indices = [Index("takenAt"), Index("day")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val day: String,
    val sys: Int,
    val dia: Int,
    val pulse: Int? = null,
    val arm: String = "left",
    val context: String = "rest",
    val valid: Boolean = true,
    val issues: String = "",
    val note: String = "",
    @ColumnInfo(defaultValue = "rest") val bucket: String = "rest",
)

/**
 * Словарь тегов. Заметки хранятся свободным текстом в замере, а теги —
 * закрытым списком: по ним строится фильтр на графике и отчёте.
 *
 * `name` уникален, чтобы не плодить «Кофе», «кофе» и « кофе».
 */
@Entity(
    tableName = "tag",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int = 0,
    val sortOrder: Int = 0,
)

/** Связь «замер — тег». Одна строка = один тег на замере. */
@Entity(
    tableName = "measurement_tag",
    primaryKeys = ["measurementId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = MeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class MeasurementTagEntity(
    val measurementId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "med",
    indices = [Index("inn"), Index("name")],
)
data class MedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val inn: String = "",
    val dose: String = "",
    val unit: String = "",
    val withFood: Boolean = false,
    val startsOn: String? = null,
    val endsOn: String? = null,
    val prescribedBy: String = "",
    val sourceUrl: String = "",
    val active: Boolean = true,
    /**
     * Срок годности упаковки в формате ISO yyyy-MM-dd.
     *
     * Это про другое, чем [startsOn] и [endsOn]: там начало и конец курса
     * («с 1 по 14»), здесь — когда упаковка станет непригодной. Просроченное
     * лекарство опасно тем, что выглядит обычно, поэтому дата нужна рядом
     * с расписанием, а не в отдельном приложении.
     */
    val expiresOn: String? = null,
    /**
     * Сколько осталось: штуки, упаковки, миллилитры. 0 — не считали.
     *
     * `defaultValue` указан явно и обязан совпадать с `DEFAULT` в миграции
     * 2 -> 3. Room сверяет не только тип и NOT NULL, но и значение по
     * умолчанию: база, обновлённая миграцией, с «лишним» DEFAULT не прошла
     * бы проверку схемы и приложение падало бы сразу при запуске.
     */
    @ColumnInfo(defaultValue = "0") val stock: Int = 0,
    @ColumnInfo(defaultValue = "'шт'") val stockUnit: String = "шт",
)

@Entity(
    tableName = "schedule",
    indices = [Index("medId")],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val times: String,
    val everyHours: Int? = null,
    val foodOverride: String = "inherit",
    val note: String = "",
    val active: Boolean = true,
)

@Entity(
    tableName = "intake",
    indices = [Index(value = ["medId", "dueAt"], unique = true), Index("dueAt"), Index("status")],
)
data class IntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val dueAt: Long,
    val takenAt: Long? = null,
    val status: String = STATUS_DUE,
    val note: String = "",
    val lastStepSent: Int = -1,
) {
    companion object {
        const val STATUS_DUE = "due"
        const val STATUS_TAKEN = "taken"
        const val STATUS_MISSED = "missed"
        const val STATUS_SNOOZED = "snoozed"
    }
}

@Entity(tableName = "weather")
data class WeatherEntity(
    @PrimaryKey val day: String,
    val lat: Double,
    val lon: Double,
    val tMean: Double? = null,
    val tMin: Double? = null,
    val tMax: Double? = null,
    val pMsl: Double? = null,
    val humidity: Double? = null,
    val wind: Double? = null,
    val precip: Double? = null,
    val source: String = "",
    val fetchedAt: Long = 0,
)

@Entity(tableName = "reminder_log", indices = [Index("intakeId")])
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intakeId: Long,
    val step: Int,
    val sentAt: Long,
    val ok: Boolean = true,
    val detail: String = "",
)
