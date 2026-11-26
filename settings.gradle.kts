rootProject.name = "mobile-agent"

pluginManagement {
    repositories {
        google() // AGP
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // M3 app-android 需要
    }
}

include(":core", ":app-android")
