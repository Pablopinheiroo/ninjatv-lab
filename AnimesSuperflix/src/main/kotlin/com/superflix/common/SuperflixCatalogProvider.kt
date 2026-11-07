package com.superflix.common

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Base comum dos quatro catálogos SuperFlix.
 * A lista de IDs vem do endpoint público /lista. Metadados ausentes são
 * complementados pelas páginas públicas do TMDB. A reprodução aponta para
 * o player HTML único hospedado no GitHub.
 */
abstract class SuperflixCatalogProvider(
    providerName: String,
    private val category: String,
    private val mediaType: TvType,
    private val movie: Boolean,
    private val anime: Boolean,
    private val genrePages: List<Pair<String, String>>
) : MainAPI() {

    override var mainUrl = API_BASE
    override var name = providerName
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = true
    override val supportedTypes = setOf(mediaType)

    private data class CatalogItem(
        val id: String,
        val title: String? = null,
        val poster: String? = null,
        val overview: String? = null,
        val year: Int? = null,
        val genres: List<String> = emptyList(),
        val mediaKind: String? = null
    )

    private data class MediaMetadata(
        val title: String,
        val poster: String?,
        val overview: String?,
        val year: Int?,
        val genres: List<String>
    )

    private val catalogCache = mutableMapOf<String, List<CatalogItem>>()
    private val metadataCache = mutableMapOf<String, MediaMetadata>()
    private val idRegex = Regex("^[0-9]{1,12}$")
    private val yearRegex = Regex("\\b(19|20)\\d{2}\\b")

    override val mainPage = mainPageOf(
        *(
            listOf("all" to "Catálogo completo") +
                genrePages.map { ("genre|" + it.first) to it.second }
        ).toTypedArray()
    )

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun firstValue(obj: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            val raw = obj.opt(key) ?: continue
            if (raw is JSONObject || raw is JSONArray) continue
            val value = clean(raw.toString())
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun posterUrl(value: String?): String? {
        val poster = clean(value) ?: return null
        return when {
            poster.startsWith("https://") || poster.startsWith("http://") -> poster
            poster.startsWith("/") -> "https://image.tmdb.org/t/p/w500" + poster
            else -> poster
        }
    }

    private fun readItem(obj: JSONObject): CatalogItem? {
        val id = firstValue(
            obj,
            listOf("tmdb_id", "tmdbId", "id_tmdb", "tmdb", "movie_id", "tv_id", "content_id", "id")
        ) ?: return null
        if (!idRegex.matches(id)) return null

        val yearText = firstValue(obj, listOf("year", "release_year", "release_date", "first_air_date"))
        val year = yearText?.let { yearRegex.find(it)?.value?.toIntOrNull() }
        val genresArray = obj.optJSONArray("genres") ?: obj.optJSONArray("genre")
        val genres = if (genresArray == null) emptyList() else
            (0 until genresArray.length()).mapNotNull { index ->
                val value = genresArray.opt(index)
                if (value is JSONObject) firstValue(value, listOf("name", "title"))
                else clean(value?.toString())
            }.distinct()

        return CatalogItem(
            id = id,
            title = firstValue(obj, listOf("title", "name", "original_title", "original_name")),
            poster = posterUrl(firstValue(obj, listOf("poster_url", "poster", "poster_path", "image", "cover", "thumbnail"))),
            overview = firstValue(obj, listOf("overview", "description", "synopsis", "plot")),
            year = year,
            genres = genres,
            mediaKind = firstValue(obj, listOf("media_type", "content_type", "category", "type"))
        )
    }

    private fun parseCatalog(jsonText: String): List<CatalogItem> {
        val output = linkedMapOf<String, CatalogItem>()

        fun addId(raw: String) {
            val id = raw.trim()
            if (idRegex.matches(id) && !output.containsKey(id)) {
                output[id] = CatalogItem(id = id)
            }
        }

        fun visit(value: Any?) {
            when (value) {
                is JSONArray -> {
                    for (index in 0 until value.length()) visit(value.opt(index))
                }
                is JSONObject -> {
                    val item = readItem(value)
                    if (item != null) {
                        output[item.id] = item
                    } else {
                        val containers = listOf(
                            "data", "results", "items", "ids", "list", "catalog",
                            "movies", "series", "animes", "doramas", "contents"
                        )
                        var visited = false
                        for (key in containers) {
                            if (value.has(key)) {
                                visit(value.opt(key))
                                visited = true
                            }
                        }
                        if (!visited) {
                            val keys = value.keys()
                            while (keys.hasNext()) {
                                val child = value.opt(keys.next())
                                if (child is JSONArray || child is JSONObject || child is String || child is Number) {
                                    visit(child)
                                }
                            }
                        }
                    }
                }
                is Number -> addId(value.toLong().toString())
                is String -> addId(value)
            }
        }

        try {
            val trimmed = jsonText.trim()
            if (trimmed.startsWith("[")) visit(JSONArray(trimmed))
            else if (trimmed.startsWith("{")) visit(JSONObject(trimmed))
            else throw ErrorLoadingException("A API SuperFlix não retornou JSON.")
        } catch (error: ErrorLoadingException) {
            throw error
        } catch (error: Exception) {
            throw ErrorLoadingException("Não foi possível interpretar a lista da SuperFlix.")
        }

        return output.values.toList()
    }

    private fun categoryMatches(item: CatalogItem): Boolean {
        val kind = item.mediaKind?.lowercase() ?: return true
        val isMovieKind = kind == "filme" || kind == "movie" || kind == "movies"
        val isAnimeKind = kind == "anime" || kind == "animes"
        val isDoramaKind = kind == "dorama" || kind == "doramas"
        val isSeriesKind = kind == "serie" || kind == "series" || kind == "tv"
        return when {
            movie -> isMovieKind || (!isAnimeKind && !isDoramaKind && !isSeriesKind)
            anime -> isAnimeKind || isSeriesKind || (!isMovieKind && !isDoramaKind)
            category == "dorama" -> isDoramaKind || isSeriesKind || (!isMovieKind && !isAnimeKind)
            else -> isSeriesKind || (!isMovieKind && !isAnimeKind && !isDoramaKind)
        }
    }

    private suspend fun fetchCatalog(genre: String? = null): List<CatalogItem> {
        val cacheKey = category + "|" + (genre ?: "all")
        catalogCache[cacheKey]?.let { return it }

        val url = API_BASE + "/lista?category=" + encode(category) +
            "&type=tmdb&format=json&order=desc" +
            if (genre.isNullOrBlank()) "" else "&genero=" + encode(genre)

        val parsed = parseCatalog(app.get(url).text)
            .filter { categoryMatches(it) }
            .distinctBy { it.id }

        if (parsed.isEmpty()) {
            throw ErrorLoadingException("A lista de " + name + " veio vazia da SuperFlixAPI.")
        }
        catalogCache[cacheKey] = parsed
        return parsed
    }

    private suspend fun fetchSearchCatalog(query: String): List<CatalogItem> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()

        val urls = listOf(
            API_BASE + "/lista?category=pesquisa&q=" + encode(term) + "&type=tmdb&format=json",
            API_BASE + "/lista?category=" + encode(category) + "&q=" + encode(term) +
                "&type=tmdb&format=json"
        )

        for (url in urls) {
            try {
                val parsed = parseCatalog(app.get(url).text)
                    .filter { categoryMatches(it) }
                    .distinctBy { it.id }
                if (parsed.isNotEmpty()) return parsed
            } catch (_: Exception) {
                // Tenta o formato de pesquisa alternativo documentado pela API.
            }
        }
        return emptyList()
    }

    private fun tmdbPage(id: String): String =
        TMDB_WEB + "/" + (if (movie) "movie" else "tv") + "/" + id + "?language=pt-BR"

    private suspend fun metadata(item: CatalogItem): MediaMetadata {
        metadataCache[item.id]?.let { return it }

        var title = item.title
        var poster = item.poster
        var overview = item.overview
        var year = item.year
        var genres = item.genres

        if (title.isNullOrBlank() || poster.isNullOrBlank() || overview.isNullOrBlank() || year == null || genres.isEmpty()) {
            try {
                val pageUrl = tmdbPage(item.id)
                val document = Jsoup.parse(app.get(pageUrl).text, pageUrl)
                val htmlTitle = clean(document.selectFirst("meta[property=og:title]")?.attr("content"))
                    ?: clean(document.selectFirst("title")?.text()?.substringBefore(" —"))
                title = title ?: htmlTitle?.replace(Regex("\\s*\\(\\d{4}\\)$"), "")
                poster = poster ?: posterUrl(document.selectFirst("meta[property=og:image]")?.attr("content"))
                overview = overview ?: clean(document.selectFirst("meta[property=og:description]")?.attr("content"))
                year = year ?: yearRegex.find(document.text())?.value?.toIntOrNull()
                if (genres.isEmpty()) {
                    genres = document.select("span.genres a, .genres a")
                        .mapNotNull { clean(it.text()) }
                        .distinct()
                }
            } catch (_: Exception) {
                // Mantém os metadados presentes na própria resposta da lista.
            }
        }

        val result = MediaMetadata(
            title = clean(title) ?: ("TMDB " + item.id),
            poster = posterUrl(poster),
            overview = clean(overview),
            year = year,
            genres = genres
        )
        metadataCache[item.id] = result
        return result
    }

    private suspend fun toSearchResponse(item: CatalogItem): SearchResponse {
        val meta = metadata(item)
        val detailUrl = tmdbPage(item.id)

        return when {
            movie -> newMovieSearchResponse(meta.title, detailUrl, TvType.Movie) {
                this.posterUrl = meta.poster
                this.year = meta.year
            }
            anime -> newAnimeSearchResponse(meta.title, detailUrl, TvType.Anime) {
                this.posterUrl = meta.poster
                this.year = meta.year
            }
            else -> newTvSeriesSearchResponse(meta.title, detailUrl, TvType.TvSeries) {
                this.posterUrl = meta.poster
                this.year = meta.year
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val genre = request.data.takeIf { it.startsWith("genre|") }?.substringAfter("genre|")
        val allItems = fetchCatalog(genre)
        val pageSize = 20
        val start = ((page - 1).coerceAtLeast(0)) * pageSize
        val selected = allItems.drop(start).take(pageSize)
        val items = selected.map { toSearchResponse(it) }
        val hasNext = start + selected.size < allItems.size
        return newHomePageResponse(request.name, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val found = fetchSearchCatalog(query)
        return found.take(40).map { toSearchResponse(it) }
    }

    private fun idFromUrl(url: String): String? {
        val match = Regex("(?:movie|tv)/([0-9]{1,12})").find(url)
        return match?.groupValues?.getOrNull(1)
            ?: url.trim().takeIf { idRegex.matches(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = idFromUrl(url) ?: throw ErrorLoadingException("ID TMDB inválido.")
        val meta = metadata(CatalogItem(id = id))
        val tags = meta.genres

        if (movie) {
            return newMovieLoadResponse(meta.title, url, TvType.Movie, id) {
                this.posterUrl = meta.poster
                this.plot = meta.overview
                this.year = meta.year
                this.tags = tags
            }
        }

        if (anime) {
            return newAnimeLoadResponse(meta.title, url, TvType.Anime) {
                this.posterUrl = meta.poster
                this.plot = meta.overview
                this.year = meta.year
                this.tags = tags
                this.episodes = mutableMapOf(
                    DubStatus.Subbed to listOf(
                        newEpisode(id) {
                            this.name = "Abrir player e episódios"
                            this.season = 1
                            this.episode = 1
                            this.description = "A lista de temporadas e episódios é exibida no player SuperFlix."
                        }
                    )
                )
            }
        }

        val episode = newEpisode(id) {
            this.name = "Abrir player e episódios"
            this.season = 1
            this.episode = 1
            this.description = "A lista de temporadas e episódios é exibida no player SuperFlix."
        }
        return newTvSeriesLoadResponse(meta.title, url, TvType.TvSeries, listOf(episode)) {
            this.posterUrl = meta.poster
            this.plot = meta.overview
            this.year = meta.year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean {
        val id = data.trim().takeIf { idRegex.matches(it) } ?: return false
        val mediaKind = if (movie) "filme" else "serie"
        val playerUrl = PLAYER_TEMPLATE + "?type=" + mediaKind + "&tmdb=" + id

        callback(
            newExtractorLink(
                source = "SuperFlix",
                name = "Reproduzir",
                url = playerUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                referer = API_BASE + "/"
                quality = Qualities.P1080.value
            }
        )
        return true
    }

    companion object {
        private const val API_BASE = "https://superflixapi.boats"
        private const val TMDB_WEB = "https://www.themoviedb.org"
        private const val PLAYER_TEMPLATE =
            "https://raw.githubusercontent.com/Pablopinheiroo/ninjatv-lab/lab/turnstile-workers/shared/superflix-player.html"
    }
}
