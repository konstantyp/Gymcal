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

fun emptyCellOnColor(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)

fun outsideDayOnColor(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)

/** Surface-variant-like empty fill (~40% alpha feel via soft gray). */
fun emptyCellFill(dark: Boolean): Color =
    if (dark) Color(0xFF3A3A3C).copy(alpha = 0.45f) else Color(0xFFE7E0EC).copy(alpha = 0.45f)
