package com.xxt.board.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 学习通 H5 接口的 HTTP 客户端：Cookie 从 Keystore 保险箱取，绝不落盘明文。 */
class ChaoxingHttp(private val vault: CookieVault) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun get(url: String, referer: String = REFERER): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            // 不让服务端压缩，省掉一次解压
            .header("Accept-Encoding", "identity")
            .header("Referer", referer)
            .header("Cookie", vault.load())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} ${url.take(80)}")
            response.body?.string().orEmpty()
        }
    }

    companion object {
        const val MOOC = "https://mooc1-api.chaoxing.com"
        const val WEB = "https://mooc1.chaoxing.com"
        const val REFERER = "$WEB/"
        const val LOGIN_URL =
            "https://passport2.chaoxing.com/login?fid=&newversion=true&refer=https%3A%2F%2Fi.chaoxing.com"
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
