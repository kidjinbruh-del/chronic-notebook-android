package ru.chronicnotebook.ui

import android.content.Context
import ru.chronicnotebook.data.MeasurementEntity
import ru.chronicnotebook.data.WeatherEntity
import ru.chronicnotebook.domain.Baseline
import ru.chronicnotebook.domain.Bucket
import ru.chronicnotebook.domain.Correlation
import ru.chronicnotebook.domain.Factor
import ru.chronicnotebook.domain.Level
import ru.chronicnotebook.domain.Protocol
import ru.chronicnotebook.domain.Stats
import ru.chronicnotebook.domain.Thresholds
import ru.chronicnotebook.domain.advice
import ru.chronicnotebook.domain.bucketOf
import ru.chronicnotebook.domain.classify
import ru.chronicnotebook.domain.hour
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Текстовый отчёт для врача. Без рекомендаций по терапии. */
class ReportBuilder(
    private val context: Context,
    private val measurements: List<MeasurementEntity>,
    private val weather: List<WeatherEntity>,
) {
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    fun build(days: Int = 30): String {
        val since = System.currentTimeMillis() - days * 24L * 3600_000
        val inWindow = measurements.filter { it.takenAt >= since }.sortedByDescending { it.takenAt }
        val valid = inWindow.filter { it.valid }
        val out = StringBuilder()

        out.appendLine("Отчёт дневника давления")
        out.appendLine("Период: последние $days дней. Сформировано ${dateFmt.format(Instant.now())}")
        out.appendLine("Замеров всего: ${inWindow.size}, корректных: ${valid.size}")
        out.appendLine()

        out.appendLine("ПРОТОКОЛ")
        // Считаем те записи, где проблема ЕСТЬ. Раньше стояло отрицание, и цифра
        // показывала ровно обратное: сколько замеров сделано правильно.
        val noRest = inWindow.count { it.issues.contains(Protocol.NO_REST) }
        out.appendLine("Без 5-минутного покоя: $noRest")
        out.appendLine("В нерестовом состоянии: ${inWindow.count { it.context != "rest" }}")
        out.appendLine()

        out.appendLine("ПОКАЗАТЕЛИ")
        if (valid.isEmpty()) {
            out.appendLine("Нет корректных замеров за период.")
        } else {
            out.appendLine("САД средний %.0f, ДАД средний %.0f, макс %d/%d, мин %d/%d".format(
                valid.map { it.sys }.average(),
                valid.map { it.dia }.average(),
                valid.maxOf { it.sys }, valid.maxOf { it.dia },
                valid.minOf { it.sys }, valid.minOf { it.dia },
            ))
            valid.mapNotNull { it.pulse }.takeIf { it.isNotEmpty() }?.let {
                out.appendLine("Пульс средний %.0f, макс %d".format(it.average(), it.max()))
            }
            out.appendLine(Stats.stdev(valid.map { it.sys })?.let { "Разброс САД ±%.0f мм рт.ст.".format(it) } ?: "Разброс САД: недостаточно замеров")
            Bucket.values().forEach { bucket ->
                val rows = valid.filter { bucketOf(it.hour()) == bucket }
                if (rows.isNotEmpty()) {
                    out.appendLine(
                        "  %s: %.0f/%.0f (n=%d)".format(
                            bucket.label, rows.map { it.sys }.average(),
                            rows.map { it.dia }.average(), rows.size
                        )
                    )
                }
            }
        }
        out.appendLine()

        out.appendLine("ПРЕВЫШЕНИЯ ПО ПОРОГАМ")
        val crisis = valid.filter { classify(it.sys, it.dia) == Level.CRISIS }
        val high = valid.filter { classify(it.sys, it.dia) == Level.HIGH }
        out.appendLine("Кризисные (>=180/120): ${crisis.size}; повышенные (>=160/100): ${high.size}")
        crisis.forEach { out.appendLine("  ${dateFmt.format(Instant.ofEpochMilli(it.takenAt))}: ${it.sys}/${it.dia}") }
        out.appendLine()

        val baseline: Baseline? = Stats.baseline(valid)
        if (baseline != null) {
            out.appendLine("ЛИЧНАЯ БАЗА (по ${baseline.n} замерам)")
            listOfNotNull(baseline.morning, baseline.day, baseline.evening).forEach {
                out.appendLine("  %s %.0f/%.0f (n=%d)".format(it.bucket.label, it.sys, it.dia, it.n))
            }
            Stats.whiteCoatHint(baseline)?.let { out.appendLine("  $it") }
            out.appendLine()
        }

        // Корреляции считаем по тому же окну, о котором написано в шапке отчёта.
        val sensitivity = Correlation.sensitivity(inWindow, weather)
        out.appendLine("СВЯЗЬ С ПОГОДОЙ")
        if (!sensitivity.ready) {
            out.appendLine(
                "Недостаточно данных: ${sensitivity.n} из ${Thresholds.CORRELATION_MIN_N} " +
                    "корректных замеров."
            )
        } else if (sensitivity.factors.isEmpty()) {
            out.appendLine("Устойчивых корреляций не выявлено (r >= 0.30 не достигнут).")
        } else {
            sensitivity.factors.forEach { f: Factor ->
                out.appendLine("  %s, лаг %d ч: r=%.2f, %s, n=%d".format(
                    f.label, f.lagHours, f.r, f.effectText, f.n
                ))
            }
        }
        out.appendLine()

        out.appendLine("ПОГОДА (последние 3 дня)")
        if (weather.isEmpty()) {
            out.appendLine("  нет данных")
        }
        weather.takeLast(3).forEach { w ->
            // Раньше отсутствующее значение печаталось как 0.0 °C и 0 мм рт.ст.
            // Врач принимал это за реальные показания.
            val temp = w.tMean?.let { "%.1f °C".format(it) } ?: "н/д"
            val press = w.pMsl?.let { "%.0f мм рт.ст.".format(it) } ?: "н/д"
            out.appendLine("  ${w.day}: $temp, $press")
        }
        out.appendLine()

        out.appendLine("ПРИМЕЧАНИЕ")
        out.appendLine(
            "Приложение фиксирует показания и разбирает их по заранее заданным порогам. " +
                "Оно не ставит диагноз, не корректирует и не назначает лечение. " +
                "Решения о терапии принимает врач."
        )
        return out.toString()
    }

    fun todayAdvice(): String {
        val now = bucketOf(Instant.now().atZone(ZoneId.systemDefault()).hour)
        return "Сейчас ${now.label}. Отдых 5 минут, сидя, две цифры через минуту."
    }
}
