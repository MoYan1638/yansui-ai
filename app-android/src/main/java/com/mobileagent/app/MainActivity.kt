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
        CronScheduler.scheduleAll(this) // 重启/重排主动任务（Octop restart-safe 思想的手机端实现）
        setContent {
            MobileAgentTheme {
                // 启动短动画：盖在最上层，播完淡出交棒（底层内容提前组合好，无白屏闪烁）
                var showSplash by remember { mutableStateOf(true) }
                // 无配置 → 四步引导（原型 onboarding）；已有配置 → 直接主框架
                var configured by mutableStateOf(engine.config != null)
                androidx.compose.foundation.layout.                Box {
                    if (configured) AppScaffold(engine) else OnboardingScreen(engine) { configured = true }
                    if (showSplash) SplashOverlay(onFinished = { showSplash = false })
                }
            }
        }
    }
}
