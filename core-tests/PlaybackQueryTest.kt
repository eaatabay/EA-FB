package com.eafb

fun main() {
    val movie = PlaybackQuery.fromData("ea-fb:movie:42", "İsyan", 2026)
    check(movie == MediaQuery("İsyan", 2026, MediaKind.MOVIE, 42))
    val episode = PlaybackQuery.fromData(
        "ea-fb:episode:42:3:2", "İsyan", 2026)
    check(episode == MediaQuery("İsyan", 2026, MediaKind.SERIES, 42, 3, 2))
    check(PlaybackQuery.fromData("ea-fb:episode:42:3:3", "İsyan", 2026)
        != episode)
    check(PlaybackQuery.fromData("ea-fb:episode:42:3:0", "İsyan", 2026)
        == null)
    check(PlaybackQuery.fromData("ea-fb:movie:42", "", 2026) == null)
    check(PlaybackQuery.fromData("ea-fb:open:big-buck-bunny",
        "Big Buck Bunny", 2008) == null)
    check(PlaybackQuery.fromData("ea-fb:live:abc", "Canlı TV", null)
        == null)
    println("PASS: exact movie and episode query mapping")
}
