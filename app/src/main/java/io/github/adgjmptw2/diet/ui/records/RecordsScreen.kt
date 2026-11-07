package io.github.adgjmptw2.diet.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    BackHandler(enabled = isActive && selectedStart != null) { selectedStart = null }

    val start = selectedStart?.let(LocalDate::ofEpochDay)
    if (start != null) {
        WeekDetailScreen(start = start, state = s, onBack = { selectedStart = null })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("기록", style = MaterialTheme.typography.headlineLarge) }
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
                Text("기록 없음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                "체중 ${week.weighDays}일 · 식단 ${week.mealDays}일",
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
