package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed

/**
 * Widget type colors: same pre-31 / no-wallpaper path as the app
 * (light ≈ seed, dark ≈ lightened tonal). No dynamic wallpaper harmonize.
 *
 * Empty / outside tokens: DesignBot Variant D (surfaceContainerLowest + outlineVariant).
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

/** Empty fill: surfaceContainerLowest (Variant D). */
fun emptyCellFill(dark: Boolean): Color =
    if (dark) Color(0xFF0F0D13) else Color(0xFFFFFFFF)

/** Empty stroke: outlineVariant (Variant D). */
fun emptyCellStroke(dark: Boolean): Color =
    if (dark) Color(0xFF49454F) else Color(0xFFCAC4D0)

/** Empty day number: onSurfaceVariant (Variant D). */
fun emptyCellOnColor(dark: Boolean): Color =
    if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F)

/** Outside-month number: empty number @ 38% opacity. */
fun outsideDayOnColor(dark: Boolean): Color =
    emptyCellOnColor(dark).copy(alpha = 0.38f)
