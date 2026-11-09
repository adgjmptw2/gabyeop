package io.github.adgjmptw2.diet.ui.meals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.MealType
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.MacroSummary
import io.github.adgjmptw2.diet.ui.common.MacroGoalSheet
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.supplements.SupplementCard
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.io.File
import java.time.LocalDate

private const val DAY_MILLIS = 86_400_000L

@Composable
fun MealsScreen(vm: MealsViewModel = hiltViewModel(), tracking: TrackingViewModel = hiltViewModel(), openAddRequest: Long = 0) {
    val state by vm.state.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val goals by tracking.macroGoals.collectAsStateWithLifecycle()
    val trackingError by tracking.error.collectAsStateWithLifecycle()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var editingGoals by rememberSaveable { mutableStateOf(false) }
    var handledAdd by rememberSaveable { mutableLongStateOf(0) }
    LaunchedEffect(openAddRequest) {
        if (openAddRequest > handledAdd) {
            vm.selectDate(state.today)
            val type = when (java.time.LocalTime.now().hour) {
                in 5..10 -> MealType.BREAKFAST
                in 11..15 -> MealType.LUNCH
                in 16..21 -> MealType.DINNER
                else -> MealType.SNACK
            }
            vm.add(type)
            handledAdd = openAddRequest
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("식단", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { vm.moveDate(-1) },
                    modifier = Modifier.semantics { contentDescription = "이전 날짜" },
                ) { Text("◀", fontFamily = FontFamily.Default) }
                TextButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) {
                    val label = if (state.date == state.today) "오늘 · ${state.date.format(DayLabel)}" else state.date.format(DayLabel)
                    Text(label, style = MaterialTheme.typography.bodyMedium.tabularNumbers())
                }
                IconButton(
                    onClick = { vm.moveDate(1) },
                    enabled = state.date < state.today,
                    modifier = Modifier.semantics { contentDescription = "다음 날짜" },
                ) { Text("▶", fontFamily = FontFamily.Default) }
            }
        }
        item { NutritionCard(state) }
        item { MacroSummary(state.entries, goals, state.targetKcal ?: 0, { editingGoals = true }) }
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        trackingError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        MealType.entries.forEach { type ->
            item(key = type.name) {
                MealCard(type, state.entries.filter { it.type == type }, onAdd = { vm.add(type) }, onEdit = vm::edit)
            }
        }
        item { SupplementCard(state.date) }
    }

    if (pickingDate) {
        MealDatePicker(state.date, state.today, onSelect = vm::selectDate, onDismiss = { pickingDate = false })
    }
    if (editingGoals) MacroGoalSheet(goals, state.targetKcal ?: 0,
        { editingGoals = false }, { tracking.setMacroGoals(it); editingGoals = false })
    editor?.let { draft ->
        key(draft.session) { AddFoodSheet(draft, query, search, vm) }
    }
}

@Composable
private fun NutritionCard(state: MealsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.date == state.today) "오늘 먹은 양" else "먹은 양", style = MaterialTheme.typography.titleMedium)
            Text("${state.total.kcal.kcalText()} kcal", style = MaterialTheme.typography.headlineMedium.tabularNumbers())
            Text(
                "목표 ${state.targetKcal?.comma() ?: "—"} kcal",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val target = state.targetKcal ?: 0
            LinearProgressIndicator(
                progress = { if (target > 0) (state.total.kcal / target).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
internal fun MacroLabel(label: String, value: Double?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${value?.mealNumber() ?: "—"} g", style = MaterialTheme.typography.titleSmall.tabularNumbers())
    }
}

@Composable
private fun MealCard(type: MealType, entries: List<MealEntry>, onAdd: () -> Unit, onEdit: (MealEntry) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                Text("${entries.sumOf { it.kcal }.kcalText()} kcal", style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAdd) { Text("추가") }
        }
        if (entries.isEmpty()) {
            Text("기록 없음", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.forEach { entry ->
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                MealRow(entry) { onEdit(entry) }
            }
        }
    }
}

@Composable
private fun MealRow(entry: MealEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "기록 수정", onClick = onClick)
            .heightIn(min = 80.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(entry.emoji, fontSize = 28.sp, fontFamily = FontFamily.Default)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.amount.mealNumber()}${entry.unit} · ${entry.kcal.kcalText()} kcal",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.photoPath?.let { path ->
            AsyncImage(
                model = File(path), contentDescription = "식단 사진", contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_meal),
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDatePicker(date: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.toEpochDay() * DAY_MILLIS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toEpochDay() * DAY_MILLIS
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    DatePickerDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large, tonalElevation = 0.dp, colors = colors,
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis != null,
                onClick = {
                    picker.selectedDateMillis?.let { onSelect(LocalDate.ofEpochDay(it / DAY_MILLIS)) }
                    onDismiss()
                },
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) { DatePicker(state = picker, colors = colors) }
}
