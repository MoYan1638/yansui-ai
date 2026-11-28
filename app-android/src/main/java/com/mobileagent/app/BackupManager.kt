package com.mobileagent.app

import android.content.Context
import android.net.Uri
import com.mobileagent.core.writeTextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份/恢复：把用户数据打成 zip 导出，或从 zip 恢复。
 *
 * 跨设备可用的关键：API Key 用 Android Keystore 加密，密文换机后解不开。
 * 所以导出时把 Key 解密成明文随包走，恢复时用本机 Keystore 重新加密落盘。
 * → 备份文件含明文 Key，UI 必须提示用户妥善保管、不要外传。
 */
object BackupManager {

    private const val MANIFEST = "manifest.json"
    private const val FORMAT = "yansui-backup"
    private const val VERSION = 1

    data class Result(val ok: Boolean, val message: String, val count: Int = 0)

    /** 需要进备份的文件（相对 filesDir）。技能正文不备份——它随 APK 分发 + GitHub 热更新 */
    private fun dataFiles(root: Path): List<Path> {
        val out = mutableListOf<Path>()
        listOf("config.json", "keys.json").forEach {
            val p = root.resolve(it)
            if (Files.exists(p)) out.add(p)
        }
        val dataDir = root.resolve("data")
        if (Files.exists(dataDir)) {
            Files.walk(dataDir).use { s ->
                s.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".tmp") }
                    .forEach { out.add(it) }
            }
        }
        // 技能的用户设置（合规确认等勾选状态）；只备 settings.json，正文不备
        val skillsDir = root.resolve("skills")
        if (Files.exists(skillsDir)) {
            Files.walk(skillsDir).use { s ->
                s.filter { Files.isRegularFile(it) && it.fileName.toString() == "settings.json" }
                    .forEach { out.add(it) }
            }
        }
        return out
    }

    /** 导出到用户通过系统文件选择器（SAF）选定的 uri */
    fun export(ctx: Context, root: Path, uri: Uri): Result = runCatching {
        val files = dataFiles(root)
        val names = mutableListOf<String>()
        val os = ctx.contentResolver.openOutputStream(uri)
            ?: return Result(false, "无法写入所选文件")
        os.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                files.forEach { f ->
                    val rel = root.relativize(f).toString().replace('\\', '/')
                    val text = String(Files.readAllBytes(f), Charsets.UTF_8)
                    // 明文 Key 随包走，换机/重装后恢复才能继续用
                    val plain = when (rel) {
                        "config.json" -> transformKey(text, decrypt = true)
                        "keys.json" -> transformKeys(text, decrypt = true)
                        else -> text
                    }
                    zip.putNextEntry(ZipEntry(rel))
                    zip.write(plain.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    names.add(rel)
                }
                val manifest = buildJsonObject {
                    put("format", JsonPrimitive(FORMAT))
                    put("version", JsonPrimitive(VERSION))
                    put("time", JsonPrimitive(System.currentTimeMillis()))
                    put("files", JsonArray(names.map { JsonPrimitive(it) }))
                }.toString()
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        Result(true, "已导出 ${files.size} 项数据", files.size)
    }.getOrDefault(Result(false, "导出失败，请重试"))

    /** 从 zip 恢复；覆盖式，调用方须先二次确认 */
    fun import(ctx: Context, root: Path, uri: Uri): Result = runCatching {
        val entries = mutableMapOf<String, String>()
        val ins = ctx.contentResolver.openInputStream(uri)
            ?: return Result(false, "无法读取所选文件")
        ins.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var e: ZipEntry?
                while (zip.nextEntry.also { e = it } != null) {
                    val entry = e ?: continue
                    if (entry.isDirectory) continue
                    // 防 Zip Slip：归一化后不得越界到上层或绝对路径
                    val name = java.nio.file.Paths.get(entry.name).normalize().toString().replace('\\', '/')
                    if (name.startsWith("..") || name.startsWith("/")) continue
                    entries[name] = String(zip.readBytes(), Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
        }
        val manifestText = entries[MANIFEST] ?: return Result(false, "不是言岁AI的备份文件")
        val manifest = runCatching { Json.parseToJsonElement(manifestText).jsonObject }.getOrNull()
            ?: return Result(false, "备份清单已损坏")
        if (manifest["format"]?.jsonPrimitive?.contentOrNull != FORMAT)
            return Result(false, "不是言岁AI的备份文件")
        val version = manifest["version"]?.jsonPrimitive?.intOrNull ?: 0
        if (version > VERSION) return Result(false, "备份来自更新版本，请先升级应用")

        var n = 0
        entries.forEach { (name, text) ->
            if (name == MANIFEST) return@forEach
            val out = when (name) {
                "config.json" -> transformKey(text, decrypt = false)
                "keys.json" -> transformKeys(text, decrypt = false)
                else -> text
            }
            val p = root.resolve(name)
            p.parent?.let { Files.createDirectories(it) }
            p.writeTextCompat(out) // 原子写：进程被杀不留半截文件
            n++
        }
        Result(true, "已恢复 $n 项数据", n)
    }.getOrDefault(Result(false, "恢复失败，备份文件可能损坏"))

    /** 单 Key 字段加/解密 */
    private fun transformKey(text: String, decrypt: Boolean): String = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val cur = o["apiKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val next = if (decrypt) (ApiKeyVault.decrypt(cur) ?: cur)
                   else (ApiKeyVault.encrypt(cur) ?: cur)
        JsonObject(o.toMutableMap().apply { put("apiKey", JsonPrimitive(next)) }).toString()
    }.getOrDefault(text)

    /** keys.json 全量 value 加/解密 */
    private fun transformKeys(text: String, decrypt: Boolean): String = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val mapped = JsonObject(o.mapValues { (_, v) ->
            val cur = v.jsonPrimitive.contentOrNull.orEmpty()
            val next = if (decrypt) (ApiKeyVault.decrypt(cur) ?: cur)
                       else (ApiKeyVault.encrypt(cur) ?: cur)
            JsonPrimitive(next)
        })
        mapped.toString()
    }.getOrDefault(text)
}
