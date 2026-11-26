package com.mobileagent.app

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 门控（M4）：可选的命令行通道。
 * 原则：只建议不强制——未装/未授权时引擎用普通模式照常工作，run_command 会礼貌拒绝并给替代建议。
 */
object ShizukuGate {
    const val REQUEST_CODE = 1001

    /** Shizuku 服务是否在运行（已安装且 Binder 活着） */
    fun ping(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** 是否已授权 */
    fun granted(): Boolean = runCatching {
        ping() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 请求授权（弹 Shizuku 授权框，结果异步） */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** 经 Shizuku 执行命令（须在后台线程调用） */
    fun run(cmd: String, workdir: String): String {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, workdir)
        val finished = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
        val output = p.inputStream.readBytes().toString(Charsets.UTF_8).take(4000)
        val err = p.errorStream.readBytes().toString(Charsets.UTF_8).take(500)
        val suffix = if (err.isNotBlank()) "\n[stderr] $err" else ""
        return if (finished) "退出码 ${p.exitValue()}\n$output$suffix"
        else { p.destroyForcibly(); "命令超时（60s），已终止" }
    }
}
