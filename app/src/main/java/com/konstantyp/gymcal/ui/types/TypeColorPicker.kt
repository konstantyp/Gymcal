package com.konstantyp.gymcal.ui.types

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.ui.theme.colorToHsv
import com.konstantyp.gymcal.ui.theme.hsvToColorPublic
import com.konstantyp.gymcal.ui.theme.rememberThemeColorParams
import com.konstantyp.gymcal.ui.theme.runtimeColorsForSeed
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * TypeColorPicker per DesignBot HSV-wheel delta:
 * preview (runtime container) → circular HSV wheel (H rim, S→white center) →
 * V/brightness slider (0.35–1.0, default 1.0). Seed = HSV→ARGB. No hex / no 8×6 grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeColorPickerContent(
    seedArgb: Long,
    onSeedChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (darkTheme, sourceColor) = rememberThemeColorParams()
    val initial = remember { colorToHsv(Color(seedArgb.toInt())) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember {
        mutableFloatStateOf(initial[1].coerceIn(0f, 1f).let { if (it < 0.05f) 0.85f else it })
    }
    // SHOULD default 1.0 to match reference; clamp to slider range when editing existing seed.
    // DesignBot: V slider SHOULD default 1.0 (reference wheel). Keep seed V when editing.
    var value by remember {
        mutableFloatStateOf(
            initial[2].takeIf { it in 0.35f..1f } ?: 1f,
        )
    }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = ((h % 360f) + 360f) % 360f
        saturation = s.coerceIn(0f, 1f)
        value = v.coerceIn(0.35f, 1f)
        onSeedChange(
            hsvToColorPublic(hue, saturation, value).toArgb().toLong() and 0xFFFFFFFFL,
        )
    }

    val runtime = remember(seedArgb, darkTheme, sourceColor?.toArgb()) {
        runtimeColorsForSeed(seedArgb, darkTheme, sourceColor)
    }
    val previewCd = stringResource(R.string.cd_color_preview)
    val rawSeed = remember(hue, saturation, value) {
        hsvToColorPublic(hue, saturation, value)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Preview — runtime container 64 / r16
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(runtime.container)
                .semantics { contentDescription = previewCd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.titleMedium,
                color = runtime.onContainer,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. HSV wheel — diameter min(availableWidth − 32 dp, 280 dp)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = minOf(maxWidth - 32.dp, 280.dp).coerceAtLeast(200.dp)
            HsvColorWheel(
                hue = hue,
                saturation = saturation,
                rawSeed = rawSeed,
                onHsChange = { h, s -> applyHsv(h, s, value) },
                modifier = Modifier.size(diameter),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Value (brightness) slider — 0.35–1.0
        Text(
            text = stringResource(R.string.value_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
        Slider(
            value = value,
            onValueChange = { applyHsv(hue, saturation, it) },
            valueRange = 0.35f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HsvColorWheel(
    hue: Float,
    saturation: Float,
    rawSeed: Color,
    onHsChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 10.dp.toPx() } // 20 dp diameter
    val strokePx = with(density) { 2.dp.toPx() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val spectrum = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red,
        )
    }
    val wheelCd = stringResource(R.string.cd_color_wheel)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = wheelCd }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (h, s) = offsetToHs(offset, size.width.toFloat(), size.height.toFloat())
                    onHsChange(h, s)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val (h, s) = offsetToHs(
                        change.position,
                        size.width.toFloat(),
                        size.height.toFloat(),
                    )
                    onHsChange(h, s)
                }
            },
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Rim = full sat hues (SweepGradient starts at 3 o'clock, clockwise).
        drawCircle(
            brush = Brush.sweepGradient(colors = spectrum, center = center),
            radius = radius,
            center = center,
        )
        // Center = white (S → 0).
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        val angleRad = Math.toRadians(hue.toDouble()).toFloat()
        val satR = saturation.coerceIn(0f, 1f) * radius
        val thumbCenter = Offset(
            x = center.x + cos(angleRad) * satR,
            y = center.y + sin(angleRad) * satR,
        )
        // Thumb: 20 dp, stroke 2 dp — white+shadow for contrast, fill = raw seed.
        drawCircle(
            color = Color.Black.copy(alpha = 0.28f),
            radius = thumbRadiusPx + strokePx * 2f,
            center = thumbCenter + Offset(0f, strokePx),
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadiusPx + strokePx,
            center = thumbCenter,
        )
        drawCircle(
            color = rawSeed,
            radius = thumbRadiusPx,
            center = thumbCenter,
        )
        drawCircle(
            color = onSurface.copy(alpha = 0.55f),
            radius = thumbRadiusPx,
            center = thumbCenter,
            style = Stroke(width = strokePx),
        )
    }
}

/** Screen Y-down atan2 → clockwise from +X (matches SweepGradient + reference). */
private fun offsetToHs(offset: Offset, width: Float, height: Float): Pair<Float, Float> {
    val cx = width / 2f
    val cy = height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val radius = min(width, height) / 2f
    val sat = (hypot(dx.toDouble(), dy.toDouble()).toFloat() / radius).coerceIn(0f, 1f)
    val hue = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        .let { ((it % 360f) + 360f) % 360f }
    return hue to sat
}
