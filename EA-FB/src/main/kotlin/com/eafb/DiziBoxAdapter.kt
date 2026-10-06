package com.eafb

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * PROPOSED revision of the V51 DiziBox adapter. NOT compiled, NOT run on a device.
 *
 * Evidence levels used in the comments below:
 *  [v28]  read from the bytecode of the working DiziBox_v28.cs3 package
 *  [obs]  observed with curl/DevTools against dizibox.live (Breaking Bad, Peaky Blinders)
 *  [hyp]  hypothesis, never confirmed
 *
 * Contract is unchanged: MediaSourceAdapter, MediaQuery, MediaOffer, SourceLink, Identity, MediaKind
 * are used exactly as in V51. Every returned link is still gated by an "#EXTM3U" check on the playlist
 * body (the host answers its own error page with HTTP 200 [obs]). That check proves the master playlist
 * only; it does not prove ExoPlayer plays the media segments.
 */
class DiziBoxAdapter(private val origin: String = "https://www.dizibox.live") : MediaSourceAdapter {
    override val id: String = "dizibox"
    private val root = URI(origin)
    private val base = origin.trimEnd('/')
    private fun trace(stage: String, detail: String) = Log.i("EA-FB-DiziBox", "$stage: $detail")

    init {
        require(root.scheme == "https" && root.host == "www.dizibox.live" &&
            root.userInfo == null && root.query == null && root.fragment == null)
    }

    // [v28] cookies and headers are sent on PAGE fetches only (getLightDocument), not on player fetches.
    // [obs] dbxu is not validated (dbxu=1 passed on admin-ajax.php), v28 hard-codes 1743289650198.
    private fun siteCookies() = mapOf(
        "LockUser" to "true", "isTrustedUser" to "true",
        "dbxu" to System.currentTimeMillis().toString()
    )
    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private class Fetched(val code: Int, val text: String)

    private suspend fun fetch(url: String, referer: String? = null, site: Boolean = false): Fetched? = try {
        val r = if (site) app.get(url, referer = referer ?: "$base/", headers = siteHeaders, cookies = siteCookies())
                else app.get(url, referer = referer)
        Fetched(r.code, r.text)
    } catch (cancel: CancellationException) { throw cancel }
      catch (_: Exception) { null }

    private fun siteUrl(raw: String): String? = try {
        val url = root.resolve(raw)
        url.toString().takeIf {
            url.scheme == "https" && url.host == root.host &&
                url.userInfo == null && url.fragment == null
        }
    } catch (_: Exception) { null }

    /** Resolve a player URL against the page it came from; handles "//host/.." and relative paths. */
    private fun playerUrl(raw: String, pageUrl: String): String? = try {
        if (raw.isBlank()) null
        else URI(pageUrl).resolve(raw.trim()).toString().takeIf { it.startsWith("https://") }
    } catch (_: Exception) { null }

    private fun isMolyHost(host: String?) =
        host != null && (host == "molystream.org" || host.endsWith(".molystream.org"))

    private fun slugify(t: String) = t.lowercase()
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun cleanSlug(seriesUrl: String) = seriesUrl.trimEnd('/').substringAfterLast('/')
        .replace(Regex("-(?:izle(?:-\\d+)?|dizi(?:-\\d+)?)$"), "")

    // [v28] parseEpisodeIdentity: ^(.+)-(\d+)-sezon-(\d+)-bolum(?:-[a-z0-9-]+)?$ after removing "-izle"
    private val episodePath = Regex("^(.+)-(\\d+)-sezon-(\\d+)-bolum(?:-[a-z0-9-]+)?$")
    private class EpId(val slug: String, val season: Int, val episode: Int)
    private fun parseEpisodeUrl(url: String): EpId? {
        val last = url.substringBefore('#').substringBefore('?').trimEnd('/')
            .substringAfterLast('/').removeSuffix("-izle")
        val m = episodePath.matchEntire(last) ?: return null
        return EpId(m.groupValues[1], m.groupValues[2].toIntOrNull() ?: return null,
            m.groupValues[3].toIntOrNull() ?: return null)
    }

    // ------------------------------------------------------------------ search

    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (query.title.isBlank()) return emptyList()
        val wanted = Identity.normalize(query.title)
        val encoded = URLEncoder.encode(query.title, "UTF-8")
        val urls = LinkedHashSet<String>()

