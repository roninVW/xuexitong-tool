package com.xxt.board

import android.test.InstrumentationTestCase
import android.content.Context
import android.app.NotificationManager
import com.xxt.board.data.*
import com.xxt.board.work.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout

/** Emulator-only fixture. No server writes, no changes to real homework records. */
@Suppress("DEPRECATION")
class DeadlineDeviceTest : InstrumentationTestCase() {
    private val key="__deadline_device_fixture__"
    private val context get()=instrumentation.targetContext
    private suspend fun graph()=withTimeout(20_000) { (context.applicationContext as XxtApp).graphFlow.filterNotNull().first() }
    private fun task(due:Long,status:TaskStatus=TaskStatus.TODO)=TaskEntity(key,"WORK","debug","debug","模拟器验收","截止提醒验收作业",status.name,null,due,null,null,null,
        "https://mooc1.chaoxing.com",System.currentTimeMillis(),System.currentTimeMillis(),null)

    fun testPrepareBackgroundReminder()=runBlocking {
        val g=graph()
        assertTrue("需要当前已登录缓存",g.isLoggedIn())
        val original=g.settings.flow.first()
        context.getSharedPreferences("deadline_device_fixture",Context.MODE_PRIVATE).edit()
            .putBoolean("enabled",original.deadlineReminders).putInt("lead",original.deadlineLeadMinutes).putBoolean("saved",true).commit()
        val due=System.currentTimeMillis()+90_000
        g.db.dao().upsertTasks(listOf(task(due)))
        g.settings.setDeadlineLeadMinutes(1)
        g.settings.setDeadlineReminders(true)
        DeadlineScheduler.reconcile(context,g)
        assertTrue(DeadlineScheduler.canNotify(context))
    }

    fun testVerifyDeliveryAndCleanup()=runBlocking {
        val g=graph()
        val saved=context.getSharedPreferences("deadline_device_fixture",Context.MODE_PRIVATE)
        try {
            val originalTask=g.db.dao().task(key)!!
            val due=originalTask.dueAt!!
            val tag=DeadlinePlan.fingerprint(key,due,1)
            val manager=context.getSystemService(NotificationManager::class.java)
            val active=manager.activeNotifications.firstOrNull{it.tag==tag}
            assertNotNull("后台一次性任务应已发出通知",active)
            assertEquals("截止提醒验收作业",active!!.notification.extras.getString("android.title"))
            val postTime=active.postTime
            DeadlineScheduler.deliver(context,g,key,due,1)
            assertEquals("重复执行不能重新发出相同提醒",postTime,manager.activeNotifications.first{it.tag==tag}.postTime)
            // A changed deadline invalidates old input, and completion cancels the posted notice.
            g.db.dao().upsertTasks(listOf(originalTask.copy(dueAt=due+3_600_000)))
            DeadlineScheduler.deliver(context,g,key,due,1)
            DeadlineScheduler.reconcile(context,g)
            assertFalse(manager.activeNotifications.any{it.tag==tag})
            val changed=DeadlinePlan.fingerprint(key,due+3_600_000,1)
            g.db.dao().upsertTasks(listOf(originalTask.copy(status=TaskStatus.SUBMITTED.name)))
            DeadlineScheduler.reconcile(context,g)
            DeadlineScheduler.deliver(context,g,key,due,1)
            assertFalse(manager.activeNotifications.any{it.tag==tag || it.tag==changed})
            g.db.dao().upsertTasks(listOf(originalTask.copy(dueAt=System.currentTimeMillis()-1)))
            DeadlineScheduler.deliver(context,g,key,System.currentTimeMillis()-1,1)
            assertFalse(manager.activeNotifications.any{it.tag==tag})
        } finally {
            g.db.dao().deleteTask(key)
            if(saved.getBoolean("saved",false)) {
                g.settings.setDeadlineReminders(saved.getBoolean("enabled",false))
                g.settings.setDeadlineLeadMinutes(saved.getInt("lead",60))
                saved.edit().clear().commit()
            }
            DeadlineScheduler.reconcile(context,g)
        }
    }
}
