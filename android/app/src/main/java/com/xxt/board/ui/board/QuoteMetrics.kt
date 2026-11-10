package com.xxt.board.ui.board

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxt.board.ui.theme.SerifSc

internal fun quoteTextStyle(font:Float)=TextStyle(fontFamily=SerifSc,fontSize=font.sp,lineHeight=(font*1.5f).sp,letterSpacing=0.8.sp)
internal fun measuredQuoteHeight(measurer:TextMeasurer,text:String,width:Int,font:Float,density:Density,
    padding:Float=6f,gap:Float=5f):Int {
    val body=measurer.measure(text,quoteTextStyle(font),constraints=Constraints(maxWidth=width.coerceAtLeast(1))).size.height
    val mark=measurer.measure("“",TextStyle(fontFamily=SerifSc,fontSize=(font*1.45f).sp,lineHeight=(font*1.45f).sp)).size.height
    return body+mark+with(density){(padding*2+gap+1).dp.roundToPx()}
}
internal fun fitsInlineQuote(lastItemVisible:Boolean,availablePx:Float,requiredPx:Int):Boolean=
    lastItemVisible && availablePx>=requiredPx && requiredPx>0
