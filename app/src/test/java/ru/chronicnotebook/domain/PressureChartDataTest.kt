package ru.chronicnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureChartDataTest {

    private fun row(
        id: Long = 1,
        takenAt: Long = 1_700_000_000_000,
        sys: Int = 120,
        valid: Boolean = true,
    ) = ChartMeasurement(id, takenAt, sys, 80, 70, valid)

    @Test
    fun tagFilterIsInclusiveAndEmptySelectionShowsAll() {
        val rows = listOf(row(1), row(2), row(3))
        val tags = mapOf(1L to setOf(10L), 3L to setOf(11L))

        assertEquals(
            listOf(1L, 2L, 3L),
            selectChartPoints(rows, tags, emptySet(), true, null).map { it.id },
        )
        assertEquals(
            listOf(1L, 3L),
            selectChartPoints(rows, tags, setOf(10L, 11L), true, null).map { it.id },
        )
    }

    @Test
    fun invalidPointsAreHiddenByDefault() {
        val rows = listOf(row(1), row(2, valid = false))
        assertEquals(listOf(1L), selectChartPoints(rows, emptyMap(), emptySet(), false, null).map { it.id })
        assertEquals(listOf(1L, 2L), selectChartPoints(rows, emptyMap(), emptySet(), true, null).map { it.id })
    }

    @Test
    fun rangeKeepsOnlyRecentPointsInChronologicalOrder() {
        val now = 1_700_000_000_000
        val rows = listOf(
            row(1, now - 40L * 24 * 3600_000),
            row(2, now - 10L * 24 * 3600_000),
            row(3, now),
        )
        val selected = selectChartPoints(rows, emptyMap(), emptySet(), true, 30, now)
        assertEquals(listOf(2L, 3L), selected.map { it.id })
    }

    @Test
    fun downsamplingKeepsShapeAndLatestPoint() {
        val points = (0 until 1_001).map {
            ChartPoint(it.toLong(), 1_700_000_000_000L + it, 120, 80, null)
        }
        val sampled = limitChartPoints(points, 100)
        assertTrue(sampled.size <= 100)
        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
        assertTrue(sampled.zipWithNext().all { (a, b) -> a.takenAt < b.takenAt })
    }
}
