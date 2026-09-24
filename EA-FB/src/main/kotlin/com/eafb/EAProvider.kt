package com.eafb

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.newEpisode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
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
    // The former Google GTV sample now returns HTTP 403. Prefer the public Mux
    // HLS Big Buck Bunny test stream, with an independent MP4 option.
    private val openMovieHls = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private val openMovieMp4 = "https://uploads.video-commander.com/sample/BigBuckBunny.mp4"
    // Attribution: (c) copyright Blender Foundation | www.bigbuckbunny.org (CC BY 3.0)
    private val openMoviePoster = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/500px-Big_buck_bunny_poster_big.jpg"
    // Use the same JPG confirmed to load in the film card for the large banner.
    // The earlier standalone PNG was not displaying on the user's TV.
    private val openMovieBackdrop = openMoviePoster
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
    ) {
        year = 2008
        posterUrl = openMoviePoster
    }

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

    private suspend fun getJson(path: String, page: Int? = null, language: String = "tr-TR"): JSONObject? {
        val catalogToken = EASettings.tmdbToken()
        if (catalogToken.isBlank()) return null
        val join = if ('?' in path) '&' else '?'
        val url = "$mainUrl$path${join}language=$language${if (page != null) "&page=$page" else ""}"
        return runCatching {
            JSONObject(app.get(url, headers = mapOf("Authorization" to "Bearer $catalogToken")).text)
        }.getOrNull()
    }

    private fun mediaKind(item: JSONObject, fallback: MediaKind): MediaKind =
        if (item.optString("media_type") == "tv" || (item.has("name") && !item.has("title"))) MediaKind.SERIES
        else if (item.optString("media_type") == "movie") MediaKind.MOVIE
        else fallback

    /** TMDb premiere year for cards and the planned visual year badge. */
    private fun mediaYear(item: JSONObject, kind: MediaKind): Int? {
        val primary = if (kind == MediaKind.SERIES) "first_air_date" else "release_date"
        val fallback = if (kind == MediaKind.SERIES) "release_date" else "first_air_date"
        val date = item.optString(primary).ifBlank { item.optString(fallback) }
        return date.take(4).toIntOrNull()?.takeIf { it in 1888..2100 }
    }

    private fun newItem(item: JSONObject, fallback: MediaKind): SearchResponse? {
        // Mixed TMDb feeds also include people; never render actors as movie cards.
        val type = item.optString("media_type")
        if (type.isNotBlank() && type != "movie" && type != "tv") return null
        val id = item.optInt("id").takeIf { it > 0 } ?: return null
        val kind = mediaKind(item, fallback)
        val title = item.optString(if (kind == MediaKind.SERIES) "name" else "title").ifBlank { return null }
        val path = if (kind == MediaKind.SERIES) "tv" else "movie"
        val poster = item.optString("poster_path").takeIf { it.startsWith("/") }
        return newMovieSearchResponse(title, "$mainUrl/$path/$id", if (kind == MediaKind.SERIES) TvType.TvSeries else TvType.Movie) {
            posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
            year = mediaYear(item, kind)
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


    private fun image(path: String?, width: String): String? =
        path?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$width$it" }

    private fun genres(item: JSONObject): List<String> {
        val rows = item.optJSONArray("genres") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }
    }

    private fun cast(item: JSONObject, series: Boolean): List<ActorData> {
        val section = if (series) "aggregate_credits" else "credits"
        val people = item.optJSONObject(section)?.optJSONArray("cast") ?: return emptyList()
        return (0 until minOf(people.length(), 18)).mapNotNull { i ->
            val person = people.optJSONObject(i) ?: return@mapNotNull null
            val personName = person.optString("name").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val role = if (series) {
                person.optJSONArray("roles")?.optJSONObject(0)?.optString("character")
            } else person.optString("character")
            ActorData(
                Actor(personName, image(person.optString("profile_path"), "w185")),
                roleString = role?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun creators(item: JSONObject, series: Boolean): String? {
        if (series) {
            val rows = item.optJSONArray("created_by") ?: return null
            val names = (0 until rows.length()).mapNotNull { i ->
                rows.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
            }.distinct().take(5)
            return names.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Yaratıcı: ")
        }
        val rows = item.optJSONObject("credits")?.optJSONArray("crew") ?: return null
        val names = (0 until rows.length()).mapNotNull { i ->
            val crew = rows.optJSONObject(i) ?: return@mapNotNull null
            if (crew.optString("job") != "Director") return@mapNotNull null
            crew.optString("name").takeIf { it.isNotBlank() }
        }.distinct().take(5)
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Yönetmen: ")
    }

    private fun recommendations(item: JSONObject, kind: MediaKind, ownId: Int): List<SearchResponse> {
        val rows = item.optJSONObject("recommendations")?.optJSONArray("results")
            ?: return emptyList()
        val seen = mutableSetOf(ownId)
        return (0 until rows.length()).mapNotNull { i ->
            val next = rows.optJSONObject(i) ?: return@mapNotNull null
            if (!seen.add(next.optInt("id"))) return@mapNotNull null
            newItem(next, kind)
        }.take(18)
    }

    private fun nextEpisode(item: JSONObject): NextAiring? {
        val upcoming = item.optJSONObject("next_episode_to_air") ?: return null
        val episode = upcoming.optInt("episode_number").takeIf { it > 0 } ?: return null
        val airDate = upcoming.optString("air_date").takeIf { it.length >= 10 }
            ?: return null
        val seconds = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
                .parse(airDate)?.time?.div(1000)
        }.getOrNull() ?: return null
        if (seconds <= System.currentTimeMillis() / 1000) return null
        return NextAiring(
            episode = episode,
            unixTime = seconds,
            season = upcoming.optInt("season_number").takeIf { it > 0 }
        )
    }

    /** Only metadata: actual episode sources will be resolved by adapters later. */
    private suspend fun tvEpisodes(id: Int, seasonList: JSONArray?): List<Episode> {
        if (seasonList == null) return emptyList()
        val seasons = (0 until seasonList.length()).mapNotNull { i ->
            seasonList.optJSONObject(i)?.optInt("season_number", -1)?.takeIf { it >= 0 }
        }.distinct().sorted()
        return seasons.chunked(4).flatMap { batch ->
            coroutineScope {
                batch.map { number ->
                    async {
                        val season = getJson("/tv/$id/season/$number")
                            ?: return@async emptyList<Episode>()
                        val episodeRows = season.optJSONArray("episodes")
                            ?: return@async emptyList<Episode>()
                        (0 until episodeRows.length()).mapNotNull { i ->
                            val entry = episodeRows.optJSONObject(i)
                                ?: return@mapNotNull null
                            val episodeNo = entry.optInt("episode_number")
                                .takeIf { it > 0 } ?: return@mapNotNull null
                            val date = entry.optString("air_date")
                            val rating = entry.optDouble("vote_average", 0.0)
                                .takeIf { it > 0.1 && it <= 10.0 &&
                                    entry.optInt("vote_count") > 0 }
                            val text = entry.optString("overview")
                            newEpisode(
                                "$mainUrl/tv/$id/season/$number/episode/$episodeNo",
                                initializer = {
                                name = entry.optString("name").ifBlank { "Bölüm $episodeNo" }
                                this.season = number
                                this.episode = episodeNo
                                posterUrl = image(entry.optString("still_path"), "w500")
                                score = rating?.let { Score.from10(it) }
                                description = if (rating != null) {
                                    text + (if (text.isBlank()) "" else "\n\n") +
                                        "Bölüm puanı: TMDb " +
                                        String.format(Locale.ROOT, "%.1f", rating) + "/10"
                                } else text
                                runTime = entry.optInt("runtime").takeIf { it > 0 }
                                addDate(date)
                                },
                                fix = false
                            )
                        }
                    }
                }.awaitAll().flatten()
            }
        }.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    }

    override suspend fun load(url: String): LoadResponse {
        if (url == openMovieUrl) {
            return newMovieLoadResponse("Big Buck Bunny", url, TvType.Movie, openMovieData) {
                year = 2008
                posterUrl = openMoviePoster
                backgroundPosterUrl = openMovieBackdrop
                plot = "© 2008 Blender Foundation | www.bigbuckbunny.org. CC BY 3.0; filmin orijinal jeneriği korunur."
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
        val tmdbId = path.substringAfterLast('/').toIntOrNull()
            ?: error("Geçersiz TMDb kimliği")
        val append = if (isSeries) "aggregate_credits,recommendations,external_ids"
                     else "credits,recommendations,external_ids"
        val item = getJson("$path?append_to_response=$append")
            ?: error("EA-FB: TMDb anahtarı ayarlanmadı veya katalog erişilemiyor")
        val title = item.optString(if (isSeries) "name" else "title")
        val primaryOverview = item.optString("overview")
        val fallbackOverview = if (primaryOverview.isBlank()) {
            getJson(path, language = "en-US")?.optString("overview").orEmpty()
        } else ""
        val overview = primaryOverview.ifBlank { fallbackOverview }
            .ifBlank { "Açıklama bulunamadı" }
        val poster = image(item.optString("poster_path"), "w500")
        val backdrop = image(item.optString("backdrop_path"), "w1280")
        val media = if (isSeries) MediaKind.SERIES else MediaKind.MOVIE
        val yearValue = mediaYear(item, media)
        val people = cast(item, isSeries)
        val director = creators(item, isSeries)
        val combinedPlot = listOfNotNull(overview, director).joinToString("\n\n")
        val recs = recommendations(item, media, tmdbId)
        return if (isSeries) {
            val episodes = tvEpisodes(tmdbId, item.optJSONArray("seasons"))
            newTvSeriesLoadResponse(title, url, kind, episodes) {
                plot = combinedPlot
                year = yearValue
                posterUrl = poster
                backgroundPosterUrl = backdrop
                actors = people
                tags = genres(item)
                recommendations = recs
                nextAiring = nextEpisode(item)
                showStatus = when (item.optString("status")) {
                    "Ended", "Canceled" -> ShowStatus.Completed
                    "Returning Series", "In Production" -> ShowStatus.Ongoing
                    else -> null
                }
                duration = item.optJSONArray("episode_run_time")?.optInt(0)
                    ?.takeIf { it > 0 }
            }
        } else {
            newMovieLoadResponse(title, url, kind, "") {
                plot = combinedPlot
                year = yearValue
                posterUrl = poster
                backgroundPosterUrl = backdrop
                actors = people
                tags = genres(item)
                recommendations = recs
                duration = item.optInt("runtime").takeIf { it > 0 }
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
            // Two independently hosted formats are selectable in CloudStream's source list;
            // availability of either stream still depends on the viewer's network.
            callback(newExtractorLink(
                "Mux (HLS)", "Big Buck Bunny • Otomatik kalite", openMovieHls,
                type = ExtractorLinkType.M3U8
            ) {
                quality = 0
                referer = ""
            })
            callback(newExtractorLink(
                "Video Commander (MP4)", "Big Buck Bunny • MP4 alternatif", openMovieMp4,
                type = ExtractorLinkType.VIDEO
            ) {
                quality = 720
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
