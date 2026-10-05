package com.adshield.util

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Formatters {
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dayTime = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

    fun dateTime(millis: Long): String = dateTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** Hora si el evento es de hoy; día y hora si es anterior. */
    fun eventTime(millis: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val moment = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (moment.toLocalDate() == today) time.format(moment) else dayTime.format(moment)
    }

    fun count(value: Long): String = NumberFormat.getIntegerInstance().format(value)
}
