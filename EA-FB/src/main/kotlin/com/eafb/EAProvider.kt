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
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

/** One CloudStream-visible provider. Other modules never register separately. */
class EAProvider : MainAPI() {
    override var name = "EA-FB"
    override var mainUrl = "https://api.themoviedb.org/3"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override val hasMainPage = true

    /** Supply a personal TMDb API bearer token locally; never commit real credentials. */
    // Open movie by Blender Foundation (CC BY 3.0); retain original closing credits.
    private val openMovieData = "ea-fb:open:big-buck-bunny"
    private val openMovieUrl = "$mainUrl/ea-fb-open/big-buck-bunny"
    private val openMovieStream = "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4"
    private val livePrefix = "$mainUrl/ea-fb-live/"
    private val channelsUrl = "https://raw.githubusercontent.com/eaatabay/EA-FB/main/config/channels.json"
    private val categories = HomeCategories.all.filter { it.tmdbPath != null }
    override val mainPage = mainPageOf(
        "ea-fb-open" to "Açık Lisanslı Deneme Filmi",
        "ea-fb-live" to "Canlı TV",
        *categories.map { it.id to it.title }.toTypedArray()
    )

    private fun demoMovie(): SearchResponse = newMovieSearchResponse(
        "Big Buck Bunny (Deneme)", openMovieUrl, TvType.Movie
    ) { year = 2008 }

    /** Repository-curated sources: "authorized" is a manual record, not proof of distribution rights. */
    private suspend fun liveChannels(): List<Channel> {
        val json = runCatching { JSONObject(app.get(channelsUrl).text) }.getOrNull() ?: return emptyList()
        val rows = json.optJSONArray("channels") ?: return emptyList()
        val entries = (0 until rows.length()).flatMap { i ->
            val channel = rows.optJSONObject(i) ?: return@flatMap emptyList()
            val title = channel.optString("name")
            val sources = channel.optJSONArray("sources") ?: return@flatMap emptyList()
            (0 until sources.length()).mapNotNull { j ->
                val source = sources.optJSONObject(j) ?: return@mapNotNull null
                ApprovedLiveSource(
                    channel = title,
                    provider = source.optString("provider"),
                    url = source.optString("url"),
                    authorized = source.optBoolean("authorized", false),
                    quality = source.optInt("quality").takeIf { it > 0 },
                    audioLanguage = source.optString("audioLanguage").takeIf { it.isNotBlank() }
                )
            }
        }
        return LiveSourcePolicy.channels(entries)
    }

    private fun liveSearch(channel: Channel): SearchResponse = newLiveSearchResponse(
        channel.name, "$livePrefix${Identity.channelKey(channel.name)}", TvType.Live, fix = false
    )

    private suspend fun getJson(path: String, page: Int? = null): JSONObject? {
        val catalogToken = EASettings.tmdbToken()
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
        if (request.data == "ea-fb-open") {
            return newHomePageResponse(
                listOf(HomePageList("Açık Lisanslı Deneme Filmi", listOf(demoMovie()))), false
            )
        }
        if (request.data == "ea-fb-live") {
            val channels = liveChannels()
            return newHomePageResponse(
                if (channels.isEmpty()) emptyList() else listOf(HomePageList("Canlı TV", channels.map(::liveSearch))),
                false
            )
        }
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
        val demo = if (Identity.normalize("Big Buck Bunny").contains(Identity.normalize(query))) listOf(demoMovie()) else emptyList()
        val live = liveChannels().filter { Identity.normalize(it.name).contains(Identity.normalize(query)) }.map(::liveSearch)
        val arr = getJson("/search/multi?query=$q")?.optJSONArray("results")
        val catalog = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.takeIf { it.optString("media_type") in setOf("movie", "tv") }
                ?.let { newItem(it, MediaKind.MOVIE) }
        }
        return demo + live + catalog
    }

    override suspend fun load(url: String): LoadResponse {
        if (url == openMovieUrl) {
            return newMovieLoadResponse("Big Buck Bunny", url, TvType.Movie, openMovieData) {
                year = 2008
                plot = "Blender Foundation © 2008. Açık lisans: CC BY 3.0; filmin orijinal jeneriği korunur."
            }
        }
        if (url.startsWith(livePrefix)) {
            val channelKey = url.removePrefix(livePrefix)
            val channel = liveChannels().firstOrNull { Identity.channelKey(it.name) == channelKey }
                ?: error("Bu kanalın onaylı yayın kaynağı şu anda bulunamadı")
            return newLiveStreamLoadResponse(channel.name, url, "ea-fb:live:$channelKey") {
                plot = "Canlı TV: ${channel.links.size} izinli yayın seçeneği"
            }
        }
        require(url.startsWith("$mainUrl/movie/") || url.startsWith("$mainUrl/tv/")) { "Geçersiz EA-FB adresi" }
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
            newMovieLoadResponse(title, url, kind, "") {
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
        if (data == openMovieData) {
            callback(newExtractorLink("Blender Foundation", "Big Buck Bunny • 180p", openMovieStream) {
                quality = 180
                referer = ""
            })
            return true
        }
        if (data.startsWith("ea-fb:live:")) {
            val key = data.removePrefix("ea-fb:live:")
            val channel = liveChannels().firstOrNull { Identity.channelKey(it.name) == key } ?: return false
            val streams = SourcePicker.preferred(channel.links, System.currentTimeMillis())
            streams.forEach { source ->
                callback(newExtractorLink(source.provider, "${channel.name} • ${source.provider}", source.url) {
                    if (source.url.substringBefore('?').endsWith(".m3u8", true)) type = ExtractorLinkType.M3U8
                    quality = source.quality ?: 0
                    referer = ""
                })
            }
            return streams.isNotEmpty()
        }
        return false
    }
}
