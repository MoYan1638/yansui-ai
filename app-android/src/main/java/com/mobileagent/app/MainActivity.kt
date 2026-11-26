package com.mobileagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private val engine by lazy { AgentEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine.initialize()
        setContent {
            MobileAgentTheme {
                // 未配置模型 → 首启引导；配置完成或已有配置 → 主框架
                var configured by mutableStateOf(engine.config != null)
                if (configured) AppScaffold(engine) else OnboardingScreen(engine) { configured = true }
            }
        }
    }
}
