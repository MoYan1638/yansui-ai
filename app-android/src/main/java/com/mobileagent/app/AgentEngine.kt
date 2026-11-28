package com.mobileagent.app

import android.content.Context
import com.mobileagent.core.AgentLoop
import com.mobileagent.core.BuiltinTools
import com.mobileagent.core.ModelClient
import com.mobileagent.core.ModelConfig
import com.mobileagent.core.Skill
import com.mobileagent.core.SkillLoader
import com.mobileagent.core.readTextCompat
import com.mobileagent.core.appendTextCompat
import com.mobileagent.core.writeTextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import java.util.concurrent.atomic.AtomicBoolean

/** 用户自定义供应商：保存后成为可选片，持久化在 data/custom-providers.json；Key 仍在 keys.json 加密 */
data class CustomProvider(val name: String, val baseUrl: String, val model: String)

/**
 * App 侧引擎封装：把 core 的 AgentLoop 接到 Android 上。
 * - 技能包随 APK 打包（assets/skills/），首次启动提取到 filesDir，之后每次启动覆盖刷新
 *   （用户数据在 data/ 下，不在 skills/ 下，覆盖安全）
 * - 文件/记忆工具沙箱 = 应用私有目录；run_command 在移动端一律拒绝（无 cmd.exe，也绝不该有）
 * - 会话检查点落盘 data/history.json，重启后恢复（模型上下文与界面气泡流同步重建）
 */
