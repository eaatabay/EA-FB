package com.eafb

import java.net.URLEncoder
import java.net.URI
import java.util.concurrent.CancellationException
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup

/**
 * Independent, fail-closed DiziMom candidate.
 * This class is NOT installed by PlaybackLinkBridge and does not bypass
 * authentication, anti-bot checks, DRM, or require private session data.
 * A site owner's permission and live playback verification are required
 * before enabling it in a release.
 */
class DiziMomAdapter(private val origin: String) : MediaSourceAdapter {
    override val id: String = "dizimom"

    init {
        val uri = URI(origin)
        require(uri.scheme == "https" && uri.host != null &&
            uri.userInfo == null && uri.query == null && uri.fragment == null)
    }

    private val base = origin.trimEnd('/')

    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (query.title.isBlank() || query.kind == MediaKind.LIVE ||
            query.kind != MediaKind.MOVIE ||\n            query.season != null || query.episode != null) return emptyList()
        val encoded = URLEncoder.encode(query.title.trim(), "UTF-8")
        val page = try { app.get("$base/?s=$encoded").text }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val doc = Jsoup.parse(page, base)
        return doc.select("div.single-item")
            .mapNotNull { card ->
                val label = card.selectFirst("div.categorytitle a")?.text()?.trim()
                    ?: return@mapNotNull null
                val url = card.selectFirst("div.cat-img a")?.absUrl("href")
                    ?: return@mapNotNull null
                val publishedYear = Regex("""Yapım Yılı\s*:\s*(\d{4})""")
                    .find(card.text())?.groupValues?.get(1)?.toIntOrNull()
                if (query.year != null && publishedYear != query.year)
                    return@mapNotNull null
                if (url.isBlank() || !sameOrigin(url) ||
                    Identity.normalize(cleanSearchTitle(label)) != Identity.normalize(query.title))
                    return@mapNotNull null
                MediaOffer(
                    providerId = id, providerTitle = "DiziMom", title = query.title,
                    year = publishedYear, kind = query.kind, pageUrl = url
                )
            }.distinctBy { it.pageUrl }.take(10)
    }

    override suspend fun resolve(offer: MediaOffer): List<SourceLink> {
        if (offer.providerId != id || offer.kind != MediaKind.MOVIE ||\n            !sameOrigin(offer.pageUrl)) return emptyList()
        // Search results are metadata, not verified episode or stream URLs.
        // Never emit an iframe, HTML page, or unknown player as a playable link.
        // A reviewed per-host resolver must be implemented before activation.
        return emptyList()
    }

    // Remove only trailing site labels; never strip words from the movie title itself.
    private fun cleanSearchTitle(raw: String): String {
        var title = raw.trim()
        val suffix = Regex(
            """\\s+(?:Türkçe Dublaj|Türkçe Altyazılı|Son Bölüm|izle|Final)\\s*$""",
            RegexOption.IGNORE_CASE
        )
        repeat(3) { title = title.replace(suffix, "").trim() }
        return title
    }

    private fun sameOrigin(value: String): Boolean = try {
        val candidate = URI(value)
        val root = URI(base)
        candidate.scheme == "https" && candidate.host.equals(root.host, true) &&
            candidate.port == root.port && candidate.userInfo == null
    } catch (_: Exception) { false }
}
