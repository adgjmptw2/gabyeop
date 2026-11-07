package io.github.adgjmptw2.diet.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.WeightEntry
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.ProgressRing
import io.github.adgjmptw2.diet.ui.common.absKg
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.GainColor
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlin.math.roundToInt

/** 다이얼로그 상태: 새 기록이면 entry = null */
private data class Editing(val entry: WeightEntry?)

@Composable
fun HomeScreen(onEditProfile: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    var editing by remember { mutableStateOf<Editing?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Editing(s.todayEntry) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = {
                    Text(
                        if (s.todayEntry == null) "체중 기록" else "오늘 체중 수정",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(s, onEditProfile) }
            item { GoalCard(s) }
            item { CalorieCard(s) }
            item {
                Text(
                    "최근 기록",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    if (s.recent.isEmpty()) {
                        Text(
                            "기록 없음",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        s.recent.forEachIndexed { index, row ->
                            RecentItem(row, onClick = { editing = Editing(row.entry) })
                            if (index < s.recent.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        WeightDialog(
            today = s.today,
            initialDate = e.entry?.date ?: s.today,
            initialKg = e.entry?.weightKg,
            dateLocked = e.entry != null,
            recordedDays = s.recordedDays,
            onSave = { date, kg -> vm.save(date, kg); editing = null },
            onDelete = e.entry?.let { entry -> { vm.delete(entry.id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Header(s: HomeUiState, onEditProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("오늘의 체중", style = MaterialTheme.typography.headlineLarge)
            Text(
                s.today.format(DayLabel),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEditProfile) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "내 정보 수정")
        }
    }
}

@Composable
private fun GoalCard(s: HomeUiState) {
    val p = s.progress
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "현재 체중",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p.currentKg.kg(),
                    style = MaterialTheme.typography.displayMedium.tabularNumbers(),
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                )
                Text(
                    "kg",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Spacer(Modifier.height(24.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val maxWidth = this.maxWidth
                if (maxWidth < 280.dp || fontScale > 1.2f) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ProgressRing(
                            p.percent,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            size = (128.dp * fontScale.coerceAtLeast(1f)).coerceAtMost(maxWidth),
                        )
                        GoalStats(s)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        ProgressRing(p.percent)
                        GoalStats(s, Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "시작 ${s.profile.startWeightKg.kg()} kg · ${s.profile.startDate.format(DayLabel)}",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalStats(s: HomeUiState, modifier: Modifier = Modifier) {
    val p = s.progress
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (p.lostKg >= 0) {
            Stat(
                "감량", "${p.lostKg.kg()} kg",
                color = if (p.lostKg > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Stat("시작보다", "+${p.lostKg.absKg()} kg", color = GainColor)
        }
        Stat(
            "남은 무게", if (p.reached) "목표 달성" else "${p.remainingKg.kg()} kg",
            color = if (p.reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Stat("목표", "${s.profile.targetWeightKg.kg()} kg")
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.tabularNumbers(), color = color)
    }
}

@Composable
private fun CalorieCard(s: HomeUiState) {
    val c = s.calories
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("하루 목표 칼로리", style = MaterialTheme.typography.titleMedium)
            Text("${c.target.comma()} kcal", style = MaterialTheme.typography.headlineMedium.tabularNumbers())
            Text(
                "오늘 먹은 양 ${s.todayKcal.roundToInt().comma()} / 목표 ${c.target.comma()} kcal",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (c.target > 0) (s.todayKcal / c.target).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "기초대사량 ${c.bmr.comma()} · 활동 포함 ${c.tdee.comma()}",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.raisedToMinimum) {
                Text(
                    "최소 섭취량에 맞춘 값이에요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentItem(row: RecentRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "기록 수정", onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.entry.date.format(DayLabel), style = MaterialTheme.typography.bodyMedium.tabularNumbers())
            row.diffKg?.let { d ->
                val (sign, color) = when {
                    d < -0.05 -> "▼" to MaterialTheme.colorScheme.primary
                    d > 0.05 -> "▲" to GainColor
                    else -> "-" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    if (sign == "-") "변화 없음" else "$sign ${d.absKg()}",
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = color,
                )
            }
        }
        Text(
            "${row.entry.weightKg.kg()} kg",
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
