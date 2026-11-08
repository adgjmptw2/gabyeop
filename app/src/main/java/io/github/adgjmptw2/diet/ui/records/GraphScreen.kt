package io.github.adgjmptw2.diet.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.domain.GraphDay
import io.github.adgjmptw2.diet.domain.GraphMetric
import io.github.adgjmptw2.diet.domain.GraphPeriod
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.WaterAmountDialog
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GraphScreen(metricRequest: Long = 0, requestedMetric: GraphMetric = GraphMetric.WATER,
    vm: GraphViewModel = hiltViewModel()) {
    var metric by rememberSaveable { mutableStateOf(if (metricRequest > 0) requestedMetric else GraphMetric.WATER) }
    var period by rememberSaveable { mutableStateOf(GraphPeriod.WEEK) }
    var handled by rememberSaveable { mutableLongStateOf(0) }
    var selectedEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(metricRequest) {
        if (metricRequest > handled) { metric = requestedMetric; handled = metricRequest; selectedEpoch = null; editingEpoch = null }
    }
    LaunchedEffect(metric, period) { vm.select(metric, period) }
    val state by vm.state.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphMetric.entries.forEach { option ->
                    FilterChip(selected = metric == option, onClick = { metric = option; selectedEpoch = null; editingEpoch = null },
                        label = { Text(option.label) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphPeriod.entries.forEach { option ->
                    FilterChip(selected = period == option, onClick = { period = option; selectedEpoch = null; editingEpoch = null },
                        label = { Text(option.label) })
                }
            }
        }
        val s = state?.takeIf { it.metric == metric && it.period == period }
        if (s == null) item { Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() } }
        else {
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("기록한 날 평균", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(s.summary.average?.let { graphValue(metric, it) } ?: "—",
                                    style = MaterialTheme.typography.titleLarge.tabularNumbers())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("목표 달성", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${s.summary.achievedDays}/${s.summary.days.size}일", style = MaterialTheme.typography.titleLarge.tabularNumbers())
                            }
                        }
                        Text("목표 ${graphValue(metric, s.target)}", style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.tertiary)
                        DailyBarChart(s, selectedEpoch?.let(LocalDate::ofEpochDay)) { day ->
                            selectedEpoch = day.date.toEpochDay()
                            if (metric == GraphMetric.WATER) editingEpoch = selectedEpoch
                        }
                        Text(if (metric == GraphMetric.CALORIES) "점선은 목표 · 목표 이하인 날을 세요" else "점선은 목표",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("빈칸은 기록 없는 날", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val selected = selectedEpoch?.let(LocalDate::ofEpochDay)?.let { date -> s.summary.days.firstOrNull { it.date == date } }
                        val current = selected ?: s.summary.days.last()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val index = s.summary.days.indexOf(current)
                            TextButton(enabled = index > 0, onClick = { selectedEpoch = s.summary.days[index - 1].date.toEpochDay() }) { Text("◀") }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(current.date.format(DayLabel), style = MaterialTheme.typography.labelMedium.tabularNumbers())
                                Text(current.value?.let { graphValue(metric, it) } ?: "기록 없음", style = MaterialTheme.typography.titleSmall.tabularNumbers())
                            }
                            TextButton(enabled = index < s.summary.days.lastIndex,
                                onClick = { selectedEpoch = s.summary.days[index + 1].date.toEpochDay() }) { Text("▶") }
                        }
                        if (metric == GraphMetric.WATER) TextButton(onClick = { editingEpoch = current.date.toEpochDay() },
                            modifier = Modifier.fillMaxWidth()) { Text("마신 물 수정") }
                        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    val s = state
    val editing = editingEpoch?.let(LocalDate::ofEpochDay)
    if (editing != null && s != null && metric == GraphMetric.WATER && s.metric == metric) {
        WaterAmountDialog(editing, s.to, s.summary.days.firstOrNull { it.date == editing }?.value?.roundToInt() ?: 0,
            { editingEpoch = null }, { vm.setWater(editing, it); editingEpoch = null })
    }
}

private fun graphValue(metric: GraphMetric, value: Double): String = when (metric) {
    GraphMetric.WATER -> String.format(Locale.KOREA, "%.1f L", value / 1000)
    GraphMetric.CALORIES -> "${value.roundToInt().comma()} kcal"
    GraphMetric.STEPS -> "${value.roundToInt().comma()}걸음"
}

@Composable
private fun DailyBarChart(state: GraphUiState, selected: LocalDate?, onDay: (GraphDay) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val days = state.summary.days
    val maximum = maxOf(state.target, days.mapNotNull { it.value }.maxOrNull() ?: 0.0, 1.0) * 1.15
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.tabularNumbers().copy(color = colors.onSurfaceVariant)
    val axisStyle = MaterialTheme.typography.labelSmall.tabularNumbers().copy(color = colors.tertiary)
    val dateLabel = DateTimeFormatter.ofPattern("M/d")
    val onSelect by rememberUpdatedState(onDay)
    Column {
        Canvas(Modifier.fillMaxWidth().height(240.dp)
            .semantics { contentDescription = "${state.metric.label} ${days.size}일, ${state.summary.recordedDays}일 기록, 목표 달성 ${state.summary.achievedDays}일. 아래 날짜 버튼으로 값을 볼 수 있어요" }
            .pointerInput(days) {
                detectTapGestures { point ->
                    val left = 36.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    if (point.x in left..right && point.y < size.height - 28.dp.toPx()) {
                        val index = ((point.x - left) / (right - left).coerceAtLeast(1f) * days.size).toInt().coerceIn(days.indices)
                        onSelect(days[index])
                    }
                }
            }) {
            val left = 36.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 16.dp.toPx()
            val bottom = size.height - 28.dp.toPx()
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            val slot = width / days.size
            fun y(value: Double) = bottom - (value / maximum).toFloat() * height
            listOf(0.0, maximum / 2, maximum).forEach { value ->
                val position = y(value)
                drawLine(colors.outlineVariant, Offset(left, position), Offset(right, position), 0.5.dp.toPx())
                val text = if (state.metric == GraphMetric.WATER) String.format(Locale.KOREA, "%.1f", value / 1000)
                    else if (value >= 10_000) String.format(Locale.KOREA, "%.0fk", value / 1000) else value.roundToInt().toString()
                val label = measurer.measure(text, labelStyle)
                drawText(label, topLeft = Offset((left - label.size.width - 4.dp.toPx()).coerceAtLeast(0f), position - label.size.height / 2f))
            }
            if (state.target > 0) drawLine(colors.tertiary, Offset(left, y(state.target)), Offset(right, y(state.target)),
                1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            days.forEachIndexed { i, day ->
                val value = day.value ?: return@forEachIndexed
                val x = left + slot * (i + 0.5f)
                val barWidth = (slot * 0.65f).coerceAtMost(24.dp.toPx())
                if (value == 0.0) {
                    drawCircle(if (day.date == selected) colors.tertiary else colors.primary, (barWidth / 2).coerceAtMost(2.dp.toPx()), Offset(x, bottom))
                } else drawRect(if (day.date == selected) colors.tertiary else colors.primary,
                    Offset(x - barWidth / 2, y(value)), Size(barWidth, bottom - y(value)))
            }
            listOf(0, days.lastIndex / 2, days.lastIndex).distinct().forEach { i ->
                val label = measurer.measure(days[i].date.format(dateLabel), labelStyle)
                val x = (left + slot * (i + 0.5f) - label.size.width / 2f).coerceIn(left, (right - label.size.width).coerceAtLeast(left))
                drawText(label, topLeft = Offset(x, bottom + 8.dp.toPx()))
            }
            if (state.summary.recordedDays == 0) {
                val label = measurer.measure("이 기간의 기록 없음", axisStyle)
                drawText(label, topLeft = Offset(left + (width - label.size.width) / 2, top + height / 2))
            }
        }
    }
}

