package io.github.adgjmptw2.diet.ui.steps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.health.connect.client.PermissionController
import io.github.adgjmptw2.diet.data.steps.HealthConnectSteps
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.data.steps.StepsAvailability
import io.github.adgjmptw2.diet.ui.common.ProgressRing
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val StepsDate = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val StepsWeekday = DateTimeFormatter.ofPattern("E", Locale.KOREAN)

@Composable
fun StepsScreen(modifier: Modifier = Modifier) {
    val vm: StepsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val health = remember { HealthConnectSteps(context) }
    val sensorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.connectSensor() else vm.markDenied()
    }
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.isNotEmpty()) vm.connectHealthConnect() else requestSensorOrSettings(vm, sensorLauncher)
    }
    val connect = {
        vm.clearDenied()
        requestSteps(vm, health, healthLauncher, sensorLauncher)
    }

    LaunchedEffect(Unit) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("걸음", style = MaterialTheme.typography.headlineLarge) }
        if (!state.hasPermission) {
            item { StepsConnectCard(state, onConnect = connect, onSettings = { openAppSettings(context) }) }
        } else {
            item { TodayStepsCard(state, onEditGoal = vm::beginGoalEdit) }
            item { RangeRow(state.range, vm::setRange) }
            item { WeekBars(state) }
            item { Summary(state) }
            item { SourceLine(state, onSettings = { openAppSettings(context) }) }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }

    if (state.goalEditing) {
        GoalDialog(state.goal, onDismiss = vm::cancelGoalEdit, onSave = vm::setGoal)
    }
}

/** Health Connect가 있으면 그 권한부터, 없으면 센서 권한부터 요청한다. */
private fun requestSteps(
    vm: StepsViewModel,
    health: HealthConnectSteps,
    healthLauncher: ActivityResultLauncher<Set<String>>,
    sensorLauncher: ActivityResultLauncher<String>,
) {
    if (health.available()) health.client()?.let { healthLauncher.launch(setOf(health.permission)) }
        ?: requestSensorOrSettings(vm, sensorLauncher)
    else requestSensorOrSettings(vm, sensorLauncher)
}

private fun requestSensorOrSettings(vm: StepsViewModel, launcher: ActivityResultLauncher<String>) {
    if (!vm.canUseSensor()) {
        vm.markDenied()
        return
    }
    when {
        vm.hasSensorPermission() -> vm.connectSensor()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> vm.connectSensor()
        else -> launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun StepsConnectCard(
    state: StepsUiState,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("걸음 수", style = MaterialTheme.typography.headlineSmall)
            Text(
                "걸음 수는 이 폰에만 저장해요. Health Connect의 걸음을 읽거나, 없으면 폰 센서로 세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConnect, shape = MaterialTheme.shapes.small) { Text("걸음 수 보기") }
            if (state.denied) {
                Text(
                    "설정에서 권한을 켜면 걸음 수를 볼 수 있어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSettings) { Text("앱 설정 열기") }
            }
        }
    }
}

@Composable
private fun TodayStepsCard(state: StepsUiState, onEditGoal: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("오늘", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onEditGoal) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "목표 바꾸기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.todaySteps.comma(), style = MaterialTheme.typography.displayMedium.tabularNumbers())
                    Text("걸음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProgressRing(
                    percent = if (state.goal > 0) (state.todaySteps * 100.0 / state.goal).roundToInt() else 0,
                    size = 100.dp,
                    stroke = 7.dp,
                )
            }
            Text("목표 ${state.goal.comma()}", style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Info("약 ${String.format(Locale.KOREA, "%.1f", state.distanceKm)} km", "거리")
                Info("약 ${state.kcal.comma()} kcal", "소모")
            }
        }
    }
}

@Composable
private fun Info(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall.tabularNumbers())
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RangeRow(selected: StepsRange, onSelect: (StepsRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepsRange.entries.forEach { range ->
            FilterChip(selected = range == selected, onClick = { onSelect(range) }, label = { Text(range.label) })
        }
    }
}

@Composable
private fun WeekBars(state: StepsUiState) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("최근 ${if (state.range == StepsRange.WEEK) "7일" else "30일"}", style = MaterialTheme.typography.titleMedium)
            StepsBars(state.days, state.goal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.days.firstOrNull()?.date?.let { Text(it.format(StepsDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.days.lastOrNull()?.date?.let { Text(it.format(StepsDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun StepsBars(days: List<StepsDay>, goal: Int) {
    val max = maxOf(goal, days.maxOfOrNull { it.steps ?: 0 } ?: goal).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceContainerHigh
    val goalColor = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        if (days.isEmpty()) return@Canvas
        val gap = size.width * 0.02f
        val width = (size.width - gap * (days.size - 1)) / days.size
        val base = size.height
        days.forEachIndexed { index, day ->
            val x = index * (width + gap)
            val value = day.steps
            val h = if (value == null) 0f else (value.toFloat() / max) * (base * 0.92f)
            if (h > 0f) drawRoundRect(
                color = if (value!! >= goal) barColor else muted,
                topLeft = Offset(x, base - h),
                size = Size(width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 3f),
            ) else drawRoundRect(
                color = muted.copy(alpha = 0.35f),
                topLeft = Offset(x, base - 2.dp.toPx()),
                size = Size(width, 2.dp.toPx()),
            )
        }
        val goalY = base - (goal.toFloat() / max) * (base * 0.92f)
        drawLine(goalColor, Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 1.dp.toPx())
    }
    if (days.size <= 7) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { day ->
                Text(day.date.format(StepsWeekday), style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Summary(state: StepsUiState) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Info("${state.average.comma()}", "평균 걸음")
            Info("${state.achieved}/${state.days.size}일", "목표 달성")
        }
    }
}

@Composable
private fun SourceLine(state: StepsUiState, onSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            when (state.source) {
                StepsAvailability.HEALTH_CONNECT -> "Health Connect에서 가져와요"
                StepsAvailability.SENSOR -> "폰 걸음 센서로 세요"
                StepsAvailability.NONE -> "권한을 켜면 걸음 수를 볼 수 있어요"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.source == StepsAvailability.SENSOR) {
            Text("앱을 깐 뒤부터 세요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("설정")
        }
    }
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("걸음 목표") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                suffix = { Text("걸음") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = value == null || value !in 1000..50000,
                supportingText = { Text("1,000 ~ 50,000") },
                shape = MaterialTheme.shapes.small,
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null && value in 1000..50000) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun StepsTile(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: StepsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val health = remember { HealthConnectSteps(context) }
    val sensorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.connectSensor() else vm.markDenied()
    }
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.isNotEmpty()) vm.connectHealthConnect() else requestSensorOrSettings(vm, sensorLauncher)
    }
    LaunchedEffect(Unit) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val percent = if (state.goal > 0) (state.todaySteps * 100.0 / state.goal).roundToInt() else 0
    Card(
        modifier = modifier.heightIn(min = 180.dp).clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("걸음", style = MaterialTheme.typography.titleSmall)
            if (state.hasPermission) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProgressRing(percent = percent, size = 84.dp, stroke = 7.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.todaySteps.comma(), style = MaterialTheme.typography.headlineSmall.tabularNumbers())
                    Text(
                        "목표 ${state.goal.comma()}",
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.denied) {
                        Text(
                            "설정에서 권한을 켜면 걸음 수를 볼 수 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { openAppSettings(context) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("앱 설정 열기") }
                    } else {
                        Text(
                            "오늘 걸음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                vm.clearDenied()
                                requestSteps(vm, health, healthLauncher, sensorLauncher)
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("걸음 수 보기") }
                    }
                }
            }
        }
    }
}