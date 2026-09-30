package com.eafb

fun main() {
    check(EpisodeRowPolicy.episodeNumber("3. The Crossing", "Bölüm") == 3)
    check(EpisodeRowPolicy.episodeNumber("Bölüm 4", "Bölüm") == 4)
    check(EpisodeRowPolicy.episodeNumber("Episode 12", "Bölüm") == 12)
    check(EpisodeRowPolicy.episodeNumber("12. Bölüm", "Bölüm") == 12)
    check(EpisodeRowPolicy.episodeNumber("15 Nisan 2026", "Bölüm") == null)
    check(EpisodeRowPolicy.rowName("3. The Crossing", "Bölüm") == "The Crossing")
    check(EpisodeRowPolicy.rowName("Bölüm 4", "Bölüm").isEmpty())
    check(EpisodeRowPolicy.seasonNumber("Sezon 2", "Sezon") == 2)
    check(EpisodeRowPolicy.seasonNumber("Season 3", "Sezon") == 3)
    check(EpisodeRowPolicy.seasonNumber("2. Sezon", "Sezon") == 2)
    check(EpisodeRowPolicy.seasonNumber("Sezon 4 Final", "Sezon") == 4)
    check(EpisodeRowPolicy.seasonNumber("Bölüm 4", "Sezon") == null)
    println("PASS: 12/12 episode row and season identity assertions")
}
