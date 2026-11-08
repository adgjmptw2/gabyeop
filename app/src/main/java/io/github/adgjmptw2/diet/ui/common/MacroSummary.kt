package io.github.adgjmptw2.diet.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.macroRatios
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.util.Locale

@Composable
fun MacroSummary(meals: List<MealEntry>) {
    val ratio = macroRatios(meals)
    val colors = MaterialTheme.colorScheme
    val labels = listOf("탄수화물", "단백질", "지방")
    val grams = listOf(ratio.carbGrams, ratio.proteinGrams, ratio.fatGrams)
    val percentages = listOf(ratio.carbPercent, ratio.proteinPercent, ratio.fatPercent)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.indices.forEach { index ->
            Text("${labels[index]} ${String.format(Locale.KOREA, "%.1f", grams[index])} g · ${percentages[index]}%",
                style = MaterialTheme.typography.bodySmall.tabularNumbers())
        }
        Row(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHigh).clearAndSetSemantics {
                contentDescription = labels.indices.joinToString(" · ") { "${labels[it]} ${percentages[it]}%" }
            }) {
            val barColors = listOf(colors.primary, colors.tertiary, colors.secondary)
            percentages.forEachIndexed { i, value ->
                if (value > 0) Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(barColors[i]))
            }
        }
    if (!ratio.hasEnergy) Text("탄·단·지 비율을 계산할 정보 없음",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    if (ratio.missingCount > 0) Text("탄·단·지 정보 없는 음식 ${ratio.missingCount}개는 빠졌어요",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
