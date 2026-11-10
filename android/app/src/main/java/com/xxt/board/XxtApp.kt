package com.xxt.board

import android.app.Application
import android.content.Context
import com.xxt.board.data.AppDatabase
import com.xxt.board.data.ChaoxingHttp
import com.xxt.board.data.ChaoxingSource
import com.xxt.board.data.CookieVault
import com.xxt.board.data.SettingsStore
import com.xxt.board.domain.SyncEngine
import com.xxt.board.work.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.xxt.board.work.DeadlineScheduler

class XxtApp : Application() {

    private val _graph = kotlinx.coroutines.flow.MutableStateFlow<AppGraph?>(null)
    val graphFlow: kotlinx.coroutines.flow.StateFlow<AppGraph?> = _graph

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        // Room 建库、AndroidKeyStore 解 Cookie、WorkManager 初始化都很重，
        // 以前全在 onCreate 的主线程里跑，冷启动会长时间一片空白。
        // 现在全部挪到 IO，UI 先渲染 SplashContent，graph 就绪后再进主界面。
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob(),
        ).launch {
            val g = runCatching { AppGraph(this@XxtApp) }.getOrNull()
            // 预热解密结果，避免首帧在主线程首次访问 AndroidKeyStore
            runCatching { g?.vault?.load() }
            _graph.value = g
            if (g != null) {
                runCatching { SyncScheduler.schedule(this@XxtApp) }
                combine(g.db.dao().observeTasks(), g.settings.flow.map {
                    it.deadlineReminders to it.deadlineLeadMinutes
                }.distinctUntilChanged()) { tasks, settings -> tasks to settings }
                    .collect { runCatching { DeadlineScheduler.reconcile(this@XxtApp, g) } }
            }
        }
    }

    /**
     * 把崩溃栈写进 filesDir/last_crash.txt。
     *
     * 这台机器上没有真机可复现，用户报「闪退」时唯一能拿到的证据就是这个文件，
     * 设置页会把最近一条显示出来。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                java.io.File(filesDir, "last_crash.txt").writeText(
                    buildString {
                        appendLine(System.currentTimeMillis())
                        appendLine("${thread.name} · Android ${android.os.Build.VERSION.RELEASE} " +
                            "(${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
                        appendLine(android.util.Log.getStackTraceString(error))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** 轻量手写 DI：依赖只有 5 个，上 Hilt 反而更重 */
class AppGraph(context: Context) {
    val vault = CookieVault(context)
    val settings = SettingsStore(context)
    val http = ChaoxingHttp(vault)
    val source = ChaoxingSource(http)
    val db = AppDatabase.build(context)
    val sync = SyncEngine(source, db.dao(), settings)

    fun isLoggedIn(): Boolean = vault.hasCookie()
}
