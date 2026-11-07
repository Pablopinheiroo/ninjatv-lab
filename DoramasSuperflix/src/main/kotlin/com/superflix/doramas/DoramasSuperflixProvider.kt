package com.superflix.doramas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Catálogo de doramas baseado na API pública TVmaze.
 * TVmaze publica seus dados sob CC BY-SA; consulte a atribuição no README.
 * Somente metadados; não oferece fontes de vídeo.
 */
class DoramasSuperflixProvider : MainAPI() {
    override var mainUrl = "https://api.tvmaze.com"
    override var name = "Doramas Superflix"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.TvSeries)

    private val asianCountries = setOf("KR", "JP", "CN", "TW", "HK")

    override val mainPage = mainPageOf(
        "drama" to "Doramas em destaque",
        "korean" to "Coreanos",
        "japanese" to "Japoneses",
        "chinese" to "Chineses",
        "taiwanese" to "Taiwaneses"
    )

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun countryCode(show: JSONObject): String? {
        val network = show.optJSONObject("network")
        val webChannel = show.optJSONObject("webChannel")
        val networkCode = network?.optJSONObject("country")?.optString("code")
        val webCode = webChannel?.optJSONObject("country")?.optString("code")
        return sequenceOf(networkCode, webCode)
            .firstOrNull { !it.isNullOrBlank() && it != "null" }
    }

    private fun isDorama(show: JSONObject): Boolean {
        val code = countryCode(show)
        val language = show.optString("language")
        return (code != null && code in asianCountries) ||
            language.equals("Korean", true) ||
            language.equals("Japanese", true) ||
            language.equals("Chinese", true)
    }

    private fun image(show: JSONObject): String? =
        show.optJSONObject("image")?.let {
            clean(it.optString("original")) ?: clean(it.optString("medium"))
        }

    private fun showGenres(show: JSONObject): List<String> {
        val genres = show.optJSONArray("genres") ?: JSONArray()
        return (0 until genres.length()).mapNotNull { index ->
            clean(genres.optString(index))
        }.distinct()
    }

    private fun toSearchResponse(show: JSONObject): SearchResponse? {
        if (!isDorama(show)) return null
        val id = show.optInt("id", 0)
        if (id <= 0) return null
        val title = clean(show.optString("name")) ?: return null
        val detailUrl = "https://api.tvmaze.com/shows/" + id
        val year = show.optString("premiered").take(4).toIntOrNull()
        return newTvSeriesSearchResponse(title, detailUrl, TvType.TvSeries) {
            this.posterUrl = image(show)
            this.year = year
        }
    }

    private fun parseSearch(json: JSONArray): List<SearchResponse> {
        return (0 until json.length()).mapNotNull { index ->
            val result = json.optJSONObject(index) ?: return@mapNotNull null
            toSearchResponse(result.optJSONObject("show") ?: return@mapNotNull null)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val query = when (request.data) {
            "korean" -> "korean"
            "japanese" -> "japanese"
            "chinese" -> "chinese"
            "taiwanese" -> "taiwanese"
            else -> "drama"
        }
        val endpoint = "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(query, "UTF-8")
        val items = parseSearch(JSONArray(app.get(endpoint).text))
        return newHomePageResponse(request.name, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val endpoint = "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(term, "UTF-8")
        return parseSearch(JSONArray(app.get(endpoint).text))
    }

    override suspend fun load(url: String): LoadResponse {
        val show = JSONObject(app.get(url).text)
        val title = clean(show.optString("name")) ?: "Sem título"
        val poster = image(show)
        val plot = clean(show.optString("summary"))
        val premiered = show.optString("premiered")
        val year = premiered.take(4).toIntOrNull()
        val tags = showGenres(show) + listOfNotNull(countryCode(show))
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags.distinct()
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
