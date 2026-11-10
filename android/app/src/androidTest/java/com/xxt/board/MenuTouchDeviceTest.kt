package com.xxt.board

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyant.backdrop.backdrops.*
import com.xxt.board.ui.liquid.*
import com.xxt.board.ui.theme.XxtTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Restored single-panel menu input and outside-tap dismissal; no saved settings edits. */
@Suppress("DEPRECATION")
class MenuTouchDeviceTest : InstrumentationTestCase() {
    fun testActualMenuHeadingsOptionsAndActionsKeepTheirInput() {
        val activity=start()
        val state=LiquidBoardMenuState()
        val types=AtomicInteger();val statuses=AtomicInteger();val subjects=AtomicInteger();val resets=AtomicInteger()
        val backgroundClicks=AtomicInteger()
        val viewport=AtomicReference(Rect.Zero)
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    XxtTheme(dark=false) {
                        val backdrop=rememberLayerBackdrop()
                        var selectedStatus by remember{mutableStateOf("todo")}
                        var selectedSubject by remember{mutableStateOf<String?>(null)}
                        val model=GlassBoardModel(
                            types=listOf(GlassMenuChoice(null,"全部",4,true),GlassMenuChoice("WORK","作业",3,false),GlassMenuChoice("EXAM","考试",1,false)),
                            statuses=listOf(GlassMenuChoice("todo","未完成",3,selectedStatus=="todo"),GlassMenuChoice("done","已完成",1,selectedStatus=="done")),
                            subjects=listOf(GlassMenuChoice(null,"全部科目",4,selectedSubject==null),GlassMenuChoice("math","数学",2,selectedSubject=="math")),
                            visibleCount=4,onType={types.incrementAndGet()},
                            onStatus={selectedStatus=it;statuses.incrementAndGet()},
                            onSubject={selectedSubject=it;subjects.incrementAndGet()},
                            onReset={selectedStatus="todo";selectedSubject=null;resets.incrementAndGet()})
                        SideEffect {state.model=model}
                        Box(Modifier.fillMaxSize().onGloballyPositioned{viewport.set(it.boundsInWindow())}) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).clickable{backgroundClicks.incrementAndGet()}) {drawRect(Color(0xFF9BBEE4))}
                            LiquidBoardMenus(state,backdrop)
                        }
                    }
                }
                state.filterAnchor=Rect(700f,130f,990f,250f)
                state.typeAnchor=Rect(40f,130f,310f,250f)
                state.open(GlassMenu.FILTER)
            }
            instrumentation.waitForIdleSync();SystemClock.sleep(800)
            val heading=findBounds {it.text?.toString()=="状态"}
            val headerDown=SystemClock.uptimeMillis()
            send(headerDown,MotionEvent.ACTION_DOWN,heading.center)
            SystemClock.sleep(180)
            send(headerDown,MotionEvent.ACTION_UP,heading.center)
            assertEquals("touching plain glass heading must not dismiss",GlassMenu.FILTER,state.active)
            val outside=Offset(viewport.get().center.x,viewport.get().bottom-180f)
            tap(outside)
            SystemClock.sleep(700)
            assertNull("outside blank area closes filter menu",state.active)
            assertEquals("outside dismiss never clicks the underlying page",0,backgroundClicks.get())
            instrumentation.runOnMainSync{state.open(GlassMenu.FILTER)}
            SystemClock.sleep(700)
            tap(findBounds{it.text?.toString()?.contains("已完成")==true}.center)
            assertEquals("status choice fires once",1,statuses.get())
            tap(findBounds{it.text?.toString()?.contains("数学")==true}.center)
            assertEquals("subject choice fires once",1,subjects.get())
            tap(findBounds{it.text?.toString()?.contains("重置筛选")==true}.center)
            assertEquals("reset action fires once",1,resets.get())
            tap(findBounds{it.contentDescription?.toString()?.contains("关闭筛选")==true}.center)
            SystemClock.sleep(700)
            assertNull("close action still closes menu",state.active)
            instrumentation.runOnMainSync{state.open(GlassMenu.TYPE)}
            SystemClock.sleep(700)
            tap(findBounds{it.text?.toString()?.contains("考试")==true}.center)
            SystemClock.sleep(650)
            assertEquals("type option fires once",1,types.get())
            assertNull("type selection still closes menu",state.active)
            instrumentation.runOnMainSync{state.open(GlassMenu.TYPE)}
            SystemClock.sleep(700)
            tap(outside)
            SystemClock.sleep(700)
            assertNull("outside blank area also closes type menu",state.active)
            assertEquals("dismiss taps remain modal",0,backgroundClicks.get())
        } finally {instrumentation.runOnMainSync{activity.finish()}}
    }
    private fun start():MainActivity {
        // Connect before composing; the first lazy connection can return a null root.
        instrumentation.uiAutomation
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        SystemClock.sleep(1000)
        return activity
    }
    private fun findBounds(predicate:(AccessibilityNodeInfo)->Boolean):Rect {
        var result:Rect?=null
        val observed=mutableListOf<String>()
        fun visit(node:AccessibilityNodeInfo) {
            observed.add("text="+node.text+";description="+node.contentDescription+";visible="+node.isVisibleToUser)
            if(result==null && predicate(node) && node.isVisibleToUser) {
                val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                if(bounds.width()>0&&bounds.height()>0)result=Rect(bounds.left.toFloat(),bounds.top.toFloat(),bounds.right.toFloat(),bounds.bottom.toFloat())
            }
            repeat(node.childCount){index->node.getChild(index)?.let{visit(it);it.recycle()}}
        }
        for (attempt in 0 until 15) {
            observed.clear()
            instrumentation.uiAutomation.rootInActiveWindow?.let{visit(it);it.recycle()}
            if (result != null) break
            SystemClock.sleep(200)
        }
        assertNotNull("menu accessibility target exists: "+observed.joinToString(" | "),result)
        return result!!
    }
    private fun tap(point:Offset) {
        val down=SystemClock.uptimeMillis()
        send(down,MotionEvent.ACTION_DOWN,point);SystemClock.sleep(90)
        send(down,MotionEvent.ACTION_UP,point)
        SystemClock.sleep(160)
    }
    private fun send(down:Long,action:Int,point:Offset) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point.x,point.y,0)
        try{check(instrumentation.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
        instrumentation.waitForIdleSync()
    }
}
