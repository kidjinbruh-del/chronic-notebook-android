package ru.chronicnotebook.domain

import ru.chronicnotebook.data.MeasurementEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.sqrt

enum class Context(val key: String, val label: String) {
    REST("rest", "покой 5 минут"),
    AFTER_WALK("after_walk", "после ходьбы"),
    AFTER_COFFEE("after_coffee", "после кофе"),
    AFTER_SMOKING("after_smoking", "после курения"),
    AFTER_STRESS("after_stress", "после стресса"),
    AFTER_MEAL("after_meal", "после еды"),
}

object Protocol {
    fun issues(
        sys: Int,
        dia: Int,
        context: Context,
        rested: Boolean,
        spoke: Boolean,
        cuffOk: Boolean,
    ): List<String> {
        val out = mutableListOf<String>()
        if (!rested) out += "не отдыхал 5 минут перед замером"
        if (spoke) out += "разговаривал во время измера"
        if (!cuffOk) out += "манжета подобрана неверно"
        if (sys - dia < 20) out += "разница систолического и диастолического подозрительно мала"
        if (context != Context.REST) out += "замер сделан в нерестовом состоянии"
        return out
    }
}

data class BucketStat(
    val bucket: Bucket,
    val n: Int,
    val sys: Double,
    val dia: Double,
    val pulse: Double?,
)

data class Baseline(
    val n: Int,
    val night: BucketStat?,
    val morning: BucketStat?,
    val day: BucketStat?,
    val evening: BucketStat?,
    val sdSys: Double?,
) {
    fun forBucket(bucket: Bucket): BucketStat? = when (bucket) {
        Bucket.NIGHT -> night ?: morning ?: day
        Bucket.MORNING -> morning ?: day
        Bucket.DAY -> day ?: morning
        Bucket.EVENING -> evening ?: morning ?: day
    }
}

object Stats {
    fun baseline(items: List<MeasurementEntity>): Baseline? {
        val valid = items.filter { it.valid }
        if (valid.size < Thresholds.BASELINE_MIN_N) return null
        val byBucket = valid.groupBy { bucketOf(it.hour()) }
        fun stat(bucket: Bucket, rows: List<MeasurementEntity>): BucketStat? {
            if (rows.isEmpty()) return null
            val pulses = rows.mapNotNull { it.pulse }
            return BucketStat(
                bucket = bucket,
                n = rows.size,
                sys = rows.map { it.sys }.average(),
                dia = rows.map { it.dia }.average(),
                pulse = if (pulses.isEmpty()) null else pulses.average(),
            )
        }
        return Baseline(
            n = valid.size,
            night = stat(Bucket.NIGHT, byBucket[Bucket.NIGHT].orEmpty()),
            morning = stat(Bucket.MORNING, byBucket[Bucket.MORNING].orEmpty()),
            day = stat(Bucket.DAY, byBucket[Bucket.DAY].orEmpty()),
            evening = stat(Bucket.EVENING, byBucket[Bucket.EVENING].orEmpty()),
            sdSys = stdev(valid.map { it.sys }),
        )    }

    fun stdev(values: List<Int>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / (values.size - 1)
        return sqrt(variance)
    }

    fun whiteCoatHint(baseline: Baseline?): String? {
        val morning = baseline?.morning ?: return null
        val evening = baseline.evening ?: return null
        val diff = evening.sys - morning.sys
        return if (diff >= 10) "вечернее давление выше утреннего на %.0f мм рт.ст.".format(diff) else null
    }
}

fun MeasurementEntity.hour(): Int =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(takenAt), ZoneId.systemDefault()).hour

fun MeasurementEntity.localDate(): String =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(takenAt), ZoneId.systemDefault()).toLocalDate().toString()
