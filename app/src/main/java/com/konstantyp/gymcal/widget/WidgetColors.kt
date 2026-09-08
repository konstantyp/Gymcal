package com.konstantyp.gymcal.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.konstantyp.gymcal.data.WorkoutType

/**
 * Blue palette Variant D (BINDING) — widgets only.
 * Hard five blues; no Material dynamic / wallpaper harmonize.
 */
object BluePalette {
    val Navy = Color(0xFF003366)
    val Steel = Color(0xFF336699)
    val Mid = Color(0xFF6699CC)
    val Sky = Color(0xFF99CCFF)
    val Ice = Color(0xFFCCFFFF)
}

fun blueColor(c: Color): GlanceColorProvider = ColorProvider(day = c, night = c)

data class WidgetTypeColors(
    val container: Color,
    val onContainer: Color,
)

fun widgetColorsForType(type: WorkoutType): WidgetTypeColors {
    val name = type.name.trim().lowercase()
    return when {
        name == "push" || type.id == "default-push" ->
            WidgetTypeColors(BluePalette.Steel, BluePalette.Ice)
        name == "pull" || type.id == "default-pull" ->
            WidgetTypeColors(BluePalette.Mid, BluePalette.Navy)
        name == "legs" || type.id == "default-legs" ->
            WidgetTypeColors(BluePalette.Sky, BluePalette.Navy)
        type.sortOrder % 3 == 0 ->
            WidgetTypeColors(BluePalette.Steel, BluePalette.Ice)
        type.sortOrder % 3 == 1 ->
            WidgetTypeColors(BluePalette.Mid, BluePalette.Navy)
        else ->
            WidgetTypeColors(BluePalette.Sky, BluePalette.Navy)
    }
}

fun emptyCellFill(): Color = BluePalette.Navy

fun emptyCellOnColor(): Color = BluePalette.Ice

fun outsideDayOnColor(): Color = BluePalette.Ice.copy(alpha = 0.38f)
