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

    /** Public client: the separate metadata Worker holds the only TMDb credential. */
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
    // Reflect locally stored category switches when CloudStream reopens EA-FB.
    override val mainPage
        get() = mainPageOf(
            "ea-fb-open" to "Açık Lisanslı Deneme Filmi",
            "ea-fb-live" to "Canlı TV",
            *categories.filter { EASettings.categoryEnabled(it.id) }
                .map { it.id to it.title }.toTypedArray()
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

    // This public config contains only a relay URL. The TMDb token is server-side.
    private val catalogConfigUrl = "https://raw.githubusercontent.com/eaatabay/EA-FB/main/config/backend.json"
    @Volatile private var relayBase: String? = null
    @Volatile private var relayCheckedAt: Long = 0L

    private suspend fun catalogRelay(): String {
        val now = System.currentTimeMillis()
        val cached = relayBase
        if (!cached.isNullOrBlank() && now - relayCheckedAt < 3_600_000L) return cached
        val config = runCatching { JSONObject(app.get(catalogConfigUrl).text) }.getOrElse {
            if (!cached.isNullOrBlank()) return cached
            error("EA-FB katalog servisi ayarına ulaşılamıyor. İnternet bağlantısını kontrol et.")
        }
        val url = config.optString("apiBaseUrl").trim().trimEnd('/')
        if (!url.startsWith("https://") || url.length > 200 ||
            url.contains("@") || url.contains("?") || url.contains("#") || url.contains(" ")) {
            error("EA-FB katalog servisi henüz etkinleştirilmedi.")
        }
        relayBase = url
        relayCheckedAt = now
        return url
    }

    private suspend fun getJson(path: String, page: Int? = null, language: String = "tr-TR"): JSONObject? {
        val relay = catalogRelay()
        val join = if ('?' in path) '&' else '?'
        val url = "$relay/v1$path${join}language=$language${if (page != null) "&page=$page" else ""}"
        // No TMDb credential or Authorization header ever reaches the client.
        return runCatching { JSONObject(app.get(url).text) }.getOrNull()
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

    private fun newItem(item: JSONObject, fallback: MediaKind, fallbackArtwork: String? = null): SearchResponse? {
        // Mixed TMDb feeds also include people; never render actors as movie cards.
        val type = item.optString("media_type")
        if (type.isNotBlank() && type != "movie" && type != "tv") return null
        val id = item.optInt("id").takeIf { it > 0 } ?: return null
        val kind = mediaKind(item, fallback)
        val title = item.optString(if (kind == MediaKind.SERIES) "name" else "title").ifBlank { return null }
        val path = if (kind == MediaKind.SERIES) "tv" else "movie"
        val poster = CatalogCardPolicy.bestArtwork(
            item.optString("poster_path"), item.optString("backdrop_path")
        ) ?: CatalogCardPolicy.bestArtwork(fallbackArtwork, null) ?: return null
        return newMovieSearchResponse(title, "$mainUrl/$path/$id", if (kind == MediaKind.SERIES) TvType.TvSeries else TvType.Movie) {
            posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
            year = mediaYear(item, kind)
            // CloudStream renders one score badge per small poster when the
            // viewer has enabled "Show ratings" in their app preferences.
            // TMDb list results contain TMDb scores; no IMDb score is invented.
            score = ratingValue(item)?.let { Score.from10(it) }
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
        if (!EASettings.categoryEnabled(category.id)) {
            return newHomePageResponse(emptyList(), false)
        }
        val route = CatalogSortPolicy.route(
            category.tmdbPath ?: return newHomePageResponse(emptyList(), false),
            category.kind,
            EASettings.sortMode()
        )
        val response = getJson(route, page)
            ?: return newHomePageResponse(emptyList(), false)
        val raw = response.optJSONArray("results")
            ?: return newHomePageResponse(emptyList(), false)
        // TMDb occasionally returns titles without poster art. On TV these
        // appear as blank, hard-to-navigate cards, especially in lower rows.
        val results = (0 until raw.length()).mapNotNull { i ->
            raw.optJSONObject(i)?.let { newItem(it, category.kind) }
        }.distinctBy { it.url }
        // Never create a visible empty rail or request nonexistent pages.
        if (results.isEmpty()) return newHomePageResponse(emptyList(), false)
        return newHomePageResponse(
            listOf(HomePageList(category.title, results, false)),
            CatalogCardPolicy.hasNext(page, raw.length(), response.optInt("total_pages", 0))
        )
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

    /** Explicit source labels: TMDb ratings are not IMDb ratings. */
    private fun ratingValue(item: JSONObject?): Double? {
        if (item == null || item.optInt("vote_count", 0) <= 0) return null
        return item.optDouble("vote_average", 0.0).takeIf { it > 0.0 && it <= 10.0 }
    }

    private fun scoreText(rating: Double): String =
        String.format(Locale.ROOT, "%.1f", rating)

    private fun titleRatings(item: JSONObject): Pair<Double?, Double?> {
        val tmdb = ratingValue(item)
        // Only the server may attach independent IMDb data. TMDb external_ids
        // contains an IMDb ID, NOT an IMDb rating.
        val imdb = item.optJSONObject("ea_fb_ratings")
            ?.optDouble("imdb", 0.0)
            ?.takeIf { it > 0.0 && it <= 10.0 }
        return Pair(imdb, tmdb)
    }

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

    /**
     * Official TMDb film collection, independent of TMDb recommendations.
     * A collection includes connected installments only; it must not silently
     * mix unrelated Spider-Man reboot timelines or similarly named movies.
     */
    private suspend fun collectionMovies(
        movie: JSONObject,
        ownId: Int
    ): Pair<String?, List<SearchResponse>> {
        val collectionId = movie.optJSONObject("belongs_to_collection")
            ?.optInt("id")?.takeIf { it > 0 } ?: return Pair(null, emptyList())
        val collection = getJson("/collection/$collectionId")
            ?: return Pair(null, emptyList())
        val parts = collection.optJSONArray("parts")
            ?: return Pair(null, emptyList())
        val byId = linkedMapOf<Int, JSONObject>()
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            val id = part.optInt("id")
            if (id > 0 && part.optString("title").isNotBlank() && !byId.containsKey(id)) {
                byId[id] = part
            }
        }
        val sortedParts = FilmCollectionPolicy.chronological(
            byId.values.map { part ->
                FilmCollectionPart(
                    part.optInt("id"), part.optString("title"),
                    part.optString("release_date")
                )
            },
            ownId
        )
        val ordered = sortedParts.mapNotNull { byId[it.id] }
        // One-item "collections" do not make a franchise strip.
        if (ordered.size < 2 || ordered.none { it.optInt("id") == ownId }) {
            return Pair(null, emptyList())
        }
        // TMDb collection parts can omit the selected film's artwork even
        // when its detail page has a valid poster. Reuse only that same film's
        // detail artwork; never misattribute another installment's poster.
        val selectedArtwork = CatalogCardPolicy.bestArtwork(
            movie.optString("poster_path"), movie.optString("backdrop_path")
        )
        val cards = ordered.mapNotNull { part ->
            newItem(part, MediaKind.MOVIE,
                if (part.optInt("id") == ownId) selectedArtwork else null)
        }
        if (cards.size < 2) return Pair(null, emptyList())
        val labels = ordered.take(12).joinToString(" • ") { part ->
            val title = part.optString("title")
            val year = mediaYear(part, MediaKind.MOVIE)
            if (year != null) "$title ($year)" else title
        }
        return Pair("Serinin Filmleri (vizyon tarihine göre): $labels", cards)
    }

    private fun upcomingEpisode(item: JSONObject): EpisodeAirPolicy.Airing? =
        item.optJSONObject("next_episode_to_air")
            ?.optString("air_date")
            ?.let { EpisodeAirPolicy.parse(it, System.currentTimeMillis()) }

    private fun nextEpisode(item: JSONObject, airing: EpisodeAirPolicy.Airing?): NextAiring? {
        if (airing?.showNativeCountdown != true) return null
        val upcoming = item.optJSONObject("next_episode_to_air") ?: return null
        val episode = upcoming.optInt("episode_number").takeIf { it > 0 } ?: return null
        return NextAiring(
            episode = episode,
            unixTime = airing.unixSeconds,
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
            ?: error("EA-FB katalog servisine şu anda erişilemiyor")
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
        val (imdbRating, tmdbRating) = titleRatings(item)
        val ratingBadges = listOfNotNull(
            imdbRating?.let { "IMDb " + scoreText(it) + "/10" },
            tmdbRating?.let { "TMDb " + scoreText(it) + "/10" }
        )
        // CloudStream's unlabeled native hero score duplicates these source-labeled
        // chips, so details deliberately show the chips only (no native score).
        val (collectionLabel, collectionCards) = if (!isSeries) {
            collectionMovies(item, tmdbId)
        } else Pair<String?, List<SearchResponse>>(null, emptyList())
        val nextAir = if (isSeries) upcomingEpisode(item) else null
        val upcomingLabel = if (nextAir != null && !nextAir.showNativeCountdown) {
            val next = item.optJSONObject("next_episode_to_air")
            val season = next?.optInt("season_number")?.takeIf { it > 0 }
            val episode = next?.optInt("episode_number")?.takeIf { it > 0 }
            val number = listOfNotNull(
                season?.let { "S$it" }, episode?.let { "B$it" }
            ).joinToString(" ")
            "Sonraki bölüm" + (if (number.isNotEmpty()) " ($number)" else "") +
                ": " + nextAir.dateLabel
        } else null
        // Put a distant premiere date before the plot where it cannot be lost
        // beneath long descriptions; native nextAiring handles near-term dates.
        // Film collection text is a short cue, never a long duplicate title list.
        val seriesNote = if (collectionCards.size >= 2)
            "Seri: " + collectionCards.size + " film; vizyon sırasıyla Önerilenler'in başında."
        else null
        val combinedPlot = listOfNotNull(
            upcomingLabel, overview, director, seriesNote
        ).joinToString("\n\n")
        val recs = recommendations(item, media, tmdbId)
        // The stock CloudStream LoadResponse exposes one recommendation rail,
        // not a separate branded "Serinin Filmleri" section. Put official
        // collection titles first, avoid duplicates, keep normal recs after.
        val movieRelated = (collectionCards + recs).distinctBy { it.url }.take(32)
        return if (isSeries) {
            val episodes = tvEpisodes(tmdbId, item.optJSONArray("seasons"))
            newTvSeriesLoadResponse(title, url, kind, episodes) {
                plot = combinedPlot
                year = yearValue
                // IMDb/TMDb appear exactly once in explicit tags.
                posterUrl = poster
                backgroundPosterUrl = backdrop
                actors = people
                tags = ratingBadges + genres(item)
                recommendations = recs
                nextAiring = nextEpisode(item, nextAir)
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
                // IMDb/TMDb appear exactly once in explicit tags.
                posterUrl = poster
                backgroundPosterUrl = backdrop
                actors = people
                tags = ratingBadges + genres(item)
                recommendations = movieRelated
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
