package com.eafb

import java.text.SimpleDateFormat
import java.util.Locale

/** Avoid stock-app countdowns spanning weeks or months. */
object EpisodeAirPolicy {
    private const val NATIVE_WINDOW_DAYS = 14L

    data class Airing(val unixSeconds: Long, val dateLabel: String, val showNativeCountdown: Boolean)

    fun parse(date: String, nowMillis: Long): Airing? {
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) return null
        val millis = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
                .parse(date)?.time
        }.getOrNull() ?: return null
        if (millis <= nowMillis) return null
        val daysAway = (millis - nowMillis) / 86_400_000L
        val label = date.substring(8, 10) + "." + date.substring(5, 7) +
            "." + date.substring(0, 4)
        return Airing(millis / 1_000L, label, daysAway < NATIVE_WINDOW_DAYS)
    }
}
