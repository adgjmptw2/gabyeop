package io.github.adgjmptw2.diet.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.domain.GraphMetric
import io.github.adgjmptw2.diet.ui.Destination
import io.github.adgjmptw2.diet.ui.RecordSection
import io.github.adgjmptw2.diet.ui.common.*
import io.github.adgjmptw2.diet.ui.fasting.rememberStartFastAction
import io.github.adgjmptw2.diet.ui.meals.TrackingViewModel
import io.github.adgjmptw2.diet.ui.meals.WaterUiState
import io.github.adgjmptw2.diet.ui.meals.liters
import io.github.adgjmptw2.diet.ui.steps.StepsTile
import io.github.adgjmptw2.diet.ui.theme.ThemeDecoration
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(onEditProfile: () -> Unit, onNavigate: (Destination) -> Unit,
    onOpenSettings: () -> Unit = onEditProfile, vm: HomeViewModel = hiltViewModel(),
    tracking: TrackingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val water by tracking.water.collectAsStateWithLifecycle()
    val goals by tracking.macroGoals.collectAsStateWithLifecycle()
    val error by tracking.error.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingWater by rememberSaveable { mutableStateOf(false) }
    var editingGlass by rememberSaveable { mutableStateOf(false) }
    var editingGoals by rememberSaveable { mutableStateOf(false) }
    val targetKcal = s.profile.calorieTarget ?: s.calories.target
    LaunchedEffect(tracking) {
        tracking.waterEvents.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar("${event.deltaMl}ml 추가했어요", "되돌리기", withDismissAction = true, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                    tracking.adjustWater(event.date, -event.deltaMl)
            }
        }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it) } }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.today.format(DayLabel), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.tabularNumbers())
                    IconButton(onClick = onEditProfile) { Icon(painterResource(R.drawable.ic_edit), "내 정보 수정", Modifier.size(20.dp)) }
                    IconButton(onClick = onOpenSettings) { Icon(painterResource(R.drawable.ic_settings), "설정", Modifier.size(20.dp)) }
                }
            }
            item {
                Card(onClick = { onNavigate(Destination.Records(openToday = true)) }, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("현재 체중", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ThemeDecoration(Modifier.size(40.dp, 24.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text("${s.progress.currentKg.kg()} kg", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium.tabularNumbers())
                            Text(if (s.progress.reached) "목표 달성" else "목표까지 ${s.progress.remainingKg.kg()} kg",
                                style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { s.progress.percent / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, gapSize = 0.dp, drawStopIndicator = {})
                        val change = s.weekChangeKg
                        Text(if (change == null) "이번 주 변화 —" else "이번 주 ${if (change > 0) "+" else if (change < 0) "−" else ""}${abs(change).kg()} kg",
                            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                            color = when { change == null || change == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                change < 0 -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.tertiary })
                    }
                }
            }
            item {
                Card(onClick = { onNavigate(Destination.Records(RecordSection.GRAPHS, GraphMetric.CALORIES)) },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val target = targetKcal
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("오늘 먹은 양", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${s.todayKcal.roundToInt().comma()} / ${target.comma()} kcal", style = MaterialTheme.typography.titleLarge.tabularNumbers())
                            }
                            TextButton(onClick = { onNavigate(Destination.AddMeal) }, contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.heightIn(min = 32.dp)) { Text("식단 추가") }
                        }
                        LinearProgressIndicator(progress = { (s.todayKcal / target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            color = if (s.todayKcal > target) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, gapSize = 0.dp, drawStopIndicator = {})
                    }
                }
            }
            item { MacroSummary(s.meals, goals, targetKcal, { editingGoals = true }) }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WaterTile(water, water.notes[s.today]?.waterMl ?: 0,
                        { tracking.adjustWater(s.today, -water.settings.glassMl) },
                        { tracking.addWater(s.today, water.settings.glassMl) },
                        { onNavigate(Destination.Records(RecordSection.GRAPHS, GraphMetric.WATER)) },
                        { editingWater = true }, { editingGlass = true }, Modifier.weight(1f).fillMaxHeight())
                    StepsTile({ onNavigate(Destination.Steps) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            item { FastingTile(tracking, { onNavigate(Destination.Fasting) }) }
        }
    }
    if (editingWater) WaterAmountDialog(s.today, s.today, water.notes[s.today]?.waterMl ?: 0,
        { editingWater = false }, { tracking.setWater(s.today, it); editingWater = false })
    if (editingGlass) WaterGlassSizeDialog(water.settings.glassMl,
        { editingGlass = false }, { tracking.setGlassMl(it); editingGlass = false })
    if (editingGoals) MacroGoalSheet(goals, targetKcal,
        { editingGoals = false }, { tracking.setMacroGoals(it); editingGoals = false })
}

@Composable
private fun WaterTile(state: WaterUiState, ml: Int, subtract: () -> Unit, add: () -> Unit, onOpen: () -> Unit,
    onEdit: () -> Unit, onEditGlass: () -> Unit, modifier: Modifier = Modifier) {
    val target = state.settings.targetMl ?: state.automaticTargetMl
    Card(onClick = onOpen, modifier = modifier,
        shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("물", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                WaterGlassButton(state.settings.glassMl, state.ready, onEditGlass)
            }
            Row(Modifier.fillMaxWidth().clickable(enabled = state.ready, onClickLabel = "마신 물 수정", onClick = onEdit)
                .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(liters(ml), style = MaterialTheme.typography.headlineSmall.tabularNumbers())
                Text(" / ${liters(target)} L", style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { (ml.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                gapSize = 0.dp, drawStopIndicator = {})
            WaterControls(ml, state.settings.glassMl, state.ready, subtract, add, onEditGlass, compact = true)
        }
    }
}

@Composable
private fun FastingTile(vm: TrackingViewModel, onOpen: () -> Unit) {
    val state by vm.fasting.collectAsStateWithLifecycle()
    val active = state.active
    val start = rememberStartFastAction(vm)
    var now by remember { mutableLongStateOf(vm.now()) }
    LaunchedEffect(active?.id) { while (true) { now = vm.now(); delay(60_000 - now % 60_000) } }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        if (active != null) {
            val elapsed = Fasting.elapsedMillis(active.startAt, now)
            val remaining = (active.targetHours * Fasting.HOUR_MS - elapsed).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(progress = { Fasting.progress(elapsed, active.targetHours) }, Modifier.size(32.dp),
                    strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("단식 ${Fasting.durationText(elapsed)} · ${if (remaining > 0) "남음 ${Fasting.durationText(remaining)}" else "목표 달성"}",
                        style = MaterialTheme.typography.bodySmall.tabularNumbers())
                    Text(Fasting.stages[Fasting.stageIndex(elapsed)].name, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(onClick = start, contentPadding = PaddingValues(0.dp)) { Text("단식 시작 · ${state.targetHours}시간") }
            val recent = state.recent.firstOrNull()
            Text(recent?.let {
                val duration = Fasting.elapsedMillis(it.startAt, it.endAt!!)
                "지난 단식 ${Fasting.durationText(duration)} · " +
                    if (duration >= it.targetHours * Fasting.HOUR_MS) "목표 달성" else "목표 ${it.targetHours}시간"
            }
                ?: "지난 단식 없음", modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
