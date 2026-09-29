package ru.chronicnotebook

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Записывает причину сбоя в файл, чтобы её можно было прочитать из приложения.
 * Раньше падение на чужом телефоне выглядело как «ошибка, какая — не ясно».
 */
object CrashLog {
    private const val TAG = "ChronicNotebook"
    private const val FILE = "last_crash.txt"
    private const val MAX = 16_000

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val header = buildString {
            appendLine("Время: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Поток: ${thread.name}")
            appendLine()
            append(error.stackTraceToString())
        }
        runCatching {
            File(context.filesDir, FILE).writeText(header.take(MAX))
        }
        android.util.Log.e(TAG, "Необработанная ошибка", error)
    }

    fun last(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
