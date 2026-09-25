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
    println("PASS: 8/8 episode air-date assertions")
}
