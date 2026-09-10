package com.konstantyp.gymcal.ui.stats

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.LocalTypeColorMap
import com.konstantyp.gymcal.ui.theme.TypeRuntimeColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Material emphasized decelerate — BINDING motion token (~280 ms bar morph). */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private const val BarMorphDurationMs = 280
private const val BarStaggerMs = 40
private const val LabelCrossfadeMs = 120

private enum class StatsPeriod { Week, Month }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    workouts: Map<LocalDate, List<String>>,
    types: List<WorkoutType>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    // Match calendar Monday-first week.
    val weekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekEnd = remember(weekStart) { weekStart.plusDays(6) }
    val yearMonth = remember(today) { YearMonth.from(today) }
    val monthStart = remember(yearMonth) { yearMonth.atDay(1) }
    val monthEnd = remember(yearMonth) { yearMonth.atEndOfMonth() }

    val weekCounts = remember(workouts, types, weekStart, weekEnd) {
        countHitsByType(workouts, types, weekStart, weekEnd)
    }
    val monthCounts = remember(workouts, types, monthStart, monthEnd) {
        countHitsByType(workouts, types, monthStart, monthEnd)
    }
    val weekTotal = weekCounts.values.sum()
    val monthTotal = monthCounts.values.sum()
    val colorMap = LocalTypeColorMap.current

    val weekRangeLabel = remember(weekStart, weekEnd, locale) {
        formatWeekRange(weekStart, weekEnd, locale)
    }
    val monthLabel = remember(yearMonth, locale) {
        yearMonth.atDay(1)
            .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    var selectedPeriod by remember { mutableIntStateOf(0) } // 0 = week, 1 = month
    val period = if (selectedPeriod == 0) StatsPeriod.Week else StatsPeriod.Month
    val reduceMotion = rememberReduceMotion()

    val activeCounts = if (period == StatsPeriod.Week) weekCounts else monthCounts
    val activeTotal = if (period == StatsPeriod.Week) weekTotal else monthTotal
    val activeSupporting = if (period == StatsPeriod.Week) weekRangeLabel else monthLabel
    val activeTitle = stringResource(
        if (period == StatsPeriod.Week) R.string.stats_week_by_type else R.string.stats_month_by_type,
    )
    val activeEmpty = stringResource(
        if (period == StatsPeriod.Week) R.string.stats_empty_week else R.string.stats_empty_month,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedPeriod) {
                Tab(
                    selected = selectedPeriod == 0,
                    onClick = { selectedPeriod = 0 },
                    text = { Text(stringResource(R.string.stats_this_week)) },
                )
                Tab(
                    selected = selectedPeriod == 1,
                    onClick = { selectedPeriod = 1 },
                    text = { Text(stringResource(R.string.stats_this_month)) },
                )
            }

            // Dual totals strip — always visible; selecting a chip selects that period
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TotalStripCard(
                    label = stringResource(R.string.stats_tab_week),
                    value = weekTotal,
                    selected = selectedPeriod == 0,
                    onClick = { selectedPeriod = 0 },
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f),
                )
                TotalStripCard(
                    label = stringResource(R.string.stats_tab_month),
                    value = monthTotal,
                    selected = selectedPeriod == 1,
                    onClick = { selectedPeriod = 1 },
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
            ) {
                // Active period only — bars morph week ↔ month in place
                PeriodByTypeSection(
                    title = activeTitle,
                    supporting = activeSupporting,
                    types = types,
                    counts = activeCounts,
                    total = activeTotal,
                    emptyMessage = activeEmpty,
                    colorMap = colorMap,
                    periodKey = period,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotalStripCard(
    label: String,
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorDuration = if (reduceMotion) 0 else BarMorphDurationMs
    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    val unselectedContainer = MaterialTheme.colorScheme.surfaceContainerLow
    val targetContainer = if (selected) selectedContainer else unselectedContainer
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(
            durationMillis = colorDuration,
            easing = EmphasizedDecelerate,
        ),
        label = "stripContainer",
    )
    val underlineColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(
            durationMillis = colorDuration,
            easing = EmphasizedDecelerate,
        ),
        label = "stripUnderline",
    )

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(underlineColor),
            )
        }
    }
}

