package com.konstantyp.gymcal.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as GymcalApp).workoutRepository
        val loaded = runCatching {
            Pair(repository.types.first(), repository.workouts.first())
        }
        val types = loaded.getOrNull()?.first.orEmpty()
        val workouts = loaded.getOrNull()?.second.orEmpty()
        val loadError = loaded.isFailure
        val typesById = types.associateBy { it.id }
        val colorCache = types.associate { it.id to widgetColorsForSeed(it.seedArgb) }

        provideContent {
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

private val TypeLabelMinCellHeight = 56.dp
private val WidgetCorner = 16.dp
private val WidgetPadding = 12.dp
private val DayGap = 6.dp
private val DayCorner = 7.dp
private val DayCellInset = 4.dp
private val MonthDayGap = 5.dp
private val MonthDayCorner = 5.dp
private val MonthDayCellInset = 2.dp
private val HeaderGap = 8.dp
private val TodayStroke = 2.dp

@Composable
private fun WidgetRoot(
    layoutMode: WidgetLayoutMode,
    typesById: Map<String, WorkoutType>,
    workouts: Map<LocalDate, String>,
    colorCache: Map<String, WidgetTypeColors>,
    loadError: Boolean,
) {
    val size = LocalSize.current
    val openCalendar = actionStartActivity<MainActivity>(
        actionParametersOf(DestKey to MainActivity.DEST_CALENDAR),
    )

    val contentPadding = if (layoutMode == WidgetLayoutMode.Month) 8.dp else WidgetPadding
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetCorner)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openCalendar)
            .padding(contentPadding),
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
private fun WeekStripContent(
    typesById: Map<String, WorkoutType>,
    workouts: Map<LocalDate, String>,
    colorCache: Map<String, WidgetTypeColors>,
    size: DpSize,
) {
    val locale = Locale.getDefault()
    val weekFields = WeekFields.of(locale)
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekEnd = days.last()

    val cellHeight = size.height - WidgetPadding * 2 - 18.dp - HeaderGap
    val showTypeLabels = cellHeight >= TypeLabelMinCellHeight

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WeekHeader(weekStart = weekStart, weekEnd = weekEnd, locale = locale)
        Spacer(modifier = GlanceModifier.height(HeaderGap))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { index, date ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(DayGap))
                }
                val typeId = workouts[date]
                val type = typeId?.let { typesById[it] }
                DayCell(
                    date = date,
                    isToday = date == today,
                    isOutsideMonth = false,
                    type = type,
                    colors = type?.let { colorCache[it.id] },
                    showTypeLabel = showTypeLabels,
                    numberFontSize = 13.sp,
                    corner = DayCorner,
                    inset = DayCellInset,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun MiniMonthContent(
    typesById: Map<String, WorkoutType>,
    workouts: Map<LocalDate, String>,
    colorCache: Map<String, WidgetTypeColors>,
) {
    val locale = Locale.getDefault()
    val weekFields = WeekFields.of(locale)
    val firstDow = weekFields.firstDayOfWeek
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    val cells = buildMonthCells(yearMonth, firstDow)
    val weeks = cells.chunked(7)

    // Compact header so 5–6 equal-weight week rows always fit without clipping.
    Column(modifier = GlanceModifier.fillMaxSize()) {
        MonthHeader(yearMonth = yearMonth, locale = locale)
        Spacer(modifier = GlanceModifier.height(4.dp))
        WeekdayRow(firstDayOfWeek = firstDow, locale = locale)
        Spacer(modifier = GlanceModifier.height(2.dp))
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
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.width(MonthDayGap))
                        }
                        val outside = date.month != yearMonth.month
                        val typeId = if (outside) null else workouts[date]
                        val type = typeId?.let { typesById[it] }
                        DayCell(
                            date = date,
                            isToday = date == today && !outside,
                            isOutsideMonth = outside,
                            type = type,
                            colors = type?.let { colorCache[it.id] },
                            showTypeLabel = false,
                            numberFontSize = 10.sp,
                            corner = MonthDayCorner,
                            inset = MonthDayCellInset,
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
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
            .clickable(openCalendar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = range,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = "Gymcal",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
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
            .clickable(openCalendar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = "Gymcal",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun WeekdayRow(firstDayOfWeek: DayOfWeek, locale: Locale) {
    val initials = weekdayInitials(firstDayOfWeek, locale)
    Row(modifier = GlanceModifier.fillMaxWidth().height(16.dp)) {
        initials.forEachIndexed { index, label ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.width(MonthDayGap))
            }
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isOutsideMonth: Boolean,
    type: WorkoutType?,
    colors: WidgetTypeColors?,
    showTypeLabel: Boolean,
    numberFontSize: TextUnit,
    corner: Dp,
    inset: Dp,
    modifier: GlanceModifier,
) {
    val hasWorkout = type != null && colors != null && !isOutsideMonth
    val openDay = actionStartActivity<MainActivity>(
        actionParametersOf(
            DestKey to MainActivity.DEST_DETAIL,
            DateKey to date.toString(),
        ),
    )

    val emptyFillDay = emptyCellFill(dark = false).let {
        if (isOutsideMonth) it.copy(alpha = 0.50f) else it
    }
    val emptyFillNight = emptyCellFill(dark = true).let {
        if (isOutsideMonth) it.copy(alpha = 0.50f) else it
    }
    val fillProvider: GlanceColorProvider = when {
        hasWorkout -> ColorProvider(
            day = colors!!.containerDay,
            night = colors.containerNight,
        )
        else -> ColorProvider(
            day = emptyFillDay,
            night = emptyFillNight,
        )
    }

    val onProvider: GlanceColorProvider = when {
        isOutsideMonth -> ColorProvider(
            day = outsideDayOnColor(false),
            night = outsideDayOnColor(true),
        )
        hasWorkout -> ColorProvider(
            day = colors!!.onContainerDay,
            night = colors.onContainerNight,
        )
        isToday -> GlanceTheme.colors.primary
        else -> ColorProvider(
            day = emptyCellOnColor(false),
            night = emptyCellOnColor(true),
        )
    }

    val typeName = type?.name.orEmpty()
    val ctx = LocalContext.current
    val desc = buildString {
        append(date.dayOfMonth)
        if (hasWorkout) append(", $typeName") else append(", ${ctx.getString(R.string.widget_a11y_none)}")
        if (isToday) append(", ${ctx.getString(R.string.widget_a11y_today)}")
    }

    // Inset before background so colored rect is ~80% of grid slot (Glance has no scale).
    val cellModifier = if (isToday) {
        GlanceModifier
            .then(modifier)
            .padding(inset)
            .cornerRadius(corner)
            .background(GlanceTheme.colors.primary)
            .padding(TodayStroke)
            .semantics { contentDescription = desc }
            .clickable(openDay)
    } else {
        GlanceModifier
            .then(modifier)
            .padding(inset)
            .cornerRadius(corner)
            .background(fillProvider)
            .semantics { contentDescription = desc }
            .clickable(openDay)
    }

    Box(
        modifier = cellModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius((corner.value - TodayStroke.value).coerceAtLeast(2f).dp)
                    .background(fillProvider),
                contentAlignment = Alignment.Center,
            ) {
                DayCellLabel(
                    day = date.dayOfMonth,
                    typeName = typeName,
                    showTypeLabel = showTypeLabel && hasWorkout,
                    numberFontSize = numberFontSize,
                    onProvider = onProvider,
                )
            }
        } else {
            DayCellLabel(
                day = date.dayOfMonth,
                typeName = typeName,
                showTypeLabel = showTypeLabel && hasWorkout,
                numberFontSize = numberFontSize,
                onProvider = onProvider,
            )
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
                    fontSize = 9.sp,
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
