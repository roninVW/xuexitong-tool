package com.xxt.board.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.xxt.board.data.CookieVault
import com.xxt.board.data.Parsers
import com.xxt.board.injectChaoxingCookies
import com.xxt.board.ui.components.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale

private const val TAG = "XXTWeb"

private enum class PageState { Loading, Ready, Error }

/**
 * 学习通自己的 CXJSBridge.js 在 device != android 时会退回 `jsbridge://` iframe 协议，
 * 纯 WebView 里没人接收，页面上的按钮就「点了没反应」。加载完必须把 device 改成 android，
 * 它才会走 `androidjsbridge.postNotification(...)` 这条原生通道。
 *
 * 但只 `setDevice` 还不够：有的页面在解析阶段就把 window.jsBridge 建好了、有的根本没有这个对象，
 * 所以这里直接把整座桥补出来（含 window.open），保证「查看作业 / 开始答题 / 提交」有出口。
 */
private const val BRIDGE_SHIM_JS = """
(function(){
  function send(name, payload){
    try{
      var body = payload;
      if (body === undefined || body === null) body = '{}';
      if (typeof body !== 'string') body = JSON.stringify(body);
      androidjsbridge.postNotification(name, String(body));
      return 'sent';
    }catch(e){ return 'err:' + e; }
  }
  var api = {
    setDevice: function(){ return 'android'; },
    getDevice: function(){ return 'android'; },
    postNotification: send,
    callHandler: send,
    call: send,
    invoke: send,
    isAndroid: true
  };
  try{
    if (!window.jsBridge) window.jsBridge = api;
    else{
      try{ if (typeof window.jsBridge.setDevice === 'function') window.jsBridge.setDevice('android'); }catch(e){}
      ['postNotification','callHandler','call','invoke'].forEach(function(k){
        if (typeof window.jsBridge[k] !== 'function') window.jsBridge[k] = send;
      });
    }
    if (!window.android) window.android = api;
    if (typeof window.androidPostNotification !== 'function'){
      window.androidPostNotification = function(n, p){ return send(n, p); };
    }
    // 新窗口一律交给原生：WebView 里 window.open 被拦，表现就是「点了没反应」
    var rawOpen = window.open;
    window.open = function(u){
      if (u){ send('CLIENT_OPEN_URL', { webUrl: String(u), url: String(u) }); return null; }
      return rawOpen ? rawOpen.apply(window, arguments) : null;
    };
    return 'android';
  }catch(e){ return 'err:' + e; }
})();
"""

/**
 * 兜底：极少数页面会罩一层「请在 APP 中打开」的全屏 fixed 遮罩，所有点击都落在它上面。
 * 只把 pointer-events 关掉，不删节点、不改布局。
 */
