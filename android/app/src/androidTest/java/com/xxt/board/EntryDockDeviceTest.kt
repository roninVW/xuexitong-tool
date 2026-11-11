package com.xxt.board

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.MainDockTabs
import com.xxt.board.ui.components.PaperDock
import com.xxt.board.ui.liquid.*
import com.xxt.board.ui.theme.XxtTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Production entries and both Dock materials, using real emulator touch events. */
@Suppress("DEPRECATION")
class EntryDockDeviceTest : InstrumentationTestCase() {
    fun testEntriesFollowAndReturnIndependentlyAndStillOpenMenus() {
        val activity = start()
        val state = LiquidBoardMenuState()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    XxtTheme(dark = false) {
                        val background = rememberLayerBackdrop()
                        SideEffect {
                            state.model = GlassBoardModel(
                                listOf(GlassMenuChoice(null,"全部",1,true)),
                                listOf(GlassMenuChoice("todo","未完成",1,true)),
                                listOf(GlassMenuChoice(null,"全部科目",1,true)),1,{},{},{},{})
                        }
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(background)) { drawRect(Color(0xFF9BBEE4)) }
                            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=80.dp),
                                horizontalArrangement=Arrangement.SpaceBetween) {
                                LiquidMenuEntry(background,state,GlassMenu.TYPE) { Text("入口作业") }
                                LiquidMenuEntry(background,state,GlassMenu.FILTER,selected=false) { Text("入口筛选") }
                            }
                            LiquidBoardMenus(state,background)
                        }
                    }
                }
            }
            SystemClock.sleep(500)
            listOf("入口作业" to GlassMenu.TYPE,"入口筛选" to GlassMenu.FILTER).forEach { (label,menu) ->
                val other = if (menu==GlassMenu.TYPE) "入口筛选" else "入口作业"
                val original=find(label);val otherOriginal=find(other)
                val down=SystemClock.uptimeMillis()
                send(down,MotionEvent.ACTION_DOWN,original.center)
                SystemClock.sleep(160)
                repeat(6) { step ->
                    send(down,MotionEvent.ACTION_MOVE,original.center+Offset((step+1)*12f,(step+1)*3f))
                    SystemClock.sleep(16)
                }
                val moved=find(label)
                assertTrue("entry follows finger",moved.center.x-original.center.x>3f)
                assertTrue("entry feedback remains compact",moved.center.x-original.center.x<32f)
                assertTrue("entry has modest pressure/drag deformation",moved.width>original.width+1f)
                assertEquals("other entry never inherits feedback",otherOriginal.center.x,find(other).center.x,1f)
                send(down,MotionEvent.ACTION_CANCEL,original.center+Offset(72f,18f))
                SystemClock.sleep(1000)
                val restored=find(label)
                assertEquals("entry returns after cancellation",original.center.x,restored.center.x,1.5f)
                assertEquals("deformation returns",original.width,restored.width,2f)
                assertNull("drag cancellation cannot open a menu",state.active)
                tap(restored.center)
                SystemClock.sleep(650)
                assertEquals("entry click still opens the correct panel",menu,state.active)
                instrumentation.runOnMainSync {state.dismiss()}
                SystemClock.sleep(800)
                assertEquals("fusion returns to logical entry anchor",original.center.x,find(label).center.x,1.5f)
            }
        } finally {instrumentation.runOnMainSync{activity.finish()}}
    }

    fun testThreeReflowedDockSlotsMatchClicksDragsAndRtl() {
        val activity=start()
        val dock=AtomicReference(Rect.Zero)
        val selected=AtomicReference(AppTab.BOARD)
        val events=AtomicInteger()
        var liquid by mutableStateOf(true)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    XxtTheme(dark=false) {
                        val background=rememberLayerBackdrop()
                        var current by remember{mutableStateOf(AppTab.BOARD)}
                        val choose:(AppTab)->Unit={current=it;selected.set(it);events.incrementAndGet()}
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            Box(Modifier.fillMaxSize()) {
                                Canvas(Modifier.fillMaxSize().layerBackdrop(background)){drawRect(Color(0xFF9BBEE4))}
                                val modifier=Modifier.padding(horizontal=24.dp,vertical=140.dp)
                                    .onGloballyPositioned{dock.set(it.boundsInWindow())}
                                if(liquid) LiquidDock(current,choose,background,modifier)
                                else PaperDock(current,choose,0,modifier)
                            }
                        }
                    }
                }
            }
            assertEquals(listOf(AppTab.BOARD,AppTab.STATS,AppTab.SETTINGS),MainDockTabs)
            for (material in listOf(true,false)) {
                for (rtl in listOf(false,true)) {
                    instrumentation.runOnMainSync {liquid=material;direction=if(rtl)LayoutDirection.Rtl else LayoutDirection.Ltr}
                    SystemClock.sleep(600)
                    assertFalse("schedule entry absent from accessibility",texts().any{it.contains("课表")})
                    val bounds=dock.get()
                    val inset=if(material) 4f*activity.resources.displayMetrics.density else 0f
                    val cell=(bounds.width-2f*inset)/3f
                    for(index in 0..2) {
                        val physical=if(rtl)2-index else index
                        // Center plus both sides of every adjusted cell.
                        for(fraction in listOf(0.5f,0.12f,0.88f)) {
                            tap(Offset(bounds.left+inset+(physical+fraction)*cell,bounds.center.y))
                            assertEquals("touch maps to the reflowed slot",MainDockTabs[index],selected.get())
                        }
                    }
                    if(material) {
                        val first=Offset(bounds.left+inset+(if(rtl)2.5f else 0.5f)*cell,bounds.center.y)
                        val last=Offset(bounds.left+inset+(if(rtl)0.5f else 2.5f)*cell,bounds.center.y)
                        tap(first)
                        val before=events.get()
                        val down=SystemClock.uptimeMillis()
                        send(down,MotionEvent.ACTION_DOWN,first)
                        repeat(12) {step ->
                            send(down,MotionEvent.ACTION_MOVE,first+(last-first)*((step+1)/12f))
                            SystemClock.sleep(16)
                        }
                        send(down,MotionEvent.ACTION_UP,last)
                        SystemClock.sleep(800)
                        assertEquals("drag settles on settings using visible index",AppTab.SETTINGS,selected.get())
                        assertEquals("one selection for one drag",before+1,events.get())
                    }
                }
            }
        } finally {instrumentation.runOnMainSync{activity.finish()}}
    }

    private fun start():MainActivity {
        instrumentation.uiAutomation
        return (instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity).also{SystemClock.sleep(600)}
    }
    private fun texts():List<String> {
        val result=mutableListOf<String>()
        visit { node -> if(node.isVisibleToUser)result.add(node.text?.toString().orEmpty()+node.contentDescription?.toString().orEmpty()) }
        return result
    }
    private fun find(text:String):Rect {
        var result:Rect?=null
        visit {node -> if(result==null&&node.isVisibleToUser&&node.text?.toString()==text) {
            val rect=android.graphics.Rect();node.getBoundsInScreen(rect)
            result=Rect(rect.left.toFloat(),rect.top.toFloat(),rect.right.toFloat(),rect.bottom.toFloat())
        }}
        assertNotNull("entry accessibility text exists: "+text,result)
        return result!!
    }
    private fun visit(action:(AccessibilityNodeInfo)->Unit) {
        fun walk(node:AccessibilityNodeInfo) {
            action(node)
            repeat(node.childCount){node.getChild(it)?.let{child->walk(child);child.recycle()}}
        }
        instrumentation.uiAutomation.clearCache()
        instrumentation.uiAutomation.rootInActiveWindow?.let{walk(it);it.recycle()}
    }
    private fun tap(point:Offset) {
        val down=SystemClock.uptimeMillis()
        send(down,MotionEvent.ACTION_DOWN,point);SystemClock.sleep(80)
        send(down,MotionEvent.ACTION_UP,point);SystemClock.sleep(180)
    }
    private fun send(down:Long,action:Int,point:Offset) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point.x,point.y,0)
        try{check(instrumentation.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
        instrumentation.waitForIdleSync()
    }
}
