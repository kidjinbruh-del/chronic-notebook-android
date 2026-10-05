package ru.chronicnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Проверки срока годности и остатка.
 *
 * Склонение проверяется на числах, на которых оно ломается: 1, 2, 4, 5, 11,
 * 12, 21, 22. В первой версии подпись собиралась из двух кусков и давала
 * «осталось 5 дн. дней».
 *
 * Те же случаи проверяются в «Хранилище препаратов»: приложения собраны
 * отдельно и общего кода у них нет, поэтому расхождение в склонении придётся
 * ловить тестами с обеих сторон.
 */
class StockTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 6)

    private fun isoIn(days: Long): String = today.plusDays(days).toString()

    @Test
    fun `без даты состояние NONE`() {
        assertEquals(StockState.NONE, Stock.state(null, today))
        assertEquals("срок не указан", Stock.expiryLabel(null, today))
    }

    @Test
    fun `испорченная дата не роняет разбор`() {
        assertNull(Stock.parse("через месяц"))
        assertNull(Stock.parse("12.26"))
        assertEquals(StockState.NONE, Stock.state("через месяц", today))
    }

    @Test
    fun `просроченное отделено от нормального`() {
        assertEquals(StockState.EXPIRED, Stock.state(isoIn(-1), today))
        assertEquals(StockState.EXPIRED, Stock.state(isoIn(-400), today))
        assertEquals(StockState.OK, Stock.state(isoIn(31), today))
        assertEquals(StockState.SOON, Stock.state(isoIn(30), today))
    }

    @Test
    fun `порог скоро настраивается`() {
        assertEquals(StockState.OK, Stock.state(isoIn(45), today))
        assertEquals(StockState.SOON, Stock.state(isoIn(45), today, 60))
    }

    @Test
    fun `склонение дней не дублируется`() {
        assertEquals("осталось 1 день", Stock.expiryLabel(isoIn(1), today))
        assertEquals("осталось 2 дня", Stock.expiryLabel(isoIn(2), today))
        assertEquals("осталось 4 дня", Stock.expiryLabel(isoIn(4), today))
        assertEquals("осталось 5 дней", Stock.expiryLabel(isoIn(5), today))
        assertEquals("осталось 11 дней", Stock.expiryLabel(isoIn(11), today))
        assertEquals("осталось 21 день", Stock.expiryLabel(isoIn(21), today))
        assertEquals("осталось 22 дня", Stock.expiryLabel(isoIn(22), today))
    }

    @Test
    fun `просрочка склоняется как срок`() {
        assertEquals("просрочен на 1 день", Stock.expiryLabel(isoIn(-1), today))
        assertEquals("просрочен на 3 дня", Stock.expiryLabel(isoIn(-3), today))
        assertEquals("просрочен на 12 дней", Stock.expiryLabel(isoIn(-12), today))
    }

    @Test
    fun `на границе суток срок еще пригоден`() {
        // Срок, совпадающий с сегодняшним днём, — это «скоро», не просрочка:
        // сегодня лекарство ещё можно принять.
        assertEquals(StockState.SOON, Stock.state(isoIn(0), today))
        assertEquals("истекает сегодня", Stock.expiryLabel(isoIn(0), today))
    }

    @Test
    fun `остаток не показывается когда не считали`() {
        // Ноль значит «не считали», а не «не осталось»: иначе в карточке
        // висело бы «Осталось: 0 шт» у каждого препарата без запаса.
        assertNull(Stock.stockLabel(0, "шт"))
        assertNull(Stock.stockLabel(-3, "шт"))
        assertEquals("12 шт", Stock.stockLabel(12, "шт"))
        assertEquals("2 уп", Stock.stockLabel(2, "уп"))
        assertEquals("5 шт", Stock.stockLabel(5, ""))
    }

    @Test
    fun `дата выводится по-русски`() {
        assertEquals("24.09.2026", Stock.human("2026-09-24"))
        assertEquals("—", Stock.human(null))
    }
}