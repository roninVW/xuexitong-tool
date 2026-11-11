package com.xxt.board.ui.liquid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop

internal const val DockLensPressedScale = 1.35f

internal fun dockLensBackgroundScale(pressure: Float): Float =
    lerp(1f, 0.8f, pressure.coerceIn(0f, 1f))

/**
 * The page fills the entire Lens first. Its real Dock background is then sampled
 * independently, scaled about the Lens center. Source pixels include the Dock's
 * original background and surface, but exclude labels, final rims and this Lens.
 * The Lens's own clip limits the composite; a smaller background footprint reveals
 * the real page at its margins rather than transparency or a second unscaled copy.
 */
internal class DockLensBackdrop(
    private val page: Backdrop,
    private val dockBackground: Backdrop,
    private val pressure: () -> Float,
    private val edgeGlow: Backdrop? = null,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(page) { drawBackdrop(density, coordinates, layerBlock) }
        val scale = dockLensBackgroundScale(pressure())
        if (scale != 1f && coordinates?.isAttached == true) {
            val center = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
            withTransform({ scale(scale, scale, center) }) {
                with(dockBackground) { drawBackdrop(density, coordinates, layerBlock) }
                edgeGlow?.let { with(it) { drawBackdrop(density, coordinates, layerBlock) } }
            }
        } else {
            with(dockBackground) { drawBackdrop(density, coordinates, layerBlock) }
            edgeGlow?.let { with(it) { drawBackdrop(density, coordinates, layerBlock) } }
        }
    }
}
