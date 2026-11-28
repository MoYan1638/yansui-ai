package com.mobileagent.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 定时主动任务的前台服务。
 *
 * 手机和桌面最大的区别：App 切后台后进程随时被系统冻结/杀死。
 * 旧实现用 BroadcastReceiver.goAsync() 起线程等 6 分钟——goAsync 只有约 10 秒窗口，
 * 超时后进程优先级掉到后台，任务几乎必然被腰斩。前台服务带常驻通知，系统才不会杀。
 */
class AgentTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(CronScheduler.EXTRA_TASK_ID)
        if (id == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Android 要求前台服务启动后立即（数秒内）调用 startForeground
        startForeground(NOTIF_ID, runningNotification())

        Thread {
            try {
                val task = CronStore.all(this).firstOrNull { it.id == id }
                if (task == null) {
                    stopSelf(startId)
                    return@Thread
                }
                // 独立引擎 + 临时上下文：不读写用户对话检查点，避免与 UI 会话互相覆盖
                val engine = AgentEngine(this, ephemeralHistory = true)
                engine.initialize()
                val result = CompletableFuture<String>()
                engine.send(
                    input = task.prompt,
                    onToolStart = { _, _ -> },
                    onToolCall = { _, _, _ -> },
                    onDone = { reply, _, _, _ -> result.complete(reply) },
                    onFailed = { msg -> result.complete("任务失败：$msg") }
                )
                val reply = result.get(10, TimeUnit.MINUTES)
                CronStore.updateResult(this, id, reply)
                notifyTaskResult(this, "● Agent 任务完成：${task.prompt.take(20)}", reply)
                if (task.repeat) CronScheduler.scheduleNext(this, task)
                else CronStore.remove(this, id) // 单次任务跑完即清
            } catch (e: Exception) {
                notifyTaskResult(this, "● Agent 任务失败：${id.take(8)}", e.message ?: e.javaClass.simpleName)
            } finally {
                stopSelf(startId)
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun runningNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "主动任务执行中", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("言岁AI · 主动任务执行中")
            .setContentText("正在后台执行，完成后会通知你")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_RUNNING = "agent_task_running"
        private const val NOTIF_ID = 2026

        /** 任务结果通知：从 Receiver 搬来，前台服务和旧路径共用 */
        fun notifyTaskResult(context: Context, title: String, text: String) {
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
            val pi = android.app.PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
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
}
