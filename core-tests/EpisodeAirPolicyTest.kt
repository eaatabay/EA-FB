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
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    println("PASS: episode air-date and stale detail label assertions")
}
