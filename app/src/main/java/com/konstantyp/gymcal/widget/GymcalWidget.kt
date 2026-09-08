package com.konstantyp.gymcal.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.currentState
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.konstantyp.gymcal.GymcalApp
import com.konstantyp.gymcal.MainActivity
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.data.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.first

enum class WidgetLayoutMode {
    Week,
    Month,
}

abstract class GymcalBaseWidget(
    private val layoutMode: WidgetLayoutMode,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    /**
     * Loads types/workouts from DataStore, then observes [WorkoutRepository] Flows
     * inside `provideContent` so seed-color edits rebuild colorCache immediately.
     *
     * Glance 1.1 reuses a running session on [update]/[updateAll]: it applies
     * UpdateGlanceState and recomposes `provideContent` without re-entering this
     * method. Capturing colors only here would leave edits stale.
     *
     * Primary path: [produceState] collecting repository Flows. [ForceRefreshAtKey]
     * remains a kick that restarts collection when Glance Preferences change
     * (OEM / session edge cases) — not a LaunchedEffect one-shot reload.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as GymcalApp).workoutRepository
        val loaded = runCatching {
            Pair(repository.types.first(), repository.workouts.first())
        }
        val initialTypes = loaded.getOrNull()?.first.orEmpty()
        val initialWorkouts = loaded.getOrNull()?.second.orEmpty()
        val initialError = loaded.isFailure

        provideContent {
            val prefs = currentState<Preferences>()
            // Kick: Preference nonce change recomposes and restarts Flow collection.
            val refreshAt = prefs[GymcalWidgetUpdater.ForceRefreshAtKey] ?: 0L

            val typesLoad by produceState(
                initialValue = Pair(initialTypes, initialError),
                key1 = refreshAt,
            ) {
                runCatching {
                    repository.types.collect { types ->
                        value = Pair(types, false)
                    }
                }.onFailure {
                    value = Pair(value.first, true)
                }
            }
            val workoutsLoad by produceState(
                initialValue = Pair(initialWorkouts, initialError),
                key1 = refreshAt,
            ) {
                runCatching {
                    repository.workouts.collect { map ->
                        value = Pair(map, false)
                    }
                }.onFailure {
                    value = Pair(value.first, true)
                }
            }
            val types = typesLoad.first
            val workouts = workoutsLoad.first
            val loadError = typesLoad.second || workoutsLoad.second

            val typesById = types.associateBy { it.id }
            // Rebuild every composition so seedArgb edits always paint.
            val colorCache = types.associate { it.id to widgetColorsForType(it) }

            GlanceTheme(colors = DynamicThemeColorProviders) {
                WidgetRoot(
                    layoutMode = layoutMode,
                    typesById = typesById,
                    workouts = workouts,
                    colorCache = colorCache,
                    loadError = loadError,
                )
            }
        }
    }

}

class GymcalWeekWidget : GymcalBaseWidget(WidgetLayoutMode.Week)

class GymcalMonthWidget : GymcalBaseWidget(WidgetLayoutMode.Month)

class GymcalWeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GymcalWeekWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            // Async: receivers are not a suspend context. No TIME_TICK (battery).
            GymcalWidgetUpdater.requestUpdateAsync(context)
        }
    }
}

class GymcalMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GymcalMonthWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            // Async: receivers are not a suspend context. No TIME_TICK (battery).
            GymcalWidgetUpdater.requestUpdateAsync(context)
        }
    }
}

private val DestKey = ActionParameters.Key<String>(MainActivity.EXTRA_DEST)
private val DateKey = ActionParameters.Key<String>(MainActivity.EXTRA_DATE)

private val TypeLabelMinCellHeight = 45.dp
/** GCal-like geometry; tighter pad/gaps so 7 week+month columns fit launcher width. */
private val WidgetCorner = 28.dp
private val WidgetPadding = 12.dp
private val HeaderRowHeight = 44.dp
private val HeaderGap = 6.dp
private val DayGap = 3.dp
private val DayCorner = 12.dp
private val MonthDayGap = 3.dp
private val MonthDayCorner = 12.dp
/** Today primary ring (app DayCell BorderStroke 2dp primary). */
private val TodayRing = 2.dp
@Composable
private fun WidgetRoot(
    layoutMode: WidgetLayoutMode,
    typesById: Map<String, WorkoutType>,
    workouts: Map<LocalDate, List<String>>,
    colorCache: Map<String, WidgetTypeColors>,
    loadError: Boolean,
) {
    val size = LocalSize.current
    val openCalendar = actionStartActivity<MainActivity>(
        actionParametersOf(DestKey to MainActivity.DEST_CALENDAR),
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetCorner)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openCalendar)
            .padding(WidgetPadding),
    ) {
        if (loadError) {
            ErrorContent(openCalendar)
        } else when (layoutMode) {
            WidgetLayoutMode.Month -> MiniMonthContent(
                typesById = typesById,
                workouts = workouts,
                colorCache = colorCache,
            )
            WidgetLayoutMode.Week -> WeekStripContent(
                typesById = typesById,
                workouts = workouts,
                colorCache = colorCache,
                size = size,
            )
        }
    }
}

