package com.donflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Donflix public catalog provider.
 *
 * Reads public catalog and detail pages using ordinary HTTP requests.
 * The native player integration is not implemented yet: loadLinks returns
 * false until a source can be supplied through an authorized native playback
 * interface. This module does not use a WebView.
 */
class DonflixProvider : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Donflix"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    companion object {
        private const val BASE_URL = "https://donflix.net"

        private val sections = listOf(
            "$BASE_URL/filmes" to "Filmes",
            "$BASE_URL/series" to "Séries",
            "$BASE_URL/animes" to "Animes",
            "$BASE_URL/filmes/dublados" to "Filmes dublados",
            "$BASE_URL/filmes/lancamentos" to "Lançamentos",
            "$BASE_URL/filmes/melhores" to "Melhores filmes",
            "$BASE_URL/filmes/classicos" to "Clássicos",
            "$BASE_URL/filmes/brasileiros" to "Brasileiros",
            "$BASE_URL/filmes/portugueses" to "Portugueses",
            "$BASE_URL/filmes/super-herois" to "Super-heróis",
            "$BASE_URL/filmes/zumbis" to "Zumbis",
            "$BASE_URL/filmes/natal" to "Natal",
            "$BASE_URL/filmes/acao" to "Ação",
            "$BASE_URL/filmes/aventura" to "Aventura",
            "$BASE_URL/filmes/animacao" to "Animação",
            "$BASE_URL/filmes/comedia" to "Comédia",
            "$BASE_URL/filmes/crime" to "Crime",
            "$BASE_URL/filmes/documentarios" to "Documentários",
            "$BASE_URL/filmes/drama" to "Drama",
            "$BASE_URL/filmes/familia" to "Família",
            "$BASE_URL/filmes/fantasia" to "Fantasia",
            "$BASE_URL/filmes/terror" to "Terror",
            "$BASE_URL/filmes/romance" to "Romance",
            "$BASE_URL/filmes/ficcao-cientifica" to "Ficção científica",
            "$BASE_URL/filmes/guerra" to "Guerra",
            "$BASE_URL/filmes/faroeste" to "Faroeste",
            "$BASE_URL/filmes/suspense" to "Suspense",
            "$BASE_URL/filmes/misterio" to "Mistério",
            "$BASE_URL/filmes/historicos" to "Históricos",
            "$BASE_URL/filmes/netflix" to "Netflix",
            "$BASE_URL/filmes/prime-video" to "Prime Video",
            "$BASE_URL/filmes/disney-plus" to "Disney+",
            "$BASE_URL/filmes/max" to "Max",
            "$BASE_URL/filmes/globoplay" to "Globoplay"
        )

        private val contentPath = Regex("^/(filme|serie|anime)/[^/]+/?$", RegexOption.IGNORE_CASE)
        private val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        private val seasonRegex = Regex("[?&]season=(\\d+)", RegexOption.IGNORE_CASE)
        private val episodeRegex = Regex("[?&]episode=(\\d+)", RegexOption.IGNORE_CASE)
    }

    override val mainPage = mainPageOf(*sections.toTypedArray())

    private fun absoluteUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val fixed = fixUrl(raw.trim())
        return fixed.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun elementImage(element: Element?): String? {
        if (element == null) return null
        val raw = sequenceOf(
            element.attr("src"),
            element.attr("data-src"),
            element.attr("data-lazy-src"),
            element.attr("data-original"),
            element.attr("srcset").substringBefore(",").substringBefore(" ")
        ).firstOrNull { it.isNotBlank() }
        return absoluteUrl(raw)
    }

    private fun classify(url: String): TvType? {
        val path = runCatching { url.substringAfter("://").substringAfter("/") }.getOrDefault(url)
            .substringBefore("?").lowercase()
        return when {
            path.startsWith("filme/") -> TvType.Movie
            path.startsWith("serie/") -> TvType.TvSeries
            path.startsWith("anime/") -> TvType.Anime
            else -> null
        }
    }

    private fun yearFrom(vararg values: String?): Int? {
        values.forEach { value ->
            if (!value.isNullOrBlank()) {
                val year = yearRegex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (year != null) return year
            }
        }
        return null
    }

    private fun titleFromCard(anchor: Element): String? {
        val image = anchor.selectFirst("img")
        return listOf(
            image?.attr("alt"),
            anchor.attr("title"),
            anchor.attr("aria-label"),
            anchor.selectFirst("h2, h3, h4, [class*=title], [class*=name]")?.text(),
            anchor.text()
        ).firstOrNull { !it.isNullOrBlank() }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun toSearchResponse(anchor: Element): SearchResponse? {
        val href = absoluteUrl(anchor.attr("href")) ?: return null
        val type = classify(href) ?: return null
        val path = "/" + href.substringAfter("://").substringAfter("/").substringBefore("?")
        if (!contentPath.matches(path)) return null

        val title = titleFromCard(anchor) ?: return null
        val poster = elementImage(anchor.selectFirst("img"))
        val year = yearFrom(title, href, anchor.text())
        val cleanTitle = title.replace(Regex("\\s*\\(?\\b(19\\d{2}|20\\d{2})\\)?\\s*$"), "").trim()

        return when (type) {
            TvType.Movie -> newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
            TvType.Anime -> newAnimeSearchResponse(cleanTitle, href, TvType.Anime) {
                this.posterUrl = poster
                this.year = year
            }
            else -> newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    private fun extractCards(document: Document): List<SearchResponse> {
        return document.select("a[href]")
            .mapNotNull { anchor ->
                val href = absoluteUrl(anchor.attr("href")) ?: return@mapNotNull null
                if (classify(href) == null) return@mapNotNull null
                toSearchResponse(anchor)
            }
            .distinctBy { it.url }
    }

    private fun hasNextPage(document: Document, currentPage: Int): Boolean {
        val nextNumber = currentPage + 1
        return document.select("a[href], link[rel=next]").any { element ->
            val href = element.attr("href")
            element.attr("rel").equals("next", ignoreCase = true) ||
                href.contains("page=$nextNumber") ||
                href.contains("/page/$nextNumber/")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = request.data.ifBlank { "$BASE_URL/filmes" }
        val pageUrl = if (page <= 1) {
            base
        } else {
            val separator = if (base.contains("?")) "&" else "?"
            "$base${separator}page=$page"
        }
        val document = app.get(pageUrl, referer = BASE_URL).document
        return newHomePageResponse(request.name, extractCards(document), hasNextPage(document, page))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(term, "UTF-8")
        val document = app.get("$BASE_URL/buscar?query=$encoded", referer = BASE_URL).document
        return extractCards(document)
    }

    private fun pageTitle(document: Document, fallback: String): String {
        return document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: document.title().substringBefore("|").trim().takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun pagePoster(document: Document): String? {
        val meta = document.selectFirst("meta[property=og:image]")?.attr("content")
        return absoluteUrl(meta) ?: elementImage(document.selectFirst("main img, article img, img[alt]"))
    }

    private fun pagePlot(document: Document): String? {
        return document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("main p, article p")?.text()?.trim()?.takeIf { it.length > 30 }
    }

    private fun extractEpisodes(document: Document, poster: String?): List<Episode> {
        return document.select("a[href*='/embed/']")
            .mapNotNull { anchor ->
                val episodeUrl = absoluteUrl(anchor.attr("href")) ?: return@mapNotNull null
                val season = seasonRegex.find(episodeUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                val episodeNumber = episodeRegex.find(episodeUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Regex("""(?i)(?:epis[oó]dio|ep\\.?)\\s*(\\d+)""")
                        .find(anchor.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                val label = sequenceOf(
                    anchor.attr("title"),
                    anchor.attr("aria-label"),
                    anchor.selectFirst("[class*=title], [class*=name], span")?.text(),
                    anchor.text()
                ).firstOrNull { !it.isNullOrBlank() }?.trim()
                    ?: episodeNumber?.let { "Episódio $it" }
                    ?: "Episódio"

                newEpisode(episodeUrl) {
                    name = label
                    this.season = season
                    this.episode = episodeNumber
                    posterUrl = elementImage(anchor.selectFirst("img")) ?: poster
                }
            }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.season ?: 1 }.thenBy { it.episode ?: Int.MAX_VALUE })
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, referer = BASE_URL).document
        val type = classify(url) ?: throw ErrorLoadingException("Tipo de conteúdo não reconhecido.")
        val title = pageTitle(document, "Sem título")
        val poster = pagePoster(document)
        val plot = pagePlot(document)
        val year = yearFrom(title, url, document.text())
        val tags = document.select("a[href*='/categoria/'], a[href*='/genero/'], [class*=genre] a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return when (type) {
            TvType.Movie -> newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
            TvType.Anime -> {
                val episodes = extractEpisodes(document, poster)
                newAnimeLoadResponse(title, url, TvType.Anime) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                    this.tags = tags
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            }
            else -> {
                val episodes = extractEpisodes(document, poster)
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                    this.tags = tags
                }
            }
        }
    }

    /**
     * Native video playback is not yet connected. The catalog and metadata
     * methods above do not create or return video sources.
     */
    @Suppress("UNUSED_PARAMETER")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean = false
}
