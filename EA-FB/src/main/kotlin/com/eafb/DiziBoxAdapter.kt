package com.eafb

import com.lagradost.cloudstream3.app
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CancellationException
import org.jsoup.Jsoup

/**
 * Experimental DiziBox staging adapter.
 * Based on Claude's DiziBoxExtractor.kt supplied by the user.
 * Endpoint and player behavior are unverified; no claim of playback support.
 */
class DiziBoxAdapter(private val origin: String = "https://www.dizibox.live") : MediaSourceAdapter {
    override val id: String = "dizibox"
    private val root = URI(origin)
    private val base = origin.trimEnd('/')

    init {
        require(root.scheme == "https" && root.host == "www.dizibox.live" &&
            root.userInfo == null && root.query == null && root.fragment == null)
    }

    private fun siteUrl(raw: String): String? = try {
        val url = root.resolve(raw)
        url.toString().takeIf {
            url.scheme == "https" && url.host == root.host &&
                url.userInfo == null && url.fragment == null
        }
    } catch (_: Exception) { null }

    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (query.title.isBlank()) return emptyList()
        // Claude's reconstructed extractor uses WordPress dwls_search.
        val url = "$base/wp-admin/admin-ajax.php?s=" +
            URLEncoder.encode(query.title, "UTF-8") + "&action=dwls_search"
        val text = try {
            app.get(url, referer = "$base/",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { return emptyList() }
        val doc = Jsoup.parse(text)
        // Search responses may contain HTML or JSON; only accept explicit
        // title/permalink pairs, never guess unrelated series identities.
        val items = Regex(
            """\{[^{}]*"post_title"\s*:\s*"([^"]+)"[^{}]*"permalink"\s*:\s*"([^"]+)"[^{}]*}"""
        ).findAll(text).mapNotNull { match ->
            val title = match.groupValues[1].replace("\\/", "/")
            if (Identity.normalize(title) != Identity.normalize(query.title)) return@mapNotNull null
            siteUrl(match.groupValues[2].replace("\\/", "/"))
        }.toList().ifEmpty {
            doc.select("a[href]").mapNotNull { a ->
                if (Identity.normalize(a.text()) == Identity.normalize(query.title))
                    siteUrl(a.attr("href")) else null
            }
        }.distinct().take(5)
        return items.map { seriesUrl ->
            MediaOffer(id, "DiziBox", query.title, query.year, query.kind,
                seriesUrl, query.tmdbId, query.season, query.episode)
        }
    }

    override suspend fun resolve(offer: MediaOffer): List<SourceLink> {
        if (offer.providerId != id || siteUrl(offer.pageUrl) != offer.pageUrl)
            return emptyList()
        // Claude's episode URL patterns are guesses: don't advertise guessed
        // links as playable. Discover an explicit episode link from the page.
        val series = try { app.get(offer.pageUrl).document }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val episodeUrl = if (offer.kind == MediaKind.SERIES) {
            val season = offer.season ?: return emptyList()
            val episode = offer.episode ?: return emptyList()
            series.select("a[href]").firstNotNullOfOrNull { a ->
                val label = a.text().trim()
                val ep = Regex("""(\d+)\.\s*Bölüm""", RegexOption.IGNORE_CASE)
                    .find(label)?.groupValues?.get(1)?.toIntOrNull()
                val seasonMatches = a.attr("href").contains("$season-sezon") ||
                    a.parents().any { it.text().contains("$season. Sezon") }
                if (ep == episode && seasonMatches) siteUrl(a.attr("href")) else null
            } ?: run {
                // Claude fallback: navigate the season tab, then match its episode card.
                val tab = series.select("div#seasons-list a").firstOrNull {
                    it.text().contains("$season. Sezon")
                }?.absUrl("href")?.let(::siteUrl) ?: return emptyList()
                val seasonDoc = try { app.get(tab).document }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { return emptyList() }
                seasonDoc.select(
                    "main article.grid-box, div#archive-content article.grid-box, " +
                    "div#episodes-list article.grid-box, div.episodes-list article.grid-box, " +
                    "div.site-content article.grid-box"
                ).firstNotNullOfOrNull { card ->
                    if (card.parents().any {
                        it.tagName() == "aside" || it.hasClass("widget") || it.id() == "sidebar"
                    }) return@firstNotNullOfOrNull null
                    val a = card.selectFirst("div.post-title a, h2 a, a")
                        ?: return@firstNotNullOfOrNull null
                    val num = Regex("""(\\d+)\\.\\s*Bölüm""", RegexOption.IGNORE_CASE)
                        .find(a.text())?.groupValues?.get(1)?.toIntOrNull()
                    if (num == episode) siteUrl(a.attr("href")) else null
                } ?: return emptyList()
            }
        } else offer.pageUrl
        val doc = if (episodeUrl == offer.pageUrl) series else try {
            app.get(episodeUrl).document
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { return emptyList() }
        val iframe = doc.selectFirst("div#video-area iframe")?.absUrl("src")
            ?: doc.selectFirst("iframe")?.absUrl("src")
            ?: return emptyList()
        return resolvePlayer(iframe, episodeUrl)
    }

    /**
     * Adapted from Claude's iframeDecode: follow Moly's nested iframe and
     * extract an explicit master playlist. Never invent a storage URL.
     * King encrypted payload handling remains unimplemented.
     */
    private suspend fun resolvePlayer(iframe: String, referer: String): List<SourceLink> {
        val uri = try { URI(iframe) } catch (_: Exception) { return emptyList() }
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return emptyList()
        if (uri.path.endsWith(".m3u8", ignoreCase = true))
            return listOf(SourceLink(id, iframe, null, null, null))
        if (!uri.path.contains("/player/moly/moly.php")) return emptyList()
        val playerUrl = iframe.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
        val player = try { app.get(playerUrl, referer = referer).document }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val nested = player.selectFirst("iframe")?.absUrl("src")
            ?: player.selectFirst("div#Player iframe")?.absUrl("src")
            ?: return emptyList()
        val nestedUri = try { URI(nested) } catch (_: Exception) { return emptyList() }
        if (nestedUri.scheme != "https" || nestedUri.host.isNullOrBlank())
            return emptyList()
        val html = try { app.get(nested, referer = base + "/").text }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val playlist = Regex("""file:\\s*['"](https://[^'"]+master\\.m3u8[^'"]*)['"]""")
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        return listOf(SourceLink(id, playlist, null, null, null))
    }
}
