package com.eafb

import java.text.SimpleDateFormat
import java.util.Locale

/** Avoid stock-app countdowns spanning weeks or months. */
object EpisodeAirPolicy {
    private const val NATIVE_WINDOW_DAYS = 14L

    data class Airing(val unixSeconds: Long, val dateLabel: String, val showNativeCountdown: Boolean)

    /**
     * TMDb can temporarily retain an already aired episode in
     * next_episode_to_air. Use the same date gate as native nextAiring so
     * the custom detail row never displays yesterday as "Sonraki bölüm".
     */
    fun nextAirDateLabel(
        date: String,
        season: Int?,
        episode: Int?,
        nowMillis: Long
    ): String? {
        parse(date, nowMillis) ?: return null
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
        }.parse(date) ?: return null
        val label = SimpleDateFormat(
            "d MMMM yyyy EEEE", Locale("tr", "TR")
        ).format(parsed)
        val number = listOfNotNull(
            season?.takeIf { it > 0 }?.let { "S$it" },
            episode?.takeIf { it > 0 }?.let { "B$it" }
        ).joinToString(" ")
        return "Sonraki bölüm" +
            (if (number.isNotEmpty()) " ($number)" else "") + ": $label"
    }

    /** Candidate dates come from TMDb's next_episode_to_air or season episodes. */
    data class Candidate(
        val season: Int,
        val episode: Int,
        val dateMillis: Long
    )

    /**
     * Select the nearest future calendar day, regardless of whether TMDb's
     * next_episode_to_air has become stale. A same-day batch release is not
     * "upcoming" without a trustworthy publication time.
     *
     * Ignore specials (season 0), missing numbers, unknown dates and invalid
     * timestamps. This is metadata selection only; no network or playback.
     */
    fun nearestFuture(
        candidates: List<Candidate>,
        nowMillis: Long
    ): Candidate? {
        if (nowMillis < 0) return null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            .format(java.util.Date(nowMillis))
        return candidates.asSequence()
            .filter { it.season > 0 && it.episode > 0 && it.dateMillis > 0 }
            .filter { candidate ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                    .format(java.util.Date(candidate.dateMillis))
                date > today
            }
            .minWithOrNull(
                compareBy<Candidate> { it.dateMillis }
                    .thenBy { it.season }
                    .thenBy { it.episode }
            )
    }

    fun label(candidate: Candidate): String {
        val formatted = SimpleDateFormat(
            "d MMMM yyyy EEEE", Locale("tr", "TR")
        ).format(java.util.Date(candidate.dateMillis))
        return "Sonraki bölüm (S${candidate.season} B${candidate.episode}): $formatted"
    }

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
