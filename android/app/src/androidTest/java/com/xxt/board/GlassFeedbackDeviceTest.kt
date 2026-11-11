package com.xxt.board

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
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
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import com.xxt.board.ui.AppTab
import com.xxt.board.ui.liquid.*
import com.xxt.board.ui.liquid.dockinitial.DampedDragAnimation
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight
import com.xxt.board.ui.theme.XxtTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual frame-clock and pointer cancellation checks; no screenshots or user settings edits. */
@Suppress("DEPRECATION")
class GlassFeedbackDeviceTest : InstrumentationTestCase() {
    fun testReleaseCancellationAndRepeatedPressRestoreOnlyFeedback() {
        val dragRef=AtomicReference<DampedDragAnimation>()
        val lightRef=AtomicReference<InteractiveHighlight>()
        val hostBounds=AtomicReference(Rect.Zero)
        val dockBounds=AtomicReference(Rect.Zero)
        val tabEvents=AtomicInteger(0)
        val menuMotions=AtomicReference<List<MenuDragFeedback>>()
        var observerEnabled by mutableStateOf(true)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    XxtTheme(dark=false) {
                        val scope=rememberCoroutineScope()
                        val light=remember(scope){InteractiveHighlight(scope)}
                        val drag=remember(scope){DampedDragAnimation(scope,0f,0f..3f,0.001f,1f,1.35f,{},{},{_,_->})}
                        val menus=remember(scope){List(2){MenuDragFeedback(scope,24f)}}
                        SideEffect {dragRef.set(drag);lightRef.set(light);menuMotions.set(menus)}
                        val background=rememberLayerBackdrop()
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(background)) {
                                drawRect(Color(0xFFE8DACA))
                                drawCircle(Color(0xFF739DD2),radius=size.width/3,center=Offset(size.width/2,size.height/2))
                            }
                            CompositionLocalProvider(LocalLiquidBackdrop provides background) {
                                run {
                                    var selected by remember{mutableStateOf(AppTab.BOARD)}
                                    Column(Modifier.padding(horizontal=32.dp,vertical=100.dp),verticalArrangement=Arrangement.spacedBy(40.dp)) {
                                        Box(Modifier.fillMaxWidth().height(80.dp).onGloballyPositioned{hostBounds.set(it.boundsInWindow())}
                                            .then(light.modifier).then(if(observerEnabled)light.gestureModifier else Modifier)) {Text("触摸取消测试")}
                                        LiquidDock(selected,{selected=it;tabEvents.incrementAndGet()},background,
                                            Modifier.onGloballyPositioned{dockBounds.set(it.boundsInWindow())})
                                    }
                                }
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync();SystemClock.sleep(350)
            val drag=dragRef.get()
            instrumentation.runOnMainSync { drag.press() }
            SystemClock.sleep(600)
            assertEquals(1.35f,drag.scaleX,0.003f)
            assertEquals(drag.scaleX,drag.scaleY,0f)
            instrumentation.runOnMainSync {
                drag.press();drag.updateValue(2.6f)
                assertEquals("pointer value must update in the same event",2.6f,drag.value,0f)
                drag.updateValue(0.4f)
                assertEquals("reverse direction must not await interpolation",0.4f,drag.value,0f)
                drag.updateValue(2.6f)
            }
            SystemClock.sleep(70)
            instrumentation.runOnMainSync {drag.animateToValue(3f);drag.release()}
            SystemClock.sleep(1200)
            assertRestored(drag)
            assertEquals("release must preserve selected value",3f,drag.value,0.005f)
            repeat(8) { index ->
                instrumentation.runOnMainSync {drag.press();drag.updateValue(if(index%2==0)0.5f else 2.5f)}
                SystemClock.sleep(25)
                instrumentation.runOnMainSync {drag.release()}
                SystemClock.sleep(25)
            }
            SystemClock.sleep(1200)
            assertRestored(drag)
            assertEquals("rapid release must not revert the user value",2.5f,drag.value,0.005f)
            instrumentation.runOnMainSync { drag.press();drag.updateValue(0.3f) }
            for(index in 1..8) {
                SystemClock.sleep(16)
                instrumentation.runOnMainSync { drag.updateValue(0.3f+index*0.28f) }
            }
            assertTrue("real drag velocity drives liquid stretch",drag.velocity>0.1f && drag.scaleX>drag.scaleY)
            val releaseVelocity=drag.velocity
            instrumentation.runOnMainSync {drag.animateToValue(3f);drag.release()}
            assertEquals("release retains motion without a zero-frame snap",releaseVelocity,drag.velocity,0.001f)
            var lensOvershot=false
            repeat(40) { SystemClock.sleep(16);if(drag.velocity< -0.003f)lensOvershot=true }
            assertTrue("released lens has a bounded spring rebound",lensOvershot)
            SystemClock.sleep(700)
            assertRestored(drag)
            assertEquals(3f,drag.value,0.005f)
            val optical=InteractiveHighlight(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
                pressure={drag.pressProgress})
            instrumentation.runOnMainSync{drag.press()}
            repeat(8) {step ->
                SystemClock.sleep(16)
                instrumentation.runOnMainSync{drag.updateValue(3f,SystemClock.uptimeMillis(),(step+1)*0.06f)}
            }
            assertTrue("vertical drag has directional liquid warp",drag.scaleY>drag.scaleX)
            assertTrue("vertical drag follows without position interpolation",drag.crossOffset>0.02f)
            instrumentation.runOnMainSync{drag.animateToValue(3f);drag.release()}
            repeat(45) {
                SystemClock.sleep(16)
                instrumentation.runOnMainSync {
                    assertEquals("optics use the same enlargement spring",drag.pressProgress,optical.pressProgress,0f)
                    assertEquals("pressure derived from actual Lens size",((drag.scale-1f)/0.35f).coerceIn(0f,1f),drag.pressProgress,0.0001f)
                }
            }
            SystemClock.sleep(600)
            assertRestored(drag)
            assertEquals("cross-axis spring leaves no offset",0f,drag.crossOffset,0.003f)
            val menus=menuMotions.get()
            menus.forEachIndexed { index, motion ->
                val start=SystemClock.uptimeMillis()
                instrumentation.runOnMainSync {motion.begin(Offset.Zero,start)}
                repeat(6) { step ->
                    SystemClock.sleep(16)
                    instrumentation.runOnMainSync {motion.move(Offset((step+1)*18f,(step+1)*4f),SystemClock.uptimeMillis())}
                }
                assertTrue("menu displacement follows pointer",motion.offset.x>8f)
                assertEquals("the other menu has independent motion",0f,menus[1-index].offset.x,0.01f)
                val heldOffset=motion.offset
                instrumentation.runOnMainSync {motion.finish()}
                assertEquals("menu release has no sudden position reset",heldOffset.x,motion.offset.x,0.01f)
                var menuOvershot=false
                repeat(40) {SystemClock.sleep(16);if(motion.offset.x< -0.03f)menuOvershot=true}
                assertTrue("menu uses a gentle spring overshoot",menuOvershot)
                SystemClock.sleep(350)
                assertEquals(0f,motion.offset.x,0.05f)
                assertEquals(0f,motion.offset.y,0.05f)
            }
            val host=hostBounds.get()
            val down=SystemClock.uptimeMillis()
            send(down,MotionEvent.ACTION_DOWN,host.center)
            SystemClock.sleep(180)
            send(down,MotionEvent.ACTION_MOVE,host.center+Offset(90f,20f))
            SystemClock.sleep(90)
            assertTrue("press displacement follows pointer",lightRef.get().offset.x>30f)
            send(down,MotionEvent.ACTION_UP,host.center+Offset(90f,20f))
            SystemClock.sleep(1100)
            assertHighlightRestored(lightRef.get())
            val secondDown=SystemClock.uptimeMillis()
            send(secondDown,MotionEvent.ACTION_DOWN,host.center)
            SystemClock.sleep(160)
            send(secondDown,MotionEvent.ACTION_MOVE,host.center+Offset(60f,10f))
            // Removing the pointer modifier cancels its coroutine while the state owner survives.
            instrumentation.runOnMainSync {observerEnabled=false}
            SystemClock.sleep(1100)
            assertHighlightRestored(lightRef.get())
            send(secondDown,MotionEvent.ACTION_CANCEL,host.center)
            val dock=dockBounds.get()
            val dockDown=SystemClock.uptimeMillis()
            send(dockDown,MotionEvent.ACTION_DOWN,Offset(dock.left+dock.width/8,dock.center.y))
            SystemClock.sleep(100)
            send(dockDown,MotionEvent.ACTION_MOVE,Offset(dock.right-dock.width/8,dock.center.y))
            SystemClock.sleep(100)
            send(dockDown,MotionEvent.ACTION_UP,Offset(dock.right-dock.width/8,dock.center.y))
            SystemClock.sleep(1000)
            assertEquals("Dock drag selects once",1,tabEvents.get())
        } finally {instrumentation.runOnMainSync {activity.finish()}}
    }
    private fun assertRestored(drag:DampedDragAnimation) {
        assertEquals("pressure released",0f,drag.pressProgress,0.003f)
        assertEquals("horizontal scale restored",1f,drag.scaleX,0.005f)
        assertEquals("vertical scale restored",1f,drag.scaleY,0.005f)
        assertEquals("velocity deformation cleared",0f,drag.velocity,0.02f)
    }
    private fun assertHighlightRestored(light:InteractiveHighlight) {
        assertEquals(0f,light.pressProgress,0.003f)
        assertEquals(0f,light.offset.x,0.01f)
        assertEquals(0f,light.offset.y,0.01f)
    }
    private fun send(down:Long,action:Int,point:Offset) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point.x,point.y,0)
        try {check(instrumentation.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
        instrumentation.waitForIdleSync()
    }
}
