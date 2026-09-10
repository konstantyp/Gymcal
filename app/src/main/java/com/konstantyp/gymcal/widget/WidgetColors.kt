package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.EmptyDayFill
import com.konstantyp.gymcal.ui.theme.EmptyDayFillLight
import com.konstantyp.gymcal.ui.theme.EmptyDayOn
import com.konstantyp.gymcal.ui.theme.EmptyDayOnLight
import com.konstantyp.gymcal.ui.theme.EmptyDayStrokeLight
import com.konstantyp.gymcal.ui.theme.TodayAccentDark
import com.konstantyp.gymcal.ui.theme.TodayAccentLight
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed

/**
 * Widget type colors match the app calendar (seed → tonal containers).
 * Empty days: light BINDING C (white + hairline); dark `#0F131C` unchanged.
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

/** Light empty fill (outside ~50%). */
fun emptyCellFillLight(outsideMonth: Boolean): Color =
    EmptyDayFillLight.copy(alpha = if (outsideMonth) 0.50f else 1f)

/** Dark empty fill (outside ~45%). */
fun emptyCellFillDark(outsideMonth: Boolean): Color =
    EmptyDayFill.copy(alpha = if (outsideMonth) 0.45f else 1f)

/** Light empty number (outside ~35%). */
fun emptyCellOnLight(outsideMonth: Boolean): Color =
    if (outsideMonth) EmptyDayOnLight.copy(alpha = 0.35f) else EmptyDayOnLight

/** Dark empty number (outside ~38%). */
fun emptyCellOnDark(outsideMonth: Boolean): Color =
    if (outsideMonth) EmptyDayOn.copy(alpha = 0.38f) else EmptyDayOn

/** @deprecated use light/dark helpers — kept for any residual call sites. */
fun emptyCellFill(outsideMonth: Boolean): Color = emptyCellFillDark(outsideMonth)

/** @deprecated use light/dark helpers */
fun emptyCellOn(outsideMonth: Boolean): Color = emptyCellOnDark(outsideMonth)

fun emptyStrokeDay(): Color = EmptyDayStrokeLight

/** Night stroke matches dark fill so the 1dp ring is invisible (no-outline dark). */
fun emptyStrokeNight(): Color = EmptyDayFill

fun todayAccentDay(): Color = TodayAccentLight

fun todayAccentNight(): Color = TodayAccentDark
