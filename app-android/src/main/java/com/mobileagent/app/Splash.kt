package com.mobileagent.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 启动短动画（品牌开场）：墨色底 → 四角星弹性点亮 + 涟漪 → 品牌名/品牌语淡入 → 整体淡出交棒主界面。
 * 与图标同源：白星挖空 + 深墨底；强调色固定夜间电光青（开机瞬间还没读主题，统一用夜间基调）。
 * 时间轴（总 2.1s）：星 0.05–0.45s 弹入 → 涟漪 0.30–0.95s → 品牌名 0.35–0.70s → 品牌语 0.55–0.95s → 淡出 0.85–1.0s。
 */
private val SplashBg = Color(0xFF101214)
private val SplashText = Color(0xFFE8EAED)
private val SplashDim = Color(0xFF9AA0A6)

/** 线段归一化：t 落在 [a,b] 内映射到 0..1 */
private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

@Composable
fun SplashOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2100, easing = LinearEasing))
        onFinished()
    }
    val time = t.value

    val starScale = easeOut(seg(time, 0.024f, 0.214f))          // 0.05–0.45s 弹入
    val rippleT = seg(time, 0.143f, 0.452f)                     // 0.30–0.95s 涟漪
    val titleAlpha = easeOut(seg(time, 0.167f, 0.333f))         // 0.35–0.70s
    val titleShift = (1f - titleAlpha) * 14f                    // 上滑入场
    val sloganAlpha = easeOut(seg(time, 0.262f, 0.452f))        // 0.55–0.95s
    val wholeAlpha = 1f - easeOut(seg(time, 0.85f, 1f))         // 收尾淡出

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBg)
            .graphicsLayer { alpha = wholeAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // 涟漪：从星形向外扩散、渐隐
                Canvas(modifier = Modifier.size(120.dp)) {
                    if (rippleT > 0f && rippleT < 1f) {
                        val base = size.minDimension / 2f
                        drawCircle(
                            color = AccentDark.copy(alpha = 0.45f * (1f - rippleT)),
                            radius = base * 0.34f + base * 0.66f * rippleT
                        )
                    }
                }
                // 四角星（与图标同源：控制点在中心产生凹边 sparkle）
                Canvas(modifier = Modifier.size(64.dp)) {
                    if (starScale <= 0f) return@Canvas
                    val r = size.minDimension / 2f * starScale
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val path = Path().apply {
                        moveTo(c.x, c.y - r)
                        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
                        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
                        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
                        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
                        close()
                    }
                    drawPath(path, Color.White)
                    // 星形底色微光
                    drawCircle(color = AccentDark.copy(alpha = 0.12f * starScale), radius = r * 1.9f)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "言岁AI",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = SplashText,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    translationY = titleShift
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "言赴岁安 · 让AI常伴",
                style = MonoStyle,
                fontSize = 12.sp,
                color = SplashDim,
                modifier = Modifier.graphicsLayer { alpha = sloganAlpha }
            )
        }
    }
}
