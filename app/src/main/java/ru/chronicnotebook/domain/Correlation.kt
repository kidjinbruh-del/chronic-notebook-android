package ru.chronicnotebook.domain

import ru.chronicnotebook.data.MeasurementEntity
import ru.chronicnotebook.data.WeatherEntity
import java.time.LocalDate

data class Factor(
    val field: String,
    val kind: Kind,
    val label: String,
    val lagHours: Int,
    val r: Double,
    val slope: Double,
    val n: Int,
) {
    enum class Kind { DELTA, LEVEL }

    val effectText: String
        get() = when (kind) {
            Kind.DELTA -> "%+.0f мм рт.ст. на 10 ед.".format(slope * 10)
            Kind.LEVEL -> "%+.0f мм рт.ст. на 1 °C".format(slope)
        }
}

data class Sensitivity(
    val n: Int,
    val ready: Boolean,
    val factors: List<Factor>,
)

data class WeatherDelta(
    val day: String,
    val tMean: Double?,
    val pMsl: Double?,
    val dT24: Double?,
    val dP24: Double?,
)

object WeatherMath {
    fun deltas(rows: List<WeatherEntity>): List<WeatherDelta> {
        val out = ArrayList<WeatherDelta>(rows.size)
        rows.forEachIndexed { index, row ->
            val prev = rows.getOrNull(index - 1)
            out += WeatherDelta(
                day = row.day,
                tMean = row.tMean,
                pMsl = row.pMsl,
                dT24 = diff(row.tMean, prev?.tMean),
                dP24 = diff(row.pMsl, prev?.pMsl),
            )
        }
        return out
    }

    private fun diff(current: Double?, previous: Double?): Double? =
        if (current == null || previous == null) null else current - previous
}

object Correlation {
    private val FIELDS = listOf(
        Triple("dT24", "падение температуры за сутки", Factor.Kind.DELTA),
        Triple("tMean", "средняя температура", Factor.Kind.LEVEL),
        Triple("dP24", "падение атмосферного давления за сутки", Factor.Kind.DELTA),
    )

    fun sensitivity(measurements: List<MeasurementEntity>, weather: List<WeatherEntity>): Sensitivity {
        val valid = measurements.filter { it.valid }
        if (valid.size < Thresholds.CORRELATION_MIN_N) {
            return Sensitivity(valid.size, ready = false, factors = emptyList())
        }
        val deltas = WeatherMath.deltas(weather).associateBy { it.day }
        val tempByDay = weather.associate { it.day to it.tMean }
        val found = mutableListOf<Factor>()

        for ((field, label, kind) in FIELDS) {
            for (lag in Thresholds.LAGS_H) {
                val xs = mutableListOf<Double>()
                val ys = mutableListOf<Double>()
                for (m in valid) {
                    val shifted = shiftDay(m.localDate(), lag)
                    val value = when (field) {
                        "tMean" -> tempByDay[shifted]
                        else -> deltas[shifted]?.let {
                            if (field == "dT24") it.dT24 else it.dP24
                        }
                    }
                    if (value == null) continue
                    xs += value
                    ys += m.sys.toDouble()
                }
                val r = pearson(xs, ys) ?: continue
                if (kotlin.math.abs(r) < Thresholds.R_THRESHOLD) continue
                val slope = slope(xs, ys) ?: continue
                found += Factor(field, kind, label, lag, r, slope, xs.size)
            }
        }
        found.sortByDescending { kotlin.math.abs(it.r) }
        return Sensitivity(valid.size, ready = true, factors = found.take(6))
    }

    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        if (n < 3) return null
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx <= 0 || syy <= 0) return null
        return sxy / sqrt(sxx * syy)
    }

    fun slope(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        if (n < 3) return null
        val mx = xs.average()
        val my = ys.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - mx
            sxx += dx * dx
            sxy += dx * (ys[i] - my)
        }
        if (sxx <= 0) return null
        return sxy / sxx
    }

    private fun sqrt(v: Double) = kotlin.math.sqrt(v)

    fun shiftDay(day: String, lagHours: Int): String {
        if (lagHours == 0) return day
        return java.time.LocalDate.parse(day)
            .atStartOfDay()
            .minusHours(lagHours.toLong())
            .toLocalDate()
            .toString()
    }

    /** Прогнозный текст по сохранённому прогнозу. Только «мерь внимательнее», без советов по терапии. */
    fun forecastAlert(factors: List<Factor>, forecast: List<WeatherDelta>): List<String> {
        if (factors.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        for (factor in factors) {
            for (day in forecast) {
                val value = when (factor.field) {
                    "dT24" -> day.dT24
                    "dP24" -> day.dP24
                    else -> day.tMean
                } ?: continue
                val threshold = if (factor.kind == Factor.Kind.DELTA) 4.0 else 6.0
                if (kotlin.math.abs(value) < threshold) continue
                val effect = value * factor.slope
                if (kotlin.math.abs(effect) < 4) continue
                lines += "%s: %s %+.0f → по вашей статистике систолическое %+.0f мм рт.ст. " +
                    "(r=%.2f, лаг %d ч, n=%d). Мерьте дважды, не пропускайте вечерний приём.".format(
                        day.day, factor.label, value, effect, factor.r, factor.lagHours, factor.n
                    )
            }
        }
        return lines.take(3)
    }
}
