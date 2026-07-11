package com.plushledger.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapPeriodTest {
    private val today = LocalDate.of(2026, 7, 10)

    @Test
    fun threeMonthsStartsAtFirstDayOfThirdLatestCalendarMonth() {
        assertEquals(LocalDate.of(2026, 5, 1), heatmapVisibleStart(today, HeatmapPeriod.THREE_MONTHS))
    }

    @Test
    fun sixMonthsStartsAtFirstDayOfSixthLatestCalendarMonth() {
        assertEquals(LocalDate.of(2026, 2, 1), heatmapVisibleStart(today, HeatmapPeriod.SIX_MONTHS))
    }

    @Test
    fun yearStartsOnJanuaryFirst() {
        assertEquals(LocalDate.of(2026, 1, 1), heatmapVisibleStart(today, HeatmapPeriod.YEAR))
    }
}
