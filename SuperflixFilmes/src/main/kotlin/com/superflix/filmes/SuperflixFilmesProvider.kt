package com.superflix.filmes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Catálogo de metadados independente.
 * Não consulta fontes de vídeo e não fornece reprodução.
 * Fonte dos metadados: Cinemeta.
 */
class SuperflixFilmesProvider : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Filmes Superflix"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Movie)

    companion object {
        private const val BASE_URL = "https://v3-cinemeta.strem.io"
        private const val CONTENT_TYPE = "movie"
        private val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
    }

    override val mainPage = mainPageOf("top" to "Em destaque")

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun yearFrom(value: String?): Int? =
        value?.let { yearRegex.find(it)?.value?.toIntOrNull() }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.opt(index)
            when (item) {
                is JSONObject -> clean(item.optString("name"))
                else -> clean(item?.toString())
            }
        }.distinct()
    }

    private fun toSearchResponse(item: JSONObject): SearchResponse? {
        val id = clean(item.optString("id")) ?: return null
        val title = clean(item.optString("name")) ?: return null
        val detailUrl = BASE_URL + "/meta/" + CONTENT_TYPE + "/" + id + ".json"
        val poster = clean(item.optString("poster"))
        val year = yearFrom(item.optString("releaseInfo"))

        return newMovieSearchResponse(title, detailUrl, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private fun parseCatalog(json: JSONObject): List<SearchResponse> {
        val metas = json.optJSONArray("metas") ?: JSONArray()
        return (0 until metas.length()).mapNotNull { index ->
            toSearchResponse(metas.optJSONObject(index) ?: return@mapNotNull null)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val skip = if (page <= 1) "" else "/skip=" + ((page - 1) * 25)
        val endpoint = BASE_URL + "/catalog/" + CONTENT_TYPE + "/" + request.data + skip + ".json"
        val json = JSONObject(app.get(endpoint).text)
        val items = parseCatalog(json)
        return newHomePageResponse(request.name, items, items.size >= 25)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(term, "UTF-8")
        val endpoint = BASE_URL + "/catalog/" + CONTENT_TYPE + "/top/search=" + encoded + ".json"
        return parseCatalog(JSONObject(app.get(endpoint).text))
    }

    override suspend fun load(url: String): LoadResponse {
        val response = JSONObject(app.get(url).text)
        val meta = response.optJSONObject("meta")
            ?: throw ErrorLoadingException("Metadados não encontrados.")
        val title = clean(meta.optString("name")) ?: "Sem título"
        val poster = clean(meta.optString("poster"))
        val plot = clean(meta.optString("description"))
        val year = yearFrom(meta.optString("releaseInfo"))
        val tags = stringList(meta.optJSONArray("genres"))

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
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
