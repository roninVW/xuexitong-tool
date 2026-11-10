package com.xxt.board.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.xxt.board.MainActivity
import com.xxt.board.R
import com.xxt.board.XxtApp
import com.xxt.board.AppGraph
import com.xxt.board.data.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Persistent one-shot WorkManager jobs survive process death and reboot. No network needed. */
object DeadlineScheduler {
    const val EXTRA_TASK_KEY = "deadline_task_key"
    private const val TAG = "homework-deadline"
    private const val AUDIT = "homework-deadline-audit"
    private const val CHANNEL = "homework_deadlines"
    private val mutex = Mutex()
    private fun journal(context: Context) = context.getSharedPreferences("deadline_journal", Context.MODE_PRIVATE)
    private fun name(fingerprint: String) = "deadline-$fingerprint"

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "作业截止提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "按作业实际截止时间发送提醒"
            })
    }

    suspend fun reconcile(context: Context, graph: AppGraph) = mutex.withLock {
        val settings = graph.settings.flow.first()
        val wm = WorkManager.getInstance(context)
        val prefs = journal(context)
        val previous = prefs.getStringSet("active", emptySet()).orEmpty().toSet()
        if (!settings.deadlineReminders || !graph.isLoggedIn()) {
            wm.cancelAllWorkByTag(TAG)
            wm.cancelUniqueWork(AUDIT)
            previous.forEach { NotificationManagerCompat.from(context).cancel(it, 0) }
            prefs.edit().putStringSet("active", emptySet()).commit()
            return@withLock
        }
        ensureChannel(context)
        wm.enqueueUniquePeriodicWork(AUDIT, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeadlineAuditWorker>(15, TimeUnit.MINUTES).build())
        val now = System.currentTimeMillis()
        val current = linkedSetOf<String>()
        graph.db.dao().allTasks().filter { DeadlinePlan.eligible(it, now) }.forEach { task ->
            val due = task.dueAt!!
            val fingerprint = DeadlinePlan.fingerprint(task.key, due, settings.deadlineLeadMinutes)
            current += fingerprint
            if (!prefs.getBoolean("sent-$fingerprint", false) && canNotify(context)) {
                val request = OneTimeWorkRequestBuilder<DeadlineWorker>()
                    .setInputData(workDataOf("key" to task.key, "due" to due, "lead" to settings.deadlineLeadMinutes))
                    .setInitialDelay(DeadlinePlan.triggerAt(due, settings.deadlineLeadMinutes, now) - now, TimeUnit.MILLISECONDS)
                    .addTag(TAG).build()
                // KEEP preserves running work; completed/missing jobs can be recreated after permission grant.
                wm.enqueueUniqueWork(name(fingerprint), ExistingWorkPolicy.KEEP, request)
            }
        }
        (previous - current).forEach {
            wm.cancelUniqueWork(name(it))
            NotificationManagerCompat.from(context).cancel(it, 0)
        }
        prefs.edit().putStringSet("active", current).commit()
    }

    /** Re-read DB + preferences just before posting. Journal and delivery are serialized. */
    suspend fun deliver(context: Context, graph: AppGraph, key: String, expectedDue: Long, expectedLead: Int): Boolean = mutex.withLock {
        val settings = graph.settings.flow.first()
        val task = graph.db.dao().task(key) ?: return@withLock true
        val now = System.currentTimeMillis()
        if (!settings.deadlineReminders || !graph.isLoggedIn() ||
            settings.deadlineLeadMinutes != expectedLead || task.dueAt != expectedDue || !DeadlinePlan.eligible(task, now)) return@withLock true
        if (now < expectedDue - expectedLead * 60_000L) return@withLock false
        if (!canNotify(context)) return@withLock true
        val fingerprint = DeadlinePlan.fingerprint(key, expectedDue, expectedLead)
        val prefs = journal(context)
        if (prefs.getBoolean("sent-$fingerprint", false)) return@withLock true
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.xxt.board.OPEN_DEADLINE"
            data = Uri.parse("xxt-board://deadline/$fingerprint")
            putExtra(EXTRA_TASK_KEY, key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dueText = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(expectedDue))
        val remaining = ((expectedDue - now + 59_999) / 60_000).coerceAtLeast(1)
        val remainingText = if (remaining >= 60) "剩余 ${remaining / 60} 小时 ${remaining % 60} 分钟" else "剩余 $remaining 分钟"
        val text = "截止 $dueText · $remainingText"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(task.title)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setTimeoutAfter(expectedDue - now).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        try {
            NotificationManagerCompat.from(context).notify(fingerprint, 0, notification)
            // Same tag/id makes crash-before-journal replay replace silently rather than alert twice.
            prefs.edit().putBoolean("sent-$fingerprint", true).commit()
            true
        } catch (_: SecurityException) { true }
    }
}

class DeadlineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = withTimeoutOrNull(20_000) { (applicationContext as XxtApp).graphFlow.filterNotNull().first() } ?: return Result.retry()
        val key = inputData.getString("key") ?: return Result.failure()
        val ok = DeadlineScheduler.deliver(applicationContext, graph, key, inputData.getLong("due", 0), inputData.getInt("lead", 60))
        return if (ok) Result.success() else Result.retry()
    }
}

class DeadlineAuditWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = withTimeoutOrNull(20_000) { (applicationContext as XxtApp).graphFlow.filterNotNull().first() } ?: return Result.retry()
        DeadlineScheduler.reconcile(applicationContext, graph)
        return Result.success()
    }
}
