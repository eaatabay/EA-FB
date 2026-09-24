package com.eafb

fun main() {
    check(HomeCategories.all.size == 28) { "Expected all 28 documented home sections" }
    check(HomeCategories.all.map { it.id }.distinct().size == 28)
    check(Identity.normalize("Dizi İZLE: ŞAFAK ı") == "dizi izle safak i")
    check(Identity.mediaKey(MediaKind.MOVIE, "İsyan", 2026) == "MOVIE:isyan:2026")
    check(Identity.channelKey("TRT 1 HD") == Identity.channelKey("TRT1"))
    val a = SourceLink("Resmi", "https://example.org/stream1.m3u8", 1080, "tr", null)
    val b = SourceLink("Yedek", "https://example.org/stream2.m3u8", 720, "en", null)
    val expired = SourceLink("Eski", "https://example.org/expired.m3u8", 2160, "tr", null, expiresAtMillis = 100)
    val ordered = SourcePicker.preferred(listOf(b, expired, a, a), nowMillis = 101)
    check(ordered == listOf(a, b)) { "Dedup, TTL and language/quality priority" }
    val combined = ChannelMerger.merge(listOf(Channel("TRT 1 HD", listOf(a)), Channel("TRT1", listOf(b, a))))
    check(combined.size == 1 && combined[0].links.size == 2) { "One channel with alternate stream links" }
    check(HomeCategories.all.count { it.tmdbPath != null } >= 20)
    println("PASS: 8/8 core assertions; one visible provider, 28 home categories, merged TV channels")
}
