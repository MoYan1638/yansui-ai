package com.mobileagent.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 模型预设：全部为国内可直连端点（或本机），零谷歌依赖。
 * provider 用于归档各家 API Key —— 同一家换模型不用重填 Key。
 */
data class ModelPreset(
    val provider: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val desc: String,
    val window: String,
    val badges: List<String> = emptyList(),
)

/** 按 baseUrl 反推 Key 归属的厂商（切换模型时复用 Key） */
fun providerKeyOf(baseUrl: String): String {
    val u = baseUrl.lowercase()
    return when {
        u.contains("deepseek") -> "deepseek"
        u.contains("moonshot") || u.contains("kimi") -> "moonshot"
        u.contains("bigmodel") || u.contains("zhipu") -> "zhipu"
        u.contains("dashscope") || u.contains("aliyun") -> "qwen"
        u.contains("minimax") -> "minimax"
        u.contains("siliconflow") -> "siliconflow"
        u.contains("localhost") || u.contains("127.0.0.1") -> "local"
        else -> u.removePrefix("https://").removePrefix("http://").substringBefore('/')
    }
}

object ModelPresets {
    val all = listOf(
        ModelPreset(
            provider = "deepseek", name = "DeepSeek-V3.2",
            baseUrl = "https://api.deepseek.com/v1", model = "deepseek-chat",
            desc = "通用对话 + 工具调用，便宜够用", window = "128K",
            badges = listOf("推荐", "低价")
        ),
        ModelPreset(
            provider = "deepseek", name = "DeepSeek-R1",
            baseUrl = "https://api.deepseek.com/v1", model = "deepseek-reasoner",
            desc = "先思考再回答，慢一点但更稳", window = "128K",
            badges = listOf("推理")
        ),
        ModelPreset(
            provider = "moonshot", name = "Kimi K2",
            baseUrl = "https://api.moonshot.cn/v1", model = "kimi-k2-0905-preview",
            desc = "长文与代码，上下文大", window = "256K",
            badges = listOf("长上下文")
        ),
        ModelPreset(
            provider = "zhipu", name = "GLM-4.6",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4", model = "glm-4.6",
            desc = "中文理解强，工具调用稳定", window = "128K"
        ),
        ModelPreset(
            provider = "qwen", name = "通义千问 Plus",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1", model = "qwen-plus",
            desc = "阿里云节点，国内延迟低", window = "128K"
        ),
        ModelPreset(
            provider = "minimax", name = "MiniMax M2",
            baseUrl = "https://api.minimaxi.com/v1", model = "MiniMax-M2",
            desc = "多模态与语音见长", window = "200K"
        ),
        ModelPreset(
            provider = "siliconflow", name = "硅基流动 · DeepSeek",
            baseUrl = "https://api.siliconflow.cn/v1", model = "deepseek-ai/DeepSeek-V3",
            desc = "一个 Key 聚合多款开源模型", window = "64K"
        ),
        ModelPreset(
            provider = "local", name = "本地 Ollama",
            baseUrl = "http://127.0.0.1:11434/v1", model = "qwen3:8b",
            desc = "本机 / 局域网跑，不花钱", window = "—",
            badges = listOf("离线")
        ),
    )

    /** 顶栏展示名：命中预设用预设名，否则原样显示模型 id */
    fun displayName(model: String?): String =
        all.firstOrNull { it.model == model }?.name ?: (model ?: "未配置")
}

// ---------- 模型选择弹层 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    engine: AgentEngine,
    currentModel: String?,
    onDismiss: () -> Unit,
    onSwitched: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var askKeyFor by remember { mutableStateOf<ModelPreset?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun pick(p: ModelPreset) {
        if (engine.keyFor(p.provider).isNullOrBlank()) {
            askKeyFor = p
        } else if (engine.switchModel(p)) {
            onSwitched()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择模型", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "${ModelPresets.all.size} 个内置",
                    style = MonoStyle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))

            ModelPresets.all.forEach { p ->
                ModelRow(
                    preset = p,
                    selected = p.model == currentModel,
                    onClick = { pick(p) }
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCustom = true }
                    .padding(horizontal = 6.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✎", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("配置自定义模型", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "自填接口地址 / 模型名 / Key，任意 OpenAI 兼容服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = { onDismiss(); onOpenSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("在设置页管理 · 支持微调模型名", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // 首次连某家厂商：补 Key
    askKeyFor?.let { p ->
        ApiKeyDialog(
            preset = p,
            onDismiss = { askKeyFor = null },
            onConfirm = { key ->
                if (engine.switchModel(p, key)) {
                    askKeyFor = null
                    onSwitched()
                    onDismiss()
                }
            }
        )
    }

    if (showCustom) {
        CustomModelDialog(
            engine = engine,
            onDismiss = { showCustom = false },
            onSaved = { showCustom = false; onSwitched(); onDismiss() }
        )
    }
}

@Composable
private fun ModelRow(preset: ModelPreset, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                preset.name.take(1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                preset.badges.forEach { b ->
                    Spacer(Modifier.width(6.dp))
                    Badge(b, highlighted = b == "推荐" || b == "离线")
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                preset.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            preset.window,
            style = MonoStyle,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = "当前",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun Badge(text: String, highlighted: Boolean) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, style = MonoStyle, fontSize = 9.sp, color = color)
    }
}

@Composable
private fun ApiKeyDialog(preset: ModelPreset, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接 ${preset.name}") },
        text = {
            Column {
                Text(
                    "该模型需要 ${preset.provider} 的 API Key，填一次即可（换同家模型不用再填）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(preset.baseUrl, style = MonoStyle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key) }, enabled = key.isNotBlank()) { Text("连接") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CustomModelDialog(engine: AgentEngine, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var baseUrl by remember { mutableStateOf(engine.config?.baseUrl ?: "") }
    var model by remember { mutableStateOf(engine.config?.model ?: "") }
    var key by remember { mutableStateOf(engine.config?.apiKey ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("接口地址（OpenAI 兼容）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("模型名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("API Key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    engine.setProviderKey(providerKeyOf(baseUrl), key)
                    engine.saveConfig(baseUrl, key, model)
                    onSaved()
                },
                enabled = baseUrl.isNotBlank() && model.isNotBlank() && key.isNotBlank()
            ) { Text("保存并使用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
