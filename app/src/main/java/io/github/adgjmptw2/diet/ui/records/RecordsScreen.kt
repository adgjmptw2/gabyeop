package io.github.adgjmptw2.diet.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.domain.DayCell
import io.github.adgjmptw2.diet.domain.WeekSummary
import io.github.adgjmptw2.diet.ui.common.absKg
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.GainColor
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun RecordsScreen(isActive: Boolean = true, vm: RecordsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    var selectedStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(enabled = isActive && (selectedDay != null || selectedStart != null)) {
        if (selectedDay != null) selectedDay = null else selectedStart = null
    }

    val start = selectedStart?.let(LocalDate::ofEpochDay)
    val day = selectedDay?.let(LocalDate::ofEpochDay)
    if (day != null) {
        val detail by remember(day) { vm.dayDetail(day) }.collectAsStateWithLifecycle(initialValue = null)
        if (detail != null) DayDetailScreen(detail!!, onBack = { selectedDay = null }, vm = vm)
        return
    }
    if (start != null) {
        WeekDetailScreen(
            start = start, state = s, onBack = { selectedStart = null },
            onDayClick = { selectedDay = it.toEpochDay() },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("기록", style = MaterialTheme.typography.headlineLarge) }
        item {
            ThisWeekCard(
                cells = s.weekCells, week = s.thisWeek,
                weights = s.days.mapValues { it.value.weightKg },
                onDayClick = { selectedDay = it.toEpochDay() },
            )
        }
        if (s.weeks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        "기록 없음", modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(s.weeks, key = { it.start.toEpochDay() }) { week ->
            WeekCard(week, onClick = { selectedStart = week.start.toEpochDay() })
        }
    }
}

internal fun weekTitle(start: LocalDate): String {
    val end = start.plusDays(6)
    val weekOfMonth = (start.dayOfMonth - 1) / 7 + 1
    return "${start.monthValue}월 ${weekOfMonth}주 (${start.monthValue}/${start.dayOfMonth}~${end.monthValue}/${end.dayOfMonth})"
}

@Composable
private fun ThisWeekCard(
    cells: List<DayCell>,
    week: WeekSummary?,
    weights: Map<LocalDate, Double?>,
    onDayClick: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("이번 주", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.forEach { cell ->
                    WeekDayCell(
                        cell, weightKg = weights[cell.date], onClick = { onDayClick(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val change = week?.changeKg
            Text(
                week?.avgKg?.let { "평균 ${it.kg()} kg" } ?: "평균 체중 없음",
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            )
            Text(
                when {
                    week == null -> "이번 주 기록 없음"
                    change == null -> "지난주와 비교할 기록 없음"
                    change < -0.05 -> "지난주 대비 ▼${change.absKg()} kg"
                    change > 0.05 -> "지난주 대비 ▲${change.absKg()} kg"
                    else -> "지난주와 같음"
                },
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekDayCell(cell: DayCell, weightKg: Double?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (cell.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val noteDot = cell.hasNote
    Column(
        modifier = modifier.heightIn(min = 72.dp)
            .then(if (cell.enabled) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            cell.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN),
            style = MaterialTheme.typography.labelSmall, color = color,
        )
        Text(
            if (cell.isToday) "오늘" else cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = if (cell.isToday) MaterialTheme.colorScheme.primary else color,
        )
        Text(
            weightKg?.kg() ?: "+",
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = if (weightKg != null) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
        if (noteDot) {
            Box(
                Modifier.size(4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Box(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun WeekCard(week: WeekSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(weekTitle(week.start), style = MaterialTheme.typography.titleMedium.tabularNumbers())
            if (week.weighDays == 0 && week.mealDays == 0) {
                if (week.noteDays > 0) {
                    Text("메모 ${week.noteDays}일", style = MaterialTheme.typography.bodyMedium.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("기록 없음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("평균 체중", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            week.avgKg?.let { "${it.kg()} kg" } ?: "기록 없음",
                            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                        )
                    }
                    val change = week.changeKg
                    val text = when {
                        change == null -> "비교 기록 없음"
                        change < -0.05 -> "▼${change.absKg()} kg"
                        change > 0.05 -> "▲${change.absKg()} kg"
                        else -> "변화 없음"
                    }
                    val color = when {
                        change == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        change < -0.05 -> MaterialTheme.colorScheme.primary
                        change > 0.05 -> GainColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text, style = MaterialTheme.typography.bodyMedium.tabularNumbers(), color = color,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "체중 ${week.weighDays}일 · 식단 ${week.mealDays}일" +
                    if (week.noteDays > 0) " · 메모 ${week.noteDays}일" else "",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                week.avgKcal?.let { "하루 평균 ${it.roundToInt().comma()} kcal" } ?: "식단 기록 없음",
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
