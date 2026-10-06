package com.eafb

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CancellationException
import org.jsoup.Jsoup

open class HDFilmCehennemiAdapter(
    private val origin: String,
    final override val id: String,
    private val sourceTitle: String
) : MediaSourceAdapter {
    private val root = URI(origin)
    private val base = origin.trimEnd('/')
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
    )
    init { require(root.scheme == "https" && root.host != null && root.userInfo == null) }
    private fun trace(stage: String, detail: String) = Log.i("EA-FB-HDFC", id + "/" + stage + ": " + detail)
    private fun siteUrl(raw: String): String? = try {
        val u = root.resolve(raw)
        u.toString().takeIf { u.scheme == "https" && u.host == root.host && u.userInfo == null }
    } catch (_: Exception) { null }

    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (query.title.isBlank()) return emptyList()
        val wanted = Identity.normalize(query.title)
        val encoded = URLEncoder.encode(query.title, "UTF-8")
        val text = try {
            app.get(base + "/search?q=" + encoded, headers = headers + mapOf(
                "X-Requested-With" to "fetch", "Content-Type" to "application/json"
            ), referer = base + "/").text
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { return emptyList() }
        val results = runCatching { org.json.JSONObject(text).optJSONArray("results") ?: org.json.JSONArray() }
            .getOrElse { return emptyList() }
        val offers = mutableListOf<MediaOffer>()
        for (i in 0 until results.length()) {
            val doc = Jsoup.parse(results.optString(i), base + "/")
            val a = doc.selectFirst("a.search-result, a[href]") ?: continue
            val href = siteUrl(a.attr("href")) ?: continue
            val foundTitle = a.selectFirst("h4.title, .title")?.text()?.trim()
                ?: a.attr("aria-label").trim()
            if (foundTitle.isBlank() || Identity.normalize(foundTitle) != wanted) continue
            offers += MediaOffer(id, sourceTitle, query.title, query.year, query.kind, href,
                query.tmdbId, query.season, query.episode)
        }
        trace("search", "matches=" + offers.size)
        return offers.distinctBy { it.pageUrl }.take(5)
    }

    override suspend fun resolve(offer: MediaOffer): List<SourceLink> {
        if (offer.providerId != id || siteUrl(offer.pageUrl) != offer.pageUrl) return emptyList()
        val contentUrl = if (offer.kind == MediaKind.SERIES) {
            val season = offer.season ?: return emptyList()
            val episode = offer.episode ?: return emptyList()
            val doc = try { app.get(offer.pageUrl, headers = headers).document }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { return emptyList() }
            doc.select("div.seasons-tab-content a[href], div.seasons a[href*='bolum'], a[href*='bolum']")
                .firstNotNullOfOrNull { a ->
                    val text = (a.selectFirst("h4, .mini-poster-title")?.text() ?: a.text()).trim()
                    val sm = Regex("""(\\d+)\\.\\s*Sezon""", RegexOption.IGNORE_CASE).find(text)
                    val em = Regex("""(\\d+)\\.\\s*B[öo]l[üu]m""", RegexOption.IGNORE_CASE).find(text)
                    val s = sm?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val e = em?.groupValues?.get(1)?.toIntOrNull()
                    if (s == season && e == episode) siteUrl(a.attr("href")) else null
                } ?: return emptyList()
        } else offer.pageUrl

        val doc = try { app.get(contentUrl, headers = headers).document }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val candidates = LinkedHashMap<String, String>()
        doc.select("iframe").forEach { frame ->
            val raw = frame.attr("data-src").ifBlank { frame.attr("src") }
            runCatching { URI(contentUrl).resolve(raw).toString() }.getOrNull()
                ?.takeIf { it.startsWith("https://") && !it.contains("youtube.com") }
                ?.let { candidates.putIfAbsent(it, sourceTitle) }
        }
        for (group in doc.select("div.alternative-links")) {
            val lang = group.attr("data-lang").uppercase()
            for (button in group.select("button.alternative-link[data-video]")) {
                val videoId = button.attr("data-video")
                if (videoId.isBlank()) continue
                val apiText = try {
                    app.get(base + "/video/" + videoId + "/", headers = headers + mapOf(
                        "X-Requested-With" to "fetch", "Content-Type" to "application/json"
                    ), referer = contentUrl).text
                } catch (cancel: CancellationException) { throw cancel }
                  catch (_: Exception) { continue }
                val raw = Regex("""data-src=[^"']*["']([^"']+)""").find(apiText)?.groupValues?.get(1) ?: continue
                val iframe = runCatching { URI(contentUrl).resolve(raw).toString() }.getOrNull() ?: continue
                val label = listOf(sourceTitle, button.text().trim(), lang).filter { it.isNotBlank() }.joinToString(" • ")
                candidates.putIfAbsent(iframe, label)
            }
        }
        val out = mutableListOf<SourceLink>()
        for ((url, label) in candidates) {
            val subtitles = mutableListOf<SourceSubtitle>()
            try {
                loadExtractor(url, contentUrl, { sub ->
                    if (sub.url.startsWith("https://")) subtitles += SourceSubtitle(sub.lang, sub.url)
                }, { link ->
                    if (link.url.startsWith("https://")) out += SourceLink(
                        provider = id, url = link.url, quality = link.quality.takeIf { it > 0 },
                        audioLanguage = null, subtitleLanguage = null, referer = link.referer,
                        isHls = link.url.substringBefore('?').endsWith(".m3u8", true),
                        displayName = link.name.takeIf { it.isNotBlank() }?.let { label + " • " + it } ?: label,
                        subtitles = subtitles.distinctBy { it.language to it.url }
                    )
                })
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: Exception) { }
        }
        trace("resolve", "candidates=" + candidates.size + " links=" + out.size)
        return out.distinctBy { it.url to it.displayName }
    }
}

class HDFilmCehennemiNlAdapter : HDFilmCehennemiAdapter(
    "https://www.hdfilmcehennemi.nl", "hdfilmcehennemi-nl", "HDFilmCehennemi NL"
)
class HDFilmCehennemiLandAdapter : HDFilmCehennemiAdapter(
    "https://www.hdfilmcehennemi.land", "hdfilmcehennemi-land", "HDFilmCehennemi LAND"
)
