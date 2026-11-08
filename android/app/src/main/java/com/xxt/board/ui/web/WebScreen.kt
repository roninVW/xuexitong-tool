package com.xxt.board.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xxt.board.ui.components.AppIcons

private enum class PageState { Loading, Ready, Error }

/**
 * 学习通的 H5 页面在「非官方容器」里打开时，会罩一层全屏 fixed 引导层
 * （"请在 APP 中打开"），所有点击都落在它上面 —— 表现就是「WebView 点不动」。
 *
 * 这里只处理最严格的一类：position fixed/absolute + 铺满屏幕 + z-index 高 +
 * 内部没有可交互元素。只把 pointer-events 关掉，不删节点、不改布局。
 */
private const val DEFUSE_OVERLAY_JS = """
(function(){
  try {
    var w = window.innerWidth, h = window.innerHeight, hit = 0;
    var all = document.querySelectorAll('body *');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var st = window.getComputedStyle(el);
      if (st.position !== 'fixed' && st.position !== 'absolute') continue;
      if (st.pointerEvents === 'none') continue;
      var r = el.getBoundingClientRect();
      if (r.width < w * 0.9 || r.height < h * 0.5) continue;
      if (parseFloat(st.zIndex || '0') < 5) continue;
      if (el.querySelector('input,textarea,select,a,button')) continue;
      el.style.pointerEvents = 'none';
      hit++;
    }
    return 'defused:' + hit;
  } catch (e) { return 'err'; }
})();
"""

/**
 * 应用内作业详情页。
 *
 * 生命周期要点（上一版闪退的几个根因）：
 * 1. WebView 用**普通持有对象**保存，不再写进 `mutableStateOf`——
 *    之前 `factory` 里 `webView = this` 是「组合期写状态」，会触发
 *    "Unsupported concurrent change during composition" 一类的崩溃。
 * 2. `onRelease` 里不直接 `destroy()`：先 `stopLoading + about:blank`，
 *    再 post 到下一帧销毁，避开「WebView 仍附着在窗口时 destroy」的原生崩溃。
 * 3. 处理 `onRenderProcessGone`：渲染进程被杀时返回 true 并切错误态，
 *    否则系统会直接杀掉整个 App（这是 WebView 闪退最常见的原因）。
 * 4. `onReceivedError`（主框架）切错误态，给重试按钮，不再白屏/闪退。
 * 5. 重试走 `key(reloadKey)` 重建，避免复用已损坏的实例。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(
    url: String,
    initialTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    // 普通持有对象：不参与重组，避免组合期写状态
    val holder = remember { WebViewHolder() }
    var pageState by remember { mutableStateOf(PageState.Loading) }
    var title by remember { mutableStateOf(initialTitle) }
    var reloadKey by remember { mutableIntStateOf(0) }

    fun openExternal(target: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
    }

    BackHandler {
        val web = holder.web
        if (web != null && web.canGoBack()) web.goBack() else onBack()
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { "作业详情" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回", tint = scheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { holder.web?.reload() }) {
                        Icon(AppIcons.Sync, contentDescription = "刷新", tint = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { openExternal(holder.web?.url ?: url) }) {
                        Icon(AppIcons.OpenInNew, contentDescription = "用浏览器打开", tint = scheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            key(reloadKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> createWebView(ctx, url, holder, onTitle = { title = it }, onState = { pageState = it }) },
                    onRelease = { web ->
                        // 不要在这里直接 destroy：此刻 View 可能还挂在窗口上。
                        holder.web = null
                        runCatching {
                            web.stopLoading()
                            web.webChromeClient = null
                            web.webViewClient = WebViewClient()
                            web.loadUrl("about:blank")
                            web.removeAllViews()
                        }
                        Handler(Looper.getMainLooper()).post {
                            runCatching { web.destroy() }
                        }
                    },
                )
            }

            if (pageState == PageState.Error) {
                ErrorState(
                    onRetry = {
                        pageState = PageState.Loading
                        reloadKey++
                    },
                    onOpenExternal = { openExternal(url) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = pageState == PageState.Loading,
                enter = fadeIn(tween(90)),
                exit = fadeOut(tween(220)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = scheme.primary)
                }
            }
        }
    }
}

private class WebViewHolder {
    var web: WebView? = null
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    url: String,
    holder: WebViewHolder,
    onTitle: (String) -> Unit,
    onState: (PageState) -> Unit,
): WebView = WebView(context).apply {
    // 与登录页保持一致的配置（登录页触控正常）
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.saveFormData = false
    settings.allowFileAccess = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.setSupportMultipleWindows(false)

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    isFocusable = true
    isFocusableInTouchMode = true
    isClickable = true
    overScrollMode = WebView.OVER_SCROLL_NEVER
    setBackgroundColor(android.graphics.Color.WHITE)

    webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, t: String?) {
            t?.takeIf { it.isNotBlank() }?.let(onTitle)
        }
    }

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
            onState(PageState.Loading)
        }

        override fun onPageFinished(view: WebView?, u: String?) {
            view?.title?.takeIf { it.isNotBlank() }?.let(onTitle)
            onState(PageState.Ready)
            // 去掉「请在 App 中打开」的全屏遮罩，否则整页点不动
            view?.evaluateJavascript(DEFUSE_OVERLAY_JS, null)
            Handler(Looper.getMainLooper()).postDelayed({
                view?.evaluateJavascript(DEFUSE_OVERLAY_JS, null)
            }, 1200)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) onState(PageState.Error)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // 返回 true = 已处理，系统不会杀掉进程
            holder.web = null
            runCatching { view.destroy() }
            onState(PageState.Error)
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host ?: return false
            if (host.endsWith("chaoxing.com")) return false
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            }
            return true
        }
    }

    holder.web = this
    loadUrl(url)
}

@Composable
private fun ErrorState(
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.background(scheme.surface), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = "页面没能加载出来",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
            Text(
                text = "可能是网络问题，或学习通临时拒绝了这次请求。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("重试") }
            TextButton(onClick = onOpenExternal) { Text("用浏览器打开") }
        }
    }
}
