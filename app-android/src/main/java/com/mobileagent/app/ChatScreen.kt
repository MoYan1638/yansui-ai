package com.mobileagent.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 会话流条目：用户气泡 / Agent 气泡 / 工具执行卡片（原型三态）/ meta 行 */
private sealed interface ChatItem {
    data class User(val text: String, val quote: String? = null) : ChatItem
    data class Agent(val text: String) : ChatItem
    data class Meta(val rounds: Int, val maxRounds: Int, val tokens: Int, val skills: Int) : ChatItem
    /** 会话分隔：新话题开始处（旧记录保留在上方） */
    data class Divider(val label: String) : ChatItem
    data class Tool(
        val id: Int,
        val name: String,
        val args: String,
        val running: Boolean,
        val ok: Boolean,
        val result: String,
        val pending: Boolean = false // 待确认态（敏感操作，用户弹窗决定中）
    ) : ChatItem
}

private val FailColor = Color(0xFFE05A5A)

// ---------- 长按菜单（参照豆包形态：贴气泡浮出的卡片＝图标快捷行 + 竖排列表，危险项红字垫底） ----------

/** 让菜单贴住气泡：优先浮在上方，空间不足翻到下方；用户气泡右对齐、AI 气泡左对齐 */
private class BubbleMenuPosition(private val alignEnd: Boolean) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gap = 8
        var y = anchorBounds.top - popupContentSize.height - gap
        if (y < gap) y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height - gap) {
            y = (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap)
        }
        val x = (if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left)
            .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
        return IntOffset(x, y)
    }
}

@Composable
private fun QuickAction(glyph: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuRow(glyph: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            glyph,
            fontSize = 13.sp,
            color = if (danger) FailColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(21.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) FailColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

// ---------- 气泡（原型：AI 描边左尖角 / 用户 accent 底右尖角；长按浮出操作菜单） ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    text: String,
    isUser: Boolean,
    quote: String? = null,
    menuOpen: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    menu: @Composable () -> Unit = {},
) {
    val menuPosition = remember(isUser) { BubbleMenuPosition(alignEnd = isUser) }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 10.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 10.dp,
                bottomStart = 10.dp,
                bottomEnd = 10.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(onClick = { onDismissMenu() }, onLongClick = onLongPress)
        ) {
            Column {
                // 引用上文（追问时挂的引用）：左侧竖条 + 灰色小字，最多两行
                if (!quote.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            quote,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (menuOpen) {
            Popup(popupPositionProvider = menuPosition, onDismissRequest = onDismissMenu) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 12.dp,
                    modifier = Modifier.width(210.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) { menu() }
                }
            }
        }
    }
}

// ---------- 话题分隔（新话题开始处；上方旧记录保留可回看） ----------

@Composable
private fun SessionDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        )
        Text(
            "  $label  ",
            style = MonoStyle,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        )
    }
}

// ---------- 工具执行卡片（原型三态：执行中 spinner / 完成 dot / 失败红点；点头部折叠） ----------

