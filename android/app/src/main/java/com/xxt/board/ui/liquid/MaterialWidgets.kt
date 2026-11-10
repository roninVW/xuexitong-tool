package com.xxt.board.ui.liquid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Native M3 remains the entire default branch, including its gestures and dimensions. */
@Composable
fun MaterialButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null) Button(onClick,modifier,enabled,content=content)
    else LiquidGlassButton(backdrop,onClick,modifier,enabled=enabled,onLongClick=onLongClick,content=content)
}
@Composable
fun MaterialTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null) FilledTonalButton(onClick,modifier,enabled,content=content)
    else LiquidGlassButton(backdrop,onClick,modifier,enabled=enabled,content=content)
}
@Composable
fun MaterialTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null) TextButton(onClick,modifier,enabled,content=content)
    else LiquidGlassButton(backdrop,onClick,modifier,enabled=enabled,compact=true,content=content)
}
@Composable
fun MaterialIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null) IconButton(onClick,modifier,enabled,content=content)
    else LiquidGlassButton(backdrop,onClick,modifier.size(48.dp),enabled=enabled,
        contentPadding=PaddingValues(12.dp)) { Box(Modifier.size(24.dp),contentAlignment=Alignment.Center) {content()} }
}

/** Original Material dialog layout and ripple.
 * A dialog has a separate window, so controls must not sample the page backdrop. */
@Composable
fun MaterialAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    CompositionLocalProvider(LocalLiquidBackdrop provides null) {
        AlertDialog(onDismissRequest,confirmButton,modifier,dismissButton=dismissButton,title=title,text=text)
    }
}

@Composable
fun MaterialFilterChip(selected: Boolean,onClick: () -> Unit,label: @Composable () -> Unit,
    modifier: Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable () -> Unit)?=null,
    trailingIcon:(@Composable () -> Unit)?=null,shape:Shape=RoundedCornerShape(8.dp),
    colors:SelectableChipColors=FilterChipDefaults.filterChipColors()) {
    val backdrop=LocalLiquidBackdrop.current
    if(backdrop==null) FilterChip(selected,onClick,label,modifier,enabled,leadingIcon=leadingIcon,
        trailingIcon=trailingIcon,shape=shape,colors=colors)
    else LiquidGlassButton(backdrop,onClick,modifier,selected=selected,enabled=enabled,buttonShape=shape,compact=true) {
        leadingIcon?.invoke();label();trailingIcon?.invoke()
    }
}
