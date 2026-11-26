package com.mobileagent.app

import android.content.Context
import com.mobileagent.core.AgentLoop
import com.mobileagent.core.BuiltinTools
import com.mobileagent.core.ModelClient
import com.mobileagent.core.ModelConfig
import com.mobileagent.core.Skill
import com.mobileagent.core.SkillLoader
import com.mobileagent.core.readTextCompat
import com.mobileagent.core.writeTextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App 侧引擎封装：把 core 的 AgentLoop 接到 Android 上。
 * - 技能包随 APK 打包（assets/skills/），首次启动提取到 filesDir，之后每次启动覆盖刷新
 *   （用户数据在 data/ 下，不在 skills/ 下，覆盖安全）
 * - 文件/记忆工具沙箱 = 应用私有目录；run_command 在移动端一律拒绝（无 cmd.exe，也绝不该有）
 * - 会话检查点落盘 data/history.json，重启后恢复（模型上下文与界面气泡流同步重建）
 */
class AgentEngine(context: Context) {

    private val appContext = context.applicationContext
    val root: Path = appContext.filesDir.toPath()
    private val skillsDir: Path = root.resolve("skills")
    val skillUpdateDir: Path get() = skillsDir // 供设置页技能热更新读取当前版本
    private val configPath: Path = root.resolve("config.json")
    private val keysPath: Path = root.resolve("keys.json")
    private val skillStatePath: Path = root.resolve("data").resolve("skills-state.json")
    private val historyPath: Path = root.resolve("data").resolve("history.json")
    private val memoryDir: Path = root.resolve("data").resolve("memory")

    private val busy = AtomicBoolean(false)
    private val history = mutableListOf<JsonElement>() // 受 busy 标志保护，单任务串行
    private val providerKeys = mutableMapOf<String, String>() // 厂商 → Key，切换模型复用
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
    var config: ModelConfig? = null
        private set

    /** 启动初始化：提取技能 → 解析 → 过闸门 → 恢复会话检查点 → 读配置与状态。主线程调用一次 */
    fun initialize() {
        extractSkillsFromAssets()
        refreshSkills()
        loadSkillState()
        loadHistory() // 手机端系统随时杀后台，会话从检查点恢复（借鉴 Octop 的 restart-safe，自研 JSON 实现）
        config = ModelConfig.load(configPath)
        loadKeys()
        // 老配置（手填时代）把 Key 归档到厂商名下，切换模型时直接可用
        config?.let {
            if (providerKeys[providerKeyOf(it.baseUrl)].isNullOrBlank()) {
                providerKeys[providerKeyOf(it.baseUrl)] = it.apiKey
                saveKeys()
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
                memoryFile = memoryDir.resolve("memory.md") // 记忆索引注入
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
        ModelConfig.save(configPath, cfg)
        config = cfg
        providerKeys[providerKeyOf(url)] = cfg.apiKey
        saveKeys()
    }

    /** 该厂商是否已存过 Key（模型选择弹窗判断要不要补填） */
    fun keyFor(provider: String): String? = providerKeys[provider]

    fun setProviderKey(provider: String, key: String) {
        if (key.isBlank()) return
        providerKeys[provider] = key.trim()
        saveKeys()
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
        if (!Files.exists(historyPath)) return
        runCatching {
            val arr = Json.parseToJsonElement(historyPath.readTextCompat()).jsonArray
            history.clear()
            history.addAll(arr)
        }
    }

    private fun saveHistory() {
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
        Json.parseToJsonElement(historyPath.readTextCompat()).jsonArray.mapNotNull { el ->
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
    }.getOrDefault(emptyList())

    private fun loadKeys() {
        providerKeys.clear()
        if (!Files.exists(keysPath)) return
        runCatching {
            Json.parseToJsonElement(keysPath.readTextCompat()).jsonObject.forEach { (k, v) ->
                providerKeys[k] = v.jsonPrimitive.content
            }
        }
    }

    private fun saveKeys() {
        runCatching {
            keysPath.writeTextCompat(
                JsonObject(providerKeys.mapValues { JsonPrimitive(it.value) }).toString()
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

    /** 新会话：清空上下文（长期记忆保留）；任务执行中返回 false */
    fun newSession(): Boolean {
        if (busy.get()) return false
        history.clear()
        runCatching { Files.deleteIfExists(historyPath) } // 检查点同步清空
        return true
    }

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
