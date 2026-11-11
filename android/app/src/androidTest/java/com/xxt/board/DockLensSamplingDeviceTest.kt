package com.xxt.board

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.test.InstrumentationTestCase
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.xxt.board.ui.liquid.DockBackgroundLayer
import com.xxt.board.ui.liquid.DockEdgeGlowLayer
import com.xxt.board.ui.liquid.DockLensBackdrop
import com.xxt.board.ui.liquid.LiquidMaterial
import com.xxt.board.ui.liquid.glassPressLens
import com.xxt.board.ui.theme.XxtTheme
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.roundToInt

/** GPU buffer assertions only: no screen screenshots are saved or inspected. */
@Suppress("DEPRECATION")
class DockLensSamplingDeviceTest : InstrumentationTestCase() {
    fun testActualDockBackgroundVisibilityCompressionAndRgbDispersionOnGpu() {
        var mode by mutableIntStateOf(0)
        val output = AtomicReference<GraphicsLayer>()
        val dockOutput = AtomicReference<GraphicsLayer>()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    XxtTheme(dark = false) {
                        val page = rememberLayerBackdrop()
                        val dock = rememberLayerBackdrop()
                        val glow = rememberLayerBackdrop()
                        val scene = remember(page, dock, glow) {
                            DockLensBackdrop(page, dock, { if (mode == 0) 0f else 1f },glow)
                        }
                        val layer = rememberGraphicsLayer()
                        SideEffect { output.set(layer); dockOutput.set(dock.graphicsLayer) }
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(page)) {
                                drawRect(Color.White)
                                val stripe = 20.dp.toPx()
                                for (index in 0..(size.width / stripe).toInt()) {
                                    if (index % 2 == 1) drawRect(Color.Black,
                                        Offset(index * stripe, 0f), Size(stripe, size.height))
                                }
                            }
                            // Use the production capture, not a transparent or flat placeholder.
                            DockBackgroundLayer(page, dock, Modifier.offset(20.dp, 180.dp).size(320.dp, 80.dp))
                            DockEdgeGlowLayer(glow,{if(mode>=4)1f else 0f},Modifier.offset(20.dp,180.dp).size(320.dp,80.dp))
                            Box(Modifier.offset(80.dp, 165.dp).size(200.dp, 110.dp)
                                .drawWithContent {
                                    layer.record(this, layoutDirection, size.toIntSize()) {
                                        this@drawWithContent.draw(
                                            density = drawContext.density,
                                            layoutDirection = drawContext.layoutDirection,
                                            canvas = drawContext.canvas,
                                            size = drawContext.size,
                                            graphicsLayer = drawContext.graphicsLayer,
                                        ) { this@drawWithContent.drawContent() }
                                    }
                                    drawLayer(layer)
                                }
                                .drawBackdrop(scene, { RoundedCornerShape(50) }, effects = {
                                    if (mode == 2 || mode == 3 || mode == 5) glassPressLens(LiquidMaterial(), 1f,
                                        restHeight = 5.dp.toPx(), restAmount = 5.dp.toPx(),
                                        chromaticAberration = mode == 3 || mode == 5)
                                }, highlight = null, shadow = null))
                        }
                    }
                }
            }
            fun frame(next: Int): PixelMap {
                instrumentation.runOnMainSync { mode = next }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(250)
                return runBlocking { output.get().toImageBitmap().toPixelMap() }
            }
            val rest = frame(0)
            val compressed = frame(1)
            val actualDock = runBlocking { dockOutput.get().toImageBitmap().toPixelMap() }
            assertTrue("actual Dock capture is opaque", actualDock[actualDock.width / 2, actualDock.height / 2].alpha > 0.98f)
            assertTrue("Lens contains the captured Dock", compressed[compressed.width / 2, compressed.height / 2].alpha > 0.98f)
            assertTrue("revealed Lens margins still contain the page", compressed[compressed.width / 2, compressed.height / 20].alpha > 0.98f)
            val textureRatio = stripePeriod(compressed) / stripePeriod(rest)
            val heightRatio = dockFootprintHeight(compressed).toFloat() / dockFootprintHeight(rest)
            assertTrue("Dock texture must shrink to 80%, actual=" + textureRatio, textureRatio in 0.77f..0.83f)
            assertTrue("the actual Dock footprint must also shrink to 80%, actual=" + heightRatio, heightRatio in 0.77f..0.83f)

            // Compare the rendered Lens with the actual captured Dock image. A
            // scaled page plus unscaled tint cannot satisfy this image comparison.
            var sampleError = 0f
            var samples = 0
            for (x in compressed.width / 5 until compressed.width * 4 / 5) {
                val sourceX = (actualDock.width / 2f + (x - compressed.width / 2f) / 0.8f)
                    .roundToInt().coerceIn(0, actualDock.width - 1)
                sampleError += abs(compressed[x, compressed.height / 2].red - actualDock[sourceX, actualDock.height / 2].red)
                samples++
            }
            sampleError /= samples
            assertTrue("Lens must show the real scaled Dock pixels, error=" + sampleError, sampleError < 0.025f)
            val y = rest.height / 12
            for (x in rest.width * 2 / 5 until rest.width * 3 / 5) {
                assertEquals("page outside Dock stays unchanged", rest[x, y].red, compressed[x, y].red, 0.01f)
            }
            val refracted = frame(2)
            val dispersed = frame(3)
            var rgbPixels = 0
            for (py in 0 until dispersed.height) for (px in 0 until dispersed.width) {
                val pixel = dispersed[px, py]
                val reference = refracted[px, py]
                val spread = maxOf(pixel.red, pixel.green, pixel.blue) - minOf(pixel.red, pixel.green, pixel.blue)
                val referenceSpread = maxOf(reference.red, reference.green, reference.blue) - minOf(reference.red, reference.green, reference.blue)
                if (pixel.alpha > 0.98f && spread - referenceSpread > 0.015f) rgbPixels++
            }
            assertTrue("official per-channel sampling remains active", rgbPixels > 40)
            val glowing=frame(4)
            var litPixels=0
            for(py in 0 until glowing.height)for(px in 0 until glowing.width) {
                if(glowing[px,py].red-compressed[px,py].red>0.02f)litPixels++
            }
            assertTrue("scaled Dock edge has visible official light",litPixels>100)
            for(x in glowing.width/5 until glowing.width*4/5) {
                assertEquals("edge light does not wash out the interior",compressed[x,compressed.height/2].red,glowing[x,glowing.height/2].red,0.005f)
            }
            val refractedGlow=frame(5)
            var refractedLitPixels=0
            for(py in 0 until refractedGlow.height)for(px in 0 until refractedGlow.width) {
                if(refractedGlow[px,py].red-dispersed[px,py].red>0.02f)refractedLitPixels++
            }
            assertTrue("Dock edge light survives actual RGB refraction",refractedLitPixels>100)
            for(x in refractedGlow.width/5 until refractedGlow.width*4/5) {
                assertEquals("refracted edge light stays away from foreground interior",dispersed[x,dispersed.height/2].red,refractedGlow[x,refractedGlow.height/2].red,0.005f)
            }
            val restored = frame(0)
            assertEquals("release restores Dock footprint", dockFootprintHeight(rest), dockFootprintHeight(restored))
            for (x in rest.width / 5 until rest.width * 4 / 5) {
                assertEquals("release restores actual Dock pixels", rest[x, rest.height / 2].red, restored[x, restored.height / 2].red, 0.005f)
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("dock_lens_gpu_metrics", "textureScale=" + textureRatio + ", dockHeightScale=" + heightRatio + ", actualDockPixelError=" + sampleError + ", rgbEdgePixels=" + rgbPixels + ", litEdgePixels=" + litPixels + ", refractedLitEdgePixels=" + refractedLitPixels)
            })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun dockFootprintHeight(pixels: PixelMap): Int {
        // The original warm Dock surface marks its true footprint; the page's
        // black/white elements have equal RGB channels. No artificial marker is drawn.
        val ys = (0 until pixels.height).filter { y ->
            val pixel = pixels[pixels.width / 2, y]
            pixel.red - pixel.blue > 0.005f
        }
        assertTrue("real Dock background must be visible through Lens", ys.size > pixels.height / 3)
        return ys.last() - ys.first() + 1
    }

    private fun stripePeriod(pixels: PixelMap): Float {
        val y = pixels.height / 2
        val xs = pixels.width / 10 until pixels.width * 9 / 10
        val minimum = xs.minOf { pixels[it, y].red }
        val maximum = xs.maxOf { pixels[it, y].red }
        assertTrue("real Dock retains page texture", maximum - minimum > 0.08f)
        val threshold = (minimum + maximum) / 2f
        val edges = mutableListOf<Int>()
        var previous = pixels[xs.first, y].red > threshold
        for (x in xs.first + 1..xs.last) {
            val white = pixels[x, y].red > threshold
            if (white != previous) { edges += x; previous = white }
        }
        assertTrue("enough actual background features entered the Lens", edges.size > 5)
        return edges.zipWithNext { a, b -> (b - a).toFloat() }.average().toFloat()
    }
}
