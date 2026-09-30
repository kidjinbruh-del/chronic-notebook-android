package ru.chronicnotebook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Файловый обмен замерами.
 *
 * Главное свойство здесь — симметрия: то, что приложение выгрузило, обязано
 * читаться обратно без потерь. Остальные тесты защищают от типичных промахов:
 * русского текста в CSV, кавычек и повторного импорта одного файла.
 */
class MeasurementIoTest {

    private fun sample(
        takenAt: Long = 1_700_000_000_000,
        sys: Int = 128,
        dia: Int = 82,
        pulse: Int? = 66,
        note: String = "утром, после кофе",
        issues: String = "без 5-минутного покоя; разболтано",
        tags: List<String> = listOf("утро", "кофе"),
        valid: Boolean = true,
    ) = PortableMeasurement(
        takenAt = takenAt,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = "left",
        context = "rest",
        valid = valid,
        issues = issues,
        note = note,
        bucket = "morning",
        tags = tags,
    )

    @Test
    fun jsonRoundTripKeepsEverything() {
        val original = listOf(sample(), sample(takenAt = 1_700_003_600_000, sys = 141, dia = 91, pulse = null))
        val back = MeasurementIo.fromJson(MeasurementIo.toJson(original))

        assertEquals(2, back.size)
        assertEquals(original.map { it.takenAt }, back.map { it.takenAt })
        assertEquals(original.map { it.sys }, back.map { it.sys })
        assertEquals(original.map { it.dia }, back.map { it.dia })
        assertEquals(original.map { it.pulse }, back.map { it.pulse })
        assertEquals(original.map { it.note }, back.map { it.note })
        assertEquals(original.map { it.issues }, back.map { it.issues })
        assertEquals(original.map { it.bucket }, back.map { it.bucket })
        assertEquals(original.first().tags, back.first().tags)
        assertTrue("нулевой пульс должен остаться null, а не 0", back[1].pulse == null)
    }

    @Test
    fun csvRoundTripKeepsEverything() {
        val original = listOf(sample())
        val back = MeasurementIo.fromCsv(MeasurementIo.toCsv(original))

        assertEquals(1, back.size)
        val m = back.single()
        assertEquals(128, m.sys)
        assertEquals(82, m.dia)
        assertEquals(66, m.pulse)
        assertEquals("утром, после кофе", m.note)
        assertEquals("без 5-минутного покоя; разболтано", m.issues)
        assertEquals(listOf("утро", "кофе"), m.tags)
        assertEquals("morning", m.bucket)
    }

    /** Excel в русской локали без BOM превращает кириллицу в мусор. */
    @Test
    fun csvStartsWithBomSoExcelReadsRussian() {
        val csv = MeasurementIo.toCsv(listOf(sample()))
        assertEquals('\uFEFF', csv.first())
    }

    @Test
    fun csvIsReadableWithoutBom() {
        val csv = MeasurementIo.toCsv(listOf(sample()))
        assertEquals(listOf("утро", "кофе"), MeasurementIo.fromCsv(csv).single().tags)
    }

    @Test
    fun csvQuotesSeparatorsAndNewlines() {
        val tricky = listOf(sample(note = "строка1\nстрока2", issues = "а; б", tags = listOf("а;б")))
        val back = MeasurementIo.fromCsv(MeasurementIo.toCsv(tricky)).single()

        assertEquals("строка1\nстрока2", back.note)
        assertEquals("а; б", back.issues)
        assertEquals(listOf("а;б"), back.tags)
    }

    @Test
    fun csvQuotesDoubleQuotes() {
        val back = MeasurementIo.fromCsv(MeasurementIo.toCsv(listOf(sample(note = "он сказал \"нормально\""))))
        assertEquals("он сказал \"нормально\"", back.single().note)
    }

    @Test
    fun csvHandlesCarriageReturnLineFeed() {
        val csv = "takenAt;sys;dia\r\n1700000000000;120;80\r\n"
        val m = MeasurementIo.fromCsv(csv).single()
        assertEquals(1_700_000_000_000L, m.takenAt)
        assertEquals(120, m.sys)
    }

    @Test
    fun jsonIsDetectedByContentNotByName() {
        val json = MeasurementIo.toJson(listOf(sample()))
        val csv = MeasurementIo.toCsv(listOf(sample()))
        assertEquals(128, MeasurementIo.fromAny(json, "measurements.txt").single().sys)
        assertEquals(128, MeasurementIo.fromAny(csv, "measurements.csv").single().sys)
    }

    @Test
    fun repeatedImportAddsNothing() {
        val rows = listOf(sample(), sample(takenAt = 1_700_003_600_000, sys = 130))
        val existing = rows.map { MeasurementIo.keyOf(it) }.toSet()

        val first = MeasurementIo.plan(rows, existing)
        assertEquals(0, first.toAdd.size)
        assertEquals(2, first.duplicates)
    }

