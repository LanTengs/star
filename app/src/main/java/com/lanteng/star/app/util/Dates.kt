package com.lanteng.star.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Dates {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun day(millis: Long?): String =
        if (millis == null) "" else dayFormat.format(Date(millis))

    fun stamp(millis: Long): String = stampFormat.format(Date(millis))

    /** Calendar year of an epoch-millis value, used for the usage breakdown. */
    fun yearOf(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.YEAR)
    }

    fun now(): Long = System.currentTimeMillis()
}
