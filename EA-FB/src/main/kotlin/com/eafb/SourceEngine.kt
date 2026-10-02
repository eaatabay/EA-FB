package com.eafb

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
    val episode: Int? = null
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
    val episode: Int? = null
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

/** Concurrent independent source resolution. No DRM bypass and no shared cache. */
class MultiSourceEngine(
    private val adapters: List<MediaSourceAdapter>,
    private val maxConcurrent: Int = 4,
    private val perAdapterTimeoutMs: Long = 8_000
) {
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
                                .asSequence()
                                .filter { it.providerId == adapter.id && sameContent(query, it) }
                                .take(30)
                                .toList()
                        }
                    } catch (_: TimeoutCancellationException) {
                        emptyList()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten().distinctBy { it.providerId to it.pageUrl }
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
        val links = offers.asSequence()
            .filter { it.pageUrl.startsWith("https://") }
            .distinctBy { it.providerId to it.pageUrl }
            .take(32)
            .mapNotNull { offer -> lookup[offer.providerId]?.let { it to offer } }
            .toList()
            .map { (adapter, offer) ->
                async(Dispatchers.IO) {
                    guard.withPermit {
                        try {
                            withTimeout(perAdapterTimeoutMs) {
                                adapter.resolve(offer).filter { it.provider == adapter.id && it.url.startsWith("https://") }
                            }
                        } catch (_: TimeoutCancellationException) {
                            emptyList()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten()
        SourcePicker.preferred(links, nowMillis, preferredLanguage, maxQuality)
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
        require(preferredProviderIds.distinct().size == preferredProviderIds.size)
        val installed = adapters.associateBy { it.id }
        val rank = preferredProviderIds.withIndex().associate { it.value to it.index }
        val ordered = offers.filter { offer ->
            installed.containsKey(offer.providerId) && sameContent(query, offer)
        }.sortedWith(compareBy<MediaOffer> {
            rank[it.providerId] ?: Int.MAX_VALUE
        }).distinctBy { it.providerId to it.pageUrl }.take(32)
        for (offer in ordered) {
            val adapter = installed[offer.providerId] ?: continue
            val links = try {
                withTimeout(perAdapterTimeoutMs) {
                    adapter.resolve(offer).filter { link ->
                        link.provider == adapter.id && link.url.startsWith("https://")
                    }
                }
            } catch (_: TimeoutCancellationException) {
                emptyList()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
            val selected = SourcePicker.preferred(
                links, nowMillis, preferredLanguage, maxQuality
            )
            if (selected.isNotEmpty()) return selected
        }
        return emptyList()
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
