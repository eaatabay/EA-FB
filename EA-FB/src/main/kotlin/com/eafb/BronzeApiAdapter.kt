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

private fun bundledBronze(className: String): MainAPI =
    Class.forName(className, true, BronzeApiAdapter::class.java.classLoader).getDeclaredConstructor().newInstance() as MainAPI

class BronzeNlAdapter(factory: (() -> MainAPI)? = null) : BronzeApiAdapter(
    "hdfilmcehennemi-nl", "HDFilmCehennemi", "hdfilmcehennemi.nl",
    factory ?: { bundledBronze("com.keyiflerolsun.HDFilmCehennemi") })

class BronzeLandAdapter(factory: (() -> MainAPI)? = null) : BronzeApiAdapter(
    "hdfilmcehennemi-land", "HDFilmCehennemi LAND", "hdfilmcehennemi.land",
    factory ?: { bundledBronze("com.keyiflerolsun.HDFilmcehennemiLand") })
