package com.eafb

import java.text.Normalizer
import java.util.Locale

/** Pure Kotlin: can be tested without Android, CloudStream or network. */
enum class MediaKind { MOVIE, SERIES, LIVE }

data class CatalogCategory(val id: String, val title: String, val kind: MediaKind, val tmdbPath: String? = null)

/** The 28 sections documented from the user's PLT-Stream screenshots. */
object HomeCategories {
    val all: List<CatalogCategory> = listOf(
        CatalogCategory("continue", "İzlemeye Devam Et", MediaKind.SERIES),
        CatalogCategory("trending", "Günün Trendleri", MediaKind.MOVIE, "/trending/all/day"),
        CatalogCategory("now-playing", "Vizyondaki Filmler", MediaKind.MOVIE, "/movie/now_playing"),
        CatalogCategory("popular-movie", "Haftanın Popüler Filmleri", MediaKind.MOVIE, "/trending/movie/week"),
        CatalogCategory("popular-tv", "Haftanın Popüler Dizileri", MediaKind.SERIES, "/trending/tv/week"),
        CatalogCategory("top-movie", "En Yüksek Puanlı Filmler", MediaKind.MOVIE, "/movie/top_rated"),
        CatalogCategory("top-tv", "En Yüksek Puanlı Diziler", MediaKind.SERIES, "/tv/top_rated"),
        CatalogCategory("netflix-movie", "Netflix Filmleri", MediaKind.MOVIE, "/discover/movie?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("netflix-tv", "Netflix Dizileri", MediaKind.SERIES, "/discover/tv?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("disney-movie", "Disney+ Filmleri", MediaKind.MOVIE, "/discover/movie?with_watch_providers=337&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("disney-tv", "Disney+ Dizileri", MediaKind.SERIES, "/discover/tv?with_watch_providers=337&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("amazon-movie", "Amazon Prime Filmleri", MediaKind.MOVIE, "/discover/movie?with_watch_providers=119&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("amazon-tv", "Amazon Prime Dizileri", MediaKind.SERIES, "/discover/tv?with_watch_providers=119&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("apple-movie", "Apple TV Filmleri", MediaKind.MOVIE, "/discover/movie?with_watch_providers=350&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("apple-tv", "Apple TV Dizileri", MediaKind.SERIES, "/discover/tv?with_watch_providers=350&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("max-movie", "HBO Max Filmleri", MediaKind.MOVIE, "/discover/movie?with_watch_providers=1899&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("max-tv", "HBO Max Dizileri", MediaKind.SERIES, "/discover/tv?with_watch_providers=1899&watch_region=TR&with_watch_monetization_types=flatrate"),
        CatalogCategory("paramount-tv", "Paramount+ Dizileri", MediaKind.SERIES),
        CatalogCategory("mubi-movie", "MUBI Filmleri", MediaKind.MOVIE),
        CatalogCategory("action", "Aksiyon Filmleri", MediaKind.MOVIE, "/discover/movie?with_genres=28"),
        CatalogCategory("sci-fi", "Bilim Kurgu Filmleri", MediaKind.MOVIE, "/discover/movie?with_genres=878"),
        CatalogCategory("horror", "Korku & Gerilim", MediaKind.MOVIE, "/discover/movie?with_genres=27,53"),
        CatalogCategory("comedy", "Komedi Filmleri", MediaKind.MOVIE, "/discover/movie?with_genres=35"),
        CatalogCategory("animation-movie", "Animasyon Filmleri", MediaKind.MOVIE, "/discover/movie?with_genres=16"),
        CatalogCategory("animation-tv", "Animasyon Dizileri", MediaKind.SERIES, "/discover/tv?with_genres=16"),
        CatalogCategory("documentary-top", "BelgeselX – En Çok İzlenenler", MediaKind.SERIES),
        CatalogCategory("documentary-trend", "BelgeselX – Haftanın Trendleri", MediaKind.SERIES),
        CatalogCategory("community", "EA-FB Önerileri", MediaKind.MOVIE)
    )
}

object Identity {
    fun normalize(name: String): String = Normalizer.normalize(
        name.replace('ı', 'i').replace('İ', 'I').lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun mediaKey(kind: MediaKind, title: String, year: Int?): String =
        "${kind.name}:${normalize(title)}:${year ?: "unknown"}"

    fun channelKey(name: String): String = normalize(name)
        .replace(Regex("\\b(hd|sd|fhd|4k|canli|live)\\b"), "")
        .replace(Regex("\\s+"), "")
        .trim()
}

data class SourceLink(
    val provider: String,
    val url: String,
    val quality: Int?,
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val expiresAtMillis: Long? = null,
    val requiresPrivateSession: Boolean = false
)

/** Do not store user-specific links in a shared cache. Cache implementation is postponed. */
object SourcePicker {
    fun unique(links: List<SourceLink>): List<SourceLink> = links.distinctBy { it.provider to it.url }

    fun preferred(
        links: List<SourceLink>,
        nowMillis: Long,
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> = unique(links)
        .filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }
        .sortedWith(
            compareByDescending<SourceLink> { it.audioLanguage == preferredLanguage }
                .thenByDescending { (it.quality ?: 0).coerceAtMost(maxQuality) }
                .thenBy { it.provider }
        )
}

data class Channel(val name: String, val links: List<SourceLink>)

object ChannelMerger {
    fun merge(channels: List<Channel>): List<Channel> = channels.groupBy { Identity.channelKey(it.name) }
        .values.map { variants ->
            Channel(
                name = variants.first().name.replace(Regex("\\s+(HD|SD|FHD|4K)$", RegexOption.IGNORE_CASE), ""),
                links = SourcePicker.unique(variants.flatMap { it.links })
            )
        }
}