@Composable
private fun ErrorContent(openCalendar: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(openCalendar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_open_gymcal),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun OpenPill(openCalendar: Action) {
    Box(
        modifier = GlanceModifier
            .height(32.dp)
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.primary)
            .padding(horizontal = 16.dp)
            .clickable(openCalendar),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_open),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WeekStripContent(
    typesById: Map<String, WorkoutType>,
    workouts: Map<LocalDate, List<String>>,
    colorCache: Map<String, WidgetTypeColors>,
    size: DpSize,
) {
    val locale = Locale.getDefault()
    val weekFields = WeekFields.of(locale)
    val firstDow = weekFields.firstDayOfWeek
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDow))
    // Always exactly 7 days — never crop to 5 (BINDING week7).
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekEnd = days.last()

    // Header 48 + weekday ~14 + gaps; remaining height for day cells.
    val cellHeight = size.height - WidgetPadding * 2 - HeaderRowHeight - 14.dp - HeaderGap - 4.dp
    val showTypeLabels = cellHeight >= TypeLabelMinCellHeight

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WeekHeader(weekStart = weekStart, weekEnd = weekEnd, locale = locale)
        Spacer(modifier = GlanceModifier.height(4.dp))
        WeekdayRow(
            firstDayOfWeek = firstDow,
            locale = locale,
            threeLetter = false,
            gap = DayGap,
            rowHeight = 14.dp,
            fontSize = 11.sp,
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { index, date ->
                val typeIds = workouts[date].orEmpty().take(2)
                val types = typeIds.mapNotNull { typesById[it] }
                val colors = types.mapNotNull { colorCache[it.id] }
                // Gap via start padding — no Spacer siblings (Glance can zero later weights).
                val cellMod = if (index > 0) {
                    GlanceModifier.padding(start = DayGap).defaultWeight().fillMaxHeight()
                } else {
                    GlanceModifier.defaultWeight().fillMaxHeight()
                }
                DayCell(
                    date = date,
                    isToday = date == today,
                    isOutsideMonth = false,
                    types = types,
                    colors = colors,
                    showTypeLabel = showTypeLabels,
                    numberFontSize = 14.sp,
                    corner = DayCorner,
                    modifier = cellMod,
                )
            }
        }
    }
}

