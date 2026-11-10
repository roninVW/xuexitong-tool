package com.xxt.board

import android.test.InstrumentationTestCase
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import com.xxt.board.ui.liquid.*
import com.xxt.board.ui.theme.XxtTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Touch injection only; no screenshots, network requests or preference/database changes. */
@Suppress("DEPRECATION")
class LiquidInteractionDeviceTest:InstrumentationTestCase() {
    fun testSliderTrackDragAndSingleToggle() {
        val sliderBounds=AtomicReference(Rect.Zero)
        val toggleBounds=AtomicReference(Rect.Zero)
        val buttonBounds=AtomicReference(Rect.Zero)
        val result=AtomicReference(0.3f)
        val switches=AtomicInteger(0)
        val clicks=AtomicInteger(0)
        val longs=AtomicInteger(0)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync {
            activity.setContent {
                XxtTheme(dark=false) {
                    val background=rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(background)) {drawRect(Color(0xFFE8DACA))}
                        CompositionLocalProvider(LocalLiquidBackdrop provides background) {
                            run {
                                var value by remember{mutableFloatStateOf(0.3f)}
                                var checked by remember{mutableStateOf(false)}
                                Column(Modifier.fillMaxWidth().padding(horizontal=36.dp,vertical=120.dp),verticalArrangement=Arrangement.spacedBy(36.dp)) {
                                    MaterialSlider(value,{value=it;result.set(it)},modifier=Modifier.onGloballyPositioned{sliderBounds.set(it.boundsInWindow())})
                                    MaterialSwitch(checked,{checked=it;switches.incrementAndGet()},Modifier.onGloballyPositioned{toggleBounds.set(it.boundsInWindow())})
                                    MaterialButton({clicks.incrementAndGet()},Modifier.onGloballyPositioned{buttonBounds.set(it.boundsInWindow())},onLongClick={longs.incrementAndGet()}){Text("按压测试")}
                                }
                            }
                        }
                    }
                }
            }
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(600)
        fun gesture(from:Offset,to:Offset,duration:Long=250) {
            val down=SystemClock.uptimeMillis()
            fun send(action:Int,point:Offset,time:Long) {
                val event=MotionEvent.obtain(down,time,action,point.x,point.y,0)
                check(instrumentation.uiAutomation.injectInputEvent(event,true));event.recycle()
            }
            send(MotionEvent.ACTION_DOWN,from,down)
            SystemClock.sleep(80)
            if(from!=to) for(i in 1..8) {
                val point=Offset(from.x+(to.x-from.x)*i/8,from.y+(to.y-from.y)*i/8)
                send(MotionEvent.ACTION_MOVE,point,SystemClock.uptimeMillis());SystemClock.sleep(duration/8)
            } else SystemClock.sleep(duration)
            send(MotionEvent.ACTION_UP,to,SystemClock.uptimeMillis())
            instrumentation.waitForIdleSync();SystemClock.sleep(120)
        }
        val slider=sliderBounds.get()
        assertTrue("slider laid out",slider.width>200f)
        gesture(Offset(slider.left+slider.width*0.30f,slider.center.y),Offset(slider.left+slider.width*0.82f,slider.center.y))
        assertTrue("direct thumb drag updates value",result.get()>0.75f)
        val adjusted=result.get()
        SystemClock.sleep(1100)
        assertEquals("spring feedback must not restore the previous slider value",adjusted,result.get(),0f)
        gesture(Offset(slider.left+slider.width*0.12f,slider.center.y),Offset(slider.left+slider.width*0.12f,slider.center.y),40)
        assertTrue("track tap positions value",result.get()<0.20f)
        val toggle=toggleBounds.get()
        gesture(toggle.center,toggle.center,100)
        assertEquals("one click means one toggle",1,switches.get())
        gesture(Offset(toggle.right-10,toggle.center.y),Offset(toggle.left+10,toggle.center.y))
        assertEquals("one drag means one toggle",2,switches.get())
        val button=buttonBounds.get()
        gesture(button.center,button.center,60)
        assertEquals(1,clicks.get())
        gesture(button.center,button.center,750)
        assertEquals("long press cannot also click",1,clicks.get())
        assertEquals(1,longs.get())
        instrumentation.runOnMainSync {activity.finish()}
    }
}