class AgentEngine(
    context: Context,
    /** 定时主动任务用：每次全新空上下文，不读写用户对话检查点，避免与 UI 会话互相覆盖 */
    private val ephemeralHistory: Boolean = false
) {

    private val appContext = context.applicationContext
    val root: Path = appContext.filesDir.toPath()
    private val skillsDir: Path = root.resolve("skills")
    val skillUpdateDir: Path get() = skillsDir // 供设置页技能热更新读取当前版本
    private val configPath: Path = root.resolve("config.json")
    private val keysPath: Path = root.resolve("keys.json")
    private val customProvidersPath: Path = root.resolve("data").resolve("custom-providers.json")
    private val skillStatePath: Path = root.resolve("data").resolve("skills-state.json")
    private val historyPath: Path = root.resolve("data").resolve("history.json")
    private val streamPath: Path = root.resolve("data").resolve("stream.json") // 对话页完整显示流（含历史话题）
    private val sessionsDir: Path = root.resolve("data").resolve("sessions") // 已归档的历史会话
    private val personaPath: Path = root.resolve("data").resolve("persona.md") // 用户自定义人设
    private val memoryDir: Path = root.resolve("data").resolve("memory")

    private val busy = AtomicBoolean(false)
    private val history = mutableListOf<JsonElement>() // 受 busy 标志保护，单任务串行
    private val stream = mutableListOf<JsonObject>()   // 对话页显示流：跨话题持久，模型上下文只跟 history
    private val providerKeys = mutableMapOf<String, String>() // 厂商 → Key，切换模型复用
    private val customProviders = mutableListOf<CustomProvider>() // 用户自定义供应商（URL/模型名），持久化
    private val disabledSkills = mutableSetOf<String>() // 技能页开关的关闭状态，持久化
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 运行确认桥（M4）：UI 层注册，工具链收到敏感操作时阻塞等待用户决定，超时/未接默拒 */
    var onConfirmRequest: ((command: String, respond: (Boolean) -> Unit) -> Unit)? = null

    private fun askConfirm(cmd: String): Boolean {
        val handler = onConfirmRequest ?: return false
        val latch = java.util.concurrent.CountDownLatch(1)
        var granted = false
        mainHandler.post { handler(cmd) { granted = it; latch.countDown() } }
        return try {
            latch.await(120, java.util.concurrent.TimeUnit.SECONDS) && granted
        } catch (e: InterruptedException) {
            false
        }
    }

    var skills: List<Skill> = emptyList()
        private set

    /** APK 内置技能（assets/skills 下的目录），技能页用它标记「内置」 */
    val builtinSkills: Set<String> by lazy {
        appContext.assets.list("skills")?.toSet() ?: emptySet()
    }
    var config: ModelConfig? = null
        private set

    /** 启动初始化：提取技能 → 解析 → 过闸门 → 恢复会话检查点 → 读配置与状态。主线程调用一次 */
    fun initialize() {
        extractSkillsFromAssets()
        refreshSkills()
        loadSkillState()
        loadHistory() // 手机端系统随时杀后台，会话从检查点恢复（借鉴 Octop 的 restart-safe，自研 JSON 实现）
        loadStream()  // 显示流恢复（含历史话题分隔；首次升级时从 history 迁移）
        config = ModelConfig.load(configPath) { ApiKeyVault.decrypt(it) ?: "" }
        loadKeys()
        loadCustomProviders()
        // 老配置（手填时代）把 Key 归档到厂商名下，切换模型时直接可用
        config?.let {
            if (providerKeys[providerKeyOf(it.baseUrl)].isNullOrBlank()) {
                providerKeys[providerKeyOf(it.baseUrl)] = it.apiKey
                saveKeys()
            }
        }
        // 迁移：旧版落盘的是明文 Key，这里升级为 Keystore 密文（一次写回，之后每次保存都是密文）
        config?.let {
            val plainStored = runCatching {
                val obj = Json.parseToJsonElement(configPath.readTextCompat()).jsonObject
                val k = obj["apiKey"]?.jsonPrimitive?.content ?: ""
                k.isNotBlank() && !ApiKeyVault.isEncrypted(k)
            }.getOrDefault(false)
            if (plainStored) {
                ModelConfig.save(configPath, it) { key -> ApiKeyVault.encrypt(key) ?: key }
            }
        }
    }

    fun refreshSkills() {
        skills = SkillLoader.loadAll(skillsDir).map { SkillLoader.applyGate(it, root) }
    }

    /** 单轮任务，阻塞执行（调用方放 IO 线程）；事件经回调吐给 UI */
    fun send(
        input: String,
        onToolStart: (name: String, args: String) -> Unit,
        onToolCall: (name: String, args: String, result: String) -> Unit,
        onDone: (reply: String, rounds: Int, tokensIn: Int, tokensOut: Int) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val cfg = config
        if (cfg == null) { onFailed("尚未连接模型，点右上角 ⚡ 选一个"); return }
        if (!busy.compareAndSet(false, true)) { onFailed("当前有任务正在执行"); return }
        try {
            val active = skills.filter { it.name !in disabledSkills } // 关闭的技能不注入
            val commandRunner: ((String) -> String)? =
                if (ShizukuGate.granted()) { cmd -> ShizukuGate.run(cmd, root.toString()) } else null
            val tools = BuiltinTools(
                workspaceRoot = root,
                memoryDir = memoryDir,
                confirm = { cmd -> askConfirm(cmd) }, // 敏感操作弹 UI 确认（原型待确认卡片）；无 UI 时默拒
                commandRunner = commandRunner // Shizuku 已授权则可用，否则拒绝并给替代建议
            )
            val loop = AgentLoop(
                client = ModelClient(cfg),
                config = cfg,
                tools = tools.all,
                skills = active,
                onToolCall = { name, args, result -> onToolCall(name, args, result) },
                onToolStart = { name, args -> onToolStart(name, args) },
                workspaceRoot = root,                       // 技能正文按需读取
                memoryFile = memoryDir.resolve("memory.md"), // 记忆索引注入
                persona = persona().takeIf { it.isNotBlank() } // 用户自定义人设注入
            )
            val reply = loop.run(input, history)
            onDone(reply, loop.lastRounds, loop.lastTokensIn, loop.lastTokensOut)
        } catch (e: Exception) {
            onFailed(e.message ?: e.javaClass.simpleName)
        } catch (e: Throwable) {
            // Error 级（NoClassDefFoundError 等）也要拦住：底层缺失绝不能砸掉进程
            onFailed("${e.javaClass.simpleName}: ${e.message ?: ""}")
        } finally {
            busy.set(false)
            saveHistory() // 每轮任务后落盘检查点（后台线程，文件小）
        }
    }

    /** 保存模型配置；family 按 baseUrl 自动推断 */
    fun saveConfig(baseUrl: String, apiKey: String, model: String, maxRounds: Int = 15) {
        val url = baseUrl.trim().trimEnd('/')
        val family = when {
            url.contains("deepseek") -> "deepseek"
            url.contains("minimax") -> "minimax"
            url.contains("localhost") || url.contains("127.0.0.1") -> "local"
            else -> "openai"
        }
        val cfg = ModelConfig(
            baseUrl = url,
            apiKey = apiKey.trim(),
            model = model.trim(),
            family = family,
            maxRounds = maxRounds.coerceIn(1, 40),
            compressRatio = 0.70
        )
        // 空 Key 不加密直接落盘（没 Key 就没必要造一段空密文）
        ModelConfig.save(configPath, cfg) { key -> if (key.isBlank()) key else ApiKeyVault.encrypt(key) ?: key }
        config = cfg
        if (cfg.apiKey.isBlank()) providerKeys.remove(providerKeyOf(url))
        else providerKeys[providerKeyOf(url)] = cfg.apiKey
        saveKeys()
    }

    /** 该厂商是否已存过 Key（模型选择弹窗判断要不要补填） */
    fun keyFor(provider: String): String? = providerKeys[provider]

    fun setProviderKey(provider: String, key: String) {
        // 空 Key = 该接口不需要密钥（本地 Ollama/免鉴权代理），清掉旧 Key 而不是拒绝保存
        if (key.isBlank()) providerKeys.remove(provider) else providerKeys[provider] = key.trim()
        saveKeys()
    }

    // ---- 自定义供应商：保存后成为可选片，可切换、可删除 ----

    /** 当前已保存的自定义供应商（快照，供设置页渲染片） */
    fun listCustomProviders(): List<CustomProvider> = customProviders.toList()

    /**
     * 登记/更新一个自定义供应商。同名（按 baseUrl 归一）覆盖，避免重复片。
     * 名字取域名主体或用户给定，切走再回来时片仍按 baseUrl 匹配回显。
     */
    fun upsertCustomProvider(baseUrl: String, model: String, name: String? = null) {
        val url = baseUrl.trim().trimEnd('/')
        if (url.isBlank()) return
        val label = name?.takeIf { it.isNotBlank() } ?: providerKeyOf(url)
        val idx = customProviders.indexOfFirst { it.baseUrl == url }
        if (idx >= 0) customProviders[idx] = CustomProvider(label, url, model.trim())
        else customProviders.add(CustomProvider(label, url, model.trim()))
        saveCustomProviders()
    }

    /** 删除自定义供应商，并清掉它名下保存的 Key */
    fun removeCustomProvider(baseUrl: String) {
        val url = baseUrl.trim().trimEnd('/')
        val removed = customProviders.firstOrNull { it.baseUrl == url } ?: return
        customProviders.remove(removed)
        providerKeys.remove(providerKeyOf(url))
        saveCustomProviders()
        saveKeys()
    }

    private fun loadCustomProviders() {
        if (ephemeralHistory) return
        customProviders.clear()
        if (!Files.exists(customProvidersPath)) return
        runCatching {
            val arr = Json.parseToJsonElement(customProvidersPath.readTextCompat()).jsonArray
            arr.forEach { el ->
                val o = el.jsonObject
                val url = o["baseUrl"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val model = o["model"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: providerKeyOf(url)
                customProviders.add(CustomProvider(name, url, model))
            }
        }
    }

    private fun saveCustomProviders() {
        if (ephemeralHistory) return
        runCatching {
            val arr = JsonArray(customProviders.map { p ->
                buildJsonObject {
                    put("name", JsonPrimitive(p.name))
                    put("baseUrl", JsonPrimitive(p.baseUrl))
                    put("model", JsonPrimitive(p.model))
                }
            })
            customProvidersPath.writeTextCompat(arr.toString())
        }
    }

    /** 切到预设模型：有 Key 直接切，没 Key 由 UI 先收集（key 参数） */
    fun switchModel(preset: ModelPreset, key: String? = null): Boolean {
        val k = (key ?: providerKeys[preset.provider])?.trim()
        if (k.isNullOrBlank()) return false
        providerKeys[preset.provider] = k
        saveKeys()
        saveConfig(preset.baseUrl, k, preset.model, config?.maxRounds ?: 15)
        return true
    }

    // ---- 会话检查点（自研：OpenAI 消息数组整体 JSON 落盘，启动时恢复） ----

    private fun loadHistory() {
        if (ephemeralHistory) return // 定时任务：全新上下文，绝不读用户对话
        if (!Files.exists(historyPath)) return
        runCatching {
            val arr = Json.parseToJsonElement(historyPath.readTextCompat()).jsonArray
            history.clear()
            // 修复旧版本压缩 bug 留下的「孤儿 tool 消息」：
            // 前面没有带 tool_calls 的 assistant 时，tool 消息会被 DeepSeek 直接 400，
            // 这里加载时顺手剔除，并记录待补错的 user 消息（孤儿前面通常是压缩占位）
            val clean = mutableListOf<JsonElement>()
            // 一条 assistant 可能声明多个并行 tool_calls，后面紧跟多条 tool 结果——
            // 必须按声明数量逐条保留，不能用单个布尔（否则第 2 条起被误判为孤儿删掉，
            // 造成 assistant 有 N 个调用却只剩 1 个结果，下次请求仍会 400）。
            var pendingToolResults = 0
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                when (obj["role"]?.jsonPrimitive?.content) {
                    "assistant" -> {
                        clean.add(el)
                        pendingToolResults = obj["tool_calls"]?.jsonArray?.size ?: 0
                    }
                    "tool" -> if (pendingToolResults > 0) {
                        clean.add(el); pendingToolResults--
                    } // 否则是孤儿 tool，丢弃
                    else -> { clean.add(el); pendingToolResults = 0 }
                }
            }
            history.addAll(clean)
            if (clean.size != arr.size) saveHistory()
        }
    }

    private fun saveHistory() {
        if (ephemeralHistory) return // 定时任务：绝不写用户对话检查点
        runCatching {
            historyPath.parent?.createDirectories()
            historyPath.writeTextCompat(JsonArray(history).toString())
        }
    }

    /**
     * 从检查点恢复界面可显示的对话（user/assistant 文本消息，按顺序）。
     * 供 ChatScreen 首次组合时重建气泡流——模型上下文和界面显示保持一致。
     */
    fun restoredChat(): List<Pair<String, String>> = runCatching {
        if (!Files.exists(historyPath)) return emptyList()
        chatPairs(Json.parseToJsonElement(historyPath.readTextCompat()).jsonArray)
    }.getOrDefault(emptyList())

    /** 消息数组 → 可显示气泡（只取 user/assistant 的文本，跳过工具与系统消息） */
    private fun chatPairs(arr: JsonArray): List<Pair<String, String>> = arr.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val role = obj["role"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (role != "user" && role != "assistant") return@mapNotNull null
        val content = (obj["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (obj["content"] as? JsonArray)?.joinToString("\n") { part ->
                (part as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: ""
            } ?: return@mapNotNull null
        if (content.isBlank()) return@mapNotNull null
        role to content
    }

    // ---- 显示流（对话页完整记录，跨话题保留；与模型上下文 history 分离） ----

    private fun msgEntry(role: String, content: String, quote: String? = null): JsonObject = buildJsonObject {
        put("t", JsonPrimitive("m")); put("r", JsonPrimitive(role)); put("c", JsonPrimitive(content))
        if (!quote.isNullOrBlank()) put("q", JsonPrimitive(quote)) // 引用上文（追问时挂的引用条）
    }

    private fun loadStream() {
        stream.clear()
        runCatching {
            if (Files.exists(streamPath)) {
                stream.addAll(Json.parseToJsonElement(streamPath.readTextCompat()).jsonArray.map { it.jsonObject })
            }
        }
        // 老版本升级迁移：有上下文检查点但显示流为空 → 把当前会话灌进显示流
        if (stream.isEmpty()) {
            val restored = restoredChat()
            if (restored.isNotEmpty()) {
                restored.forEach { (r, c) -> stream.add(msgEntry(r, c)) }
                saveStream()
            }
        }
    }

    private fun saveStream() {
        runCatching {
            streamPath.parent?.createDirectories()
            streamPath.writeTextCompat(JsonArray(stream).toString())
        }
    }

    /** 记录一轮可见对话到显示流（工具卡片不入流）；跨话题持久保留；quote = 引用的上文 */
    fun recordTurn(userText: String, replyText: String, quote: String? = null) {
        beginTurn(userText, quote)
        endTurn(replyText)
    }

    /** 新一轮开始：先把用户消息写进显示流（工具卡片随后按序插入，重启后顺序不乱） */
    fun beginTurn(userText: String, quote: String? = null) {
        stream.add(msgEntry("user", userText, quote))
        saveStream()
    }

    /** 一次工具调用完成：写进显示流（旧版只在内存里，重启就没了） */
    fun appendTool(name: String, args: String, result: String, ok: Boolean) {
        stream.add(buildJsonObject {
            put("t", JsonPrimitive("tl"))
            put("n", JsonPrimitive(name))
            put("a", JsonPrimitive(args.take(120)))
            put("ok", JsonPrimitive(ok))
            put("r", JsonPrimitive(result.take(600)))
        })
        saveStream()
    }

    /** 本轮结束：追加助手回复 */
    fun endTurn(replyText: String) {
        if (replyText.isNotBlank()) stream.add(msgEntry("assistant", replyText))
        saveStream()
    }

    /** 对话页显示流条目（tag, 内容, 引用；工具卡片额外带参数/结果） */
    data class DisplayEntry(
        val tag: String,       // divider / user / assistant / tool
        val content: String,   // 文本内容；工具条目为工具名
        val quote: String? = null,
        val args: String = "",
        val ok: Boolean = true,
        val result: String = "",
    )

    /** 对话页显示流条目 */
    fun streamEntries(): List<DisplayEntry> = stream.map {
        when (it["t"]?.jsonPrimitive?.contentOrNull) {
            "d" -> DisplayEntry("divider", it["l"]?.jsonPrimitive?.contentOrNull ?: "")
            "tl" -> DisplayEntry(
                tag = "tool",
                content = it["n"]?.jsonPrimitive?.contentOrNull ?: "工具",
                args = it["a"]?.jsonPrimitive?.contentOrNull ?: "",
                ok = it["ok"]?.jsonPrimitive?.booleanOrNull ?: true,
                result = it["r"]?.jsonPrimitive?.contentOrNull ?: ""
            )
            else -> DisplayEntry(
                it["r"]?.jsonPrimitive?.contentOrNull ?: "",
                it["c"]?.jsonPrimitive?.contentOrNull ?: "",
                it["q"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    /** 删除显示流中的一条消息（长按删除用）；只影响对话页显示，模型上下文不动 */
    fun deleteStreamEntry(index: Int): Boolean {
        if (busy.get()) return false
        if (index < 0 || index >= stream.size) return false
        stream.removeAt(index)
        saveStream()
        return true
    }

    /** 显示流第 n 条可见消息在 history 数组中的原始下标（divider 不占位） */
    private fun historySourceIndex(visibleIdx: Int): Int {
        var vi = 0
        history.forEachIndexed { i, el ->
            val obj = el as? JsonObject ?: return@forEachIndexed
            val role = obj["role"]?.jsonPrimitive?.content ?: return@forEachIndexed
            if (role != "user" && role != "assistant") return@forEachIndexed
            val content = (obj["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: (obj["content"] as? JsonArray)?.joinToString("\n") {
                    (it as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: ""
                } ?: return@forEachIndexed
            if (content.isBlank()) return@forEachIndexed
            if (vi == visibleIdx) return i
            vi++
        }
        return -1
    }

    /**
     * 清除一条消息：显示流 + 模型上下文一起删（「清除」档，不可恢复）。
     * assistant 带工具调用时，把紧随其后的 tool 结果一并删掉，避免孤儿 tool 消息报错。
     */
    fun clearStreamEntry(index: Int): Boolean {
        if (busy.get()) return false
        if (index < 0 || index >= stream.size) return false
        val visibleIdx = stream.take(index).count { it["t"]?.jsonPrimitive?.contentOrNull == "m" }
        stream.removeAt(index)
        saveStream()
        runCatching {
            val src = historySourceIndex(visibleIdx)
            if (src >= 0) {
                val isToolCall = (history[src] as? JsonObject)?.get("tool_calls") != null
                history.removeAt(src)
                if (isToolCall) {
                    while (src < history.size &&
                        (history[src] as? JsonObject)?.get("role")?.jsonPrimitive?.content == "tool"
                    ) history.removeAt(src)
                }
                saveHistory()
            }
        }
        return true
    }

    // ---- 历史会话（新会话=归档旧对话 + 开新上下文，记录永不丢） ----

    /** 归档会话的摘要，供「历史」列表展示 */
    data class SessionInfo(val id: String, val title: String, val count: Int, val timeLabel: String)

    /** 把当前上下文写进 sessions/<时间戳>.json；返回归档 id（无内容返回 null） */
    private fun archiveHistory(): String? {
        if (history.isEmpty()) return null
        return runCatching {
            sessionsDir.createDirectories()
            val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            var id = fmt.format(java.util.Date())
            var f = sessionsDir.resolve("$id.json")
            var n = 1
            while (Files.exists(f)) { id = fmt.format(java.util.Date()) + "-$n"; f = sessionsDir.resolve("$id.json"); n++ }
            f.writeTextCompat(JsonArray(history).toString())
            id
        }.getOrNull()
    }

    /** 历史会话列表（新的在前） */
    fun archivedSessions(): List<SessionInfo> = runCatching {
        if (!Files.exists(sessionsDir)) return emptyList()
        Files.list(sessionsDir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }
                .sorted(Comparator.reverseOrder())
                .map { p ->
                    val pairs = runCatching {
                        chatPairs(Json.parseToJsonElement(p.readTextCompat()).jsonArray)
                    }.getOrDefault(emptyList())
                    val rawId = p.fileName.toString().removeSuffix(".json")
                    SessionInfo(
                        id = rawId,
                        title = pairs.firstOrNull { it.first == "user" }?.second?.take(24)?.replace("\n", " ")
                            ?: "（空会话）",
                        count = pairs.count { it.first == "user" },
                        timeLabel = rawId.take(8).let {
                            "${it.substring(4, 6)}/${it.substring(6, 8)} " +
                                rawId.substringAfter("-", "").take(4).let { t ->
                                    if (t.length >= 4) "${t.substring(0, 2)}:${t.substring(2, 4)}" else ""
                                }
                        }.trim()
                    )
                }.collect(java.util.stream.Collectors.toList())
        }
    }.getOrDefault(emptyList())

    /** 读取某个归档会话的可显示消息 */
    fun sessionChat(id: String): List<Pair<String, String>> = runCatching {
        val f = sessionsDir.resolve("$id.json")
        if (!Files.exists(f)) return emptyList()
        chatPairs(Json.parseToJsonElement(f.readTextCompat()).jsonArray)
    }.getOrDefault(emptyList())

    /**
     * 新会话：当前对话归档到历史，然后清空上下文开新话题（长期记忆保留）。
     * 任务执行中返回 null。
     */
    fun startNewSession(): String? {
        if (busy.get()) return null
        val archived = archiveHistory()
        history.clear()
        runCatching { Files.deleteIfExists(historyPath) } // 检查点同步清空
        // 显示流不清空：插入话题分隔，旧记录继续留在对话页
        stream.add(buildJsonObject {
            put("t", JsonPrimitive("d")); put("l", JsonPrimitive("新话题 · 上次对话已存入历史"))
        })
        saveStream()
        return archived ?: ""
    }

    private fun loadKeys() {
        providerKeys.clear()
        if (!Files.exists(keysPath)) return
        runCatching {
            Json.parseToJsonElement(keysPath.readTextCompat()).jsonObject.forEach { (k, v) ->
                providerKeys[k] = ApiKeyVault.decrypt(v.jsonPrimitive.content) ?: ""
            }
        }
    }

    private fun saveKeys() {
        runCatching {
            keysPath.writeTextCompat(
                JsonObject(providerKeys.mapValues { (_, v) ->
                    JsonPrimitive(ApiKeyVault.encrypt(v) ?: v)
                }).toString()
            )
        }
    }

    private fun loadSkillState() {
        disabledSkills.clear()
        if (!Files.exists(skillStatePath)) return
        runCatching {
            Json.parseToJsonElement(skillStatePath.readTextCompat()).jsonObject.forEach { (k, v) ->
                if (v.jsonPrimitive.booleanOrNull == false) disabledSkills.add(k)
            }
        }
    }

    private fun saveSkillState() {
        runCatching {
            skillStatePath.parent?.createDirectories()
            skillStatePath.writeTextCompat(
                JsonObject(skills.associate { it.name to JsonPrimitive(it.name !in disabledSkills) }).toString()
            )
        }
    }

    // ---- 人设与记忆（WorkBuddy 式：用户可直接在设置页编辑） ----

    /** 当前人设文本（data/persona.md，空文件返回空串） */
    fun persona(): String = runCatching {
        if (!Files.exists(personaPath)) "" else personaPath.readTextCompat()
    }.getOrDefault("")

    /** 保存人设；下一条消息即生效（AgentLoop 每次任务重新构造） */
    fun savePersona(text: String) {
        personaPath.parent?.createDirectories()
        personaPath.writeTextCompat(text.trim())
    }

    /** 当前长期记忆全文（data/memory/memory.md） */
    fun memoryText(): String = runCatching {
        val f = memoryDir.resolve("memory.md")
        if (!Files.exists(f)) "" else f.readTextCompat()
    }.getOrDefault("")

    /** 保存长期记忆全文 */
    fun saveMemory(text: String) {
        memoryDir.createDirectories()
        memoryDir.resolve("memory.md").writeTextCompat(text.trim())
    }

    /**
     * 从历史对话提炼记忆与人设（一次性模型调用，无工具）。
     * 覆盖当前会话 + 最近 2 个归档会话；结果回调给 UI 确认后落盘。
     * 返回 false 表示忙或未连模型。
     */
    fun distillFromHistory(
        onDone: (memoryPart: String, personaPart: String) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val cfg = config
        if (cfg == null) { onFailed("尚未连接模型"); return }
        if (!busy.compareAndSet(false, true)) { onFailed("当前有任务正在执行"); return }
        Thread {
            try {
                val sb = StringBuilder()
                fun feed(pairs: List<Pair<String, String>>) {
                    pairs.takeLast(60).forEach { (r, c) ->
                        sb.append(if (r == "user") "用户: " else "AI: ").append(c.take(400)).append('\n')
                    }
                }
                feed(restoredChat())
                archivedSessions().take(2).forEach { feed(sessionChat(it.id)) }
                if (sb.isBlank()) { onFailed("还没有历史对话可提炼"); return@Thread }
                val prompt = "以下是你和用户的历史对话记录。请提炼值得长期记住的信息，输出两段（严格用这两个标题，不要多余的话）：\n" +
                    "## 记忆\n- 用户个人信息、喜好厌恶、重要日期、约定、项目背景，一行一条简洁事实（没有就写「无」）\n" +
                    "## 人设建议\n- 综合对话中用户对 AI 角色的期望，给一段 60 字以内的角色设定（性格、称呼、语气；没有则写「无」）\n\n" +
                    "对话记录：\n${sb.take(12000)}"
                val resp = ModelClient(cfg).chat(
                    listOf(buildJsonObject { put("role", JsonPrimitive("user")); put("content", JsonPrimitive(prompt)) }),
                    emptyList()
                )
                val content = (((resp["choices"]?.jsonArray?.firstOrNull() as? JsonObject)
                    ?.get("message") as? JsonObject)
                    ?.get("content") as? JsonPrimitive)?.content ?: ""
                val memPart = content.substringAfter("## 记忆", "").substringBefore("##").trim()
                val perPart = content.substringAfter("## 人设建议", "").trim()
                if (memPart.isBlank() && perPart.isBlank()) { onFailed("提炼失败，模型没给出有效内容"); return@Thread }
                onDone(memPart.removePrefix("-").trim(), perPart.trim())
            } catch (e: Exception) {
                onFailed(e.message ?: e.javaClass.simpleName)
            } catch (e: Throwable) {
                onFailed("${e.javaClass.simpleName}: ${e.message ?: ""}")
            } finally {
                busy.set(false)
            }
        }.start()
    }

    /** 把提炼结果落盘：记忆追加、人设仅在为空时写入（不覆盖用户已写的） */
    fun applyDistilled(memoryPart: String, personaPart: String) {
        if (memoryPart.isNotBlank() && memoryPart != "无") {
            memoryDir.createDirectories()
            memoryPart.lineSequence().filter { it.isNotBlank() }.forEach {
                memoryDir.resolve("memory.md").appendTextCompat("- ${it.removePrefix("- ").trim()}\n")
            }
        }
        if (personaPart.isNotBlank() && personaPart != "无" && persona().isBlank()) {
            savePersona(personaPart)
        }
    }

    /** 清空全部对话记录：当前上下文 + 显示流 + 归档会话（人设/记忆/配置/技能都不动）；执行中返回 false */
    fun clearAllChats(): Boolean {
        if (busy.get()) return false
        history.clear()
        stream.clear()
        runCatching { Files.deleteIfExists(historyPath) }
        runCatching { Files.deleteIfExists(streamPath) }
        runCatching {
            if (Files.exists(sessionsDir)) {
                val files = Files.list(sessionsDir).use { s -> s.collect(java.util.stream.Collectors.toList()) }
                files.forEach { Files.deleteIfExists(it) }
            }
        }
        onChatsCleared?.invoke()
        return true
    }

    /** 对话被清空时通知界面（ChatScreen 注册，清掉内存气泡） */
    var onChatsCleared: (() -> Unit)? = null

    // ---- 技能开关（技能页 Switch；关闭的技能不注入提示词） ----

    fun isSkillEnabled(name: String): Boolean = name !in disabledSkills

    fun setSkillEnabled(name: String, enabled: Boolean) {
        if (enabled) disabledSkills.remove(name) else disabledSkills.add(name)
        saveSkillState()
    }

    /** 清空长期记忆文件；任务执行中返回 false */
    fun clearMemory(): Boolean {
        if (busy.get()) return false
        val f = memoryDir.resolve("memory.md").toFile()
        return !f.exists() || f.delete()
    }

    /**
     * 启用带合规闸门的技能：把闸门字段写为 true 后重新过闸门。
     * 调用方（UI）必须先让用户显式确认年满 18 周岁。
     */
    fun enableGatedSkill(skillName: String): Boolean {
        val skill = skills.firstOrNull { it.name == skillName } ?: return false
        val gateFile = skill.dir.resolve("gate.json")
        if (!Files.exists(gateFile)) return false
        val gate = Json.parseToJsonElement(gateFile.readTextCompat()).jsonObject
        val rel = gate["setting"]?.jsonPrimitive?.content ?: return false
        val field = gate["field"]?.jsonPrimitive?.content ?: return false
        val settingsPath = root.resolve(rel).normalize()
        settingsPath.parent?.createDirectories()
        val existing: JsonObject = if (Files.exists(settingsPath))
            Json.parseToJsonElement(settingsPath.readTextCompat()).jsonObject else JsonObject(emptyMap())
        settingsPath.writeTextCompat(setDeep(existing, field.split('.'), true).toString())
        refreshSkills()
        return true
    }

    /** 深合并写嵌套 JSON 字段（合规确认 → {"合规":{"已满18周岁确认":true}}） */
    private fun setDeep(target: JsonObject, keys: List<String>, value: Boolean): JsonObject {
        if (keys.isEmpty()) return target
        if (keys.size == 1) {
            return JsonObject(target.toMutableMap().apply { put(keys[0], JsonPrimitive(value)) })
        }
        val child = (target[keys.first()] as? JsonObject) ?: JsonObject(emptyMap())
        return JsonObject(target.toMutableMap().apply { put(keys.first(), setDeep(child, keys.drop(1), value)) })
    }

    /**
     * 从 assets/skills 递归提取技能包（覆盖式，用户数据在 data/ 不受影响）。
     * 已热更新过的技能目录（有 .hotupdate.json 标记）跳过——本地更新版优先于 APK 内置版。
     */
    private fun extractSkillsFromAssets() {
        fun copy(assetPath: String, target: Path) {
            val children = appContext.assets.list(assetPath) ?: return
            if (children.isEmpty()) {
                target.parent?.createDirectories()
                appContext.assets.open(assetPath).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                for (child in children) copy("$assetPath/$child", target.resolve(child))
            }
        }
        // 顶层按技能目录跳过：热更新过的目录保持云端版本，不被 APK 内置旧版覆盖
        appContext.assets.list("skills")?.forEach { skill ->
            if (Files.exists(skillsDir.resolve(skill).resolve(".hotupdate.json"))) return@forEach
            copy("skills/$skill", skillsDir.resolve(skill))
        }
    }
}
