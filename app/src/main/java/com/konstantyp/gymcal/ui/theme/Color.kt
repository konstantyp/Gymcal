package com.konstantyp.gymcal.ui.theme

import androidx.compose.ui.graphics.Color

/** Default type seeds (v1.1 delta — Push/Pull/Legs). */
val WorkoutPushSeed = Color(0xFFC62828)
val WorkoutPullSeed = Color(0xFF1565C0)
val WorkoutLegsSeed = Color(0xFF2E7D32)
/** Palette presets (not factory defaults). */
val WorkoutChestSeed = Color(0xFFB83280)
val WorkoutBicepsSeed = Color(0xFF6B46C1)

/** 12 preset seeds for TypeColorPicker (v1.1 §2.3). */
val TypeColorPresets: List<Color> = listOf(
    Color(0xFFC62828), // Push
    Color(0xFFD97706), // amber
    Color(0xFFCA8A04), // gold
    Color(0xFF2E7D32), // Legs
    Color(0xFF0D9488), // teal
    Color(0xFF1565C0), // Pull
    Color(0xFF4F46E5), // indigo
    Color(0xFF6B46C1), // Biceps (preset)
    Color(0xFFB83280), // Chest (preset)
    Color(0xFFE11D48), // rose
    Color(0xFF78716C), // stone
    Color(0xFF334155), // graphite
)

val TypeColorPresetNames: List<String> = listOf(
    "Czerwień", "Bursztyn", "Złoto", "Zieleń", "Teal", "Błękit",
    "Indigo", "Fiolet", "Magenta", "Róż", "Kamień", "Grafit",
)

/** Dark empty day fill — BINDING weekends-empty-0F131C (unchanged). */
val EmptyDayFill = Color(0xFF0F131C)

/** Dark on-empty text for contrast on [EmptyDayFill]. */
val EmptyDayOn = Color(0xFFE8EEF8)

/** Dark empty hairline (app DayCell). */
val EmptyDayStrokeDark = Color(0xFF2A303C)

/** Light empty fill — BINDING light-daycell-C. */
val EmptyDayFillLight = Color(0xFFFFFFFF)

/** Light empty number — BINDING light-daycell-C. */
val EmptyDayOnLight = Color(0xFF3C3C43)

/** Light empty hairline — BINDING light-daycell-C. */
val EmptyDayStrokeLight = Color(0xFFC6C6C8)

/** Light today accent ring / number — BINDING light-daycell-C. */
val TodayAccentLight = Color(0xFF0B57D0)

/** Dark today accent fallback when not using dynamic primary. */
val TodayAccentDark = Color(0xFFA8C7FA)
