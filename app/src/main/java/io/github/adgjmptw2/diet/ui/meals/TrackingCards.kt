package io.github.adgjmptw2.diet.ui.meals

import io.github.adgjmptw2.diet.ui.common.DayLabel
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.Fasting
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrackingCards(date: LocalDate, vm: TrackingViewModel = hiltViewModel()) {
    val water by vm.water.collectAsStateWithLifecycle()
    val fasting by vm.fasting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WaterCard(water.notes[date]?.waterMl ?: 0, water.settings.targetMl ?: water.automaticTargetMl,
                    water.settings.glassMl, { vm.adjustWater(date, it) }, Modifier.weight(1f))
                FastingCard(fasting, vm, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WaterCard(water.notes[date]?.waterMl ?: 0, water.settings.targetMl ?: water.automaticTargetMl,
                    water.settings.glassMl, { vm.adjustWater(date, it) })
                FastingCard(fasting, vm)
            }
        }
    }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

fun liters(ml: Int): String = String.format(Locale.KOREA, "%.2f", ml / 1000.0).trimEnd('0').let {
    if (it.endsWith('.')) it + "0" else it
}

@Composable
private fun WaterCard(ml: Int, target: Int, glassMl: Int, adjust: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("물  ${liters(ml)} / ${liters(target)} L", style = MaterialTheme.typography.titleSmall)
            val colors = MaterialTheme.colorScheme
            // 물은 테마 강조색 대신 물빛으로(빨간 물처럼 보이지 않게)
            val water = if (colors.background.luminance() < 0.5f) Color(0xFF7DB9E8) else Color(0xFF5B9BD5)
            Canvas(Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "물 ${ml} ml, 목표 ${target} ml" }) {
                val step = size.width / 8
                repeat(8) { i ->
                    val x = i * step + step * 0.2f
                    val width = step * 0.6f
                    val h = size.height
                    val path = Path().apply {
                        moveTo(x, 1f); lineTo(x + width, 1f)
                        lineTo(x + width * 0.85f, h - 1f); lineTo(x + width * 0.15f, h - 1f); close()
                    }
                    val filled = ml.toDouble() / target.coerceAtLeast(1) >= (i + 1) / 8.0
                    drawPath(path, if (filled) water else colors.surfaceContainerHigh)
                    drawPath(path, colors.outline, style = Stroke(1.dp.toPx()))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { adjust(-glassMl) }, enabled = ml > 0,
                    modifier = Modifier.semantics { contentDescription = "물 한 잔 빼기" }) { Text("− 한 잔") }
                TextButton(onClick = { adjust(glassMl) }, enabled = ml < 10_000) { Text("+ 한 잔") }
                Text("${glassMl} ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val stageImages = listOf(R.drawable.fast_stage_1, R.drawable.fast_stage_2, R.drawable.fast_stage_3,
    R.drawable.fast_stage_4, R.drawable.fast_stage_5, R.drawable.fast_stage_6)

@Composable
private fun FastingCard(state: FastingUiState, vm: TrackingViewModel, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val active = state.active
    var now by remember { mutableLongStateOf(vm.now()) }
    LaunchedEffect(active?.id) {
        while (true) { now = vm.now(); delay(60_000 - now % 60_000) }
    }
    val elapsed = active?.let { Fasting.elapsedMillis(it.startAt, now) } ?: 0
    val index = Fasting.stageIndex(elapsed)
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!it) vm.notificationDenied()
    }
    Card(onClick = { expanded = !expanded }, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(stageImages[index]), null, Modifier.size(40.dp))
                Text("단식", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(if (expanded) "접기 ▴" else "몸 상태 ▾", style = MaterialTheme.typography.labelSmall)
            }
            if (active == null) {
                Text("목표 시간", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(14, 16, 18, 20).forEach { hours ->
                        FilterChip(selected = state.targetHours == hours, onClick = { vm.selectTarget(hours) },
                            modifier = Modifier.weight(1f),
                            label = { Text("$hours", style = MaterialTheme.typography.labelSmall) })
                    }
                }
                TextButton(onClick = {
                    vm.start()
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("시작") }
            } else {
                Text(Fasting.durationText(elapsed), style = MaterialTheme.typography.headlineSmall)
                val colors = MaterialTheme.colorScheme
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val y = size.height / 2
                    drawLine(colors.surfaceContainerHigh, Offset(0f, y), Offset(size.width, y), 6.dp.toPx())
                    drawLine(colors.primary, Offset(0f, y),
                        Offset(size.width * Fasting.progress(elapsed, active.targetHours), y), 6.dp.toPx())
                    Fasting.stages.drop(1).filter { it.fromHours < active.targetHours }.forEach {
                        val x = size.width * it.fromHours / active.targetHours
                        drawLine(colors.onSurfaceVariant, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                }
                Text("${Fasting.stages[index].name} · 목표 ${active.targetHours}시간", style = MaterialTheme.typography.bodySmall)
                if (elapsed > Fasting.MAX_DURATION_MS) Text("기록은 최대 7일로 남아요",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.finish() }) { Text("단식 끝내기") }
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    active?.let { fast ->
                        val start = Instant.ofEpochMilli(fast.startAt).atZone(ZoneId.systemDefault())
                        var dayOffset by rememberSaveable(fast.id, fast.startAt) { mutableLongStateOf(0L) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { dayOffset-- },
                                enabled = start.toLocalDate().plusDays(dayOffset) > LocalDate.now().minusDays(7)) { Text("◀") }
                            Text(start.toLocalDate().plusDays(dayOffset).format(DayLabel), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { dayOffset++ },
                                enabled = start.toLocalDate().plusDays(dayOffset) < LocalDate.now()) { Text("▶") }
                        }
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, hour, minute -> vm.changeStart(dayOffset, hour, minute) },
                                start.hour, start.minute, true).show()
                        }) { Text("시작 ${start.format(DateTimeFormatter.ofPattern("HH:mm"))} ▸") }
                    }
                    Fasting.stages.forEachIndexed { stage, info ->
                        Row(Modifier.fillMaxWidth().alpha(if (active != null && stage < index) 0.5f else 1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(painterResource(stageImages[stage]), null, Modifier.size(32.dp))
                            Column {
                                Text("${if (active != null && stage == index) "지금 · " else ""}${info.fromHours}시간~ ${info.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active != null && stage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(info.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("개인차가 커요. 일반적으로 알려진 변화예요. 임신 중이거나 당뇨약을 먹는다면 단식 전에 의사와 상의하세요.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
