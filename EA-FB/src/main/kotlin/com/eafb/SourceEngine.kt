package com.eafb

import android.util.Log
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class MediaOffer(
    val providerId: String,
    val providerTitle: String,
    val title: String,
    val year: Int?,
    val kind: MediaKind,
    val pageUrl: String,
    val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val playbackData: String? = null
) {
    init { require((season == null && episode == null) ||
        (kind == MediaKind.SERIES && season != null && season >= 0 &&
            episode != null && episode > 0)) }
}

data class MediaQuery(
    val title: String,
    val year: Int?,
    val kind: MediaKind,
    val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val alternateTitles: List<String> = emptyList()
) {
    init { require((season == null && episode == null) ||
        (kind == MediaKind.SERIES && season != null && season >= 0 &&
            episode != null && episode > 0)) }
}

/** Implement only for sources that permit integration. */
interface MediaSourceAdapter {
    val id: String
    suspend fun search(query: MediaQuery): List<MediaOffer>
    suspend fun resolve(offer: MediaOffer): List<SourceLink>
}

/** Optional incremental delivery; implementations call emit sequentially in this coroutine. */
interface ProgressiveMediaSourceAdapter : MediaSourceAdapter {
    suspend fun resolveIncrementally(offer: MediaOffer, emit: (SourceLink) -> Unit)
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> = buildList {
        resolveIncrementally(offer) { add(it) }
    }
}

/** Concurrent independent source resolution. No DRM bypass and no shared cache. */
class MultiSourceEngine(
    adapters: List<MediaSourceAdapter>,
    private val maxConcurrent: Int = 4,
    private val perAdapterTimeoutMs: Long = 20_000
) {
    private val adapters = adapters.toList()

    init {
        require(maxConcurrent in 1..16)
        require(perAdapterTimeoutMs in 100..60_000)
        require(adapters.map { it.id }.distinct().size == adapters.size)
    }

    suspend fun find(query: MediaQuery): List<MediaOffer> = supervisorScope {
        val guard = Semaphore(maxConcurrent)
        adapters.map { adapter ->
            async(Dispatchers.IO) {
                guard.withPermit {
                    try {
                        withTimeout(perAdapterTimeoutMs) {
                            adapter.search(query)
                                .also { Log.i("EA-FB-Source", "search provider=${adapter.id} offers=${it.size}") }
                                .asSequence()
                                .filter { it.providerId == adapter.id && it.pageUrl.startsWith("https://") && sameContent(query, it) }
                                .take(30)
                                .toList()
                        }
                    } catch (_: TimeoutCancellationException) {
                        Log.w("EA-FB-Source", "search timeout provider=${adapter.id}")
                        emptyList()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten().distinctBy { Triple(it.providerId, it.pageUrl, it.playbackData) }
    }

    suspend fun resolve(
        offers: List<MediaOffer>,
        nowMillis: Long,
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> = supervisorScope {
        val guard = Semaphore(maxConcurrent)
        val lookup = adapters.associateBy { it.id }
        // A caller may supply offers directly; do not trust them merely because
        // their provider ID is installed. Cap work and reject malformed origins.
        require(nowMillis >= 0)
        require(maxQuality > 0)
        val links = offers.asSequence()
            .filter { it.pageUrl.startsWith("https://") }
            .distinctBy { Triple(it.providerId, it.pageUrl, it.playbackData) }
            .take(32)
            .mapNotNull { offer -> lookup[offer.providerId]?.let { it to offer } }
            .toList()
            .map { (adapter, offer) ->
                async(Dispatchers.IO) {
                    guard.withPermit {
                        resolveAdapter(adapter, offer)
                    }
                }
            }.awaitAll().flatten()
        SourcePicker.preferred(links, nowMillis, preferredLanguage, maxQuality)
            .filter { SourceLinkPolicy.compatibleQuality(it, maxQuality) }
    }

    /**
     * Try a centrally preferred, currently installed adapter first, then
     * independent fallback offers. This only resolves fresh HTTPS links:
     * successful discovery is NOT proof that the video actually played.
     * The caller must pass offers from an approved source search; this method
     * cannot grant distribution rights or persist playback history.
     */
    suspend fun resolveFirstAvailable(
        query: MediaQuery,
        offers: List<MediaOffer>,
        nowMillis: Long,
        preferredProviderIds: List<String> = emptyList(),
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> {
        require(nowMillis >= 0)
        require(maxQuality > 0)
        require(preferredProviderIds.distinct().size == preferredProviderIds.size)
        val installed = adapters.associateBy { it.id }
        val rank = preferredProviderIds.withIndex().associate { it.value to it.index }
        val ordered = offers.filter { offer ->
            installed.containsKey(offer.providerId) &&
                offer.pageUrl.startsWith("https://") && sameContent(query, offer)
        }.sortedWith(compareBy<MediaOffer> {
            rank[it.providerId] ?: Int.MAX_VALUE
        }).distinctBy { Triple(it.providerId, it.pageUrl, it.playbackData) }.take(32)
        for (offer in ordered) {
            val adapter = installed[offer.providerId] ?: continue
            val links = resolveAdapter(adapter, offer)
            val selected = SourcePicker.preferred(
                links, nowMillis, preferredLanguage, maxQuality
            )
            val compatible = selected.filter { SourceLinkPolicy.compatibleQuality(it, maxQuality) }
            if (compatible.isNotEmpty()) return compatible
        }
        return emptyList()
    }

    private suspend fun resolveAdapter(adapter: MediaSourceAdapter, offer: MediaOffer): List<SourceLink> {
        val accepted = mutableListOf<SourceLink>()
        fun accept(link: SourceLink) {
            if (link.provider == adapter.id && SourceLinkPolicy.isPlaybackUrl(link) && !link.requiresPrivateSession)
                synchronized(accepted) { accepted += link }
        }
        try {
            withTimeout(perAdapterTimeoutMs) {
                if (adapter is ProgressiveMediaSourceAdapter) adapter.resolveIncrementally(offer, ::accept)
                else adapter.resolve(offer).forEach(::accept)
            }
        } catch (_: TimeoutCancellationException) {
            Log.w("EA-FB-Source", "resolve timeout provider=${adapter.id} retained=${accepted.size}")
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w("EA-FB-Source", "resolve failed provider=${adapter.id} error=${error.javaClass.simpleName} retained=${accepted.size}")
        }
        Log.i("EA-FB-Source", "resolve provider=${adapter.id} links=${accepted.size}")
        return synchronized(accepted) { accepted.toList() }
    }

    private fun sameContent(query: MediaQuery, offer: MediaOffer): Boolean {
        if (query.kind != offer.kind) return false
        if (query.season != null &&
            (query.season != offer.season || query.episode != offer.episode)) return false
        if (query.season != null && query.tmdbId != null &&
            query.tmdbId != offer.tmdbId) return false
        // An equal external ID must not override a contradicting known release year.
        if (query.year != null && offer.year != null && query.year != offer.year) return false
        if (query.tmdbId != null && offer.tmdbId != null) return query.tmdbId == offer.tmdbId
        if (Identity.normalize(query.title) != Identity.normalize(offer.title)) return false
        return query.year == null || (offer.year != null && offer.year == query.year)
    }
}
