package com.konstantyp.gymcal.ui.stats

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.LocalTypeColorMap
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

    var selectedTab by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    // Approximate scroll targets: dual strip ~120dp, week section ~ (types*48 + header)
    val weekSectionOffset = 0
    val monthSectionOffset = remember(types.size) {
        // Week title + supporting + bars (+ empty copy padding)
        56 + types.size.coerceAtLeast(1) * 52 + 28
    }

    LaunchedEffect(selectedTab) {
        val target = if (selectedTab == 0) weekSectionOffset else monthSectionOffset
        scrollState.animateScrollTo(target)
    }

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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(stringResource(R.string.stats_tab_week))
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(stringResource(R.string.stats_tab_month))
                    },
                )
            }

            // Dual totals strip — pinned below tabs (always visible)
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f),
                )
                TotalStripCard(
                    label = stringResource(R.string.stats_tab_month),
                    value = monthTotal,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
            ) {
                // Week · by type
                PeriodByTypeSection(
                    title = stringResource(R.string.stats_week_by_type),
                    supporting = weekRangeLabel,
                    types = types,
                    counts = weekCounts,
                    total = weekTotal,
                    emptyMessage = stringResource(R.string.stats_empty_week),
                    colorMap = colorMap,
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Month · by type
                PeriodByTypeSection(
                    title = stringResource(R.string.stats_month_by_type),
                    supporting = monthLabel,
                    types = types,
                    counts = monthCounts,
                    total = monthTotal,
                    emptyMessage = stringResource(R.string.stats_empty_month),
                    colorMap = colorMap,
                )
            }
        }
    }
}

@Composable
private fun TotalStripCard(
    label: String,
    value: Int,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
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
    colorMap: Map<String, com.konstantyp.gymcal.ui.theme.TypeRuntimeColors>,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = supporting,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (types.isEmpty() || total == 0) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Start,
        )
    }

    if (types.isNotEmpty()) {
        val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            types.forEach { type ->
                val count = counts[type.id] ?: 0
                val fill = colorMap[type.id]?.container
                    ?: MaterialTheme.colorScheme.primary
                TypeStatBar(
                    name = type.name,
                    count = count,
                    fraction = if (total == 0) 0f else count.toFloat() / maxCount,
                    fillColor = fill,
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
    modifier: Modifier = Modifier,
) {
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
                text = count.toString(),
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
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
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
