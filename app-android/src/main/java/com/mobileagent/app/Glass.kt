package com.mobileagent.app

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柔光玻璃（液态玻璃观感）：半透明基色 + 斜向柔光 + 棱边高光 + 柔影。
 *
 * 不用 RenderEffect / 第三方模糊库——那些在低版本会直接失效或拖慢首帧，
 * 这里靠"多层渐变叠加"做出玻璃质感，全系机型表现一致。
 * 下面的内容用一层竖向渐隐遮罩收口，透出的文字自然淡掉，不会有"糊在字上"的脏感。
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 14.dp,
    glow: Float = 0.14f
): Modifier {
    val dark = isSystemInDarkTheme()
    val topLightPx = with(LocalDensity.current) { 24.dp.toPx() }

    // 基色：浅色靠白色堆叠，深色靠白色低透明度提亮（比深灰更通透）
    val fill = if (dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.145f), Color.White.copy(alpha = 0.055f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.74f))
        )
    }

    // 斜向柔光：左上偏亮，右下略反光，模拟光线穿过玻璃
    val sheen = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.10f else 0.52f),
            Color.White.copy(alpha = 0.0f),
            Color.White.copy(alpha = if (dark) 0.03f else 0.13f)
        )
    )

    // 棱边：上亮下暗，厚度感全靠这一圈
    val edge = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.30f else 1f),
            Color.White.copy(alpha = if (dark) 0.05f else 0.28f)
        )
    )

    // 顶部一道更亮的窄高光（约 22dp，玻璃上沿的"切口反光"）
    val topLight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.16f else 0.80f),
        1f to Color.White.copy(alpha = 0f),
        startY = 0f,
        endY = topLightPx
    )

    val shadowColor =
        if (dark) Color.Black.copy(alpha = 0.55f)
        else Color(0xFF7E858F).copy(alpha = glow)

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor
        )
        .clip(shape)
        .drawBehind {
            drawRect(fill)
            drawRect(sheen)
            drawRect(topLight)
        }
        .border(1.dp, edge, shape)
}
