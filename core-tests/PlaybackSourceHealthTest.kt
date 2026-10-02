package com.eafb

fun main() {
    val observations = listOf(
        SourceHealthObservation("licensed", true, 1000),
        SourceHealthObservation("licensed", false, 1100),
        SourceHealthObservation("backup", false, 900),
        SourceHealthObservation("backup", true, 1050),
        SourceHealthObservation("stale", true, 100),
        SourceHealthObservation("future", true, 1200),
        SourceHealthObservation("", true, 1100)
    )
    check(PlaybackSourceHealth.healthyIds(observations, 1150, 100) == setOf("backup"))
    check(PlaybackSourceHealth.healthyIds(observations, 1200, 100) == setOf("future"))
    check(PlaybackSourceHealth.healthyIds(observations, 2000, 100).isEmpty())
    check(PlaybackSourceHealth.healthyIds(emptyList(), 1000).isEmpty())
    check(runCatching {
        PlaybackSourceHealth.healthyIds(observations, -1)
    }.isFailure)
    check(runCatching {
        PlaybackSourceHealth.healthyIds(observations, 1000, 0)
    }.isFailure)
    println("PASS: source health expires and latest negative observation wins")
}
