package com.eafb

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.newEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale
import java.text.SimpleDateFormat
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
    private val mainPageDeduper = CatalogPageDeduper()
    // V40: Turkish episode-title enrichment is deliberately outside load().
    // The V39 detail response remains fast; short-season title batches run later.
    private val episodeTitleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Keep every category registered with CloudStream. The host may cache mainPage
    // requests at provider initialization, so removing a disabled category here
    // prevents it from reappearing when the viewer enables it later.
    // getMainPage applies the persisted switch on every request instead.
    override val mainPage
        get() = mainPageOf(
            "ea-fb-live" to "Canlı TV",
            *categories.map { "${it.id}|cfg=${EASettings.homeRevision()}" to it.title }.toTypedArray()
        )

    private fun demoMovie(): SearchResponse = newMovieSearchResponse(
        "Big Buck Bunny (Deneme)", openMovieUrl, TvType.Movie
    ) {
        year = 2008
        posterUrl = openMoviePoster
    }

    /** Repository-curated sources: "authorized" is a manual record, not proof of distribution rights. */
    private suspend fun liveChannels(): List<Channel> {
        // A remote JSON edit is not independent rights approval. The checked-in
        // public plugin has no compiled live-channel authorization.
        if (!LiveChannelDeliveryConfig.enabled) return emptyList()
        val json = try { JSONObject(app.get(channelsUrl).text) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return emptyList() }
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
        if (CatalogRelayPolicy.usableCached(cached, relayCheckedAt, now) != null &&
            now - relayCheckedAt < 3_600_000L) return cached!!
        val config = try { JSONObject(app.get(catalogConfigUrl).text) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                CatalogRelayPolicy.usableCached(cached, relayCheckedAt, now)?.let { return it }
                error("EA-FB katalog servisi ayarına ulaşılamıyor. İnternet bağlantısını kontrol et.")
            }
        val url = CatalogRelayPolicy.approved(
            config.optString("apiBaseUrl"), config.optString("status")
        ) ?: error("EA-FB katalog servisi henüz etkinleştirilmedi.")
        relayBase = url
        relayCheckedAt = now
        return url
    }

    private suspend fun getJson(path: String, page: Int? = null, language: String = "tr-TR"): JSONObject? {
        val relay = catalogRelay()
        val join = if ('?' in path) '&' else '?'
        val url = "$relay/v1$path${join}language=$language${if (page != null) "&page=$page" else ""}"
        // No TMDb credential or Authorization header ever reaches the client.
        return try { JSONObject(app.get(url).text) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
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

    private fun newItem(
        item: JSONObject,
        fallback: MediaKind,
        fallbackArtwork: String? = null
    ): SearchResponse? {
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
        val card = newMovieSearchResponse(title, "$mainUrl/$path/$id", if (kind == MediaKind.SERIES) TvType.TvSeries else TvType.Movie) {
            posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
            year = mediaYear(item, kind)
            // CloudStream renders one score badge per small poster when the
            // viewer has enabled "Show ratings" in their app preferences.
            // TMDb list results contain TMDb scores; no IMDb score is invented.
            score = ratingValue(item)?.let { Score.from10(it) }
        }
        return card
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
        val requestCategoryId = request.data.substringBefore("|cfg=")
        val category = categories.firstOrNull { it.id == requestCategoryId }
            ?: return newHomePageResponse(emptyList(), false)
        if (!EASettings.categoryEnabled(category.id)) {
            return newHomePageResponse(emptyList(), false)
        }
        val route = CatalogSortPolicy.route(
            category.tmdbPath ?: return newHomePageResponse(emptyList(), false),
            category.kind,
            EASettings.sortMode()
        )
        val sortMode = EASettings.sortMode()
        fun cards(response: JSONObject?): List<SearchResponse> {
            val rows = response?.optJSONArray("results") ?: return emptyList()
            // No blank posters or future premieres in the "En Yeni" mode,
            // including when a regional platform needs a popular fallback.
            return (0 until rows.length()).mapNotNull { i ->
                val item = rows.optJSONObject(i) ?: return@mapNotNull null
                if (sortMode == CatalogSortMode.NEWEST &&
                    category.tmdbPath?.startsWith("/discover/") == true &&
                    !CatalogReleasePolicy.released(item.optString(
                        if (category.kind == MediaKind.SERIES) "first_air_date" else "release_date"
                    ), System.currentTimeMillis())) return@mapNotNull null
                newItem(item, category.kind)
            }.distinctBy { it.url }
        }
        val response = getJson(route, page)
        var results = cards(response)
        var usedFallback = false
        var usedForeignCatalog = false
        var scannedExtraPages = false
        // TMDb may place unreleased titles across several newest pages. Scan
        // only when the first valid page yields no released artwork cards.
        // A scanned rail is deliberately non-paginated to avoid duplicate
        // cards on the host's subsequent page-2 request.
        val extraPages = CatalogPagePolicy.extraNewestPages(
            page, response?.optInt("total_pages", 0) ?: 0,
            results.isEmpty(), category.tmdbPath.startsWith("/discover/"),
            sortMode == CatalogSortMode.NEWEST,
            response?.optJSONArray("results") != null
        )
        for (extraPage in extraPages) {
                val extra = getJson(route, extraPage) ?: break
                scannedExtraPages = true
                results = (results + cards(extra)).distinctBy { it.url }
                if (results.isNotEmpty()) break
        }
        // Platform/genre feeds may contain only posterless entries on page 1.
        // Preserve the requested provider/genre and scan two further pages
        // before treating the rail as empty. Never invent a different catalog.
        if (results.isEmpty() && extraPages.isEmpty()) {
            val sparsePages = CatalogPagePolicy.extraSparseDiscoverPages(
                page, response?.optInt("total_pages", 0) ?: 0,
                true, category.tmdbPath.startsWith("/discover/"),
                response?.optJSONArray("results") != null
            )
            for (extraPage in sparsePages) {
                val extra = getJson(route, extraPage) ?: break
                scannedExtraPages = true
                results = (results + cards(extra)).distinctBy { it.url }
                if (results.isNotEmpty()) break
            }
        }
        // A sparse regional provider feed can be empty under date/vote sorting.
        // Preserve the platform rail with clearly labeled popular results rather
        // than silently presenting popular titles as "highest rated" or "newest".
        if (page == 1 && results.isEmpty() &&
            route != category.tmdbPath && category.tmdbPath.startsWith("/discover/")) {
            val fallback = getJson(
                CatalogSortPolicy.route(category.tmdbPath, category.kind, CatalogSortMode.POPULAR), page
            )
            results = cards(fallback)
            usedFallback = results.isNotEmpty()
        }
        // TMDb currently reports no TR subscription results for these four
        // provider rails. Show a clearly marked GB catalog when TR is empty;
        // never imply that these titles are available to stream in Türkiye.
        if (page == 1 && results.isEmpty() &&
            category.id in setOf("apple-movie", "apple-tv", "paramount-movie", "paramount-tv")) {
            val foreignPath = category.tmdbPath.replace("watch_region=TR", "watch_region=GB")
            val foreignRoute = CatalogSortPolicy.route(foreignPath, category.kind, sortMode)
            results = cards(getJson(foreignRoute, page))
            if (results.isEmpty() && foreignRoute != foreignPath) {
                results = cards(getJson(CatalogSortPolicy.route(
                    foreignPath, category.kind, CatalogSortMode.POPULAR), page))
            }
            usedForeignCatalog = results.isNotEmpty()
        }
        if (results.isEmpty()) return newHomePageResponse(emptyList(), false)
        val label = category.title + when {
            usedForeignCatalog -> " (GB kataloğu; Türkiye erişimi doğrulanmadı)"
            usedFallback -> " (Popüler alternatif)"
            else -> ""
        }
        val rawLength = response?.optJSONArray("results")?.length() ?: 0
        val hasNext = CatalogPagePolicy.allowNextPage(
            usedFallback || usedForeignCatalog, scannedExtraPages,
            page, rawLength, response?.optInt("total_pages", 0) ?: 0
        )
        // Trending ranks can move between separately cached TMDb pages, and some
        // hosts may ask for an already-appended page again. Keep feed-local URL
        // identity across pagination calls; page 1 resets state for normal refresh.
        val visibleResults = mainPageDeduper.filter(
            category.id + "|" + sortMode.name, page, results
        ) { it.url }
        return newHomePageResponse(
            if (visibleResults.isEmpty()) emptyList()
            else listOf(HomePageList(label, visibleResults, false)),
            hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val normalized = Identity.normalize(query)
        val demo = if (normalized.isNotEmpty() && Identity.normalize("Big Buck Bunny").contains(normalized))
            listOf(demoMovie()) else emptyList()
        val live = if (normalized.isEmpty()) emptyList() else liveChannels()
            .filter { Identity.normalize(it.name).contains(normalized) }.map(::liveSearch)
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
        val enriched = item.optJSONObject("ea_fb_ratings")
        val imdb = enriched?.takeIf {
            it.optString("source") == "OMDb API" &&
                item.optJSONObject("external_ids")?.optString("imdb_id")
                    ?.matches(Regex("tt[0-9]{7,10}")) == true
        }?.optDouble("imdb", 0.0)?.takeIf { it > 0.0 && it <= 10.0 }
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
    private data class FilmCollectionResult(
        val label: String?,
        val cards: List<SearchResponse>,
        val releaseDates: Map<Int, String>,
        val ratings: Map<Int, Double>
    )

    private suspend fun collectionMovies(
        movie: JSONObject,
        ownId: Int
    ): FilmCollectionResult {
        val collectionId = movie.optJSONObject("belongs_to_collection")
            ?.optInt("id")?.takeIf { it > 0 } ?: return FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
        val collection = getJson("/collection/$collectionId")
            ?: return FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
        val parts = collection.optJSONArray("parts")
            ?: return FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
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
            return FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
        }
        // TMDb collection parts can omit the selected film's artwork even
        // when its detail page has a valid poster. Reuse only that same film's
        // detail artwork; never misattribute another installment's poster.
        val selectedArtwork = CatalogCardPolicy.bestArtwork(
            movie.optString("poster_path"), movie.optString("backdrop_path")
        )
        val cards = ordered.mapNotNull { part ->
            val artwork = CatalogCardPolicy.collectionArtwork(
                part.optInt("id"), ownId,
                part.optString("poster_path"), part.optString("backdrop_path"),
                selectedArtwork
            )
            newItem(part, MediaKind.MOVIE, artwork)
        }
        if (cards.size < 2) return FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
        // Only label installments that actually have visible artwork cards.
        val visibleIds = cards.mapNotNull { it.url.substringAfterLast('/').toIntOrNull() }.toSet()
        if (ownId !in visibleIds) return FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
        val releaseDates = sortedParts.mapNotNull { part ->
            FilmCollectionPolicy.displayDate(part.releaseDate)?.let { part.id to it }
        }.toMap()
        val ratings = ordered.mapNotNull { part ->
            ratingValue(part)?.let { part.optInt("id") to it }
        }.toMap()
        return FilmCollectionResult(
            "Film Serisi: ${cards.size} film • vizyon sırası",
            cards,
            releaseDates,
            ratings
        )
    }

    private fun longTurkishDate(iso: String): String? = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso) ?: return null
        SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr", "TR")).format(parsed)
    } catch (_: Exception) { null }

    private fun upcomingEpisode(item: JSONObject): EpisodeAirPolicy.Airing? =
        item.optJSONObject("next_episode_to_air")
            ?.optString("air_date")
            ?.let { EpisodeAirPolicy.parse(it, System.currentTimeMillis()) }

    private fun nextEpisode(item: JSONObject, airing: EpisodeAirPolicy.Airing?): NextAiring? {
        airing ?: return null
        val next = item.optJSONObject("next_episode_to_air") ?: return null
        val episode = next.optInt("episode_number").takeIf { it > 0 } ?: return null
        val season = next.optInt("season_number").takeIf { it > 0 }
        return NextAiring(episode, airing.unixSeconds, season)
    }

    private fun genericEpisodeName(value: String, episodeNo: Int): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return true
        val escapedEpisode = Regex.escape(episodeNo.toString())
        return listOf(
            Regex("""(?i)^bölüm\s*$escapedEpisode$"""),
            Regex("""(?i)^$escapedEpisode\.?\s*bölüm$"""),
            Regex("""(?i)^episode\s*$escapedEpisode$"""),
            Regex("""(?i)^episode\s*#?\s*\d+\.$escapedEpisode$""")
        ).any { it.matches(normalized) }
    }

    private data class EpisodeTitleBatch(val season: Int, val episodes: List<Int>)

    /**
     * V42 title enrichment stays bounded per detail open, but a long season is
     * split into requests instead of being discarded just because it has >12 episodes.
     */
    private fun titleBatches(episodes: List<Episode>): List<EpisodeTitleBatch> {
        var remaining = 48
        val batches = mutableListOf<EpisodeTitleBatch>()
        episodes.groupBy { it.season }.toSortedMap(compareBy<Int?> { it ?: Int.MAX_VALUE })
            .forEach { (season, rows) ->
                if (remaining <= 0) return@forEach
                val seasonNo = season?.takeIf { it > 0 } ?: return@forEach
                val numbers = rows.mapNotNull { it.episode?.takeIf { number -> number > 0 } }
                    .distinct().sorted()
                for (chunk in numbers.chunked(10)) {
                    if (remaining <= 0) break
                    val bounded = chunk.take(remaining)
                    if (bounded.isNotEmpty()) {
                        batches += EpisodeTitleBatch(seasonNo, bounded)
                        remaining -= bounded.size
                    }
                }
            }
        return batches
    }

    private suspend fun fetchTurkishEpisodeTitles(
        id: Int, batch: EpisodeTitleBatch, sourceLanguage: String
    ): List<EpisodeTitleStyle.TitleEpisode> {
        val query = batch.episodes.joinToString(",")
        val safeSource = sourceLanguage.lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("^[a-z]{2,3}$")) } ?: "en"
        val response = getJson(
            "/tv/$id/season/${batch.season}/episode-titles/$safeSource?episodes=$query"
        ) ?: return emptyList()
        val titles = response.optJSONObject("titles") ?: return emptyList()
        val originals = response.optJSONObject("originals")
        return batch.episodes.mapNotNull { episodeNo ->
            val name = titles.optString(episodeNo.toString()).trim()
            name.takeUnless { genericEpisodeName(it, episodeNo) }?.let {
                val original = originals?.optString(episodeNo.toString())?.trim()
                    ?.takeIf { value -> value.isNotBlank() && value != name }
                EpisodeTitleStyle.TitleEpisode(batch.season, episodeNo, it, original)
            }
        }
    }

    private fun scheduleTurkishEpisodeTitles(
        id: Int, seriesUrl: String, episodes: List<Episode>, sourceLanguage: String
    ) {
        val batches = titleBatches(episodes)
        if (batches.isEmpty()) return
        episodeTitleScope.launch {
            val resolved = mutableListOf<EpisodeTitleStyle.TitleEpisode>()
            for (wave in batches.chunked(2)) {
                resolved += coroutineScope {
                    wave.map { batch ->
                        async { fetchTurkishEpisodeTitles(id, batch, sourceLanguage) }
                    }.awaitAll().flatten()
                }
            }
            if (resolved.isEmpty()) return@launch
            EpisodeTitleStyle.publish(seriesUrl, resolved)
            val translated = resolved.associateBy { it.season to it.episode }
            EpisodeUpcomingStyle.publish(seriesUrl, episodes.mapNotNull { ep ->
                ep.date?.takeIf { it > System.currentTimeMillis() }?.let { date ->
                    val season = ep.season
                    val episode = ep.episode
                    if (season != null && episode != null) {
                        EpisodeUpcomingStyle.FutureEpisode(
                            season, episode, date, translated[season to episode]?.name ?: ep.name
                        )
                    } else null
                }
            })
        }
    }

    /** Only metadata: actual episode sources will be resolved by adapters later. */
    private suspend fun tvEpisodes(id: Int, seasonList: JSONArray?, fallbackBackdrop: String?): List<Episode> {
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
                        // V35: avoid the second season request when Turkish already has
                        // every field we use. Large libraries should pay for EN only when at
                        // least one episode actually needs a title/overview/still fallback.
                        val needsEnglish = (0 until episodeRows.length()).any { idx ->
                            val row = episodeRows.optJSONObject(idx) ?: return@any false
                            val episodeNo = row.optInt("episode_number").takeIf { it > 0 }
                                ?: return@any false
                            genericEpisodeName(row.optString("name"), episodeNo) ||
                                row.optString("overview").isBlank() ||
                                row.optString("still_path").isBlank()
                        }
                        val englishRows = if (needsEnglish) {
                            getJson("/tv/$id/season/$number", language = "en-US")
                                ?.optJSONArray("episodes")
                        } else null
                        val englishByEpisode = (0 until (englishRows?.length() ?: 0)).mapNotNull { idx ->
                            englishRows?.optJSONObject(idx)?.let { row ->
                                row.optInt("episode_number").takeIf { it > 0 }?.let { it to row }
                            }
                        }.toMap()
                        (0 until episodeRows.length()).mapNotNull { i ->
                            val entry = episodeRows.optJSONObject(i)
                                ?: return@mapNotNull null
                            val episodeNo = entry.optInt("episode_number")
                                .takeIf { it > 0 } ?: return@mapNotNull null
                            // Identity is season + episode, never episode number alone.
                            // Also require the EN fallback row to belong to this same season.
                            val english = englishByEpisode[episodeNo]?.takeIf { row ->
                                row.optInt("season_number", number) == number &&
                                    row.optInt("episode_number") == episodeNo
                            }
                            val date = entry.optString("air_date").ifBlank { english?.optString("air_date").orEmpty() }
                            val rating = entry.optDouble("vote_average", 0.0)
                                .takeIf { it > 0.1 && it <= 10.0 &&
                                    entry.optInt("vote_count") > 0 }
                            val text = entry.optString("overview").ifBlank { english?.optString("overview").orEmpty() }
                            val localizedName = entry.optString("name")
                            val englishName = english?.optString("name").orEmpty()
                            // V35: never block a detail page on one HTTP request per episode.
                            // TMDb's season payload is the fast Turkish source; if its title is
                            // only a generic "Bölüm N", use the already-fetched EN season row.
                            // The exact translations relay stays available server-side for a
                            // future lazy/cache-backed enrichment path, but is not in load().
                            val episodeName = when {
                                !genericEpisodeName(localizedName, episodeNo) -> localizedName
                                !genericEpisodeName(englishName, episodeNo) -> englishName
                                else -> ""
                            }
                            val still = entry.optString("still_path").ifBlank { english?.optString("still_path").orEmpty() }
                            newEpisode(
                                "$mainUrl/tv/$id/season/$number/episode/$episodeNo",
                                initializer = {
                                this.name = episodeName.ifBlank { null }
                                this.season = number
                                this.episode = episodeNo
                                posterUrl = image(still, "w500") ?: fallbackBackdrop
                                val future = date.isNotBlank() && try {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date)?.time?.let { it > System.currentTimeMillis() } == true
                                } catch (_: Exception) { false }
                                score = if (future) null else rating?.let { Score.from10(it) }
                                description = if (future) {
                                    text.ifBlank { "Bölüm özeti henüz yayınlanmadı." }
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
        val genreLabels = genres(item)
        // TV detail enhancement: ratings + genres move beside duration when the
        // host exposes its stable result_meta_duration row. Native tags remain
        // populated as a compatibility fallback and are hidden only on success.
        val nextAirDateLabel = if (isSeries) item.optJSONObject("next_episode_to_air")?.let { next ->
            val date = next.optString("air_date").takeIf { it.isNotBlank() }?.let(::longTurkishDate)
            val season = next.optInt("season_number").takeIf { it > 0 }
            val episode = next.optInt("episode_number").takeIf { it > 0 }
            date?.let { d ->
                val number = listOfNotNull(season?.let { "S$it" }, episode?.let { "B$it" }).joinToString(" ")
                "Sonraki bölüm" + (if (number.isNotEmpty()) " ($number)" else "") + ": $d"
            }
        } else null
        DetailMetaRow.publish(url, imdbRating, tmdbRating, genreLabels, nextAirDateLabel)
        // CloudStream's unlabeled native hero score duplicates these source-labeled
        // chips, so details deliberately show the chips only (no native score).
        val collection = if (!isSeries) {
            collectionMovies(item, tmdbId)
        } else FilmCollectionResult(null, emptyList(), emptyMap(), emptyMap())
        val collectionLabel = collection.label
        val collectionCards = collection.cards
        FilmSeriesRail.publish(url, collectionCards.map { card ->
            val id = card.url.substringAfterLast('/').toIntOrNull()
            FilmSeriesRail.Card(
                card.name,
                card.url,
                card.posterUrl,
                id?.let(collection.releaseDates::get),
                id?.let(collection.ratings::get)
            )
        })
        val nextAir = if (isSeries) upcomingEpisode(item) else null
        // Put a distant premiere date before the plot where it cannot be lost
        // beneath long descriptions; native nextAiring handles near-term dates.
        // Film collection text is a short cue, never a long duplicate title list.
        val seriesNote = collectionLabel
        // Some TV layouts hide detail tags below the fold. Put the clearly
        // sourced ratings at the top of the visible description as well.
        // Never substitute TMDb's vote_average for an unavailable IMDb score.
        val combinedPlot = listOfNotNull(
            seriesNote, overview, director
        ).joinToString("\n\n")
        val recs = recommendations(item, media, tmdbId)
        // The film series has its own row. The host's recommendations contain
        // only unrelated suggestions, never a second copy of the series.
        val collectionUrls = collectionCards.map { it.url }.toSet()
        val movieRelated = recs.filterNot { it.url in collectionUrls }
            .distinctBy { it.url }
        return if (isSeries) {
            val episodes = tvEpisodes(tmdbId, item.optJSONArray("seasons"), backdrop)
            scheduleTurkishEpisodeTitles(
                tmdbId, url, episodes, item.optString("original_language")
            )
            // V38: the initial metadata publish happens before season fan-out.
            // Republish after that bounded work so first-open detail rows are
            // rendered against the host views that are about to receive the response.
            DetailMetaRow.publish(url, imdbRating, tmdbRating, genreLabels, nextAirDateLabel)
            EpisodeUpcomingStyle.publish(url, episodes.mapNotNull { ep ->
                ep.date?.takeIf { it > System.currentTimeMillis() }?.let { date ->
                    val season = ep.season
                    val episode = ep.episode
                    if (season != null && episode != null) EpisodeUpcomingStyle.FutureEpisode(season, episode, date, ep.name) else null
                }
            })
            newTvSeriesLoadResponse(title, url, kind, episodes) {
                plot = combinedPlot
                year = yearValue
                score = tmdbRating?.let { Score.from10(it) }
                // IMDb/TMDb appear exactly once in explicit tags.
                posterUrl = poster
                backgroundPosterUrl = backdrop
                actors = people
                tags = ratingBadges + genreLabels
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
                tags = ratingBadges + genreLabels
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
