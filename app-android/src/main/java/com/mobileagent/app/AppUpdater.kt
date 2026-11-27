package com.mobileagent.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内检查更新（自研实现，无第三方依赖）：
 * - 更新源：仓库根目录的 update.json（raw + ghproxy 镜像双通道，与 SkillUpdater 同一套基建，国内可达）
 * - APK 本体：GitHub Releases 资产，下载走系统级 DownloadManager（有系统通知、断点续传、不占应用内存）
 * - 安装：下载完成后拷进应用 cache，经 FileProvider 授权交给系统安装器
 *   （首次安装需用户在系统设置里授予本应用「安装未知应用」）
 */
object AppUpdater {

    data class Release(val version: String, val code: Long, val notes: String, val urls: List<String>)

    private const val UPDATE_RAW =
        "https://raw.githubusercontent.com/MoYan1638/yansui-ai/main/update.json"
    private const val UPDATE_MIRROR =
        "https://ghproxy.net/https://raw.githubusercontent.com/MoYan1638/yansui-ai/main/update.json"

    const val PREF = "app_update"

    /** 当前应用版本名（读包信息，不用硬编码） */
    fun currentVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /** v0.8.5 vs 0.8.6 逐段数字比较，只认「远端更大」；容忍 v 前缀 */
    fun isNewer(remote: String, local: String): Boolean {
        fun parse(v: String) = v.removePrefix("v").trim().split(".").map { it.toIntOrNull() ?: 0 }
        val r = parse(remote)
        val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** 拉取远端 update.json（IO 线程调用）；GitHub 与镜像均失败返回 null */
    fun fetch(): Release? {
        val body = download(UPDATE_RAW) ?: download(UPDATE_MIRROR) ?: return null
        return runCatching {
            val obj = Json.parseToJsonElement(String(body, Charsets.UTF_8)).jsonObject
            val urls = buildList {
                obj["apk"]?.jsonPrimitive?.content?.let { add(it) }
                obj["apk_mirror"]?.jsonPrimitive?.content?.let { add(it) }
            }
            if (urls.isEmpty()) return null
            Release(
                version = obj["version"]?.jsonPrimitive?.content ?: return null,
                code = obj["code"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                notes = obj["notes"]?.jsonPrimitive?.content ?: "",
                urls = urls
            )
        }.getOrNull()
    }

    /** 提交系统下载任务，返回 download id（主线程调用即可） */
    fun enqueueDownload(ctx: Context, release: Release): Long {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val name = "yansui-ai-${release.version}-release.apk"
        val req = DownloadManager.Request(Uri.parse(release.urls.first()))
            .setTitle(name)
            .setDescription("言岁AI v${release.version} 更新包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // 下到应用私有目录（Android/data/包名/files/），微信同款：
            // 文件管理里不留安装包，装完也不用清残留
            .setDestinationInExternalFilesDir(ctx, null, name)
        val id = dm.enqueue(req)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong("download_id", id)
            .putString("download_version", release.version)
            .apply()
        return id
    }

    /** 下载进度（0..100）；任务不存在返回 -1 */
    fun progress(ctx: Context, id: Long): Int = runCatching {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = dm.query(DownloadManager.Query().setFilterById(id))
        if (q.moveToFirst()) {
            val total = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
        } else -1
    }.getOrDefault(-1)

    /** 任务状态：SUCCESSFUL / FAILED / RUNNING / PAUSED / PENDING；任务不存在返回 -1 */
    fun status(ctx: Context, id: Long): Int = runCatching {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = dm.query(DownloadManager.Query().setFilterById(id))
        if (q.moveToFirst()) q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else -1
    }.getOrDefault(-1)

    /** 下载完成后校验并拷进 cache，返回待安装文件；失败返回 null */
    fun prepareInstall(ctx: Context, id: Long): File? = runCatching {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = dm.query(DownloadManager.Query().setFilterById(id))
        if (!q.moveToFirst()) return null
        val status = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        if (status != DownloadManager.STATUS_SUCCESSFUL) return null
        val src = dm.getUriForDownloadedFile(id)
        val out = File(ctx.cacheDir, "update.apk")
        ctx.contentResolver.openInputStream(src)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        out
    }.getOrNull()

    /** 发起系统安装器（需用户已授予「安装未知应用」；未授予时系统会先引导到授权页）；返回是否拉起成功 */
    fun install(ctx: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * 恢复上次未走完的安装流程：查记录的下载任务状态——
     * 下载完成且比当前版本新 → 拷贝出 apk 返回 (版本, 文件)；
     * 已装上或下载失败 → 清理残留（下载记录 + cache 里的 apk）返回 null。
     */
    fun resumeDownload(ctx: Context, currentVer: String): Pair<String, File>? = runCatching {
        val pref = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val id = pref.getLong("download_id", -1L)
        val ver = pref.getString("download_version", null) ?: return null
        if (id <= 0L) return null
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = dm.query(DownloadManager.Query().setFilterById(id))
        if (!q.moveToFirst()) { pref.edit().clear().apply(); return null }
        val status = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        when {
            status == DownloadManager.STATUS_SUCCESSFUL && isNewer(ver, currentVer) ->
                prepareInstall(ctx, id)?.let { ver to it }
            status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED -> {
                // 已经装上（手动装过）或下载失败：清理残留
                pref.edit().clear().apply()
                runCatching { File(ctx.cacheDir, "update.apk").delete() }
                null
            }
            else -> null // 还在下载中，不打扰
        }
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "yansui-app")
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
