package io.github.adgjmptw2.diet.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.domain.BadgeState
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.absKg
import io.github.adgjmptw2.diet.ui.theme.GainColor
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GoalDateLabel = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(vm: ProgressViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("성과", style = MaterialTheme.typography.headlineLarge) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ProgressPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = s.period == period,
                        onClick = { vm.selectPeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index, ProgressPeriod.entries.size),
                    ) { Text(period.label, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("체중 추이", style = MaterialTheme.typography.titleMedium)
                    val periodLabel = s.period.days?.let { "최근 ${it}일" } ?: "전체 기간"
                    Text(periodLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WeightChart(s.entries, s.averages, s.profile.targetWeightKg, s.from, s.today, periodLabel)
                }
            }
        }
        item { SummaryCard(s) }
        item { Text("배지", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(s.badges.chunked(3), key = { row -> row.first().id }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { badge -> BadgeCard(badge, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: ProgressUiState) {
    val lost = state.lostKg
    val monthChange = state.monthChangeKg
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryRow(
                "시작 대비",
                when {
                    lost == null -> "기록 없음"
                    lost > 0.05 -> "${lost.absKg()} kg 감량"
                    lost < -0.05 -> "${lost.absKg()} kg 증가"
                    else -> "변화 없음"
                },
                when {
                    lost == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    lost > 0.05 -> MaterialTheme.colorScheme.primary
                    lost < -0.05 -> GainColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            SummaryRow(
                "이번 달 변화",
                when {
                    monthChange == null -> "기록 없음"
                    monthChange < -0.05 -> "▼${monthChange.absKg()} kg"
                    monthChange > 0.05 -> "▲${monthChange.absKg()} kg"
                    else -> "변화 없음"
                },
                when {
                    monthChange == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    monthChange < -0.05 -> MaterialTheme.colorScheme.primary
                    monthChange > 0.05 -> GainColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            SummaryRow(
                "예상 도달일",
                when {
                    state.goalReached -> "목표 달성"
                    state.goalDate != null -> state.goalDate.format(GoalDateLabel)
                    else -> "추세가 더 쌓이면 보여요"
                },
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.tabularNumbers(), color = color, textAlign = TextAlign.End, modifier = Modifier.weight(2f))
    }
}

@Composable
private fun BadgeCard(badge: BadgeState, modifier: Modifier = Modifier) {
    val earned = badge.earnedOn != null
    val progress = (badge.progress * 100).toInt()
    val status = badge.earnedOn?.let { "${it.format(DayLabel)} 획득" } ?: "진행률 ${progress}%"
    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = "${badge.name}. ${badge.description}. $status" },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                badgeEmoji(badge.id), modifier = Modifier.alpha(if (earned) 1f else 0.4f),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                badge.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (earned) FontWeight.SemiBold else FontWeight.Normal,
                color = if (earned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
            Text(
                badge.earnedOn?.format(DayLabel) ?: "$progress%",
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = if (earned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!earned) {
                LinearProgressIndicator(
                    progress = { badge.progress.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

private fun badgeEmoji(id: String): String = when (id) {
    "first_record" -> "✏️"
    "streak_7" -> "📅"
    "streak_30" -> "🌿"
    "loss_1kg" -> "🪶"
    "loss_5_percent" -> "🌱"
    "loss_10_percent" -> "🌳"
    "halfway" -> "⛰️"
    "goal" -> "🏁"
    else -> "🥗"
}
