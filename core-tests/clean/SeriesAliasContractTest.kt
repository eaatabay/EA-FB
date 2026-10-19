package com.eafb

/** Confirmed TMDb/IMDb/season evidence is recorded in docs/series-name-codex. */
fun main() {
    val query = MediaQuery("Organize İşler", 2026, MediaKind.SERIES, 320754, 1, 8)
    val alias = "Organize İşler: Karun Hazinesi"
    check(alias in SourceSearchTitles.candidates(query)) { "Confirmed S1 alias is missing" }
    for (invalid in listOf(query.copy(tmdbId = 30634), query.copy(tmdbId = 577990),
        query.copy(kind = MediaKind.MOVIE), query.copy(year = 2005), query.copy(year = null),
        query.copy(season = 0), query.copy(season = 2), query.copy(episode = 0),
        query.copy(episode = 9), query.copy(title = "Organize İşler: Sazan Sarmalı"),
        query.copy(title = "Unrelated"))) {
        check(alias !in SourceSearchTitles.candidates(invalid)) { "Alias leaked to a different identity: $invalid" }
    }
    for (episode in 1..8) check(alias in SourceSearchTitles.candidates(query.copy(episode = episode)))
    check(SourceSearchTitles.candidates(query.copy(tmdbId = 42)) == listOf(query.title))
    println("PASS: confirmed alias confined to TV 320754 / 2026 / S1 E1..8; movies and other identities rejected")
}
