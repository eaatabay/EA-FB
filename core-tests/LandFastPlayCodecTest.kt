package com.eafb

import java.util.Base64

fun main() {
    fun ok(value: Boolean, label: String) { check(value) { "FAIL: " + label }; println("PASS " + label) }
    fun encode(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    val rawPlayer = "https://player.example.org/embed?id=1|ignored"
    val crypt = rawPlayer.map { (it.code xor 'x'.code).toChar() }.joinToString("")
    val encrypted = encode(crypt)
    val key = encode("x")
    val sample = " SPG . cerceve (frame, '" + encrypted + "', \"" + key + "\" )"
    val operands = LandFastPlayCodec.spgOperands(sample)
    ok(operands != null, "SPG tolerates whitespace and mixed quotes")
    ok(LandFastPlayCodec.playerUrl(LandFastPlayCodec.xorDecrypt(Base64.getDecoder().decode(operands!!.first), Base64.getDecoder().decode(operands.second))!!) == "https://player.example.org/embed?id=1", "Base64 XOR player URL")
    ok(LandFastPlayCodec.normalizeHttps("https://player.example.org/w/", "//cdn.example.org/master.m3u8") == "https://cdn.example.org/master.m3u8", "protocol-relative CDN normalized")
    ok(LandFastPlayCodec.normalizeHttps("https://player.example.org/w/", "/manifests/x/master.m3u8") == "https://player.example.org/manifests/x/master.m3u8", "root relative manifest")
    ok(LandFastPlayCodec.normalizeHttps("https://player.example.org/w/", "http://127.0.0.1/secret") == null, "reject loopback HTTP")
    ok(LandFastPlayCodec.normalizeHttps("https://player.example.org/w/", "https://192.168.0.1/secret") == null, "reject private network")
    ok(LandFastPlayCodec.streamUrl("var src: '//cdn.example.org/master.m3u8';", "https://player.example.org/path") == "https://cdn.example.org/master.m3u8", "stream src URL")
    ok(LandFastPlayCodec.streamUrl("const id='/manifests/1a2b';", "https://player.example.org/path") == "https://player.example.org/manifests/1a2b", "fallback manifest")
    ok(LandFastPlayCodec.sp("{\"sp\": \"fake-token\"}") == "fake-token", "sp parsing")
    ok(LandFastPlayCodec.timestamp("{\"spT\": 1700000000}", 2) == 1700000000000L, "spT seconds normalized")
    ok(LandFastPlayCodec.timestamp("", 1700000000000L) == 1700000000000L, "timestamp fallback")
    ok(LandFastPlayCodec.fnv1a32Hex("hello") == "4f9f2cab", "FNV-1a reference")
    ok(LandFastPlayCodec.xSp("fake", 123L, "abc123") == "123.abc123." + LandFastPlayCodec.fnv1a32Hex("fake|123|abc123"), "token deterministic fixture")
    ok(LandFastPlayCodec.xSp("fake", 123L, "x") == null, "invalid nonce rejected")
    ok(LandFastPlayCodec.quality("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=12,RESOLUTION=1920x1080") == 1080, "quality")
    ok(LandFastPlayCodec.quality("#EXTM3U\n#EXTINF:2.0,") == null, "no invented 1080")
    val master = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"Turkish\",URI=\"sub/tr.m3u8\"\n"
    ok(LandFastPlayCodec.subtitles(master, "https://cdn.example.org/hls/master.m3u8") == listOf("Turkish" to "https://cdn.example.org/hls/sub/tr.m3u8"), "subtitle master URI")
    ok(LandFastPlayCodec.isHls("\ufeff  #EXTM3U\n"), "BOM manifest")
    ok(!LandFastPlayCodec.isHls("<html>challenge</html>"), "challenge rejected")
    println("LAND CODEC: ALL TESTS PASSED")
}
