package com.xxt.board

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xxt.board.domain.UpdateChecker
import com.xxt.board.ui.liquid.LocalLiquidBackdrop
import com.xxt.board.ui.liquid.LocalLiquidMaterial
import com.xxt.board.ui.liquid.LiquidMaterial
import com.xxt.board.ui.settings.UpdateDialogs
import com.xxt.board.ui.settings.UpdateUiState
import com.xxt.board.ui.theme.XxtTheme
import java.io.File

/** Real Release data + real Compose/dialog animation, without login or saved settings. */
@Suppress("DEPRECATION")
class UpdateDialogDeviceTest : InstrumentationTestCase() {
    fun testRealReleaseLiquidAnimationAndDefaultDialog() {
        val release = UpdateChecker.fetchLatestRelease()
        val offersUpdate = UpdateChecker.compareVersions(BuildConfig.VERSION_NAME, release.version) > 0
        val expectedTitle = if (offersUpdate) "发现新版本" else "当前已是最新版本"
        val initialState = if (offersUpdate) UpdateUiState.Available(release) else UpdateUiState.UpToDate
        var state by mutableStateOf(initialState)
        var liquid by mutableStateOf(true)
        val activity = start()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    XxtTheme(dark = false) {
                        val background = rememberLayerBackdrop {
                            drawRect(Color(0xFFF6F0E2))
                            drawContent()
                        }
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(background)) {
                                drawCircle(Color(0xFF8EB6C9), size.minDimension * 0.42f)
                                drawCircle(Color(0xFFD6A66F), size.minDimension * 0.30f)
                            }
                            CompositionLocalProvider(
                                LocalLiquidBackdrop provides background.takeIf { liquid },
                                LocalLiquidMaterial provides LiquidMaterial(),
                            ) {
                                UpdateDialogs(
                                    state = state,
                                    backdrop = background.takeIf { liquid },
                                    onStateChange = { state = it },
                                    onDownload = {},
                                    onCancelDownload = {},
                                    onRetry = {},
                                    onInstall = { _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(70)
            saveScreenshot("release-liquid-entering.png")
            SystemClock.sleep(650)
            assertEquals(expectedTitle, findText(expectedTitle, attempts = 20).text)
            if (offersUpdate) {
                assertNotNull(
                    findBoundsOrNull(attempts = 5) { it.text?.toString()?.contains(release.tagName) == true },
                )
                assertNotNull(findBoundsOrNull(attempts = 5) { it.text?.toString() == "更新日志" })
            }
            saveScreenshot("release-liquid-final.png")

            instrumentation.runOnMainSync { state = UpdateUiState.Idle }
            SystemClock.sleep(70)
            saveScreenshot("release-liquid-exiting.png")
            SystemClock.sleep(500)

            instrumentation.runOnMainSync {
                liquid = false
                state = initialState
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(450)
            assertEquals(expectedTitle, findText(expectedTitle, attempts = 20).text)
            saveScreenshot("release-default-final.png")
            instrumentation.runOnMainSync { state = UpdateUiState.Idle }
            SystemClock.sleep(450)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun start(): MainActivity {
        instrumentation.uiAutomation
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        SystemClock.sleep(700)
        return activity
    }

    private data class Found(val text: String, val bounds: Rect)

    private fun findText(text: String, attempts: Int): Found {
        val bounds = findBoundsOrNull(attempts = attempts) { it.text?.toString() == text }
        assertNotNull("Expected visible text: $text", bounds)
        return Found(text, bounds!!)
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "update-verification")
        check(directory.exists() || directory.mkdirs())
        File(directory, name).outputStream().buffered().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun findBoundsOrNull(
        attempts: Int = 1,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): Rect? {
        repeat(attempts) {
            var result: Rect? = null
            fun visit(node: AccessibilityNodeInfo) {
                if (result == null && predicate(node) && node.isVisibleToUser) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        result = Rect(
                            bounds.left.toFloat(), bounds.top.toFloat(),
                            bounds.right.toFloat(), bounds.bottom.toFloat(),
                        )
                    }
                }
                repeat(node.childCount) { index ->
                    node.getChild(index)?.let { child ->
                        visit(child)
                        child.recycle()
                    }
                }
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                visit(root)
                root.recycle()
            }
            result?.let { return it }
            SystemClock.sleep(120)
        }
        return null
    }
}
