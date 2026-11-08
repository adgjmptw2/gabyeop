package io.github.adgjmptw2.diet.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

val LocalAppTheme = staticCompositionLocalOf { AppTheme.LIGHT }

/** 직접 그린 장식. 정보와 터치 영역을 갖지 않으며 LIGHT/DARK에는 공간도 남기지 않는다. */
@Composable
fun ThemeDecoration(
    modifier: Modifier = Modifier,
    theme: AppTheme = LocalAppTheme.current,
    colors: ColorScheme = MaterialTheme.colorScheme,
) {
    when (theme) {
        AppTheme.BUNNY -> Canvas(modifier.size(60.dp, 52.dp).clearAndSetSemantics { }) {
            drawBunnyEars(colors.surface, colors.primaryContainer, colors.primary.copy(alpha = 0.45f))
            val faceTop = Offset(size.width * 0.09f, size.height * 0.41f)
            val faceSize = Size(size.width * 0.72f, size.height * 0.55f)
            drawOval(colors.surface, faceTop, faceSize)
            drawOval(colors.primary.copy(alpha = 0.45f), faceTop, faceSize, style = Stroke(1.dp.toPx()))
            drawOval(colors.primaryContainer, Offset(size.width * 0.17f, size.height * 0.73f),
                Size(size.width * 0.15f, size.height * 0.1f))
            drawOval(colors.primaryContainer, Offset(size.width * 0.58f, size.height * 0.73f),
                Size(size.width * 0.15f, size.height * 0.1f))
            drawCircle(colors.onSurface, size.minDimension * 0.025f, Offset(size.width * 0.34f, size.height * 0.67f))
            drawCircle(colors.onSurface, size.minDimension * 0.025f, Offset(size.width * 0.57f, size.height * 0.67f))
            val nose = Path().apply {
                moveTo(size.width * 0.41f, size.height * 0.75f)
                quadraticTo(size.width * 0.45f, size.height * 0.72f, size.width * 0.49f, size.height * 0.75f)
                lineTo(size.width * 0.45f, size.height * 0.81f)
                close()
            }
            drawPath(nose, colors.primary)
            // 작은 하트 하나. 캐릭터·이모지 자산을 사용하지 않는다.
            val heart = Path().apply {
                moveTo(size.width * 0.87f, size.height * 0.46f)
                cubicTo(size.width * 0.68f, size.height * 0.32f, size.width * 0.82f, size.height * 0.23f,
                    size.width * 0.87f, size.height * 0.32f)
                cubicTo(size.width * 0.94f, size.height * 0.23f, size.width * 1.02f, size.height * 0.34f,
                    size.width * 0.87f, size.height * 0.46f)
                close()
            }
            drawPath(heart, colors.primary.copy(alpha = 0.65f))
        }
        AppTheme.OCEAN -> Canvas(modifier.size(80.dp, 40.dp).clearAndSetSemantics { }) {
            repeat(2) { row ->
                val y = size.height * (0.53f + row * 0.23f)
                val wave = Path().apply {
                    moveTo(size.width * 0.04f, y)
                    cubicTo(size.width * 0.2f, y - size.height * 0.24f,
                        size.width * 0.35f, y + size.height * 0.24f, size.width * 0.51f, y)
                    cubicTo(size.width * 0.67f, y - size.height * 0.24f,
                        size.width * 0.82f, y + size.height * 0.24f, size.width * 0.97f, y)
                }
                drawPath(wave, colors.primary.copy(alpha = if (row == 0) 0.55f else 0.28f),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(colors.primary.copy(alpha = 0.35f), size.minDimension * 0.055f,
                Offset(size.width * 0.73f, size.height * 0.18f), style = Stroke(1.dp.toPx()))
            drawCircle(colors.primary.copy(alpha = 0.22f), size.minDimension * 0.035f,
                Offset(size.width * 0.9f, size.height * 0.32f))
            drawCircle(colors.primary.copy(alpha = 0.3f), size.minDimension * 0.045f,
                Offset(size.width * 0.5f, size.height * 0.1f))
        }
        AppTheme.LIGHT, AppTheme.DARK -> Unit
    }
}

@Composable
fun BunnyRingEars(modifier: Modifier = Modifier) {
    if (LocalAppTheme.current != AppTheme.BUNNY) return
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.size(38.dp, 30.dp).clearAndSetSemantics { }) {
        drawBunnyEars(colors.surface, colors.primaryContainer, colors.primary.copy(alpha = 0.6f), fullHeight = true)
    }
}

private fun DrawScope.drawBunnyEars(face: Color, inner: Color, outline: Color, fullHeight: Boolean = false) {
    val earHeight = size.height * if (fullHeight) 0.95f else 0.6f
    listOf(0.23f, 0.49f).forEachIndexed { index, x ->
        val top = if (index == 0) size.height * 0.02f else size.height * 0.07f
        val origin = Offset(size.width * x, top)
        val earSize = Size(size.width * 0.18f, earHeight)
        drawOval(face, origin, earSize)
        drawOval(outline, origin, earSize, style = Stroke(1.dp.toPx()))
        drawOval(inner, Offset(origin.x + earSize.width * 0.27f, origin.y + earHeight * 0.13f),
            Size(earSize.width * 0.46f, earHeight * 0.7f))
    }
}
