package com.konstantyp.gymcal.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import com.konstantyp.gymcal.ui.theme.EmptyDayFill
import com.konstantyp.gymcal.ui.theme.EmptyDayOn
import com.konstantyp.gymcal.ui.theme.LocalTypeColorMap
import com.konstantyp.gymcal.ui.theme.TypeRuntimeColors
import com.konstantyp.gymcal.ui.theme.contrastingOnColor

private val CellShape = RoundedCornerShape(8.dp)

/** Soft edge for empty cells on near-black #0F131C. */
private val EmptyDayStroke = Color(0xFF2A303C)

@Composable
fun DayCell(
    dayOfMonth: Int,
    isToday: Boolean,
    isOutsideMonth: Boolean,
    isSelected: Boolean,
    typeIds: List<String>,
    typeNames: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colorMap = LocalTypeColorMap.current
    val runtimes: List<TypeRuntimeColors> = typeIds.mapNotNull { colorMap[it] }
        .take(2)
        .let { list -> if (isOutsideMonth) emptyList() else list }
    val slotCount = runtimes.size

    val baseFillSingle: Color = when {
        slotCount == 1 -> runtimes[0].container
        isOutsideMonth -> EmptyDayFill.copy(alpha = 0.42f)
        else -> EmptyDayFill
    }

    val fillSingle: Color = if (isSelected && !isOutsideMonth && slotCount <= 1) {
        androidx.compose.ui.graphics.lerp(baseFillSingle, scheme.primaryContainer, 0.32f)
    } else {
        baseFillSingle
    }

    val stroke: BorderStroke? = when {
        isToday && !isOutsideMonth -> BorderStroke(2.dp, scheme.primary)
        isSelected && !isOutsideMonth -> BorderStroke(2.dp, scheme.primary)
        slotCount == 0 && !isOutsideMonth -> BorderStroke(1.dp, EmptyDayStroke)
        else -> null
    }

    // Dual: centered light/contrasting number over both halves (BINDING B).
    val numberColor: Color = when {
        isOutsideMonth -> EmptyDayOn.copy(alpha = 0.45f)
        slotCount >= 2 -> Color.White
        isSelected && !isOutsideMonth -> contrastingOnColor(fillSingle)
        slotCount == 1 -> runtimes[0].onContainer
        isToday -> scheme.primary
        else -> EmptyDayOn
    }

    val showTypeLabel = slotCount == 1 && typeNames.firstOrNull()?.isNotBlank() == true
    val typeLabel = typeNames.firstOrNull().orEmpty()
    val todayFlag = if (isToday) stringResource(R.string.a11y_today) else ""
    val noWorkout = stringResource(R.string.a11y_no_workout)
    val outside = stringResource(R.string.a11y_outside_month)
    val desc = buildString {
        append("$dayOfMonth")
        when {
            slotCount >= 2 -> {
                val names = typeNames.filter { it.isNotBlank() }.joinToString(", ")
                if (names.isNotEmpty()) append(", $names") else append(", 2")
            }
            showTypeLabel -> append(", $typeLabel")
            else -> append(", $noWorkout")
        }
        if (todayFlag.isNotEmpty()) append(", $todayFlag")
        if (isOutsideMonth) append(", $outside")
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CellShape)
            .then(
                if (slotCount < 2) Modifier.background(fillSingle, CellShape) else Modifier
            )
            .then(
                if (stroke != null) Modifier.border(stroke, CellShape) else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (slotCount >= 2) {
            // Horizontal split B: top = slot0, bottom = slot1 + type names (BINDING app-daycell-dual-labels)
            val name0 = typeNames.getOrNull(0).orEmpty()
            val name1 = typeNames.getOrNull(1).orEmpty()
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(runtimes[0].container),
                    contentAlignment = Alignment.Center,
                ) {
                    if (name0.isNotBlank()) {
                        Text(
                            text = name0,
                            style = MaterialTheme.typography.labelSmall,
                            color = runtimes[0].onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(runtimes[1].container),
                    contentAlignment = Alignment.Center,
                ) {
                    if (name1.isNotBlank()) {
                        Text(
                            text = name1,
                            style = MaterialTheme.typography.labelSmall,
                            color = runtimes[1].onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
            // Day number centered over both halves
            Text(
                text = dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = numberColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        } else {
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
}
