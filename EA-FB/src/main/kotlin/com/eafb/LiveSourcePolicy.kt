package com.eafb

import java.net.URI

data class ApprovedLiveSource(
    val channel: String,
    val provider: String,
    val url: String,
    val authorized: Boolean,
    val quality: Int? = null,
    val audioLanguage: String? = null
)

/** A repository-controlled list; never automatically import unlicensed third-party playlists. */
object LiveSourcePolicy {
    fun acceptedUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.host.lowercase() !in setOf("localhost", "127.0.0.1", "::1") &&
            (uri.path.endsWith(".m3u8", true) || uri.path.endsWith(".mp4", true))
    }.getOrDefault(false)

    fun channels(entries: List<ApprovedLiveSource>): List<Channel> =
        ChannelMerger.merge(entries.asSequence()
            .filter { it.authorized && it.channel.isNotBlank() && it.provider.isNotBlank() && acceptedUrl(it.url) }
            .map {
                Channel(it.channel, listOf(SourceLink(
                    provider = it.provider.trim(), url = it.url, quality = it.quality?.takeIf { q -> q in 1..4320 },
                    audioLanguage = it.audioLanguage?.takeIf(String::isNotBlank), subtitleLanguage = null
                )))
            }.toList())
}
