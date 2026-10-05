package ru.chronicnotebook.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Срок годности упаковки и остаток — то, ради чего карточка препарата в
 * дневнике давления связана с «Хранилищем препаратов».
 *
 * Логика намеренно совпадает с одноимённой в хранилище: человек должен
 * видеть одно и то же, если откроет оба приложения. Общего кода между
 * приложениями нет и не будет — они собираются отдельно и не зависят друг
 * от друга, поэтому расхождение здесь проверяется тестами с обеих сторон.
 */
enum class StockState {
    /** Срок вышел: применять нельзя. */
    EXPIRED,

    /** Истекает скоро. */
    SOON,

    /** В норме. */
    OK,

    /** Срок не указан. */
    NONE,
}

object Stock {

    /** За сколько дней предупреждать о подходящем сроке. */
    const val SOON_DAYS = 30

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val HUMAN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return try {
            LocalDate.parse(iso, ISO)
        } catch (e: DateTimeParseException) {
            // Мусор в поле даты не должен ронять карточку препарата: запись
            // обязана остаться читаемой даже с испорченным сроком.
            null
        }
    }

    fun toIso(date: LocalDate): String = date.format(ISO)

    fun human(iso: String?): String = parse(iso)?.format(HUMAN) ?: "—"

    fun daysLeft(iso: String?, today: LocalDate): Long? =
        parse(iso)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }

    fun state(iso: String?, today: LocalDate, soonDays: Int = SOON_DAYS): StockState {
        val left = daysLeft(iso, today) ?: return StockState.NONE
        return when {
            left < 0 -> StockState.EXPIRED
            left <= soonDays -> StockState.SOON
            else -> StockState.OK
        }
    }

    /**
     * Подпись под сроком: «просрочен на 5 дней», «осталось 18 дней».
     *
     * Склонение пишется здесь целиком. В первой версии оно собиралось из двух
     * кусков и давало «осталось 5 дн. дней».
     */
    fun expiryLabel(iso: String?, today: LocalDate, soonDays: Int = SOON_DAYS): String {
        val left = daysLeft(iso, today) ?: return "срок не указан"
        return when (state(iso, today, soonDays)) {
            StockState.EXPIRED -> {
                // «просрочен» — мужского рода («срок просрочен»), поэтому
                // склоняется только число: «на 1 день», «на 2 дня», «на 5 дней».
                val n = -left
                if (n == 0L) "срок вышел сегодня" else "просрочен на $n ${dayWord(n)}"
            }
            StockState.SOON ->
                if (left == 0L) "истекает сегодня" else "осталось $left ${dayWord(left)}"
            else -> "осталось $left ${dayWord(left)}"
        }
    }

    /** Подпись остатка. Ноль значит «не считали», а не «не осталось». */
    fun stockLabel(count: Int, unit: String): String? =
        if (count <= 0) null else "$count ${unit.ifBlank { "шт" }}"

    private fun dayWord(n: Long): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m100 in 11..14 -> "дней"
            m10 == 1L -> "день"
            m10 in 2..4 -> "дня"
            else -> "дней"
        }
    }
}