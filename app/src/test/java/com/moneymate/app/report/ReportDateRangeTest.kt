package com.moneymate.app.report

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDateRangeTest {
    @Test fun rollingPeriodsIncludeTodayAndTheExpectedNumberOfCalendarDays() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10, 15, 30, 0) }.timeInMillis
        listOf(ReportPeriod.LAST_WEEK to 7, ReportPeriod.TWO_WEEKS to 14, ReportPeriod.MONTH to 30)
            .forEach { (period, days) ->
                val range = reportDateRange(period, now = now)
                val firstDay = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -(days - 1))
                }.timeInMillis
                assertEquals(firstDay, range.startMillis)
                assertFalse(range.contains(firstDay - 1))
                assertTrue(range.contains(firstDay))
                assertTrue(range.contains(now))
                assertFalse(range.contains(range.endExclusiveMillis))
            }
    }

    @Test fun customRangeUsesExactInclusiveStartAndExclusiveEnd() {
        val range = reportDateRange(ReportPeriod.CUSTOM, 1000L, 2000L)
        assertFalse(range.contains(999L))
        assertTrue(range.contains(1000L))
        assertTrue(range.contains(1999L))
        assertFalse(range.contains(2000L))
    }
}
