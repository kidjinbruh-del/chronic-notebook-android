package ru.chronicnotebook.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Файловый обмен замерами: JSON для своих копий и CSV для Excel.
 *
 * Формат не «как получится»: экспорт и импорт — пара, и человек вправе
 * рассчитывать, что файл, выгруженный до обновления приложения, загрузится
 * после. Поэтому у файла есть явные `format` и `version`, а неизвестные поля
 * не ломают разбор — новые версии приложения не отказываются читать старый файл.
 *
 * CSV разделён `;`, а не запятой: в русской локали Excel открывает запятую как
 * десятичный разделитель и показывает весь файл одной колонкой. Кодировка —
 * UTF-8 с BOM по той же причине, плюс поля с `;` и переводами строк берутся в кавычки.
 */
object MeasurementIo {

    const val FORMAT = "chronic-notebook-measurements"
    const val VERSION = 1

    private const val CSV_SEPARATOR = ';'
    private const val TAG_JOINER = "|"
    private val BOM = '\uFEFF'

    private val CSV_HEADER = listOf(
        "takenAt", "sys", "dia", "pulse", "arm", "context",
        "valid", "issues", "note", "bucket", "tags",
    )

    // ------------------------------------------------------------- JSON

    fun toJson(rows: List<PortableMeasurement>, exportedAt: Long = System.currentTimeMillis()): String {
        val file = JsonFile(
            format = FORMAT,
            version = VERSION,
            exportedAt = exportedAt,
            measurements = rows.sortedBy { it.takenAt },
        )
        return Gson().toJson(file)
    }

    fun fromJson(text: String): List<PortableMeasurement> {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            throw ImportException("Файл повреждён: это не JSON")
        }
        if (!root.isJsonObject) throw ImportException("Ожидался объект JSON в корне файла")

        val obj = root.asJsonObject
        val format = obj.get("format")?.asString
        if (format != null && format != FORMAT) {
            throw ImportException("Файл из другого приложения (format=$format)")
        }
        val version = obj.get("version")?.asInt ?: 1
        if (version > VERSION) {
            throw ImportException("Файл сохранён более новой версией приложения (v$version)")
        }
        val array = obj.getAsJsonArray("measurements")
            ?: throw ImportException("В файле нет списка замеров")

