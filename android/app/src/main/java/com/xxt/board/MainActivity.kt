package com.xxt.board

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import com.xxt.board.ui.components.PaperTopBar
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import com.xxt.board.ui.theme.M3Transitions
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import android.webkit.JsPromptResult
import android.webkit.JsResult
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import com.xxt.board.ui.components.AppIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxt.board.ui.PageSwitcher
import com.xxt.board.ui.components.BackdropHost
import com.xxt.board.ui.components.LocalPageFocus
import com.xxt.board.ui.components.PaperDock
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import android.webkit.CookieManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xxt.board.data.AppSettings
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.board.BoardScreen
import com.xxt.board.ui.board.BoardViewModel
import com.xxt.board.ui.login.LoginScreen
import com.xxt.board.ui.web.openWebPage
import com.xxt.board.ui.schedule.ScheduleScreen
import com.xxt.board.ui.schedule.ScheduleViewModel
import com.xxt.board.ui.settings.SettingsScreen
import com.xxt.board.ui.stats.StatsScreen
import com.xxt.board.ui.theme.AccentColors
import com.xxt.board.ui.components.LocalGlassAlpha
import com.xxt.board.ui.theme.ThemeStyle
import com.xxt.board.ui.theme.XxtTheme
import com.xxt.board.ui.wallpaper.AppWallpaper
import com.xxt.board.ui.wallpaper.LocalWallpaper
import com.xxt.board.ui.wallpaper.WallpaperBackdrop
import com.xxt.board.ui.wallpaper.WallpaperSpec
import com.xxt.board.work.SyncScheduler
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as XxtApp

        setContent {
            val graph by app.graphFlow.collectAsState(initial = null)
            if (graph == null) {
                // 首帧立刻有内容：不碰 Room / Keystore / 宋体字体
                SplashContent()
            } else {
                val g = graph!!
                val settings by g.settings.flow.collectAsState(initial = AppSettings())
                val dark = settings.darkMode ?: isSystemInDarkTheme()
                val view = LocalView.current

                LaunchedEffect(dark) {
                    val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }

                val backdrop = remember { WallpaperBackdrop() }
                SideEffect { backdrop.cardBlur = settings.cardBlur }

                XxtTheme(
                    dark = dark,
                    accents = AccentColors(
                        base = Color(settings.workColor),
                        examOverride = settings.examColor
                            .takeIf { it != 0L }
                            ?.let { Color(it) },
                    ),
                    style = ThemeStyle.of(settings.themeStyle),
                ) {
                    CompositionLocalProvider(
                        LocalWallpaper provides backdrop,
                        // 0 = 自动（随模糊强度）；否则就是用户手动拉的不透明度
                        LocalGlassAlpha provides settings.glassOpacity
                            .takeIf { it > 0 }?.let { it / 100f },
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AppWallpaper(
                                spec = WallpaperSpec(settings.wallpaperPath, settings.wallpaperVideo),
                                backdrop = backdrop,
                            )
                            AppNav(graph = g, settings = settings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashContent() {
    val dark = isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF151517) else Color(0xFFF6F0E2)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Color(0xFFC4763F))
            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
            Text(
                text = "作业板",
                color = if (dark) Color(0xFFEDEDEF) else Color(0xFF2B2419),
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun AppNav(graph: AppGraph, settings: AppSettings) {
    val nav = rememberNavController()
    val start = if (graph.isLoggedIn()) Routes.MAIN else Routes.LOGIN
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val boardVm: BoardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BoardViewModel(graph.db.dao(), graph.sync) }
        },
    )
    val scheduleVm: ScheduleViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ScheduleViewModel(graph.db.scheduleDao()) }
        },
    )

    val cs = MaterialTheme.colorScheme
    // 网页顶部栏与主程序同色：主题跟随用户设置，不一定等于系统
    val webDark = settings.darkMode ?: isSystemInDarkTheme()
    val wallpaper = LocalWallpaper.current
    val shell = if (wallpaper.active) Modifier else Modifier.background(cs.surface)
    Box(Modifier.fillMaxSize().then(shell)) {
        NavHost(
            navController = nav,
            startDestination = start,
            // 统一 M3 转场：层级导航走 shared axis X（前进右进、返回左进）
            enterTransition = { M3Transitions.sharedAxisEnter(forward = true) },
            exitTransition = { M3Transitions.sharedAxisExit(forward = true) },
            popEnterTransition = { M3Transitions.sharedAxisEnter(forward = false) },
            popExitTransition = { M3Transitions.sharedAxisExit(forward = false) },
        ) {
            composable(Routes.MAIN) {
                var tabName by rememberSaveable { mutableStateOf(AppTab.BOARD.name) }
                val tab = AppTab.entries.firstOrNull { it.name == tabName } ?: AppTab.BOARD

                fun switchTab(next: AppTab) {
                    if (next == tab) return
                    tabName = next.name
                }

                BackHandler(enabled = tab != AppTab.BOARD) {
                    tabName = AppTab.BOARD.name
                }

                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                // 采样层在切换器之外：四个页面共享一份，顶栏与 Dock 都从它取内容。
                BackdropHost {
                    PageSwitcher(target = tab) { current ->
                    // 只有目标页可以录制共享玻璃采样，旧页仅负责按进度退出。
                    // 动画结束后 current==tab，采样自动恢复，不需要延迟或手动重置 alpha。
                    CompositionLocalProvider(LocalPageFocus provides (current == tab)) {
                    when (current) {
                        AppTab.BOARD -> BoardScreen(
                            vm = boardVm,
                            blurRadius = settings.blurRadius,
                            flameEnabled = settings.flameEffect,
                            showQuote = settings.showQuote,
                            // 应用内网页 = 独立原生 Activity（WebView 不进 Compose，避免测量/触摸被干扰）
                            onOpenWeb = { task -> openWebPage(context, task.url, task.title, webDark) },
                            // 卡片上的「学习通」= 跳学习通 App（失败会回落到应用内页面）
                            onJump = { task ->
                                scope.launch {
                                    // 先把入口页解析成真正的答题页，交给学习通 / 浏览器才落在作业上
                                    val target = runCatching {
                                        graph.source.resolveOpenUrl(task.key, task.url)
                                    }.getOrDefault(task.url)
                                    openInChaoxing(context, target, task.title) { url ->
                                        openWebPage(context, url, task.title, webDark)
                                    }
                                }
                            },
                            onNeedLogin = {
                                graph.vault.clear()
                                nav.navigate(Routes.LOGIN) { popUpTo(0) }
                            },
                            onNavigate = { next -> switchTab(next) },
                        )

                        AppTab.SCHEDULE -> ScheduleScreen(
                            vm = scheduleVm,
                            blurRadius = settings.blurRadius,
                            onNavigate = { next -> switchTab(next) },
                        )

                        AppTab.STATS -> StatsScreen(
                            vm = boardVm,
                            blurRadius = settings.blurRadius,
                            onNavigate = { next -> switchTab(next) },
                        )

                        else -> SettingsScreen(
                            settings = settings,
                            blurRadius = settings.blurRadius,
                            onDarkMode = { v -> scope.launch { graph.settings.setDarkMode(v) } },
                            onBlur = { v -> scope.launch { graph.settings.setBlurRadius(v) } },
                            onGlassOpacity = { v -> scope.launch { graph.settings.setGlassOpacity(v) } },
                            onThemeStyle = { v -> scope.launch { graph.settings.setThemeStyle(v) } },
                            onWorkColor = { v -> scope.launch { graph.settings.setWorkColor(v) } },
                            onExamColor = { v -> scope.launch { graph.settings.setExamColor(v) } },
                            onShowQuote = { v -> scope.launch { graph.settings.setShowQuote(v) } },
                            onFlame = { v -> scope.launch { graph.settings.setFlameEffect(v) } },
                            onNotifications = { v ->
                                scope.launch {
                                    graph.settings.setNotifications(v)
                                    if (v) SyncScheduler.schedule(context) else SyncScheduler.cancel(context)
                                }
                            },
                            onLogout = {
                                graph.vault.clear()
                                nav.navigate(Routes.LOGIN) { popUpTo(0) }
                            },
                            onNavigate = { next -> switchTab(next) },
                        )
                    }
                    }
                    }

                    // Dock 画在页面切换器之外：转场时保持静止，不跟随页面淡入淡出
                    PaperDock(
                        selected = tab,
                        onSelect = { switchTab(it) },
                        blurRadius = settings.blurRadius,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp + navBottom),
                    )
                }
            }

            composable(Routes.LOGIN) {
                LoginScreen { cookies ->
                    graph.vault.save(cookies)
                    nav.navigate(Routes.MAIN) { popUpTo(0) }
                }
            }

        }
    }
}

