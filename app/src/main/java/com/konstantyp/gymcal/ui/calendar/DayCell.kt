package com.konstantyp.gymcal.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.ui.theme.LocalTypeColorMap
import com.konstantyp.gymcal.ui.theme.contrastingOnColor

private val CellShape = RoundedCornerShape(8.dp)

@Composable
fun DayCell(
    dayOfMonth: Int,
    isToday: Boolean,
    isOutsideMonth: Boolean,
    isSelected: Boolean,
    typeId: String?,
    typeName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colorMap = LocalTypeColorMap.current
    val runtime = typeId?.let { colorMap[it] }
    // Missing type in map (deleted) → treat as empty
    val hasWorkout = runtime != null && !isOutsideMonth

    val baseFill: Color = when {
        hasWorkout -> runtime!!.container
        else -> scheme.surfaceVariant.copy(alpha = if (isOutsideMonth) 0.2f else 0.4f)
    }

    val fill: Color = if (isSelected && !isOutsideMonth) {
        androidx.compose.ui.graphics.lerp(baseFill, scheme.primaryContainer, 0.32f)
    } else {
        baseFill
    }

    val stroke: BorderStroke? = when {
        isToday && !isOutsideMonth -> BorderStroke(2.dp, scheme.primary)
        isSelected && !isOutsideMonth -> BorderStroke(2.dp, scheme.primary)
        !hasWorkout && !isOutsideMonth -> BorderStroke(1.dp, scheme.outlineVariant)
        else -> null
    }

    // Spec: label uses the same contrast color as the day number.
    val numberColor: Color = when {
        isOutsideMonth -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        isSelected && !isOutsideMonth -> contrastingOnColor(fill)
        hasWorkout -> runtime!!.onContainer
        isToday -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    val showTypeLabel = hasWorkout && !typeName.isNullOrBlank()
    val typeLabel = typeName ?: ""
    val todayFlag = if (isToday) stringResource(R.string.a11y_today) else ""
    val noWorkout = stringResource(R.string.a11y_no_workout)
    val outside = stringResource(R.string.a11y_outside_month)
    val desc = buildString {
        append("$dayOfMonth")
        if (showTypeLabel) append(", $typeLabel") else append(", $noWorkout")
        if (todayFlag.isNotEmpty()) append(", $todayFlag")
        if (isOutsideMonth) append(", $outside")
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CellShape)
            .background(fill, CellShape)
            .then(
                if (stroke != null) Modifier.border(stroke, CellShape) else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = numberColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (showTypeLabel) {
                // dayCellLabelGap = 2.dp; labelSmall; maxLines 1; ellipsis
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = numberColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
