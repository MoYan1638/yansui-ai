package com.mobileagent.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.util.Calendar
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** 主动任务（自研 Cron，借鉴思想、Android 原生 AlarmManager 实现）：每日/单次，到点后台跑 Agent 并发通知 */
data class CronTask(
    val id: String,
    val prompt: String,
    val time: String,        // "HH:mm"
    val repeat: Boolean,     // true = 每天
    val lastResult: String? = null
)

// ---------- 存储（自研 JSON，与其余状态一致的落盘方式） ----------

object CronStore {

    private fun file(context: Context) =
        java.io.File(context.filesDir, "data/cron.json")

    fun all(context: Context): List<CronTask> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        Json.parseToJsonElement(f.readText()).jsonArray.map { o ->
            val obj = o.jsonObject
            CronTask(
                id = obj["id"]!!.jsonPrimitive.content,
                prompt = obj["prompt"]!!.jsonPrimitive.content,
                time = obj["time"]!!.jsonPrimitive.content,
                repeat = obj["repeat"]!!.jsonPrimitive.boolean,
                lastResult = obj["lastResult"]?.jsonPrimitive?.contentOrNull
            )
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, tasks: List<CronTask>) {
        runCatching {
            file(context).parentFile?.mkdirs()
            file(context).writeText(buildJsonArray {
                tasks.forEach { t ->
                    add(buildJsonObject {
                        put("id", t.id)
                        put("prompt", t.prompt)
                        put("time", t.time)
                        put("repeat", t.repeat)
                        t.lastResult?.let { put("lastResult", it) }
                    })
                }
            }.toString())
        }
    }

    fun add(context: Context, prompt: String, time: String, repeat: Boolean): CronTask {
        val task = CronTask(
            id = System.currentTimeMillis().toString(),
            prompt = prompt.trim(),
            time = time.trim(),
            repeat = repeat
        )
        save(context, all(context) + task)
        return task
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun updateResult(context: Context, id: String, result: String) {
        save(context, all(context).map { if (it.id == id) it.copy(lastResult = result.take(300)) else it })
    }
}

// ---------- 调度（setAndAllowWhileIdle：Doze 下也能走，无需精确闹钟权限） ----------

object CronScheduler {
    const val EXTRA_TASK_ID = "task_id"
    private const val REQUEST_BASE = 20000

    private fun pendingIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_BASE + id.hashCode(),
        Intent(context, CronTaskReceiver::class.java).putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** 排下一次触发（明天/今天稍后同分钟） */
    fun scheduleNext(context: Context, task: CronTask) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val parts = task.time.split(":")
        if (parts.size != 2) return
        val h = parts[0].trim().toIntOrNull() ?: return
        val m = parts[1].trim().toIntOrNull() ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent(context, task.id))
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, id))
    }

    /** 启动/重启后重排全部任务（Octop restart-safe 思想的手机端实现） */
    fun scheduleAll(context: Context) {
        CronStore.all(context).forEach { scheduleNext(context, it) }
    }
}

// ---------- 到点执行 + 通知 ----------

class CronTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CronScheduler.EXTRA_TASK_ID) ?: return
        // 手机切后台会冻结/杀进程：goAsync 只有约 10 秒窗口，撑不住 Agent 多轮任务。
        // 首选前台服务保活（常驻通知，系统不杀）。但 Android 12+ 后台启动前台服务有限制：
        // 用户关了电池优化（白名单）属豁免可启动；否则用 goAsync 尽力跑短任务。
        // 无论哪条路都 try-catch 兜住，绝不让系统限制抛的异常把进程崩掉。
        val inWhitelist = try {
            (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (e: Exception) { false }

        if (Build.VERSION.SDK_INT < 31 || inWhitelist) {
            try {
                val svc = Intent(context, AgentTaskService::class.java)
                    .putExtra(CronScheduler.EXTRA_TASK_ID, id)
                androidx.core.content.ContextCompat.startForegroundService(context, svc)
                return
            } catch (e: Exception) {
                // 前台服务启动被系统拒（含 ForegroundServiceStartNotAllowedException）：降级
            }
        }
        // 兜底：前台服务不可用时的尽力执行（未加白名单时系统可能冻结，短任务或可完成）
        val pending = goAsync()
        Thread {
            try {
                AgentTaskService.executeTask(context, id)
            } finally {
                pending.finish()
            }
        }.start()
    }

}

/** 重启后重排全部任务 */
class CronBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CronScheduler.scheduleAll(context)
    }
}
