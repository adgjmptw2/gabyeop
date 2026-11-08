package io.github.adgjmptw2.diet.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.domain.*
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class CalendarView(val label: String) { DAY("일"), WEEK("주"), MONTH("월") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(isActive: Boolean = true, openTodayRequest: Long = 0, vm: RecordsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    var view by rememberSaveable { mutableStateOf(CalendarView.MONTH) }
    var dayEpoch by rememberSaveable { mutableLongStateOf(s.today.toEpochDay()) }
    var monthEpoch by rememberSaveable { mutableLongStateOf(s.today.withDayOfMonth(1).toEpochDay()) }
    var weekEpoch by rememberSaveable { mutableLongStateOf(s.today.minusDays((s.today.dayOfWeek.value - 1).toLong()).toEpochDay()) }
    var focusWeight by rememberSaveable { mutableStateOf(false) }
    var handledRequest by rememberSaveable { mutableLongStateOf(0) }
    fun openDay(date: LocalDate) {
        dayEpoch = date.toEpochDay()
        monthEpoch = date.withDayOfMonth(1).toEpochDay()
        weekEpoch = date.minusDays((date.dayOfWeek.value - 1).toLong()).toEpochDay()
        focusWeight = date == s.today && s.days[date]?.weightKg == null
        view = CalendarView.DAY
    }
    LaunchedEffect(openTodayRequest) {
        if (openTodayRequest != 0L && openTodayRequest != handledRequest) {
            openDay(s.today)
            handledRequest = openTodayRequest
        }
    }
    BackHandler(enabled = isActive && view == CalendarView.DAY) { view = CalendarView.MONTH }
    val day = LocalDate.ofEpochDay(dayEpoch).coerceAtMost(s.today)
    val week = LocalDate.ofEpochDay(weekEpoch)
    val month = YearMonth.from(LocalDate.ofEpochDay(monthEpoch))
    Column(Modifier.fillMaxSize()) {
        Text("기록", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            CalendarView.entries.forEachIndexed { index, option ->
                SegmentedButton(selected = view == option, onClick = { view = option },
                    shape = SegmentedButtonDefaults.itemShape(index, CalendarView.entries.size)) { Text(option.label) }
            }
        }
        when (view) {
            CalendarView.MONTH -> LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    CalendarNavigation("${month.year}년 ${month.monthValue}월",
                        { monthEpoch = month.minusMonths(1).atDay(1).toEpochDay() },
                        { monthEpoch = month.plusMonths(1).atDay(1).toEpochDay() },
                        month < YearMonth.from(s.today))
                }
                item { MonthGrid(month, s, ::openDay) }
                item {
                    val entries = s.weights.filter { YearMonth.from(it.date) == month && it.date <= s.today }.sortedBy { it.date }
                    val average = entries.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average()
                    val change = if (entries.isEmpty()) null else entries.last().weightKg - entries.first().weightKg
                    Text("기록 ${entries.size}일 · 평균 ${average?.kg() ?: "—"} kg · " +
                        "${if (month == YearMonth.from(s.today)) "이번 달" else "${month.monthValue}월"} ${signedKg(change)}",
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CalendarView.WEEK -> LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val end = week.plusDays(6)
                    CalendarNavigation("${week.monthValue}월 ${week.dayOfMonth}일 ~ ${if (end.monthValue != week.monthValue) "${end.monthValue}월 " else ""}${end.dayOfMonth}일",
                        { weekEpoch = week.minusWeeks(1).toEpochDay() }, { weekEpoch = week.plusWeeks(1).toEpochDay() },
                        week.plusDays(6) < s.today)
                }
                item { ThisWeekCard(week, s, ::openDay) }
                items((0L..6L).map(week::plusDays).filter { it <= s.today }.reversed(), key = { it.toEpochDay() }) { date ->
                    DayCard(s.days[date] ?: RecordDay(date, null, emptyList()), s.notes[date]?.memo.orEmpty()) { openDay(date) }
                }
                item { Text("주간 목록", style = MaterialTheme.typography.titleMedium) }
                items(s.weeks, key = { "week-${it.start}" }) { summary ->
                    Card(onClick = { weekEpoch = summary.start.toEpochDay() },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(weekTitle(summary.start), style = MaterialTheme.typography.titleSmall)
                            Text("평균 ${summary.avgKg?.kg() ?: "—"} kg · 변화 ${signedKg(summary.changeKg)}",
                                style = MaterialTheme.typography.bodyMedium.tabularNumbers())
                            Text("체중 ${summary.weighDays}일 · 식단 ${summary.mealDays}일 · 메모 ${summary.noteDays}일",
                                style = MaterialTheme.typography.bodySmall)
                            Text(summary.avgKcal?.let { "하루 평균 ${it.roundToInt().comma()} kcal" } ?: "식단 기록 없음",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            CalendarView.DAY -> {
                CalendarNavigation(day.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                    { dayEpoch = day.minusDays(1).toEpochDay(); focusWeight = false },
                    { dayEpoch = day.plusDays(1).toEpochDay(); focusWeight = day.plusDays(1) == s.today && s.days[s.today]?.weightKg == null },
                    day < s.today)
                val detail by remember(day) { vm.dayDetail(day) }.collectAsStateWithLifecycle(initialValue = null)
                detail?.let {
                    DayDetailScreen(it, vm = vm,
                        focusWeight = focusWeight && isActive, onFocused = { focusWeight = false })
                }
            }
        }
    }
}

@Composable
internal fun CalendarNavigation(label: String, previous: () -> Unit, next: () -> Unit, canNext: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = previous, modifier = Modifier.semantics { contentDescription = "이전 기간" }) { Text("◀") }
        Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.tabularNumbers())
        TextButton(onClick = next, enabled = canNext, modifier = Modifier.semantics { contentDescription = "다음 기간" }) { Text("▶") }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, s: RecordsUiState, onDay: (LocalDate) -> Unit) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
        (0 until cells).chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { index ->
                    val number = index - offset + 1
                    val date = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                    if (date == null) Box(Modifier.weight(1f).height(68.dp))
                    else {
                        val weight = s.days[date]?.weightKg
                        val enabled = date <= s.today
                        val colors = MaterialTheme.colorScheme
                        val color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.35f)
                        val note = s.notes[date]
                        val hasNote = note?.hasTextNotes == true
                        Column(Modifier.weight(1f).heightIn(min = 68.dp).clip(MaterialTheme.shapes.small)
                            .then(if (date == s.today) Modifier.background(colors.primaryContainer) else Modifier)
                            .clickable(enabled = enabled) { onDay(date) }
                            .padding(vertical = 6.dp).semantics {
                                contentDescription = "${date} ${if (date == s.today) "오늘 " else ""}${weight?.let { "${it.kg()} kg" } ?: "체중 없음"}${if (hasNote) " 메모 있음" else ""}"
                            }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = color)
                            Text(weight?.kg() ?: "", style = MaterialTheme.typography.labelSmall.tabularNumbers(), color = color)
                            Box(Modifier.size(4.dp).clip(CircleShape).then(if (hasNote) Modifier.background(colors.primary) else Modifier))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThisWeekCard(start: LocalDate, s: RecordsUiState, onDay: (LocalDate) -> Unit) {
    val summary = s.weeks.firstOrNull { it.start == start }
    val notes = s.notes.values.associateBy { it.date }
    Card(shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (s.today in start..start.plusDays(6)) "이번 주" else "주간 요약", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                (0L..6L).forEach { offset ->
                    val date = start.plusDays(offset)
                    val note = notes[date]
                    Column(Modifier.weight(1f).heightIn(min = 72.dp).clickable(enabled = date <= s.today) { onDay(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.KOREAN),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (date == s.today) "오늘" else date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                            color = if (date == s.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (date > s.today) 0.35f else 1f))
                        Text(s.days[date]?.weightKg?.kg() ?: "", style = MaterialTheme.typography.labelSmall.tabularNumbers())
                        if (note?.hasTextNotes == true)
                            Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
            Text("평균 ${summary?.avgKg?.kg() ?: "—"} kg · 지난주 대비 ${signedKg(summary?.changeKg)}",
                style = MaterialTheme.typography.bodySmall.tabularNumbers())
        }
    }
}

internal fun weekTitle(start: LocalDate): String {
    val end = start.plusDays(6)
    return "${start.monthValue}/${start.dayOfMonth} ~ ${end.monthValue}/${end.dayOfMonth}"
}
private fun signedKg(value: Double?): String = value?.let {
    "${if (it > 0) "+" else ""}${it.kg()} kg"
} ?: "—"
