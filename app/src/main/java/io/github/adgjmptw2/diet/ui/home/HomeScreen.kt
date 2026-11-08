package io.github.adgjmptw2.diet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.ui.meals.TrackingViewModel
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
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.ProgressRing
import io.github.adgjmptw2.diet.ui.common.absKg
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.ThemeDecoration
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlin.math.roundToInt

@Composable
fun HomeScreen(onEditProfile: () -> Unit, onOpenMeals: () -> Unit = {}, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())

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
            item { CalorieCard(s) }
            item { ActiveFastingLine(onOpenMeals) }
        }
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
                ThemeDecoration()
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
            Stat("시작보다", "+${p.lostKg.absKg()} kg", color = MaterialTheme.colorScheme.onSurface)
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
    val target = s.profile.calorieTarget ?: c.target
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("하루 목표 칼로리", style = MaterialTheme.typography.titleMedium)
            Text("${target.comma()} kcal", style = MaterialTheme.typography.headlineMedium.tabularNumbers())
            Text(
                "오늘 먹은 양 ${s.todayKcal.roundToInt().comma()} / 목표 ${target.comma()} kcal",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (target > 0) (s.todayKcal / target).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                (if (s.profile.calorieTarget != null) "권장 ${c.target.comma()} · " else "") +
                    "기초대사량 ${c.bmr.comma()} · 활동 포함 ${c.tdee.comma()}",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.raisedToMinimum && s.profile.calorieTarget == null) {
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
private fun ActiveFastingLine(onOpenMeals: () -> Unit, vm: TrackingViewModel = hiltViewModel()) {
    val state by vm.fasting.collectAsStateWithLifecycle()
    val active = state.active ?: return
    val now by produceState(vm.now(), active.id) {
        while (true) { value = vm.now(); delay(60_000 - value % 60_000) }
    }
    val elapsed = Fasting.elapsedMillis(active.startAt, now)
    Text("단식 ${Fasting.durationText(elapsed)} · ${Fasting.stages[Fasting.stageIndex(elapsed)].name}",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMeals).padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
