package ru.chronicnotebook.ui

import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.chronicnotebook.domain.ChartPoint

class PressureChartTest {

    private fun targetAt(x: Double): LineCartesianLayerMarkerTarget =
        object : LineCartesianLayerMarkerTarget {
            override val x: Double = x
            override val canvasX: Float = 0f
            override val points: List<LineCartesianLayerMarkerTarget.Point> = listOf(
                LineCartesianLayerMarkerTarget.Point(
                    LineCartesianLayerModel.Entry(x, 128.0),
                    0f,
                    0,
                )
            )
        }

    @Test
    fun markerLabelDescribesNearestPoint() {
        val points = listOf(
            ChartPoint(1, 1_700_000_000_000, 120, 80, 70),
            ChartPoint(2, 1_700_000_100_000, 145, 92, null),
        )
        val label = chartMarkerLabel(points, mapOf(2L to listOf("кофе")), listOf(targetAt(1_700_000_090_000.0)))

        assertTrue(label.contains("145/92"))
        assertTrue(label.contains("#кофе"))
    }
}