        val out = mutableListOf<PortableMeasurement>()
        var skipped = 0
        array.forEach { element ->
            val m = readJsonRow(element)
            if (m == null) skipped++ else out.add(m)
        }
        if (out.isEmpty() && skipped > 0) {
            throw ImportException("В файле $skipped строк, но ни одна не прошла проверку")
        }
        return out
    }

    private fun readJsonRow(element: com.google.gson.JsonElement): PortableMeasurement? {
        if (!element.isJsonObject) return null
        val o = element.asJsonObject
        val takenAt = o.get("takenAt")?.asLong ?: return null
        val sys = o.get("sys")?.asInt ?: return null
        val dia = o.get("dia")?.asInt ?: return null
        if (!validValues(takenAt, sys, dia, o.get("pulse")?.takeIf { !it.isJsonNull }?.asInt)) return null
        return PortableMeasurement(
            takenAt = takenAt,
            sys = sys,
            dia = dia,
            pulse = o.get("pulse")?.takeIf { !it.isJsonNull }?.asInt,
            arm = o.get("arm")?.asString ?: "left",
            context = o.get("context")?.asString ?: "rest",
            valid = o.get("valid")?.asBoolean ?: true,
            issues = o.get("issues")?.asString.orEmpty(),
            note = o.get("note")?.asString.orEmpty(),
            bucket = o.get("bucket")?.asString ?: "rest",
            tags = o.getAsJsonArray("tags")?.map { it.asString }
                ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        )
    }

    // -------------------------------------------------------------- CSV

    /** Первая строка — заголовок, разделитель `;`, в конце BOM. */
    fun toCsv(rows: List<PortableMeasurement>): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(CSV_HEADER.joinToString(CSV_SEPARATOR.toString())).append("\r\n")
        rows.sortedBy { it.takenAt }.forEach { m ->
            sb.append(
                listOf(
                    m.takenAt.toString(),
                    m.sys.toString(),
                    m.dia.toString(),
                    m.pulse?.toString().orEmpty(),
                    m.arm,
                    m.context,
                    if (m.valid) "1" else "0",
                    m.issues,
                    m.note,
                    m.bucket,
                    m.tags.joinToString(TAG_JOINER),
                ).joinToString(CSV_SEPARATOR.toString(), transform = ::csvQuote)
            )
            sb.append("\r\n")
        }
        return sb.toString()
    }

    fun fromCsv(text: String): List<PortableMeasurement> {
        val rows = parseCsv(text.removePrefix(BOM.toString()))
        if (rows.isEmpty()) throw ImportException("Файл пуст")
        val header = rows.first().map { it.trim().lowercase() }
        if (header.none { it == "takenat" } || header.none { it == "sys" }) {
            throw ImportException("В CSV нет колонок takenAt и sys")
        }
        fun col(name: String): Int = header.indexOf(name)

        val iTaken = col("takenat")
        val iSys = col("sys")
        val iDia = col("dia")
        val iPulse = col("pulse")
        val iArm = col("arm")
        val iCtx = col("context")
        val iValid = col("valid")
        val iIssues = col("issues")
        val iNote = col("note")
        val iBucket = col("bucket")
        val iTags = col("tags")

        val out = mutableListOf<PortableMeasurement>()
        rows.drop(1).forEach { r ->
            fun at(i: Int): String? = r.getOrNull(i)?.trim()
            val takenAt = at(iTaken)?.toLongOrNull()
            val sys = at(iSys)?.toIntOrNull()
            val dia = at(iDia)?.toIntOrNull()
            if (takenAt == null || sys == null || dia == null) return@forEach
            val pulse = at(iPulse)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            if (!validValues(takenAt, sys, dia, pulse)) return@forEach
            out.add(
                PortableMeasurement(
                    takenAt = takenAt,
                    sys = sys,
                    dia = dia,
                    pulse = pulse,
                    arm = at(iArm)?.takeIf { it.isNotEmpty() } ?: "left",
                    context = at(iCtx)?.takeIf { it.isNotEmpty() } ?: "rest",
                    valid = when (at(iValid)?.lowercase()) {
                        "0", "false", "нет", "no" -> false
                        else -> true
                    },
                    issues = at(iIssues).orEmpty(),
                    note = at(iNote).orEmpty(),
                    bucket = at(iBucket)?.takeIf { it.isNotEmpty() } ?: "rest",
                    tags = at(iTags).orEmpty()
                        .split(TAG_JOINER)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                )
            )
        }
        if (out.isEmpty()) {
            throw ImportException("Ни одна строка CSV не прошла проверку значений")
        }
        return out
    }

    private fun csvQuote(value: String): String {
        val needQuotes = value.any { it == CSV_SEPARATOR || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needQuotes) "\"$escaped\"" else escaped
    }

    /** Разбор CSV с кавычками и переводами строк внутри полей. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == CSV_SEPARATOR -> {
                    row.add(field.toString()); field.setLength(0)
                }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** По расширению и содержимому: чем читать файл. */
    fun fromAny(text: String, fileName: String? = null): List<PortableMeasurement> {
        val looksJson = text.trimStart().startsWith("{")
        val looksCsv = fileName?.endsWith(".csv", true) == true
        return when {
            looksJson || (!looksCsv && text.contains("\"measurements\"")) -> fromJson(text)
            else -> fromCsv(text)
        }
    }

    // ------------------------------------------------------- проверка

    /**
     * Отсекает заведомый мусор: часы вместо миллисекунд, опечатки в цифрах,
     * пульс 900.
     *
     * Границы намеренно широкие — это «правдоподобно», а не «норма»: задача
     * импорта не отсеивать странные, но реальные замеры, а ловить битые файлы.
     * По времени — от 2000 года до 2100-го: запись 1970 года означает, что в
     * файле лежит ноль или секунды вместо миллисекунд.
     */
    fun validValues(takenAt: Long, sys: Int, dia: Int, pulse: Int?): Boolean {
        if (sys !in 40..300 || dia !in 20..200) return false
        if (pulse != null && pulse !in 20..250) return false
        if (takenAt !in MIN_TAKEN_AT..MAX_TAKEN_AT) return false
        return true
    }

    /** 2000-01-01T00:00:00Z */
    const val MIN_TAKEN_AT = 946_684_800_000L

    /** 2100-01-01T00:00:00Z */
    const val MAX_TAKEN_AT = 4_102_444_800_000L

    // ------------------------------------------------------ дедупликация

    /**
     * Ключ одного и того же замера: время и сами цифры.
     *
     * Именно время, а не `id`: при импорте на другое устройство id не совпадёт,
     * и повторный импорт того же файла каждый раз добавлял бы копии.
     */
    fun keyOf(m: PortableMeasurement): String =
        "${m.takenAt}|${m.sys}|${m.dia}|${m.pulse ?: -1}"

    /**
     * Что добавить, что уже есть и что негодно.
     *
     * Совпадение считается и внутри файла: если там две одинаковые строки,
     * вторая тоже отбрасывается, иначе дубль появлялся бы сразу после
     * импорта.
     */
    fun plan(
        incoming: List<PortableMeasurement>,
        existingKeys: Set<String>,
    ): ImportPlan {
        val seen = HashSet<String>(existingKeys)
        val toAdd = mutableListOf<PortableMeasurement>()
        var duplicates = 0
        incoming.forEach { m ->
            val key = keyOf(m)
            if (seen.add(key)) toAdd.add(m) else duplicates++
        }
        return ImportPlan(
            toAdd = toAdd,
            duplicates = duplicates,
            skipped = 0,
            tagsToCreate = toAdd.flatMap { it.tags }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sorted(),
        )
    }

    /** «day» в местном поясе: импорт на устройстве с другим часовым поясом пересчитывает. */
    fun dayOf(takenAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(takenAt), zone).toString()

    private data class JsonFile(
        val format: String,
        val version: Int,
        val exportedAt: Long,
        val measurements: List<PortableMeasurement>,
    )
}