package com.mobileagent.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * nio 文本读写兼容层：Files.readString/writeString 是 Java 11+ API，
 * Android（API 26 的 android.jar）没有这两个方法，直接用会在运行时 NoSuchMethodError。
 * 全仓库统一走这两个函数，JVM 与 Android 双端安全。
 */
fun Path.readTextCompat(): String = String(Files.readAllBytes(this), Charsets.UTF_8)

fun Path.writeTextCompat(text: String) {
    parent?.let { Files.createDirectories(it) }
        Files.write(this, text.toByteArray(Charsets.UTF_8))
}

fun Path.appendTextCompat(text: String) {
    parent?.let { Files.createDirectories(it) }
    Files.newBufferedWriter(this, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { it.write(text) }
}
