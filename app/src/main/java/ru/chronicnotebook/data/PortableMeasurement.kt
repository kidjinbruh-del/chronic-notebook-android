package ru.chronicnotebook.data

/**
 * Замер в виде, пригодном для файла.
 *
 * Отдельный тип, а не сама `MeasurementEntity`: в базе есть `id`, который
 * после импорта на другое устройство не имеет смысла, и `day`, который
 * пересчитывается из `takenAt` в местном часовом поясе.
 */
data class PortableMeasurement(
    val takenAt: Long,
    val sys: Int,
    val dia: Int,
    val pulse: Int? = null,
    val arm: String = "left",
    val context: String = "rest",
    val valid: Boolean = true,
    val issues: String = "",
    val note: String = "",
    val bucket: String = "rest",
    val tags: List<String> = emptyList(),
)

/** Разбор файла упал так, что человек должен понять, что именно не так. */
class ImportException(message: String) : Exception(message)

/** Сколько чего получилось при импорте. */
data class ImportSummary(
    val added: Int,
    val duplicates: Int,
    val skipped: Int,
    val tagsCreated: Int,
) {
    val text: String
        get() = buildString {
            append("Добавлено: $added")
            if (duplicates > 0) append(", совпало с уже имеющимися: $duplicates")
            if (skipped > 0) append(", пропущено негодных строк: $skipped")
            if (tagsCreated > 0) append(", новых тегов: $tagsCreated")
        }
}

/** Готовый к импорту файл: что добавить, что уже есть, что негодно. */
data class ImportPlan(
    val toAdd: List<PortableMeasurement>,
    val duplicates: Int,
    val skipped: Int,
    val tagsToCreate: List<String>,
)