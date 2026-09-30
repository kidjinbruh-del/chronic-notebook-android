package ru.chronicnotebook.io

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Чтение и запись файлов, выбранных самим человеком.
 *
 * Приложение не просит доступ к памяти и не пишет в общую папку: путь
 * выбирается через системный выбор файла (SAF). Поэтому здесь нет ни одного
 * разрешения, а файл может лежать где угодно — в «Загрузках», в «Google Диске»
 * или в папке «Документы».
 */
object FileExchange {

    /** Максимум за раз: файл в 50 МБ замеров — это мусор, а не данные. */
    private const val MAX_CHARS = 8 * 1024 * 1024

    class FileTooLarge(val chars: Int) :
        Exception("Файл слишком большой ($chars символов). Выгрузите данные частями.")

    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Не удалось открыть файл" }
            InputStreamReader(stream, Charsets.UTF_8).readText()
        }
        if (text.length > MAX_CHARS) throw FileTooLarge(text.length)
        text
    }

    suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // truncate: режим "w" у провайдера может не обрезать старый файл, и тогда
        // к новому экспорту дописался бы хвост прошлой выгрузки.
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Не удалось создать файл")
    }

    /** Имя файла по-русски, с датой: «Замеры-2026-09-30.json». */
    fun fileName(prefix: String, extension: String, at: Long = System.currentTimeMillis()): String {
        val date = java.time.Instant.ofEpochMilli(at)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return "$prefix-$date.$extension"
    }
}