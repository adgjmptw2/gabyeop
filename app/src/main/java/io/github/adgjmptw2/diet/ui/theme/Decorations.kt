package io.github.adgjmptw2.diet.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.R

val LocalAppTheme = staticCompositionLocalOf { AppTheme.LIGHT }

enum class DecorationKind { PEEK, SIT }

/** 테마 장식. 정보와 터치 영역을 갖지 않으며 LIGHT/DARK에는 공간도 남기지 않는다. */
@Composable
fun ThemeDecoration(
    modifier: Modifier = Modifier,
    kind: DecorationKind = DecorationKind.PEEK,
    theme: AppTheme = LocalAppTheme.current,
    colors: ColorScheme = MaterialTheme.colorScheme,
) {
    when (theme) {
        AppTheme.BUNNY -> when (kind) {
            DecorationKind.PEEK -> DecorImage(R.drawable.bunny_peek, modifier.size(72.dp, 54.dp))
            DecorationKind.SIT -> DecorImage(R.drawable.bunny_sit, modifier.size(132.dp))
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
    DecorImage(R.drawable.bunny_ears, modifier.size(48.dp, 36.dp))
}

@Composable
private fun DecorImage(@DrawableRes id: Int, modifier: Modifier) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.clearAndSetSemantics { },
    )
}
