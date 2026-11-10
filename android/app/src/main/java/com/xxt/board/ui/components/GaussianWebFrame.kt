package com.xxt.board.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout

/** Native equivalent of GaussianTopBar. The source contains only page content;
 * one RenderNode copy gets the shared Gaussian effect, and controls sit above it.
 * No bitmap/screenshot, WebView scale, CSS change or touch interception is involved.
 */
class GaussianWebFrame(context: Context, private val pageColor: Int) : FrameLayout(context) {
    private val source = RecordingPage(context).apply { setBackgroundColor(pageColor) }
    private val blur = BlurCopy(context)
    private var content: View? = null
    private var web: WebView? = null
    private var headerHeight = 0
    var captureCount: Int = 0
        private set
    val contentInset: Int get() = (content?.layoutParams as? LayoutParams)?.topMargin ?: 0

    init {
        setBackgroundColor(pageColor)
        addView(source, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(blur, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        source.onRecord = { captureCount++; blur.invalidate() }
    }

    fun setPageContent(view: View, scrollView: WebView) {
        source.removeAllViews()
        content = view
        web = scrollView
        source.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // A native top inset preserves the initial viewport. Scrolling consumes the inset,
        // making actual WebView pixels pass behind the fixed bar. Width and touch ownership
        // stay with WebView, and the view hierarchy maps events after every layout change.
        scrollView.setOnScrollChangeListener { _, _, _, _, _ -> updateInset() }
        updateInset()
    }

    fun configure(heightPx: Int, radiusPx: Float, tint: Int) {
        if (headerHeight != heightPx) {
            headerHeight = heightPx.coerceAtLeast(0)
            blur.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, headerHeight)
            updateInset()
        }
        blur.configure(radiusPx, tint)
    }

    private fun updateInset() {
        val view = content ?: return
        val params = view.layoutParams as LayoutParams
        val inset = (headerHeight - (web?.scrollY ?: 0).coerceAtLeast(0)).coerceAtLeast(0)
        if (params.topMargin != inset) {
            params.topMargin = inset
            view.layoutParams = params
        }
    }

    private class RecordingPage(context: Context) : FrameLayout(context) {
        val node = if (Build.VERSION.SDK_INT >= 31) RenderNode("TopBarPageSource") else null
        var onRecord: () -> Unit = {}
        override fun draw(canvas: Canvas) {
            val layer = node
            if (layer == null || !canvas.isHardwareAccelerated || width == 0 || height == 0) {
                super.draw(canvas); return
            }
            layer.setPosition(0, 0, width, height)
            val recording = layer.beginRecording(width, height)
            try { super.draw(recording) } finally { layer.endRecording() }
            canvas.drawRenderNode(layer)
            onRecord()
        }
        override fun onDetachedFromWindow() {
            node?.discardDisplayList()
            super.onDetachedFromWindow()
        }
    }

    private inner class BlurCopy(context: Context) : View(context) {
        private val node = if (Build.VERSION.SDK_INT >= 31) RenderNode("TopBarGaussianCopy") else null
        private var radius = Float.NaN
        private var tintColor = pageColor
        init { setWillNotDraw(false); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        fun configure(radiusPx: Float, tint: Int) {
            if (radius != radiusPx) {
                radius = radiusPx
                if (Build.VERSION.SDK_INT >= 31) node?.setRenderEffect(
                    if (radiusPx > 0f) platformTopBarBlur(radiusPx) else null)
            }
            tintColor = tint
            invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            val page = source.node
            val filtered = node
            if (Build.VERSION.SDK_INT >= 31 && radius > 0f && canvas.isHardwareAccelerated &&
                page != null && page.hasDisplayList() && filtered != null && width > 0 && height > 0) {
                // Same window coordinates as the source, with blur support outside the bar.
                val padding = kotlin.math.ceil(radius * 2f).toInt()
                filtered.setPosition(-padding, -padding, width + padding, height + padding)
                val record = filtered.beginRecording(width + padding * 2, height + padding * 2)
                record.translate(padding.toFloat(), padding.toFloat())
                record.drawRenderNode(page)
                filtered.endRecording()
                canvas.save()
                canvas.clipRect(0, 0, width, height)
                canvas.drawRenderNode(filtered)
                canvas.restore()
            }
            canvas.drawColor(tintColor)
        }
        override fun onDetachedFromWindow() {
            node?.discardDisplayList()
            super.onDetachedFromWindow()
        }
    }
}
