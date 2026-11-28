package com.mobileagent.core

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * nio 文本读写兼容层：Files.readString/writeString 是 Java 11+ API，
 * Android（API 26 的 android.jar）没有这两个方法，直接用会在运行时 NoSuchMethodError。
 * 全仓库统一走这两个函数，JVM 与 Android 双端安全。
 */
fun Path.readTextCompat(): String = String(Files.readAllBytes(this), Charsets.UTF_8)

/**
 * 原子写：先写同目录 .tmp 再 ATOMIC_MOVE 覆盖。
 * 手机进程随时可能被系统杀（切后台/内存紧张/Doze），直接覆盖写中途被杀会留下半截文件，
 * 下次启动解析失败 → 会话/密钥/配置全丢。原子写保证要么旧文件完整、要么新文件完整。
 */
fun Path.writeTextCompat(text: String) {
    parent?.let { Files.createDirectories(it) }
    // 唯一后缀：避免并发写同一文件时共用 tmp 互相踩踏
    val tmp = resolveSibling("$fileName.${System.nanoTime()}.tmp")
    try {
        Files.write(tmp, text.toByteArray(Charsets.UTF_8))
        try {
            Files.move(tmp, this, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            // 某些文件系统不支持原子 move，退回普通 move（仍比直接覆盖安全）
            Files.move(tmp, this, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        runCatching { Files.deleteIfExists(tmp) }
    }
}

fun Path.appendTextCompat(text: String) {
    parent?.let { Files.createDirectories(it) }
    Files.newBufferedWriter(this, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { it.write(text) }
}
