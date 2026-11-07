package io.github.adgjmptw2.diet.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

/** 다이얼로그 상태: 새 기록이면 entry = null */
private data class Editing(val entry: WeightEntry?)

@Composable
fun HomeScreen(onEditProfile: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    var editing by remember { mutableStateOf<Editing?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Editing(s.todayEntry) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(if (s.todayEntry == null) "체중 기록" else "오늘 체중 수정") },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            items(s.recent, key = { it.entry.id }) { row ->
                RecentItem(row, onClick = { editing = Editing(row.entry) })
                HorizontalDivider()
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.today.format(DayLabel), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("오늘의 체중", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onEditProfile) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "내 정보 수정")
        }
    }
}

@Composable
private fun GoalCard(s: HomeUiState) {
    val p = s.progress
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("현재 체중", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.currentKg.kg(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(" kg", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(p.percent)
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (p.lostKg >= 0) {
                        Stat("감량", "${p.lostKg.kg()} kg")
                    } else {
                        Stat("시작보다", "+${p.lostKg.absKg()} kg")
                    }
                    Stat("남은 무게", if (p.reached) "목표 달성" else "${p.remainingKg.kg()} kg")
                    Stat("목표", "${s.profile.targetWeightKg.kg()} kg")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "시작 ${s.profile.startWeightKg.kg()} kg · ${s.profile.startDate.format(DayLabel)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CalorieCard(s: HomeUiState) {
    val c = s.calories
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("하루 목표 칼로리", style = MaterialTheme.typography.labelLarge)
            Text("${c.target.comma()} kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "기초대사량 ${c.bmr.comma()} · 활동 포함 ${c.tdee.comma()}",
                style = MaterialTheme.typography.bodySmall,
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.entry.date.format(DayLabel), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        row.diffKg?.let { d ->
            val (sign, color) = when {
                d < -0.05 -> "▼" to MaterialTheme.colorScheme.primary
                d > 0.05 -> "▲" to GainColor
                else -> "-" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                if (sign == "-") "변화 없음" else "$sign ${d.absKg()}",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Text("${row.entry.weightKg.kg()} kg", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
