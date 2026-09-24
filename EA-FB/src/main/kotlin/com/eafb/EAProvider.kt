package com.eafb

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject

/** One CloudStream-visible provider. Other modules never register separately. */
class EAProvider : MainAPI() {
    override var name = "EA-FB"
    override var mainUrl = "https://api.themoviedb.org/3"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override val hasMainPage = true

    /** Supply a personal TMDb API bearer token locally; never commit real credentials. */
    private val catalogToken = EAConfig.tmdbBearerToken
    private val categories = HomeCategories.all.filter { it.tmdbPath != null }
    override val mainPage = mainPageOf(*categories.map { it.id to it.title }.toTypedArray())

    private suspend fun getJson(path: String, page: Int? = null): JSONObject? {
        if (catalogToken.isBlank()) return null
        val join = if ('?' in path) '&' else '?'
        val url = "$mainUrl$path${join}language=tr-TR${if (page != null) "&page=$page" else ""}"
        return runCatching {
            JSONObject(app.get(url, headers = mapOf("Authorization" to "Bearer $catalogToken")).text)
        }.getOrNull()
    }

    private fun mediaKind(item: JSONObject, fallback: MediaKind): MediaKind =
        if (item.optString("media_type") == "tv" || (item.has("name") && !item.has("title"))) MediaKind.SERIES
        else if (item.optString("media_type") == "movie") MediaKind.MOVIE
        else fallback

    private fun newItem(item: JSONObject, fallback: MediaKind): SearchResponse? {
        val id = item.optInt("id").takeIf { it > 0 } ?: return null
        val kind = mediaKind(item, fallback)
        val title = item.optString(if (kind == MediaKind.SERIES) "name" else "title").ifBlank { return null }
        val path = if (kind == MediaKind.SERIES) "tv" else "movie"
        val poster = item.optString("poster_path").takeIf { it.startsWith("/") }
        return newMovieSearchResponse(title, "$mainUrl/$path/$id", if (kind == MediaKind.SERIES) TvType.TvSeries else TvType.Movie) {
            posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val category = categories.firstOrNull { it.id == request.data }
            ?: return newHomePageResponse(emptyList(), false)
        val results = getJson(category.tmdbPath ?: return newHomePageResponse(emptyList(), false), page)
            ?.optJSONArray("results")
            ?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { newItem(it, category.kind) } } }
            .orEmpty()
        return newHomePageResponse(listOf(HomePageList(category.title, results, true)), results.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val arr = getJson("/search/multi?query=$q")?.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.takeIf { it.optString("media_type") in setOf("movie", "tv") }
                ?.let { newItem(it, MediaKind.MOVIE) }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val path = url.substringAfter(mainUrl)
        val isSeries = path.startsWith("/tv/")
        val kind = if (isSeries) TvType.TvSeries else TvType.Movie
        val item = getJson(path) ?: error("EA-FB: TMDb anahtarı ayarlanmadı veya katalog erişilemiyor")
        val title = item.optString(if (isSeries) "name" else "title")
        val overview = item.optString("overview")
        val poster = item.optString("poster_path").takeIf { it.startsWith("/") }
        return if (isSeries) {
            // Episodes and supported source adapters will be populated in the next milestone.
            newTvSeriesLoadResponse(title, url, kind, emptyList()) {
                plot = overview
                posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
            }
        } else {
            newMovieLoadResponse(title, url, kind, url) {
                plot = overview
                posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // In this foundation release there are no verified playable adapters.
        // Do not advertise a video or bypass paid streaming DRM.
        return false
    }
}
