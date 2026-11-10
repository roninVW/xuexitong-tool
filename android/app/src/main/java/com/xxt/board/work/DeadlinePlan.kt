package com.xxt.board.work

import com.xxt.board.data.TaskEntity
import com.xxt.board.data.TaskStatus
import com.xxt.board.data.TaskType
import java.security.MessageDigest

/** Pure scheduling policy; both scheduler and delivery use this exact predicate. */
object DeadlinePlan {
    fun eligible(task: TaskEntity, now: Long): Boolean = task.type == TaskType.WORK.name &&
        task.status == TaskStatus.TODO.name && task.dueAt != null && task.dueAt > now

    fun triggerAt(dueAt: Long, leadMinutes: Int, now: Long): Long =
        (dueAt - leadMinutes.coerceIn(1, 10080) * 60_000L).coerceAtLeast(now)

    fun fingerprint(key: String, dueAt: Long, leadMinutes: Int): String =
        MessageDigest.getInstance("SHA-256").digest("$key|$dueAt|$leadMinutes".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
