package com.mobileagent.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 会话流条目：用户气泡 / Agent 气泡 / 工具调用时间线 */
private sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Agent(val text: String) : ChatItem
    data class Tool(val summary: String) : ChatItem
}

@Composable
private fun Bubble(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChatScreen(
    engine: AgentEngine,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val items = remember { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var currentModel by remember { mutableStateOf(engine.config?.model) }
    val listState = rememberLazyListState()

    // 新条目自动滚到底部
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || running) return
        if (currentModel == null) { showPicker = true; return } // 未连接模型：先把选模型弹层弹出来
        input = ""
        running = true
        items += ChatItem.User(text)
        android.os.Handler(android.os.Looper.getMainLooper()).let { main ->
            Thread {
                engine.send(
                    input = text,
                    onToolCall = { _, summary -> main.post { items += ChatItem.Tool(summary) } },
                    onDone = { reply -> main.post { items += ChatItem.Agent(reply); running = false } },
                    onFailed = { msg -> main.post { items += ChatItem.Agent("⚠ 出错了：$msg"); running = false } }
                )
            }.start()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mobile Agent",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(Modifier.weight(1f))
            // 模型快捷切换：点开弹层选模型，无需进设置页
            Surface(
                onClick = { showPicker = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.width(5.dp))
                    Text(
                        ModelPresets.displayName(currentModel),
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(Modifier.width(5.dp))
                    Text("⌄", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 消息区
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
                        if (currentModel == null) {
                            // 首次使用：不挡路，直接在会话页引导选模型
                            Text(
                                "还没有连接模型",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "点右上角 ⚡ 选一个内置模型，填一次 Key 就能开跑",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { showPicker = true },
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("选择模型") }
                        } else {
                            Text(
                                "引擎就绪，派个任务试试",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "core M1/M2 · ${engine.skills.size} 个技能已加载",
                                style = MonoStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(items) { m ->
                when (m) {
                    is ChatItem.Tool -> Text(
                        "⚙ ${m.summary}",
                        style = MonoStyle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    is ChatItem.User -> Bubble(m.text, isUser = true)
                    is ChatItem.Agent -> Bubble(m.text, isUser = false)
                }
            }
            if (running) {
                item {
                    Text(
                        "● 思考中",
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        // 输入条：输入框 + 发送按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("给 Agent 派个任务", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = { send() },
                enabled = !running && input.isNotBlank(),
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (running || input.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showPicker) {
        ModelPickerSheet(
            engine = engine,
            currentModel = currentModel,
            onDismiss = { showPicker = false },
            onSwitched = { currentModel = engine.config?.model },
            onOpenSettings = onOpenSettings
        )
    }
}
