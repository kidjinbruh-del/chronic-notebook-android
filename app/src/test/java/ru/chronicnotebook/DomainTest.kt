package ru.chronicnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.chronicnotebook.data.MeasurementEntity
import ru.chronicnotebook.data.WeatherEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.sqrt

class RulesTest {
    @Test
    fun crisisTakesPriority() {
        assertEquals(Level.CRISIS, classify(185, 95))
        assertEquals(Level.CRISIS, classify(150, 125))
        assertEquals(Level.HIGH, classify(165, 95))
        assertEquals(Level.ELEVATED, classify(145, 85))
        assertEquals(Level.NORMAL, classify(120, 80))
        assertEquals(Level.LOW, classify(85, 70))
    }

    @Test
    fun adviceIsFixedTextAndMentionsEmergency() {
        assertTrue(advice(Level.CRISIS, 190, 100).contains("103"))
        assertFalse(advice(Level.HIGH, 170, 100).contains("назнач"))
    }

    @Test
    fun bucketBoundaries() {
        assertEquals(Bucket.NIGHT, bucketOf(0))
        assertEquals(Bucket.NIGHT, bucketOf(4))
        assertEquals(Bucket.MORNING, bucketOf(5))
        assertEquals(Bucket.MORNING, bucketOf(11))
        assertEquals(Bucket.DAY, bucketOf(12))
        assertEquals(Bucket.DAY, bucketOf(17))
        assertEquals(Bucket.EVENING, bucketOf(18))
        assertEquals(Bucket.EVENING, bucketOf(23))
    }

    @Test
    fun protocolFlagsBadConditions() {
        val clean = Protocol.issues(130, 85, Context.REST, rested = true, spoke = false, cuffOk = true)
        assertTrue(clean.isEmpty())
        assertTrue(Protocol.issues(130, 85, Context.AFTER_WALK, true, false, true).isNotEmpty())
        assertTrue(Protocol.issues(130, 85, Context.REST, false, false, true).isNotEmpty())
        assertTrue(Protocol.issues(90, 85, Context.REST, true, false, true).isNotEmpty())
    }
}

class StatsTest {
    private fun at(hour: Int, sys: Int, dia: Int, day: Int) = MeasurementEntity(
        takenAt = Instant.parse("2026-03-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z").toEpochMilli(),
        day = "2026-03-${day.toString().padStart(2, '0')}",
        sys = sys,
        dia = dia,
    )

    @Test
    fun baselineNeedsFourteen() {
        assertNull(Stats.baseline((1..13).map { at(8, 120, 80, it) }))
        val full = (1..14).map { at(8, 120, 80, it) }
        val baseline = Stats.baseline(full)
        assertNotNull(baseline)
        assertEquals(14, baseline!!.n)
        assertEquals(120.0, baseline.morning!!.sys, 0.001)
    }

    @Test
    fun baselineSkipsInvalid() {
        val rows = (1..14).map { at(8, 130, 85, it) } + at(8, 190, 120, 15).copy(valid = false)
        assertEquals(14, Stats.baseline(rows)!!.n)
    }

    @Test
    fun stdevUsesSampleFormula() {
        assertNull(Stats.stdev(listOf(120)))
        assertEquals(7.0710678, Stats.stdev(listOf(115, 125))!!, 0.0001)
    }
}

class CorrelationTest {
    @Test
    fun pearsonRecoversKnownCoefficient() {
        val xs = (1..40).map { it.toDouble() }
        val ys = xs.map { 100 - 0.5 * it }
        val r = Correlation.pearson(xs, ys)!!
        assertEquals(-1.0, r, 0.0001)
        assertEquals(-0.5, Correlation.slope(xs, ys)!!, 0.0001)
    }

    @Test
    fun noConstantInput() {
        assertNull(Correlation.pearson(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun sensitivityFindsWeatherEffect() {
        val start = java.time.LocalDate.of(2026, 3, 1)
        val weather = (0 until 60).map { offset ->
            val date = start.plusDays(offset.toLong()).toString()
            WeatherEntity(
                day = date,
                lat = 55.7, lon = 39.7,
                tMean = 20.0 - offset * 0.1,
                pMsl = 760.0,
            )
        }
        val temps = weather.map { it.tMean!! }
        val measurements = weather.mapIndexed { index, w ->
            val day = w.day
            MeasurementEntity(
                takenAt = Instant.parse("${day}T12:00:00Z").toEpochMilli(),
                day = day,
                sys = (120 - 0.6 * temps[index]).toInt(),
                dia = 80,
            )
        }
        val result = Correlation.sensitivity(measurements, weather)
        assertTrue(result.ready)
        assertTrue(result.factors.isNotEmpty())
        val tFactor = result.factors.first { it.field == "tMean" }
        assertTrue(tFactor.r < -0.9)
    }

    @Test
    fun needsTwentyMeasurements() {
        val result = Correlation.sensitivity(
            (1..10).map { MeasurementEntity(takenAt = 0, day = "2026-03-01", sys = 120, dia = 80) },
            emptyList(),
        )
        assertFalse(result.ready)
    }

    @Test
    fun shiftDayMovesBackwardsByLag() {
        assertEquals("2026-03-02", Correlation.shiftDay("2026-03-02", 0))
        assertEquals("2026-03-01", Correlation.shiftDay("2026-03-02", 24))
        assertEquals("2026-02-28", Correlation.shiftDay("2026-03-02", 30))
        assertEquals("2026-02-28", Correlation.shiftDay("2026-03-02", 48))
    }
}
