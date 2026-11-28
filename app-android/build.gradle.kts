plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mobileagent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yansui.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 70
        versionName = "0.8.34"
    }

    // 正式签名：密钥与密码在 signing/signing.properties（不入库，本地保管；丢了就无法再升级）
    val signingProps: Map<String, String> = rootProject.file("signing/signing.properties").let { f ->
        if (f.exists()) f.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
        }.toMap() else emptyMap()
    }
    signingConfigs {
        create("release") {
            if (signingProps.isNotEmpty()) {
                storeFile = rootProject.file(signingProps["storeFile"]!!)
                storePassword = signingProps["storePassword"]
                keyAlias = signingProps["keyAlias"]
                keyPassword = signingProps["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // M1/M2 的引擎：模型适配层 + 有界循环 + SkillLoader，原封复用
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // 液态玻璃：真实背景模糊 + 折射（API 33+ 生效，低版本自动回退）
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Shizuku（M4 命令行通道）：平台基础设施，运行时组件由用户自行安装，未装不影响其他功能
    // 锁定 12.2.0：newProcess 公开可用（13.1.x 起官方标记私有，推荐的 UserService 对单机 App 过重）
    implementation("dev.rikka.shizuku:api:12.2.0")
    implementation("dev.rikka.shizuku:provider:12.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
