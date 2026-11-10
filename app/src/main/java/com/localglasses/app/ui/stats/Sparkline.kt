package com.localglasses.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object SparklineData {

    fun downsample(values: List<Double?>, maxPoints: Int = 120): List<Double?> {
        require(maxPoints > 0) { "maxPoints must be positive" }
        if (values.size <= maxPoints) return values
        if (maxPoints == 1) return listOf(values.first())
        return List(maxPoints) { index ->
            val source = (index.toLong() * (values.size - 1) / (maxPoints - 1)).toInt()
            values[source]
        }
    }

    /** Index ranges of contiguous non-null runs, so nulls become real gaps. */
    fun segments(values: List<Double?>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start: Int? = null
        values.forEachIndexed { index, value ->
            if (value == null) {
                start?.let { result += it..(index - 1) }
                start = null
            } else if (start == null) {
                start = index
            }
        }
        start?.let { result += it..(values.lastIndex) }
        return result
    }
}

@Composable
fun Sparkline(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val data = remember(values) { SparklineData.downsample(values) }
    val segments = remember(data) { SparklineData.segments(data) }
    val numbers = remember(data) { data.filterNotNull() }
    val minimum = numbers.minOrNull()
    val maximum = numbers.maxOrNull()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("sparkline"),
    ) {
        if (numbers.isEmpty() || minimum == null || maximum == null) return@Canvas
        val min = minimum
        val max = maximum
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = if (data.size <= 1) 0f else size.width / (data.size - 1)

        fun point(index: Int, value: Double) = androidx.compose.ui.geometry.Offset(
            x = index * stepX,
            y = (size.height - ((value - min) / span * size.height)).toFloat(),
        )

        segments.forEach { range ->
            if (range.first == range.last) {
                val value = data[range.first] ?: return@forEach
                drawCircle(
                    color = color,
                    radius = 3.dp.toPx(),
                    center = point(range.first, value),
                )
            } else {
                val path = Path()
                range.forEachIndexed { offset, index ->
                    val value = data[index] ?: return@forEachIndexed
                    val p = point(index, value)
                    if (offset == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
