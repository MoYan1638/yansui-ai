package com.mobileagent.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.mobileagent.core.Skill
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 方案动效规范的标准缓动曲线 */
private val AppEasing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)

// ---------- 通用小组件（原型样式还原） ----------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MonoStyle,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

/** 设置页分区卡片：每个板块独立描边，不再一整片文字堆在一起 */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

/** 描边小标签（tag/chip，原型 .tag .chip） */
@Composable
private fun TagChip(text: String, dim: Boolean = false, accent: Boolean = false) {
    val color = when {
        accent -> MaterialTheme.colorScheme.primary
        dim -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = if (dim) 0.5f else 0.45f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text, style = MonoStyle, fontSize = 9.5.sp, color = color)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = false,
    hint: String? = null,
    masked: Boolean = false,
) {
    // 掩码显示（可切换）：回显明文密钥易被截图/旁人窥视，默认打码
    var reveal by remember { mutableStateOf(false) }
    Column {
        Text(
            label,
            style = MonoStyle,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            visualTransformation = if (masked && !reveal) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            trailingIcon = if (masked) {
                {
                    TextButton(onClick = { reveal = !reveal }) {
                        Text(if (reveal) "隐藏" else "显示", fontSize = 11.sp)
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** 主框架：三页切换（会话/技能/设置），shared-axis 转场；底部导航悬浮在页面上层 */
@Composable
fun AppScaffold(engine: AgentEngine) {
    val navScope = rememberCoroutineScope()

    // 液态玻璃：页面层录进 backdrop，底部导航实时模糊/折射页面内容；API<31 无 RenderEffect 回退渐变玻璃
    val realGlass = isRenderEffectSupported()
    val pageBackdrop = rememberLayerBackdrop()
    val navInset = 86.dp // 导航胶囊占用的高度，页面内容预留，避免被盖住

    // HorizontalPager：页面是真实横滑的，导航拖拽/点按/页面滑动都驱动同一个 pagerState，
    // 所以拖导航时整页跟手，反过来滑页面时导航透镜也会跟着走
    val pagerState = rememberPagerState(pageCount = { 3 })

    // 页面底色带一层极淡的强调色晕染（底部最浓）：柔光玻璃需要"有东西可折"，纯平色玻璃会显脏
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush())
            .systemBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2, // 三页常驻：保住聊天列表位置，backdrop 也不会缺内容
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
                // 关键：底色要画在 backdrop 层「里面」，玻璃才采得到不透明像素
                .background(appBackgroundBrush())
        ) { t ->
            when (t) {
                0 -> ChatScreen(engine, bottomInset = navInset, onOpenSettings = {
                    navScope.launch { pagerState.animateScrollToPage(2) }
                })
                1 -> SkillsScreen(engine, bottomInset = navInset)
                else -> SettingsScreen(engine, bottomInset = navInset)
            }
        }

        GlassNavBar(
            tab = pagerState.currentPage,
            pagerState = pagerState,
            backdrop = pageBackdrop,
            realGlass = realGlass,
            modifier = Modifier.align(Alignment.BottomCenter),
            onSelect = { idx -> navScope.launch { pagerState.animateScrollToPage(idx) } }
        )
    }
}

/** 底部导航：真液态玻璃胶囊 + 滑动跟随透镜指示器；API<31 无 RenderEffect 回退渐变玻璃 */
@Composable
private fun GlassNavBar(
    tab: Int,
    pagerState: PagerState,
    backdrop: Backdrop,
    realGlass: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    if (realGlass) LiquidNavBar(tab, pagerState, backdrop, modifier)
    else GlassNavBarFallback(tab, modifier, onSelect)
}

/** 回退：悬浮柔光玻璃胶囊（API<31 无 RenderEffect 用渐变玻璃，选中项内嵌磨砂小胶囊） */
@Composable
private fun GlassNavBarFallback(tab: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current
    val items = listOf(
        Triple("会话", Icons.Filled.Home, 0),
        Triple("技能", Icons.AutoMirrored.Filled.List, 1),
        Triple("设置", Icons.Filled.Settings, 2)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)
            .glass(shape = RoundedCornerShape(26.dp), elevation = 18.dp)
            .padding(vertical = 5.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, icon, idx) ->
            val selected = tab == idx
            val sel by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(240, easing = AppEasing),
                label = "navSel$idx"
            )
            val tint by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(220, easing = AppEasing),
                label = "navTint$idx"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(idx)
                        }
                    }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                // 选中态：内部磨砂胶囊，向外一层强调色柔光
                if (sel > 0.01f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 9.dp)
                            .graphicsLayer {
                                alpha = sel
                                scaleX = 0.90f + 0.10f * sel
                                scaleY = 0.90f + 0.10f * sel
                            }
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (dark) Color.White.copy(alpha = 0.11f)
                                else Color.White.copy(alpha = 0.90f)
                            )
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier
                            .size(21.dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.06f * sel
                                scaleY = 1f + 0.06f * sel
                            }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 10.5.sp,
                        color = tint,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** 液态玻璃滑动跟随导航（按 AndroidLiquidGlass 官方 LiquidBottomTabs 简化；页面跟随由 PagerState 驱动） */
