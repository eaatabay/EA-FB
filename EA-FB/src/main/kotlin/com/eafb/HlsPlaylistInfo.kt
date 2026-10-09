package com.eafb

data class HlsPlaylistInfo(val quality: Int?, val adaptive: Boolean) {
    companion object {
        fun parse(body: String): HlsPlaylistInfo {
            val text = body.trimStart('\ufeff', '\n', '\r', ' ', '\t')
            if (!text.startsWith("#EXTM3U")) return HlsPlaylistInfo(null, false)
            val quality = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
                .findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
            return HlsPlaylistInfo(quality, text.lineSequence().any { it.trimStart().startsWith("#EXT-X-STREAM-INF:") })
        }
    }
}
