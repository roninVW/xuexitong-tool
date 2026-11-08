package com.xxt.board.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.xxt.board.R
import com.xxt.board.XxtApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as XxtApp
        // graph 在 IO 线程异步初始化；还没就绪就下次再跑
        val graph = app.graphFlow.value ?: return Result.retry()
        if (!graph.settings.flow.first().notifications) return Result.success()
        val report = graph.sync.sync()
        if (report.needLogin) return Result.failure()
        if (report.error != null) return Result.retry()
        if (report.tasks > 0) Notifier.notifySynced(applicationContext, report.tasks, report.newTasks)
        return Result.success()
    }
}

object SyncScheduler {
    private const val NAME = "periodic-sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(60, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}

object Notifier {
    private const val CHANNEL = "sync"
    private const val ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "同步提醒", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun notifySynced(context: Context, count: Int, newCount: Int = 0) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (newCount > 0) "新增 $newCount 项作业" else "作业已同步")
            .setContentText(if (newCount > 0) "共 $count 项，点开看看" else "共 $count 项作业与考试")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }
}