@Composable
private fun ToolCard(item: ChatItem.Tool) {
    var open by remember { mutableStateOf(!item.running || item.pending) }
    val statusText = when {
        item.pending -> "待确认"
        item.running -> "执行中"
        item.ok -> "完成"
        item.result.contains("拒绝") -> "已拒绝"
        else -> "失败"
    }
    val statusColor = when {
        item.pending -> FailColor
        item.running -> MaterialTheme.colorScheme.onSurfaceVariant
        item.ok -> MaterialTheme.colorScheme.primary
        else -> FailColor
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Column {
            // 头部：状态 + 工具名 + 参数省略 + chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.running && !item.pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(statusText, style = MonoStyle, fontSize = 11.sp, color = statusColor)
                Spacer(Modifier.width(9.dp))
                Text(item.name, style = MonoStyle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(
                    item.args.take(48),
                    style = MonoStyle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (open) "▾" else "▸",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            // 展开：status / result 键值
            if (open) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        "status  $statusText",
                        style = MonoStyle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "result  " + item.result.ifBlank { "…" },
                        style = MonoStyle,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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

    // 从显示流恢复完整对话（含历史话题分隔；手机杀后台/退出重开后记录仍在）
    LaunchedEffect(engine) {
        if (items.isEmpty()) {
            engine.streamEntries().forEach { e ->
                when (e.tag) {
                    "divider" -> items += ChatItem.Divider(e.content)
                    "user" -> items += ChatItem.User(e.content, e.quote)
                    "assistant" -> items += ChatItem.Agent(e.content)
                }
            }
        }
    }

    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var viewingSession by remember { mutableStateOf<AgentEngine.SessionInfo?>(null) }
    var currentModel by remember { mutableStateOf(engine.config?.model) }
    var pendingConfirm by remember { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    var menuIdx by remember { mutableStateOf(-1) } // 长按浮出菜单的 items 下标（-1 无）
    var quotedText by remember { mutableStateOf<String?>(null) } // 追问引用的上文（输入框中显为引用条）
    var clearTarget by remember { mutableStateOf(-1) } // 「清除」待确认的 items 下标
    val ctxRef = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 追问挂上引用后直接聚焦输入框、弹键盘
    LaunchedEffect(quotedText) {
        if (quotedText != null) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    val clipboard = ctxRef.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val listState = rememberLazyListState()

    // 运行确认桥：工具链敏感操作 → 弹确认对话框（原型待确认卡片态）
    DisposableEffect(engine) {
        engine.onConfirmRequest = { cmd, respond ->
            // 标记最后一张执行中卡片为「待确认」
            val idx = items.indexOfLast { it is ChatItem.Tool && it.running }
            if (idx >= 0) {
                val t = items[idx] as ChatItem.Tool
                items[idx] = t.copy(pending = true)
            }
            pendingConfirm = cmd to respond
        }
        onDispose {
            engine.onConfirmRequest = null
            engine.onChatsCleared = null
        }
    }

    // 设置页「清空对话」后，同步清掉内存气泡流
    DisposableEffect(engine) {
        engine.onChatsCleared = { items.clear() }
        onDispose { engine.onChatsCleared = null }
    }

    // 新条目自动滚到底部
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
        menuIdx = -1 // 列表结构变化时收起长按菜单，避免下标错位
    }

    fun toast(msg: String) {
        android.widget.Toast.makeText(ctxRef, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun shareText(content: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        runCatching { ctxRef.startActivity(Intent.createChooser(i, "分享消息")) }
    }

    /** 删除显示流里的这条消息（工具卡片/Meta 行不入流，无需处理） */
    fun streamIndexOf(idx: Int) = items.take(idx).count {
        it is ChatItem.User || it is ChatItem.Agent || it is ChatItem.Divider
    }

    fun deleteItem(idx: Int) {
        if (engine.deleteStreamEntry(streamIndexOf(idx))) items.removeAt(idx) else toast("任务执行中，稍后再试")
    }

    /** 清除：显示流 + 模型上下文一起忘掉 */
    fun clearItem(idx: Int) {
        if (engine.clearStreamEntry(streamIndexOf(idx))) items.removeAt(idx) else toast("任务执行中，稍后再试")
    }

    fun activeSkillNames() = engine.skills
        .filter { engine.isSkillEnabled(it.name) }
        .joinToString(" · ") { it.name }

    /** 开新话题：当前对话归档进历史，上下文重置，界面上方旧记录保留 */
    fun startNewTopic() {
        if (running) return
        if (engine.startNewSession() == null) return // 执行中，拒绝
        if (items.isEmpty() || items.last() is ChatItem.Divider) return
        items += ChatItem.Divider("新话题 · 上次对话已存入历史")
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || running) return
        if (currentModel == null) { showPicker = true; return } // 未连接模型：先把选模型弹层弹出来
        val quote = quotedText?.trim()?.takeIf { it.isNotBlank() }
        input = ""
        quotedText = null
        running = true
        items += ChatItem.User(text, quote)
        var toolSeq = 0
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        // 引用上文时给模型带上出处，避免它佯装不知道在追问什么
        val prompt = if (quote == null) text else "（引用上文：${quote.take(500)}）\n\n$text"
        Thread {
            engine.send(
                input = prompt,
                onToolStart = { name, args ->
                    main.post {
                        items += ChatItem.Tool(++toolSeq, name, args.take(60), running = true, ok = true, result = "")
                    }
                },
                onToolCall = { _, _, result ->
                    main.post {
                        val idx = items.indexOfLast { it is ChatItem.Tool && it.running }
                        if (idx >= 0) {
                            val t = items[idx] as ChatItem.Tool
                            val ok = !result.startsWith("工具执行失败") && !result.startsWith("工具执行异常")
                            items[idx] = t.copy(running = false, ok = ok, result = result.take(600))
                        }
                    }
                },
                onDone = { reply, rounds, tin, tout ->
                    main.post {
                        items += ChatItem.Agent(reply)
                        items += ChatItem.Meta(
                            rounds = rounds,
                            maxRounds = engine.config?.maxRounds ?: 15,
                            tokens = tin + tout,
                            skills = engine.skills.count { engine.isSkillEnabled(it.name) }
                        )
                        engine.recordTurn(text, reply, quote) // 可见对话持久进显示流（含引用）
                        running = false
                    }
                },
                onFailed = { msg ->
                    main.post {
                        items += ChatItem.Agent("⚠ 出错了：$msg")
                        running = false
                        engine.recordTurn(text, "⚠ 出错了：$msg", quote)
                    }
                }
            )
        }.start()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 顶栏（左上角「＋」开新话题；右侧：历史入口 + 模型徽章）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 开新话题：归档当前对话 + 新上下文（旧记录留在对话流里）
            Surface(
                onClick = { startNewTopic() },
                enabled = !running,
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    "＋",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (running) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            // 历史会话（归档的旧对话，只读回看）
            Text(
                "历史",
                style = MonoStyle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { showHistory = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(2.dp))
            // 模型徽章：点击弹出选模型层
            Surface(
                onClick = { showPicker = true },
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    "${ModelPresets.displayName(currentModel)} · API",
                    style = MonoStyle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // 消息区
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                "点右上角徽章选一个内置模型，填一次 Key 就能开跑",
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
                                "core M1/M2 · ${engine.skills.count { engine.isSkillEnabled(it.name) }} 个技能已启用",
                                style = MonoStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            itemsIndexed(items) { idx, m ->
                // 长按菜单内容：图标快捷行（复制/追问）+ 竖排列表（新话题继续/分享/删除）
                val menuBody: @Composable () -> Unit = {
                    val content = when (m) {
                        is ChatItem.User -> m.text
                        is ChatItem.Agent -> m.text
                        else -> ""
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        QuickAction("⧉", "复制", Modifier.weight(1f)) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", content))
                            toast("已复制")
                            menuIdx = -1
                        }
                        QuickAction("↩", "追问", Modifier.weight(1f)) {
                            quotedText = content
                            toast("已引用该条，输入你的追问")
                            menuIdx = -1
                        }
                    }
                    MenuDivider()
                    MenuRow("✚", "在新话题中继续") {
                        startNewTopic()
                        input = content.take(400)
                        toast("已开新话题，内容已填入")
                        menuIdx = -1
                    }
                    MenuRow("↗", "分享") {
                        shareText(content)
                        menuIdx = -1
                    }
                    MenuDivider()
                    MenuRow("✕", "删除（仅对话页）", danger = true) {
                        deleteItem(idx)
                        menuIdx = -1
                    }
                    MenuRow("⌦", "清除（含上下文）", danger = true) {
                        clearTarget = idx
                        menuIdx = -1
                    }
                }
                when (m) {
                    is ChatItem.Tool -> ToolCard(m)
                    is ChatItem.Divider -> SessionDivider(m.label)
                    is ChatItem.User -> Bubble(
                        text = m.text,
                        isUser = true,
                        quote = m.quote,
                        menuOpen = menuIdx == idx,
                        onLongPress = { menuIdx = idx },
                        onDismissMenu = { menuIdx = -1 },
                        menu = menuBody
                    )
                    is ChatItem.Agent -> Bubble(
                        text = m.text,
                        isUser = false,
                        menuOpen = menuIdx == idx,
                        onLongPress = { menuIdx = idx },
                        onDismissMenu = { menuIdx = -1 },
                        menu = menuBody
                    )
                    is ChatItem.Meta -> Text(
                        "轮次 ${m.rounds}/${m.maxRounds} · tokens ${m.tokens} · 已启用 skill: ${m.skills}",
                        style = MonoStyle,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (running) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "思考中",
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 输入栏（原型：input-chip + 方形 accent 发送键 + skill-hint）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 追问时挂的引用：上方给两个快捷追问（学豆包：解释一下 / 详细展开）
            if (quotedText != null) {
                Row(
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("解释一下", "详细展开").forEach { s ->
                        Surface(
                            onClick = { input = s },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                "$s →",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column {
                    // 引用条：被引用的上文 + × 取消引用
                    quotedText?.let { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 10.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                q,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { quotedText = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        )
                    }
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (input.isEmpty()) {
                                    Text(
                                        "发消息，或描述一个任务",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    Surface(
                        onClick = { send() },
                        enabled = !running && input.isNotBlank(),
                        shape = RoundedCornerShape(9.dp),
                        color = if (running || input.isBlank()) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            "↑",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (running || input.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFF101215),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    }
                }
            }
            val hint = activeSkillNames()
            if (hint.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "已启用 $hint",
                        style = MonoStyle,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

    // 历史会话列表（新话题归档后在这里回看）
    if (showHistory) {
        val sessions = remember(showHistory) { engine.archivedSessions() }
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("历史会话") },
            text = {
                if (sessions.isEmpty()) {
                    Text(
                        "还没有历史会话。点右上角「＋」开新话题时，当前对话会归档到这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { s ->
                            Surface(
                                onClick = { viewingSession = s },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                                    Text(
                                        s.title.ifBlank { "（空会话）" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${s.timeLabel} · ${s.count} 轮对话",
                                        style = MonoStyle,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("关闭") }
            }
        )
    }

    // 查看某条历史会话（只读）
    viewingSession?.let { s ->
        val msgs = remember(s.id) { engine.sessionChat(s.id) }
        AlertDialog(
            onDismissRequest = { viewingSession = null },
            title = {
                Text(
                    s.title.ifBlank { "（空会话）" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(msgs) { (role, content) ->
                        Bubble(content, isUser = role == "user")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingSession = null }) { Text("关闭") }
            }
        )
    }

    // 「清除（含上下文）」确认：不可恢复，明确告知后果
    clearTarget.takeIf { it >= 0 }?.let { idx ->
        AlertDialog(
            onDismissRequest = { clearTarget = -1 },
            title = { Text("清除这条消息？") },
            text = {
                Text(
                    "将从对话页移除，并让模型彻底忘记这段上下文。此操作不可恢复；如需保留，请选「删除（仅对话页）」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { clearItem(idx); clearTarget = -1 }) {
                    Text("清除", color = FailColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearTarget = -1 }) { Text("取消") }
            }
        )
    }

    // 敏感操作确认（原型：允许执行 / 拒绝）
    pendingConfirm?.let { (cmd, respond) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { respond(false); pendingConfirm = null },
            title = { Text("敏感操作待确认") },
            text = {
                Column {
                    Text(
                        "Agent 请求执行以下命令：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            cmd,
                            style = MonoStyle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "命令可能影响系统状态且不可逆，请确认后再允许。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { respond(true); pendingConfirm = null }) {
                    Text("允许执行", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { respond(false); pendingConfirm = null }) {
                    Text("拒绝", color = FailColor)
                }
            }
        )
    }
}
