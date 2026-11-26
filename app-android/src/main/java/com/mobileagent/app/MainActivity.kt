package com.mobileagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 会话气泡：用户右对齐，Agent 左对齐 */
private data class ChatMessage(val isUser: Boolean, val text: String)

/**
 * M3 骨架：主界面壳（首页原型 → 会话页）。
 * 当前阶段只接 UI 与主题，引擎接线（AgentLoop 后台执行）为下一步。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileAgentTheme {
                ChatScreen()
            }
        }
    }
}

@Composable
private fun ChatScreen() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
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
            Text(
                "deepseek-chat · M3 骨架",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 消息区
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "●",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 28.sp
                        )
                        Text(
                            "引擎已就绪，界面接线中",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "core 模块 · M1/M2 验证通过",
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(messages) { m ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (m.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (m.isUser) 16.dp else 4.dp,
                            bottomEnd = if (m.isUser) 4.dp else 16.dp
                        ),
                        color = if (m.isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(
                            m.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (m.isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 输入条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("给 Agent 派个任务", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    }
}
