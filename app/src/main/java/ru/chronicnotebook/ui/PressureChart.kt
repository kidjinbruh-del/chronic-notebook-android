package ru.chronicnotebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import ru.chronicnotebook.data.MeasurementEntity
import ru.chronicnotebook.data.MeasurementTagEntity
import ru.chronicnotebook.data.TagEntity
import ru.chronicnotebook.domain.ChartMeasurement
import ru.chronicnotebook.domain.ChartPoint
import ru.chronicnotebook.domain.ChartRange
import ru.chronicnotebook.domain.limitChartPoints
import ru.chronicnotebook.domain.selectChartPoints
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val DAY_MS = 86_400_000.0
private val axisDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM").withZone(ZoneId.systemDefault())
private val markerDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

/**
 * Интерактивный график давления.
 *
 * Две линии — верхнее и нижнее. Точка пальцем показывает дату, оба значения,
 * пульс и метки; график можно тянуть и масштабировать щипком. Диапазон и метки
 * выбираются выше, поэтому один и тот же экран отвечает и на «как было в этом
 * месяце», и на «что бывает после нагрузки».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PressureChartCard(
    measurements: List<MeasurementEntity>,
    allTags: List<TagEntity>,
    tagLinks: List<MeasurementTagEntity>,
) {
    var range by remember { mutableStateOf(ChartRange.MONTH) }
    var selectedTagIds by remember { mutableStateOf(emptySet<Long>()) }
    var includeInvalid by remember { mutableStateOf(false) }

    val tagsByMeasurement = remember(tagLinks) {
        tagLinks.groupBy({ it.measurementId }, { it.tagId }).mapValues { it.value.toSet() }
    }
    val tagNamesById = remember(allTags) { allTags.associate { it.id to it.name } }
    val tagNamesByMeasurement = remember(tagLinks, tagNamesById) {
        tagLinks.groupBy({ it.measurementId }, { tagNamesById[it.tagId].orEmpty() })
            .mapValues { entry -> entry.value.filter { it.isNotEmpty() }.distinct().sorted() }
    }
    val chartRows = remember(measurements) {
        measurements.map {
            ChartMeasurement(it.id, it.takenAt, it.sys, it.dia, it.pulse, it.valid)
        }
    }
    val points = remember(chartRows, tagsByMeasurement, selectedTagIds, includeInvalid, range) {
        limitChartPoints(
            selectChartPoints(
                rows = chartRows,
                tagsByMeasurement = tagsByMeasurement,
                selectedTagIds = selectedTagIds,
                includeInvalid = includeInvalid,
                days = range.days,
            ),
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("График давления", style = MaterialTheme.typography.titleMedium)
            Text(
                "Синяя линия — верхнее, зелёная — нижнее. Коснитесь точки, чтобы увидеть " +
                    "дату, пульс и метки; график тянется и масштабируется.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { range = option },
                        label = { Text(option.label) },
                    )
                }
            }

            if (allTags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = {
                                selectedTagIds =
                                    if (tag.id in selectedTagIds) selectedTagIds - tag.id
                                    else selectedTagIds + tag.id
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    TagDot(tag.colorArgb)
                                    Text(tag.name)
                                }
                            },
                        )
                    }
                }
            }

            FilterChip(
                selected = includeInvalid,
                onClick = { includeInvalid = !includeInvalid },
                label = { Text("Показывать сомнительные замеры") },
            )

            if (points.size < 2) {
                Text(
                    "Для линии нужно хотя бы два замера в выбранном диапазоне. " +
                        "Добавьте замер или расширьте период.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            PressureChart(
                points = points,
                tagNamesByMeasurement = tagNamesByMeasurement,
                labelEveryDays = range.axisStepDays,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(MaterialTheme.colorScheme.primary, "Верхнее")
                LegendDot(MaterialTheme.colorScheme.secondary, "Нижнее")
                Text(
                    "Пороговые линии 160 и 180 появляются, если такие значения есть в выборке",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TagDot(colorArgb = color.toArgb())
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PressureChart(
    points: List<ChartPoint>,
    tagNamesByMeasurement: Map<Long, List<String>>,
    labelEveryDays: Int,
) {
    val producer = remember { CartesianChartModelProducer() }
    // Ось X — миллисекунды, а шаг — день. Иначе Vico считал бы шагом НОД между
    // метками времени и пытался подписать каждую миллисекунду.
    val xs = remember(points) { points.map { it.takenAt.toDouble() } }
    val sysValues = remember(points) { points.map { it.sys } }
    val diaValues = remember(points) { points.map { it.dia } }

    LaunchedEffect(producer, xs, sysValues, diaValues) {
        producer.runTransaction {
            lineSeries {
                series(xs, sysValues)
                series(xs, diaValues)
            }
        }
    }

    val sysColor = MaterialTheme.colorScheme.primary
    val diaColor = MaterialTheme.colorScheme.secondary
    // Диапазон Y — по данным с запасом 15% и округлением до десятков.
    // По умолчанию Vico начинал ось с нуля, и вся линия прижималась кверху.
    val rangeProvider = remember {
        object : CartesianLayerRangeProvider {
            override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val span = (maxY - minY).coerceAtLeast(10.0)
                return floor((minY - span * 0.15) / 10.0) * 10.0
            }

            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
                val span = (maxY - minY).coerceAtLeast(10.0)
                return ceil((maxY + span * 0.15) / 10.0) * 10.0
            }
        }
    }
    val lineProvider = LineCartesianLayer.LineProvider.series(
        listOf(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(sysColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.point(
                        rememberShapeComponent(fill(sysColor), CorneredShape.Pill)
                    )
                ),
            ),
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(diaColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.point(
                        rememberShapeComponent(fill(diaColor), CorneredShape.Pill)
                    )
                ),
            ),
        )
    )
    val verticalFormatter = remember {
        CartesianValueFormatter { _, value, _ -> value.roundToInt().toString() }
    }
    val horizontalFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            axisDateFormat.format(Instant.ofEpochMilli(value.roundToLong()))
        }
    }
    val markerFormatter = remember(points, tagNamesByMeasurement) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            chartMarkerLabel(points, tagNamesByMeasurement, targets)
        }
    }
    val markerLabel = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface,
        textSize = 12.sp,
        lineCount = 4,
        padding = insets(10.dp, 6.dp),
        background = rememberShapeComponent(
            fill = fill(MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = CorneredShape.Pill,
        ),
    )
    val highLine = rememberThresholdLine(160.0, "Высокое 160", MaterialTheme.colorScheme.error)
    val crisisLine = rememberThresholdLine(180.0, "Кризис 180", MaterialTheme.colorScheme.error)
    val decorations = remember(points, highLine, crisisLine) {
        buildList {
            if (points.any { it.sys >= 155 }) add(highLine)
            if (points.any { it.sys >= 175 }) add(crisisLine)
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(lineProvider = lineProvider, rangeProvider = rangeProvider),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = verticalFormatter,
                itemPlacer = VerticalAxis.ItemPlacer.step({ 10.0 }),
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = horizontalFormatter,
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelEveryDays }),
            ),
            marker = rememberDefaultCartesianMarker(
                label = markerLabel,
                valueFormatter = markerFormatter,
                guideline = rememberAxisGuidelineComponent(),
            ),
            decorations = decorations,
            // Значения оси — миллисекунды, а шаг — день. Без этого шага Vico брал бы
            // НОД между метками времени и пытался подписать каждую миллисекунду.
            getXStep = { DAY_MS },
        ),
        modelProducer = producer,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        scrollState = rememberVicoScrollState(
            scrollEnabled = points.size > 14,
            initialScroll = Scroll.Absolute.End,
        ),
        zoomState = rememberVicoZoomState(zoomEnabled = points.size > 7),
    )
}

@Composable
private fun rememberThresholdLine(value: Double, label: String, color: Color): HorizontalLine {
    val line = rememberLineComponent(fill = fill(color), thickness = 1.5.dp)
    val labelComponent = rememberTextComponent(
        color = color,
        textSize = 11.sp,
        padding = insets(8.dp, 2.dp),
        background = rememberShapeComponent(
            fill = fill(color.copy(alpha = 0.12f)),
            shape = CorneredShape.Pill,
        ),
    )
    return remember(line, labelComponent, value, label) {
        HorizontalLine(
            y = { value },
            line = line,
            labelComponent = labelComponent,
            label = { label },
            // Слева живут подписи оси, поэтому плашка порога уезжает вправо:
            // иначе она накрывала первую высокую точку.
            horizontalLabelPosition = Position.Horizontal.End,
        )
    }
}

/**
 * Подпись выбранной точки.
 *
 * Vico отдаёт только координаты, поэтому ищем ближайший замер сами. Точка
 * обязана существовать: маркер не появляется без модели.
 */
internal fun chartMarkerLabel(
    points: List<ChartPoint>,
    tagNamesByMeasurement: Map<Long, List<String>>,
    targets: List<com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker.Target>,
): String {
    val x = targets.firstOrNull { it is LineCartesianLayerMarkerTarget }?.x
        ?: return "Замер не найден"
    val point = points.minByOrNull { abs(it.takenAt - x) } ?: return "Замер не найден"
    return buildString {
        append(markerDateFormat.format(Instant.ofEpochMilli(point.takenAt)))
        append("\n${point.sys}/${point.dia}")
        point.pulse?.let { append(" · $it уд/мин") }
        val tags = tagNamesByMeasurement[point.id].orEmpty()
        if (tags.isNotEmpty()) append("\n#${tags.joinToString(" #")}")
    }
}
