package com.eafb

import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.net.URI
import java.util.concurrent.CancellationException

/**
 * The pinned Bronze DEX is merged into this test's DEX at packaging time.
 * These are NOT recovered Bronze Kotlin sources. Delegate the actual search,
 * load and loadLinks implementations, including Rhino/FastPlay/LocalHlsServer.
 * Do not register the bundled Bronze plugin entry points as extra providers.
 */
open class BronzeApiAdapter(
    final override val id: String,
    private val label: String,
    private val domain: String,
    private val factory: () -> MainAPI
) : ProgressiveMediaSourceAdapter {
    private val api: MainAPI by lazy(factory)

    private fun sourceUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme == "https" && uri.userInfo == null && uri.fragment == null &&
            (uri.host == domain || uri.host == "www.$domain")
    }.getOrDefault(false)

    private fun nameKey(name: String): String = Identity.normalize(name.trim().removeSuffix(" izle"))

    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (query.kind == MediaKind.LIVE) return emptyList()
        if (query.kind == MediaKind.MOVIE) return searchMovie(query)
        val titles = SourceSearchTitles.candidates(query)
        val wanted = titles.map(::nameKey).toSet()
        val offers = mutableListOf<MediaOffer>()
        val visited = mutableSetOf<String>()
        try {
            for (title in titles) {
                for (result in api.search(title).orEmpty().take(50)) {
                    if (nameKey(result.name) !in wanted || !sourceUrl(result.url) || !visited.add(result.url)) continue
                    val loaded = api.load(result.url) ?: continue
                    if (nameKey(loaded.name) !in wanted ||
                        (query.year != null && loaded.year != null && query.year != loaded.year)) continue
                    val payloads = when (loaded) {
                        is MovieLoadResponse -> if (query.kind == MediaKind.MOVIE) listOf(loaded.dataUrl) else emptyList()
                        is TvSeriesLoadResponse -> if (query.kind == MediaKind.SERIES && query.season != null && query.episode != null)
                            loaded.episodes.filter { it.season == query.season && it.episode == query.episode }.map { it.data }
                            else emptyList()
                        else -> emptyList()
                    }
                    for (data in payloads.distinct().filter { it.isNotBlank() }) {
                        offers += MediaOffer(id, label, query.title, loaded.year ?: query.year, query.kind,
                            result.url, query.tmdbId, query.season, query.episode, data)
                    }
                    if (offers.size >= 5) break
                }
                if (offers.isNotEmpty()) break
            }
        } catch (cancel: CancellationException) { throw cancel }
          catch (error: Exception) { traceFailure("search", error) }
          catch (error: LinkageError) { traceFailure("runtime", error) }
        return offers.take(5)
    }

    // Film matching only: keep the existing series/episode search path unchanged.
    private suspend fun searchMovie(query: MediaQuery): List<MediaOffer> {
        val titles = SourceSearchTitles.candidates(query)
        val matcher = BronzeMovieNames(titles)
        val visited = mutableSetOf<String>()
        var examined = 0
        fun reject(reason: String, actualYear: Int? = null) {
            Log.i("EA-FB-Bronze", "movie reject provider=$id tmdb=${query.tmdbId} reason=$reason expectedYear=${query.year} actualYear=$actualYear")
        }
        for (title in titles) {
            val results = try { api.search(title).orEmpty().take(50) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { traceFailure("search", error); continue }
            catch (error: LinkageError) { traceFailure("runtime", error); continue }
            val offers = mutableListOf<MediaOffer>()
            for (result in results) {
                examined++
                val resultMatch = matcher.match(result.name)
                if (resultMatch == BronzeMovieNames.Match.NONE) { reject("search-title"); continue }
                if (!sourceUrl(result.url)) { reject("source-origin"); continue }
                if (!visited.add(result.url)) continue
                val loaded = try { api.load(result.url) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { traceFailure("load", error); continue }
                catch (error: LinkageError) { traceFailure("runtime", error); continue }
                if (loaded !is MovieLoadResponse) { reject("media-type"); continue }
                val loadedMatch = matcher.match(loaded.name)
                if (loadedMatch == BronzeMovieNames.Match.NONE) { reject("load-title", loaded.year); continue }
                if (query.year != null && loaded.year != null && query.year != loaded.year) {
                    reject("year-mismatch", loaded.year); continue
                }
                // Newly accepted spellings/composite aliases require positive year corroboration.
                if ((resultMatch == BronzeMovieNames.Match.VARIANT || loadedMatch == BronzeMovieNames.Match.VARIANT) &&
                    (query.year == null || loaded.year == null || query.year != loaded.year)) {
                    reject("variant-needs-year", loaded.year); continue
                }
                if (loaded.dataUrl.isBlank()) { reject("empty-data", loaded.year); continue }
                offers += MediaOffer(id, label, query.title, loaded.year ?: query.year, query.kind,
                    result.url, query.tmdbId, playbackData = loaded.dataUrl)
                // Two distinct plausible pages are ambiguous; never choose by result order.
                if (offers.size > 1) { reject("ambiguous-pages"); return emptyList() }
            }
            if (offers.isNotEmpty()) {
                Log.i("EA-FB-Bronze", "movie search provider=$id tmdb=${query.tmdbId} examined=$examined offers=1")
                return offers
            }
        }
        Log.i("EA-FB-Bronze", "movie search provider=$id tmdb=${query.tmdbId} examined=$examined offers=0")
        return emptyList()
    }

    override suspend fun resolveIncrementally(offer: MediaOffer, emit: (SourceLink) -> Unit) {
        if (offer.providerId != id || !sourceUrl(offer.pageUrl)) return
        val data = offer.playbackData?.takeIf { it.isNotBlank() } ?: return
        // The list stays live until loadLinks completes; late subtitle callbacks are retained.
        val subtitles = java.util.Collections.synchronizedList(mutableListOf<SourceSubtitle>())
        try {
            api.loadLinks(data, false, { sub ->
                if (sub.url.startsWith("https://") ||
                    (id == "hdfilmcehennemi-land" && SourceLinkPolicy.isLandLoopback(sub.url, subtitle = true))) {
                    val value = SourceSubtitle(sub.lang, sub.url, sub.headers.orEmpty().toMap())
                    synchronized(subtitles) { if (value !in subtitles) subtitles.add(value) }
                }
            }, { link ->
                val loopback = id == "hdfilmcehennemi-land" && link.type == ExtractorLinkType.M3U8 &&
                    SourceLinkPolicy.isLandLoopback(link.url)
                if (link.url.startsWith("https://") || loopback) {
                    val headers = link.headers.toMap()
                    val referer = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: link.referer
                    emit(SourceLink(id, link.url, link.quality.takeIf { it > 0 }, null, null,
                        referer = referer, isHls = link.type == ExtractorLinkType.M3U8,
                        displayName = "$label • ${link.name}", subtitles = subtitles, headers = headers,
                        isAdaptive = loopback, isLandLoopback = loopback))
                }
            })
        } catch (cancel: CancellationException) { throw cancel }
          catch (error: Exception) { traceFailure("resolve", error) }
          catch (error: LinkageError) { traceFailure("runtime", error) }
    }

    private fun traceFailure(stage: String, error: Throwable) {
        // Never log opaque playback data, signed URLs, nonce, cookies or X-Sp values.
        Log.w("EA-FB-Bronze", "$stage provider=$id error=${error.javaClass.simpleName}")
    }
}

/** No fuzzy substring matching or guessed translations; aliases come only from the query. */
private class BronzeMovieNames(titles: List<String>) {
    enum class Match { NONE, EXACT, VARIANT }
    private fun key(raw: String): String = Identity.normalize(raw.trim().removeSuffix(" izle"))
    private val wanted = titles.map(::key).filter { it.isNotBlank() }.toSet()
    private fun words(value: String): List<String> = value.removePrefix("the ").split(' ').filter { it.isNotBlank() }
    private fun equivalent(value: String, alias: String): Boolean {
        val left = words(value)
        val right = words(alias)
        return left.isNotEmpty() && right.isNotEmpty() &&
            (left.joinToString("") == right.joinToString("") || left.sorted() == right.sorted())
    }
    fun match(raw: String): Match {
        val value = key(raw)
        if (value in wanted) return Match.EXACT
        // Split only a spaced bilingual separator, never the hyphen in Spider-Man.
        val parts = raw.trim().split(Regex("\\s+[-–—|]\\s+"))
        if (parts.size > 1) {
            return if (parts.all { part -> wanted.any { equivalent(key(part), it) } }) Match.VARIANT else Match.NONE
        }
        return if (wanted.any { equivalent(value, it) }) Match.VARIANT else Match.NONE
    }
}

private fun bundledBronze(className: String): MainAPI =
    Class.forName(className, true, BronzeApiAdapter::class.java.classLoader).getDeclaredConstructor().newInstance() as MainAPI

class BronzeNlAdapter(factory: (() -> MainAPI)? = null) : BronzeApiAdapter(
    "hdfilmcehennemi-nl", "HDFilmCehennemi", "hdfilmcehennemi.nl",
    factory ?: { bundledBronze("com.keyiflerolsun.HDFilmCehennemi") })

class BronzeLandAdapter(factory: (() -> MainAPI)? = null) : BronzeApiAdapter(
    "hdfilmcehennemi-land", "HDFilmCehennemi LAND", "hdfilmcehennemi.land",
    factory ?: { bundledBronze("com.keyiflerolsun.HDFilmcehennemiLand") })
