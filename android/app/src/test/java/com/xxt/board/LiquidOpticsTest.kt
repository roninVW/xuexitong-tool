package com.xxt.board

import com.xxt.board.ui.liquid.LiquidMaterial
import com.xxt.board.ui.board.fitsInlineQuote
import org.junit.Assert.*
import org.junit.Test

class LiquidOpticsTest {
    @Test fun opticalParametersAreIndependent() {
        val material=LiquidMaterial()
        assertEquals(material.refractionScale,material.copy(transparency=0.9f).refractionScale,0f)
        assertEquals(material.surfaceAlpha(0.45f),material.copy(refraction=1.8f).surfaceAlpha(0.45f),0f)
        assertEquals(0.45f,material.surfaceAlpha(0.45f),0f)
        assertEquals(0f,material.copy(refraction=0f).refractionScale,0f)
        assertEquals(0f,material.copy(transparency=1f).surfaceAlpha(0.45f),0f)
    }
    @Test fun quotePlacementDependsOnMeasuredContentSpace() {
        assertFalse(fitsInlineQuote(false,600f,100))
        assertFalse(fitsInlineQuote(true,99f,100))
        assertTrue(fitsInlineQuote(true,100f,100))
        assertFalse(fitsInlineQuote(true,100f,180))
    }
}
