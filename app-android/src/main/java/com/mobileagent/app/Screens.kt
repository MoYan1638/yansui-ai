package com.mobileagent.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileagent.core.Skill

/** 方案动效规范的标准缓动曲线 */
private val AppEasing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)

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

// ---------- 共用输入框 ----------

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// ---------- 技能页 ----------

@Composable
fun SkillsScreen(engine: AgentEngine) {
    var skills by remember { mutableStateOf(engine.skills) }
    var confirmSkill by remember { mutableStateOf<Skill?>(null) }
    val gateMark = "【合规闸门】"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("技能", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(10.dp))
            Text(
                "${skills.count { !it.body.startsWith(gateMark) }}/${skills.size} 生效",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            items(skills) { skill ->
                val gated = skill.body.startsWith(gateMark)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(skill.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (gated) "待确认" else "已注入",
                                style = MonoStyle,
                                fontSize = 10.sp,
                                color = if (gated) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            skill.description.ifBlank { "（无描述）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (gated) {
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = { confirmSkill = skill }) {
                                Text("启用（需合规确认）")
                            }
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
                Text("启用「${skill.name}」前需确认：我已年满 18 周岁。\n\n该技能包含拟人化陪伴内容，依据相关规定需要完成成年确认后才会注入人设。")
            },
            confirmButton = {
                TextButton(onClick = {
                    engine.enableGatedSkill(skill.name)
                    skills = engine.skills
                    confirmSkill = null
                }) { Text("我已满 18 周岁，启用") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkill = null }) { Text("取消") }
            }
        )
    }
}

// ---------- 设置页 ----------

@Composable
fun SettingsScreen(engine: AgentEngine) {
    val cfg = engine.config
    var baseUrl by remember(cfg) { mutableStateOf(cfg?.baseUrl ?: "") }
    var model by remember(cfg) { mutableStateOf(cfg?.model ?: "") }
    var apiKey by remember(cfg) { mutableStateOf(cfg?.apiKey ?: "") }
    var savedTip by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 14.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
            item {
                Text("模型配置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Field("接口地址", baseUrl, { baseUrl = it; savedTip = false })
                Spacer(Modifier.height(10.dp))
                Field("模型名", model, { model = it; savedTip = false })
                Spacer(Modifier.height(10.dp))
                Field("API Key", apiKey, { apiKey = it; savedTip = false }, singleLine = true)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) {
                                error = "三项都不能为空"
                            } else {
                                engine.saveConfig(baseUrl, apiKey, model)
                                savedTip = true; error = null
                            }
                        }
                    ) { Text("保存") }
                    if (savedTip) {
                        TextButton(onClick = {}) { Text("✓ 已保存", color = MaterialTheme.colorScheme.primary) }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))

                Text("会话", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { engine.newSession() }) { Text("新会话") }
                    OutlinedButton(onClick = { engine.clearMemory() }) { Text("清空长期记忆") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "新会话清空对话上下文；长期记忆是 Agent 跨会话保存的笔记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))
                Text(
                    "engine core M1/M2 · ${engine.skills.size} skills · maxRounds 15",
                    style = MonoStyle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
