package com.eafb

import android.util.Log
import com.lagradost.cloudstream3.app
import java.net.URI
import java.net.URLEncoder
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
        if (query.kind != MediaKind.SERIES || query.season == null || query.episode == null) return emptyList()
        for (title in SourceSearchTitles.candidates(query)) {
            val ajax = try {
                app.post("$base/wp-admin/admin-ajax.php",
                    data = mapOf("action" to "data_fetch", "keyword" to title)).text
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: Exception) { "" }
            val wanted = Identity.normalize(title)
            val matches = Jsoup.parse(ajax, base).select("div#searchelement").mapNotNull { result ->
                val anchors = result.select("a")
                val found = anchors.lastOrNull()?.text()?.trim().orEmpty()
                if (Identity.normalize(found) != wanted) null
                else siteUrl(anchors.firstOrNull()?.attr("href").orEmpty())
            }.distinct().take(5)
            val ajaxOffers = episodeOffers(query, matches)
            if (ajaxOffers.isNotEmpty()) return ajaxOffers
            // Bronze v27's GET search is the fallback when AJAX returns no usable episode.
            val html = try { app.get("$base/?s=" + URLEncoder.encode(title, "UTF-8")).text }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { continue }
            val doc = Jsoup.parse(html, base)
            val links = doc.select("div.incontent div#list-series, div#list-series, div#list-series-main")
                .select("div.cat-title-main a, div#categorytitle a, a")
                .mapNotNull { anchor ->
                    val names = listOf(anchor.text(), anchor.attr("title"))
                    if (names.none { it.isNotBlank() && Identity.normalize(it) == wanted }) null
                    else siteUrl(anchor.attr("href"))
                }.distinct().take(5)
            val offers = episodeOffers(query, links)
            if (offers.isNotEmpty()) return offers
        }
        return emptyList()
    }

    private suspend fun episodeOffers(query: MediaQuery, matches: List<String>): List<MediaOffer> {
        val season = query.season ?: return emptyList()
        val episode = query.episode ?: return emptyList()
        val found = mutableListOf<MediaOffer>()
        for (seriesUrl in matches) {
            val doc = try { app.get(seriesUrl).document }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { continue }
            val epUrl = doc.select("div.bolumust").firstNotNullOfOrNull { card ->
                val heading = card.selectFirst("div.baslik")?.ownText()?.trim() ?: return@firstNotNullOfOrNull null
                if (!DiziYouEpisodeParser.exactEpisode(heading, season, episode)) return@firstNotNullOfOrNull null
                siteUrl(card.closest("a")?.attr("href") ?: "")
            } ?: continue
            found += MediaOffer(id, "DiziYou", query.title, query.year, query.kind, epUrl,
                query.tmdbId, season, episode)
        }
        return found.distinctBy { it.pageUrl }
    }

    private suspend fun playlistInfo(url: String): HlsPlaylistInfo = try {
        HlsPlaylistInfo.parse(app.get(url, referer = "$base/").text)
    } catch (cancel: CancellationException) { throw cancel }
      catch (_: Exception) { HlsPlaylistInfo(null, false) }

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
        val originalInfo = if ("turkceAltyazili" in options || "ingilizceAltyazili" in options)
            playlistInfo(originalUrl) else HlsPlaylistInfo(null, false)
        val dubbedInfo = if ("turkceDublaj" in options) playlistInfo(dubbedUrl) else HlsPlaylistInfo(null, false)
        val links = buildList {
            val subtitles = buildList {
                if ("turkceAltyazili" in options) add(SourceSubtitle("Türkçe", "$storage/subtitles/$itemId/tr.vtt"))
                if ("ingilizceAltyazili" in options) add(SourceSubtitle("English", "$storage/subtitles/$itemId/en.vtt"))
            }
            if ("turkceAltyazili" in options || "ingilizceAltyazili" in options)
                add(SourceLink(id, originalUrl, originalInfo.quality, "original", null,
                    referer = "$base/", isHls = true, displayName = "DiziYou • Orijinal", subtitles = subtitles, isAdaptive = originalInfo.adaptive))
            if ("turkceDublaj" in options)
                add(SourceLink(id, dubbedUrl, dubbedInfo.quality, "tr", null,
                    referer = "$base/", isHls = true, displayName = "DiziYou • Türkçe Dublaj", isAdaptive = dubbedInfo.adaptive))
        }.distinctBy { Triple(it.url, it.audioLanguage, it.subtitleLanguage) }
        trace("resolve", "iframe=true itemId=true options=${options.sorted()} links=${links.size}")
        return links
    }
}
