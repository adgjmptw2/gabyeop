package io.github.adgjmptw2.diet.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.ui.theme.BunnyRingEars
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 소수 첫째 자리. -0.0은 0.0으로 */
fun Double.kg(): String {
    val r = (this * 10).roundToLong() / 10.0
    return String.format(Locale.KOREA, "%.1f", if (r == 0.0) 0.0 else r)
}

fun Double.absKg(): String = abs(this).kg()

fun Int.comma(): String = NumberFormat.getInstance(Locale.KOREA).format(this)

val DayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

@Composable
fun ProgressRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    showThemeDecoration: Boolean = false,
) {
    val progress = percent.coerceIn(0, 100)
    val sweep by animateFloatAsState(progress / 100f, tween(700), label = "ring")
    Box(
        modifier.size(size).clearAndSetSemantics {
            progressBarRangeInfo = ProgressBarRangeInfo(progress / 100f, 0f..1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val s = Stroke(stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = s)
            if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, topLeft, arcSize, style = s)
        }
        Text(
            "$progress%",
            style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
        )
        if (showThemeDecoration) {
            BunnyRingEars(Modifier.align(Alignment.TopCenter).offset(y = (-16).dp))
        }
    }
}
