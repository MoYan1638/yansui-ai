rootProject.name = "mobile-agent"

// 仓库策略：阿里云镜像优先（国内裸网络可构建），google()/mavenCentral 兜底
// 镜像说明：aliyun google 镜像覆盖 AGP/AndroidX/Compose；central 镜像覆盖其余依赖
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google() // 兜底：镜像缺新版本时走官方源
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google() // M3 app-android 需要
        mavenCentral()
    }
}

include(":core", ":app-android")
