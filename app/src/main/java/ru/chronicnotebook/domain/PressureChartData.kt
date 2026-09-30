package ru.chronicnotebook.domain

/**
 * Данные для графика давления без привязки к базе и библиотеке графика.
 *
 * Фильтры живут здесь, а не в composable: их можно проверить обычным тестом,
 * а сам график остаётся тонкой обёрткой над выбранными точками.
 */
data class ChartMeasurement(
    val id: Long,
    val takenAt: Long,
    val sys: Int,
    val dia: Int,
    val pulse: Int?,
    val valid: Boolean,
)

data class ChartPoint(
    val id: Long,
    val takenAt: Long,
    val sys: Int,
    val dia: Int,
    val pulse: Int?,
)

enum class ChartRange(val label: String, val days: Int?, val axisStepDays: Int) {
    WEEK("7 дней", 7, 1),
    MONTH("30 дней", 30, 7),
    QUARTER("90 дней", 90, 14),
    ALL("Всё время", null, 30),
}

/**
 * Выбор точек под текущие настройки графика.
 *
 * Метки работают как «или»: выбраны «стресс» и «кофе» — видны точки с любой из
 * них. Пустой выбор меток фильтр не включает.
 */
fun selectChartPoints(
    rows: List<ChartMeasurement>,
    tagsByMeasurement: Map<Long, Set<Long>>,
    selectedTagIds: Set<Long>,
    includeInvalid: Boolean,
    days: Int?,
    nowMillis: Long = System.currentTimeMillis(),
): List<ChartPoint> {
    val since = days?.let { nowMillis - it * 24L * 3600_000 }
    return rows.asSequence()
        .filter { includeInvalid || it.valid }
        .filter { since == null || it.takenAt >= since }
        .filter { selectedTagIds.isEmpty() || tagsByMeasurement[it.id].orEmpty().any(selectedTagIds::contains) }
        .sortedBy { it.takenAt }
        .map { ChartPoint(it.id, it.takenAt, it.sys, it.dia, it.pulse) }
        .toList()
}

/**
 * Уменьшение числа точек для слабых устройств.
 *
 * Обычный дневник — десятки точек, и лимит его не трогает. При сотнях точек
 * берём равномерную выборку плюс последнюю: иначе линия на графике дергалась
 * бы и тянула зум.
 */
fun limitChartPoints(points: List<ChartPoint>, maxPoints: Int = 500): List<ChartPoint> {
    require(maxPoints >= 2) { "Нужно место хотя бы для двух точек" }
    if (points.size <= maxPoints) return points
    val stride = kotlin.math.ceil(points.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val sampled = points.filterIndexed { index, _ -> index % stride == 0 }.toMutableList()
    if (sampled.last() != points.last()) {
        sampled[sampled.lastIndex] = points.last()
    }
    return sampled
}
