package com.xxt.board.domain

import com.xxt.board.data.BoardDao
import com.xxt.board.data.ChaoxingSource
import com.xxt.board.data.CourseEntity
import com.xxt.board.data.SettingsStore
import com.xxt.board.data.StatusHistoryEntity
import com.xxt.board.data.Task
import com.xxt.board.data.TaskEntity
import com.xxt.board.data.TaskStatus
import com.xxt.board.data.toDomain
import kotlinx.coroutines.delay

data class SyncReport(
    val needLogin: Boolean = false,
    val courses: Int = 0,
    val tasks: Int = 0,
    /** 本次新出现的未交作业数量 */
    val newTasks: Int = 0,
    val error: String? = null,
)

data class Buckets(
    val todo: List<Task>,
    val pending: List<Task>,
    val done: List<Task>,
    val expired: List<Task>,
)

object TaskBuckets {

    /**
     * 四栏分类。
     * 注意：部分学校「过期」也返回 status=1，所以这里用 dueAt 做二次判定。
     */
    fun split(tasks: List<Task>, now: Long): Buckets {
        val todo = ArrayList<Task>()
        val pending = ArrayList<Task>()
        val done = ArrayList<Task>()
        val expired = ArrayList<Task>()

        for (t in tasks) {
            when (t.status) {
                TaskStatus.EXPIRED -> expired += t
                TaskStatus.SUBMITTED -> pending += t
                TaskStatus.GRADED -> done += t
                TaskStatus.TODO ->
                    if (t.dueAt != null && t.dueAt <= now) expired += t else todo += t
            }
        }
        return Buckets(
            todo = todo.sortedBy { it.dueAt ?: Long.MAX_VALUE },
            pending = pending.sortedByDescending { it.submittedAt ?: 0L },
            done = done.sortedByDescending { it.gradedAt ?: 0L },
            expired = expired.sortedByDescending { it.dueAt ?: 0L },
        )
    }
}

/** 同一条任务的详情最多 6 小时抓一次，避免每次同步重复请求 */
private const val DETAIL_RETRY_MS = 6 * 60 * 60 * 1000L

class SyncEngine(
    private val source: ChaoxingSource,
    private val dao: BoardDao,
    private val settings: SettingsStore,
) {

    suspend fun sync(onStep: (String) -> Unit = {}): SyncReport {
        if (!source.checkSession()) return SyncReport(needLogin = true)

        val courses = runCatching { source.courses() }
            .getOrElse { return SyncReport(error = it.message) }

        dao.upsertCourses(
            courses.map { CourseEntity(it.courseId, it.classId, it.cpi, it.name, it.subject.name) },
        )

        val now = System.currentTimeMillis()
        val existing = dao.allTasks().associateBy { it.key }
        val merged = ArrayList<TaskEntity>()
        var newCount = 0

        for (course in courses) {
            onStep("正在同步 ${course.name}")
            val tasks = runCatching { source.tasks(course) }.getOrNull() ?: continue
            for (task in tasks) {
                if (existing[task.key] == null && task.status == TaskStatus.TODO) newCount++
                merged += merge(existing[task.key], task, now)
            }
            delay(250) // 礼貌间隔，别把接口打急了
        }

        // 补详情：① 未交 / 已过期但缺截止时间；② 已批阅但列表页没给分数（考试分数多半只在详情页）。
        // 同一条只补一次（6 小时内不重复抓），每次同步最多 24 条。
        var budget = 24
        for (i in merged.indices) {
            if (budget <= 0) break
            val e = merged[i]
            if (e.url.isBlank()) continue
            val needDue = (e.status == TaskStatus.TODO.name || e.status == TaskStatus.EXPIRED.name) &&
                e.dueAt == null
            val needScore = e.status == TaskStatus.GRADED.name && e.score.isNullOrBlank()
            if (!needDue && !needScore) continue
            val lastTry = e.detailFetchedAt ?: 0L
            if (lastTry != 0L && now - lastTry < DETAIL_RETRY_MS) continue
            budget--
            onStep("补详情 ${e.title.take(10)}")
            val enriched = runCatching { source.detail(e.toDomain()) }.getOrNull() ?: continue
            merged[i] = e.copy(
                dueAt = enriched.dueAt ?: e.dueAt,
                submittedAt = enriched.submittedAt ?: e.submittedAt,
                score = enriched.score ?: e.score,
                detailFetchedAt = now,
            )
        }

        dao.upsertTasks(merged)
        settings.markSynced(now)
        return SyncReport(courses = courses.size, tasks = merged.size, newTasks = newCount)
    }

    private suspend fun merge(old: TaskEntity?, task: Task, now: Long): TaskEntity {
        val prev = old?.status?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() }
        var submitted = old?.submittedAt
        var graded = old?.gradedAt

        when {
            old == null ->
                if (task.status == TaskStatus.SUBMITTED || task.status == TaskStatus.GRADED) {
                    submitted = task.publishAt
                }

            prev != task.status -> {
                if (task.status == TaskStatus.SUBMITTED) submitted = now
                if (task.status == TaskStatus.GRADED) graded = now
                dao.addHistory(StatusHistoryEntity(task.key, task.status.name, now))
            }
        }

        return TaskEntity(
            key = task.key,
            type = task.type.name,
            courseId = task.courseId,
            classId = task.classId,
            courseName = task.courseName,
            title = task.title,
            status = task.status.name,
            publishAt = task.publishAt ?: old?.publishAt,
            dueAt = task.dueAt ?: old?.dueAt,
            submittedAt = submitted,
            gradedAt = graded,
            score = task.score ?: old?.score,
            url = task.url,
            firstSeenAt = old?.firstSeenAt ?: now,
            lastSyncAt = now,
            detailFetchedAt = old?.detailFetchedAt,
        )
    }
}
