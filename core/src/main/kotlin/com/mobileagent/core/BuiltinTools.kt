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
    private val confirm: (command: String) -> Boolean = { true }
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
            override val parameters = """{"type":"object","properties":{"path":{"type":"string","description":"工作区内相对路径"}},"required":["path"]}"""
            override fun execute(args: JsonObject): String {
                val p = resolveInRoot(args["path"]!!.jsonPrimitive.content)
                if (!Files.exists(p)) throw ToolException("文件不存在：$p")
                return p.readTextCompat()
            }
        },
        object : Tool {
            override val name = "write_file"
            override val description = "写入（或创建）工作区内一个文本文件，覆盖式写入"
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
            override val parameters = """{"type":"object","properties":{"path":{"type":"string","description":"工作区内相对路径，默认为根目录"}}}"""
            override fun execute(args: JsonObject): String {
                val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: "."
                val p = resolveInRoot(rel)
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
            override val parameters = """{"type":"object","properties":{}}"""
            override fun execute(args: JsonObject): String {
                if (!Files.exists(memoryFile)) return "（记忆为空）"
                return memoryFile.readTextCompat()
            }
        },
        object : Tool {
            override val name = "memory_write"
            override val description = "写入长期记忆。append 追加一行；replace 整体替换（谨慎）"
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
            override val parameters = """{"type":"object","properties":{"command":{"type":"string","description":"要执行的命令"}},"required":["command"]}"""
            override fun execute(args: JsonObject): String {
                val cmd = args["command"]!!.jsonPrimitive.content
                if (!confirm(cmd)) throw ToolException("用户拒绝了该命令")
                val process = ProcessBuilder("cmd.exe", "/c", cmd)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
                val output = process.inputStream.readBytes().toString(Charsets.UTF_8).take(4000)
                return if (finished) "退出码 ${process.exitValue()}\n$output"
                else { process.destroyForcibly(); "命令超时（60s），已终止" }
            }
        }
    )
}
