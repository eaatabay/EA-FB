package com.eafb

fun main() {
    check(DiziYouEpisodeParser.exactEpisode("1. Sezon 7. Bölüm", 1, 7))
    check(!DiziYouEpisodeParser.exactEpisode("1. Sezon 7. Bölüm", 1, 8))
    check(!DiziYouEpisodeParser.exactEpisode("1. Sezon 7. Bölüm", 2, 7))
    check(DiziYouEpisodeParser.playerId("https://www.diziyou.one/player/abc-123.html", "www.diziyou.one") == "abc-123")
    check(DiziYouEpisodeParser.playerId("https://evil.example/player/abc.html", "www.diziyou.one") == null)
    // Current CLEAN already permits query/fragment and real DiziYou subdomains.
    check(DiziYouEpisodeParser.playerId("https://www.diziyou.one/player/abc.html?x=1", "www.diziyou.one") == "abc")
    check(DiziYouEpisodeParser.playerId("https://player.diziyou.one/abc.html#player", "www.diziyou.one") == "abc")
    check(DiziYouEpisodeParser.playerId("https://diziyou.one.evil.example/abc.html", "www.diziyou.one") == null)
    check(DiziYouEpisodeParser.playerId("https://user@www.diziyou.one/abc.html", "www.diziyou.one") == null)
    check(DiziYouEpisodeParser.playerId("http://www.diziyou.one/abc.html", "www.diziyou.one") == null)
    println("DiziYou parser: 10/10 PASS")
}
