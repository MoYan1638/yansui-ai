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
        val task = CronStore.all(context).firstOrNull { it.id == id } ?: return
        val pending = goAsync() // 给后台执行留时间
        Thread {
            try {
                // 独立引擎实例跑任务：用临时上下文，不读写用户对话检查点——
                // 否则到点执行时若用户正在聊天，两个引擎同时读写 history.json 会互相覆盖、丢失对话
                val engine = AgentEngine(context, ephemeralHistory = true)
                engine.initialize()
                val result = CompletableFuture<String>()
                engine.send(
                    input = task.prompt,
                    onToolStart = { _, _ -> },
                    onToolCall = { _, _, _ -> },
                    onDone = { reply, _, _, _ -> result.complete(reply) },
                    onFailed = { msg -> result.complete("任务失败：$msg") }
                )
                val reply = result.get(6, TimeUnit.MINUTES)
                CronStore.updateResult(context, id, reply)
                notify(context, "● Agent 任务完成：${task.prompt.take(20)}", reply)
                if (task.repeat) CronScheduler.scheduleNext(context, task)
                else CronStore.remove(context, id) // 单次任务跑完即清
            } catch (e: Exception) {
                notify(context, "● Agent 任务失败：${task.prompt.take(20)}", e.message ?: e.javaClass.simpleName)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun notify(context: Context, title: String, text: String) {
        // 只建议不强制：未授权通知权限则静默把结果写进 lastResult，不打扰
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("agent_tasks", "主动任务", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "agent_tasks")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text.take(150))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1500)))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(title.hashCode(), n)
    }
}

/** 重启后重排全部任务 */
class CronBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CronScheduler.scheduleAll(context)
    }
}
