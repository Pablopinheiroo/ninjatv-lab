package com.superflix.doramas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Catálogo de doramas baseado no índice público TVmaze.
 * TVmaze publica seus dados sob CC BY-SA; consulte a atribuição no README.
 * Somente metadados; não oferece fontes de vídeo.
 */
class DoramasSuperflixProvider : MainAPI() {
    override var mainUrl = "https://www.tvmaze.com"
    override var name = "Doramas Superflix"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.TvSeries)

    private val asianCountries = setOf("KR", "JP", "CN", "TW", "HK")

    private val categoryPages = listOf(
        "genre|Action" to "Ação",
        "genre|Action,Adventure" to "Action & Adventure",
        "genre|Anime,Animation" to "Animação",
        "genre|Adventure" to "Aventura",
        "genre|Comedy" to "Comédia",
        "genre|Crime" to "Crime",
        "genre|Documentary" to "Documentário",
        "genre|Drama" to "Drama",
        "genre|Family" to "Família",
        "genre|Fantasy" to "Fantasia",
        "genre|Western" to "Faroeste",
        "genre|Science-Fiction" to "Ficção científica",
        "genre|War" to "Guerra",
        "genre|History" to "História",
        "genre|Mystery" to "Mistério",
        "genre|Music" to "Música",
        "genre|Romance" to "Romance",
        "genre|Science-Fiction,Fantasy" to "Sci-Fi & Fantasy",
        "genre|Horror" to "Terror",
        "genre|Thriller" to "Thriller",
        "genre|War" to "War & Politics"
    )

    override val mainPage = mainPageOf(
        *(
            listOf(
                "asian" to "Doramas em destaque",
                "country|KR" to "Coreanos",
                "country|JP" to "Japoneses",
                "country|CN" to "Chineses",
                "country|TW" to "Taiwaneses"
            ) + categoryPages
        ).toTypedArray()
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

    private fun showLanguage(show: JSONObject): String =
        clean(show.optString("language")) ?: ""

    private fun isDorama(show: JSONObject): Boolean {
        val code = countryCode(show)
        val language = showLanguage(show)
        return code in asianCountries ||
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

    private fun matchesCategory(show: JSONObject, data: String): Boolean {
        if (data.startsWith("country|")) {
            val expected = data.substringAfter("country|")
            val code = countryCode(show)
            val language = showLanguage(show)
            return code == expected || when (expected) {
                "KR" -> language.equals("Korean", true)
                "JP" -> language.equals("Japanese", true)
                "CN", "TW" -> language.equals("Chinese", true)
                else -> false
            }
        }
        if (data.startsWith("genre|")) {
            val wanted = data.substringAfter("genre|").split(",").map { it.trim() }
            val actual = showGenres(show)
            return wanted.any { selected ->
                actual.any { it.equals(selected, ignoreCase = true) }
            }
        }
        return true
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

    private fun parseIndex(json: JSONArray, category: String): List<SearchResponse> {
        return (0 until json.length()).mapNotNull { index ->
            val show = json.optJSONObject(index) ?: return@mapNotNull null
            if (!isDorama(show) || !matchesCategory(show, category)) return@mapNotNull null
            toSearchResponse(show)
        }.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // TVmaze show index is paginated by 250 IDs per page; the app page number
        // follows the index so browsing can continue beyond the first 100 results.
        val indexPage = (page - 1).coerceAtLeast(0)
        val endpoint = "https://api.tvmaze.com/shows?page=" + indexPage
        val json = JSONArray(app.get(endpoint).text)
        val items = parseIndex(json, request.data)
        return newHomePageResponse(request.name, items, json.length() >= 250)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val endpoint = "https://api.tvmaze.com/search/shows?q=" +
            URLEncoder.encode(term, "UTF-8").replace("+", "%20")
        return parseSearch(JSONArray(app.get(endpoint).text))
            .distinctBy { it.url }
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
