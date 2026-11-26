plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mobileagent.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mobileagent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // M1/M2 的引擎：模型适配层 + 有界循环 + SkillLoader，原封复用
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Shizuku（M4 命令行通道）：平台基础设施，运行时组件由用户自行安装，未装不影响其他功能
    // 锁定 12.2.0：newProcess 公开可用（13.1.x 起官方标记私有，推荐的 UserService 对单机 App 过重）
    implementation("dev.rikka.shizuku:api:12.2.0")
    implementation("dev.rikka.shizuku:provider:12.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
