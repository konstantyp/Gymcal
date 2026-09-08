package com.konstantyp.gymcal.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.data.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@Composable
fun MonthGrid(
    yearMonth: YearMonth,
    workouts: Map<LocalDate, List<String>>,
    typesById: Map<String, WorkoutType>,
    selectedDate: LocalDate?,
    today: LocalDate = LocalDate.now(),
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = remember(yearMonth) { buildMonthCells(yearMonth) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    val outside = date.month != yearMonth.month
                    val ids = if (outside) emptyList() else workouts[date].orEmpty()
                        .filter { typesById.containsKey(it) }
                        .take(2)
                    val names = ids.mapNotNull { typesById[it]?.name }
                    DayCell(
                        dayOfMonth = date.dayOfMonth,
                        isToday = date == today,
                        isOutsideMonth = outside,
                        isSelected = selectedDate == date,
                        typeIds = ids,
                        typeNames = names,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Monday-first grid covering the visible month (5–6 weeks). */
private fun buildMonthCells(yearMonth: YearMonth): List<LocalDate> {
    val firstOfMonth = yearMonth.atDay(1)
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastOfMonth = yearMonth.atEndOfMonth()
    val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = mutableListOf<LocalDate>()
    var d = gridStart
    while (!d.isAfter(gridEnd)) {
        days += d
        d = d.plusDays(1)
    }
    return days
}
