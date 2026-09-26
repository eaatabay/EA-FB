package com.eafb

fun main() {
    check(LiveSourcePolicy.acceptedUrl("https://tv.example.org/live/trt.m3u8?token=abc"))
    check(LiveSourcePolicy.acceptedUrl("https://tv.example.org/movie.mp4"))
    check(!LiveSourcePolicy.acceptedUrl("http://tv.example.org/live.m3u8"))
    check(!LiveSourcePolicy.acceptedUrl("https://user:secret@tv.example.org/live.m3u8"))
    check(!LiveSourcePolicy.acceptedUrl("https://localhost/live.m3u8"))
    check(!LiveSourcePolicy.acceptedUrl("https://tv.example.org/page.html"))
    for (unsafe in listOf(
        "https://192.168.1.10/live.m3u8",
        "https://127.0.0.2/live.m3u8",
        "https://10.0.0.1/live.m3u8",
        "https://[::1]/live.m3u8",
        "https://tv.local/live.m3u8",
        "https://tv.internal/live.m3u8",
        "https://tv.example.org:8443/live.m3u8",
        "https://tv.example.org:443/live.m3u8",
        "https://tv.example.org/../private.m3u8",
        "https://tv.example.org/%2e%2e/private.m3u8",
        "https://tv.example.org//live.m3u8",
        "https://tv.example.org/live.m3u8#fragment",
        "https://tv.example.org\\\\private.m3u8"
    )) check(!LiveSourcePolicy.acceptedUrl(unsafe)) { unsafe }
    check(LiveSourcePolicy.acceptedUrl("https://tv.example.org/live.m3u8?token=abc"))
    val entries = listOf(
        ApprovedLiveSource("TRT 1 HD", "Resmî", "https://tv.example.org/a.m3u8", true, 1080),
        ApprovedLiveSource("TRT1", "Alternatif", "https://other.example.org/b.m3u8", true, 720),
        ApprovedLiveSource("TRT1", "Resmî", "https://tv.example.org/a.m3u8", true, 1080),
        ApprovedLiveSource("TRT1", "Yetkisiz", "https://other.example.org/c.m3u8", false),
        ApprovedLiveSource("TRT1", "Düzensiz", "http://other.example.org/d.m3u8", true)
    )
    val channels = LiveSourcePolicy.channels(entries)
    check(channels.size == 1 && channels[0].name == "TRT 1")
    check(channels[0].links.size == 2)
    check(channels[0].links.map { it.provider }.toSet() == setOf("Resmî", "Alternatif"))
    println("PASS: 23/23 live-source policy assertions")
}
