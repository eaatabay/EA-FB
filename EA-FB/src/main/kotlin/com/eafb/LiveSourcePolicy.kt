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

/** Remote live-channel JSON cannot enable distribution in an unreviewed release. */
object LiveChannelDeliveryConfig {
    const val enabled = false
}

/** A repository-controlled list; never automatically import unlicensed third-party playlists. */
object LiveSourcePolicy {
    fun acceptedUrl(url: String): Boolean = runCatching {
        if (url.length !in 20..2048 || !url.startsWith("https://")) return false
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return false
        val path = uri.rawPath.orEmpty()
        val publicHostname = host.matches(Regex(
            "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+"
        )) && host.length <= 253 && host.split('.').all { it.length <= 63 } &&
            !host.matches(Regex("[0-9]+(?:\\.[0-9]+){3}")) &&
            listOf(".local", ".localhost", ".internal", ".invalid")
                .none { host.endsWith(it) }
        publicHostname && uri.scheme == "https" && uri.userInfo == null &&
            uri.port == -1 && uri.rawFragment == null &&
            !path.contains('%') && !path.contains('\\') &&
            !path.contains("//") && path.split('/').none { it == "." || it == ".." } &&
            (path.endsWith(".m3u8", true) || path.endsWith(".mp4", true))
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
