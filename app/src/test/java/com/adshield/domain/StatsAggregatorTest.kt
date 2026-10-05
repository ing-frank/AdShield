package com.adshield.domain

import com.adshield.domain.model.StatsBucket
import com.adshield.domain.model.StatsTotals
import com.adshield.domain.usecase.StatsAggregator
import com.adshield.domain.usecase.StatsRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class StatsAggregatorTest {

    private val utc = ZoneId.of("UTC")
    private val hour = StatsAggregator.HOUR_MS
    // 2026-10-05 17:30:00 UTC
    private val now = 1_791_221_400_000L

    @Test
    fun totalsSumEveryBucket() {
        val totals = StatsTotals.of(
            listOf(
                StatsBucket(0, allowed = 10, ads = 1, trackers = 2, threats = 3, custom = 4),
                StatsBucket(hour, allowed = 5, ads = 1)
            )
        )
        assertEquals(15L, totals.allowed)
        assertEquals(2L, totals.ads)
        assertEquals(11L, totals.blocked)
        assertEquals(26L, totals.total)
    }

    @Test
    fun hourStartTruncatesToTheHour() {
        assertEquals(now - 30 * 60_000, StatsAggregator.hourStart(now))
    }

    @Test
    fun dayRangeProducesTwentyFourHourlyBarsEndingNow() {
        val current = StatsAggregator.hourStart(now)
        val bars = StatsAggregator.buildBars(
            StatsRange.DAY, now, utc,
            listOf(
                StatsBucket(current, allowed = 7, ads = 3),
                StatsBucket(current - 23 * hour, trackers = 2),
                StatsBucket(current - 24 * hour, ads = 99) // fuera del rango
            )
        )
        assertEquals(24, bars.size)
        assertEquals("17", bars.last().label)
        assertEquals(3L, bars.last().blocked)
        assertEquals(7L, bars.last().allowed)
        assertEquals("18", bars.first().label)
        assertEquals(2L, bars.first().blocked)
        assertEquals(5L, bars.sumOf { it.blocked })
    }

    @Test
    fun weekRangeGroupsByDay() {
        val today = StatsAggregator.startOfToday(now, utc)
        val bars = StatsAggregator.buildBars(
            StatsRange.WEEK, now, utc,
            listOf(
                StatsBucket(today + 2 * hour, ads = 1),
                StatsBucket(today + 9 * hour, trackers = 4, allowed = 10),
                StatsBucket(today - 6 * 24 * hour, threats = 2),
                StatsBucket(today - 7 * 24 * hour, ads = 50) // fuera del rango
            )
        )
        assertEquals(7, bars.size)
        assertEquals("05/10", bars.last().label)
        assertEquals(5L, bars.last().blocked)
        assertEquals(10L, bars.last().allowed)
        assertEquals("29/09", bars.first().label)
        assertEquals(2L, bars.first().blocked)
    }

    @Test
    fun monthRangeHasThirtyBars() {
        assertEquals(30, StatsAggregator.buildBars(StatsRange.MONTH, now, utc, emptyList()).size)
    }
}