private const val DEFUSE_OVERLAY_JS = """
(function(){
  try{
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

/** 不带 `; wv` 的浏览器 UA：学习通按 `wv` 标记判定「非浏览器容器」，会砍掉功能或罩引导层 */
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * 应用内作业 / 考试页。
 *
 * 关键点（不实现就会「进得去、点不动」）：
 * 1. **默认打开原地址**（答题前一个界面）。列表给的 `/work/phone/task-work` 是详情入口页，
 *    从那里点「开始答题」即可；若调用方传了 [resolve]，才在加载前把地址换成答题页。
 * 2. **jsBridge**：学习通的「继续答题 / 查看详情 / 修改答案 / 提交」是 jsBridge 调用，
 *    不是普通链接。必须实现 `androidjsbridge.postNotification`，并把整座桥补出来
 *    （`window.jsBridge` / `window.open`），否则按钮静默无反应。
 * 3. **多窗口**：部分入口走 `window.open`，必须 `setSupportMultipleWindows(true)`
 *    并接管 `onCreateWindow`，否则新页面永远不出现。
 * 4. **JS 对话框**：提交前的 `confirm()` 如果不接管，看起来就是「点了没反应」。
 * 5. **文件选择**：带附件的作业要能选文件，否则停在答题页交不上去。
 * 6. UA 去掉 `wv` + 放开混合内容 + 先灌 Cookie，否则白屏或跳登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(
    url: String,
    initialTitle: String,
    resolve: suspend (String) -> String = { it },
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val holder = remember { WebHolder() }
    var pageState by remember { mutableStateOf(PageState.Loading) }
    var title by remember { mutableStateOf(initialTitle) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var jsRequest by remember { mutableStateOf<JsRequest?>(null) }
    var jsInput by remember { mutableStateOf("") }
    var candidates by remember(url) { mutableStateOf<List<String>>(emptyList()) }
    var candidateIndex by remember(url) { mutableIntStateOf(0) }

    // 文件选择：作业附件
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = holder.fileCallback
        holder.fileCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode != android.app.Activity.RESULT_OK || data == null -> null
            data.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    fun openExternal(target: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
    }

    BackHandler {
        val web = holder.web
        if (web != null && web.canGoBack()) web.goBack() else onBack()
    }

    // 入口页 → 答题页：先解析再加载，避免先闪一下入口页再跳走。
    // 解析失败（Cookie 过期 / 改版 / 断网）就退回入口页，不影响打开。
    LaunchedEffect(reloadKey) {
        var web = holder.web
        var guard = 0
        while (web == null && guard < 50) {
            delay(20)
            web = holder.web
            guard++
        }
        pageState = PageState.Loading
        val resolved = runCatching { withTimeoutOrNull(6000) { resolve(url) } }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: url
        // 默认（resolve 恒等）直接用列表给的地址，也就是「答题前一个界面」；
        // 若调用方传了解析函数，则优先用解析出的地址，再退到原地址。
        val list = listOf(resolved, Parsers.toWebHost(resolved), url)
            .filter { it.isNotBlank() }
            .distinct()
        candidates = list
        candidateIndex = 0
        Log.d(TAG, "resolve: $url -> ${list.joinToString(" | ")}")
        holder.load?.invoke(list.first())
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
                    factory = { ctx ->
                        createWebView(
                            context = ctx,
                            holder = holder,
                            onTitle = { title = it },
                            onState = { pageState = it },
                            onJsRequest = { jsRequest = it; jsInput = it.defaultText },
                            onToast = { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            // 正文为空（域名不对 / 未登录 / 改版）时，换下一个候选地址再试
                            onBlank = {
                                val next = if (candidateIndex < candidates.lastIndex) {
                                    candidates[candidateIndex + 1]
                                } else {
                                    null
                                }
                                if (next != null) candidateIndex++ else Unit
                                Log.d(TAG, "blank page -> next: $next")
                                next
                            },
                            onPickFile = { callback, params ->
                                holder.fileCallback = callback
                                val intent = runCatching { params?.createIntent() }.getOrNull()
                                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "*/*"
                                    }
                                runCatching { filePicker.launch(intent) }.onFailure {
                                    holder.fileCallback = null
                                    callback.onReceiveValue(null)
                                }
                            },
                            onExternal = { openExternal(it) },
                        )
                    },
                    onRelease = { web ->
                        // 不要在这里直接 destroy：此刻 View 可能还挂在窗口上。
                        holder.web = null
                        holder.destroyPopup()
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

    // JS 对话框：alert / confirm / prompt 必须回给页面，否则页面卡在等待上
    jsRequest?.let { req ->
        AlertDialog(
            onDismissRequest = {
                runCatching { req.result.cancel() }
                jsRequest = null
            },
            title = { Text(if (req.isPrompt) "输入" else "提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(req.message.ifBlank { "—" })
                    if (req.isPrompt) {
                        OutlinedTextField(
                            value = jsInput,
                            onValueChange = { jsInput = it },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (req.isPrompt) (req.result as? JsPromptResult)?.confirm(jsInput)
                        else req.result.confirm()
                    }
                    jsRequest = null
                }) { Text("确定") }
            },
            dismissButton = if (req.isPrompt || req.hasCancel) {
                {
                    TextButton(onClick = {
                        runCatching { req.result.cancel() }
                        jsRequest = null
                    }) { Text("取消") }
                }
            } else null,
        )
    }
}

// ---------------------------------------------------------------- WebView

private class WebHolder {
    var web: WebView? = null
    var popup: WebView? = null
    var fileCallback: ValueCallback<Array<Uri>>? = null

    /** createWebView 注入：由 Composable 决定第一次加载哪个地址（先解析答题页） */
    var load: ((String) -> Unit)? = null

    fun destroyPopup() {
        popup?.let { p ->
            runCatching {
                (p.parent as? android.view.ViewGroup)?.removeView(p)
                p.destroy()
            }
        }
        popup = null
    }
}

private class JsRequest(
    val message: String,
    val defaultText: String,
    val result: JsResult,
    val isPrompt: Boolean,
    val hasCancel: Boolean,
)

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    holder: WebHolder,
    onTitle: (String) -> Unit,
    onState: (PageState) -> Unit,
    onJsRequest: (JsRequest) -> Unit,
    onPickFile: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Unit,
    onExternal: (String) -> Unit,
    onToast: (String) -> Unit,
    onBlank: () -> String?,
): WebView {
    // 登录态：应用内请求走自己的 Cookie 头，WebView 用系统 CookieManager，两边不同步
    runCatching { injectChaoxingCookies(CookieVault(context).load()) }

    var currentUrl = ""
    var triedFallback = false

    val web = WebView(context)

    fun load(target: String, referer: String? = null) {
        val cleaned = cleanUrl(target)
        if (cleaned.isBlank()) return
        currentUrl = cleaned
        Log.d(TAG, "load: $cleaned  referer=$referer")
        onState(PageState.Loading)
        val headers = if (referer.isNullOrBlank()) emptyMap() else mapOf("Referer" to referer)
        runCatching { web.loadUrl(cleaned, headers) }
            .onFailure { onState(PageState.Error) }
    }

    fun attachBridge() {
        web.evaluateJavascript(BRIDGE_SHIM_JS) { r ->
            Log.d(TAG, "bridge shim -> $r @ ${web.url}")
        }
        web.evaluateJavascript(DEFUSE_OVERLAY_JS, null)
    }

    /** 页面里给的地址：站内就就地打开，站外交给系统 */
    fun bridgeTarget(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val absolute = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "https://mooc1-api.chaoxing.com$value"
            else -> return null
        }
        val host = Uri.parse(absolute).host.orEmpty().lowercase(Locale.ROOT)
        return if (host == "chaoxing.com" || host.endsWith(".chaoxing.com")) absolute else null
    }

    /**
     * jsBridge 通知统一入口。学习通的按钮名不止一个（新旧页面不同），
     * 只认 CLIENT_OPEN_URL 会漏掉一半入口，表现就是「有的按钮能点、有的点了没反应」。
     */
    fun handleBridge(name: String, body: String) {
        when (name) {
            "CLIENT_OPEN_URL", "CLIENT_OPEN_NEW_URL", "CLIENT_OPEN_BROWSER", "CLIENT_OPEN_PAGE" -> {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val raw = listOf("webUrl", "url", "targetUrl", "link", "path")
                    .firstNotNullOfOrNull { key -> json?.optString(key)?.takeIf { it.isNotBlank() } }
                val target = bridgeTarget(raw)
                when {
                    target != null -> load(target, referer = web.url ?: currentUrl)
                    raw != null && raw.startsWith("http") -> onExternal(raw)
                    else -> Log.d(TAG, "bridge open 跳过（不是地址）: ${raw.orEmpty().take(80)}")
                }
            }

            "CLIENT_REFRESH_EVENT", "CLIENT_RELOAD" -> web.reload()

            // 提交完 / 关闭弹层：能退就退，退不动就留在当前页
            "CLIENT_CLOSE_PAGE", "CLIENT_CLOSE", "CLIENT_GO_BACK" -> {
                if (web.canGoBack()) web.goBack()
            }

            "CLIENT_SHOW_TOAST" -> {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                if (!msg.isNullOrBlank()) onToast(msg)
            }

            else -> Log.d(TAG, "bridge 未处理: $name")
        }
    }

    // 页面 jsBridge → 原生：学习通所有「继续答题 / 修改答案 / 提交」都走这里
    web.addJavascriptInterface(
        object {
            @JavascriptInterface
            fun postNotification(name: String?, payload: String?) {
                if (name.isNullOrEmpty()) return
                val body = payload.orEmpty()
                Log.d(TAG, "jsBridge: $name ${body.take(240)}")
                web.post { handleBridge(name, body) }
            }
        },
        "androidjsbridge",
    )

    with(web.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        saveFormData = false
        allowFileAccess = false
        allowContentAccess = true
        useWideViewPort = true
        loadWithOverviewMode = true
        javaScriptCanOpenWindowsAutomatically = true
        mediaPlaybackRequiresUserGesture = false
        // 作业页会 window.open 出新窗口，必须允许并接管，否则新页面永远打不开
        setSupportMultipleWindows(true)
        // 部分课件 / 附件是 http 资源，不放开会被静默拦掉
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = BROWSER_UA
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }

    // 触控：外层是 Compose，WebView 必须自己拿到焦点与点击，否则滚动/输入会被吞
    web.isFocusable = true
    web.isFocusableInTouchMode = true
    web.isClickable = true
    web.overScrollMode = WebView.OVER_SCROLL_NEVER
    web.setBackgroundColor(android.graphics.Color.WHITE)

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

    web.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
            onState(PageState.Loading)
        }

        override fun onPageFinished(view: WebView?, u: String?) {
            view?.title?.takeIf { it.isNotBlank() }?.let(onTitle)
            onState(PageState.Ready)
            attachBridge()
            // 单页应用可能晚一步替换 window.jsBridge，补两次
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { attachBridge() } }, 600)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { attachBridge() } }, 1800)

            // 兜底：加载完正文仍为空（域名不对 / 未登录 / 改版），换下一个候选地址再试
            view?.evaluateJavascript(
                "(function(){var b=document.body;return b?b.innerText.trim().length:0})()",
            ) { len ->
                val textLen = len?.trim('"')?.toIntOrNull() ?: 0
                if (textLen >= 40 || triedFallback) return@evaluateJavascript
                val next = onBlank() ?: return@evaluateJavascript
                triedFallback = true
                Log.d(TAG, "正文过短($textLen)，换地址重试: $next")
                load(next, referer = "https://mooc1.chaoxing.com")
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            Log.d(TAG, "onReceivedError: ${error.errorCode} ${request.url}")
            onState(PageState.Error)
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            if (request?.isForMainFrame != true) return
            val code = errorResponse?.statusCode ?: 0
            if (code >= 400) {
                Log.d(TAG, "onReceivedHttpError: $code ${request.url}")
                onState(PageState.Error)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            holder.web = null
            runCatching { view.destroy() }
            onState(PageState.Error)
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
            when (scheme) {
                "http", "https" -> {
                    val host = uri.host.orEmpty()
                    if (host.endsWith("chaoxing.com")) return false
                    onExternal(uri.toString())
                    return true
                }
                // 页面用 location / iframe 走壳协议时，把里面的目标地址接过来，
                // 否则这一下点击就落到「交给系统打开」上，在学习通里根本没有对应入口。
                "jsbridge", "chaoxing", "cxstudy" -> {
                    val target = bridgedAddress(uri)
                    if (target != null) {
                        Log.d(TAG, "壳协议 -> $target")
                        load(target, referer = web.url ?: currentUrl)
                    } else {
                        Log.d(TAG, "壳协议无地址: $uri")
                    }
                    return true
                }
                else -> {
                    onExternal(uri.toString())
                    return true
                }
            }
        }
    }

    web.webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, t: String?) {
            t?.takeIf { it.isNotBlank() }?.let(onTitle)
        }

        override fun onJsAlert(view: WebView?, u: String?, message: String?, result: JsResult?): Boolean {
            if (result == null) return false
            onJsRequest(JsRequest(message.orEmpty(), "", result, isPrompt = false, hasCancel = false))
            return true
        }

        override fun onJsConfirm(view: WebView?, u: String?, message: String?, result: JsResult?): Boolean {
            if (result == null) return false
            onJsRequest(JsRequest(message.orEmpty(), "", result, isPrompt = false, hasCancel = true))
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            u: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?,
        ): Boolean {
            if (result == null) return false
            onJsRequest(
                JsRequest(message.orEmpty(), defaultValue.orEmpty(), result, isPrompt = true, hasCancel = true),
            )
            return true
        }

        override fun onShowFileChooser(
            view: WebView?,
            callback: ValueCallback<Array<Uri>>?,
            params: FileChooserParams?,
        ): Boolean {
            if (callback == null) return false
            onPickFile(callback, params)
            return true
        }

        /**
         * window.open / target=_blank：用一个临时 WebView 接住 transport，
         * 拿到它要去的第一个地址就销毁，然后在本 WebView 里正常打开。
         */
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(context)
            holder.popup = popup
            popup.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = BROWSER_UA
            }
            var handedOff = false
            fun handOff(target: String?) {
                if (handedOff || target.isNullOrBlank()) return
                if (target == "about:blank") return
                handedOff = true
                Log.d(TAG, "window.open -> $target")
                holder.destroyPopup()
                load(target, referer = currentUrl)
            }
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    handOff(request?.url?.toString())
                    return true
                }

                override fun onPageStarted(v: WebView?, u: String?, favicon: Bitmap?) {
                    handOff(u)
                }
            }
            popup.webChromeClient = this
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }

    web.setDownloadListener { downloadUrl, _, _, _, _ ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))) }
    }

    holder.web = web
    // 真正的加载由 Composable 在解析出答题页之后触发（见 LaunchedEffect）
    holder.load = { target -> load(target, referer = "https://mooc1.chaoxing.com") }
    return web
}

/** jsbridge://openUrl?url=... / cxstudy://web?url=... 这类壳协议里的目标地址 */
private fun bridgedAddress(uri: Uri): String? {
    for (key in listOf("url", "webUrl", "targetUrl", "link")) {
        val value = uri.getQueryParameter(key).orEmpty()
        if (value.startsWith("http://") || value.startsWith("https://")) return value
    }
    val decoded = Uri.decode(uri.path.orEmpty().trimStart('/'))
    return if (decoded.startsWith("http://") || decoded.startsWith("https://")) decoded else null
}

/** 去掉学习通内部标记，补齐成绝对地址 */
private fun cleanUrl(raw: String): String {
    var url = raw.trim()
        .substringBefore("#INNER")
        .replace(Regex("[&?]keyboardDisplayRequiresUserAction=1"), "")
    if (url.endsWith("?") || url.endsWith("&")) url = url.dropLast(1)
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://mooc1-api.chaoxing.com$url"
        else -> url
    }
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
