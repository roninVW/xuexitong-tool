package com.xxt.board

import com.xxt.board.data.resolveThemeStyle
import com.xxt.board.data.resolveMaterialStyle
import com.xxt.board.ui.liquid.morphGlassBounds
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialSettingsTest {
    @Test fun legacyLiquidSelectionPreservesPaperAndEnablesMaterial() {
        assertEquals("paper",resolveThemeStyle("liquid","paper"))
        assertEquals("liquid",resolveMaterialStyle(null,"liquid"))
    }
    @Test fun explicitDefaultMaterialOverridesLegacyLiquidMarker() {
        assertEquals("default",resolveMaterialStyle("default","liquid"))
    }
    @Test fun materialChoiceDoesNotChangeTheTheme() {
        for(theme in listOf("paper","journal")) {
            assertEquals(theme,resolveThemeStyle(theme,null))
            assertEquals("liquid",resolveMaterialStyle("liquid",theme))
            assertEquals("default",resolveMaterialStyle(null,theme))
        }
    }
    @Test fun unconfiguredAndInvalidPreferencesHaveStableDefaults() {
        assertEquals("journal",resolveThemeStyle(null,null))
        assertEquals("journal",resolveThemeStyle("unknown",null))
        assertEquals("default",resolveMaterialStyle("unknown","paper"))
    }
    @Test fun morphHasExactEntryAndPanelEndpoints() {
        val entry=Rect(800f,80f,1040f,200f)
        val panel=Rect(160f,220f,1040f,1400f)
        assertEquals(entry,morphGlassBounds(entry,panel,0f))
        assertEquals(panel,morphGlassBounds(entry,panel,1f))
        val mid=morphGlassBounds(entry,panel,0.5f)
        assertEquals(Rect(480f,150f,1040f,800f),mid)
    }
    @Test fun reversedProgressRetracesTheSameContourWithoutJumps() {
        val entry=Rect(48f,80f,270f,200f)
        val panel=Rect(48f,220f,660f,880f)
        var previous=entry
        for(i in 1..100) {
            val current=morphGlassBounds(entry,panel,i/100f)
            assertTrue(current.width>=previous.width)
            assertTrue(current.height>=previous.height)
            assertTrue(current.right-previous.right<=4.01f)
            previous=current
        }
        assertEquals(morphGlassBounds(entry,panel,0.3f),morphGlassBounds(entry,panel,1f-0.7f))
    }
}