private object Routes {
    const val MAIN = "main"
    const val LOGIN = "login"
}

private const val CHAOXING_PACKAGE = "com.chaoxing.mobile"

private fun toast(context: Context, text: String) {
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
}

/** 拿不到深层链接时的兜底：链接进剪贴板，用户在学习通里长按就能粘 */
private fun copyLink(context: Context, url: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("学习通作业链接", url))
    }
}

/**
 * 一跳学习通 App，尽量直接落到「这份作业」上。
 *
 * 老实现只有一个假设：`ACTION_VIEW + setPackage` 就能把网址喂给学习通。实际不是——
 * 学习通没给作业页注册 http 过滤器，`queryIntentActivities` 直接空，于是每次都落到
 * 应用内浏览器，看起来就是「跳转没生效」。
 *
 * 现在的顺序（每一级都有可见反馈，logcat 里也留痕）：
 *  1. App 若确实注册了这个域名 → 直接把答题页网址交给它；
 *  2. App 自己声明的私有 scheme（cxstudy / chaoxing）带上目标地址；
 *  3. `intent://` 形式（带 package + scheme=https）；
 *  4. 都不行 → 拉起首页，并把答题页地址放进剪贴板（在学习通里粘贴即可）。
 */
fun openInChaoxing(context: Context, url: String, title: String, onFallback: (String) -> Unit) {
    val pm = context.packageManager
    if (runCatching { pm.getPackageInfo(CHAOXING_PACKAGE, 0) }.isFailure) {
        toast(context, "没装学习通，先用应用内页面打开")
        onFallback(url)
        return
    }

    // 接口域名在 App 里打不开，换成网页域名；入口页若没解析出答题页，至少也是个能看的页面
    val web = webUrlOf(url)

    // 1) 域名被 App 接管（部分版本会注册 i.chaoxing.com 之类）
    for (target in listOf(web, web.replaceFirst("https://", "http://"))) {
        if (!appHandles(pm, target)) continue
        if (startView(context, target)) {
            Log.d("XXTJump", "交给学习通处理: $target")
            toast(context, "已在学习通里打开这份作业")
            return
        }
    }

    // 2) App 自己的私有 scheme。
    //
    // 这里不再拿 scheme 探测结果做闸门：`queryIntentActivities` 在部分 ROM / Android 版本上
    // 会因为包可见性或 filter 形态返回空，一旦为空，下面的深链就一条都不会发出去——表现正是
    // 「点了学习通，只跳到首页」。探测结果只用于日志与排序，发不发由「有没有装」决定。
    val schemes = appSchemes(pm)
    Log.d("XXTJump", "学习通声明的 scheme=${schemes.ifEmpty { "探测为空" }} | 目标=$web")
    val encoded = Uri.encode(web)
    val deepLinks = buildList {
        // 学习通网页里唤醒用的就是 cxstudy://；带 url/webUrl 参数是各家壳常见写法，
        // 参数被忽略时至少把 App 拉起来，不会比现状更差。
        add("cxstudy://openApp?url=$encoded&webUrl=$encoded")
        add("cxstudy://web?url=$encoded&webUrl=$encoded")
        add("cxstudy://browser?url=$encoded")
        add("cxstudy://?url=$encoded&webUrl=$encoded")
        if ("chaoxing" in schemes || schemes.isEmpty()) {
            add("chaoxing://web?url=$encoded&webUrl=$encoded")
            add("chaoxing://openApp?url=$encoded&webUrl=$encoded")
        }
        // 纯唤醒：保证 App 一定起来（排在上面几条之后）
        add("cxstudy://")
        // intent:// 形式，交给系统按 package + scheme 解析
        add("intent://${web.substringAfter("://")}#Intent;scheme=https;package=$CHAOXING_PACKAGE;end")
    }
    for (deep in deepLinks) {
        if (!startRaw(context, deep)) {
            Log.d("XXTJump", "深链被拒: ${deep.take(80)}")
            continue
        }
        Log.d("XXTJump", "深链已发出: ${deep.take(120)}")
        copyLink(context, web)
        toast(context, if (deep == "cxstudy://") {
            "已打开学习通；它没有开放按作业直达，答题页链接已复制"
        } else {
            "已调起学习通；若停在首页，答题页链接已复制，可粘贴打开"
        })
        return
    }

    // 3) 启动页带上地址：多数版本会忽略，少数会当深链用，代价为零
    val launch = pm.getLaunchIntentForPackage(CHAOXING_PACKAGE)
    if (launch != null) {
        launch.setData(Uri.parse(web))
        launch.putExtra("url", web)
        launch.putExtra("webUrl", web)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(launch) }.isSuccess) {
            Log.d("XXTJump", "启动页带参: $web")
            copyLink(context, web)
            toast(context, "学习通已打开；答题页链接已复制，可粘贴打开")
            return
        }
    }

    toast(context, "打开学习通失败，已用应用内页面打开")
    onFallback(web)
}

