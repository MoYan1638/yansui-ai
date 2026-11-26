package com.mobileagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private val engine by lazy { AgentEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine.initialize()
        setContent {
            // 不再强制首启配置页：直接进主框架，未配置时在会话页引导选模型
            MobileAgentTheme { AppScaffold(engine) }
        }
    }
}
