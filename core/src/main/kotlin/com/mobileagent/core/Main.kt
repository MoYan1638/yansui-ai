package com.mobileagent.core

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * M1 命令行入口：JVM 版核心循环的临时界面。
 * 配置：data/config.json（从 data/config.example.json 复制修改），
 * apiKey 也可用环境变量 MOBILE_AGENT_API_KEY 覆盖（不落盘）。
 */
fun main(args: Array<String>) {
    // Windows 控制台默认 GBK，强制 UTF-8 输入输出（App 阶段无此问题）
    System.setOut(java.io.PrintStream(System.out, true, Charsets.UTF_8))
    val stdin = java.io.BufferedReader(java.io.InputStreamReader(System.`in`, Charsets.UTF_8))

    val repoRoot: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val dataDir = repoRoot.resolve("data")

    val config = ModelConfig.load(dataDir.resolve("config.json")) ?: run {
        println("未找到配置文件 data/config.json")
        println("请复制 docs/config.example.json 到 data/config.json 并填入你的 API 信息。")
        exitProcess(1)
    }
    val envKey = System.getenv("MOBILE_AGENT_API_KEY")
    val effective = if (!envKey.isNullOrBlank()) config.copy(apiKey = envKey) else config

    println("Mobile Agent M1 · JVM 核心循环")
    println("模型：${effective.model} · 端点：${effective.baseUrl} · 轮数上限：${effective.maxRounds}")
    println("输入任务开始；/exit 退出，/new 开新会话，/memory 看记忆，/history 看消息数，/skills 看已加载技能")
    println("-".repeat(56))

    val client = ModelClient(effective)
    // 桌面 JVM：本地 shell 通道（Windows cmd / Unix sh），保留原有 y/N 确认
    val tools = BuiltinTools(
        workspaceRoot = repoRoot,
        memoryDir = dataDir.resolve("memory"),
        confirm = { cmd ->
            print("⚠ 执行命令（敏感操作）：$cmd\n允许吗？[y/N] ")
            stdin.readLine()?.trim()?.lowercase() == "y"
        },
        commandRunner = { cmd ->
            val isWin = System.getProperty("os.name").lowercase().contains("windows")
            val process = ProcessBuilder(*(if (isWin) arrayOf("cmd.exe", "/c", cmd) else arrayOf("sh", "-c", cmd)))
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8).take(4000)
            if (finished) "退出码 ${process.exitValue()}\n$output"
            else { process.destroyForcibly(); "命令超时（60s），已终止" }
        }
    )

    // M2：加载内置技能（skills/ 目录），love-companion 过合规闸门
    val skills = SkillLoader.loadAll(repoRoot.resolve("skills")).map { SkillLoader.applyGate(it, repoRoot) }
    if (skills.isNotEmpty()) {
        println("已加载技能：${skills.joinToString(", ") {
            if (it.body.startsWith("【合规闸门】")) "${it.name}（合规闸门拦截）" else "${it.name}（就绪）"
        }}")
    }

    val loop = AgentLoop(client, effective, tools.all, skills,
        onToolCall = { name, argsJson, result ->
            println("  [工具] $name($argsJson)")
            println("  [结果] ${result.replace("\n", "\n         ").take(300)}")
        }
    )

    val history = mutableListOf<kotlinx.serialization.json.JsonElement>()

    while (true) {
        print("\n你 > ")
        val input = stdin.readLine()?.trim() ?: break
        if (input.isEmpty()) continue
        when {
            input == "/exit" -> { println("再见"); exitProcess(0) }
            input == "/new" -> { history.clear(); println("（新会话）"); continue }
            input == "/memory" -> {
                val mf = dataDir.resolve("memory/memory.md")
                println(if (Files.exists(mf)) mf.readTextCompat() else "（记忆为空）"); continue
            }
            input == "/history" -> { println("当前会话 ${history.size} 条消息"); continue }
            input == "/skills" -> {
                if (skills.isEmpty()) println("（未加载任何技能）")
                else skills.forEach { println("● ${it.name} —— ${it.description.take(80)}…") }
                continue
            }
        }
        println()
        val answer = loop.run(input, history)
        println("\nAgent > $answer")
    }
}
