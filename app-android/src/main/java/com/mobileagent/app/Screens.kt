package com.mobileagent.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileagent.core.Skill
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 方案动效规范的标准缓动曲线 */
private val AppEasing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)
private val WarnRed = Color(0xFFE05A5A)

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
) {
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

/** 主框架：三页切换（会话/技能/设置），shared-axis 转场 */
@Composable
fun AppScaffold(engine: AgentEngine) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260, easing = AppEasing)) { dir * it / 4 } +
                 fadeIn(tween(260, easing = AppEasing))) togetherWith
                (slideOutHorizontally(tween(200, easing = AppEasing)) { -dir * it / 4 } +
                 fadeOut(tween(160)))
            },
            label = "pages",
            modifier = Modifier.weight(1f)
        ) { t ->
            when (t) {
                0 -> ChatScreen(engine, onOpenSettings = { tab = 2 })
                1 -> SkillsScreen(engine)
                else -> SettingsScreen(engine)
            }
        }

        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            val tabs = listOf("会话" to Icons.Filled.Home, "技能" to Icons.AutoMirrored.Filled.List, "设置" to Icons.Filled.Settings)
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) }
                )
            }
        }
    }
}

// ---------- 首启引导（原型 onboarding.html：4 步流程） ----------

@Composable
fun OnboardingScreen(engine: AgentEngine, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    // 步骤2/3 的状态
    var useLocal by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("https://api.deepseek.com/v1") }
    var model by remember { mutableStateOf("deepseek-chat") }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }

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
            if (step < 4) TextButton(onClick = onDone) { Text("跳过") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { i ->
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
                2 -> OnboardingAccess(useLocal) { useLocal = it; error = null }
                3 -> Column {
                    Text("STEP 2/3 · 配置", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
                        hint = if (useLocal) "本地 Ollama 通常不需要 Key，随便填个 ollama 即可" else "还没有？去对应平台控制台获取")
                    testResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MonoStyle, fontSize = 11.sp,
                            color = if (it.startsWith("●")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(14.dp))
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
                        enabled = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank(),
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
                        3 -> {
                            if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) {
                                error = "三项都要填（Key 在各模型平台的控制台获取）"
                            } else step = 4
                        }
                        4 -> {
                            engine.saveConfig(baseUrl, apiKey, model)
                            onDone()
                        }
                        else -> step++
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (step == 4) "完成并进入" else "下一步") }
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
        Text("手机上的 Agent", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
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
            "恋人伴侣陪伴 · 从0到1做软件 · 推上 GitHub",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "引导共 4 步 · 模型接入 → 配置 → 权限 → 完成 · 约 1 分钟",
            style = MonoStyle,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 步骤2 · 选接入方式 */
@Composable
private fun OnboardingAccess(useLocal: Boolean, onPick: (Boolean) -> Unit) {
    Column {
        Text("STEP 1/3 · 模型接入", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Agent 用哪个大脑？", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            "两种接入方式，之后随时可以在设置里换。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        AccessOption(
            sel = !useLocal,
            name = "API 接入", tag = "推荐", tagDim = false,
            desc = "填入任意兼容 OpenAI 协议的服务（DeepSeek、智谱等）。模型在云端跑，手机零负担，能力强。"
        ) { onPick(false) }
        Spacer(Modifier.height(10.dp))
        AccessOption(
            sel = useLocal,
            name = "本地部署", tag = "进阶", tagDim = true,
            desc = "连接本机或局域网的 Ollama / LM Studio。数据不出设备，但要求较高配置，速度与电量有代价。"
        ) { onPick(true) }
        if (useLocal) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, WarnRed.copy(alpha = 0.5f))
            ) {
                Text(
                    "本地模式提示：7B 级模型建议 12GB+ 内存设备；生成速度约为 API 的 1/5~1/10，长时间运行显著耗电。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AccessOption(
    sel: Boolean, name: String, tag: String, tagDim: Boolean, desc: String, onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                TagChip(tag, dim = tagDim)
            }
            Spacer(Modifier.height(6.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 步骤4 · 权限清单 + 完成 */
@Composable
private fun OnboardingPerms() {
    Column {
        Text("STEP 3/3 · 权限", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
fun SkillsScreen(engine: AgentEngine) {
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
            contentPadding = PaddingValues(vertical = 12.dp)
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

@Composable
fun SettingsScreen(engine: AgentEngine) {
    val cfg = engine.config
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var baseUrl by remember(cfg) { mutableStateOf(cfg?.baseUrl ?: "https://api.deepseek.com/v1") }
    var model by remember(cfg) { mutableStateOf(cfg?.model ?: "deepseek-chat") }
    var apiKey by remember(cfg) { mutableStateOf(cfg?.apiKey ?: "") }
    var savedTip by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var localMode by remember { mutableStateOf(baseUrl.contains("11434") || baseUrl.contains("localhost")) }

    // Shizuku（可选能力，只建议不强制）
    var shizukuTick by remember { mutableIntStateOf(0) }

    // 主动任务
    var tasks by remember { mutableStateOf(CronStore.all(ctx)) }
    var showAddTask by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true

    val providers = listOf(
        "deepseek" to ("https://api.deepseek.com/v1" to "deepseek-chat"),
        "智谱" to ("https://open.bigmodel.cn/api/paas/v4" to "glm-4.6"),
        "openai" to ("https://api.openai.com/v1" to "gpt-4o-mini"),
        "自定义…" to ("" to "")
    )

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

        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                SectionLabel("接入方式")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccessOption(
                        sel = !localMode, name = "API 接入", tag = "当前", tagDim = false,
                        desc = "云端模型，能力强、速度快"
                    ) { localMode = false }
                    AccessOption(
                        sel = localMode, name = "本地部署", tag = "进阶", tagDim = true,
                        desc = "Ollama / LM Studio，数据不出设备"
                    ) {
                        localMode = true
                        baseUrl = "http://127.0.0.1:11434/v1"
                        model = "qwen3:8b"
                        savedTip = false
                    }
                }

                SectionLabel("供应商")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    providers.forEach { (name, pair) ->
                        val sel = pair.first == baseUrl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable {
                                if (pair.first.isNotBlank()) {
                                    baseUrl = pair.first; model = pair.second; savedTip = false
                                }
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
                        Field("MODEL", model, { model = it; savedTip = false }, singleLine = true,
                            hint = "按模型家族自动匹配提示词模板（deepseek / minimax / openai）")
                        Spacer(Modifier.height(10.dp))
                        Field("API KEY", apiKey, { apiKey = it; savedTip = false }, singleLine = true,
                            hint = "存储于应用私有目录，不明文上传")
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
                            if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) {
                                error = "三项都不能为空"
                            } else {
                                engine.saveConfig(baseUrl, apiKey, model)
                                savedTip = true; error = null
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("保存") }
                    Spacer(Modifier.width(12.dp))
                    if (savedTip) {
                        Text("● 已保存 · ${engine.config?.model}", style = MonoStyle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                SectionLabel("会话")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { engine.newSession() }, shape = RoundedCornerShape(8.dp)) { Text("新会话") }
                    OutlinedButton(onClick = { engine.clearMemory() }, shape = RoundedCornerShape(8.dp)) { Text("清空长期记忆") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "新会话清空对话上下文；长期记忆是 Agent 跨会话保存的笔记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionLabel("后台活跃")
                val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val whitelisted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
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

                SectionLabel("命令行通道 · Shizuku（可选）")
                val shizukuState = remember(shizukuTick) {
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

                SectionLabel("主动任务（定时）")
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

                Spacer(Modifier.height(18.dp))
                Text(
                    "engine core M1/M2 · ${engine.skills.size} skills · maxRounds ${engine.config?.maxRounds ?: 15}",
                    style = MonoStyle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
}
