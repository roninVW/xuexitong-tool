package com.xxt.board

import com.xxt.board.ui.liquid.sliderValueAt
import org.junit.Assert.*
import org.junit.Test

class GlassControlMathTest {
    @Test fun sliderEndpointsAndTrackPositioningRespectRange() {
        assertEquals(20f,sliderValueAt(-1f,20f..60f,0),0f)
        assertEquals(60f,sliderValueAt(2f,20f..60f,0),0f)
        assertEquals(40f,sliderValueAt(0.5f,20f..60f,0),0f)
    }
    @Test fun discreteSliderRoundsToTheSameIntervalsAsM3() {
        assertEquals(4f,sliderValueAt(0.11f,0f..40f,19),0f)
        assertEquals(40f,sliderValueAt(0.999f,0f..40f,19),0f)
    }
}
