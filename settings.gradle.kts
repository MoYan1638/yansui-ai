rootProject.name = "mobile-agent"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // M3 app-android 需要
    }
}

include(":core")
// M2: include(":skill-loader")
// M3: include(":app-android")
