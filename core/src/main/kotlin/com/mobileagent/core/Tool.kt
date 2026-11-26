package com.mobileagent.core

import kotlinx.serialization.json.*

/**
 * 工具接口：skill 的内置工具与未来 skill 自带工具都实现此接口。
 * parameters 为 JSON Schema 字符串，喂给模型做 function calling。
 */
interface Tool {
    val name: String
    val description: String
    val parameters: String

    fun execute(args: JsonObject): String

    fun toOpenAiSchema(): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", Json.parseToJsonElement(parameters))
        })
    }
}

class ToolException(message: String) : Exception(message)
