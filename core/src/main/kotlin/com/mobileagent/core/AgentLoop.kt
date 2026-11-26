package com.mobileagent.core

import kotlinx.serialization.json.*

/**
 * 有界工具循环——Agent 的核心（方案四道兜底全部实现）：
 * 1. 轮数上限：超过 maxRounds 强制汇报停下，防死循环烧 token
 * 2. 参数校验：模型吐的工具调用先过 JSON 解析，错了把错误回填让它重试
 * 3. 上下文压缩：估算字符超阈值时压缩早期轮次（保最近 10 条不压缩）
 * 4. 工具隔离：单个工具抛异常不影响整体，异常信息作为观察结果回填
 */
class AgentLoop(
    private val client: ModelClient,
    private val config: ModelConfig,
    private val tools: List<Tool>,
    private val skills: List<Skill> = emptyList(),
    private val onToolCall: (name: String, args: String, result: String) -> Unit = { _, _, _ -> },
    private val onToolStart: (name: String, args: String) -> Unit = { _, _ -> } // UI 显示“执行中”卡片
) {

    companion object {
        // TODO(M3/12.2 细化): 按真实模型窗口大小与 tokenizer 估算替换字符估算
        private const val COMPRESS_TRIGGER_CHARS = 24_000
        private const val KEEP_RECENT = 10
        // 单个 skill 注入上限（字符）：超长截断，防止一个技能吃掉大半上下文
        private const val SKILL_BODY_LIMIT = 16_000
    }

    private val systemPrompt = buildString {
        append("你是运行在用户设备上的 Agent，通过调用工具完成用户的任务，而不是只输出文字建议。\n")
        append("规则：\n")
        append("1. 需要文件、网络、记忆、命令时主动调工具，一步步做完再汇报\n")
        append("2. 每次工具调用后根据观察结果决定下一步，不要臆测结果\n")
        append("3. 敏感操作（删除、支付、泄露隐私）必须先征询用户\n")
        append("4. 任务完成后简洁汇报做了什么、结果在哪\n")
        if (skills.isNotEmpty()) {
            append("\n## 已加载技能\n")
            for (skill in skills) {
                append("\n### 技能：${skill.name}\n")
                val body = if (skill.body.length > SKILL_BODY_LIMIT)
                    skill.body.take(SKILL_BODY_LIMIT) + "\n…[正文过长已截断]"
                else skill.body
                append(body).append("\n")
            }
        }
    }

    /** 本轮任务元信息（UI meta 行用）：轮数与 token 累计 */
    var lastRounds: Int = 0
        private set
    var lastTokensIn: Int = 0
        private set
    var lastTokensOut: Int = 0
        private set

    /** 单次任务：把用户输入加入历史并循环到模型给出最终回答 */
    fun run(userInput: String, history: MutableList<JsonElement>): String {
        history.add(msg("user", userInput))
        var rounds = 0
        while (rounds < config.maxRounds) {
            rounds++
            compressIfNeeded(history)
            val resp = try {
                client.chat(listOf(msg("system", systemPrompt)) + history, tools)
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
        "工具执行失败：${e.message}"
    } catch (e: Exception) {
        "工具执行异常：${e.javaClass.simpleName}: ${e.message}"
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
