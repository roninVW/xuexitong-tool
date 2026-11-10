package com.xxt.board.ui.login

import com.xxt.board.ui.components.PaperTopBar
import com.xxt.board.ui.theme.JournalTitle

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import com.xxt.board.ui.components.GaussianWebFrame
import com.xxt.board.ui.components.LocalGlassAlpha
import com.xxt.board.ui.components.glassAlpha
import androidx.compose.ui.viewinterop.AndroidView
import com.xxt.board.data.ChaoxingHttp

private val HOSTS = listOf(
    "https://chaoxing.com",
    "https://i.chaoxing.com",
    "https://mooc1.chaoxing.com",
    "https://mooc1-api.chaoxing.com",
    "https://passport2.chaoxing.com",
    "https://passport2-api.chaoxing.com",
    "https://mobilelearn.chaoxing.com",
)

/** 学习通全站 Cookie 收割：缺一个子域都可能 401 */
fun harvestCookies(): String {
    val map = LinkedHashMap<String, String>()
    val manager = CookieManager.getInstance()
    for (host in HOSTS) {
        val raw = runCatching { manager.getCookie(host) }.getOrNull() ?: continue
        for (part in raw.split(";")) {
            val i = part.indexOf('=')
            if (i > 0) map[part.substring(0, i).trim()] = part.substring(i + 1).trim()
        }
    }
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

/**
 * 登录页：全 M3 外壳（TopAppBar + LinearProgressIndicator + surface），
 * 内容仍是官方登录 WebView —— 只收 Cookie，不碰密码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(blurRadius: Int = 28, onLoggedIn: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var loading by remember { mutableStateOf(true) }
    var handled by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val alpha = LocalGlassAlpha.current ?: glassAlpha(blurRadius)
    var headerHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(),
            update = { frame -> frame.configure(headerHeight, with(density) { blurRadius.dp.toPx() },
                cs.surfaceContainerHigh.copy(alpha = alpha).toArgb()) },
            factory = { context ->
                val frame = GaussianWebFrame(context, cs.surface.toArgb())
                val body = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                }
                body.addView(android.widget.TextView(context).apply {
                    text = "登录成功后自动返回，不保存密码"
                    setTextColor(cs.onSurfaceVariant.toArgb())
                    textSize = 14f
                    setPadding(with(density) { 16.dp.roundToPx() }, with(density) { 10.dp.roundToPx() },
                        with(density) { 16.dp.roundToPx() }, with(density) { 10.dp.roundToPx() })
                })
                val web =                     WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.saveFormData = false
                        settings.allowFileAccess = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView,
                                url: String,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                if (handled) return
                                val cookies = harvestCookies()
                                if (
                                    cookies.contains("_d=") ||
                                    cookies.contains("_uid=") ||
                                    cookies.contains("uf=")
                                ) {
                                    handled = true
                                    onLoggedIn(cookies)
                                }
                            }
                        }
                        loadUrl(ChaoxingHttp.LOGIN_URL)
                    }
                body.addView(web, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                frame.setPageContent(body, web)
                frame
            })
        Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }) {
            PaperTopBar(title = "登录学习通")
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
