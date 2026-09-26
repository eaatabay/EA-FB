package com.eafb

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Client-side guard: upstream feeds and popular fallback may contain future titles. */
object CatalogReleasePolicy {
    private val datePattern = Regex("\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])")

    fun released(date: String?, nowMillis: Long): Boolean {
        if (date.isNullOrBlank() || !datePattern.matches(date)) return false
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pos = ParsePosition(0)
        val premiere = parser.parse(date, pos) ?: return false
        if (pos.index != date.length) return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(nowMillis))
        return date <= today && premiere.time <= nowMillis
    }
}
