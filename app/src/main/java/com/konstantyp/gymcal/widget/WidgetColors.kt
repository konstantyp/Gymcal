package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed

/**
 * Widget type colors match the app calendar (seed → tonal containers).
 * Supersedes blue palette Variant D for widgets.
 * Empty/chrome use GlanceTheme Material roles in GymcalWidget.
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

fun widgetColorsForType(type: WorkoutType): WidgetTypeColors =
    widgetColorsForSeed(type.seedArgb)

/** Fallback empty fill if GlanceTheme surfaceVariant unavailable. */
fun emptyCellFillFallback(dark: Boolean): Color =
    if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC)

fun emptyCellOnFallback(dark: Boolean): Color =
    if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F)

fun outlineVariantFallback(dark: Boolean): Color =
    if (dark) Color(0xFF49454F) else Color(0xFFCAC4D0)
