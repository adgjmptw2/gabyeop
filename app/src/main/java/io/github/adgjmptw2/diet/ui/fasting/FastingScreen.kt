package io.github.adgjmptw2.diet.ui.fasting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.Fast
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.domain.GraphPeriod
import io.github.adgjmptw2.diet.domain.fastingGraph
import io.github.adgjmptw2.diet.ui.Destination
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.meals.TrackingViewModel
import io.github.adgjmptw2.diet.ui.meals.TrackingEvent
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val stageImages = listOf(R.drawable.fast_stage_1, R.drawable.fast_stage_2, R.drawable.fast_stage_3,
    R.drawable.fast_stage_4, R.drawable.fast_stage_5, R.drawable.fast_stage_6)
private val timeLabel = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun FastingScreen(modifier: Modifier = Modifier, onNavigate: (Destination) -> Unit = {}, vm: TrackingViewModel = hiltViewModel()) {
    val state by vm.fasting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val start = rememberStartFastAction(vm)
    val active = state.active
    var now by remember { mutableLongStateOf(vm.now()) }
    var editingStart by rememberSaveable { mutableStateOf(false) }
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var editingPastId by rememberSaveable { mutableStateOf<String?>(null) }
    var recentLimit by rememberSaveable { mutableIntStateOf(5) }
    // 시작 전에 고른 시작 시각(null = 지금)
    var plannedStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var graphPeriod by rememberSaveable { mutableStateOf(GraphPeriod.WEEK) }
    val graphTarget = active?.targetHours ?: state.targetHours
    val graph = remember(state.history, graphPeriod, graphTarget, now / 60_000, vm.zone()) {
        fastingGraph(state.history, graphPeriod, graphTarget, vm.now(), vm.zone())
    }
    LaunchedEffect(active?.id) {
        while (true) { now = vm.now(); delay(1000 - now % 1000) }
    }
    LaunchedEffect(vm) {
        vm.fastingEvents.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val message = when (event) {
                    is TrackingEvent.FastFinished -> "단식을 끝냈어요"
                    is TrackingEvent.FastDeleted -> "단식 기록을 지웠어요"
                }
                if (snackbar.showSnackbar(message, "되돌리기", withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    when (event) {
                        is TrackingEvent.FastFinished -> vm.undoFinish(event.fast)
                        is TrackingEvent.FastDeleted -> vm.restorePast(event.fast)
                    }
                }
            }
        }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it) } }
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("단식", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth()) }
            item { FastingRing(active, active?.targetHours ?: state.targetHours, now) }
            if (active == null) {
                item { GoalChips(state.targetHours, vm::selectTarget, { editingGoal = true }) }
                item {
                    val planned = plannedStart?.takeIf { Fasting.validStart(it, now) }
                    val label = planned?.let {
                        val at = Instant.ofEpochMilli(it).atZone(vm.zone())
                        val today = Instant.ofEpochMilli(now).atZone(vm.zone()).toLocalDate()
                        "${if (at.toLocalDate() == today) "오늘" else at.toLocalDate().format(DayLabel)} ${at.format(timeLabel)}"
                    } ?: "지금"
                    Card(onClick = { pickingStart = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("시작 시각", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(label, style = MaterialTheme.typography.titleMedium.tabularNumbers())
                            Spacer(Modifier.width(6.dp))
                            Icon(painterResource(R.drawable.ic_edit), "시작 시각 수정", Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (planned != null) TextButton(onClick = { plannedStart = null }) { Text("지금 시작으로 바꾸기") }
                }
                item { Button(onClick = { start(plannedStart?.takeIf { Fasting.validStart(it, vm.now()) }); plannedStart = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("단식 시작") } }
            } else {
                item { Button(onClick = vm::finish, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("단식 끝내기") } }
                item {
                    val started = Instant.ofEpochMilli(active.startAt).atZone(vm.zone())
                    val end = Instant.ofEpochMilli(active.startAt + active.targetHours * Fasting.HOUR_MS).atZone(vm.zone())
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeCard("시작", started.toLocalDate(), started.format(timeLabel), { editingStart = true },
                            Modifier.weight(1f).fillMaxHeight())
                        TimeCard("목표 시각", end.toLocalDate(), end.format(timeLabel), { editingGoal = true },
                            Modifier.weight(1f).fillMaxHeight())
                    }
                }
                if (Fasting.elapsedMillis(active.startAt, now) > Fasting.MAX_DURATION_MS)
                    item { Text("기록은 최대 7일로 남아요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("단식 기록", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { onNavigate(Destination.FastingGraph) }) {
                                Text("전체 >")
                            }
                        }
                        FastingPeriodSelector(graphPeriod, { graphPeriod = it })
                        FastingChart(graph, graphTarget)
                    }
                }
            }
            item { Text("최근 단식", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
            if (state.recent.isEmpty()) item { Text("기록 없음", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()) }
            items(state.recent.take(recentLimit), key = { it.id }) { fast ->
                val date = Instant.ofEpochMilli(requireNotNull(fast.endAt)).atZone(vm.zone()).toLocalDate()
                val duration = Fasting.elapsedMillis(fast.startAt, now, fast.endAt).coerceAtMost(Fasting.MAX_DURATION_MS)
                Card(onClick = { editingPastId = fast.id },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "단식 기록 수정" },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(date.format(DayLabel), style = MaterialTheme.typography.bodySmall)
                            Text(Fasting.durationText(duration), style = MaterialTheme.typography.titleSmall.tabularNumbers())
                        }
                        Text(if (duration >= fast.targetHours * Fasting.HOUR_MS) "목표 달성" else "목표 ${fast.targetHours}시간",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (recentLimit < state.recent.size) item {
                TextButton(onClick = { recentLimit = (recentLimit + 5).coerceAtMost(state.recent.size) }) { Text("더 보기") }
            }
            item { StageDescriptions(active, now) }
        }
    }
    if (editingGoal) FastingGoalDialog(active?.targetHours ?: state.targetHours,
        { editingGoal = false }, { vm.selectTarget(it); editingGoal = false })
    if (editingStart && active != null) StartTimeDialog(active.startAt, vm.now(), vm.zone(), { editingStart = false },
        { vm.changeStartAt(it); editingStart = false })
    if (pickingStart && active == null) StartTimeDialog(plannedStart ?: vm.now(), vm.now(), vm.zone(), { pickingStart = false },
        { plannedStart = it; pickingStart = false })
    state.recent.firstOrNull { it.id == editingPastId }?.let { fast ->
        PastFastDialog(fast = fast, others = state.history, now = now, zone = vm.zone(),
            onDismiss = { editingPastId = null },
            onSave = { startAt, endAt, hours, result -> vm.updatePast(fast.id, startAt, endAt, hours, result) },
            onDelete = { result -> vm.deletePast(fast.id, result) })
    }
}

@Composable
private fun FastingRing(active: Fast?, targetHours: Int, now: Long) {
    val elapsed = active?.let { Fasting.elapsedMillis(it.startAt, now) } ?: 0L
    val stageIndex = Fasting.stageIndex(elapsed)
    val targetMs = targetHours * Fasting.HOUR_MS
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = (maxWidth * 0.88f).coerceAtMost(360.dp)
        val radius = diameter / 2 - 20.dp
        Box(Modifier.size(diameter).semantics {
            contentDescription = if (active == null) "목표 ${targetHours}시간" else
                "단식 ${Fasting.durationText(elapsed)}, ${Fasting.stages[stageIndex].name}"
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 20.dp.toPx()
                val stroke = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(colors.surfaceContainerHigh, -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                if (active != null && elapsed > 0) rotate(-90f) {
                    drawArc(Brush.sweepGradient(listOf(colors.primary, colors.tertiary), center = center),
                        0f, 360f * Fasting.progress(elapsed, targetHours), false, Offset(inset, inset), arcSize, style = stroke)
                }
            }
            if (active != null) Fasting.stages.drop(1).filter { it.fromHours <= targetHours }.forEach { stage ->
                val index = Fasting.stages.indexOf(stage)
                val angle = Math.toRadians(stage.fromHours.toDouble() / targetHours * 360 - 90)
                Box(Modifier.offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat())
                    .size(32.dp).clip(CircleShape).background(colors.surface)
                    .padding(3.dp).alpha(if (elapsed >= stage.fromHours * Fasting.HOUR_MS) 1f else 0.3f)) {
                    Image(painterResource(stageImages[index]), "${stage.fromHours}시간 · ${stage.name}", Modifier.fillMaxSize())
                }
            }
            Column(Modifier.width(diameter * 0.72f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Image(painterResource(stageImages[if (active != null) stageIndex else 0]), null, Modifier.size(56.dp))
                Text(if (active == null) "목표" else "단식 중", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                BasicText(if (active == null) "${targetHours}시간" else Fasting.durationText(elapsed),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displaySmall.tabularNumbers().copy(color = colors.onSurface,
                        textAlign = TextAlign.Center, lineHeight = 1.1.em),
                    maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 40.sp, stepSize = 1.sp))
                if (active != null) {
                    Text("${elapsed / 1000 % 60}초", style = MaterialTheme.typography.labelSmall.tabularNumbers(), color = colors.onSurfaceVariant)
                    HorizontalDivider(Modifier.width(120.dp).padding(vertical = 2.dp), color = colors.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (elapsed >= targetMs) "목표 달성" else "남음", style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant)
                        Text(if (elapsed >= targetMs) "+${Fasting.durationText(elapsed - targetMs)}" else Fasting.durationText(targetMs - elapsed),
                            style = MaterialTheme.typography.titleSmall.tabularNumbers(), color = colors.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeCard(label: String, date: LocalDate, time: String, onEdit: () -> Unit, modifier: Modifier) {
    Card(onClick = onEdit, modifier = modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(painterResource(R.drawable.ic_edit), "${label} 수정", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(date.format(DayLabel), style = MaterialTheme.typography.bodySmall.tabularNumbers())
            Text(time, style = MaterialTheme.typography.titleLarge.tabularNumbers())
        }
    }
}

@Composable
private fun GoalChips(hours: Int, onSelect: (Int) -> Unit, onCustom: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(14, 16, 18, 20).forEach { option ->
            FilterChip(selected = hours == option, onClick = { onSelect(option) },
                label = { Text("${option}시간", style = MaterialTheme.typography.labelMedium.tabularNumbers()) })
        }
        if (onCustom != null) FilterChip(selected = hours !in listOf(14, 16, 18, 20), onClick = onCustom,
            label = { Text(if (hours !in listOf(14, 16, 18, 20)) "${hours}시간" else "직접", style = MaterialTheme.typography.labelMedium.tabularNumbers()) })
    }
}

@Composable
internal fun FastingGoalDialog(hours: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(hours.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in Fasting.TARGET_HOURS
    AlertDialog(onDismissRequest = onDismiss, title = { Text("단식 목표") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalChips(parsed ?: 0, { text = it.toString() })
            OutlinedTextField(text, { text = it.filter(Char::isDigit).take(2) }, label = { Text("목표 시간") },
                suffix = { Text("시간") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !valid, supportingText = { Text("1~72시간") })
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { parsed?.let(onSave) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
private fun StageDescriptions(active: Fast?, now: Long) {
    val index = active?.let { Fasting.stageIndex(Fasting.elapsedMillis(it.startAt, now)) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Fasting.stages.forEachIndexed { stage, info ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(stageImages[stage]), null, Modifier.size(36.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${if (stage == index) "지금 · " else ""}${info.fromHours}시간~ ${info.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (stage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("개인차가 커요. 일반적으로 알려진 변화예요. 임신 중이거나 당뇨약을 먹는다면 단식 전에 의사와 상의하세요.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