    @Test
    fun importSkipsAlreadyPresentAndKeepsNewOnes() {
        val existing = listOf(sample(takenAt = 1_700_000_000_000))
        val incoming = listOf(
            sample(takenAt = 1_700_000_000_000),
            sample(takenAt = 1_700_007_200_000, sys = 135),
        )

        val plan = MeasurementIo.plan(incoming, existing.map { MeasurementIo.keyOf(it) }.toSet())
        assertEquals(1, plan.toAdd.size)
        assertEquals(1_700_007_200_000L, plan.toAdd.single().takenAt)
        assertEquals(1, plan.duplicates)
    }

    @Test
    fun duplicatesInsideOneFileAreDropped() {
        val plan = MeasurementIo.plan(listOf(sample(), sample()), emptySet())
        assertEquals(1, plan.toAdd.size)
        assertEquals(1, plan.duplicates)
    }

    @Test
    fun sameTimeDifferentValuesAreTwoMeasurements() {
        val plan = MeasurementIo.plan(
            listOf(sample(sys = 120), sample(sys = 128)),
            emptySet(),
        )
        assertEquals("разные цифры — это разные замеры", 2, plan.toAdd.size)
    }

    @Test
    fun newTagsAreListedForCreation() {
        val plan = MeasurementIo.plan(listOf(sample(tags = listOf("кофе", "Кофе", " стресс "))), emptySet())
        // Регистр и пробелы не должны плодить «Кофе» и « стресс » отдельно.
        assertEquals(listOf("кофе", "стресс"), plan.tagsToCreate)
    }

    @Test
    fun junkValuesAreRejected() {
        assertTrue(!MeasurementIo.validValues(1_700_000_000_000, 39, 80, null))
        assertTrue(!MeasurementIo.validValues(1_700_000_000_000, 128, 201, null))
        assertTrue(!MeasurementIo.validValues(1_700_000_000_000, 128, 80, 900))
        // Ноль означает, что в файле секунды вместо миллисекунд.
        assertTrue(!MeasurementIo.validValues(0L, 128, 80, null))
        assertTrue(!MeasurementIo.validValues(1_700_000_000_000_000_000L, 128, 80, null))
        assertTrue(MeasurementIo.validValues(1_700_000_000_000, 250, 150, 180))
    }

    @Test
    fun brokenJsonIsRejectedWithReadableMessage() {
        val e = assertThrows(ImportException::class.java) {
            MeasurementIo.fromJson("{это не json")
        }
        assertTrue(e.message!!.contains("не JSON"))
    }

    @Test
    fun foreignFileIsRejected() {
        val e = assertThrows(ImportException::class.java) {
            MeasurementIo.fromJson("""{"format":"other-app","version":1,"measurements":[]}""")
        }
        assertTrue(e.message!!.contains("другого приложения"))
    }

    @Test
    fun newerVersionIsRejectedInsteadOfSilentlyDroppingFields() {
        val e = assertThrows(ImportException::class.java) {
            MeasurementIo.fromJson("""{"format":"chronic-notebook-measurements","version":99,"measurements":[]}""")
        }
        assertTrue(e.message!!.contains("более новой"))
    }

    @Test
    fun fileWithOnlyJunkIsRejected() {
        assertThrows(ImportException::class.java) {
            MeasurementIo.fromJson("""{"format":"chronic-notebook-measurements","version":1,"measurements":[{"sys":1}]}""")
        }
    }

    @Test
    fun csvWithoutKnownColumnsIsRejected() {
        val e = assertThrows(ImportException::class.java) {
            MeasurementIo.fromCsv("a;b;c\n1;2;3\n")
        }
        assertTrue(e.message!!.contains("takenAt"))
    }

    @Test
    fun emptyCsvIsRejected() {
        assertThrows(ImportException::class.java) { MeasurementIo.fromCsv("") }
    }

    @Test
    fun unknownJsonFieldsAreIgnored() {
        val text = """
            {"format":"chronic-notebook-measurements","version":1,"exportedAt":1,
             "measurements":[{"takenAt":1700000000000,"sys":120,"dia":80,"pulse":70,
                              "tags":["утро"],"futureField":42}]}
        """.trimIndent()
        val m = MeasurementIo.fromJson(text).single()
        assertEquals(120, m.sys)
        assertEquals(listOf("утро"), m.tags)
    }

    @Test
    fun emptyTagListSurvivesRoundTrip() {
        val back = MeasurementIo.fromCsv(MeasurementIo.toCsv(listOf(sample(tags = emptyList()))))
        assertEquals(emptyList<String>(), back.single().tags)
    }

    @Test
    fun dayIsRecomputedInLocalZone() {
        // 2025-09-14T21:00Z: в UTC ещё 14-е, в Москве уже 15-е.
        val evening = Instant.parse("2025-09-14T21:00:00Z").toEpochMilli()
        assertEquals("2025-09-14", MeasurementIo.dayOf(evening, ZoneId.of("UTC")))
        assertEquals("2025-09-15", MeasurementIo.dayOf(evening, ZoneId.of("Europe/Moscow")))
    }
}