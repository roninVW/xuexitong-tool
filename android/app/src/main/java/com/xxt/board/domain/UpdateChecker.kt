package com.xxt.board.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
     * 拉取最新正式 Release。网络、限流、响应格式错误均抛出明确异常，
     * UI 不得把这些失败折叠成“已是最新版本”。
     */
    fun fetchLatestRelease(): Release = try {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "xuexitong-tool-app")
            .build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 ->
                    throw UpdateException("仓库还没有正式发布（Release）")
                response.code == 429 ||
                    (response.code == 403 && response.header("X-RateLimit-Remaining") == "0") ->
                    throw UpdateException("GitHub API 请求过于频繁，请稍后重试（HTTP ${response.code}）")
                !response.isSuccessful ->
                    throw UpdateException("GitHub 请求失败（HTTP ${response.code}）")
            }
            val body = response.body?.string() ?: throw UpdateException("GitHub 返回空响应")
            try {
                parseRelease(JSONObject(body))
            } catch (e: UpdateException) {
                throw e
            } catch (e: Exception) {
                throw UpdateException("GitHub 返回的 Release 数据无法解析", e)
            }
        }
    } catch (e: UpdateException) {
        throw e
    } catch (e: IOException) {
        throw UpdateException("网络连接失败，请检查网络或代理后重试", e)
    }

    private fun parseRelease(obj: JSONObject): Release {
        val tagName = obj.optString("tag_name").trim()
        val htmlUrl = obj.optString("html_url").trim()
        if (tagName.isEmpty() || htmlUrl.isEmpty()) {
            throw UpdateException("Release 缺少版本号或发布页面")
        }
        // 防御性检查：即使用户代理之外的 API 实现误返回预发布，也不作为正式版更新。
        if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) {
            throw UpdateException("最新 Release 不是正式版本")
        }
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
            tagName = tagName,
            title = obj.optString("name", "").ifBlank { tagName },
            changelog = obj.optString("body", "").trim(),
            publishedAt = obj.optString("published_at").takeIf { it.isNotBlank() },
            htmlUrl = htmlUrl,
            assets = assets,
        )
    }

    private val abiNames = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /**
     * 只使用 API 返回的真实 browser_download_url。优先当前设备 ABI，其次通用包；
     * 明确标注为其他 ABI、未签名或未对齐的包不参与选择。
     */
    fun findApkAsset(
        release: Release,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    ): Asset? = release.assets.mapNotNull { asset ->
        val name = asset.name.lowercase()
        if (!name.endsWith(".apk") || asset.downloadUrl.isBlank() ||
            "unsigned" in name || "unaligned" in name
        ) return@mapNotNull null

        val markedAbis = abiNames.filter { abi ->
            val variants = when (abi) {
                "arm64-v8a" -> listOf("arm64-v8a", "arm64_v8a", "arm64")
                "armeabi-v7a" -> listOf("armeabi-v7a", "armeabi_v7a", "armv7")
                else -> listOf(abi, abi.replace('_', '-'))
            }
            variants.any { marker ->
                Regex("(^|[^a-z0-9_])${Regex.escape(marker)}($|[^a-z0-9_])").containsMatchIn(name)
            }
        }
        if (markedAbis.size > 1) return@mapNotNull null
        val markedAbi = markedAbis.singleOrNull()
        val abiIndex = supportedAbis.indexOf(markedAbi)
        if (markedAbi != null && abiIndex < 0) return@mapNotNull null

        val abiScore = when {
            markedAbi != null -> 2_000 - abiIndex
            else -> 1_000 // 无 ABI 标记的 APK 按通用包处理，下载后仍会严格校验。
        }
        val releaseScore = if ("release" in name) 100 else 0
        val debugPenalty = if ("debug" in name) 50 else 0
        Triple(asset, abiScore + releaseScore - debugPenalty, asset.downloadUrl)
    }.sortedWith(
        compareByDescending<Triple<Asset, Int, String>> { it.second }
            .thenBy { it.third }, // 同分时保持稳定，不用文件大小猜测正确包。
    ).firstOrNull()?.first

    // ---------------------------------------------------------------- 版本比较

    private data class SemanticVersion(val core: List<String>, val prerelease: List<String>)

    /** 按 SemVer 2.0 比较；允许省略后续核心段并补 0，忽略构建元数据。 */
    fun compareVersions(current: String, latest: String): Int {
        // 返回值始终以 latest 相对 current 为准：> 0 才表示有更新。
        val a = semanticVersion(latest)
        val b = semanticVersion(current)
        for (i in 0 until maxOf(a.core.size, b.core.size)) {
            val result = compareNumeric(a.core.getOrElse(i) { "0" }, b.core.getOrElse(i) { "0" })
            if (result != 0) return result
        }
        if (a.prerelease.isEmpty() || b.prerelease.isEmpty()) {
            return when {
                a.prerelease.isEmpty() && b.prerelease.isEmpty() -> 0
                a.prerelease.isEmpty() -> 1 // 正式版高于同核心版本的预发布版。
                else -> -1
            }
        }
        for (i in 0 until maxOf(a.prerelease.size, b.prerelease.size)) {
            val x = a.prerelease.getOrNull(i) ?: return -1
            val y = b.prerelease.getOrNull(i) ?: return 1
            val xNumeric = x.all(Char::isDigit)
            val yNumeric = y.all(Char::isDigit)
            val result = when {
                xNumeric && yNumeric -> compareNumeric(x, y)
                xNumeric != yNumeric -> if (xNumeric) -1 else 1
                else -> x.compareTo(y)
            }
            if (result != 0) return result
        }
        return 0
    }

    private fun semanticVersion(version: String): SemanticVersion {
        val normalized = version.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val parts = normalized.split('-', limit = 2)
        val core = parts[0].split('.')
        val prerelease = parts.getOrElse(1) { "" }.split('.').filter { it.isNotEmpty() }
        val valid = core.isNotEmpty() && core.all { it.isNotEmpty() && it.all(Char::isDigit) } &&
            (parts.size == 1 || (prerelease.isNotEmpty() && parts[1].split('.').size == prerelease.size)) &&
            prerelease.all { id -> id.all { it.isLetterOrDigit() || it == '-' } }
        if (!valid) throw UpdateException("无法解析版本号：$version")
        return SemanticVersion(core, prerelease)
    }

    /** 不使用 Int/Long，避免超大版本段溢出；先比有效位数，再比字典序。 */
    private fun compareNumeric(a: String, b: String): Int {
        val x = a.trimStart('0').ifEmpty { "0" }
        val y = b.trimStart('0').ifEmpty { "0" }
        return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
    }

    // ---------------------------------------------------------------- 下载 & 安装

    /**
     * 下载到临时文件，确认响应完整后再改成 APK。协程取消会同时取消 OkHttp 请求，
     * 不会留下可被误安装的半截安装包。
     */
    suspend fun downloadApk(
        context: Context,
        asset: Asset,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = updateDir(context).apply {
            if (!exists() && !mkdirs()) throw UpdateException("无法创建更新包目录")
        }
        val fileName = asset.name.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "update.apk" }
        val target = File(dir, fileName)
        val partial = File(dir, "$fileName.part")
        partial.delete()
        onProgress(0)
        try {
            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("User-Agent", "xuexitong-tool-app")
                .build()
            downloadHttp.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateException("下载失败：GitHub 返回 HTTP ${response.code}")
                }
                val body = response.body ?: throw UpdateException("下载中断：响应为空")
                val responseSize = body.contentLength()
                val expectedSize = asset.sizeBytes.takeIf { it > 0 }
                if (expectedSize != null && responseSize > 0 && expectedSize != responseSize) {
                    throw UpdateException("下载失败：服务器文件大小与 Release 不一致")
                }
                val total = expectedSize ?: responseSize
                var done = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var lastPct = 0
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done * 100L / total).toInt().coerceIn(0, 99)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
                if ((expectedSize != null && done != expectedSize) ||
                    (responseSize > 0 && done != responseSize)
                ) {
                    throw UpdateException("下载不完整，请重试")
                }
            }
            ensureActive()
            if (target.exists() && !target.delete()) {
                throw UpdateException("无法替换旧的更新包")
            }
            if (!partial.renameTo(target)) {
                throw UpdateException("无法保存已下载的更新包")
            }
            onProgress(100)
            target
        } catch (e: kotlinx.coroutines.CancellationException) {
            partial.delete()
            throw e
        } catch (e: UpdateException) {
            partial.delete()
            throw e
        } catch (e: IOException) {
            partial.delete()
            throw UpdateException("下载失败：网络连接中断，请重试", e)
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    /** 校验安装包身份、Release 版本、升级方向和当前应用签名。 */
    @Suppress("DEPRECATION")
    fun validateApk(context: Context, file: File, expectedVersion: String): PackageInfo {
        if (!file.isFile || file.length() == 0L) throw UpdateException("更新包不存在或为空")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException("更新包不是有效的 APK")
        if (archive.packageName != context.packageName) {
            throw UpdateException("更新包包名不匹配：${archive.packageName ?: "未知"}")
        }
        val archiveVersion = archive.versionName
            ?: throw UpdateException("更新包没有版本号")
        if (compareVersions(archiveVersion, expectedVersion) != 0) {
            throw UpdateException("更新包版本 $archiveVersion 与 Release $expectedVersion 不一致")
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        val installedCode = PackageInfoCompat.getLongVersionCode(installed)
        if (archiveCode <= installedCode) {
            throw UpdateException("更新包 versionCode $archiveCode 不大于当前版本 $installedCode")
        }
        val archiveSigners = archive.signerDigests()
        val installedSigners = installed.signerDigests()
        if (archiveSigners.isEmpty() || archiveSigners != installedSigners) {
            throw UpdateException("更新包签名与当前应用不一致")
        }
        return archive
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerDigests(): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        }.orEmpty()
        return certificates.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openUnknownAppSources(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    enum class InstallResult { LAUNCHED, PERMISSION_REQUIRED, NO_INSTALLER }

    /** 校验完成后才能调用；由系统安装器和用户完成最终确认。 */
    fun installApk(context: Context, file: File): InstallResult {
        if (!canRequestPackageInstalls(context)) return InstallResult.PERMISSION_REQUIRED
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            InstallResult.LAUNCHED
        } else {
            InstallResult.NO_INSTALLER
        }
    }
}

/** 检查更新链路上的一切失败，message 直接面向用户。 */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)
