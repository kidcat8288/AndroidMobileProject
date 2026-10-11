package com.moneymate.app.report

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod(val label: String, val days: Int) {
    LAST_WEEK("Last week", 7),
    TWO_WEEKS("Two weeks", 14),
    MONTH("Month", 30),
    CUSTOM("Custom range", 0)
}

data class ReportDateRange(val startMillis: Long, val endExclusiveMillis: Long) {
    fun contains(timestamp: Long): Boolean = timestamp >= startMillis && timestamp < endExclusiveMillis
}

fun reportDateRange(period: ReportPeriod, customStartMillis: Long = 0L, customEndExclusiveMillis: Long = 0L, now: Long = System.currentTimeMillis()): ReportDateRange {
    if (period == ReportPeriod.CUSTOM && customEndExclusiveMillis > customStartMillis) {
        return ReportDateRange(customStartMillis, customEndExclusiveMillis)
    }
    val today = startOfDay(now)
    val start = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -(period.days.coerceAtLeast(1) - 1))
    }.timeInMillis
    return ReportDateRange(start, nextDay(today))
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun nextDay(dayStart: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_YEAR, 1)
}.timeInMillis

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDateControls(
    selected: ReportPeriod,
    range: ReportDateRange,
    onSelect: (ReportPeriod, Long, Long) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selected == period,
                    onClick = {
                        if (period != ReportPeriod.CUSTOM) {
                            onSelect(period, 0L, 0L)
                        } else {
                            val startCalendar = Calendar.getInstance().apply { timeInMillis = range.startMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val start = Calendar.getInstance().apply {
                                        set(year, month, day, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    val endCalendar = Calendar.getInstance().apply { timeInMillis = range.endExclusiveMillis - 1 }
                                    DatePickerDialog(
                                        context,
                                        { _, endYear, endMonth, endDay ->
                                            val endDayStart = Calendar.getInstance().apply {
                                                set(endYear, endMonth, endDay, 0, 0, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            onSelect(ReportPeriod.CUSTOM, start, nextDay(endDayStart))
                                        },
                                        endCalendar.get(Calendar.YEAR),
                                        endCalendar.get(Calendar.MONTH),
                                        endCalendar.get(Calendar.DAY_OF_MONTH)
                                    ).apply {
                                        setTitle("Select end date")
                                        datePicker.minDate = start
                                        show()
                                    }
                                },
                                startCalendar.get(Calendar.YEAR),
                                startCalendar.get(Calendar.MONTH),
                                startCalendar.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                setTitle("Select start date")
                                show()
                            }
                        }
                    },
                    label = { Text(period.label) }
                )
            }
        }
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
        Text("${dateFormat.format(Date(range.startMillis))} – ${dateFormat.format(Date(range.endExclusiveMillis - 1))}")
    }
}