@Composable
private fun MiniMonthContent(
    typesById: Map<String, WorkoutType>,
    workouts: Map<LocalDate, List<String>>,
    colorCache: Map<String, WidgetTypeColors>,
) {
    val locale = Locale.getDefault()
    val weekFields = WeekFields.of(locale)
    val firstDow = weekFields.firstDayOfWeek
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    val cells = buildMonthCells(yearMonth, firstDow)
    val weeks = cells.chunked(7)
    val size = LocalSize.current
    val weekRows = weeks.size.coerceAtLeast(1)
    // Header + weekday + gaps; remaining height split across week rows.
    val monthCellHeight = (
        size.height - WidgetPadding * 2 - HeaderRowHeight - 16.dp - 4.dp - 4.dp -
            MonthDayGap * (weekRows - 1)
        ) / weekRows
    val showTypeLabels = monthCellHeight >= TypeLabelMinCellHeight

    Column(modifier = GlanceModifier.fillMaxSize()) {
        MonthHeader(yearMonth = yearMonth, locale = locale)
        Spacer(modifier = GlanceModifier.height(4.dp))
        WeekdayRow(
            firstDayOfWeek = firstDow,
            locale = locale,
            threeLetter = true,
            gap = MonthDayGap,
            rowHeight = 16.dp,
            fontSize = 10.sp,
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            weeks.forEachIndexed { wIndex, week ->
                if (wIndex > 0) {
                    Spacer(modifier = GlanceModifier.height(MonthDayGap))
                }
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEachIndexed { index, date ->
                        val outside = date.month != yearMonth.month
                        val typeIds = if (outside) emptyList() else workouts[date].orEmpty().take(2)
                        val types = typeIds.mapNotNull { typesById[it] }
                        val colors = types.mapNotNull { colorCache[it.id] }
                        val cellMod = if (index > 0) {
                            GlanceModifier.padding(start = MonthDayGap).defaultWeight().fillMaxHeight()
                        } else {
                            GlanceModifier.defaultWeight().fillMaxHeight()
                        }
                        DayCell(
                            date = date,
                            isToday = date == today && !outside,
                            isOutsideMonth = outside,
                            types = types,
                            colors = colors,
                            showTypeLabel = showTypeLabels,
                            numberFontSize = 12.sp,
                            corner = MonthDayCorner,
                            modifier = cellMod,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(weekStart: LocalDate, weekEnd: LocalDate, locale: Locale) {
    val openCalendar = actionStartActivity<MainActivity>(
        actionParametersOf(DestKey to MainActivity.DEST_CALENDAR),
    )
    val range = formatWeekRange(weekStart, weekEnd, locale)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(HeaderRowHeight)
            .clickable(openCalendar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = range,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        OpenPill(openCalendar)
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, locale: Locale) {
    val openCalendar = actionStartActivity<MainActivity>(
        actionParametersOf(DestKey to MainActivity.DEST_CALENDAR),
    )
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    val title = yearMonth.atDay(1).format(formatter)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(HeaderRowHeight)
            .clickable(openCalendar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        OpenPill(openCalendar)
    }
}

@Composable
private fun WeekdayRow(
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
    threeLetter: Boolean,
    gap: Dp,
    rowHeight: Dp,
    fontSize: TextUnit,
) {
    val labels = if (threeLetter) {
        weekdayShortLabels(firstDayOfWeek, locale)
    } else {
        weekdayInitials(firstDayOfWeek, locale)
    }
    Row(modifier = GlanceModifier.fillMaxWidth().height(rowHeight)) {
        labels.forEachIndexed { index, label ->
            val labelMod = if (index > 0) {
                GlanceModifier.padding(start = gap).defaultWeight()
            } else {
                GlanceModifier.defaultWeight()
            }
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = labelMod,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isOutsideMonth: Boolean,
    types: List<WorkoutType>,
    colors: List<WidgetTypeColors>,
    showTypeLabel: Boolean,
    numberFontSize: TextUnit,
    corner: Dp,
    modifier: GlanceModifier,
) {
    val slotCount = minOf(types.size, colors.size).let { n ->
        if (isOutsideMonth) 0 else n
    }
    val hasWorkout = slotCount >= 1
    val isDual = slotCount >= 2
    val openDay = actionStartActivity<MainActivity>(
        actionParametersOf(
            DestKey to MainActivity.DEST_DETAIL,
            DateKey to date.toString(),
        ),
    )

    fun typeFill(c: WidgetTypeColors): GlanceColorProvider = ColorProvider(
        day = c.containerDay,
        night = c.containerNight,
    )

    val emptyFillProvider: GlanceColorProvider = ColorProvider(
        day = emptyCellFill(isOutsideMonth),
        night = emptyCellFill(isOutsideMonth),
    )

    val onProvider: GlanceColorProvider = when {
        isOutsideMonth -> ColorProvider(
            day = emptyCellOn(true),
            night = emptyCellOn(true),
        )
        isDual -> ColorProvider(
            day = androidx.compose.ui.graphics.Color.White,
            night = androidx.compose.ui.graphics.Color.White,
        )
        slotCount == 1 -> ColorProvider(
            day = colors[0].onContainerDay,
            night = colors[0].onContainerNight,
        )
        isToday -> GlanceTheme.colors.primary
        else -> ColorProvider(
            day = emptyCellOn(false),
            night = emptyCellOn(false),
        )
    }

    val typeName = types.firstOrNull()?.name.orEmpty()
    val dualNames = types.take(2).joinToString("+") { it.name }
    val ctx = LocalContext.current
    val desc = buildString {
        append(date.dayOfMonth)
        when {
            isDual -> append(", $dualNames")
            hasWorkout -> append(", $typeName")
            else -> append(", ${ctx.getString(R.string.widget_a11y_none)}")
        }
        if (isToday) append(", ${ctx.getString(R.string.widget_a11y_today)}")
    }

    val label: @Composable () -> Unit = {
        DayCellLabel(
            day = date.dayOfMonth,
            typeName = typeName,
            // Dual labels live in each half (BINDING dual-labels); single under number.
            showTypeLabel = showTypeLabel && slotCount == 1,
            numberFontSize = numberFontSize,
            onProvider = onProvider,
        )
    }

    // Dual B: type name in each half when space allows (BINDING dual-labels); number overlay centered.
    val dualFill: @Composable (GlanceModifier) -> Unit = { innerMod ->
        fun halfOn(c: WidgetTypeColors): GlanceColorProvider = ColorProvider(
            day = c.onContainerDay,
            night = c.onContainerNight,
        )
        Column(modifier = innerMod) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(typeFill(colors[0])),
                contentAlignment = Alignment.Center,
            ) {
                val n0 = types.getOrNull(0)?.name.orEmpty()
                if (showTypeLabel && n0.isNotBlank()) {
                    Text(
                        text = n0,
                        style = TextStyle(
                            color = halfOn(colors[0]),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
                    )
                }
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(typeFill(colors[1])),
                contentAlignment = Alignment.Center,
            ) {
                val n1 = types.getOrNull(1)?.name.orEmpty()
                if (showTypeLabel && n1.isNotBlank()) {
                    Text(
                        text = n1,
                        style = TextStyle(
                            color = halfOn(colors[1]),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }

    when {
        // Today + dual: primary ring around whole cell, split fill inside.
        isToday && isDual -> {
            val innerCorner = (corner.value - TodayRing.value).coerceAtLeast(2f).dp
            Box(
                modifier = modifier
                    .cornerRadius(corner)
                    .background(GlanceTheme.colors.primary)
                    .padding(TodayRing)
                    .semantics { contentDescription = desc }
                    .clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(innerCorner),
                    contentAlignment = Alignment.Center,
                ) {
                    dualFill(
                        GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(innerCorner),
                    )
                    label()
                }
            }
        }
        // Today + single assigned: type fill + primary ring.
        isToday && hasWorkout -> {
            val innerCorner = (corner.value - TodayRing.value).coerceAtLeast(2f).dp
            Box(
                modifier = modifier
                    .cornerRadius(corner)
                    .background(GlanceTheme.colors.primary)
                    .padding(TodayRing)
                    .semantics { contentDescription = desc }
                    .clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(innerCorner)
                        .background(typeFill(colors[0])),
                    contentAlignment = Alignment.Center,
                ) {
                    label()
                }
            }
        }
        // Today empty: EmptyDayFill + primary 2dp ring, number primary.
        isToday && !hasWorkout -> {
            val innerCorner = (corner.value - TodayRing.value).coerceAtLeast(2f).dp
            Box(
                modifier = modifier
                    .cornerRadius(corner)
                    .background(GlanceTheme.colors.primary)
                    .padding(TodayRing)
                    .semantics { contentDescription = desc }
                    .clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(innerCorner)
                        .background(emptyFillProvider),
                    contentAlignment = Alignment.Center,
                ) {
                    label()
                }
            }
        }
        // Dual (not today): horizontal split B — two stacked fills.
        isDual -> {
            Box(
                modifier = modifier
                    .cornerRadius(corner)
                    .semantics { contentDescription = desc }
                    .clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                dualFill(GlanceModifier.fillMaxSize().cornerRadius(corner))
                label()
            }
        }
        // Assigned single (not today): type tonal fill.
        hasWorkout -> {
            Box(
                modifier = modifier
                    .cornerRadius(corner)
                    .background(typeFill(colors[0]))
                    .semantics { contentDescription = desc }
                    .clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                label()
            }
        }
        // Empty: #0F131C fill, no outline (BINDING widget-no-empty-outline).
        else -> {
            Box(
                modifier = modifier
                    .cornerRadius(corner)
                    .background(emptyFillProvider)
                    .semantics { contentDescription = desc }
                    .clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                label()
            }
        }
    }
}

@Composable
private fun DayCellLabel(
    day: Int,
    typeName: String,
    showTypeLabel: Boolean,
    numberFontSize: TextUnit,
    onProvider: GlanceColorProvider,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.toString(),
            style = TextStyle(
                color = onProvider,
                fontSize = numberFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        if (showTypeLabel && typeName.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = typeName,
                style = TextStyle(
                    color = onProvider,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun formatWeekRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    val monthFmt = DateTimeFormatter.ofPattern("MMM", locale)
    fun monthLabel(d: LocalDate): String =
        d.format(monthFmt).trimEnd('.').lowercase(locale)

    return if (start.month == end.month && start.year == end.year) {
        "${start.dayOfMonth}–${end.dayOfMonth} ${monthLabel(start)}"
    } else if (start.year == end.year) {
        "${start.dayOfMonth} ${monthLabel(start)}–${end.dayOfMonth} ${monthLabel(end)}"
    } else {
        "${start.dayOfMonth} ${monthLabel(start)} ${start.year}–${end.dayOfMonth} ${monthLabel(end)} ${end.year}"
    }
}

private fun buildMonthCells(yearMonth: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate> {
    val firstOfMonth = yearMonth.atDay(1)
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val lastOfMonth = yearMonth.atEndOfMonth()
    val lastDow = firstDayOfWeek.plus(6)
    val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(lastDow))
    val days = mutableListOf<LocalDate>()
    var d = gridStart
    while (!d.isAfter(gridEnd)) {
        days += d
        d = d.plusDays(1)
    }
    while (days.size % 7 != 0) {
        days += days.last().plusDays(1)
    }
    return days
}

private fun weekdayInitials(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> {
    val pl = mapOf(
        DayOfWeek.MONDAY to "P",
        DayOfWeek.TUESDAY to "W",
        DayOfWeek.WEDNESDAY to "Ś",
        DayOfWeek.THURSDAY to "C",
        DayOfWeek.FRIDAY to "P",
        DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "N",
    )
    return (0..6).map { offset ->
        val dow = firstDayOfWeek.plus(offset.toLong())
        if (locale.language == "pl") {
            pl[dow] ?: dow.name.take(1)
        } else {
            DateTimeFormatter.ofPattern("EEEEE", locale).format(
                LocalDate.now().with(TemporalAdjusters.nextOrSame(dow)),
            )
        }
    }
}

/** Month: 3-letter weekday labels (Mon… / Pon…). */
private fun weekdayShortLabels(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> {
    val pl = mapOf(
        DayOfWeek.MONDAY to "Pon",
        DayOfWeek.TUESDAY to "Wto",
        DayOfWeek.WEDNESDAY to "Śro",
        DayOfWeek.THURSDAY to "Czw",
        DayOfWeek.FRIDAY to "Pią",
        DayOfWeek.SATURDAY to "Sob",
        DayOfWeek.SUNDAY to "Nie",
    )
    return (0..6).map { offset ->
        val dow = firstDayOfWeek.plus(offset.toLong())
        if (locale.language == "pl") {
            pl[dow] ?: dow.name.take(3)
        } else {
            DateTimeFormatter.ofPattern("EEE", locale).format(
                LocalDate.now().with(TemporalAdjusters.nextOrSame(dow)),
            ).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
        }
    }
}
