package io.github.adgjmptw2.diet.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 온보딩과 설정에서 함께 쓰는 실제 팔레트 미리보기. 작은 화면에서는 가로로 스크롤한다. */
@Composable
fun ThemePicker(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentColors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = ((maxWidth - 24.dp) / 4).coerceAtLeast(72.dp * fontScale)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                val colors = appColorScheme(theme)
                val isSelected = selected == theme
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.width(cardWidth).clip(shape)
                        .background(currentColors.surface)
                        .selectable(isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(theme) })
                        .border(if (isSelected) 2.dp else 1.dp,
                            if (isSelected) currentColors.primary else currentColors.outlineVariant, shape)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(9.dp)).background(colors.background)) {
                        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                            val w = size.width
                            val h = size.height
                            drawRoundRect(colors.surface, Offset(w * 0.12f, h * 0.32f), Size(w * 0.76f, h * 0.53f),
                                cornerRadius = CornerRadius(5.dp.toPx()))
                            drawLine(colors.onSurface, Offset(w * 0.14f, h * 0.17f), Offset(w * 0.48f, h * 0.17f),
                                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(colors.primary, Offset(w * 0.25f, h * 0.51f), Offset(w * 0.62f, h * 0.51f),
                                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(colors.outlineVariant, Offset(w * 0.25f, h * 0.7f), Offset(w * 0.5f, h * 0.7f),
                                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(colors.tertiary, 2.dp.toPx(), Offset(w * 0.72f, h * 0.7f))
                        }
                        ThemeDecoration(Modifier.align(Alignment.TopEnd).size(28.dp, 25.dp), theme, colors)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(theme.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = currentColors.onSurface)
                        if (isSelected) {
                            Canvas(Modifier.size(12.dp).clearAndSetSemantics { }) {
                                val check = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.5f)
                                    lineTo(size.width * 0.4f, size.height * 0.75f)
                                    lineTo(size.width * 0.88f, size.height * 0.22f)
                                }
                                drawPath(check, currentColors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }
        }
    }
}
