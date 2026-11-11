package com.xxt.board

import com.xxt.board.ui.liquid.dockIndicatorBounds
import com.xxt.board.ui.liquid.dockLensBackgroundScale
import com.xxt.board.ui.liquid.DockLensPressedScale
import com.xxt.board.ui.liquid.glassPressBackdropScale
import com.xxt.board.ui.liquid.sliderValueAt
import com.xxt.board.ui.liquid.dockinitial.dockLensStretch
import com.xxt.board.ui.liquid.dockinitial.dockLensLean
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class GlassControlMathTest {
    @Test fun lensMotionPreservesBaseAreaAndUsesOneMovingRect() {
        val base=DockLensPressedScale
        val stretch=dockLensStretch(6f)
        val moving=dockIndicatorBounds(66f,4f,268f,56f,1f,false,base,
            scaleX=base*stretch,scaleY=base/stretch,offset=Offset(268f*dockLensLean(6f),0f))
        val rest=dockIndicatorBounds(66f,4f,268f,56f,1f,false,base)
        assertTrue(moving.width>rest.width && moving.height<rest.height)
        assertEquals(rest.width*rest.height,moving.width*moving.height,0.01f)
        assertEquals(rest.center.x+268f*dockLensLean(6f),moving.center.x,0.001f)
        assertEquals(dockLensStretch(6f),dockLensStretch(-6f),0f)
        assertEquals(-dockLensLean(6f),dockLensLean(-6f),0f)
        assertEquals(1f,dockLensStretch(0f),0f)
        assertEquals(1f,dockLensStretch(Float.NaN),0f)
        assertEquals(0f,dockLensLean(Float.POSITIVE_INFINITY),0f)
        assertEquals(dockLensStretch(6f),dockLensStretch(200f),0f)
    }
    @Test fun sliderEndpointsAndTrackPositioningRespectRange() {
        assertEquals(20f,sliderValueAt(-1f,20f..60f,0),0f)
        assertEquals(60f,sliderValueAt(2f,20f..60f,0),0f)
        assertEquals(40f,sliderValueAt(0.5f,20f..60f,0),0f)
    }
    @Test fun discreteSliderRoundsToTheSameIntervalsAsM3() {
        assertEquals(4f,sliderValueAt(0.11f,0f..40f,19),0f)
        assertEquals(40f,sliderValueAt(0.999f,0f..40f,19),0f)
    }
    @Test fun dockPressShrinksOnlyCapturedBackgroundToNinetyPercent() {
        assertEquals(1f,glassPressBackdropScale(0f),0f)
        assertEquals(0.9f,glassPressBackdropScale(1f),0f)
        assertEquals(0.96f,glassPressBackdropScale(0.4f),0.001f)
    }
    @Test fun dockLensBackgroundCompressionIsIndependentFromLegacyFeedback() {
        assertEquals(1f,dockLensBackgroundScale(0f),0f)
        assertEquals(0.8f,dockLensBackgroundScale(1f),0f)
        assertEquals(0.9f,dockLensBackgroundScale(0.5f),0.001f)
        assertEquals(1f,dockLensBackgroundScale(-1f),0f)
        assertEquals(0.8f,dockLensBackgroundScale(2f),0f)
    }
    @Test fun dockIndicatorUsesOneActualRectAndCanOverflowAt135Percent() {
        val rest=dockIndicatorBounds(66f,4f,268f,56f,1f,false,1f)
        assertEquals(272f,rest.left,0.001f)
        assertEquals(5f,rest.top,0.001f)
        assertEquals(268f,rest.width,0.001f)
        assertEquals(56f,rest.height,0.001f)

        val pressed=dockIndicatorBounds(66f,4f,268f,56f,1f,false,DockLensPressedScale)
        assertEquals(361.8f,pressed.width,0.001f)
        assertEquals(75.6f,pressed.height,0.001f)
        assertEquals(225.1f,pressed.left,0.001f)
        assertEquals(-4.8f,pressed.top,0.001f)
        assertEquals(DockLensPressedScale,pressed.width/268f,0.0001f)
        assertEquals(DockLensPressedScale,pressed.height/56f,0.0001f)
        assertEquals(rest.center.x,pressed.center.x,0.001f)
        assertEquals(rest.center.y,pressed.center.y,0.001f)
        val first=dockIndicatorBounds(66f,4f,268f,56f,0f,false,DockLensPressedScale)
        assertTrue(first.left<0f)
        assertTrue(pressed.top<0f && pressed.bottom>66f)

        val rtl=dockIndicatorBounds(66f,4f,268f,56f,1f,true,1f)
        assertEquals(1080f-272f-268f,rtl.left,0.001f)
    }
}
