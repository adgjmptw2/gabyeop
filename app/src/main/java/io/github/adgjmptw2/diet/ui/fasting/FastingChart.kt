package io.github.adgjmptw2.diet.ui.fasting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.domain.FastingGraphBar
import io.github.adgjmptw2.diet.domain.FastingGraphSummary
import io.github.adgjmptw2.diet.domain.GraphPeriod
import io.github.adgjmptw2.diet.domain.fastingAxisMaximum
import io.github.adgjmptw2.diet.domain.fastingGraphPeriods
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FastingPeriodSelector(period: GraphPeriod, onPeriod: (GraphPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 260.dp).fillMaxWidth()) {
        fastingGraphPeriods.forEachIndexed { index, option ->
            SegmentedButton(selected = period == option, onClick = { onPeriod(option) },
                shape = SegmentedButtonDefaults.itemShape(index, fastingGraphPeriods.size)) {
                Text(when (option) { GraphPeriod.WEEK -> "주"; GraphPeriod.MONTH -> "월"; else -> "년" })
            }
        }
    }
}

@Composable
fun FastingChart(summary: FastingGraphSummary, targetHours: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val monthly = summary.period == GraphPeriod.YEAR
    var selectedEpoch by rememberSaveable(summary.period) { mutableStateOf<Long?>(null) }
    val selectedBar = summary.bars.firstOrNull { it.date.toEpochDay() == selectedEpoch } ?: summary.bars.lastOrNull()
    val scroll = rememberScrollState()
    val maximum = fastingAxisMaximum(summary.bars.map { it.durationMillis?.div(Fasting.HOUR_MS) }, targetHours.toDouble())
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.tabularNumbers().copy(color = colors.onSurfaceVariant)
    val average = summary.averageHours?.let { "${String.format(Locale.KOREA, "%.1f", it)}시간" } ?: "—"
    LaunchedEffect(summary.period, summary.bars.lastOrNull()?.date, scroll.maxValue) {
        scroll.scrollTo(scroll.maxValue)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (monthly) "하루 평균 시간" else "하루 시간", modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant)
        Text(selectedBar?.let { fastingBarLabel(it, monthly) } ?: "기록 없음",
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            style = MaterialTheme.typography.labelMedium.tabularNumbers(), textAlign = TextAlign.Center,
            color = if (selectedBar == null) colors.onSurfaceVariant else colors.onSurface)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val axisWidth = 48.dp
            // 좁은 화면에서도 주 보기의 일곱 막대가 한 번에 보인다.
            val slotWidth = ((maxWidth - axisWidth) / 7).coerceIn(16.dp, 40.dp)
            val barWidth = (slotWidth - 12.dp).coerceIn(8.dp, 28.dp)
            val plotHeight = 220.dp
            val inset = 12.dp
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Box(Modifier.width((slotWidth * summary.bars.size).coerceAtLeast(maxWidth - axisWidth))) {
                        Row(Modifier.align(Alignment.TopEnd)) {
                            summary.bars.forEach { bar ->
                                val isSelected = bar == selectedBar
                                Column(Modifier.width(slotWidth)
                                    .clickable(role = Role.Button, onClickLabel = "시간 보기") { selectedEpoch = bar.date.toEpochDay() }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = fastingBarLabel(bar, monthly)
                                        selected = isSelected
                                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.width(barWidth).height(plotHeight)) {
                                        val top = inset.toPx()
                                        val height = size.height - top * 2
                                        val radius = CornerRadius(size.width / 2)
                                        val trackSize = Size(size.width, height)
                                        drawRoundRect(colors.surfaceContainerHigh, Offset(0f, top), trackSize, radius)
                                        val duration = bar.durationMillis
                                        if (duration != null && duration > 0) {
                                            val totalHeight = (duration / Fasting.HOUR_MS / maximum).toFloat().coerceIn(0f, 1f) * height
                                            val met = duration >= targetHours * Fasting.HOUR_MS
                                            val color = colors.primary.copy(alpha = if (met) 1f else 0.6f)
                                            drawRoundRect(if (bar.ongoing) colors.primary.copy(alpha = 0.35f) else color,
                                                Offset(0f, top + height - totalHeight), Size(size.width, totalHeight), radius)
                                            if (bar.ongoing && bar.completedMillis != null && bar.completedMillis > 0) {
                                                val finishedHeight = (bar.completedMillis / Fasting.HOUR_MS / maximum).toFloat().coerceIn(0f, 1f) * height
                                                drawRoundRect(color, Offset(0f, top + height - finishedHeight),
                                                    Size(size.width, finishedHeight), radius)
                                            }
                                        }
                                        if (isSelected) {
                                            val border = 1.dp.toPx()
                                            drawRoundRect(colors.tertiary, Offset(border, top + border),
                                                Size((size.width - border * 2).coerceAtLeast(0f), height - border * 2),
                                                radius, style = Stroke(border))
                                        }
                                    }
                                    Column(Modifier.heightIn(min = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (monthly) {
                                            Text("${bar.date.monthValue}월", style = labelStyle)
                                            Text(bar.date.year.toString(), style = labelStyle)
                                        } else {
                                            Text(bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN), style = labelStyle)
                                            Text(bar.date.dayOfMonth.toString(), style = labelStyle)
                                            Text("${bar.date.monthValue}월", style = labelStyle)
                                        }
                                    }
                                }
                            }
                        }
                        Canvas(Modifier.fillMaxWidth().height(plotHeight)) {
                            val y = inset.toPx() + (1 - targetHours / maximum).toFloat() * (size.height - inset.toPx() * 2)
                            if (targetHours > 0) drawLine(colors.tertiary, Offset(0f, y), Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                        }
                    }
                }
                Canvas(Modifier.width(axisWidth).height(plotHeight)) {
                    val top = inset.toPx()
                    val height = size.height - top * 2
                    // 큰 값에서는 눈금 간격도 늘려 라벨이 겹치지 않게 한다.
                    val step = kotlin.math.ceil(maximum / 4 / 6).toInt().coerceAtLeast(1) * 6
                    val ticks = ((0..maximum.toInt() step step).toList() + maximum.toInt()).distinct()
                    ticks.forEach { value ->
                        val label = measurer.measure(if (value == maximum.toInt()) "${value}시간" else value.toString(), labelStyle)
                        val y = top + (1 - value / maximum).toFloat() * height
                        drawText(label, topLeft = Offset(6.dp.toPx(), y - label.size.height / 2f))
                    }
                }
            }
        }
        Text("평균 $average · " + if (monthly) "단식 ${summary.fastingDays}일"
            else "목표 달성 ${summary.achievedDays}/${summary.bars.size}일",
            style = MaterialTheme.typography.bodySmall.tabularNumbers())
        Text(if (summary.bars.any { it.ongoing }) "연한 막대는 단식 중 · 점선은 목표 ${targetHours}시간" else "점선은 목표 ${targetHours}시간",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

private fun fastingBarLabel(bar: FastingGraphBar, monthly: Boolean): String {
    val date = if (monthly) "${bar.date.year}년 ${bar.date.monthValue}월" else
        bar.date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
    val duration = bar.durationMillis?.let { (if (monthly) "평균 " else "") + Fasting.durationText(it.toLong()) } ?: "기록 없음"
    return "$date · $duration" + if (bar.ongoing) " · 단식 중" else ""
}
