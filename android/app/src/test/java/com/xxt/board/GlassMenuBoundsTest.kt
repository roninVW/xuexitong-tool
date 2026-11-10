package com.xxt.board

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.xxt.board.ui.liquid.glassMenuBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuBoundsTest {
    private val viewport = Size(1080f, 2400f)

    @Test fun typeMenuAlignsToLeftEntryAndStartsBelowIt() {
        val anchor = Rect(48f, 80f, 270f, 200f)
        val bounds = glassMenuBounds(anchor, viewport, Size(600f, 560f), 40f, 70f, 80f, false)
        assertEquals(anchor.left, bounds.left, 0f)
        assertEquals(220f, bounds.top, 0f)
    }

    @Test fun filterMenuAlignsToRightEntry() {
        val anchor = Rect(800f, 80f, 1030f, 200f)
        val bounds = glassMenuBounds(anchor, viewport, Size(860f, 1100f), 40f, 70f, 80f, true)
        assertEquals(anchor.right, bounds.right, 0f)
        assertEquals(220f, bounds.top, 0f)
    }

    @Test fun menuNearBottomMovesAboveNavigationBar() {
        val bounds = glassMenuBounds(Rect(800f, 2100f, 1030f, 2220f), viewport,
            Size(860f, 1100f), 40f, 70f, 80f, true)
        assertEquals(2320f, bounds.bottom, 0f)
        assertTrue(bounds.top >= 70f)
    }

    @Test fun smallLandscapeViewportKeepsPanelAndAnchorInSafeArea() {
        val bounds = glassMenuBounds(Rect(1100f, 0f, 1200f, 80f), Size(1280f, 720f),
            Size(850f, 540f), 40f, 70f, 80f, true)
        assertTrue(bounds.left >= 40f)
        assertTrue(bounds.right <= 1240f)
        assertTrue(bounds.top >= 70f)
        assertTrue(bounds.bottom <= 640f)
    }

    @Test fun offscreenEntryDoesNotPlacePanelOutsideViewport() {
        val bounds = glassMenuBounds(Rect(-100f, -100f, 120f, 20f), viewport,
            Size(600f, 560f), 40f, 70f, 80f, false)
        assertEquals(40f, bounds.left, 0f)
        assertEquals(70f, bounds.top, 0f)
    }
}
