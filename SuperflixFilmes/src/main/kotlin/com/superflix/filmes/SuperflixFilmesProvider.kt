package com.superflix.filmes

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Experimental SuperFlix movie catalog.
 *
 * Catalog and player-page routes follow the public SuperFlix API documentation.
 * The documented playback integration is an HTML iframe page, not a direct
 * VIDEO/M3U8/DASH URL. Cloudstream's native ExtractorLink contract accepts
 * media resources, so this provider forwards playback to the official page
 * in the device browser instead of mislabeling HTML as a video stream.
 */
class SuperflixFilmesProvider : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Superflix Filmes"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Movie)

    companion object {
        const val BASE_URL = "https://superflixapi.boats"
        private const val DOCUMENTED_TEST_ID = "tt0068646"

        @Volatile
        internal var appContext: Context? = null

        private val moviePathRegex = Regex(
            "^/filme/([^/]+)/?$",
            RegexOption.IGNORE_CASE
        )
        private val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
    }

    override val mainPage = mainPageOf(
        "$BASE_URL/filmes" to "Catálogo de filmes"
    )

    private fun absoluteUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val fixed = fixUrl(raw.trim())
        return fixed.takeIf {
            it.startsWith("https://") || it.startsWith("http://")
        }
    }

    private fun movieIdFrom(rawUrl: String): String? {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        val match = moviePathRegex.matchEntire(uri.path.orEmpty()) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
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

    private fun titleFromCard(anchor: Element, id: String): String {
        return sequenceOf(
            anchor.selectFirst("img")?.attr("alt"),
            anchor.attr("title"),
            anchor.attr("aria-label"),
            anchor.selectFirst("h2, h3, h4, [class*=title], [class*=name]")?.text(),
            anchor.text()
        ).firstOrNull { !it.isNullOrBlank() }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Filme $id"
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

    private fun toSearchResponse(anchor: Element): SearchResponse? {
        val href = absoluteUrl(anchor.attr("href")) ?: return null
        val id = movieIdFrom(href) ?: return null
        val title = titleFromCard(anchor, id)
        val year = yearFrom(title, href, anchor.text())
        val cleanTitle = title
            .replace(Regex("\\s*\\(?\\b(19\\d{2}|20\\d{2})\\)?\\s*$"), "")
            .trim()
            .ifBlank { title }

        return newMovieSearchResponse(
            cleanTitle,
            "$mainUrl/filme/$id",
            TvType.Movie
        ) {
            posterUrl = elementImage(anchor.selectFirst("img"))
            this.year = year
        }
    }

    private fun extractCards(document: Document): List<SearchResponse> {
        return document.select("a[href]")
            .mapNotNull { toSearchResponse(it) }
            .distinctBy { it.url }
    }

    private fun documentedTestMovie(): SearchResponse {
        return newMovieSearchResponse(
            "O Poderoso Chefão — teste de integração",
            "$mainUrl/filme/$DOCUMENTED_TEST_ID",
            TvType.Movie
        ) {
            year = 1972
        }
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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val base = request.data.ifBlank { "$mainUrl/filmes" }
        val pageUrl = if (page <= 1) {
            base
        } else {
            val separator = if (base.contains("?")) "&" else "?"
            "$base${separator}page=$page"
        }

        val document = runCatching {
            app.get(pageUrl, referer = mainUrl).document
        }.getOrNull()

        val cards = document?.let { extractCards(it) }.orEmpty()
        val results = if (page == 1) {
            listOf(documentedTestMovie()) + cards.filterNot {
                it.url.endsWith("/filme/$DOCUMENTED_TEST_ID")
            }
        } else {
            cards
        }

        return newHomePageResponse(
            request.name,
            results.distinctBy { it.url },
            document?.let { hasNextPage(it, page) } ?: false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()

        val encoded = URLEncoder.encode(term, "UTF-8")
        val searchUrl =
            "$mainUrl/lista?category=pesquisa&q=$encoded&format=html"
        val document = app.get(searchUrl, referer = mainUrl).document
        return extractCards(document)
    }

    private fun pageTitle(document: Document?, fallback: String): String {
        return document?.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.selectFirst("meta[property=og:title]")?.attr("content")
                ?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.title()?.substringBefore("|")?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun pagePoster(document: Document?): String? {
        val meta = document?.selectFirst("meta[property=og:image]")?.attr("content")
        return absoluteUrl(meta)
            ?: elementImage(document?.selectFirst("main img, article img, img[alt]"))
    }

    private fun pagePlot(document: Document?): String? {
        return document?.selectFirst("meta[property=og:description]")?.attr("content")
            ?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.selectFirst("meta[name=description]")?.attr("content")
                ?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.selectFirst("main p, article p")?.text()
                ?.trim()?.takeIf { it.length > 30 }
    }

    override suspend fun load(url: String): LoadResponse {
        val canonicalUrl = absoluteUrl(url)
            ?: throw ErrorLoadingException("Endereço de filme inválido.")
        val id = movieIdFrom(canonicalUrl)
            ?: throw ErrorLoadingException("ID de filme SuperFlix inválido.")

        // Details are best-effort: a catalog page may be readable even when
        // the player page applies additional browser-side checks.
        val document = runCatching {
            app.get(canonicalUrl, referer = mainUrl).document
        }.getOrNull()

        val title = pageTitle(document, "Filme $id")
        val poster = pagePoster(document)
        val plot = pagePlot(document)
        val year = yearFrom(title, canonicalUrl, document?.text())

        return newMovieLoadResponse(
            title,
            canonicalUrl,
            TvType.Movie,
            canonicalUrl
        ) {
            posterUrl = poster
            this.year = year
            this.plot = plot
        }
    }

    /**
     * Opens the documented SuperFlix player page in the user's browser.
     *
     * The public integration describes an iframe page. It does not publish a
     * direct media URL suitable for ExtractorLink, so this method does not
     * pretend the HTML page is a native video stream.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean {
        val playerUrl = absoluteUrl(data) ?: return false
        val uri = runCatching { Uri.parse(playerUrl) }.getOrNull() ?: return false

        if (uri.scheme != "https" ||
            uri.host?.equals(Uri.parse(mainUrl).host, ignoreCase = true) != true ||
            moviePathRegex.matchEntire(uri.path.orEmpty()) == null
        ) {
            return false
        }

        val context = appContext ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
