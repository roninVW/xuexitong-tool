package com.xxt.board.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases 检查更新（复用项目现有的 OkHttp，零新增依赖）。
 *
 * 流程：`releases/latest`（GitHub 只返回最新正式版，draft / prerelease 自动排除）
 * → 解析版本号 / 更新日志 / 发布时间 / 资产 → [compareVersions] 判断是否更新
 * → 下载 Assets 里的 APK（IO 线程，带进度）→ FileProvider + 系统安装界面，
 * 由用户确认安装；没有 APK 资产时给出 Release 网页入口。
 *
 * 所有网络方法都必须在 IO 线程调用（UI 层负责 `Dispatchers.IO`）。
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/roninVW/xuexitong-tool/releases/latest"

    /** 项目统一网络栈：OkHttp（与 SyncEngine 同库） */
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val downloadHttp: OkHttpClient = http.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 更新包下载目录（应用外部私有目录，卸载时随应用清除） */
    fun updateDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.cacheDir, "update")

    // ---------------------------------------------------------------- 模型

    data class Asset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    data class Release(
        val tagName: String,
        val title: String,
        val changelog: String,
        val publishedAt: String?,
        val htmlUrl: String,
        val assets: List<Asset>,
    ) {
        /** 版本号（去掉 tag 前缀 v/V） */
        val version: String get() = tagName.removePrefix("v").removePrefix("V")

        /** 发布时间格式化为 yyyy-MM-dd（解析失败返回原文） */
        val publishedText: String?
            get() = publishedAt?.let {
                runCatching {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
                        .format(Instant.parse(it))
                }.getOrNull() ?: it
            }
    }

    // ---------------------------------------------------------------- API

    /**
     * 拉取最新正式 Release。失败（网络 / 限流 / 无 Release）抛 [UpdateException]，
     * UI 层负责提示，不 here 崩溃。
     */
    fun fetchLatestRelease(): Release {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "xuexitong-tool-app")
            .build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 ->
                    throw UpdateException("仓库还没有正式发布（Release）")
                !response.isSuccessful ->
                    throw UpdateException("GitHub 返回 ${response.code}")
            }
            val body = response.body?.string() ?: throw UpdateException("GitHub 返回空响应")
            return parseRelease(JSONObject(body))
        }
    }

    private fun parseRelease(obj: JSONObject): Release {
        val assetsJson = obj.optJSONArray("assets") ?: JSONArray()
        val assets = buildList {
            for (i in 0 until assetsJson.length()) {
                val a = assetsJson.getJSONObject(i)
                add(
                    Asset(
                        name = a.optString("name"),
                        downloadUrl = a.optString("browser_download_url"),
                        sizeBytes = a.optLong("size", 0L),
                    )
                )
            }
        }
        return Release(
            tagName = obj.optString("tag_name"),
            title = obj.optString("name", "").ifBlank { obj.optString("tag_name") },
            changelog = obj.optString("body", "").trim(),
            publishedAt = obj.optString("published_at").takeIf { it.isNotBlank() },
            htmlUrl = obj.optString("html_url"),
            assets = assets,
        )
    }

    /** 从 Assets 里挑 APK：优先 release / arm64 通用包，排除明显不是安装包的命名 */
    fun findApkAsset(release: Release): Asset? =
        release.assets
            .filter { it.name.lowercase().endsWith(".apk") }
            .sortedWith(
                compareByDescending<Asset> { it.name.lowercase().contains("release") }
                    .thenByDescending { it.name.lowercase().contains("arm64") }
                    .thenByDescending { it.sizeBytes }
            )
            .firstOrNull()

    // ---------------------------------------------------------------- 版本比较

    /**
     * 语义化比较版本号：逐段按数字比较（0.40.10 > 0.40.9），段数不足补 0，
     * 前缀 v/V 与后缀（如 -beta）忽略。返回 >0 表示 [latest] 更新。
     */
    fun compareVersions(current: String, latest: String): Int {
        val a = segment(current)
        val b = segment(latest)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** "v0.40.1-beta" → [0, 40, 1]（非数字尾巴丢弃；解析失败的段按 0 处理） */
    private fun segment(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).takeIf { s -> s.isNotEmpty() }?.toIntOrNull() ?: 0 }

    // ---------------------------------------------------------------- 下载 & 安装

    /**
     * 下载 APK 到 [updateDir]，[onProgress] 回调 0..100（仍在 IO 线程回调）。
     * 返回下载完成的文件；失败抛 [UpdateException]。
     */
    fun downloadApk(context: Context, asset: Asset, onProgress: (Int) -> Unit): File {
        val dir = updateDir(context).apply { mkdirs() }
        // 先清掉旧包，避免同目录越积越多
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, asset.name.substringAfterLast('/').ifBlank { "update.apk" })

        val request = Request.Builder()
            .url(asset.downloadUrl)
            .header("User-Agent", "xuexitong-tool-app")
            .build()
        downloadHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException("下载失败：GitHub 返回 ${response.code}")
            }
            val total = (response.body?.contentLength() ?: -1L).takeIf { it > 0 }
                ?: asset.sizeBytes
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = (done * 100L / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            } ?: throw UpdateException("下载中断：响应为空")
        }
        return target
    }

    /**
     * 调起系统安装界面（用户确认，不做任何静默安装）。
     * 未授予「安装未知应用」时系统会自行引导；连安装器都拉不起来时，
     * 跳到本应用的「安装未知应用」设置页。
     *
     * @return true = 已拉起安装界面
     */
    fun installApk(context: Context, file: File): Boolean {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            // 系统没有可用的安装器（极端 ROM）：引导用户开「安装未知应用」
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        return launched
    }
}

/** 检查更新链路上的一切失败，message 直接面向用户 */
class UpdateException(message: String) : Exception(message)
