package com.mobileagent.core

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * 模型配置（对应自研方案 12.4 的 JSON 字段定案；JVM 阶段 key 落在本地 data/ 目录，
 * Android 阶段改为只存 Keystore 别名，本结构不变）
 */
data class ModelConfig(
    val baseUrl: String,      // OpenAI 兼容端点，不含 /chat/completions
    val apiKey: String,
    val model: String,
    val family: String,       // 按模型家族挂提示词模板
    val maxRounds: Int,       // 有界循环轮数上限（方案四道兜底之一）
    val compressRatio: Double // 上下文压缩触发阈值
) {
    companion object {
        fun load(path: Path): ModelConfig? = try {
            val obj = Json.parseToJsonElement(path.readTextCompat()).jsonObject
            ModelConfig(
                baseUrl = obj["baseUrl"]!!.jsonPrimitive.content.trimEnd('/'),
                apiKey = obj["apiKey"]!!.jsonPrimitive.content,
                model = obj["model"]!!.jsonPrimitive.content,
                family = (obj["family"] ?: JsonPrimitive("openai-generic")).jsonPrimitive.content,
                maxRounds = (obj["maxRounds"] ?: JsonPrimitive(15)).jsonPrimitive.int,
                compressRatio = (obj["compressRatio"] ?: JsonPrimitive(0.70)).jsonPrimitive.double
            )
        } catch (e: Exception) {
            null
        }

        /** 持久化配置（App 设置页/首启引导写回用） */
        fun save(path: Path, c: ModelConfig) {
            path.parent?.createDirectories()
            path.writeTextCompat(buildJsonObject {
                put("baseUrl", c.baseUrl)
                put("apiKey", c.apiKey)
                put("model", c.model)
                put("family", c.family)
                put("maxRounds", c.maxRounds)
                put("compressRatio", c.compressRatio)
            }.toString())
        }
    }
}

class ModelException(message: String) : Exception(message)

/**
 * 模型适配层：统一走 OpenAI 兼容协议（chat/completions + tool calling），
 * 零 SDK 依赖，直接 java.net.http 调用。各家 API 与 Ollama/LM Studio 均吃这一个口。
 */
class ModelClient(private val config: ModelConfig) {

    private val http = java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    /**
     * 发起一轮对话。messages 为 OpenAI 格式数组，tools 非空时带上工具 Schema。
     * 返回响应 JSON（取 choices[0].message）。
     */
    fun chat(messages: List<JsonElement>, tools: List<Tool>): JsonObject {
        val body = buildJsonObject {
            put("model", config.model)
            put("messages", JsonArray(messages))
            put("temperature", 0.7)
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { it.toOpenAiSchema() }))
            }
        }
        val request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("${config.baseUrl}/chat/completions"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(120))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val resp = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw ModelException("HTTP ${resp.statusCode()}: ${resp.body().take(500)}")
        }
        return Json.parseToJsonElement(resp.body()).jsonObject
    }
}
