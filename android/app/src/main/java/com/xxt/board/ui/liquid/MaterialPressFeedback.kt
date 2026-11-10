package com.xxt.board.ui.liquid

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.xxt.board.ui.liquid.vendor.InteractiveHighlight

/** Non-consuming observer: existing click/long-press ownership remains unchanged. */
fun Modifier.materialPressFeedback():Modifier=composed {
    run {
        val scope=rememberCoroutineScope()
        val light=remember(scope){InteractiveHighlight(scope)}
        this.then(light.modifier).then(light.gestureModifier)
    }
}
