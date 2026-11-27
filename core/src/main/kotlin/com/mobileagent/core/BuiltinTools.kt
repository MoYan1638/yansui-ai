package com.mobileagent.core

import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * M1 内置工具集（对应方案 12.1）。
 * 文件类工具一律沙箱在 workspaceRoot 内，防止模型路径穿越乱写系统文件。
 * run_command 是敏感操作：执行前经 confirm 钩子（命令行 = y/n 询问；App 阶段 = 待确认卡片）。
 */
class BuiltinTools(
    workspaceRoot: Path,
    memoryDir: Path,
    private val confirm: (command: String) -> Boolean = { true },
    private val commandRunner: ((command: String) -> String)? = null // 命令执行通道：桌面 JVM=本地 shell；移动端=Shizuku；未接入则拒绝
) {
    private val root: Path = workspaceRoot.toAbsolutePath().normalize()
    private val memoryFile: Path = memoryDir.resolve("memory.md").toAbsolutePath().normalize()

    private fun resolveInRoot(rel: String, forWrite: Boolean = false): Path {
        if (rel.isBlank()) throw ToolException("路径不能为空")
        val p = root.resolve(rel).normalize()
        if (!p.startsWith(root)) throw ToolException("路径越界：只允许访问工作区内（$root）")
        if (forWrite && p == root) throw ToolException("不能写工作区根目录本身")
        return p
    }

    val all: List<Tool> = listOf(
        object : Tool {
            override val name = "read_file"
            override val description = "读取工作区内一个文本文件的内容"
            override val usage = "path 只能是工作区内相对路径，不要拼绝对路径；读之前不确定文件是否存在可先用 list_dir 查看"
            override val parameters = """{"type":"object","properties":{"path":{"type":"string","description":"工作区内相对路径"}},"required":["path"]}"""
            override fun execute(args: JsonObject): String {
                val rel = args["path"]!!.jsonPrimitive.content
                val p = resolveInRoot(rel)
                // 文件尚未创建属于正常状态（首次使用的人设/记忆），不当错误——技能约定「不存在视为空」
                if (!Files.exists(p)) return "（文件不存在：$rel —— 视为空内容；需要时用 write_file 创建）"
                return p.readTextCompat()
            }
        },
        object : Tool {
            override val name = "write_file"
            override val description = "写入（或创建）工作区内一个文本文件，覆盖式写入"
            override val usage = "覆盖式写入：修改文件前必须先 read_file 原文，在原文基础上改，不要凭记忆重写整个文件"
            override val parameters = """{"type":"object","properties":{"path":{"type":"string","description":"工作区内相对路径"},"content":{"type":"string","description":"完整文件内容"}},"required":["path","content"]}"""
            override fun execute(args: JsonObject): String {
                val p = resolveInRoot(args["path"]!!.jsonPrimitive.content, forWrite = true)
                p.parent?.createDirectories()
                p.writeTextCompat(args["content"]!!.jsonPrimitive.content)
                return "已写入 ${p.fileName}（${Files.size(p)} 字节）"
            }
        },
        object : Tool {
            override val name = "list_dir"
            override val description = "列出工作区内某目录的文件与子目录"
            override val usage = "path 不传默认列根目录；只列一层，看子目录内容需再次调用"
            override val parameters = """{"type":"object","properties":{"path":{"type":"string","description":"工作区内相对路径，默认为根目录"}}}"""
            override fun execute(args: JsonObject): String {
                val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: "."
                val p = resolveInRoot(rel)
                if (!Files.exists(p)) return "（目录不存在：$rel —— 视为空）"
                if (!Files.isDirectory(p)) throw ToolException("不是目录：$p")
                return Files.list(p).use { stream ->
                    stream.map { entry ->
                        (if (Files.isDirectory(entry)) "[目录] " else "[文件] ") + entry.fileName
                    }.toList().joinToString("\n").ifBlank { "（空目录）" }
                }
            }
        },
        object : Tool {
            override val name = "http_request"
            override val description = "发起 HTTP GET/POST 请求（用于调外部 API）"
            override val usage = "仅支持 https；POST 的 body 必须是合法 JSON 字符串；返回体超过 2000 字符会被截断，需要更多分页拉取"
            override val parameters = """{"type":"object","properties":{"url":{"type":"string","description":"完整 URL，必须 https"},"method":{"type":"string","enum":["GET","POST"]},"body":{"type":"string","description":"POST 时的请求体"}},"required":["url"]}"""
            override fun execute(args: JsonObject): String {
                val url = args["url"]!!.jsonPrimitive.content
                if (!url.startsWith("https://")) throw ToolException("只允许 https 请求")
                val method = args["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
                if (method.uppercase() !in listOf("GET", "POST")) throw ToolException("不支持的方法：$method")
                // HttpURLConnection：JVM / Android 全版本通用（java.net.http 是桌面 JVM 专属）
                val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method.uppercase()
                    conn.connectTimeout = 30_000
                    conn.readTimeout = 30_000
                    if (method.uppercase() == "POST") {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.outputStream.use {
                            it.write((args["body"]?.jsonPrimitive?.contentOrNull ?: "").toByteArray(Charsets.UTF_8))
                        }
                    }
                    val code = conn.responseCode
                    val respBody = if (code in 200..299)
                        conn.inputStream.readBytes().toString(Charsets.UTF_8)
                    else
                        conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    return "HTTP $code\n${respBody.take(2000)}"
                } finally {
                    conn.disconnect()
                }
            }
        },
        object : Tool {
            override val name = "memory_read"
            override val description = "读取长期记忆（跨会话保存的用户信息与项目要点）"
            override val usage = "无需参数，直接调用；任务涉及用户个人偏好/历史约定时先读一次再行动"
            override val parameters = """{"type":"object","properties":{}}"""
            override fun execute(args: JsonObject): String {
                if (!Files.exists(memoryFile)) return "（记忆为空）"
                return memoryFile.readTextCompat()
            }
        },
        object : Tool {
            override val name = "memory_write"
            override val description = "写入长期记忆。append 追加一行；replace 整体替换（谨慎）"
            override val usage = "默认用 append 追加一条，不要轻易 replace（会抹掉全部记忆）；每次只记一条简洁事实，不要长篇大论"
            override val parameters = """{"type":"object","properties":{"mode":{"type":"string","enum":["append","replace"]},"content":{"type":"string"}},"required":["mode","content"]}"""
            override fun execute(args: JsonObject): String {
                val content = args["content"]!!.jsonPrimitive.content
                memoryFile.parent?.createDirectories()
                when (args["mode"]?.jsonPrimitive?.contentOrNull ?: "append") {
                    "append" -> memoryFile.appendTextCompat("- $content\n")
                    "replace" -> memoryFile.writeTextCompat(content)
                }
                return "记忆已保存"
            }
        },
        object : Tool {
            override val name = "run_command"
            override val description = "在工作区内执行一条命令行命令（敏感操作，会先请求用户确认）"
            override val usage = "敏感操作：调用后用户会看到确认弹窗，被拒绝就换方法，不要反复重试同一命令"
            override val parameters = """{"type":"object","properties":{"command":{"type":"string","description":"要执行的命令"}},"required":["command"]}"""
            override fun execute(args: JsonObject): String {
                val cmd = args["command"]!!.jsonPrimitive.content
                if (!confirm(cmd)) throw ToolException("用户拒绝了该命令")
                val runner = commandRunner
                    ?: throw ToolException("当前设备未接入命令行通道（未安装/未授权 Shizuku）。请改用文件/HTTP 工具完成任务，或向用户说明")
                return runner(cmd)
            }
        }
    )
}
