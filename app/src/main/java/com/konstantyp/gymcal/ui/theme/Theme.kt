package com.konstantyp.gymcal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.konstantyp.gymcal.data.WorkoutType
import kotlin.math.max
import kotlin.math.min

/** Per-type runtime colors (container fill + onContainer content). */
data class TypeRuntimeColors(
    val container: Color,
    val onContainer: Color,
)

val LocalTypeColorMap = staticCompositionLocalOf<Map<String, TypeRuntimeColors>> { emptyMap() }

fun Map<String, TypeRuntimeColors>.container(typeId: String): Color? =
    this[typeId]?.container

fun Map<String, TypeRuntimeColors>.onContainer(typeId: String): Color? =
    this[typeId]?.onContainer

/** Pick black/white for WCAG AA contrast ≥ 4.5:1 against [background]. */
fun contrastingOnColor(background: Color): Color {
    return if (background.luminance() > 0.179f) Color.Black else Color.White
}

/**
 * M3-style harmonize: shift [seed] hue toward [source] by up to 15° (half the delta),
 * preserving chroma/lightness identity so type hues stay distinct.
 */
internal fun harmonizeColor(seed: Color, source: Color): Color {
    val seedHsl = rgbToHsl(seed)
    val sourceHsl = rgbToHsl(source)
    val delta = shortestHueDelta(seedHsl[0], sourceHsl[0])
    val rotation = (delta * 0.5f).coerceIn(-15f, 15f)
    val hue = sanitizeHue(seedHsl[0] + rotation)
    return hslToColor(hue, seedHsl[1], seedHsl[2], seed.alpha)
}

/**
 * Map a (optionally harmonized) seed to container / onContainer tones.
 * Light container ≈ tonal 40; dark ≈ tonal 80; onContainer ≈ tonal 10 / 90,
 * with black/white fallback when contrast would fall below AA.
 */
fun tonalTypePair(seed: Color, darkTheme: Boolean): TypeRuntimeColors {
    val hsl = rgbToHsl(seed)
    val containerTone = if (darkTheme) 0.80f else 0.40f
    val onTone = if (darkTheme) 0.10f else 0.90f
    val chroma = max(hsl[1], 0.35f).coerceAtMost(0.85f)
    val container = hslToColor(hsl[0], chroma, containerTone)
    val tonalOn = hslToColor(hsl[0], chroma * 0.4f, onTone)
    val onContainer = if (contrastRatio(tonalOn, container) >= 4.5f) {
        tonalOn
    } else {
        contrastingOnColor(container)
    }
    return TypeRuntimeColors(container = container, onContainer = onContainer)
}

/** Build runtime colors for a single seed (used by picker live preview). */
fun runtimeColorsForSeed(
    seedArgb: Long,
    darkTheme: Boolean,
    sourceColor: Color?,
): TypeRuntimeColors {
    val seed = Color(seedArgb.toInt())
    return if (sourceColor == null) {
        val container = if (darkTheme) {
            Color(
                red = seed.red + (1f - seed.red) * 0.55f,
                green = seed.green + (1f - seed.green) * 0.55f,
                blue = seed.blue + (1f - seed.blue) * 0.55f,
                alpha = 1f,
            )
        } else {
            seed
        }
        TypeRuntimeColors(container = container, onContainer = contrastingOnColor(container))
    } else {
        tonalTypePair(harmonizeColor(seed, sourceColor), darkTheme)
    }
}

fun buildTypeColorMap(
    types: List<WorkoutType>,
    darkTheme: Boolean,
    sourceColor: Color?,
): Map<String, TypeRuntimeColors> {
    return types.associate { type ->
        type.id to runtimeColorsForSeed(type.seedArgb, darkTheme, sourceColor)
    }
}

private val LightFallbackScheme = lightColorScheme()
private val DarkFallbackScheme = darkColorScheme()

@Composable
fun GymcalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    types: List<WorkoutType> = emptyList(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme: ColorScheme = when {
        useDynamic -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFallbackScheme
        else -> LightFallbackScheme
    }

    // API 31+: harmonize type seeds against dynamic/wallpaper primary.
    // Pre-31: fallback tonal from raw seed (sourceColor = null).
    val sourceColor: Color? = if (useDynamic) colorScheme.primary else null
    val typeIdsKey = types.joinToString("|") { "${it.id}:${it.seedArgb}" }
    val typeColorMap = remember(darkTheme, useDynamic, sourceColor?.toArgb(), typeIdsKey) {
        buildTypeColorMap(types, darkTheme, sourceColor)
    }

    CompositionLocalProvider(LocalTypeColorMap provides typeColorMap) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GymcalTypography,
            content = content,
        )
    }
}

/** Expose current theme params for picker preview outside GymcalTheme rebuild. */
@Composable
fun rememberThemeColorParams(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
): Pair<Boolean, Color?> {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val primary = if (useDynamic) {
        val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scheme.primary
    } else {
        null
    }
    return darkTheme to (if (useDynamic) primary else null)
}

// --- HSL helpers (public for TypeColorPicker hue/sat editing) ---

fun colorToHsl(color: Color): FloatArray = rgbToHsl(color)

fun hslToColorPublic(h: Float, s: Float, l: Float, alpha: Float = 1f): Color =
    hslToColor(h, s, l, alpha)

private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    if (maxC == minC) {
        return floatArrayOf(0f, 0f, l)
    }
    val d = maxC - minC
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return floatArrayOf(h * 360f, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hue = sanitizeHue(h) / 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    if (sat == 0f) {
        return Color(light, light, light, alpha)
    }
    fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
    val p = 2f * light - q
    val r = hue2rgb(p, q, hue + 1f / 3f)
    val g = hue2rgb(p, q, hue)
    val b = hue2rgb(p, q, hue - 1f / 3f)
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), alpha)
}

private fun shortestHueDelta(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun sanitizeHue(h: Float): Float {
    var x = h % 360f
    if (x < 0f) x += 360f
    return x
}

private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return max(l1, l2) / min(l1, l2)
}


fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv // H 0..360, S 0..1, V 0..1
}

fun hsvToColorPublic(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val argb = android.graphics.Color.HSVToColor(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        floatArrayOf(hue, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)),
    )
    return Color(argb)
}
