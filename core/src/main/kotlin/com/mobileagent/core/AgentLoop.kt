package com.mobileagent.core

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * 有界工具循环——Agent 的核心（自研方案兜底体系）：
 * 1. 轮数上限：超过 maxRounds 强制汇报停下，防死循环烧 token
 * 2. 参数校验：模型吐的工具调用先过 JSON 解析，错了把错误回填让它重试
 * 3. 上下文压缩：估算字符超阈值时压缩早期轮次（保最近 10 条不压缩）
 * 4. 工具隔离：单个工具抛异常不影响整体，异常信息作为观察结果回填
 * 5. 失败观察指引：失败结果附带行动指引，防模型拿垃圾结果继续编
 * 6. 轻量规划器：长任务先无工具生成子步骤计划再进循环（借鉴思想、自研实现，失败静默降级）
 * 7. 技能按需注入：摘要常驻 + 相关性高的注入全文，其余由模型 read_file 按需读取
 * 8. 记忆索引：相关记忆条目常驻上下文，全文用 memory_read 查看
 */
class AgentLoop(
    private val client: ModelClient,
    private val config: ModelConfig,
    private val tools: List<Tool>,
    private val skills: List<Skill> = emptyList(),
    private val onToolCall: (name: String, args: String, result: String) -> Unit = { _, _, _ -> },
    private val onToolStart: (name: String, args: String) -> Unit = { _, _ -> },
    private val workspaceRoot: Path? = null, // 技能正文按需读取：算相对路径给 read_file
    private val memoryFile: Path? = null,    // 长期记忆文件：注入相关条目索引
    private val persona: String? = null,     // 用户自定义人设（WorkBuddy 式可编辑，注入系统提示词开头）
) {

    companion object {
        // TODO(M3/12.2 细化): 按真实模型窗口大小与 tokenizer 估算替换字符估算
        private const val COMPRESS_TRIGGER_CHARS = 24_000
        private const val KEEP_RECENT = 10
        // 单个 skill 注入上限（字符）：超长截断，防止一个技能吃掉大半上下文
        private const val SKILL_BODY_LIMIT = 16_000
        // 正文全文注入阈值：短文直接注入，长文走相关性判定
        private const val SKILL_INLINE_LIMIT = 2_000
        private const val GATE_MARK = "【合规闸门】"
        private val PLAN_TRIGGER = Regex("(然后|之后|接着|首先|步骤|第[一二三四五1-5]步|一共)")
    }

    private val usageSection = buildString {
        val lines = tools.filter { it.usage.isNotBlank() }.joinToString("\n") { "- ${it.name}：${it.usage}" }
        if (lines.isNotBlank()) {
            append("\n## 工具使用要点（务必遵守）\n")
            append(lines).append("\n")
        }
    }

    /** 本轮任务元信息（UI meta 行用）：轮数与 token 累计 */
    var lastRounds: Int = 0
        private set
    var lastTokensIn: Int = 0
        private set
    var lastTokensOut: Int = 0
        private set

    // ---------- 相关性（自研：字符 bigram 重合度，零依赖） ----------

    private fun bigramScore(a: String, b: String): Double {
        if (a.length < 2 || b.length < 2) return 0.0
        val ga = a.windowed(2).toSet()
        val gb = b.windowed(2).toSet()
        val inter = ga.intersect(gb).size
        return inter.toDouble() / ga.size.coerceAtLeast(1)
    }

    /** 与任务相关的记忆条目（自研索引：bigram 匹配 top N；少则全给） */
    private fun memorySection(userInput: String): String? {
        val f = memoryFile ?: return null
        if (!Files.exists(f)) return null
        val lines = runCatching { f.readTextCompat().lineSequence().filter { it.isNotBlank() }.toList() }
            .getOrDefault(emptyList())
        if (lines.isEmpty()) return null
        val picked = if (lines.size <= 12) lines
        else lines.sortedByDescending { bigramScore(userInput, it) }.take(12)
        return "\n## 长期记忆（与任务相关的摘录；全部记忆可用 memory_read 查看）\n" +
            picked.joinToString("\n") + "\n"
    }

    /** 技能区：摘要常驻 + 相关性/短文全文注入 + 按需读取指引（gate 技能绝不提供绕过路径） */
    private fun skillSection(userInput: String): String {
        if (skills.isEmpty()) return ""
        val sb = StringBuilder("\n## 已加载技能\n")
        val needRead = mutableListOf<Skill>()
        for (skill in skills) {
            val gated = skill.body.startsWith(GATE_MARK)
            if (gated) {
                // 法规红线：闸门未确认，只给提示行，不注入正文、不给任何读取路径
                sb.append("\n### 技能：${skill.name}（未启用）\n${skill.body}\n")
                continue
            }
            val related = bigramScore(userInput, skill.name + " " + skill.description) >= 0.04
            if (skill.body.length <= SKILL_INLINE_LIMIT || related) {
                sb.append("\n### 技能：${skill.name}\n")
                val body = if (skill.body.length > SKILL_BODY_LIMIT)
                    skill.body.take(SKILL_BODY_LIMIT) + "\n…[正文过长已截断]"
                else skill.body
                sb.append(body).append("\n")
            } else {
                sb.append("\n### 技能：${skill.name}（摘要，正文未注入）\n${skill.description}\n")
                if (workspaceRoot != null) needRead.add(skill)
            }
        }
        if (needRead.isNotEmpty() && workspaceRoot != null) {
            sb.append("\n若任务与上述带「摘要」标记的技能相关，先用 read_file 读取其 SKILL.md 获取完整指令再行动：\n")
            for (skill in needRead) {
                val rel = runCatching {
                    workspaceRoot!!.relativize(skill.dir).resolve("SKILL.md").toString()
                }.getOrDefault(skill.dir.fileName.toString() + "/SKILL.md")
                sb.append("- ${skill.name}: read_file(path=\"$rel\")\n")
            }
        }
        return sb.toString()
    }

    /** 轻量规划器触发：长文本或多步骤特征（借鉴思想、自研启发式） */
    private fun needsPlan(userInput: String): Boolean =
        userInput.length >= 40 || PLAN_TRIGGER.containsMatchIn(userInput)

    private fun planFor(userInput: String): String? = try {
        val resp = client.chat(
            listOf(
                msg(
                    "system",
                    "你是任务规划器。针对用户任务输出 3-6 行执行计划，每行一个子步骤（可注明打算使用的工具）。" +
                    "只输出计划本身，不要执行、不要解释、不要多余空行。"
                ),
                msg("user", userInput)
            ),
            emptyList()
        )
        resp["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() && !it.startsWith("模型调用失败") }
    } catch (e: Exception) {
        null // 计划失败静默降级，不影响主流程
    }

    /** 单次任务：把用户输入加入历史并循环到模型给出最终回答 */
    fun run(userInput: String, history: MutableList<JsonElement>): String {
        history.add(msg("user", userInput))

        val systemPrompt = buildString {
            persona?.takeIf { it.isNotBlank() }?.let {
                append("## 人设（用户自定义，遵守其中的语气、称呼与角色设定；与安全规则冲突时以安全规则为准）\n")
                append(it.trim()).append("\n\n")
            }
            append("你是运行在用户设备上的 Agent，通过调用工具完成用户的任务，而不是只输出文字建议。\n")
            append("规则：\n")
            append("1. 需要文件、网络、记忆时主动调工具，一步步做完再汇报\n")
            append("2. 每次工具调用后根据观察结果决定下一步，不要臆测结果\n")
            append("3. 敏感操作（删除、支付、泄露隐私）必须先征询用户\n")
            append("4. 任务完成后简洁汇报做了什么、结果在哪\n")
        } + skillSection(userInput) + usageSection + (memorySection(userInput) ?: "")

        // 轻量规划器：长任务先出计划（自研降级：失败跳过）
        var rounds = 0
        val plan = if (needsPlan(userInput) && rounds == 0) planFor(userInput) else null
        if (plan != null) {
            rounds++ // 规划占用一轮配额
        }
        val finalSystem = if (plan != null)
            "$systemPrompt\n## 任务计划（先按计划逐步执行，情况变化可调整）\n$plan\n"
        else systemPrompt

        lastRounds = rounds

        while (rounds < config.maxRounds) {
            rounds++
            compressIfNeeded(history)
            val resp = try {
                client.chat(listOf(msg("system", finalSystem)) + history, tools)
            } catch (e: ModelException) {
                lastRounds = rounds
                return "模型调用失败：${e.message}"
            }
            // usage 统计（各家 OpenAI 兼容端点均有，缺失则跳过）
            (resp["usage"] as? JsonObject)?.let { u ->
                lastTokensIn += u["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                lastTokensOut += u["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            }
            val message = resp["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
            val toolCalls = message["tool_calls"]

            if (toolCalls == null || toolCalls.jsonArray.isEmpty()) {
                val content = message["content"]?.jsonPrimitive?.contentOrNull ?: "（空回复）"
                history.add(msg("assistant", content))
                lastRounds = rounds
                return content
            }

            // 带工具调用的 assistant 消息必须原样进历史
            history.add(rebuildAssistant(message))

            for (call in toolCalls.jsonArray) {
                val c = call.jsonObject
                val id = c["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val fn = c["function"]!!.jsonObject
                val name = fn["name"]!!.jsonPrimitive.content
                val argsRaw = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                onToolStart(name, argsRaw)
                val result = executeTool(name, argsRaw)
                onToolCall(name, argsRaw, result)
                history.add(buildJsonObject {
                    put("role", JsonPrimitive("tool"))
                    put("tool_call_id", JsonPrimitive(id))
                    put("content", JsonPrimitive(result))
                })
            }
        }
        lastRounds = rounds
        return "已达到轮数上限（${config.maxRounds} 轮），强制停下以免失控。以上是当前进展——" +
               "你可以让我继续、调整方向，或提高轮数上限。"
    }

    /** 兜底二：参数校验 + 兜底四：工具隔离。任何失败都变成"观察结果"回填，不炸循环 */
    private fun executeTool(name: String, argsRaw: String): String = try {
        val tool = tools.firstOrNull { it.name == name }
            ?: throw ToolException("未知工具：$name")
        val args = try {
            Json.parseToJsonElement(argsRaw).jsonObject
        } catch (e: Exception) {
            throw ToolException("参数不是合法 JSON：${e.message}，请修正后重新调用")
        }
        tool.execute(args)
    } catch (e: ToolException) {
        // 失败观察附带行动指引：防模型拿垃圾结果继续编（自研定制，非外部方案照搬）
        "工具执行失败：${e.message}\n" +
        "（该结果不可用：请修正参数重试一次；若仍失败，换一种方法完成，或向用户如实说明卡在哪）"
    } catch (e: Exception) {
        "工具执行异常：${e.javaClass.simpleName}: ${e.message}\n" +
        "（该结果不可用：不要基于此继续推理，换方法或向用户说明）"
    }

    /** 兜底三：上下文压缩。超阈值时把早期消息内容替换为占位符，最近 KEEP_RECENT 条不动 */
    private fun compressIfNeeded(history: MutableList<JsonElement>) {
        val totalChars = history.sumOf { it.toString().length }
        if (totalChars <= COMPRESS_TRIGGER_CHARS) return
        val cutoff = maxOf(0, history.size - KEEP_RECENT)
        for (i in 0 until cutoff) {
            val m = history[i].jsonObject
            val role = m["role"]?.jsonPrimitive?.contentOrNull ?: continue
            if (role == "user" && m["content"]?.jsonPrimitive?.contentOrNull?.startsWith("[压缩]") == true) continue
            if (role == "tool") {
                history[i] = buildJsonObject {
                    put("role", JsonPrimitive("tool"))
                    m["tool_call_id"]?.let { put("tool_call_id", it) }
                    put("content", JsonPrimitive("[早期工具结果已压缩]"))
                }
            } else {
                history[i] = msg(role, "[压缩] ${m["content"]?.jsonPrimitive?.contentOrNull?.take(100) ?: ""}")
            }
        }
    }

    private fun rebuildAssistant(m: JsonObject): JsonObject = buildJsonObject {
        put("role", JsonPrimitive("assistant"))
        m["content"]?.jsonPrimitive?.contentOrNull?.let { put("content", JsonPrimitive(it)) }
        m["tool_calls"]?.let { put("tool_calls", it) }
    }

    private fun msg(role: String, content: String): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        put("content", JsonPrimitive(content))
    }
}
