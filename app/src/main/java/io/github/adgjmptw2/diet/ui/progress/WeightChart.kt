package io.github.adgjmptw2.diet.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.WeightAverage
import io.github.adgjmptw2.diet.domain.WeightEntry
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun WeightChart(
    entries: List<WeightEntry>,
    averages: List<WeightAverage>,
    targetKg: Double,
    from: LocalDate,
    to: LocalDate,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val points = remember(entries, from, to) { entries.filter { it.date in from..to }.sortedBy { it.date } }
    val summary = when (points.size) {
        0 -> "$periodLabel 체중 기록 없음"
        1 -> "$periodLabel 체중 ${points.first().weightKg.kg()} kg, 기록 1개. 기록 2개부터 추이가 보여요"
        else -> "$periodLabel ${points.first().weightKg.kg()} kg에서 ${points.last().weightKg.kg()} kg. " +
            "체중 ${points.size}회 기록. 굵은 선은 7일 평균, 점선은 목표 ${targetKg.kg()} kg"
    }
    if (points.size <= 1) {
        Box(
            modifier.fillMaxWidth().height(200.dp).clearAndSetSemantics { contentDescription = summary },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (points.isEmpty()) "이 기간의 체중 기록 없음" else "체중 기록 2개부터 추이가 보여요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val visibleAverages = remember(averages, from, to) { averages.filter { it.date in from..to } }
    val scale = remember(points, visibleAverages, targetKg) {
        chartScale(points.map { it.weightKg } + visibleAverages.mapNotNull { it.avgKg } + targetKg)
    }
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.primary
    val pointColor = colors.onSurface
    val targetColor = colors.tertiary
    val gridColor = colors.outlineVariant
    val labelStyle = MaterialTheme.typography.bodySmall.tabularNumbers().copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val yLabels = scale.ticks.map { measurer.measure("${it.kg()} kg", labelStyle) }
    val dateFormat = remember(from, to) { DateTimeFormatter.ofPattern(if (from.year == to.year) "M/d" else "yy/M/d") }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(248.dp).clearAndSetSemantics { contentDescription = summary }) {
            val left = yLabels.maxOf { it.size.width }.toFloat() + 8.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val top = yLabels.maxOf { it.size.height } / 2f + 4.dp.toPx()
            val bottom = size.height - yLabels.maxOf { it.size.height } - 12.dp.toPx()
            val width = right - left
            val height = bottom - top
            if (width <= 0f || height <= 0f) return@Canvas
            val dayCount = (to.toEpochDay() - from.toEpochDay()).coerceAtLeast(1)

            fun x(date: LocalDate): Float = left + (date.toEpochDay() - from.toEpochDay()).toFloat() / dayCount * width
            fun y(kg: Double): Float = bottom - ((kg - scale.min) / (scale.max - scale.min)).toFloat() * height

            scale.ticks.forEachIndexed { index, value ->
                val position = y(value)
                drawLine(gridColor, Offset(left, position), Offset(right, position), strokeWidth = 0.5.dp.toPx())
                val label = yLabels[index]
                drawText(label, topLeft = Offset(left - label.size.width - 8.dp.toPx(), position - label.size.height / 2f))
            }
            drawLine(
                color = targetColor,
                start = Offset(left, y(targetKg)),
                end = Offset(right, y(targetKg)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            val path = Path()
            var continuing = false
            visibleAverages.forEach { average ->
                val kg = average.avgKg
                if (kg == null) {
                    continuing = false
                } else {
                    if (continuing) path.lineTo(x(average.date), y(kg)) else path.moveTo(x(average.date), y(kg))
                    continuing = true
                }
            }
            drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { entry -> drawCircle(pointColor, radius = 3.dp.toPx(), center = Offset(x(entry.date), y(entry.weightKg))) }

            val middle = from.plusDays(dayCount / 2)
            val candidates = listOf(from, middle, to).distinct()
            val widestDate = candidates.maxOf { measurer.measure(it.format(dateFormat), labelStyle).size.width }
            val dates = if (width >= widestDate * 3 + 24.dp.toPx()) candidates else listOf(from, to).distinct()
            dates.forEach { date ->
                val label = measurer.measure(date.format(dateFormat), labelStyle)
                val labelX = (x(date) - label.size.width / 2f).coerceIn(left, (right - label.size.width).coerceAtLeast(left))
                drawText(label, topLeft = Offset(labelX, bottom + 8.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartLegend("실제 기록", pointColor, kind = 0, modifier = Modifier.weight(1f))
            ChartLegend("7일 평균", lineColor, kind = 1, modifier = Modifier.weight(1f))
            ChartLegend("목표 ${targetKg.kg()} kg", targetColor, kind = 2, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color, kind: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(16.dp).padding(vertical = 4.dp)) {
            if (kind == 0) {
                drawCircle(color, radius = 2.5.dp.toPx())
            } else {
                drawLine(
                    color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = if (kind == 1) 3.dp.toPx() else 1.5.dp.toPx(),
                    pathEffect = if (kind == 2) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class ChartScale(val min: Double, val max: Double, val ticks: List<Double>)

/** 목표선을 포함하며 축 눈금을 3~5개로 유지한다. */
private fun chartScale(values: List<Double>): ChartScale {
    val min = values.min()
    val max = values.max()
    val padding = ((max - min) * 0.08).coerceAtLeast(0.2)
    val low = min - padding
    val high = max + padding
    var step = niceStep((high - low) / 4)
    while (ceil(high / step) - floor(low / step) > 4) step = niceStep(step * 1.01)
    val bottom = floor(low / step) * step
    val top = ceil(high / step) * step
    val intervals = ((top - bottom) / step).roundToInt().coerceIn(2, 4)
    val ticks = (0..intervals).map { bottom + it * step }
    return ChartScale(ticks.first(), ticks.last(), ticks)
}

private fun niceStep(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    return doubleArrayOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
}
