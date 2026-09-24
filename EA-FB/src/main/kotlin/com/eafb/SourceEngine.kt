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
    val tmdbId: Int? = null
)

data class MediaQuery(
    val title: String,
    val year: Int?,
    val kind: MediaKind,
    val tmdbId: Int? = null
)

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
        val links = offers.mapNotNull { offer -> lookup[offer.providerId]?.let { it to offer } }
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

    private fun sameContent(query: MediaQuery, offer: MediaOffer): Boolean {
        if (query.kind != offer.kind) return false
        if (query.tmdbId != null && offer.tmdbId != null) return query.tmdbId == offer.tmdbId
        if (Identity.normalize(query.title) != Identity.normalize(offer.title)) return false
        return query.year == null || (offer.year != null && offer.year == query.year)
    }
}
