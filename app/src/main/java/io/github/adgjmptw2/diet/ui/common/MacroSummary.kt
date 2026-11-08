package io.github.adgjmptw2.diet.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.domain.MacroGoal
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.util.Locale

@Composable
fun MacroSummary(meals: List<MealEntry>, goals: MacroGoal, targetKcal: Int,
    onEditGoals: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val targets = goals.resolved(targetKcal)
    val dark = colors.surface.luminance() < 0.5f
    // 영양소를 구분하는 세 색은 테마와 별개로 고정한다.
    val nutrientColors = if (dark) listOf(Color(0xFFB58CF0), Color(0xFFFF8A7A), Color(0xFFE3B341))
        else listOf(Color(0xFF7B3FC4), Color(0xFFE0604F), Color(0xFFB8860B))
    val labels = listOf("탄수화물", "지방", "단백질")
    val grams = listOf(meals.sumOf { it.carb ?: 0.0 }, meals.sumOf { it.fat ?: 0.0 }, meals.sumOf { it.prot ?: 0.0 })
    val targetGrams = listOf(requireNotNull(targets.carbGrams), requireNotNull(targets.fatGrams), requireNotNull(targets.proteinGrams))
    Card(onClick = onEditGoals, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("영양정보", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(painterResource(R.drawable.ic_chevron_right), "영양정보 목표", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.indices.forEach { i ->
                    Surface(modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(18.dp),
                        color = colors.surface, border = BorderStroke(1.5.dp, colors.outlineVariant)) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(labels[i], style = MaterialTheme.typography.labelMedium, color = nutrientColors[i])
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                BasicText(String.format(Locale.KOREA, "%.1f", grams[i]),
                                    style = MaterialTheme.typography.headlineMedium.tabularNumbers().copy(fontWeight = FontWeight.Bold, color = colors.onSurface),
                                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp,
                                        maxFontSize = MaterialTheme.typography.headlineMedium.fontSize, stepSize = 1.sp),
                                    maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
                                Text("g", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                                    modifier = Modifier.alignByBaseline())
                            }
                            val target = targetGrams[i]
                            LinearProgressIndicator(
                                progress = { if (target > 0) (grams[i] / target).toFloat().coerceIn(0f, 1f) else if (grams[i] > 0) 1f else 0f },
                                modifier = Modifier.fillMaxWidth().height(1.5.dp),
                                color = if (grams[i] > target) colors.tertiary else nutrientColors[i],
                                trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
                            Text("/$target g", style = MaterialTheme.typography.labelSmall.tabularNumbers(), color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
