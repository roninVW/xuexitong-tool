package com.xxt.board

import com.xxt.board.data.*
import com.xxt.board.work.DeadlinePlan
import org.junit.Assert.*
import org.junit.Test

class DeadlinePlanTest {
    private val now=1_000_000L
    private fun task(status: TaskStatus=TaskStatus.TODO,due: Long?=now+120_000,type: TaskType=TaskType.WORK)=
        TaskEntity("test",type.name,"c","cl","课程","作业",status.name,null,due,null,null,null,
            "https://mooc1.chaoxing.com",now,now,null)
    @Test fun onlyPendingHomeworkWithFutureDeadlineIsEligible() {
        assertTrue(DeadlinePlan.eligible(task(),now))
        for(status in listOf(TaskStatus.SUBMITTED,TaskStatus.GRADED,TaskStatus.EXPIRED))
            assertFalse(DeadlinePlan.eligible(task(status),now))
        assertFalse(DeadlinePlan.eligible(task(type=TaskType.EXAM),now))
        assertFalse(DeadlinePlan.eligible(task(due=null),now))
        assertFalse(DeadlinePlan.eligible(task(due=now),now))
        assertFalse(DeadlinePlan.eligible(task(due=now-1),now))
    }
    @Test fun overdueReminderWindowSchedulesImmediatelyWithoutSendingExpiredWork() {
        assertEquals(now,DeadlinePlan.triggerAt(now+120_000,60,now))
        assertEquals(now+60_000,DeadlinePlan.triggerAt(now+120_000,1,now))
    }
    @Test fun dedupeKeyChangesOnlyWhenTaskOrReminderChanges() {
        val key=DeadlinePlan.fingerprint("a",now,60)
        assertEquals(key,DeadlinePlan.fingerprint("a",now,60))
        assertNotEquals(key,DeadlinePlan.fingerprint("a",now+1,60))
        assertNotEquals(key,DeadlinePlan.fingerprint("a",now,30))
        assertNotEquals(key,DeadlinePlan.fingerprint("b",now,60))
    }
}
