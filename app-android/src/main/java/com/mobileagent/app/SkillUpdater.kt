package com.mobileagent.app

import com.mobileagent.core.readTextCompat
import com.mobileagent.core.writeTextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 技能热更新（只跟踪内置的 love-companion，自研实现，无第三方依赖）：
 * - 仓库端声明文件 skills/love-companion/update.json：
 *   {"version":"x.y.z","files":["SKILL.md","gate.json","references/xx.md",...]}
 * - 本地标记文件 skills/love-companion/.hotupdate.json（启动提取 assets 时，存在即跳过该技能，
 *   本地热更新版优先于 APK 内置版）
 * - 拉取源：GitHub raw 优先，失败自动切镜像；任一源可用即可完成更新
 */
object SkillUpdater {

    const val SKILL = "love-companion"
    private const val BASE_RAW = "https://raw.githubusercontent.com/MoYan1638/mobile-agent/main/skills/$SKILL"
    private const val BASE_MIRROR = "https://ghproxy.net/https://raw.githubusercontent.com/MoYan1638/mobile-agent/main/skills/$SKILL"

    /** 本地标记：记录已更新到的版本与文件清单（用于清理旧版多余文件） */
    private data class LocalState(val version: String, val files: List<String>)

    data class Manifest(val version: String, val files: List<String>)

    data class Status(val local: String, val remote: String, val hasUpdate: Boolean)

    private val markerName = ".hotupdate.json"

    // ---- 对 UI / 引擎暴露的能力 ----

    /** 当前本地版本：热更新过 → 标记文件里的版本；否则 APK 内置（builtin） */
    fun localVersion(skillsDir: Path): String {
        val state = readLocalState(skillsDir) ?: return "builtin"
        return state.version
    }

    /** 拉取云端清单并与本地比对（IO 线程调用） */
    fun check(skillsDir: Path): Status {
        val manifest = fetchManifest()
            ?: throw IOException("更新源不可达（GitHub 与镜像均失败）")
        val local = localVersion(skillsDir)
        return Status(local, manifest.version, manifest.version != local)
    }

    /**
     * 应用更新：逐文件下载替换 → 清理旧版多余文件 → 写标记 → 返回新版本
     * （IO 线程调用；成功后由调用方 refreshSkills() 立即生效）
     */
    fun apply(skillsDir: Path): String {
        val manifest = fetchManifest() ?: throw IOException("更新源不可达（GitHub 与镜像均失败）")
        val skillDir = skillsDir.resolve(SKILL)
        val old = readLocalState(skillsDir)?.files?.toSet() ?: emptySet()
        val newFiles = manifest.files.toSet()

        for (rel in manifest.files) {
            if (!isSafeRel(rel)) throw IOException("清单含非法路径: $rel")
            val bytes = download("$BASE_RAW/$rel") ?: download("$BASE_MIRROR/$rel")
                ?: throw IOException("下载失败: $rel")
            val target = skillDir.resolve(rel)
            target.parent?.let { Files.createDirectories(it) }
            Files.write(target, bytes)
        }
        // 旧版有、新版没有的文件：删除，避免残留指令误导模型
        (old - newFiles).forEach { rel ->
            if (isSafeRel(rel)) runCatching { Files.deleteIfExists(skillDir.resolve(rel)) }
        }
        skillDir.resolve(markerName).writeTextCompat(
            JsonObject(mapOf(
                "version" to JsonPrimitive(manifest.version),
                "files" to JsonArray(manifest.files.map { JsonPrimitive(it) })
            )).toString()
        )
        return manifest.version
    }

    // ---- 内部实现 ----

    private fun readLocalState(skillsDir: Path): LocalState? = runCatching {
        val marker = skillsDir.resolve(SKILL).resolve(markerName)
        if (!Files.exists(marker)) return null
        val obj = Json.parseToJsonElement(marker.readTextCompat()).jsonObject
        LocalState(
            version = obj["version"]?.jsonPrimitive?.content ?: return null,
            files = obj["files"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        )
    }.getOrNull()

    private fun fetchManifest(): Manifest? {
        val body = download("$BASE_RAW/update.json") ?: download("$BASE_MIRROR/update.json") ?: return null
        return runCatching {
            val obj = Json.parseToJsonElement(String(body, Charsets.UTF_8)).jsonObject
            Manifest(
                version = obj["version"]?.jsonPrimitive?.content ?: return null,
                files = obj["files"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null
            )
        }.getOrNull()
    }

    /** HttpURLConnection（Android 全版本可用，不用 java.net.http）；30s 连接 / 15s 读取 */
    private fun download(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "yansui-app")
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** 只允许技能包内的相对路径，防目录穿越 */
    private fun isSafeRel(rel: String): Boolean =
        rel.isNotBlank() && !rel.contains("..") && !rel.startsWith("/") && !rel.contains("\\") &&
            (rel == "SKILL.md" || rel == "gate.json" || rel.startsWith("references/"))
}
