package com.eafb

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val now = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse("2026-09-25")!!.time
    val tomorrow = EpisodeAirPolicy.parse("2026-09-26", now)!!
    check(tomorrow.showNativeCountdown)
    check(tomorrow.dateLabel == "26.09.2026")
    check(!EpisodeAirPolicy.parse("2026-10-10", now)!!.showNativeCountdown)
    check(!EpisodeAirPolicy.parse("2026-10-09", now)!!.showNativeCountdown)
    check(EpisodeAirPolicy.parse("2026-09-25", now) == null)
    check(EpisodeAirPolicy.parse("2026-09-20", now) == null)
    check(EpisodeAirPolicy.parse("2026-02-30", now) == null)
    check(EpisodeAirPolicy.parse("not-a-date", now) == null)
    val oct2 = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        .parse("2026-10-02")!!.time
    // Reported Cennetin Doğusu case: episode aired on October 1, but the
    // detail screen still claimed it was the next upcoming episode on Oct 2.
    check(EpisodeAirPolicy.nextAirDateLabel(
        "2026-10-01", 1, 7, oct2) == null)
    check(EpisodeAirPolicy.nextAirDateLabel(
        "2026-10-02", 1, 8, oct2) == null)
    val future = EpisodeAirPolicy.nextAirDateLabel(
        "2026-10-03", 1, 9, oct2)
    check(future != null && future.startsWith("Sonraki bölüm (S1 B9): 3 Ekim 2026"))
    check(EpisodeAirPolicy.nextAirDateLabel(
        "2026-02-30", 1, 10, oct2) == null)
    check(EpisodeAirPolicy.nextAirDateLabel(
        "", 1, 10, oct2) == null)
    // Air-date is calendar-local: the same past-date rejection must hold on
    // a Mi Box in Türkiye, not only in the UTC CI fixture.
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
    val istanbulOct2 = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        .parse("2026-10-02")!!.time
    check(EpisodeAirPolicy.nextAirDateLabel(
        "2026-10-01", 1, 7, istanbulOct2) == null)
    check(EpisodeAirPolicy.nextAirDateLabel(
        "2026-10-02", 1, 8, istanbulOct2) == null)
    fun dateMillis(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(value)!!.time
    fun candidate(season: Int, episode: Int, date: String) =
        EpisodeAirPolicy.Candidate(season, episode, dateMillis(date))
    val today = dateMillis("2026-10-02")
    // Stale next_episode_to_air points at episode 7 on October 1.
    // The season list already knows about episode 8 on October 9.
    val weekly = EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 7, "2026-10-01"),
        candidate(1, 8, "2026-10-09"),
        candidate(1, 9, "2026-10-16")
    ), today)
    check(weekly == candidate(1, 8, "2026-10-09"))
    check(EpisodeAirPolicy.label(weekly!!).startsWith(
        "Sonraki bölüm (S1 B8): 9 Ekim 2026"))
    // Season finale: all episode dates have already passed.
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 7, "2026-10-01")
    ), today) == null)
    // Same-day batch release is not treated as a future episode.
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 1, "2026-10-02"),
        candidate(1, 2, "2026-10-02"),
        candidate(1, 8, "2026-10-02")
    ), today) == null)
    // TMDb hint can be stale, missing, or even later than a season row.
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 10, "2026-10-23"),
        candidate(1, 8, "2026-10-09")
    ), today) == candidate(1, 8, "2026-10-09"))
    check(EpisodeAirPolicy.nearestFuture(emptyList(), today) == null)
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(0, 1, "2026-10-03"),
        candidate(1, 0, "2026-10-03"),
        EpisodeAirPolicy.Candidate(1, 3, -1L),
        candidate(2, 1, "2026-10-05")
    ), today) == candidate(2, 1, "2026-10-05"))
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 3, "2026-10-03"),
        candidate(1, 2, "2026-10-03")
    ), today) == candidate(1, 2, "2026-10-03"))
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 7, "2026-10-01"),
        candidate(1, 8, "2026-10-09")
    ), dateMillis("2026-10-10")) == null)
    // Türkiye-local midnight must agree with UTC date-only behavior.
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
    val trToday = dateMillis("2026-10-02")
    check(EpisodeAirPolicy.nearestFuture(listOf(
        candidate(1, 1, "2026-10-02"),
        candidate(1, 2, "2026-10-03")
    ), trToday) == candidate(1, 2, "2026-10-03"))
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    println("PASS: episode air-date, batch release, season finale, stale hint and nearest future")
}
