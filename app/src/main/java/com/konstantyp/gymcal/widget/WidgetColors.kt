package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed

/**
 * Widget type colors: same pre-31 / no-wallpaper path as the app
 * (light ≈ seed, dark ≈ lightened tonal). No dynamic wallpaper harmonize.
 *
 * Empty / outside tokens: DesignBot GCal-like BINDING
 * (surfaceContainerHighest, no stroke).
 */
data class WidgetTypeColors(
    val containerDay: Color,
    val onContainerDay: Color,
    val containerNight: Color,
    val onContainerNight: Color,
)

fun widgetColorsForSeed(seedArgb: Long): WidgetTypeColors {
    val light = runtimeColorsForSeed(seedArgb, darkTheme = false, sourceColor = null)
    val dark = runtimeColorsForSeed(seedArgb, darkTheme = true, sourceColor = null)
    return WidgetTypeColors(
        containerDay = light.container,
        onContainerDay = light.onContainer,
        containerNight = dark.container,
        onContainerNight = dark.onContainer,
    )
}

/** Empty fill: surfaceContainerHighest (GCal-like chocolate-bar). */
fun emptyCellFill(dark: Boolean): Color =
    if (dark) Color(0xFF36343B) else Color(0xFFE6E0E9)

/** Empty day number: onSurfaceVariant. */
fun emptyCellOnColor(dark: Boolean): Color =
    if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F)

/** Outside-month number: empty number @ 38% opacity. */
fun outsideDayOnColor(dark: Boolean): Color =
    emptyCellOnColor(dark).copy(alpha = 0.38f)