/** 应用是否给这个网址声明了打开方式（学习通大多作业页是 false，这正是老实现失效的原因） */
private fun appHandles(pm: android.content.pm.PackageManager, url: String): Boolean = runCatching {
    pm.queryIntentActivities(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(CHAOXING_PACKAGE),
        0,
    ).isNotEmpty()
}.getOrDefault(false)

/** 应用声明的私有 scheme（cxstudy / chaoxing …），用来拼深链 */
private fun appSchemes(pm: android.content.pm.PackageManager): Set<String> {
    val probe = Intent(Intent.ACTION_VIEW).setPackage(CHAOXING_PACKAGE)
    val list = runCatching {
        pm.queryIntentActivities(probe, android.content.pm.PackageManager.MATCH_ALL)
    }.getOrNull().orEmpty()
    val out = LinkedHashSet<String>()
    for (ri in list) {
        val filter = ri.filter ?: continue
        for (i in 0 until filter.countDataSchemes()) {
            filter.getDataScheme(i)?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }?.let(out::add)
        }
    }
    return out
}

private fun startView(context: Context, target: String): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            setPackage(CHAOXING_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}.isSuccess

private fun startRaw(context: Context, raw: String): Boolean {
    val intent = if (raw.startsWith("intent:")) {
        runCatching { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return false
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply { setPackage(CHAOXING_PACKAGE) }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** 接口域名（mooc1-api）在 WebView 里打不开，统一换回网页域名 */
fun webUrlOf(raw: String): String {
    val cleaned = raw.trim().replace("mooc1-api.chaoxing.com", "mooc1.chaoxing.com")
    return when {
        cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
        cleaned.startsWith("//") -> "https:$cleaned"
        cleaned.startsWith("/") -> "https://mooc1.chaoxing.com$cleaned"
        cleaned.isBlank() -> "https://mooc1.chaoxing.com"
        else -> "https://mooc1.chaoxing.com/$cleaned"
    }
}

/** 需要注入登录态的学习通域名（考试页在 mooc2-ans，作业页在 mooc1） */
private val CHAOXING_HOSTS = listOf(
    "https://mooc1.chaoxing.com",
    "https://mooc2-ans.chaoxing.com",
    "https://mooc2.chaoxing.com",
    "https://mooc1-api.chaoxing.com",
    "https://i.chaoxing.com",
    "https://www.chaoxing.com",
    "https://chaoxing.com",
    "https://passport2.chaoxing.com",
    "https://sso.chaoxing.com",
    "https://v8.chaoxing.com",
)

/**
 * 把同步用的 Cookie 灌进 WebView 的 CookieManager。
 *
 * 应用里的网络请求走 ChaoxingHttp（自己拼 Cookie 头），WebView 用的是系统
 * CookieManager，两边本来不同步。不灌的话：作业页有时能凑合打开，
 * **考试页基本都会跳登录或直接白屏** —— 这就是"考试的 webview 无法显示内容"的主因。
 */
fun injectChaoxingCookies(cookie: String) {
    if (cookie.isBlank()) return
    val cm = CookieManager.getInstance()
    cm.setAcceptCookie(true)
    val pairs = cookie.split(";").map { it.trim() }.filter { it.contains('=') }.map {
        it.substringBefore('=') to it.substringAfter('=')
    }
    if (pairs.isEmpty()) return
    for (host in CHAOXING_HOSTS) {
        // 只补缺的：WebView 里有同名 Cookie 就以它为准（用户可能在 WebView 里重新登录过，
        // 那些 Cookie 比保险箱里的新）。缺什么补什么，既不会把新会话顶掉，也不会白屏。
        val have = runCatching { cm.getCookie(host) }.getOrNull().orEmpty()
            .split(";")
            .map { it.trim().substringBefore('=') }
            .filter { it.isNotEmpty() }
            .toSet()
        for ((name, value) in pairs) {
            if (name !in have) cm.setCookie(host, "$name=$value; path=/")
        }
    }
    cm.flush()
}
