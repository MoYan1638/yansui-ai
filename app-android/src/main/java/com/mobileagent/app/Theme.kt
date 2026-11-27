package com.mobileagent.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 视觉体系（方案定稿）：中性色打底 + 单强调色。
 * 夜间 = 电光青 #39C5CF，白天 = 信号绿 #4FBE7D，仅跟随系统，不提供手动切换。
 */

// 强调色
val AccentDark = Color(0xFF39C5CF)   // 电光青
val AccentLight = Color(0xFF4FBE7D)  // 信号绿

// 夜间中性色
private val DarkBg = Color(0xFF101214)
private val DarkSurface = Color(0xFF17191D)
private val DarkSurfaceHigh = Color(0xFF22252A)
private val DarkText = Color(0xFFE8EAED)
private val DarkTextDim = Color(0xFF9AA0A6)

// 白天中性色
private val LightBg = Color(0xFFF7F7F5)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceHigh = Color(0xFFEFEFEC)
private val LightText = Color(0xFF1A1C1E)
private val LightTextDim = Color(0xFF6B6F73)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF002022),
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextDim,
    outline = Color(0xFF33373D)
)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFF00220F),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextDim,
    outline = Color(0xFFD8D8D4)
)

/** 等宽字体点缀：技术信息（模型名、端点、工具调用）用 mono，正文用默认 */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    fontWeight = FontWeight.Normal
)

/**
 * 页面底色：中性底 + 底部一层极淡强调色晕染。
 * 必须画进 layerBackdrop 内部（玻璃录制层里）——否则玻璃采样到的是透明像素，
 * 模糊后变灰，整条底栏像蒙了一层灰雾。
 */
@Composable
fun appBackgroundBrush(): Brush = Brush.verticalGradient(
    0.0f to MaterialTheme.colorScheme.background,
    0.62f to MaterialTheme.colorScheme.background,
    1.0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)
)

@Composable
fun MobileAgentTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
