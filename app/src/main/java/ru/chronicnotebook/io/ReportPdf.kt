package ru.chronicnotebook.io

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Отчёт в PDF без сторонних библиотек.
 *
 * Пишется через системный `PdfDocument`: приложение остаётся офлайн и не
 * тянет в APK чужой рендеринг. Листы A4, поля 40 pt, шрифт 11 pt — столько
 * текста врач читает с телефона или печатает на приёме.
 */
object ReportPdf {

    private const val PAGE_W = 595 // A4 при 72 dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val BODY_SIZE = 11f
    private const val LINE_H = 14f

    fun render(body: String): ByteArray {
        val document = PdfDocument()
        val text = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = BODY_SIZE
        }
        val footer = Paint().apply {
            isAntiAlias = true
            color = Color.GRAY
            textSize = 9f
        }

        val lines = wrap(body, text, PAGE_W - 2 * MARGIN)
        var pageNumber = 0
        var cursor = 0
        do {
            pageNumber++
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            val canvas = page.canvas
            var y = MARGIN
            while (cursor < lines.size && y < PAGE_H - MARGIN - LINE_H) {
                canvas.drawText(lines[cursor], MARGIN, y + BODY_SIZE, text)
                cursor++
                y += LINE_H
            }
            // Номер страницы: у длинного отчёта иначе непонятно, что страницы
            // не потерялись при печати.
            canvas.drawText("$pageNumber", PAGE_W / 2f, PAGE_H - MARGIN / 2f, footer)
            document.finishPage(page)
        } while (cursor < lines.size)

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    /**
     * Разбивка на строки по ширине листа.
     *
     * `breakText` режет по словам, но очень длинное слово (например, путь)
     * умеет разрезать посередине — иначе оно уехало бы за край и потерялось.
     */
    private fun wrap(body: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        body.lines().forEach { raw ->
            val line = raw.replace("\t", "    ")
            if (line.isEmpty()) {
                out.add("")
                return@forEach
            }
            var rest = line
            val first = paint.breakText(rest, true, maxWidth, null)
            out.add(rest.substring(0, first.coerceAtMost(rest.length)))
            rest = rest.substring(first.coerceAtMost(rest.length))
            while (rest.isNotEmpty()) {
                val n = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                out.add(rest.substring(0, n))
                rest = rest.substring(n)
            }
        }
        return out
    }
}