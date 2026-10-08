package com.eafb

fun main() {
    check(EpisodeRouteMatcher.matches("1. Sezon 3. Bölüm", "https://example.org/ep/3", 1, 3))
    check(EpisodeRouteMatcher.matches("3. Bölüm", "https://example.org/dizi/1-sezon-3-bolum/", 1, 3))
    check(EpisodeRouteMatcher.matches("", "https://example.org/dizi/s02e04-izle", 2, 4))
    check(EpisodeRouteMatcher.matches("Season 3 Episode 12", "https://example.org/ep", 3, 12))
    check(EpisodeRouteMatcher.matches("1x08", "https://example.org/ep", 1, 8))
    check(!EpisodeRouteMatcher.matches("3. Bölüm", "https://example.org/ep/3", 2, 3))
    check(!EpisodeRouteMatcher.matches("2. Sezon 4. Bölüm", "https://example.org/ep/4", 2, 5))
    check(!EpisodeRouteMatcher.matches("", "https://example.org/dizi/", 1, 1))
    println("PASS: episode route matching and rejection fixtures")
}