@Composable
private fun LiquidNavBar(
    tab: Int,
    pagerState: PagerState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    // 玻璃底色：给模糊结果铺一层薄底，与页面同族色
    val containerColor =
        if (dark) Color(0xFF121418).copy(alpha = 0.40f)
        else Color(0xFFFAFAFA).copy(alpha = 0.46f)
    val items = listOf(
        Triple("会话", Icons.Filled.Home, 0),
        Triple("技能", Icons.AutoMirrored.Filled.List, 1),
        Triple("设置", Icons.Filled.Settings, 2)
    )

    BoxWithConstraints(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)
    ) {
        val density = LocalDensity.current
        val tabs = items.size
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabs }

        // 指示器位置由 PagerState 驱动：currentPage + offsetFraction 就是连续页码，
        // 拖导航、滑页面、点按切页三条路径都汇到同一状态，不会打架

        // 长按跟随手指：按下时指示器贴着手指走，同时整页 Pager 实时跟手；松手就近停靠
        val navScope = rememberCoroutineScope()
        var pressing by remember { mutableStateOf(false) }
        val fingerX = remember { mutableFloatStateOf(0f) }

        fun pagePosAt(x: Float, width: Float): Float =
            ((x - tabWidth / 2f) / tabWidth).coerceIn(0f, (tabs - 1).toFloat())

        // 强调色图层：录进独立 backdrop，被指示器透镜折射后呈强调色
        val tabsBackdrop = rememberLayerBackdrop()
        val indicatorBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

        // ① 外层玻璃胶囊：实时模糊折射页面内容；高光+内阴影定义棱边——
        //    背后是纯色时（聊天页静止、设置页空白）没有内容可模糊，全靠这道棱边才看得出是玻璃
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.30f) },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp)
                .pointerInput(tabs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressing = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        fingerX.floatValue = down.position.x.coerceIn(0f, size.width.toFloat())
                        var lastIdx = pagerState.currentPage
                        var scrollJob: Job? = null
                        drag(down.id) { change ->
                            fingerX.floatValue = change.position.x.coerceIn(0f, size.width.toFloat())
                            change.consume()
                            // 整页实时跟手：把手指位置换算成连续页码，直接驱动 Pager
                            val pos = pagePosAt(fingerX.floatValue, size.width.toFloat())
                            val idx = pos.toInt()
                            if (idx != lastIdx) {
                                lastIdx = idx
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            val frac = pos - idx
                            scrollJob?.cancel()
                            scrollJob = navScope.launch {
                                if (frac <= 0.5f || idx + 1 >= tabs) pagerState.scrollToPage(idx, frac.coerceAtMost(0.5f))
                                else pagerState.scrollToPage(idx + 1, frac - 1f)
                            }
                        }
                        // 松手就近停靠：Pager 自带动画滑到最近页，指示器跟着同一状态走，无缝交接
                        val pos = pagePosAt(fingerX.floatValue, size.width.toFloat())
                        val idx = (pos + 0.5f).toInt().coerceIn(0, tabs - 1)
                        pressing = false
                        navScope.launch {
                            pagerState.animateScrollToPage(idx)
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, icon, idx) ->
                val selected = tab == idx
                val tint by animateColorAsState(
                    targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(220, easing = AppEasing),
                    label = "navTint$idx"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            color = tint,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ② 隐藏的强调色同款图层（不显示、只录进 backdrop 供透镜取样）：
        //    alpha 在 layerBackdrop 外层 → 记录的是全色内容；染色 graphicsLayer 在内层 → 记录的是染色后内容
        //    图标和文字都要录——指示器盖在真实标签上方，透镜里必须画得出文字，否则滑到哪哪的标题就被玻璃吃掉
        Row(
            modifier = Modifier
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, icon, _) ->
                Box(
                    modifier = Modifier.weight(1f).padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(label, fontSize = 10.5.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ③ 滑动跟随的玻璃透镜指示器：折射外层玻璃 + 强调色图标层，带色散、高光与内阴影
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    // 按住时贴手指走；其余时刻跟随 Pager 实际位置（页面滑动也带着透镜走）
                    translationX =
                        if (pressing) (fingerX.floatValue - tabWidth / 2f)
                            .coerceIn(0f, tabWidth * (tabs - 1))
                        else (pagerState.currentPage + pagerState.currentPageOffsetFraction) * tabWidth
                }
                .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { Capsule() },
                    effects = {
                        lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.5f) },
                    onDrawSurface = {
                        drawRect(if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabs)
        )
    }
}

// ---------- 首启引导（原型 onboarding.html：4 步流程） ----------

@Composable
fun OnboardingScreen(engine: AgentEngine, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    // 步骤3 的状态（纯 API 接入）
    var baseUrl by remember { mutableStateOf("https://api.deepseek.com/v1") }
    var model by remember { mutableStateOf("deepseek-chat") }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    // API 开通指引弹窗（「先建一个」入口）
    if (showGuide) {
        ApiGuideDialog(onDismiss = { showGuide = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp)
    ) {
        // 顶部：跳过 + 步骤 dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            if (step < 3) TextButton(onClick = onDone) { Text("跳过") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (step == i + 1) 8.dp else 6.dp)
                        .background(
                            if (step == i + 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.weight(1f))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(220, easing = AppEasing)) +
                 slideInHorizontally(tween(220, easing = AppEasing)) { it / 6 }) togetherWith
                (fadeOut(tween(140)))
            },
            label = "onboarding"
        ) { s ->
            when (s) {
                1 -> OnboardingCover()
                2 -> Column {
                    Text("STEP 1/2 · 选择模型 API", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("填入接入信息", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "密钥只保存在应用私有目录，不上传、不进任何云端。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Field("BASE URL", baseUrl, {
                        baseUrl = it
                        model = if (it.contains("11434")) "qwen3:8b" else "deepseek-chat"
                        testResult = null
                    }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Field("MODEL", model, { model = it; testResult = null }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Field("API KEY", apiKey, { apiKey = it; error = null; testResult = null }, singleLine = true,
                        hint = "任意 OpenAI 兼容接口均可；免鉴权接口（如本地模型）可留空")
                    testResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MonoStyle, fontSize = 11.sp,
                            color = if (it.startsWith("●")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { showGuide = true }) {
                        Text("还没有 API Key？先建一个 ↗")
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            testResult = "测试中…"
                                val cfg = com.mobileagent.core.ModelConfig(
                                    baseUrl = baseUrl.trim().trimEnd('/'), apiKey = apiKey, model = model,
                                    family = "openai", maxRounds = 1, compressRatio = 0.7
                                )
                                Thread {
                                    val r = try {
                                        com.mobileagent.core.ModelClient(cfg).chat(
                                            listOf(buildJsonObject {
                                                put("role", JsonPrimitive("user"))
                                                put("content", JsonPrimitive("hi"))
                                            }),
                                            emptyList()
                                        )
                                        "● 测试连接：通过 · $model"
                                    } catch (e: Exception) {
                                        "测试失败：${e.message?.take(120)}"
                                    }
                                    testResult = r
                                }.start()
                        },
                        enabled = baseUrl.isNotBlank() && model.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("测试连接") }
                }
                else -> OnboardingPerms()
            }
        }

        Spacer(Modifier.weight(1f))

        // 底部导航：上一步 / 下一步
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 1) {
                OutlinedButton(onClick = { step-- }, shape = RoundedCornerShape(8.dp)) { Text("上一步") }
            } else Spacer(Modifier.width(1.dp))
            Button(
                onClick = {
                    when (step) {
                        2 -> {
                            if (baseUrl.isBlank() || model.isBlank()) {
                                error = "接口地址和模型名必填；API Key 可留空（本地/免鉴权接口）"
                            } else step = 3
                        }
                        3 -> {
                            engine.saveConfig(baseUrl, apiKey, model)
                            onDone()
                        }
                        else -> step++
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (step == 3) "完成并进入" else "下一步") }
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** 步骤1 · 封面 */
@Composable
private fun OnboardingCover() {
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by blink.animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(520)), label = "blink"
    )
    Column {
        Row {
            Text(">", style = MonoStyle, fontSize = 40.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                "▍",
                style = MonoStyle,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(cursorAlpha)
            )
        }
        Text("言岁AI", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text("言赴岁安 · 让AI常伴", style = MonoStyle, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(
            "不止是聊天。你说任务，它调工具跑完——查资料、读写文件、调接口，全程可见每一步执行。",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Text("SKILLS", style = MonoStyle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "内置 love-companion · 恋人伴侣陪伴",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "引导共 3 步 · 选模型 API → 权限说明 → 完成 · 约 1 分钟",
            style = MonoStyle,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** API 供应商开通指引（引导步骤2 与设置页共用） */
private data class ApiGuide(
    val name: String,
    val url: String,
    val steps: String,
    val tip: String,
    val recommended: Boolean = false
)

private val apiGuides = listOf(
    ApiGuide(
        "DeepSeek", "https://platform.deepseek.com",
        "① 注册账号并充值（10 元起，能用很久）\n② 左侧「API keys」→「创建 API key」\n③ 复制 Key（只显示一次，记得保存）",
        "deepseek-chat 便宜够用，工具调用稳", recommended = true
    ),
    ApiGuide(
        "硅基流动", "https://cloud.siliconflow.cn",
        "① 注册（送免费额度）\n② 「API 密钥」→「新建 API 密钥」",
        "一个 Key 聚合多款开源模型"
    ),
    ApiGuide(
        "智谱 GLM", "https://open.bigmodel.cn",
        "① 注册并实名 → 控制台\n② 「API 密钥」→ 创建",
        "中文理解强，新用户有免费额度"
    ),
    ApiGuide(
        "Kimi · 月之暗面", "https://platform.moonshot.cn",
        "① 注册 →「API Key 管理」→ 新建\n② 充值后可用",
        "长上下文大"
    ),
    ApiGuide(
        "通义千问 · 阿里云", "https://bailian.console.aliyun.com",
        "① 开通百炼服务\n② 「API-KEY」→ 创建",
        "国内延迟低，新用户有免费额度"
    ),
    ApiGuide(
        "MiniMax", "https://platform.minimaxi.com",
        "① 注册 →「接口密钥」→ 创建",
        "多模态与语音见长"
    ),
)

/** 在应用内浏览器打开官网（不跳系统浏览器）；失败时兜底到外部浏览器 */
private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        BrowserActivity.start(ctx, url)
    }.onFailure {
        runCatching {
            ctx.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** API 开通指引弹窗：引导页「先建一个」与设置页共用 */
@Composable
private fun ApiGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Key · 开通教程") },
        text = {
            Text(
                "Agent 需要「OpenAI 兼容」的模型 API（跑在云端，手机零负担）。选一家平台建 Key，回来填入即可；已有任意兼容接口的，直接填自定义。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apiGuides.forEach { g -> item { ApiProviderCard(g) } }
                item { CustomApiCard() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 自定义 API 卡片：已有的任意 OpenAI 兼容接口都能接，不局限于内置名单 */
@Composable
private fun CustomApiCard() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自定义 API", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                TagChip("已有 Key 就用这个")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "已有任意兼容 OpenAI 协议的接口都能接——包括上面没列到的平台、聚合中转站、公司内网网关。",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "下一步直接填：BASE URL（如 https://你的域名/v1）+ MODEL + API KEY；接口需支持 /chat/completions。之后也能在会话页「配置自定义模型」或设置页随时改。",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiProviderCard(g: ApiGuide) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (g.recommended) {
                    Spacer(Modifier.width(8.dp))
                    TagChip("推荐")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                g.steps,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.tip,
                    style = MonoStyle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "打开官网 ↗",
                    style = MonoStyle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { openUrl(ctx, g.url) }
                        .padding(4.dp)
                )
            }
        }
    }
}

/** 步骤4 · 权限清单 + 完成 */
@Composable
private fun OnboardingPerms() {
    Column {
        Text("STEP 2/2 · 权限", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("基础权限清单", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            "Agent 跑任务需要的最小权限；拒绝后相关功能自动停用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        PermRow("网络访问", "调用模型 API 与 HTTP 工具")
        PermRow("文件读写", "限定在应用私有目录内，出目录逐次确认")
        PermRow("电池白名单", "防止长任务被系统杀后台（可选，稍后在系统设置里开）")
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                "不会索要：通讯录、位置、相机、麦克风。命令行等敏感能力在移动端默认关闭。",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun PermRow(name: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- 技能页（原型 skills.html：搜索 / 分类 / 开关 / 授权 chips） ----------

private val GateMark = "【合规闸门】"

@Composable
fun SkillsScreen(engine: AgentEngine, bottomInset: Dp = 0.dp) {
    var skills by remember { mutableStateOf(engine.skills) }
    var query by remember { mutableStateOf("") }
    var confirmSkill by remember { mutableStateOf<Skill?>(null) }
    var version by remember { mutableIntStateOf(0) } // 开关切换后强制刷新计数

    fun effectiveEnabled(skill: Skill) =
        engine.isSkillEnabled(skill.name) && !skill.body.startsWith(GateMark)

    val shown = skills.filter {
        query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true)
    }
    val enabledCount = skills.count { effectiveEnabled(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Skill 库", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(10.dp))
            Text(
                "已启用 $enabledCount / ${skills.size}",
                style = MonoStyle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 搜索框
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索 skill 名称或用途",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp + bottomInset)
        ) {
            items(shown, key = { it.name }) { skill ->
                val gated = skill.body.startsWith(GateMark)
                val switched = version >= 0 && engine.isSkillEnabled(skill.name) && !gated
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                skill.name,
                                style = MonoStyle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            TagChip(skill.category.ifBlank { "陪伴" }, dim = true)
                            if (skill.name in engine.builtinSkills) {
                                Spacer(Modifier.width(4.dp))
                                TagChip("内置", accent = true)
                            } else {
                                Spacer(Modifier.width(4.dp))
                                TagChip("自定义", dim = true)
                            }
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = switched,
                                // gate 未确认：先走合规弹窗；确认后自动开
                                onCheckedChange = { want ->
                                    if (gated) {
                                        if (want) confirmSkill = skill
                                    } else {
                                        engine.setSkillEnabled(skill.name, want)
                                        version++
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            skill.description.ifBlank { "（无描述）" },
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "工具授权",
                                style = MonoStyle,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            TagChip("文件读写", accent = true)
                            Spacer(Modifier.width(6.dp))
                            TagChip("HTTP", accent = true)
                            Spacer(Modifier.width(6.dp))
                            TagChip("记忆", accent = true)
                            Spacer(Modifier.width(6.dp))
                            TagChip("命令行·关闭", dim = true)
                        }
                    }
                }
            }
        }
    }

    // 合规确认弹窗（闸门启用唯一入口）
    confirmSkill?.let { skill ->
        AlertDialog(
            onDismissRequest = { confirmSkill = null },
            title = { Text("合规确认") },
            text = {
                Text("启用「${skill.name}」前需确认：我已年满 18 周岁。\n\n该技能包含拟人化陪伴内容，依据《人工智能拟人化互动服务管理暂行办法》需要完成成年确认后才会注入人设。")
            },
            confirmButton = {
                TextButton(onClick = {
                    engine.enableGatedSkill(skill.name) // 写 gate 确认字段
                    engine.setSkillEnabled(skill.name, true) // 开关联动打开
                    skills = engine.skills
                    version++
                    confirmSkill = null
                }) { Text("我已满 18 周岁，启用") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkill = null }) { Text("取消") }
            }
        )
    }
}

// ---------- 设置页（原型 settings.html：接入方式 / 供应商 / 字段卡 / 参数） ----------

/** 人设/记忆 编辑弹层（WorkBuddy 式：多行文本直接改，保存即生效） */
@Composable
private fun PersonaMemoryDialog(
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 340.dp)
                            .padding(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${text.length} 字",
                    style = MonoStyle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text); onDismiss() }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SettingsScreen(engine: AgentEngine, bottomInset: Dp = 0.dp) {
    val cfg = engine.config
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var baseUrl by remember(cfg) { mutableStateOf(cfg?.baseUrl ?: "https://api.deepseek.com/v1") }
    var model by remember(cfg) { mutableStateOf(cfg?.model ?: "deepseek-chat") }
    // Key 回到卡片里编辑：按当前供应商读写，底层仍按厂商独立加密保存
    var apiKey by remember(cfg) {
        mutableStateOf(engine.keyFor(providerKeyOf(baseUrl)) ?: cfg?.apiKey?.takeIf { it.isNotBlank() } ?: "")
    }
    var savedTip by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var customTick by remember { mutableIntStateOf(0) } // 自定义供应商增删后重建供应商片
    var confirmDelCustom by remember { mutableStateOf<String?>(null) } // 待删除的自定义供应商 baseUrl

    // 数据备份/恢复（导出明文 Key 随包走，换机可恢复；UI 提示妥善保管）
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var backupMsg by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMsg = "正在导出…"
        Thread {
            val r = BackupManager.export(ctx, engine.root, uri)
            mainHandler.post { backupMsg = r.message }
        }.start()
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMsg = "正在恢复…"
        Thread {
            val r = BackupManager.import(ctx, engine.root, uri)
            mainHandler.post {
                backupMsg = r.message
                // 恢复后磁盘数据变了，但引擎内存还是旧的——重建 Activity 让全新引擎从盘重载
                if (r.ok) (ctx as? android.app.Activity)?.recreate()
            }
        }.start()
    }

    // Shizuku（可选能力，只建议不强制）
    var shizukuTick by remember { mutableIntStateOf(0) }

    // 从系统设置页（电池白名单/通知/Shizuku 授权）返回时页面不会重组，但这些状态是读系统值的。
    // 监听 ON_RESUME 强制 +1，让下面几个状态重新计算，做到「设置完一返回就刷新」。
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(ctx) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // 技能热更新（只跟踪 love-companion）
    var skillUpdState by remember { mutableStateOf("idle") } // idle/checking/available/applying/done/latest/error
    var skillUpdMsg by remember { mutableStateOf(SkillUpdater.localVersion(engine.skillUpdateDir)) }

    // 应用内检查更新（APK 本体走系统下载器，装包从 GitHub Releases 拉取，双源自动切换镜像）
    var appUpdState by remember { mutableStateOf("idle") } // idle/checking/latest/available/downloading/ready/error
    var appUpdMsg by remember { mutableStateOf("") }
    var appRelease by remember { mutableStateOf<AppUpdater.Release?>(null) }
    var appApkFile by remember { mutableStateOf<File?>(null) }
    var showAppUpd by remember { mutableStateOf(false) }
    var appDlId by remember { mutableLongStateOf(-1L) }
    var appDlProgress by remember { mutableIntStateOf(0) }
    var appDlSrcIdx by remember { mutableIntStateOf(0) } // 当前下载源：0=主源，1=备用镜像
    var autoInstalledId by remember { mutableLongStateOf(-1L) } // 已自动拉起过安装的任务 id，防广播+轮询重复弹
    val currentVer = remember { AppUpdater.currentVersion(ctx) }

    // 自动拉起安装（下载完成/恢复）：同一任务只弹一次，拉起后立刻移除下载任务与通知、清记录
    fun autoInstall(f: File, id: Long) {
        if (autoInstalledId == id) return
        autoInstalledId = id
        appApkFile = f
        appUpdState = "ready"
        showAppUpd = true
        AppUpdater.install(ctx, f)
        AppUpdater.afterInstallLaunched(ctx, id)
    }

    // 下载失败：还有备用源就自动切过去重下，全失败才提示换网络。
    // id 用于防止「完成广播」和「轮询」两条路径同时触发重复切换。
    fun onDownloadFailed(id: Long, reason: String) {
        if (id != appDlId || appUpdState != "downloading") return
        val rel = appRelease
        val next = rel?.let { AppUpdater.nextSource(appDlSrcIdx, it.urls.size) } ?: -1
        if (rel != null && next >= 0) {
            // 移除失败任务，避免同名目标文件被占用导致新任务直接失败
            runCatching {
                (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
            }
            appDlSrcIdx = next
            appUpdMsg = "主下载源失败，正在切换备用源…"
            appDlId = AppUpdater.enqueueDownload(ctx, rel, next)
            appDlProgress = 0
            // 保持 downloading；appDlId 变化会让下面的轮询 effect 自动重启
        } else {
            appUpdMsg = reason
            appUpdState = "error"
        }
    }

    fun checkAppUpdate() {
        if (appUpdState == "checking" || appUpdState == "downloading") return
        appUpdState = "checking"
        Thread {
            val rel = runCatching { AppUpdater.fetch() }.getOrNull()
            when {
                rel == null -> {
                    appUpdMsg = "更新源不可达（GitHub 与镜像均失败）"
                    appUpdState = "error"
                }
                AppUpdater.isNewer(rel.version, currentVer) -> {
                    appRelease = rel
                    appUpdState = "available"
                    showAppUpd = true
                }
                else -> appUpdState = "latest"
            }
        }.start()
    }

    // 下载完成广播：校验产物并拷进 cache 待安装
    val appDlReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                if (id != appDlId) return
                val f = AppUpdater.prepareInstall(ctx, id)
                if (f != null) {
                    // 直接拉起系统安装器：用户既然点了下载，就不用再多点一步
                    //（首次会先跳到「允许安装未知应用」授权页，授完再装）
                    autoInstall(f, id)
                } else {
                    onDownloadFailed(id, "下载失败或已取消")
                }
            }
        }
    }
    DisposableEffect(ctx) {
        // DownloadManager 的完成广播来自系统下载器进程，NOT_EXPORTED 会把它挡在外面——
        // 必须 EXPORTED 才收得到（广播里带 download id，收到后再查 DownloadManager 状态校验，不会被伪造）
        androidx.core.content.ContextCompat.registerReceiver(
            ctx, appDlReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        onDispose { runCatching { ctx.unregisterReceiver(appDlReceiver) } }
    }

    // 进设置页时补偿一次：上次下载完成但广播没收到（页面切走/应用被杀），
    // 从 DownloadManager 记录的任务状态里恢复；已手动装过的残留也会被清理
    LaunchedEffect(Unit) {
        // 每次进设置页先清掉上次遗留的安装包（此刻安装早已结束，删除安全）
        AppUpdater.clearStaleApk(ctx)
        if (appUpdState == "idle") {
            Thread {
                val r = AppUpdater.resumeDownload(ctx, currentVer)
                if (r != null) {
                    appUpdMsg = "v${r.first} 更新包已就绪"
                    val id = ctx.getSharedPreferences(AppUpdater.PREF, android.content.Context.MODE_PRIVATE)
                        .getLong("download_id", -1L)
                    autoInstall(r.second, id)
                }
            }.start()
        }
    }

    // 下载中轮询：进度 + 任务状态。不依赖广播——广播丢了也能在 0.5s 内发现下完并拉起安装器
    LaunchedEffect(appUpdState, appDlId) {
        while (appUpdState == "downloading" && appDlId > 0) {
            val status = AppUpdater.status(ctx, appDlId)
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val f = AppUpdater.prepareInstall(ctx, appDlId)
                if (f != null) {
                    autoInstall(f, appDlId)
                } else {
                    appUpdMsg = "更新包校验失败，请重试"
                    appUpdState = "error"
                }
                break
            } else if (status == DownloadManager.STATUS_FAILED) {
                onDownloadFailed(appDlId, "下载失败，请重试或换网络")
                break
            }
            appDlProgress = AppUpdater.progress(ctx, appDlId)
            delay(500)
        }
    }

    // 主动任务
    var tasks by remember { mutableStateOf(CronStore.all(ctx)) }
    var showAddTask by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // resumeTick 变化时重读权限状态（从系统设置返回自动刷新）
    val notifGranted = remember(resumeTick) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    // 供应商片：内置预设 + 用户已保存的自定义供应商 + 末尾「自定义…」新增入口。
    // 点片切换 URL/模型并带出该厂商已存的 Key。customTick 变化时重建，反映增删。
    val providers = remember(customTick) {
        val disp = mapOf(
            "deepseek" to "deepseek", "moonshot" to "kimi", "zhipu" to "智谱",
            "qwen" to "通义", "minimax" to "minimax", "siliconflow" to "硅基流动"
        )
        val builtin = ModelPresets.all.distinctBy { it.provider }.map { p ->
            (disp[p.provider] ?: p.provider) to (p.baseUrl to p.model)
        }.toMutableList()
        builtin.add("openai" to ("https://api.openai.com/v1" to "gpt-4o-mini"))
        // 已保存的自定义供应商：按 baseUrl 匹配（与内置片同源渲染）
        engine.listCustomProviders().forEach { cp ->
            builtin.add(cp.name to (cp.baseUrl to cp.model))
        }
        builtin.toList() + listOf("自定义…" to ("" to ""))
    }
    // 已保存自定义片的 baseUrl 集合，用来给这些片显示删除按钮
    val customUrls = remember(customTick) { engine.listCustomProviders().map { it.baseUrl }.toSet() }
    // 内置厂商地址集合：保存时命中它们就不登记为自定义供应商
    val builtinUrls = remember {
        (ModelPresets.all.map { it.baseUrl.trimEnd('/') } + "https://api.openai.com/v1").toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "模型设置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 14.dp)
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp + bottomInset)) {
            item {
                TextButton(onClick = { showGuide = true }) {
                    Text("？如何获取 API Key · 各平台开通教程", style = MaterialTheme.typography.bodySmall)
                }

                SectionLabel("供应商")
                // 片子多了会撑破一行，自动换行排布
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 当前地址是否属于某个内置厂商；不属于时高亮「自定义…」片
                    val usingBuiltIn = providers.any { it.first.isNotBlank() && it.second.first == baseUrl }
                    providers.forEach { (name, pair) ->
                        val isAddNew = pair.first.isBlank() // 末尾「自定义…」新增入口
                        val isSavedCustom = customUrls.contains(pair.first) // 用户已保存的自定义片
                        val sel = if (isAddNew) !usingBuiltIn else pair.first == baseUrl
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.clickable {
                                    if (isAddNew) {
                                        // 新增：清空三行让用户填自己的 OpenAI 兼容接口
                                        baseUrl = ""; model = ""; apiKey = ""
                                    } else {
                                        baseUrl = pair.first; model = pair.second
                                        apiKey = engine.keyFor(providerKeyOf(pair.first)) ?: ""
                                    }
                                    savedTip = false
                                    error = null
                                }
                            ) {
                                Text(
                                    name,
                                    style = MonoStyle,
                                    fontSize = 11.sp,
                                    color = if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            if (isSavedCustom) {
                                // 自定义供应商可删除（按 baseUrl 匹配）
                                TextButton(
                                    onClick = { confirmDelCustom = pair.first },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text("×", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Field("BASE URL", baseUrl, { baseUrl = it; savedTip = false }, singleLine = true)
                        Spacer(Modifier.height(10.dp))
                        Field("MODEL", model, { model = it; savedTip = false }, singleLine = true)
                        Spacer(Modifier.height(10.dp))
                        Field("API KEY", apiKey, { apiKey = it; savedTip = false }, singleLine = true,
                            masked = true,
                            hint = "按厂商独立加密保存；免鉴权接口（如本地模型）可留空")
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val key = apiKey.trim()
                            when {
                                baseUrl.isBlank() || model.isBlank() -> error = "接口地址和模型名都要填"
                                else -> {
                                    // Key 按当前厂商存一份再落配置，切供应商回来时自动带出
                                    engine.setProviderKey(providerKeyOf(baseUrl), key)
                                    engine.saveConfig(baseUrl, key, model)
                                    // 非内置地址登记为自定义供应商：保存后成为可选片，切走再回来仍能回显/切换
                                    if (baseUrl.trimEnd('/') !in builtinUrls) {
                                        engine.upsertCustomProvider(baseUrl, model)
                                        customTick++
                                    }
                                    savedTip = true; error = null
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("保存") }
                    Spacer(Modifier.width(12.dp))
                    if (savedTip) {
                        Text("● 已保存 · ${engine.config?.model}", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(6.dp))

                SectionLabel("会话")
                SettingsCard {
                var confirmClearChats by remember { mutableStateOf(false) }
                var confirmClearChats2 by remember { mutableStateOf(false) }
                var clearAck by remember { mutableStateOf(false) }
                var confirmClearMemory by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { engine.startNewSession() }, shape = RoundedCornerShape(8.dp)) { Text("新会话") }
                        OutlinedButton(onClick = { confirmClearChats = true }, shape = RoundedCornerShape(8.dp)) {
                            Text("清空对话", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    OutlinedButton(onClick = { confirmClearMemory = true }, shape = RoundedCornerShape(8.dp)) {
                        Text("清空长期记忆", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (confirmClearMemory) {
                    AlertDialog(
                        onDismissRequest = { confirmClearMemory = false },
                        title = { Text("清空长期记忆？") },
                        text = {
                            Text(
                                "将删除 Agent 跨会话记住的全部信息（喜好、约定、重要日期等），不可恢复。清空后需要重新教它。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                engine.clearMemory(); confirmClearMemory = false
                            }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClearMemory = false }) { Text("取消") }
                        }
                    )
                }
                if (confirmClearChats) {
                    // 第一级警告：明确列出会删什么
                    AlertDialog(
                        onDismissRequest = { confirmClearChats = false },
                        title = { Text("清空全部对话？") },
                        text = {
                            Text(
                                "即将删除：当前对话、全部话题记录、所有历史归档。\n\n删除后无法找回，请确认你不再需要这些记录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmClearChats = false
                                clearAck = false
                                confirmClearChats2 = true
                            }) { Text("继续", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClearChats = false }) { Text("取消") }
                        }
                    )
                }
                if (confirmClearChats2) {
                    // 第二级警告：不可逆 + 必须勾选知晓才能执行
                    AlertDialog(
                        onDismissRequest = { confirmClearChats2 = false },
                        title = { Text("最后确认 · 不可逆操作") },
                        text = {
                            Column {
                                Text(
                                    "此操作不可逆！所有对话记录将被永久删除，没有任何备份，事后无法恢复。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "仅当你确定要彻底清除全部聊天记录时才继续。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = clearAck, onCheckedChange = { clearAck = it })
                                    Text(
                                        "我已知晓：不可逆、不可恢复",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    engine.clearAllChats()
                                    confirmClearChats2 = false
                                },
                                enabled = clearAck
                            ) { Text("永久删除", color = if (clearAck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClearChats2 = false }) { Text("取消") }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "新会话存档当前对话并开新上下文（记录不丢，会话页「历史」可回看）；长期记忆是 Agent 跨会话保存的笔记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                SectionLabel("数据备份")
                SettingsCard {
                    Text(
                        "导出一个备份文件，换机或重装后恢复对话、记忆、人设与设置。备份含明文 API Key，请只存在自己的设备里、不要外传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val name = "言岁AI备份-" + java.text.SimpleDateFormat(
                                    "yyyyMMdd-HHmm", java.util.Locale.US
                                ).format(java.util.Date()) + ".zip"
                                exportLauncher.launch(name)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("导出备份") }
                        OutlinedButton(
                            onClick = { confirmRestore = true },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("从备份恢复") }
                    }
                    if (backupMsg.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(backupMsg, style = MonoStyle, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (confirmRestore) {
                    AlertDialog(
                        onDismissRequest = { confirmRestore = false },
                        title = { Text("从备份恢复？") },
                        text = {
                            Text(
                                "当前对话、记忆、人设与设置会被备份文件覆盖，且不可撤销。确认已选对备份文件。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmRestore = false
                                restoreLauncher.launch("*/*")
                            }) { Text("选择备份文件", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRestore = false }) { Text("取消") }
                        }
                    )
                }

                SectionLabel("人设与记忆")
                SettingsCard {
                var editPersona by remember { mutableStateOf(false) }
                var editMemory by remember { mutableStateOf(false) }
                var distilling by remember { mutableStateOf(false) }
                var distillResult by remember { mutableStateOf<Pair<String, String>?>(null) }
                var distillError by remember { mutableStateOf<String?>(null) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val btnPad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    OutlinedButton(
                        onClick = { editPersona = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = btnPad,
                        modifier = Modifier.weight(1f)
                    ) { Text("编辑人设", fontSize = 13.sp, maxLines = 1, softWrap = false) }
                    OutlinedButton(
                        onClick = { editMemory = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = btnPad,
                        modifier = Modifier.weight(1f)
                    ) { Text("编辑记忆", fontSize = 13.sp, maxLines = 1, softWrap = false) }
                    OutlinedButton(
                        onClick = {
                            distilling = true; distillError = null
                            engine.distillFromHistory(
                                onDone = { m, p -> mainHandler.post { distilling = false; distillResult = m to p } },
                                onFailed = { e -> mainHandler.post { distilling = false; distillError = e } }
                            )
                        },
                        enabled = !distilling,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = btnPad,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                            if (distilling) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            } else {
                                Text("从历史提炼", fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                distillError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "人设是「她」的性格与称呼（每次对话都生效）；记忆是 Agent 跨会话保存的笔记，都可以直接改；「从历史提炼」会翻最近的对话自动总结，确认后存入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 提炼结果确认
                distillResult?.let { (mem, per) ->
                    AlertDialog(
                        onDismissRequest = { distillResult = null },
                        title = { Text("提炼结果") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text("记忆", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    mem.ifBlank { "（无）" },
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(10.dp))
                                Text("人设建议", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    per.ifBlank { "（无）" },
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (engine.persona().isNotBlank() && per.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "已有自定义人设，人设建议不会自动覆盖，可到「编辑人设」参考手填",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                engine.applyDistilled(mem, per)
                                distillResult = null
                            }) { Text("存入") }
                        },
                        dismissButton = {
                            TextButton(onClick = { distillResult = null }) { Text("丢弃") }
                        }
                    )
                }

                if (editPersona) {
                    PersonaMemoryDialog(
                        title = "编辑人设",
                        hint = "写「她」是谁、什么性格、怎么称呼你。示例：她叫小岁，温柔体贴，喜欢用「宝」称呼我，说话简短带点俏皮。",
                        initial = engine.persona(),
                        onDismiss = { editPersona = false },
                        onSave = { engine.savePersona(it) }
                    )
                }
                if (editMemory) {
                    PersonaMemoryDialog(
                        title = "编辑记忆",
                        hint = "Agent 跨会话记住的事（生日、喜好、约定…），一行一条，可自由增删改。",
                        initial = engine.memoryText(),
                        onDismiss = { editMemory = false },
                        onSave = { engine.saveMemory(it) }
                    )
                }
                }

                SectionLabel("后台活跃")
                SettingsCard {
                val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                // 依赖 resumeTick：从系统电池设置页返回即刷新
                val whitelisted = remember(resumeTick) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (whitelisted) "● 已在电池优化白名单" else "○ 未加入电池优化白名单",
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = if (whitelisted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    if (!whitelisted) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("申请白名单") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "白名单可防止长任务被系统冻杀（主动消息/定时任务需要）。国产 ROM（MIUI/EMUI/ColorOS 等）还需在系统设置里单独开启：自启动、后台弹出界面、锁后台。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                SectionLabel("命令行通道 · Shizuku（可选）")
                SettingsCard {
                val shizukuState = remember(shizukuTick, resumeTick) {
                    when {
                        ShizukuGate.granted() -> "ready"
                        ShizukuGate.ping() -> "ping"
                        else -> "none"
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (shizukuState) {
                            "ready" -> "● 已就绪 · 命令行工具已解锁"
                            "ping" -> "○ Shizuku 运行中 · 待授权"
                            else -> "○ 未安装 Shizuku"
                        },
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = if (shizukuState == "ready") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    if (shizukuState == "ping") {
                        OutlinedButton(
                            onClick = { ShizukuGate.requestPermission(); shizukuTick++ },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("授权") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (shizukuState == "ready")
                        "run_command 工具已可用：Agent 可通过 Shizuku 执行系统命令（每次执行都会弹窗请你确认，拒绝即取消）。"
                    else
                        "未解锁时 Agent 照常工作（文件/HTTP/记忆工具不受影响）。安装 Shizuku（adb 启动或无线调试激活）并回到本页授权后，命令行工具解锁；不装也不影响其他功能。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                SectionLabel("技能更新 · love-companion")
                SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (skillUpdState) {
                            "checking" -> "○ 正在检查更新…"
                            "available" -> "● 发现新版本：$skillUpdMsg"
                            "applying" -> "○ 正在更新…"
                            "done" -> "● 已更新到 $skillUpdMsg"
                            "latest" -> "● 已是最新（$skillUpdMsg）"
                            "error" -> "× 更新失败：$skillUpdMsg"
                            else -> "● 当前版本：" + if (skillUpdMsg == "builtin") "APK 内置版" else skillUpdMsg
                        },
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = if (skillUpdState in listOf("available", "done", "latest"))
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    if (skillUpdState in listOf("idle", "done", "latest", "error")) {
                        OutlinedButton(
                            onClick = {
                                skillUpdState = "checking"
                                Thread {
                                    try {
                                        val st = SkillUpdater.check(engine.skillUpdateDir)
                                        if (st.hasUpdate) {
                                            skillUpdMsg = "${st.local} → ${st.remote}"
                                            skillUpdState = "available"
                                        } else {
                                            skillUpdMsg = st.local
                                            skillUpdState = "latest"
                                        }
                                    } catch (e: Exception) {
                                        skillUpdMsg = e.message ?: "网络不可用"
                                        skillUpdState = "error"
                                    }
                                }.start()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("检查更新") }
                    }
                    if (skillUpdState == "available") {
                        OutlinedButton(
                            onClick = {
                                skillUpdState = "applying"
                                Thread {
                                    try {
                                        val v = SkillUpdater.apply(engine.skillUpdateDir)
                                        engine.refreshSkills()
                                        skillUpdMsg = v
                                        skillUpdState = "done"
                                    } catch (e: Exception) {
                                        skillUpdMsg = e.message ?: "更新失败"
                                        skillUpdState = "error"
                                    }
                                }.start()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("立即更新") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "技能内容托管在 GitHub 仓库：点「检查更新」拉取最新版替换本地文件，立即生效、无需重装 App。GitHub 不通时自动切换镜像源。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                SectionLabel("应用更新")
                SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (appUpdState) {
                            "checking" -> "○ 正在检查更新…"
                            "latest" -> "● 已是最新版本（v$currentVer）"
                            "available" -> "● 发现新版本 v${appRelease?.version}"
                            "downloading" -> "○ 下载中 $appDlProgress%"
                            "ready" -> "● 更新包就绪，待安装"
                            "error" -> "× $appUpdMsg"
                            else -> "● 当前版本 v$currentVer"
                        },
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = when {
                            appUpdState in listOf("latest", "available", "ready") -> MaterialTheme.colorScheme.primary
                            appUpdState == "error" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    if (appUpdState in listOf("idle", "latest", "error")) {
                        OutlinedButton(onClick = { checkAppUpdate() }, shape = RoundedCornerShape(8.dp)) {
                            Text("检查更新")
                        }
                    }
                    if (appUpdState == "available") {
                        OutlinedButton(onClick = { showAppUpd = true }, shape = RoundedCornerShape(8.dp)) {
                            Text("查看详情")
                        }
                    }
                    if (appUpdState == "ready") {
                        OutlinedButton(
                            onClick = {
                                appApkFile?.let {
                                    AppUpdater.install(ctx, it)
                                    if (appDlId > 0) AppUpdater.afterInstallLaunched(ctx, appDlId)
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("立即安装") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "更新源为 GitHub Releases（不通时自动切换镜像）。下载由系统下载器接管，完成后在本页或弹窗点「立即安装」；首次安装需在系统设置里允许本应用「安装未知应用」。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                SectionLabel("主动任务（定时）")
                SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (notifGranted) "● 任务通知已开启" else "○ 未开启任务通知",
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = if (notifGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    if (!notifGranted && android.os.Build.VERSION.SDK_INT >= 33) {
                        OutlinedButton(
                            onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("开启通知") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                tasks.forEach { task ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(task.time, style = MonoStyle, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                task.lastResult?.let {
                                    Text(
                                        "上次：$it",
                                        style = MonoStyle,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                if (task.repeat) "每日" else "单次",
                                style = MonoStyle, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "✕",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable {
                                        CronStore.remove(ctx, task.id)
                                        CronScheduler.cancel(ctx, task.id)
                                        tasks = CronStore.all(ctx)
                                    }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showAddTask = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) { Text("＋ 添加主动任务") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "到点后 Agent 在后台自动执行任务，结果通过通知告诉你（如：晚安问候 / 早报 / 提醒）。需要电池白名单配合。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "engine core M1/M2 · ${engine.skills.size} skills · maxRounds ${engine.config?.maxRounds ?: 15}",
                    style = MonoStyle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 删除自定义供应商确认（放在 LazyColumn 外，避免随列表滚动）
        confirmDelCustom?.let { url ->
            AlertDialog(
                onDismissRequest = { confirmDelCustom = null },
                title = { Text("删除自定义供应商") },
                text = {
                    Text(
                        "将移除「${providerKeyOf(url)}」这个供应商片，并删除它保存的 API Key。当前若正在使用该地址，需重新配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        engine.removeCustomProvider(url)
                        // 若正删掉的是当前使用地址，回到默认 DeepSeek
                        if (baseUrl.trimEnd('/') == url.trimEnd('/')) {
                            baseUrl = "https://api.deepseek.com/v1"; model = "deepseek-chat"; apiKey = ""
                        }
                        customTick++
                        savedTip = false
                        confirmDelCustom = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelCustom = null }) { Text("取消") }
                }
            )
        }
    }

    // 添加主动任务对话框
    if (showAddTask) {
        var taskPrompt by remember { mutableStateOf("") }
        var taskTime by remember { mutableStateOf("08:00") }
        var taskRepeat by remember { mutableStateOf(true) }
        var taskError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddTask = false },
            title = { Text("添加主动任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = taskPrompt, onValueChange = { taskPrompt = it },
                        label = { Text("任务描述（发给 Agent）") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = taskTime, onValueChange = { taskTime = it },
                        label = { Text("触发时间（HH:mm，24 小时制）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每天重复", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = taskRepeat, onCheckedChange = { taskRepeat = it })
                    }
                    taskError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (taskPrompt.isBlank()) taskError = "任务描述不能为空"
                        else if (!Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(taskTime.trim())) taskError = "时间格式不对，例：08:00"
                        else {
                            val task = CronStore.add(ctx, taskPrompt, taskTime, taskRepeat)
                            CronScheduler.scheduleNext(ctx, task)
                            tasks = CronStore.all(ctx)
                            showAddTask = false
                        }
                    }
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddTask = false }) { Text("取消") } }
        )
    }

    // API 开通教程（与引导页「先建一个」共用同一份指引）
    if (showGuide) {
        ApiGuideDialog(onDismiss = { showGuide = false })
    }

    // 应用更新弹窗：版本说明 + 下载进度 + 安装入口
    if (showAppUpd && appUpdState in listOf("available", "downloading", "ready")) {
        appRelease?.let { rel ->
            AlertDialog(
                onDismissRequest = { if (appUpdState != "downloading") showAppUpd = false },
                title = { Text("发现新版本 v${rel.version}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "当前版本 v$currentVer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (rel.notes.isNotBlank()) {
                            Text(
                                rel.notes,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (appUpdState == "downloading") {
                            LinearProgressIndicator(progress = { appDlProgress / 100f })
                            Text(
                                "已下载 $appDlProgress%",
                                style = MonoStyle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    when (appUpdState) {
                        "available" -> TextButton(onClick = {
                            appDlSrcIdx = 0
                            appDlId = AppUpdater.enqueueDownload(ctx, rel, 0)
                            appDlProgress = 0
                            appUpdMsg = ""
                            appUpdState = "downloading"
                        }) { Text("下载更新") }
                        "downloading" -> TextButton(onClick = {}, enabled = false) { Text("下载中…") }
                        "ready" -> TextButton(onClick = {
                            appApkFile?.let {
                                AppUpdater.install(ctx, it)
                                if (appDlId > 0) AppUpdater.afterInstallLaunched(ctx, appDlId)
                            }
                        }) { Text("立即安装") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (appUpdState == "downloading") {
                            runCatching {
                                (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(appDlId)
                            }
                            appUpdState = "available"
                        } else showAppUpd = false
                    }) { Text(if (appUpdState == "downloading") "取消下载" else "以后再说") }
                }
            )
        }
    }
}
