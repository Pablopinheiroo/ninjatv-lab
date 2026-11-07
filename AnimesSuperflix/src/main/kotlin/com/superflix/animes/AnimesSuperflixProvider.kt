package com.superflix.animes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Catálogo de metadados de anime via Kitsu API.
 * Kitsu substitui o endpoint Jikan que falhou na conexão IPv6 do aparelho.
 * Somente metadados; não oferece fontes de vídeo.
 */
class AnimesSuperflixProvider : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Animes Superflix"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Anime)

    companion object {
        private const val BASE_URL = "https://kitsu.io/api/edge"
        private const val PAGE_SIZE = 20
    }

    private val categoryPages = listOf(
        "category|action" to "Ação",
        "category|adventure" to "Aventura",
        "category|comedy" to "Comédia",
        "category|drama" to "Drama",
        "category|fantasy" to "Fantasia",
        "category|horror" to "Terror",
        "category|mystery" to "Mistério",
        "category|romance" to "Romance",
        "category|science-fiction" to "Ficção científica",
        "category|slice-of-life" to "Cotidiano",
        "category|sports" to "Esportes",
        "category|supernatural" to "Sobrenatural",
        "category|thriller" to "Suspense",
        "category|music" to "Música"
    )

    override val mainPage = mainPageOf(
        *(
            listOf(
                "popular" to "Mais populares",
                "current" to "Em exibição",
                "upcoming" to "Em breve"
            ) + categoryPages
        ).toTypedArray()
    )

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun animeTitle(attributes: JSONObject): String? {
        val titles = attributes.optJSONObject("titles")
        return clean(attributes.optString("canonicalTitle"))
            ?: clean(titles?.optString("en"))
            ?: clean(titles?.optString("en_jp"))
            ?: clean(titles?.optString("ja_jp"))
    }

    private fun poster(attributes: JSONObject): String? =
        attributes.optJSONObject("posterImage")?.let {
            clean(it.optString("large"))
                ?: clean(it.optString("medium"))
                ?: clean(it.optString("original"))
                ?: clean(it.optString("small"))
        }

    private fun year(attributes: JSONObject): Int? =
        attributes.optString("startDate").take(4).toIntOrNull()

    private fun toSearchResponse(item: JSONObject): SearchResponse? {
        val id = clean(item.optString("id")) ?: return null
        val attributes = item.optJSONObject("attributes") ?: return null
        val title = animeTitle(attributes) ?: return null
        val detailUrl = BASE_URL + "/anime/" + id + "?include=genres"
        return newAnimeSearchResponse(title, detailUrl, TvType.Anime) {
            this.posterUrl = poster(attributes)
            this.year = year(attributes)
        }
    }

    private fun parseItems(json: JSONObject): List<SearchResponse> {
        val data = json.optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { index ->
            toSearchResponse(data.optJSONObject(index) ?: return@mapNotNull null)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val offset = (page - 1).coerceAtLeast(0) * PAGE_SIZE
        val params = mutableListOf(
            "page[limit]=$PAGE_SIZE",
            "page[offset]=$offset"
        )
        when {
            request.data.startsWith("category|") ->
                params.add("filter[categories]=" + encode(request.data.substringAfter("category|")))
            request.data == "current" -> params.add("filter[status]=current")
            request.data == "upcoming" -> params.add("filter[status]=upcoming")
        }
        params.add("sort=-userCount")
        val endpoint = BASE_URL + "/anime?" + params.joinToString("&")
        val json = JSONObject(app.get(endpoint).text)
        val next = clean(json.optJSONObject("links")?.optString("next"))
        val items = parseItems(json)
        return newHomePageResponse(request.name, items, next != null)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val endpoint = BASE_URL + "/anime?filter[text]=" + encode(term) +
            "&page[limit]=$PAGE_SIZE&sort=-userCount"
        return parseItems(JSONObject(app.get(endpoint).text))
    }

    override suspend fun load(url: String): LoadResponse {
        val response = JSONObject(app.get(url).text)
        val item = response.optJSONObject("data")
            ?: throw ErrorLoadingException("Metadados do anime não encontrados.")
        val attributes = item.optJSONObject("attributes") ?: JSONObject()
        val title = animeTitle(attributes) ?: "Sem título"
        val included = response.optJSONArray("included") ?: JSONArray()
        val tags = (0 until included.length()).mapNotNull { index ->
            val related = included.optJSONObject(index) ?: return@mapNotNull null
            if (related.optString("type") != "genres") return@mapNotNull null
            clean(related.optJSONObject("attributes")?.optString("name"))
        }.distinct()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster(attributes)
            this.plot = clean(attributes.optString("synopsis"))
            this.year = year(attributes)
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
