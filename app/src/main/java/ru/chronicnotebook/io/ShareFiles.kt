package ru.chronicnotebook.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Отправка готовых файлов врачу или в мессенджер.
 *
 * Файл создаётся во внутренней cache-папке и отдаётся только на чтение по
 * одноразовому URI. В общую память приложение ничего не пишет и разрешений не
 * просит: после отправки временный файл удаляется.
 */
object ShareFiles {

    suspend fun share(context: Context, fileName: String, mimeType: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            // В cache могли остаться прошлые отправки: чистим их заранее, чтобы
            // врач не получил старый файл под новым именем.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, safeName(fileName))
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            shareUri(context, mimeType, uri, fileName)
        }
    }

    suspend fun shareText(context: Context, subject: String, text: String) {
        withContext(Dispatchers.Default) {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, subject))
        }
    }

    private suspend fun shareUri(context: Context, mimeType: String, uri: Uri, title: String) {
        withContext(Dispatchers.Main) {
            val send = Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, title))
        }
    }

    private fun safeName(name: String): String {
        val clean = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        return clean.ifEmpty { "file.bin" }
    }
}

/** Имя выбранного пользователем файла: нужно, чтобы отличить JSON от CSV. */
suspend fun displayName(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}
