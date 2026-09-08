package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.EmptyDayFill
import com.konstantyp.gymcal.ui.theme.EmptyDayOn
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed

/**
 * Widget type colors match the app calendar (seed → tonal containers).
 * Empty days use [EmptyDayFill] / [EmptyDayOn] (not surfaceVariant grey).
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

fun emptyCellFill(outsideMonth: Boolean): Color =
    EmptyDayFill.copy(alpha = if (outsideMonth) 0.45f else 1f)

fun emptyCellOn(outsideMonth: Boolean): Color =
    if (outsideMonth) EmptyDayOn.copy(alpha = 0.38f) else EmptyDayOn

fun outlineVariantFallback(dark: Boolean): Color =
    if (dark) Color(0xFF49454F) else Color(0xFFCAC4D0)
