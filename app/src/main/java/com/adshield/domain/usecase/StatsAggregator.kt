package com.adshield.domain.usecase

import com.adshield.domain.model.StatsBucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class StatsRange(val label: String, val days: Int) {
    DAY("24 horas", 1),
    WEEK("7 días", 7),
    MONTH("30 días", 30)
}

data class ChartBar(val label: String, val blocked: Long, val allowed: Long)

/** Convierte contadores por hora en las barras de la gráfica. Lógica pura, sin Android. */
object StatsAggregator {

    const val HOUR_MS = 3_600_000L

    fun hourStart(timestamp: Long): Long = timestamp - (timestamp % HOUR_MS)

    /** Inicio (en ms) del periodo que cubre [range] contando hacia atrás desde [now]. */
    fun rangeStart(range: StatsRange, now: Long, zone: ZoneId): Long = when (range) {
        StatsRange.DAY -> hourStart(now) - 23 * HOUR_MS
        else -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .minusDays((range.days - 1).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun startOfToday(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun buildBars(range: StatsRange, now: Long, zone: ZoneId, buckets: List<StatsBucket>): List<ChartBar> {
        val from = rangeStart(range, now, zone)
        val inRange = buckets.filter { it.hourStart >= from }

        if (range == StatsRange.DAY) {
            val byHour = inRange.associateBy { it.hourStart }
            return (0 until 24).map { i ->
                val start = from + i * HOUR_MS
                val hour = Instant.ofEpochMilli(start).atZone(zone).hour
                val bucket = byHour[start]
                ChartBar("%02d".format(hour), bucket?.blocked ?: 0, bucket?.allowed ?: 0)
            }
        }

        val byDay = HashMap<LocalDate, LongArray>()
        for (bucket in inRange) {
            val day = Instant.ofEpochMilli(bucket.hourStart).atZone(zone).toLocalDate()
            val acc = byDay.getOrPut(day) { LongArray(2) }
            acc[0] += bucket.blocked
            acc[1] += bucket.allowed
        }
        val firstDay = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        return (0 until range.days).map { i ->
            val day = firstDay.plusDays(i.toLong())
            val acc = byDay[day]
            ChartBar("%02d/%02d".format(day.dayOfMonth, day.monthValue), acc?.get(0) ?: 0, acc?.get(1) ?: 0)
        }
    }
}
