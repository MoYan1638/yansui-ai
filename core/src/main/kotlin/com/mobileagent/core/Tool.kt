package com.mobileagent.core

import kotlinx.serialization.json.*

/**
 * 工具接口：skill 的内置工具与未来 skill 自带工具都实现此接口。
 * parameters 为 JSON Schema 字符串，喂给模型做 function calling。
 * usage 为自研的「使用要点」：一行实测经验/约束/易错点，注入系统提示词帮小模型传参少踩坑
 * （思想来自开源框架的工具卡，形态是自研的轻量单行，不引入外部格式）。
 */
interface Tool {
    val name: String
    val description: String
    val parameters: String

    /** 使用要点（可选）：约束、易错点、最佳实践，空串则不注入 */
    val usage: String get() = ""

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