        // 1) dwls_search JSON (V51 path). [obs] it returned exactly 5 results for two different
        //    "walking dead" queries and 0 for "game of thrones", so it may be capped or incomplete [hyp].
        val text = try {
            app.get("$base/wp-admin/admin-ajax.php?s=$encoded&action=dwls_search", referer = "$base/",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01"),
                cookies = mapOf("isTrustedUser" to "true",
                    "dbxu" to System.currentTimeMillis().toString())).text
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { "" }
        trace("search-response", "bytes=${text.length}, json=${text.trimStart().startsWith('{')}, title=${query.title}")
        runCatching {
            val results = org.json.JSONObject(text).optJSONArray("results") ?: org.json.JSONArray()
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                if (Identity.normalize(item.optString("post_title")) != wanted) continue
                siteUrl(item.optString("permalink"))?.let { urls.add(it) }
            }
        }
        trace("search-json", "matches=${urls.size}")

        // 2) HTML search page. [v28] GET /?s=<q>, results are article.detailed-article
        if (urls.isEmpty()) {
            urls.addAll(searchHtml(wanted, encoded))
            trace("search-html", "matches=${urls.size}")
        }
        // 3) Direct series page, accepted only if the page's own <h1> equals the requested title.
        //    [obs] /diziler/game-of-thrones/ answered 200 although search returned nothing.
        if (urls.isEmpty()) {
            urls.addAll(directSeries(query.title, wanted))
            trace("search-direct", "matches=${urls.size}")
        }
        return urls.take(5).map { seriesUrl ->
            MediaOffer(id, "DiziBox", query.title, query.year, query.kind,
                seriesUrl, query.tmdbId, query.season, query.episode)
        }
    }

    private suspend fun searchHtml(wanted: String, encoded: String): List<String> {
        val f = fetch("$base/?s=$encoded", site = true) ?: return emptyList()
        val doc = Jsoup.parse(f.text, "$base/")
        // Selectors come from v28's search()/toMainPageResult(); the result markup itself was never observed here [hyp].
        return doc.select("article.detailed-article").mapNotNull { art ->
            val link = art.selectFirst("a[href*='/diziler/']") ?: return@mapNotNull null
            val names = listOfNotNull(
                link.text(), art.selectFirst("strong")?.text(), art.selectFirst("h3")?.text(),
                art.selectFirst("img")?.attr("title"), art.selectFirst("img")?.attr("alt"))
            if (names.none { it.isNotBlank() && Identity.normalize(it) == wanted }) return@mapNotNull null
            siteUrl(link.attr("href"))
        }.distinct()
    }

    private suspend fun directSeries(title: String, wanted: String): List<String> {
        val slug = slugify(title)
        if (slug.isBlank()) return emptyList()
        for (candidate in listOf(slug, "$slug-izle")) {
            val url = "$base/diziler/$candidate/"
            val f = fetch(url, site = true) ?: continue
            if (f.code != 200) continue
            // [v28] load(): title is div.tv-overview h1
            val h1 = Jsoup.parse(f.text, url).selectFirst("div.tv-overview h1 a, div.tv-overview h1")?.text().orEmpty()
            if (h1.isNotBlank() && Identity.normalize(h1) == wanted) return listOf(url)
        }
        return emptyList()
    }

    // ----------------------------------------------------------------- resolve

    override suspend fun resolve(offer: MediaOffer): List<SourceLink> {
        if (offer.providerId != id || siteUrl(offer.pageUrl) != offer.pageUrl)
            return emptyList()
        val seriesFetch = fetch(offer.pageUrl, site = true)
        if (seriesFetch == null) { trace("series-page", "fetch-failed"); return emptyList() }
        val series = Jsoup.parse(seriesFetch.text, offer.pageUrl)
        trace("series-page", "code=${seriesFetch.code}; kind=${offer.kind}, season=${offer.season}, episode=${offer.episode}")

        val episodeUrl = if (offer.kind == MediaKind.SERIES) {
            val season = offer.season ?: return emptyList()
            val episode = offer.episode ?: return emptyList()
            findEpisodeUrl(offer.pageUrl, series, season, episode)
                ?: run { trace("episode-url", "not-found"); return emptyList() }
        } else offer.pageUrl

        val epFetch = if (episodeUrl == offer.pageUrl) seriesFetch
            else fetch(episodeUrl, referer = offer.pageUrl, site = true)
        if (epFetch == null) { trace("episode-page", "fetch-failed"); return emptyList() }
        val doc = Jsoup.parse(epFetch.text, episodeUrl)

        // [v28] main source = div#video-area iframe; alternatives = div.video-toolbar option[value],
        //       each alternative is its own page whose div#video-area iframe is decoded the same way.
        //       V51 only followed the first iframe.
        val entries = LinkedHashSet<String>()
        (doc.select("div#video-area iframe").firstOrNull() ?: doc.select("iframe").firstOrNull())
            ?.let { playerUrl(it.attr("src"), episodeUrl) }?.let { entries.add(it) }
        val alternatives = doc.select("div.video-toolbar option[value]")
            .mapNotNull { siteUrl(it.attr("value")) }.filter { it != episodeUrl }.distinct().take(6)
        trace("episode-page", "iframeCount=${doc.select("iframe").size}, main=${entries.size}, alternatives=${alternatives.size}")
        for (alt in alternatives) {
            val f = fetch(alt, referer = episodeUrl, site = true) ?: continue
            val d = Jsoup.parse(f.text, alt)
            (d.select("div#video-area iframe").firstOrNull() ?: d.select("iframe").firstOrNull())
                ?.let { playerUrl(it.attr("src"), alt) }?.let { entries.add(it) }
        }
        trace("player-entry", "hosts=${entries.map { runCatching { URI(it).host }.getOrNull() }}")

        // SourceLink's field names are not visible from this file, so no de-duplication by URL here;
        // entries are already a set of distinct player URLs.
        val out = ArrayList<SourceLink>()
        for (entry in entries) out.addAll(resolvePlayer(entry, episodeUrl))
        trace("resolve", "links=${out.size}")
        return out
    }

    private suspend fun findEpisodeUrl(seriesUrl: String, series: Document, season: Int, episode: Int): String? {
        val slug = cleanSlug(seriesUrl)
        // 1) episode cards on the series page itself ([v28] load(): article.grid-box)
        matchEpisode(series.select("article.grid-box a[href]"), season, episode, slug, seasonPage = false)
            ?.let { trace("episode-url", "series-page-card"); return it }
        // 2) season tab -> season page -> cards ([v28] div#seasons-list a, then article.grid-box)
        val seasonRx = Regex("(?<![0-9])$season\\. ?Sezon")
        val tabs = series.select("div#seasons-list a[href]")
            .filter { seasonRx.containsMatchIn(it.text()) || it.attr("href").contains("/$season-sezon") }
            .mapNotNull { siteUrl(it.attr("href")) }.distinct()
        for (tab in tabs) {
            val f = fetch(tab, referer = seriesUrl, site = true) ?: continue
            matchEpisode(Jsoup.parse(f.text, tab).select("article.grid-box a[href]"), season, episode, slug, seasonPage = true)
                ?.let { trace("episode-url", "season-tab"); return it }
        }
        // 3) direct URL patterns, accepted only when the fetched page really has a video area.
        //    [obs] ...-1-sezon-3-bolum-izle/ worked for Breaking Bad and Peaky Blinders; the other
        //    three patterns come from the PLT archive and were never confirmed [hyp].
        val raw = seriesUrl.trimEnd('/').substringAfterLast('/')
        val candidates = listOf(
            "$base/$slug-$season-sezon-$episode-bolum-izle/",
            "$base/$slug-$season-sezon-$episode-bolum-hd-izle/",
            "$base/$raw-$season-sezon-$episode-bolum-izle/",
            "$base/$slug-$season-sezon-$episode-bolum/"
        ).distinct()
        for (c in candidates) {
            val f = fetch(c, referer = seriesUrl, site = true) ?: continue
            if (f.code == 200 && f.text.contains("video-area")) { trace("episode-url", "direct-pattern"); return c }
        }
        return null
    }

    /**
     * V51 accepted ANY anchor whose text said "N. Bölüm" and whose ancestors' text mentioned the season,
     * but <body> is an ancestor of every anchor, so for season 1 the season test was always true [hyp: may
     * have picked a wrong link]. Here season and episode come from the URL (v28 regex) or the anchor text.
     */
    private fun matchEpisode(anchors: List<Element>, season: Int, episode: Int, slug: String, seasonPage: Boolean): String? {
        for (a in anchors) {
            val href = siteUrl(a.attr("href")) ?: continue
            val idn = parseEpisodeUrl(href)
            val text = a.text()
            val s = idn?.season ?: Regex("(\\d+)\\. ?Sezon").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val e = idn?.episode ?: Regex("(\\d+)\\. ?Bölüm").find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (e != episode) continue
            if (s != null) { if (s != season) continue } else if (!seasonPage) continue
            if (!seasonPage && (idn == null || idn.slug != slug)) continue
            return href
        }
        return null
    }

    // ------------------------------------------------------------------ players

    /**
     * Supported chains: dizibox king.php / moly.php / haydi.php -> Molystream embed -> HLS master.
     * v28 hands every other host to Cloudstream's loadExtractor(); this adapter has no equivalent,
     * so unknown hosts are traced ("unsupported-host") and skipped. That is the main remaining gap.
     */
    private suspend fun resolvePlayer(iframe: String, referer: String, depth: Int = 0): List<SourceLink> {
        if (depth >= 4) { trace("player", "depth-limit"); return emptyList() }
        val uri = try { URI(iframe) } catch (_: Exception) { return emptyList() }
        trace("player-hop", "host=${uri.host}, path=${uri.path}, depth=$depth")
        if (uri.scheme != "https" || uri.userInfo != null || uri.host.isNullOrBlank())
            return emptyList()
        val path = uri.path.orEmpty()
        if (uri.host == root.host) {
            return when {
                path.contains("/player/king/king.php") -> viaKing(iframe, referer, depth)
                path.contains("/player/moly/moly.php") -> viaMoly(iframe, referer, depth)
                path.contains("/player/haydi.php") -> viaHaydi(iframe, referer, depth)
                else -> { trace("player", "unsupported-site-path=$path"); emptyList() }
            }
        }
        if (isMolyHost(uri.host)) return resolveMolystream(iframe)
        trace("player", "cloudstream-extractor-host=${uri.host}")
        return cloudstreamExtract(iframe, referer)
    }

    /** Reuse CloudStream's bundled extractor registry for third-party player hosts. */
    private suspend fun cloudstreamExtract(url: String, referer: String): List<SourceLink> {
        val result = mutableListOf<SourceLink>()
        var rawCount = 0
        var rejectedCount = 0
        var extractorMatched = false
        val extractedSubtitles = mutableListOf<SourceSubtitle>()
        try {
            extractorMatched = loadExtractor(url, referer, { subtitle ->
                val subtitleUri = runCatching { URI(subtitle.url) }.getOrNull()
                if (subtitleUri?.scheme == "https" && subtitleUri.host != null && subtitleUri.userInfo == null) {
                    extractedSubtitles += SourceSubtitle(subtitle.lang, subtitle.url)
                }
            }, { link ->
                rawCount++
                val uri = runCatching { URI(link.url) }.getOrNull()
                if (uri?.scheme == "https" && uri.host != null && uri.userInfo == null) {
                    val hls = uri.path.orEmpty().endsWith(".m3u8", ignoreCase = true) ||
                        uri.path.orEmpty().startsWith("/embed/sheila/")
                    result.add(SourceLink(
                        id, link.url, link.quality.takeIf { it > 0 }, null, null,
                        referer = link.referer, isHls = hls,
                        displayName = link.name.takeIf { it.isNotBlank() },
                        subtitles = extractedSubtitles.distinctBy { it.language to it.url }
                    ))
                } else rejectedCount++
            })
        } catch (cancel: CancellationException) { throw cancel }
          catch (error: Exception) { trace("extractor-error", error.javaClass.simpleName) }
        trace("cloudstream-extractor", "host=${runCatching { URI(url).host }.getOrNull()} matched=$extractorMatched raw=$rawCount rejected=$rejectedCount accepted=${result.size}")
        return result.distinctBy { it.url }
    }

    private fun firstIframe(html: String, pageUrl: String): String? {
        val d = Jsoup.parse(html, pageUrl)
        val el = d.selectFirst("div#Player iframe") ?: d.selectFirst("iframe") ?: return null
        return playerUrl(el.attr("src"), pageUrl)
    }

    private suspend fun viaKing(iframe: String, referer: String, depth: Int): List<SourceLink> {
        val kingUrl = iframe.replace("king.php?v=", "king.php?wmode=opaque&v=")
        val f = fetch(kingUrl, referer = referer, site = true) ?: return emptyList()
        trace("king-response", "status=${f.code} bytes=${f.text.length} iframe=${Jsoup.parse(f.text).select("iframe").size} script=${Jsoup.parse(f.text).select("script").size} unescape=${f.text.contains("unescape(")}")
        val nested = firstIframe(f.text, kingUrl)
        if (nested != null && nested != iframe && nested != kingUrl) {
            val links = resolvePlayer(nested, kingUrl, depth + 1)
            if (links.isNotEmpty()) return links
        }

        // King may render an encrypted playlist directly, without a nested iframe.
        // Only accept a verified HTTPS Molystream HLS master; never return arbitrary JS URLs.
        val crypto = Regex("""CryptoJS\.AES\.decrypt\(\s*["']([^"'\r\n]+)["']\s*,\s*["']([^"'\r\n]+)["']\s*\)""").find(f.text)
        val decrypted = crypto?.let {
            openSslAesDecrypt(it.groupValues[2], it.groupValues[1])
        }
        val candidates = listOfNotNull(decrypted, f.text).flatMap { body ->
            Regex("""(?:file|src|source)\s*[:=]\s*["'](https://[^"']+)["']""",
                RegexOption.IGNORE_CASE).findAll(body).take(8)
                .map { it.groupValues[1].replace("&amp;", "&") }.toList()
        }.distinct()
        trace("king", "nested=${nested != null}, aes=${crypto != null}, decrypted=${decrypted != null}, candidates=${candidates.size}")
        for (candidate in candidates) {
            val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
            if (isMolyHost(uri.host)) {
                val links = verifiedPlaylist(candidate, "https://${uri.host}/")
                if (links.isNotEmpty()) return links
            }
        }
        return emptyList()
    }

    private suspend fun viaMoly(iframe: String, referer: String, depth: Int): List<SourceLink> {
        // [v28] moly.php?h= -> moly.php?wmode=opaque&h= ; body holds unescape("<urlencoded base64 html>").
        val playerUrl = iframe.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
        val f = fetch(playerUrl, referer = referer, site = true) ?: return emptyList()
        val decoded = decodeUnescaped(f.text)
        trace("moly-response", "status=${f.code} bytes=${f.text.length} iframe=${Jsoup.parse(f.text).select("iframe").size} unescape=${f.text.contains("unescape(")} decoded=${decoded != null}")
        val nested = firstIframe(decoded ?: f.text, playerUrl)
        if (nested == null) { trace("moly", "no-nested-iframe, bytes=${f.text.length}"); return emptyList() }
        if (nested == iframe || nested == playerUrl) return emptyList()
        return resolvePlayer(nested, playerUrl, depth + 1)
    }

    private suspend fun viaHaydi(iframe: String, referer: String, depth: Int): List<SourceLink> {
        // [v28] ?v= may itself be a base64 URL; otherwise the page holds unescape("<base64 html>") with an iframe.
        val direct = Regex("[?&]v=([^&#]+)").find(iframe)?.groupValues?.get(1)
            ?.let { decodeBase64Param(it) }?.trim()
        if (direct != null && direct.startsWith("http")) {
            val url = if (direct.startsWith("http://")) "https://" + direct.removePrefix("http://") else direct
            trace("haydi", "direct-host=${runCatching { URI(url).host }.getOrNull()}")
            return resolvePlayer(url, iframe, depth + 1)
        }
        val playerUrl = iframe.replace("haydi.php?v=", "haydi.php?wmode=opaque&v=")
        val f = fetch(playerUrl, referer = referer) ?: return emptyList()
        val nested = firstIframe(decodeUnescaped(f.text) ?: f.text, playerUrl)
        if (nested == null) { trace("haydi", "no-nested-iframe, bytes=${f.text.length}"); return emptyList() }
        if (nested == iframe || nested == playerUrl) return emptyList()
        return resolvePlayer(nested, playerUrl, depth + 1)
    }

    /** JS unescape() is percent-decoding only: '+' must stay '+' (URLDecoder would turn it into a space). */
    private fun percentDecode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun decodeBase64Param(s: String): String? = runCatching {
        String(Base64.decode(percentDecode(s), Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull()

    private fun decodeUnescaped(html: String): String? {
        val enc = Regex("""unescape\s*\(\s*(['"])(.*?)\1\s*\)""").find(html)?.groupValues?.get(2) ?: return null
        return decodeBase64Param(enc)
    }

    // --------------------------------------------------------------- molystream

    /**
     * [v28] King path: the player page contains CryptoJS.AES.decrypt("<data>","<pass>"); the decrypted
     * script holds file: '<url>'. V51 skipped this and built /embed/sheila/<id> from the embed id.
     * [obs] The synthesized URL does return a valid master for Breaking Bad and Peaky Blinders, but a raw
     * curl of the embed page showed no plain "file:" (DevTools showed it only after the page's own script ran).
     * Order here: decrypted file URL, then plain file/m3u8 in the page, then the synthesized URL (last resort).
     */
    private suspend fun resolveMolystream(embedUrl: String): List<SourceLink> {
        val uri = try { URI(embedUrl) } catch (_: Exception) { return emptyList() }
        val host = uri.host ?: return emptyList()
        val hostReferer = "https://$host/"
        val candidates = LinkedHashMap<String, String>() // url -> how it was found
        val page = fetch(embedUrl, referer = "$base/")?.text.orEmpty()

        // Match ciphertext and password from the SAME CryptoJS call. The old greedy
        // password regex could capture unrelated JavaScript up to a later ");".
        val crypto = Regex("""CryptoJS\.AES\.decrypt\(\s*["']([^"'\r\n]+)["']\s*,\s*["']([^"'\r\n]+)["']\s*\)""").find(page)
        val data = crypto?.groupValues?.get(1)
        val pass = crypto?.groupValues?.get(2)
        val decrypted = if (data != null && pass != null) openSslAesDecrypt(pass, data) else null
        trace("molystream-page", "host=$host, bytes=${page.length}, crypto=${data != null && pass != null}, decrypted=${decrypted != null}")
        decrypted?.let { Regex("file:\\s*'([^']+)'").find(it)?.groupValues?.get(1) }
            ?.let { candidates.getOrPut(it.replace("&amp;", "&")) { "decrypted" } }

        Regex("(?:file|src|source)\\s*[:=]\\s*['\"](https://[^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .findAll(page).map { it.groupValues[1].replace("&amp;", "&") }.take(5)
            .forEach { candidates.getOrPut(it) { "plain" } }

        val embedId = uri.path.orEmpty().removePrefix("/embed/").trim('/')
        if (uri.path.orEmpty().startsWith("/embed/") && embedId.matches(Regex("[a-zA-Z0-9-]+")))
            candidates.getOrPut("https://$host/embed/sheila/$embedId") { "synthesized" }

        for ((url, how) in candidates) {
            val found = verifiedPlaylist(url, hostReferer)
            trace("molystream-candidate", "how=$how, ok=${found.isNotEmpty()}")
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** [v28 CryptoJS.decrypt] Base64 -> "Salted__" + 8-byte salt + data; EVP_BytesToKey(MD5) -> AES-256-CBC key+IV. */
    private fun openSslAesDecrypt(passphrase: String, encryptedBase64: String): String? = try {
        val raw = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val salt = raw.copyOfRange(8, 16)
        val data = raw.copyOfRange(16, raw.size)
        val pass = passphrase.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("MD5")
        val derived = ByteArray(48)
        var filled = 0
        var prev = ByteArray(0)
        while (filled < 48) {
            md.reset(); md.update(prev); md.update(pass); md.update(salt)
            prev = md.digest()
            val n = minOf(prev.size, 48 - filled)
            System.arraycopy(prev, 0, derived, filled, n)
            filled += n
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // v28 names PKCS7Padding; same for 16-byte blocks
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, 0, 32, "AES"), IvParameterSpec(derived, 32, 16))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (_: Exception) { null }

    /**
     * Fetches the master playlist and requires an "#EXTM3U" body. [obs] Referer is required (Origin alone
     * returned an HTML "404 - No Video Found" page with HTTP 200); Origin is not. The path is no longer
     * restricted, the body check is the gate; the host must still be a molystream.org host.
     */
    private suspend fun verifiedPlaylist(url: String, referer: String): List<SourceLink> {
        val uri = try { URI(url) } catch (_: Exception) { return emptyList() }
        if (uri.scheme != "https" || uri.userInfo != null || !isMolyHost(uri.host)) return emptyList()
        val response = fetch(url, referer = referer)?.text ?: return emptyList()
        if (!response.trimStart().startsWith("#EXTM3U")) { trace("playlist", "not-m3u8; bytes=${response.length}"); return emptyList() }
        val heights = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
            .findAll(response).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val quality = heights.maxOrNull()
        trace("playlist", "verified quality=${quality ?: "adaptive"}")
        return listOf(SourceLink(id, url, quality, null, null,
            referer = referer, isHls = true,
            displayName = "DiziBox"))
    }
}