package com.superflix.animes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Catálogo de animes com dados públicos do Jikan API.
 * Somente metadados; não oferece fontes de vídeo.
 */
class AnimesSuperflixProvider : MainAPI() {
    override var mainUrl = "https://api.jikan.moe"
    override var name = "Animes Superflix"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "top" to "Mais populares",
        "airing" to "Em exibição",
        "upcoming" to "Em breve"
    )

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun animeTitle(item: JSONObject): String? =
        clean(item.optString("title_english"))
            ?: clean(item.optString("title"))
            ?: clean(item.optString("title_japanese"))

    private fun poster(item: JSONObject): String? =
        item.optJSONObject("images")
            ?.optJSONObject("jpg")
            ?.let { clean(it.optString("large_image_url")) ?: clean(it.optString("image_url")) }

    private fun year(item: JSONObject): Int? {
        val direct = item.optInt("year", 0).takeIf { it > 0 }
        if (direct != null) return direct
        val aired = item.optJSONObject("aired")?.optString("from")
        return aired?.take(4)?.toIntOrNull()
    }

    private fun genres(item: JSONObject): List<String> {
        val array = item.optJSONArray("genres") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            clean(array.optJSONObject(index)?.optString("name"))
        }.distinct()
    }

    private fun toSearchResponse(item: JSONObject): SearchResponse? {
        val id = item.optInt("mal_id", 0)
        if (id <= 0) return null
        val title = animeTitle(item) ?: return null
        val detailUrl = "https://api.jikan.moe/v4/anime/" + id + "/full"
        return newAnimeSearchResponse(title, detailUrl, TvType.Anime) {
            this.posterUrl = poster(item)
            this.year = year(item)
        }
    }

    private fun parseItems(json: JSONObject): List<SearchResponse> {
        val data = json.optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { index ->
            toSearchResponse(data.optJSONObject(index) ?: return@mapNotNull null)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNumber = page.coerceAtLeast(1)
        val filter = when (request.data) {
            "airing" -> "airing"
            "upcoming" -> "upcoming"
            else -> "bypopularity"
        }
        val endpoint = "https://api.jikan.moe/v4/top/anime?filter=" + filter + "&limit=25&page=" + pageNumber
        val items = parseItems(JSONObject(app.get(endpoint).text))
        return newHomePageResponse(request.name, items, items.size >= 25)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val endpoint = "https://api.jikan.moe/v4/anime?q=" +
            URLEncoder.encode(term, "UTF-8") + "&sfw=true&limit=25&page=1"
        return parseItems(JSONObject(app.get(endpoint).text))
    }

    override suspend fun load(url: String): LoadResponse {
        val json = JSONObject(app.get(url).text)
        val item = json.optJSONObject("data")
            ?: throw ErrorLoadingException("Metadados do anime não encontrados.")
        val title = animeTitle(item) ?: "Sem título"
        val synopsis = clean(item.optString("synopsis"))
        val tags = genres(item)
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster(item)
            this.plot = synopsis
            this.year = year(item)
            this.tags = tags
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean = false
}
