package com.eafb

fun main() {
    val movie = PlaybackData.movie(42)
    val episode = PlaybackData.episode(42, 3, 2)
    check(movie == "ea-fb:movie:42")
    check(episode == "ea-fb:episode:42:3:2")
    check(PlaybackData.parse(movie) == PlaybackData.Movie(42))
    check(PlaybackData.parse(episode) == PlaybackData.Episode(42, 3, 2))
    check(PlaybackData.parse(PlaybackData.episode(42, 0, 1)) ==
        PlaybackData.Episode(42, 0, 1))
    for (bad in listOf("", "ea-fb:movie:0", "ea-fb:movie:-1",
        "ea-fb:movie:0042", "ea-fb:movie:42:extra", "ea-fb:episode:42:1:0",
        "ea-fb:episode:42:-1:1", "ea-fb:episode:42:1",
        "ea-fb:episode:42:1:2:extra", "ea-fb:episode:42:1:+2",
        "ea-fb:live:42", "ea-fb:movie:999999999999999999999",
        "http://example.org/film")) {
        check(PlaybackData.parse(bad) == null) { "Accepted invalid data: $bad" }
    }
    check(runCatching { PlaybackData.movie(0) }.isFailure)
    check(runCatching { PlaybackData.episode(42, -1, 1) }.isFailure)
    check(runCatching { PlaybackData.episode(42, 1, 0) }.isFailure)
    println("PASS: movie/episode playback identity codec")
}
