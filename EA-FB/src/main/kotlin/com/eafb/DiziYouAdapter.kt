package com.eafb

import android.util.Log
import com.lagradost.cloudstream3.app
import java.net.URI
import java.util.concurrent.CancellationException
import org.jsoup.Jsoup

/** Inactive candidate: requires endpoint, rights and playback verification. */
class DiziYouAdapter(private val origin: String = "https://www.diziyou.one") : MediaSourceAdapter {
    override val id = "diziyou"
    private val root = URI(origin)
    init {
        require(root.scheme == "https" && root.host == "www.diziyou.one" &&
            root.userInfo == null && root.query == null && root.fragment == null &&
            (root.path.isNullOrBlank() || root.path == "/"))
    }
    private val base = origin.trimEnd('/')
    private val storage = "https://storage.diziyou.one"
    private fun trace(stage: String, detail: String) = Log.i("EA-FB-DiziYou", "$stage: $detail")

    private fun siteUrl(raw: String): String? = try {
        val uri = root.resolve(raw)
        uri.toString().takeIf {
            uri.scheme == "https" && uri.host == root.host &&
                uri.userInfo == null && uri.fragment == null
        }
    } catch (_: Exception) { null }

    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (query.kind != MediaKind.SERIES || query.season == null ||
            query.episode == null || query.title.isBlank()) return emptyList()
        val html = try {
            app.post(
                "$base/wp-admin/admin-ajax.php",
                data = mapOf("action" to "data_fetch", "keyword" to query.title)
            ).text
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { return emptyList() }
        val candidates = Jsoup.parse(html, base).select("div#searchelement")
        val matches = candidates.mapNotNull { result ->
            val anchors = result.select("a")
            val title = anchors.lastOrNull()?.text()?.trim() ?: return@mapNotNull null
            if (Identity.normalize(title) != Identity.normalize(query.title)) return@mapNotNull null
            siteUrl(anchors.firstOrNull()?.attr("href") ?: "")
        }.distinct().take(5)
        val found = mutableListOf<MediaOffer>()
        for (seriesUrl in matches) {
            val doc = try { app.get(seriesUrl).document }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { continue }
            val epUrl = doc.select("div.bolumust").firstNotNullOfOrNull { card ->
                val heading = card.selectFirst("div.baslik")?.ownText()?.trim()
                    ?: return@firstNotNullOfOrNull null
                if (!DiziYouEpisodeParser.exactEpisode(heading, query.season, query.episode))
                    return@firstNotNullOfOrNull null
                siteUrl(card.closest("a")?.attr("href") ?: "")
            } ?: continue
            found += MediaOffer(
                id, "DiziYou", query.title, query.year, query.kind, epUrl,
                query.tmdbId, query.season, query.episode
            )
        }
        return found.distinctBy { it.pageUrl }
    }

    private suspend fun playlistQuality(url: String): Int? = try {
        val body = app.get(url, referer = "$base/").text
        if (!body.trimStart().startsWith("#EXTM3U")) null
        else Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
            .findAll(body).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
    } catch (cancel: CancellationException) { throw cancel }
      catch (_: Exception) { null }

    override suspend fun resolve(offer: MediaOffer): List<SourceLink> {
        if (offer.providerId != id || offer.kind != MediaKind.SERIES ||
            offer.season == null || offer.episode == null ||
            siteUrl(offer.pageUrl) != offer.pageUrl) return emptyList()
        val doc = try { app.get(offer.pageUrl).document }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val iframe = doc.selectFirst("iframe#diziyouPlayer")?.absUrl("src")
        if (iframe.isNullOrBlank()) {
            trace("resolve", "iframe=false")
            return emptyList()
        }
        val itemId = DiziYouEpisodeParser.playerId(iframe, root.host)
        if (itemId == null) {
            trace("resolve", "iframe=true itemId=false host=" +
                runCatching { URI(iframe).host }.getOrNull())
            return emptyList()
        }
        val options = doc.select("span.diziyouOption").map { it.id() }.filter { it.isNotBlank() }.toSet()
        val originalUrl = storage + "/episodes/" + itemId + "/play.m3u8"
        val dubbedUrl = storage + "/episodes/" + itemId + "_tr/play.m3u8"
        val originalQuality = if ("turkceAltyazili" in options || "ingilizceAltyazili" in options)
            playlistQuality(originalUrl) else null
        val dubbedQuality = if ("turkceDublaj" in options) playlistQuality(dubbedUrl) else null
        val links = buildList {
            // The non-_tr playlist is the original-audio variant. Site option IDs describe
            // subtitle availability, but no separate subtitle URL has been proven here;
            // do not advertise a selectable CloudStream subtitle track until one exists.
            if ("turkceAltyazili" in options || "ingilizceAltyazili" in options)
                add(SourceLink(id, originalUrl, originalQuality, "original", null,
                    referer = "$base/", isHls = true, displayName = "DiziYou • Orijinal"))
            if ("turkceDublaj" in options)
                add(SourceLink(id, dubbedUrl, dubbedQuality, "tr", null,
                    referer = "$base/", isHls = true, displayName = "DiziYou • Türkçe Dublaj"))
        }.distinctBy { Triple(it.url, it.audioLanguage, it.subtitleLanguage) }
        trace("resolve", "iframe=true itemId=true options=${options.sorted()} links=${links.size}")
        return links
    }
}
