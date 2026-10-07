package com.eafb

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.getAndUnpack
import android.util.Base64
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CancellationException
import org.jsoup.Jsoup
import org.json.JSONObject

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
        trace("search", "title=" + query.title.take(70))
        val encoded = URLEncoder.encode(query.title, "UTF-8")
        val land = id.endsWith("-land")
        val searchUrl = if (land) base + "/?s=" + encoded else base + "/search?q=" + encoded
        val text = try {
            app.get(searchUrl, headers = headers + mapOf(
                "X-Requested-With" to "fetch", "Content-Type" to "application/json"
            ), referer = base + "/").text
        } catch (cancel: CancellationException) { throw cancel }
          catch (e: Exception) { trace("search", "request-failed=" + e.javaClass.simpleName); return emptyList() }
        trace("search", "response-length=" + text.length + " json=" + text.trimStart().startsWith("{"))
        val results = if (land) null else runCatching { org.json.JSONObject(text).optJSONArray("results") ?: org.json.JSONArray() }
            .getOrElse { trace("search", "invalid-json=" + it.javaClass.simpleName); return emptyList() }
        val offers = mutableListOf<MediaOffer>()
        val documents = if (land) {
            Jsoup.parse(text, base + "/").select("div.result-item article, #results article, .film-list article, article.item, div.poster, #content-holder article, #content-holder div.poster, a[href*=\u0027/film/\u0027], a[href*=\u0027/dizi/\u0027]").map { it }
        } else (0 until (results?.length() ?: 0)).map { Jsoup.parse(results!!.optString(it), base + "/") }
        for (doc in documents) {
            val a = doc.selectFirst(".image a, .poster a, .details .title a, h2 a, h3 a, a[href*=\u0027/film/\u0027], a[href*=\u0027/dizi/\u0027], a[href]") ?: if (doc.tagName() == "a") doc else continue
            val href = siteUrl(a.attr("href")) ?: continue
            val foundTitle = (doc.selectFirst(".h2.flbaslik, .details .title a, h4.title, .title, h2, h3, .poster-title")?.text()?.trim()
                ?: a.attr("title").ifBlank { a.attr("aria-label") }.ifBlank { doc.selectFirst("img[alt]")?.attr("alt").orEmpty() }.ifBlank { a.text() }.ifBlank { href.substringBefore("?").trimEnd('/').substringAfterLast('/').replace('-', ' ') }.trim())
            if (foundTitle.isBlank()) continue
            val normalizedTitle = Identity.normalize(foundTitle)
            val titleWords = normalizedTitle.split(" ").filter { it.length > 2 }.toSet()
            val wantedWords = wanted.split(" ").filter { it.length > 2 }.toSet()
            val overlap = titleWords.intersect(wantedWords).size
            val slugMatch = Identity.normalize(href.substringBefore("?").trimEnd('/').substringAfterLast('/').replace('-', ' ')).contains(wanted)
            val strongMatch = slugMatch || normalizedTitle == wanted ||
                normalizedTitle.contains(wanted) || wanted.contains(normalizedTitle) ||
                (wantedWords.size >= 2 && overlap >= 2 &&
                    overlap * 2 >= wantedWords.size && overlap * 2 >= titleWords.size)
            if (!strongMatch) continue
            offers += MediaOffer(id, sourceTitle, query.title, query.year, query.kind, href,
                query.tmdbId, query.season, query.episode)
        }
        trace("search", "matches=" + offers.size + " parsed=" + documents.size + " titled=" + documents.count { it.selectFirst(".h2.flbaslik, .details .title a, h4.title, .title, h2, h3, .poster-title") != null })
        return offers.distinctBy { it.pageUrl }.take(5)
    }

    private fun playlistQuality(body: String): Int? =
        Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
            .findAll(body).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()


    // NL: Bronze v52 bytecode model: resolve "file: variable" from its JavaScript context.
    private fun decryptPlayerUrl(html: String): String? {
        val scripts = Jsoup.parse(html).select("script").map { it.data() }
        val variable = Regex("""file:\s*([a-zA-Z_$][\w$]*)\s*[,}]""")
            .findAll(html).lastOrNull()?.groupValues?.get(1) ?: return null
        val candidates = scripts + scripts.filter { it.contains("eval(function(") }
            .mapNotNull { runCatching { getAndUnpack(it) }.getOrNull() }
        // The player variable may be assigned with let/const or without a declaration.
        val declaration = Regex("""(?:\b(?:var|let|const)\s+)?""" + Regex.escape(variable) + """\s*=""")
        val code = candidates.firstOrNull { declaration.containsMatchIn(it) }
            ?: candidates.joinToString("\n").takeIf { declaration.containsMatchIn(it) }
            ?: return null
        return try {
            val context = org.mozilla.javascript.Context.enter()
            try {
                context.optimizationLevel = -1
                val scope = context.initSafeStandardObjects()
                context.evaluateString(scope, NL_POLYFILLS, "polyfills", 1, null)
                context.evaluateString(scope, code, "nl-player", 1, null)
                val value = org.mozilla.javascript.ScriptableObject.getProperty(scope, variable)
                org.mozilla.javascript.Context.toString(value).takeIf { it.startsWith("https://") }
            } finally {
                org.mozilla.javascript.Context.exit()
            }
        } catch (e: Exception) {
            trace("local", "decrypt-failed=" + e.javaClass.simpleName)
            null
        } catch (e: LinkageError) {
            trace("local", "rhino-unavailable=" + e.javaClass.simpleName)
            null
        }
    }

    private suspend fun landPlayers(doc: org.jsoup.nodes.Document, pageUrl: String): List<Pair<String, String>> {
        val html = doc.outerHtml()
        val nonce = listOf(
            Regex("""videoAjax\s*=\s*\{[\s\S]*?nonce\s*:\s*['"]([\w-]+)['"]"""),
            Regex("""['"]nonce['"]\s*:\s*['"]([\w-]+)['"]"""),
            Regex("""nonce\s*:\s*['"]([\w-]+)['"]""")
        ).firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
        val postId = doc.selectFirst("#fimcnt")?.attr("data-post-id")?.takeIf { it.isNotBlank() }
            ?: Regex("""data-post-id=['"](\d+)['"]""").find(html)?.groupValues?.get(1)
        val players = linkedSetOf<Triple<String, String, String>>()
        for (el in doc.select("[data-player-name]")) {
            val pid = el.attr("data-post-id").ifBlank { postId.orEmpty() }
            val name = el.attr("data-player-name")
            if (pid.isNotBlank() && name.isNotBlank()) players.add(Triple(pid, name, el.attr("data-part-key")))
        }
        trace("land-dom", "nonce=" + (nonce != null) + " postId=" + (postId != null) + " players=" + players.size)
        if (nonce.isNullOrBlank()) return emptyList()
        val site = runCatching { URI(pageUrl) }.getOrNull()?.let { it.scheme + "://" + it.host } ?: base
        val out = mutableListOf<Pair<String, String>>()
        for ((pid, name, part) in players) {
            try {
                val result = app.post(site + "/wp-admin/admin-ajax.php",
                    headers = headers + mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to pageUrl),
                    data = mapOf("action" to "get_video_url", "nonce" to nonce,
                        "post_id" to pid, "player_name" to name, "part_key" to part))
                val data = JSONObject(result.text).optJSONObject("data")
                var embed = data?.optJSONObject("stream")?.optString("url").orEmpty()
                    .ifBlank { data?.optString("url").orEmpty() }
                if (embed.startsWith("/")) embed = site + embed
                if (embed.contains("setplay", ignoreCase = true)) {
                    trace("land-ajax", "setplay-skipped")
                    continue
                }
                if (embed.startsWith("https://")) out.add(embed to (sourceTitle + " • " + name))
            } catch (cancel: CancellationException) { throw cancel }
              catch (e: Exception) { trace("land-ajax", "failed=" + e.javaClass.simpleName) }
        }
        return out
    }

    companion object {
        private val NL_POLYFILLS = """
            var b64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
            function atob(input) {
                var str = String(input).replace(/=+$/, ''), output = '';
                for (var bc=0, bs, buffer, idx=0; buffer=str.charAt(idx++);
                    ~buffer && (bs=bc%4 ? bs*64+buffer : buffer, bc++%4) ?
                    output+=String.fromCharCode(255 & bs >> (-2*bc & 6)) : 0) {
                    buffer=b64.indexOf(buffer);
                }
                return output;
            }
            function btoa(input) {
                var str=String(input), output='';
                for (var block, charCode, idx=0, map=b64;
                    str.charAt(idx | 0) || (map='=', idx%1);
                    output+=map.charAt(63 & block >> 8-idx%1*8)) {
                    charCode=str.charCodeAt(idx+=3/4);
                    block=block<<8 | charCode;
                }
                return output;
            }
        """
    }

    private suspend fun localSource(url: String, label: String): List<SourceLink> {
        val response = try { app.get(url, headers = headers, referer = base + "/") }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { trace("local", "request-failed=" + e.javaClass.simpleName); return emptyList() }
        val html = response.text
        val viaJs = decryptPlayerUrl(html)
        val scripts = Jsoup.parse(html).select("script").map { it.data() }
        val combined = scripts.joinToString("\n") + "\n" +
            scripts.filter { it.contains("eval(function(") }
                .joinToString("\n") { runCatching { getAndUnpack(it) }.getOrNull().orEmpty() }
        trace("local", "shape scripts=" + scripts.size + " fileVariable=" + (viaJs != null))
        val encoded = Regex("""file_link\s*[:=]\s*["\x27]([^"\x27]+)["\x27]""")
            .find(combined)?.groupValues?.get(1)
        val decoded = encoded?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() }
        val direct = Regex("""(?:file|src|source|url)\s*[:=]\s*["\x27](https://[^"\x27\s]+(?:\.m3u8|\.mp4)(?:\?[^"\x27]*)?)["\x27]""", RegexOption.IGNORE_CASE)
            .find(combined)?.groupValues?.get(1)
        val stream = listOfNotNull(viaJs, decoded, direct).firstOrNull { it.startsWith("https://") }
            ?: run { trace("local", "missing-stream-url"); return emptyList() }
        val tracks = Regex("""tracks\s*:\s*\[([\s\S]*?)]""")
            .find(combined)?.groupValues?.get(1).orEmpty()
        val subtitles = Regex("""\{[^{}]*?file\s*:\s*["']([^"']+)["'][^{}]*?label\s*:\s*["']([^"']+)["'][^{}]*?kind\s*:\s*["']captions["'][^{}]*?}""", RegexOption.IGNORE_CASE)
            .findAll(tracks).mapNotNull { m ->
                val subUrl = runCatching { URI(url).resolve(m.groupValues[1]).toString() }.getOrNull()
                subUrl?.takeIf { it.startsWith("https://") }?.let { SourceSubtitle(m.groupValues[2], it) }
            }.toList()
        val embedReferer = runCatching { URI(url) }.getOrNull()
            ?.let { it.scheme + "://" + it.host + "/" } ?: (base + "/")
        val body = try { app.get(stream, referer = embedReferer).text } catch (_: Exception) { "" }
        val quality = playlistQuality(body)
        trace("local", "stream-ready subtitles=" + subtitles.size)
        return listOf(SourceLink(id, stream, quality, null, null,
            referer = embedReferer, isHls = viaJs != null || stream.substringBefore('?').endsWith(".m3u8", true),
            displayName = label, subtitles = subtitles))
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
                    val sm = Regex("""(\d+)\.\s*Sezon""", RegexOption.IGNORE_CASE).find(text)
                    val em = Regex("""(\d+)\.\s*B[öo]l[üu]m""", RegexOption.IGNORE_CASE).find(text)
                    val s = sm?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val e = em?.groupValues?.get(1)?.toIntOrNull()
                    if (s == season && e == episode) siteUrl(a.attr("href")) else null
                } ?: return emptyList()
        } else offer.pageUrl

        val doc = try { app.get(contentUrl, headers = headers).document }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return emptyList() }
        val candidates = LinkedHashMap<String, String>()
        trace("resolve", "alternative groups=" + doc.select("div.alternative-links").size + " iframes=" + doc.select("iframe").size + " scripts=" + doc.select("script").size)
        if (id.endsWith("-land")) landPlayers(doc, contentUrl).forEach { (url, name) -> candidates.putIfAbsent(url, name) }
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
                val unescaped = apiText.replace("\\\\", "\\").replace("\\\"", "\"")
                val frames = Jsoup.parse(unescaped, contentUrl).select("iframe")
                trace("video", "frames=" + frames.size)
                if (frames.isEmpty()) continue
                val dataSrc: String = frames[0].attr("data-src")
                val src: String = frames[0].attr("src")
                val raw: String = if (dataSrc.isNotBlank()) dataSrc else src
                if (raw.isBlank()) continue
                val iframe = when {
                    raw.startsWith("//") -> "https:" + raw
                    raw.startsWith("http") -> raw
                    else -> runCatching { URI(contentUrl).resolve(raw).toString() }.getOrNull()
                } ?: continue
                val label = listOf(sourceTitle, button.text().trim(), lang).filter { it.isNotBlank() }.joinToString(" • ")
                val normalized = if (iframe.contains("?rapidrame_id=")) {
                    val rapidId = iframe.substringAfter("?rapidrame_id=").substringBefore('&')
                    if (rapidId.isNotBlank()) base + "/playerr/" + rapidId else iframe
                } else iframe
                candidates.putIfAbsent(normalized, label)
            }
        }
        val out = mutableListOf<SourceLink>()
        for ((url, label) in candidates) {
            val local = localSource(url, label)
            if (local.isNotEmpty()) {
                out += local
                continue
            }
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
              catch (e: Exception) { trace("extractor", "failed=" + e.javaClass.simpleName) }
            trace("extractor", "host=" + (runCatching { URI(url).host }.getOrNull() ?: "unknown") + " links=" + out.size)
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
