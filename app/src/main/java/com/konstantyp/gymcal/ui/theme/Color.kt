package com.konstantyp.gymcal.ui.theme

import androidx.compose.ui.graphics.Color

/** Fallback seeds from SPEC v1.0 §1.2 — also default type seeds in v1.1. */
val WorkoutPushSeed = Color(0xFFC45C26)
val WorkoutPullSeed = Color(0xFF2B6CB0)
val WorkoutLegsSeed = Color(0xFF2F855A)
val WorkoutChestSeed = Color(0xFFB83280)
val WorkoutBicepsSeed = Color(0xFF6B46C1)

/** 12 preset seeds for TypeColorPicker (v1.1 §2.3). */
val TypeColorPresets: List<Color> = listOf(
    Color(0xFFC45C26), // Push
    Color(0xFFD97706), // amber
    Color(0xFFCA8A04), // gold
    Color(0xFF2F855A), // Legs
    Color(0xFF0D9488), // teal
    Color(0xFF2B6CB0), // Pull
    Color(0xFF4F46E5), // indigo
    Color(0xFF6B46C1), // Biceps
    Color(0xFFB83280), // Chest
    Color(0xFFE11D48), // rose
    Color(0xFF78716C), // stone
    Color(0xFF334155), // graphite
)

val TypeColorPresetNames: List<String> = listOf(
    "Pomarańcz", "Bursztyn", "Złoto", "Zieleń", "Teal", "Błękit",
    "Indigo", "Fiolet", "Magenta", "Róż", "Kamień", "Grafit",
)
