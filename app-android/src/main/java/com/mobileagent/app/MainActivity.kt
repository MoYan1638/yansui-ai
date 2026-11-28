package com.mobileagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private val engine by lazy { AgentEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine.initialize()
        // 启动即清理历史遗留的下载任务与「下载完成」通知：点通知会重开安装器，
        // 必须在主界面（不进设置页）也生效，否则「更新好了还一直弹安装」
        AppUpdater.clearFinishedTasks(this, AppUpdater.currentVersion(this))
        AppUpdater.clearStaleApk(this)
        CronScheduler.scheduleAll(this) // 重启/重排主动任务（Octop restart-safe 思想的手机端实现）
        setContent {
            MobileAgentTheme {
                // 启动短动画：盖在最上层，播完淡出交棒（底层内容提前组合好，无白屏闪烁）
                var showSplash by remember { mutableStateOf(true) }
                // 无配置 → 四步引导（原型 onboarding）；已有配置 → 直接主框架
                var configured by remember { mutableStateOf(engine.config != null) }
                androidx.compose.foundation.layout.                Box {
                    if (configured) AppScaffold(engine) else OnboardingScreen(engine) { configured = true }
                    if (showSplash) SplashOverlay(onFinished = { showSplash = false })
                }
            }
        }
    }
}
