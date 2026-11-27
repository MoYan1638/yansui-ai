plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.4.10"
    application
}

application {
    mainClass.set("com.mobileagent.core.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

// 命令行运行时以仓库根为工作目录（文件工具的沙箱根 = 仓库根），并转发标准输入
tasks.withType<JavaExec>().configureEach {
    workingDir = rootDir
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}
