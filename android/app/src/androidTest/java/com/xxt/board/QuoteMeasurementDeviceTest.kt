package com.xxt.board

import android.test.InstrumentationTestCase
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.xxt.board.ui.board.measuredQuoteHeight

/** Real Android paragraphs at varied scales; no emulator configuration or user-data changes. */
@Suppress("DEPRECATION")
class QuoteMeasurementDeviceTest:InstrumentationTestCase() {
    fun testQuoteRequiresMoreSpaceAtNarrowWidthAndLargeFontScale() {
        val measured=mutableMapOf<String,Int>()
        instrumentation.runOnMainSync {
            val resolver=createFontFamilyResolver(instrumentation.targetContext)
            listOf(1f,1.5f,2f).forEach { scale ->
                val density=Density(1f,scale)
                val measurer=TextMeasurer(resolver,density,LayoutDirection.Ltr)
                val text="保持热爱，奔赴山海。在每一个平凡的日子里，认真做好眼前的事情。"
                measured[scale.toString()+"n"]=measuredQuoteHeight(measurer,text,180,15f,density)
                measured[scale.toString()+"w"]=measuredQuoteHeight(measurer,text,360,15f,density)
            }
        }
        assertTrue(measured.getValue("1.0n")>measured.getValue("1.0w"))
        assertTrue(measured.getValue("2.0n")>measured.getValue("1.0n"))
        assertTrue(measured.getValue("1.5w")>measured.getValue("1.0w"))
    }
}
