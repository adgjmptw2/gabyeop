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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.adgjmptw2.diet.ui.theme.ThemeDecoration
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlin.math.roundToInt

/** 다이얼로그 상태: 새 기록이면 entry = null */
private data class Editing(val entry: WeightEntry?)

@Composable
fun HomeScreen(onEditProfile: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    // 회전·다크 모드 전환에도 창이 남도록 열린 상태와 대상 id만 저장한다.
    var editingOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = if (!editingOpen) null else Editing(editingId?.let { id ->
        s.todayEntry?.takeIf { it.id == id } ?: s.recent.firstOrNull { it.entry.id == id }?.entry
    })
    val openEditor = { entry: WeightEntry? -> editingId = entry?.id; editingOpen = true }
    val closeEditor = { editingOpen = false; editingId = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(s, onEditProfile) }
            item { GoalCard(s) }
            item {
                // 떠 있는 버튼은 최근 기록을 가려서 카드 아래 일반 버튼으로 둠
                Button(
                    onClick = { openEditor(s.todayEntry) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.todayEntry == null) "체중 기록" else "오늘 체중 수정",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
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
                        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ThemeDecoration()
                            Text(
                                "기록 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        s.recent.forEachIndexed { index, row ->
                            RecentItem(row, onClick = { openEditor(row.entry) })
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
            onSave = { date, kg -> vm.save(date, kg); closeEditor() },
            onDelete = e.entry?.let { entry -> { vm.delete(entry.id); closeEditor() } },
            onDismiss = { closeEditor() },
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
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "설정")
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "현재 체중",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeDecoration(Modifier.size(60.dp, 44.dp))
            }
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
                            showThemeDecoration = true,
                        )
                        GoalStats(s)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        ProgressRing(p.percent, showThemeDecoration = true)
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
            "목표까지", if (p.reached) "목표 달성" else "${p.remainingKg.kg()} kg",
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
