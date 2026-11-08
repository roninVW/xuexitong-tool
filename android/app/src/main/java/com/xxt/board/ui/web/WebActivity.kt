package com.xxt.board.ui.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.xxt.board.data.CookieVault
import com.xxt.board.injectChaoxingCookies
import org.json.JSONObject
import java.util.Locale

/**
 * 应用内学习通网页 —— 独立原生 Activity，WebView 不经过 Compose。
 *
 * 与 [siye4366/xxt-Tool] 的 WebActivity 同构（这是对方显示正常的那份实现）：
 * 纯 View 层级放 WebView，不做图形层变换、不做玻璃采样、不套 NavHost 转场。
 * 之前在 Compose 里用 `AndroidView` 嵌 WebView，触摸与测量要先过 Compose 这一层，
 * 页面一旦有固定宽度布局（考试页就是）就会错位、元素被挤出可视区。
 *
 * 关键点：
 * 1. jsBridge：学习通「查看作业 / 开始答题 / 提交」走 `androidjsbridge.postNotification`，
 *    页面加载完要把 `jsBridge.setDevice('android')`，否则按钮静默无反应。
 * 2. window.open：`setSupportMultipleWindows(true)` + `onCreateWindow` 用**弹窗**承接，
 *    不能把新地址加载进当前 WebView（那样会把试卷页顶掉，看着像元素丢失）。
 * 3. JS 对话框：提交前的 `confirm()` 不接管，看起来也是「点了没反应」。
 * 4. 文件选择：带附件的作业要能选文件。
 * 5. UA 去掉 `wv`、放开混合内容、先灌 Cookie，否则白屏或跳登录。
 *
 * 注意：**不覆写 `window.open`**、**不设 textZoom / setSupportZoom**，与参考实现保持一致。
 */
class WebActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REFERER = "referer"

        private const val TAG = "XXTWeb"
        private const val DEFAULT_REFERER = "https://mooc1.chaoxing.com/"

        /** 不带 `; wv` 的浏览器 UA：学习通按 `wv` 判定「非浏览器容器」 */
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val BRIDGE_ATTACH_JS =
            "(function(){try{" +
                "if(window.jsBridge&&typeof window.jsBridge.setDevice==='function'){" +
                "window.jsBridge.setDevice('android');return 'android';}" +
                "return 'no-bridge';}catch(e){return 'error';}})()"
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var errorBox: LinearLayout
    private lateinit var tvError: TextView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var popup: WebView? = null
    private var popupDialog: android.app.Dialog? = null
    private var currentUrl: String = ""

    private val night: Boolean by lazy {
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private val paper: Int get() = if (night) 0xFF151517.toInt() else 0xFFF6F0E2.toInt()
    private val card: Int get() = if (night) 0xFF1E1E21.toInt() else 0xFFFFFDF7.toInt()
    private val ink: Int get() = if (night) 0xFFEDEDEF.toInt() else 0xFF2B2419.toInt()
    private val accent: Int get() = 0xFFC4763F.toInt()

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode != RESULT_OK || data == null -> null
            data.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val referer = intent.getStringExtra(EXTRA_REFERER) ?: DEFAULT_REFERER

        buildLayout(title)
        configureWebView()
        injectCookies()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            },
        )

        load(url, referer)
    }

    override fun onDestroy() {
        destroyPopup()
        runCatching {
            webView.stopLoading()
            webView.webChromeClient = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------ UI

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private fun buildLayout(title: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(card)
            setPadding(dp(4), 0, dp(4), 0)
        }
        bar.addView(barButton("←") { if (webView.canGoBack()) webView.goBack() else finish() })

        tvTitle = TextView(this).apply {
            text = title.ifEmpty { "学习通" }
            setTextColor(ink)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bar.addView(
            tvTitle,
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = dp(8)
            },
        )

        bar.addView(barButton("⟳") { webView.reload() })
        bar.addView(barButton("↗") { openExternal(webView.url ?: currentUrl) })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            visibility = View.VISIBLE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))

        val stage = FrameLayout(this)
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        webView = WebView(this)
        stage.addView(
            webView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        errorBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(paper)
            visibility = View.GONE
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        tvError = TextView(this).apply {
            setTextColor(ink)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        errorBox.addView(tvError)
        errorBox.addView(
            actionButton("重试") { hideError(); webView.reload() },
        )
        errorBox.addView(
            actionButton("用浏览器打开") { openExternal(webView.url ?: currentUrl) },
        )
        stage.addView(
            errorBox,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        setContentView(root)

        // 状态栏 / 导航栏避让：WebView 画到状态栏下面会盖住页面自己的标题栏
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun barButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(ink)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
        }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }

    private fun showError(message: String) {
        tvError.text = message
        errorBox.visibility = View.VISIBLE
        webView.visibility = View.GONE
        progress.visibility = View.GONE
    }

    private fun hideError() {
        errorBox.visibility = View.GONE
        webView.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // 作业页会 window.open 出新窗口，必须允许并接管
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = BROWSER_UA
            allowFileAccess = false
            allowContentAccess = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // 页面 jsBridge → 原生
        webView.addJavascriptInterface(ChaoxingJsBridge(), "androidjsbridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleScheme(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                view?.title?.takeIf { it.isNotBlank() }?.let { tvTitle.text = it }
                // 告诉页面「当前是 Android 原生壳」，它才会走 androidjsbridge
                view?.evaluateJavascript(BRIDGE_ATTACH_JS) { r ->
                    Log.d(TAG, "bridge -> $r @ ${view.url}")
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    showError("加载失败：${error?.description ?: "网络错误"}\n${request.url}")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    showError("页面返回 HTTP ${errorResponse?.statusCode}\n${request.url}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView?, t: String?) {
                t?.takeIf { it.isNotBlank() }?.let { tvTitle.text = it }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                if (isFinishing || isDestroyed) {
                    result.cancel()
                    return true
                }
                AlertDialog.Builder(this@WebActivity)
                    .setMessage(message.orEmpty())
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                if (isFinishing || isDestroyed) {
                    result.cancel()
                    return true
                }
                AlertDialog.Builder(this@WebActivity)
                    .setMessage(message.orEmpty())
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?,
            ): Boolean {
                if (result == null) return false
                if (isFinishing || isDestroyed) {
                    result.cancel()
                    return true
                }
                val input = EditText(this@WebActivity).apply {
                    setText(defaultValue.orEmpty())
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
                AlertDialog.Builder(this@WebActivity)
                    .setMessage(message.orEmpty())
                    .setView(input)
                    .setPositiveButton("确定") { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?,
            ): Boolean {
                if (callback == null) return false
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    filePicker.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    false
                }
            }

            /**
             * window.open：用**弹窗**承接，主页面保持不动。
             * 若把新地址塞进当前 WebView，考试页会被顶掉，看起来就是元素丢失/错位。
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                if (resultMsg == null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

                val child = WebView(this@WebActivity)
                child.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = BROWSER_UA
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(child, true)
                child.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val uri = request?.url ?: return false
                        return handleScheme(uri)
                    }
                }
                child.webChromeClient = this

                val dialog = android.app.Dialog(this@WebActivity)
                dialog.setContentView(child)
                dialog.setOnDismissListener {
                    popup = null
                    popupDialog = null
                    runCatching { child.destroy() }
                }
                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                popup = child
                popupDialog = dialog

                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // 当前只申请网络权限；摄像头 / 麦克风类页面先拒绝，避免误导
                request?.deny()
            }

            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                if (msg != null) Log.d(TAG, "JS ${msg.messageLevel()}: ${msg.message()} (${msg.lineNumber()})")
                return true
            }
        }

        webView.setDownloadListener { downloadUrl, _, _, _, _ ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))) }
        }
    }

    private fun injectCookies() {
        val cookie = runCatching { CookieVault(this).load() }.getOrDefault("")
        if (cookie.isBlank()) {
            Log.d(TAG, "本地没有登录 Cookie")
            return
        }
        runCatching { injectChaoxingCookies(cookie) }
    }

    private fun load(target: String, referer: String? = null) {
        if (target.isBlank()) {
            showError("没有可打开的地址")
            return
        }
        currentUrl = target
        Log.d(TAG, "load: $target referer=$referer")
        hideError()
        progress.visibility = View.VISIBLE
        val headers = if (referer.isNullOrBlank()) emptyMap() else mapOf("Referer" to referer)
        webView.loadUrl(target, headers)
    }

    /** http(s) 留在本页；其它 scheme（含 cxstudy:// 等）交给系统 */
    private fun handleScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        return when (scheme) {
            "http", "https" -> false
            else -> {
                openExternal(uri.toString())
                true
            }
        }
    }

    private fun openExternal(target: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        }.onFailure { Log.d(TAG, "openExternal 失败: $target") }
    }

    private fun destroyPopup() {
        runCatching { popupDialog?.dismiss() }
        popupDialog = null
        popup?.let { p -> runCatching { p.destroy() } }
        popup = null
    }

    // ------------------------------------------------------------ jsBridge

    /** 页面里的 `androidjsbridge.postNotification(name, json)` 会进到这里（非 UI 线程）。 */
    private inner class ChaoxingJsBridge {
        @JavascriptInterface
        fun postNotification(name: String?, payload: String?) {
            if (name.isNullOrEmpty()) return
            val body = payload.orEmpty()
            runOnUiThread { handleBridgeNotification(name, body) }
        }
    }

    private fun handleBridgeNotification(name: String, payload: String) {
        Log.d(TAG, "jsBridge: $name ${payload.take(200)}")
        when (name) {
            // 页面要打开下一个原生页面：继续答题 / 查看答案 / 修改作业
            "CLIENT_OPEN_URL" -> {
                val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
                val raw = json.optString("webUrl").trim().ifEmpty { json.optString("url").trim() }
                if (raw.isBlank()) return
                val absolute = when {
                    raw.startsWith("http://") || raw.startsWith("https://") -> raw
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "https://mooc1-api.chaoxing.com$raw"
                    else -> return
                }
                load(absolute, webView.url ?: currentUrl)
            }

            "CLIENT_REFRESH_EVENT" -> webView.reload()

            else -> Unit
        }
    }
}

/** 从别处（Compose / 原生）打开学习通页面的统一入口 */
fun openWebPage(context: Context, url: String, title: String) {
    val intent = Intent(context, WebActivity::class.java).apply {
        putExtra(WebActivity.EXTRA_URL, url)
        putExtra(WebActivity.EXTRA_TITLE, title)
    }
    if (context !is android.app.Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
