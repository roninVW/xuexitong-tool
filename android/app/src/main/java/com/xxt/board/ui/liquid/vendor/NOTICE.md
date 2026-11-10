# AndroidLiquidGlass attribution

DampedDragAnimation.kt, InteractiveHighlight.kt and DragGestureInspector.kt are adapted
from Kyant0/AndroidLiquidGlass's Android catalog, licensed under Apache-2.0.
The adjacent LICENSE preserves the upstream license. Package names are adapted for this app.

LiquidDock follows the official LiquidBottomTabs composition (background layer, hidden
tab-content source, lens indicator, spring press/velocity deformation), with one gesture
host and an offscreen cutout to keep each icon visible exactly once. LiquidGlassButton
follows LiquidButton. Glass menus use the official exportedBackdrop nesting mechanism.

References:
- https://github.com/Kyant0/AndroidLiquidGlass
- https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar
- https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar
- https://kyant.gitbook.io/backdrop/tutorials/glass-slider
- https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet

Backdrop is pinned to 1.0.0-rc02, the Android implementation using Compose 1.9.4.
The project's Kotlin/AGP/Material3 versions are retained. The new CMP releases require
a broader toolchain migration that is outside this theme's scope. Real AGSL lens
refraction needs Android 13+; older devices retain their original theme.

Retained interaction adaptations (2026-11-11): InteractiveHighlight observes the Initial
pointer pass without consuming click or drag ownership, tracks the touch location directly,
and draws its light above component content. The Dock indicator samples the exported Dock
floor together with its icon source once. Slider and switch controls share held, drag and
release feedback; menu children follow the same entry-to-panel morph progress.
The later settings redesign, global header rewrite, material transition and adjustable
optical parameters were rolled back at the user's request.

Targeted fixes (2026-11-11): the Dock moving lens samples a buffered, unoutlined interior
source with the same vibrancy, blur and surface tint. The visible Dock alone owns its lens
contour, shadow and highlight. Selection items and menu actions now paint directly on the
single menu glass surface. DampedDragAnimation uses separately cancellable value, velocity
and feedback jobs; finger-up/cancel always starts feedback recovery immediately and never
resets the user value. InteractiveHighlight scales displacement back to zero with release.

Dock follow-up: pointer positions now update synchronous Compose state; only finger-up uses
spring position interpolation. The interactive light is drawn inside the indicator below its
official directional Highlight, preventing a whole-Dock light overlay from washing out the
edge. Liquid transparency scales surface alpha, while refraction independently scales the
Backdrop lens amount. Default material retains its existing opacity preferences and widgets.
