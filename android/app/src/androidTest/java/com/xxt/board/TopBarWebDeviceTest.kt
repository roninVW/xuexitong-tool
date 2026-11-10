package com.xxt.board

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import com.xxt.board.ui.components.GaussianWebFrame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION")
class TopBarWebDeviceTest : InstrumentationTestCase() {
    fun testNativeCaptureScrollInsetAndPageClick() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val frameRef = AtomicReference<GaussianWebFrame>()
        val webRef = AtomicReference<WebView>()
        val loaded = CountDownLatch(1)
        val headerPx = 140
        try {
            // Wait for MainActivity's application graph/setContent before installing this fixture.
            SystemClock.sleep(1500)
            instrumentation.runOnMainSync {
                val web = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
                    }
                }
                val frame = GaussianWebFrame(activity, 0xFFF6F0E2.toInt())
                frame.setPageContent(web, web)
                frame.configure(headerPx, 60f, 0xB3FFFDF7.toInt())
                val root = FrameLayout(activity)
                root.addView(frame, FrameLayout.LayoutParams(-1, -1))
                root.addView(TextView(activity).apply { text = "清晰的顶部标题" }, FrameLayout.LayoutParams(-1, headerPx))
                activity.setContentView(root)
                frameRef.set(frame); webRef.set(web)
                web.loadDataWithBaseURL("https://local.invalid/", """
                    <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="margin:0;background:repeating-linear-gradient(#ed9 0px,#ed9 40px,#69b 40px,#69b 80px)">
                    <button id="tap" style="position:fixed;top:200px;left:40px;width:180px;height:70px"
                    onclick="window.clicked=(window.clicked||0)+1">网页点击验证</button>
                    <div style="height:5000px"></div></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            assertTrue("local WebView loaded", loaded.await(12, TimeUnit.SECONDS))
            SystemClock.sleep(300)
            val frame = frameRef.get(); val web = webRef.get()
            val initialWidth = web.width
            instrumentation.runOnMainSync {
                assertEquals("initial content avoids header", headerPx, frame.contentInset)
                assertTrue("WebView recorded using hardware canvas", frame.captureCount > 0)
                web.scrollTo(0, headerPx * 3)
            }
            SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("actual page passes under header", 0, frame.contentInset)
                assertEquals("scrolling never scales page width", initialWidth, web.width)
                assertEquals("native page fills viewport after consuming inset", frame.height, web.height)
            }
            val point = eval(web, "JSON.stringify((function(){var r=document.getElementById('tap').getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,window.innerWidth]})())")
            val values = org.json.JSONArray(org.json.JSONTokener(point).nextValue() as String)
            val location = IntArray(2)
            instrumentation.runOnMainSync { web.getLocationOnScreen(location) }
            val factor = web.width / values.getDouble(2)
            val x = location[0] + (values.getDouble(0) * factor).toFloat()
            val y = location[1] + (values.getDouble(1) * factor).toFloat()
            val down = SystemClock.uptimeMillis()
            send(down, MotionEvent.ACTION_DOWN, x, y)
            send(down, MotionEvent.ACTION_UP, x, y)
            SystemClock.sleep(150)
            assertEquals("web touch maps to original button after inset change", "1", eval(web, "String(window.clicked||0)").trim('"'))
            instrumentation.runOnMainSync { web.scrollTo(0, 0) }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { assertEquals("return to top restores original inset", headerPx, frame.contentInset) }
        } finally {
            instrumentation.runOnMainSync { webRef.get()?.destroy(); activity.finish() }
        }
    }
    private fun eval(web: WebView, script: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { result.set(it); done.countDown() } }
        assertTrue("JavaScript callback", done.await(5, TimeUnit.SECONDS))
        return result.get()
    }
    private fun send(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        instrumentation.waitForIdleSync()
    }
}
