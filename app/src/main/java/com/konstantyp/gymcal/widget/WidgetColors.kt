package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed

/**
 * Widget type colors: same pre-31 / no-wallpaper path as the app
 * (light ≈ seed, dark ≈ lightened tonal). No dynamic wallpaper harmonize.
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

/** Empty day number: onSurface bases @ 68% alpha. */
fun emptyCellOnColor(dark: Boolean): Color =
    if (dark) Color(0xFFE6E1E5).copy(alpha = 0.68f)
    else Color(0xFF1D1B20).copy(alpha = 0.68f)

/** Outside-month number: onSurface @ 38% opacity. */
fun outsideDayOnColor(dark: Boolean): Color =
    if (dark) Color(0xFFE6E1E5).copy(alpha = 0.38f)
    else Color(0xFF1D1B20).copy(alpha = 0.38f)

/** Empty fill: primaryContainer-tint (not flat gray). */
fun emptyCellFill(dark: Boolean): Color =
    if (dark) Color(0xFF4A4458) else Color(0xFFE8DEF8)
