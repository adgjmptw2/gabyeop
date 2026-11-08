package io.github.adgjmptw2.diet.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import io.github.adgjmptw2.diet.R
import java.io.File
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.MealType
import io.github.adgjmptw2.diet.domain.Nutrition
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun WeekDetailScreen(
    start: LocalDate,
    state: RecordsUiState,
    onBack: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val days = remember(start, state) {
        (0L..6L).map { start.plusDays(it) }.filter { it <= state.today }.asReversed().map { date ->
            state.days[date] ?: RecordDay(date, null, emptyList())
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("뒤로") }
                Text("주간 기록", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${start.year}년 ${weekTitle(start)}",
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day, memo = state.notes[day.date]?.memo.orEmpty(), onClick = { onDayClick(day.date) })
        }
    }
}

@Composable
internal fun DayCard(day: RecordDay, memo: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(day.date.format(DayLabel), style = MaterialTheme.typography.titleMedium.tabularNumbers())
            if (memo.isNotBlank()) Text(memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (day.weightKg == null && day.meals.isEmpty() && memo.isBlank()) {
                Text("기록 없음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    day.weightKg?.let { "체중 ${it.kg()} kg" } ?: "체중 기록 없음",
                    style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    color = if (day.weightKg != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (day.meals.isEmpty()) {
                    Text("식단 기록 없음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MealType.entries.forEach { type ->
                        val meals = day.meals.filter { it.type == type }
                        if (meals.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                meals.forEach { meal -> MealRow(meal) }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "하루 합계 ${Nutrition.total(day.meals).kcal.roundToInt().comma()} kcal",
                        style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MealRow(meal: MealEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(meal.emoji.ifBlank { "🍽" }, style = MaterialTheme.typography.titleLarge)
        Text(meal.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "${meal.kcal.roundToInt().comma()} kcal",
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
        meal.photoPath?.let { path ->
            AsyncImage(
                model = File(path), contentDescription = "식단 사진", contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_meal),
                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
}