@Composable
private fun PeriodByTypeSection(
    title: String,
    supporting: String,
    types: List<WorkoutType>,
    counts: Map<String, Int>,
    total: Int,
    emptyMessage: String,
    colorMap: Map<String, TypeRuntimeColors>,
    periodKey: StatsPeriod,
    reduceMotion: Boolean,
) {
    val labelDuration = if (reduceMotion) 0 else LabelCrossfadeMs

    AnimatedContent(
        targetState = Triple(supporting, title, emptyMessage),
        transitionSpec = {
            fadeIn(tween(labelDuration, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(labelDuration, easing = FastOutSlowInEasing))
        },
        label = "periodLabels",
    ) { (sup, tit, empty) ->
        Column {
            Text(
                text = sup,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (types.isEmpty() || total == 0) {
                Text(
                    text = empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Start,
                )
            }
        }
    }

    if (types.isNotEmpty()) {
        // Scale bars to the active period's max so end-states match mockups;
        // fraction still morphs when the period (and thus counts/max) changes.
        val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            types.forEachIndexed { index, type ->
                val count = counts[type.id] ?: 0
                val fill = colorMap[type.id]?.container
                    ?: MaterialTheme.colorScheme.primary
                val fraction = if (total == 0) 0f else count.toFloat() / maxCount
                TypeStatBar(
                    name = type.name,
                    count = count,
                    fraction = fraction,
                    fillColor = fill,
                    staggerIndex = index,
                    reduceMotion = reduceMotion,
                    periodKey = periodKey,
                )
            }
        }
    }
}

@Composable
private fun TypeStatBar(
    name: String,
    count: Int,
    fraction: Float,
    fillColor: Color,
    staggerIndex: Int,
    reduceMotion: Boolean,
    periodKey: StatsPeriod,
    modifier: Modifier = Modifier,
) {
    val targetFraction = fraction.coerceIn(0f, 1f)
    val anim = remember { Animatable(targetFraction) }
    val countAnim = remember { Animatable(count.toFloat()) }

    LaunchedEffect(periodKey, targetFraction, count, reduceMotion) {
        if (reduceMotion) {
            anim.snapTo(targetFraction)
            countAnim.snapTo(count.toFloat())
        } else {
            if (staggerIndex > 0) delay(BarStaggerMs.toLong() * staggerIndex)
            // Morph fill width + trailing count from previous period values → new
            kotlinx.coroutines.coroutineScope {
                launch {
                    anim.animateTo(
                        targetValue = targetFraction,
                        animationSpec = tween(
                            durationMillis = BarMorphDurationMs,
                            easing = EmphasizedDecelerate,
                        ),
                    )
                }
                launch {
                    countAnim.animateTo(
                        targetValue = count.toFloat(),
                        animationSpec = tween(
                            durationMillis = BarMorphDurationMs,
                            easing = EmphasizedDecelerate,
                        ),
                    )
                }
            }
        }
    }

    val displayFraction = anim.value
    val displayCount = countAnim.value.roundToInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = displayCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (displayFraction > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(displayFraction)
                        .clip(RoundedCornerShape(8.dp))
                        .background(fillColor),
                )
            }
        }
    }
}

private fun countHitsByType(
    workouts: Map<LocalDate, List<String>>,
    types: List<WorkoutType>,
    start: LocalDate,
    end: LocalDate,
): Map<String, Int> {
    val typeIds = types.map { it.id }.toSet()
    val counts = types.associate { it.id to 0 }.toMutableMap()
    var d = start
    while (!d.isAfter(end)) {
        val ids = workouts[d].orEmpty().filter { it in typeIds }
        for (id in ids) {
            counts[id] = (counts[id] ?: 0) + 1
        }
        d = d.plusDays(1)
    }
    return counts
}

private fun formatWeekRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    val dayFmt = DateTimeFormatter.ofPattern("d", locale)
    val monthDayFmt = DateTimeFormatter.ofPattern("d MMMM", locale)
    return if (start.month == end.month && start.year == end.year) {
        val endLabel = end.format(monthDayFmt)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        "${start.format(dayFmt)}–$endLabel"
    } else {
        val startLabel = start.format(monthDayFmt)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val endLabel = end.format(monthDayFmt)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        "$startLabel – $endLabel"
    }
}
