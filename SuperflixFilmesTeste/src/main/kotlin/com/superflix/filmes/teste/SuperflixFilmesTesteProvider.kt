package com.superflix.filmes.teste

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONTokener
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream

/**
 * Teste SuperFlix de filmes. O catálogo é carregado pelo endpoint JSON público;
 * não existe item de teste pré-cadastrado nem alternativa HTML.
 */
class SuperflixFilmesTesteProvider : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Superflix Filmes — Teste API"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie)

    companion object {
        const val BASE_URL = "https://superflixapi.monster"

        // Domínios oficiais/aliases conhecidos do SuperFlix. O domínio da
        // página pode redirecionar, enquanto o iframe já usa o domínio atual.
        private val allowedPlayerHosts = setOf(
            "superflixapi.boats",
            "superflixapi.monster",
            "superflixapi.fun",
            "superflixapi.baby",
            "superflixapi.pro"
        )

        // Recursos de publicidade/telemetria observados nos testes.
        // O domínio do player e o Cloudflare Turnstile NÃO entram nesta lista.
        private val blockedAdResourceHosts = setOf(
            "cvt-s2.agl004.fun",
            "whos.amung.us",
            "ad.turn.com",
            "sync.srv.stackadapt.com",
            "match.adsrvr.org",
            "sync.ipredictive.com",
            "match.sharethrough.com",
            "sync.inmobi.com",
            "ssbsync.smartadserver.com",
            "bh.contextweb.com",
            "usync.ingage.tech",
            "c1.adform.net",
            "ih.adscale.de",
            "ads.pubmatic.com",
            "sync.ottadvisors.com",
            "cs-server-s2s.yellowblue.io",
            "rtb.gumgum.com",
            "sync.1rx.io",
            "ssum.casalemedia.com",
            "sync.go.sonobi.com",
            "csync.loopme.me",
            "pixel-sync.sitescout.com",
            "s.ad.smaato.net",
            "ssp-sync.criteo.com",
            "x.bidswitch.net",
            "eb2.3lift.com",
            "secure.adnxs.com",
            "sync.intentiq.com",
            "cdn.id5-sync.com",
            "jadserve.postrelease.com",
            "us-u.openx.net",
            "match.prod.bidr.io",
            "cs.media.net",
            "eus.rubiconproject.com",
            "track.bidgx.com",
            "cm.g.doubleclick.net",
            "sync.pmbmonetize.live",
            "servetraff.com"
        )

        private fun isBlockedAdResource(uri: Uri): Boolean {
            val host = uri.host?.lowercase() ?: return false
            val path = uri.encodedPath.orEmpty().lowercase()

            if (host in blockedAdResourceHosts) return true
            if (host == "capitalhospitals.org" && path.startsWith("/1/")) return true

            // Bloqueia somente os pings/telemetria do JW Player, não o player nem seus vídeos.
            if (host == "prd.jwpltx.com" &&
                (path == "/v1/jwplayer6/ping.gif" || path == "/v1/clienta/ping.gif")
            ) return true

            // Links de campanha observados no relatório.
            if (host == "s.shopee.com.br") return true
            if (host == "asiafilm.org" && path.startsWith("/4/")) return true

            return false
        }

        private fun isBlockedAdNavigation(uri: Uri): Boolean {
            val host = uri.host?.lowercase() ?: return false
            val path = uri.encodedPath.orEmpty().lowercase()
            return host == "s.shopee.com.br" ||
                (host == "asiafilm.org" && path.startsWith("/4/"))
        }

        private val playerPathRegex = Regex(
            "^/(?:filme/[^/]+|serie/\\d+(?:/\\d+(?:/\\d+)?)?)/?$",
            RegexOption.IGNORE_CASE
        )
        private val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")

        // Procura o iframe que a própria página apresenta para copiar/incorporar.
        private val findIframeSourcesScript = """
            (function () {
                const candidates = [];

                document.querySelectorAll("iframe[src]").forEach(function (node) {
                    candidates.push(node.getAttribute("src") || "");
                });

                const nodes = document.querySelectorAll(
                    "textarea, pre, code, input, [data-clipboard-text], [data-code], [onclick]"
                );
                nodes.forEach(function (node) {
                    candidates.push(node.value || "");
                    candidates.push(node.textContent || "");
                    ["data-clipboard-text", "data-code", "value", "onclick"].forEach(function (key) {
                        const value = node.getAttribute(key);
                        if (value) candidates.push(value);
                    });
                });

                if (document.body) candidates.push(document.body.innerText || "");
                if (document.documentElement) candidates.push(document.documentElement.innerHTML || "");

                const found = [];
                candidates.forEach(function (value) {
                    const decoded = String(value || "")
                        .replace(/&quot;/gi, '"')
                        .replace(/&#39;|&apos;/gi, "'")
                        .replace(/&lt;/gi, "<")
                        .replace(/&gt;/gi, ">")
                        .replace(/&amp;/gi, "&");

                    const expression = /<iframe\b[^>]*?\bsrc\s*=\s*(["'])(.*?)\1/gi;
                    let match;
                    while ((match = expression.exec(decoded)) !== null) {
                        found.push(match[2]);
                    }
                });

                return JSON.stringify(Array.from(new Set(found)));
            })();
        """.trimIndent()
    }


    override val mainPage = mainPageOf(
        (BASE_URL + "/lista?category=filme&type=tmdb&format=json&order=desc") to "Catálogo de filmes SuperFlix"
    )

    private data class CatalogEntry(
        val id: String,
        val title: String? = null,
        val poster: String? = null,
        val year: Int? = null,
        val kind: String? = null
    )

    private fun absoluteUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        val fixed = when {
            value.startsWith("//") -> "https:" + value
            value.startsWith("/") -> BASE_URL + value
            else -> fixUrl(value)
        }
        return fixed.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun normalizedPoster(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) return absoluteUrl(value)
        if (value.startsWith("/uploads/") || value.startsWith("/storage/") || value.startsWith("/images/") ||
            value.startsWith("/assets/") || value.startsWith("/media/")) return absoluteUrl(value)
        if (value.startsWith("/t/p/")) return "https://image.tmdb.org" + value
        if (value.startsWith("/w") && value.length > 3) return "https://image.tmdb.org/t/p" + value
        if (value.startsWith("/")) return "https://image.tmdb.org/t/p/w500" + value
        return absoluteUrl(value)
    }

    private fun firstText(obj: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty() && value != "null" && value != "false") return value
        }
        return null
    }

    private fun cleanCatalogId(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        return value.takeIf { it.matches(Regex("^(tt\\d{5,12}|\\d{1,12})$")) }
    }

    private var cachedCatalog: List<CatalogEntry>? = null
    private var cachedCatalogAt: Long = 0L
    private var cachedCatalogRetryAfter: Long = 0L

    private companion object RateLimitPolicy {
        const val RATE_LIMIT_COOLDOWN_MS = 20_000L
    }

    private fun parseCatalogJson(raw: String?): List<CatalogEntry> {
        if (raw.isNullOrBlank()) return emptyList()

        val entries = linkedMapOf<String, CatalogEntry>()

        fun addId(
            rawId: String?,
            title: String? = null,
            poster: String? = null,
            year: String? = null
        ) {
            val id = cleanCatalogId(rawId) ?: return
            val parsedYear = year?.let {
                Regex("\\b(19\\d{2}|20\\d{2})\\b").find(it)?.value?.toIntOrNull()
            }
            val candidate = CatalogEntry(
                id = id,
                title = title?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
                poster = normalizedPoster(poster),
                year = parsedYear
            )
            val previous = entries[id]
            entries[id] = when {
                previous == null -> candidate
                previous.title.isNullOrBlank() && !candidate.title.isNullOrBlank() -> candidate
                previous.poster.isNullOrBlank() && !candidate.poster.isNullOrBlank() ->
                    previous.copy(poster = candidate.poster, year = previous.year ?: candidate.year)
                else -> previous
            }
        }

        fun addStringIds(value: String) {
            val text = value.trim()
            if (text.isEmpty()) return

            // Handles direct IDs, /filme/{id} URLs and simple delimited ID lists.
            val direct = cleanCatalogId(text)
            if (direct != null) {
                addId(direct)
                return
            }

            val urlMatches = Regex(
                "(?:https?://[^\\s\"']+)?/filme/(tt\\d{5,12}|\\d{1,12})",
                RegexOption.IGNORE_CASE
            ).findAll(text)
            urlMatches.forEach { addId(it.groupValues[1]) }

            val tokens = text.split(Regex("[,;\\s]+"))
                .map { it.trim().trim('"', '\'', '[', ']', '{', '}') }
                .filter { it.isNotEmpty() }
            if (tokens.isNotEmpty() && tokens.all { cleanCatalogId(it) != null }) {
                tokens.forEach { addId(it) }
            }
        }

        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 12 || value == null || value == JSONObject.NULL) return

            when (value) {
                is JSONArray -> {
                    for (index in 0 until value.length()) visit(value.opt(index), depth + 1)
                }
                is JSONObject -> {
                    val idKeys = listOf(
                        "tmdb_id", "tmdb", "id_tmdb", "tmdbId", "tmdbID",
                        "movie_id", "imdb_id", "imdb", "id", "codigo"
                    )
                    val rawId = firstText(value, idKeys)
                    val pageUrl = firstText(value, listOf("page_url", "url", "link", "href", "permalink"))
                    val id = cleanCatalogId(rawId) ?: pageUrl?.let { extractCatalogId(it) }

                    if (id != null) {
                        addId(
                            id,
                            firstText(value, listOf("title", "name", "nome", "titulo", "original_title")),
                            firstText(value, listOf("poster_url", "poster", "poster_path", "image_url", "image", "thumbnail", "thumb", "cover")),
                            firstText(value, listOf("year", "ano", "release_year", "release_date"))
                        )
                    } else {
                        // Known collection wrappers used by public JSON APIs.
                        val collectionKeys = listOf(
                            "results", "data", "items", "ids", "titles",
                            "list", "catalog", "records", "content", "movies", "filmes"
                        )
                        var foundCollection = false
                        for (key in collectionKeys) {
                            if (!value.has(key) || value.isNull(key)) continue
                            foundCollection = true
                            visit(value.opt(key), depth + 1)
                        }

                        // Some versions return an object with IDs as property names.
                        if (!foundCollection) {
                            val iterator = value.keys()
                            while (iterator.hasNext()) {
                                val key = iterator.next()
                                val keyId = cleanCatalogId(key) ?: continue
                                val child = value.opt(key)
                                if (child is JSONObject) {
                                    addId(
                                        keyId,
                                        firstText(child, listOf("title", "name", "nome", "titulo")),
                                        firstText(child, listOf("poster_url", "poster", "poster_path", "image", "thumbnail", "cover")),
                                        firstText(child, listOf("year", "ano", "release_year", "release_date"))
                                    )
                                } else if (child is String) {
                                    addId(keyId, title = child)
                                } else {
                                    addId(keyId)
                                }
                            }
                        }
                    }
                }
                is Number -> addId(value.toLong().toString())
                is String -> {
                    val text = value.trim()
                    if (text.startsWith("{") || text.startsWith("[") || text.startsWith("\"")) {
                        val nested = runCatching { JSONTokener(text).nextValue() }.getOrNull()
                        if (nested != null && nested !is String) {
                            visit(nested, depth + 1)
                        } else if (nested is String && nested != text) {
                            visit(nested, depth + 1)
                        } else {
                            addStringIds(text)
                        }
                    } else {
                        addStringIds(text)
                    }
                }
            }
        }

        val root = runCatching { JSONTokener(raw.trim()).nextValue() }.getOrNull()
        if (root != null) {
            visit(root)
        } else {
            addStringIds(raw)
        }

        return entries.values.toList()
    }

    private fun extractCatalogId(rawUrl: String): String? {
        val uri = runCatching { Uri.parse(absoluteUrl(rawUrl) ?: rawUrl) }.getOrNull() ?: return null
        val match = Regex("^/filme/([^/]+)/?$", RegexOption.IGNORE_CASE).matchEntire(uri.path.orEmpty()) ?: return null
        return cleanCatalogId(match.groupValues.getOrNull(1))
    }

    private fun validatedEmbedUrl(raw: String?): String? {
        val candidate = absoluteUrl(raw) ?: return null
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val hostAllowed = uri.host?.lowercase()?.let { it in allowedPlayerHosts } ?: false
        return candidate.takeIf { uri.scheme == "https" && hostAllowed && playerPathRegex.matches(uri.path.orEmpty()) }
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

    private fun elementImage(element: Element?): String? {
        if (element == null) return null
        val raw = sequenceOf(element.attr("src"),element.attr("data-src"),element.attr("data-lazy-src"),
            element.attr("data-original"),element.attr("srcset").substringBefore(",").substringBefore(" "))
            .firstOrNull { it.isNotBlank() }
        return normalizedPoster(raw)
    }

    private fun contentPageUrl(id: String): String = BASE_URL + "/filme/" + id

    private fun toSearchResponse(entry: CatalogEntry): SearchResponse {
        val title = entry.title?.takeIf { it.isNotBlank() }
            ?: "Filme TMDB " + entry.id
        val url = contentPageUrl(entry.id)
        return newMovieSearchResponse(title, url, TvType.Movie) {
            posterUrl = entry.poster
            year = entry.year
        }
    }

    /**
     * Consulta somente o endpoint JSON documentado pelo serviço.
     * A lista completa é mantida em memória por alguns minutos para que
     * avançar páginas não provoque uma nova transferência do catálogo inteiro.
     */
    private suspend fun fetchApiEntries(): List<CatalogEntry> {
        val now = System.currentTimeMillis()
        val cached = cachedCatalog
        if (cached != null && now - cachedCatalogAt < 5 * 60 * 1000L) {
            return cached
        }
        if (now < cachedCatalogRetryAfter) {
            val waitSeconds = ((cachedCatalogRetryAfter - now) / 1000L).coerceAtLeast(1L)
            throw ErrorLoadingException(
                "A API SuperFlix pediu uma pausa. Aguarde aproximadamente " +
                    waitSeconds + " segundos antes de tentar novamente."
            )
        }

        val url = BASE_URL + "/lista?category=filme&type=tmdb&format=json&order=desc"
        val raw = try {
            app.get(
                url,
                referer = mainUrl,
                headers = mapOf("Accept" to "application/json")
            ).text
        } catch (error: Exception) {
            val detail = error.message ?: error.javaClass.simpleName
            if (detail.contains("429") ||
                detail.contains("too many", ignoreCase = true) ||
                detail.contains("rate limit", ignoreCase = true)
            ) {
                cachedCatalogRetryAfter = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
                throw ErrorLoadingException(
                    "A API SuperFlix limitou temporariamente as consultas. " +
                        "Aguarde alguns segundos antes de atualizar o catálogo."
                )
            }
            throw ErrorLoadingException("Não foi possível consultar a API pública de filmes: " + detail)
        }

        val trimmed = raw.trimStart()

        // A API pode responder HTTP 200 com um JSON de erro. Reconheça isso
        // antes de procurar IDs para não apresentar limite de requisições
        // como se fosse uma falha na estrutura do catálogo.
        val apiError = runCatching {
            val json = JSONObject(trimmed)
            val isError = json.optBoolean("error", false) ||
                json.optString("status").equals("error", ignoreCase = true)
            if (isError) {
                json.optString("message").takeIf { it.isNotBlank() && it != "null" }
            } else {
                null
            }
        }.getOrNull()

        if (apiError != null) {
            val isRateLimit = apiError.contains("muitas requisições", ignoreCase = true) ||
                apiError.contains("too many", ignoreCase = true) ||
                apiError.contains("rate limit", ignoreCase = true)

            if (isRateLimit) {
                cachedCatalogRetryAfter = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
                throw ErrorLoadingException(
                    "A API SuperFlix limitou temporariamente as consultas: " + apiError +
                        " Aguarde alguns segundos antes de atualizar. Nenhuma nova tentativa automática foi feita."
                )
            }

            throw ErrorLoadingException("A API SuperFlix retornou um erro: " + apiError)
        }
        if (trimmed.startsWith("<!doctype", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.contains("cf-chl-", ignoreCase = true) ||
            trimmed.contains("Just a moment", ignoreCase = true)
        ) {
            throw ErrorLoadingException(
                "A API respondeu com uma página HTML em vez de JSON. " +
                    "O endpoint público não liberou a lista para esta requisição. " +
                    "Não foi usado catálogo de teste nem lista alternativa."
            )
        }

        val entries = parseCatalogJson(raw)
        if (entries.isEmpty()) {
            val sample = trimmed.replace(Regex("\\s+"), " ").take(180)
            throw ErrorLoadingException(
                "A API respondeu, mas não encontramos IDs TMDB no JSON. " +
                    "Início da resposta: " + sample
            )
        }

        cachedCatalog = entries
        cachedCatalogAt = now
        return entries
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val catalog = fetchApiEntries()
        val pageSize = 24
        val currentPage = page.coerceAtLeast(1)
        val start = (currentPage - 1) * pageSize
        val visible = catalog.drop(start).take(pageSize)
        val responses = visible.map { toSearchResponse(it) }
        val hasMore = catalog.size > start + pageSize

        return newHomePageResponse(request.name, responses, hasMore)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()

        return fetchApiEntries()
            .filter { entry ->
                entry.id.contains(term, ignoreCase = true) ||
                    entry.title?.contains(term, ignoreCase = true) == true
            }
            .take(100)
            .map { toSearchResponse(it) }
    }

    private fun pageTitle(document: Document?,fallback: String): String {
        return document?.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.title()?.substringBefore("|")?.trim()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun pagePoster(document: Document?): String? {
        val meta=document?.selectFirst("meta[property=og:image]")?.attr("content")
        return normalizedPoster(meta) ?: elementImage(document?.selectFirst("main img, article img, img[alt]"))
    }

    private fun pagePlot(document: Document?): String? {
        return document?.selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: document?.selectFirst("main p, article p")?.text()?.trim()?.takeIf { it.length>30 }
    }

    override suspend fun load(url: String): LoadResponse {
        val canonicalUrl=absoluteUrl(url) ?: throw ErrorLoadingException("Endereço de conteúdo inválido.")
        val id=extractCatalogId(canonicalUrl) ?: throw ErrorLoadingException("ID de conteúdo SuperFlix inválido.")
        val document=runCatching { app.get(canonicalUrl,referer=mainUrl).document }.getOrNull()
        val title=pageTitle(document,"Filme"+" "+id)
        val poster=pagePoster(document)
        val plot=pagePlot(document)
        val year=yearFrom(title,canonicalUrl,document?.text())
        if ("movie"=="movie") {
            return newMovieLoadResponse(title,canonicalUrl,TvType.CustomMedia,canonicalUrl) {
                posterUrl=poster; this.year=year; this.plot=plot
            }
        }
        val episodes=collectEpisodes(id,document,poster)
        return if ("movie"=="anime") {
            newAnimeLoadResponse(title,canonicalUrl,TvType.Anime) {
                posterUrl=poster; this.year=year; this.plot=plot
                addEpisodes(DubStatus.Subbed,episodes)
            }
        } else {
            newTvSeriesLoadResponse(title,canonicalUrl,TvType.TvSeries,episodes) {
                posterUrl=poster; this.year=year; this.plot=plot
            }
        }
    }

    private suspend fun collectEpisodes(id: String,detail: Document?,poster: String?): List<Episode> {
        val episodes=mutableListOf<Episode>()
        if (detail!=null) episodes.addAll(extractEpisodes(detail,id,poster))
        if (episodes.isNotEmpty()) return episodes.distinctBy { it.data }
        val seasons=detail?.let { extractSeasonNumbers(it,id) }.orEmpty().ifEmpty { listOf(1) }
        for (season in seasons.distinct().take(24)) {
            val seasonUrl=BASE_URL+"/serie/"+id+"/"+season
            val seasonDocument=runCatching { app.get(seasonUrl,referer=mainUrl).document }.getOrNull() ?: continue
            episodes.addAll(extractEpisodes(seasonDocument,id,poster))
        }
        return episodes.distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.season?:1 }.thenBy { it.episode?:Int.MAX_VALUE })
    }

    private fun extractSeasonNumbers(document: Document,id: String): List<Int> {
        val html=document.html().replace("\\/","/").replace("&amp;","&")
        val pattern=Regex("/serie/"+Regex.escape(id)+"/(\\d+)(?:/|[?\"'])")
        return pattern.findAll(html).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.distinct().sorted().toList()
    }

    private fun extractEpisodes(document: Document,id: String,poster: String?): List<Episode> {
        val found=linkedMapOf<String,Episode>()
        val pathPattern=Regex("^/serie/"+Regex.escape(id)+"/(\\d+)/(\\d+)/?$",RegexOption.IGNORE_CASE)
        document.select("a[href]").forEach { anchor ->
            val absolute=absoluteUrl(anchor.attr("href")) ?: return@forEach
            val uri=runCatching { Uri.parse(absolute) }.getOrNull() ?: return@forEach
            val match=pathPattern.matchEntire(uri.path.orEmpty()) ?: return@forEach
            val season=match.groupValues[1].toIntOrNull() ?: return@forEach
            val number=match.groupValues[2].toIntOrNull() ?: return@forEach
            val label=sequenceOf(anchor.attr("title"),anchor.attr("aria-label"),
                anchor.selectFirst("[class*=title], [class*=name], span")?.text(),anchor.text())
                .firstOrNull { !it.isNullOrBlank() }?.replace(Regex("\\s+")," ")?.trim()
                ?.takeIf { it.isNotBlank() } ?: ("Temporada "+season+" · Episódio "+number)
            val episodeUrl=BASE_URL+"/serie/"+id+"/"+season+"/"+number
            found[episodeUrl]=newEpisode(episodeUrl) {
                name=label; this.season=season; episode=number
                posterUrl=elementImage(anchor.selectFirst("img"))?:poster
            }
        }
        val normalizedHtml=document.html().replace("\\/","/").replace("&amp;","&")
        val embeddedPattern=Regex("/serie/"+Regex.escape(id)+"/(\\d+)/(\\d+)")
        embeddedPattern.findAll(normalizedHtml).forEach { match ->
            val season=match.groupValues[1].toIntOrNull() ?: return@forEach
            val number=match.groupValues[2].toIntOrNull() ?: return@forEach
            val episodeUrl=BASE_URL+"/serie/"+id+"/"+season+"/"+number
            if (!found.containsKey(episodeUrl)) found[episodeUrl]=newEpisode(episodeUrl) {
                name="Temporada "+season+" · Episódio "+number
                this.season=season; episode=number; posterUrl=poster
            }
        }
        return found.values.sortedWith(compareBy<Episode> { it.season?:1 }.thenBy { it.episode?:Int.MAX_VALUE })
    }

    private fun iframePlayerPathAllowed(rawUrl: String): Boolean {
        val candidate=absoluteUrl(rawUrl) ?: return false
        val uri=runCatching { Uri.parse(candidate) }.getOrNull() ?: return false
        val hostAllowed=uri.host?.lowercase()?.let { it in allowedPlayerHosts } ?: false
        return uri.scheme=="https" && hostAllowed && playerPathRegex.matchEntire(uri.path.orEmpty())!=null
    }

    private fun iframeHtml(embedUrl: String): String {
        val safeUrl = embedUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")

        return """
            <!doctype html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        min-height: 100%;
                        background: #000;
                        overflow: hidden;
                    }
                    body {
                        height: 100vh;
                        height: 100dvh;
                    }
                    iframe {
                        display: block;
                        width: 100%;
                        height: 100vh;
                        height: 100dvh;
                        min-height: 100vh;
                        min-height: 100dvh;
                        max-width: 100%;
                        border: 0;
                        border-radius: 0;
                    }
                </style>
            </head>
            <body>
                <iframe
                    src="$safeUrl"
                    allow="autoplay *; encrypted-media *; picture-in-picture *; fullscreen *; clipboard-write *; accelerometer *; gyroscope *"
                    allowfullscreen
                    webkitallowfullscreen
                    mozallowfullscreen
                    frameborder="0">
                </iframe>
                <script>
                    (function () {
                        function say(message) {
                            try { console.log("[SF-DIAG] " + String(message).slice(0, 180)); } catch (e) {}
                        }

                        var frame = document.querySelector("iframe");
                        if (!frame) {
                            say("iframe ausente no HTML de incorporação");
                            return;
                        }

                        function describe(element) {
                            if (!element) return "(elemento não identificado)";
                            var label = (element.innerText || element.textContent || "")
                                .replace(/\s+/g, " ").trim().slice(0, 45);
                            var href = element.getAttribute ? (element.getAttribute("href") || "") : "";
                            if (href && href !== "javascript:void(0)") {
                                try {
                                    var parsed = new URL(href, frame.src);
                                    href = parsed.host + parsed.pathname;
                                } catch (e) {
                                    href = "(link não disponível)";
                                }
                            }

                            function visualState(node) {
                                try {
                                    var style = doc.defaultView.getComputedStyle(node);
                                    var rect = node.getBoundingClientRect();
                                    var classText = typeof node.className === "string"
                                        ? node.className.replace(/\\s+/g, ".").slice(0, 55)
                                        : "";
                                    return node.tagName + (classText ? "." + classText : "") +
                                        "{" + style.display + "," + style.visibility +
                                        ",op=" + style.opacity + ",pe=" + style.pointerEvents +
                                        ",area=" + Math.round(rect.width) + "x" + Math.round(rect.height) +
                                        (node.hidden ? ",hidden" : "") + "}";
                                } catch (e) {
                                    return node.tagName + "{estado indisponível}";
                                }
                            }

                            var state = visualState(element);
                            var parent = element.parentElement;
                            var parentDepth = 0;
                            while (parent && parentDepth < 3) {
                                state += " > " + visualState(parent);
                                parent = parent.parentElement;
                                parentDepth++;
                            }
                            return element.tagName + " texto=" + label +
                                " link=" + (href || "(sem link)") + " estado=" + state;
                        }

                        say("iframe solicitado: " + frame.src);

                        function attachDiagnostics() {
                            say("iframe carregado: " + frame.src);
                            try {
                                var doc = frame.contentDocument;
                                if (!doc || !doc.documentElement) {
                                    say("documento interno do iframe indisponível");
                                    return;
                                }

                                var path = "/";
                                try { path = frame.contentWindow.location.pathname; } catch (e) {
                                    path = "(caminho protegido por origem diferente)";
                                }
                                say("documento interno acessível; caminho=" + path);

                                if (doc.__sfDiagInstalled) return;
                                doc.__sfDiagInstalled = true;

                                doc.addEventListener("click", function (event) {
                                    var target = event.target;
                                    if (target && target.nodeType === 3) target = target.parentElement;
                                    var clickable = target && target.closest
                                        ? target.closest("a,button,[role='button'],[onclick]")
                                        : target;
                                    say("clique dentro do iframe: " + describe(clickable || target));
                                }, true);

                                var seen = new Set();
                                function reportPlayerControls() {
                                    doc.querySelectorAll("a,button,[role='button'],[onclick]").forEach(function (element) {
                                        var label = (element.innerText || element.textContent || "")
                                            .replace(/\s+/g, " ").trim();
                                        if (!/dublado|legendado|servidor|principal|alternativo/i.test(label)) return;
                                        var description = describe(element);
                                        if (!seen.has(description)) {
                                            seen.add(description);
                                            say("controle encontrado: " + description);
                                        }
                                    });
                                }

                                reportPlayerControls();

                                // Diagnóstico de reprodução: registra eventos e metadados
                                // técnicos, mas nunca grava cookies, tokens, cabeçalhos ou URLs
                                // completos de mídia. Iframes de outra origem permanecem isolados.
                                var mediaSeen = new WeakSet();
                                function safeHost(raw) {
                                    try { return new URL(raw, doc.location.href).host || "(sem host)"; }
                                    catch (e) { return "(origem indisponível)"; }
                                }
                                function inspectMedia() {
                                    doc.querySelectorAll("video, audio").forEach(function (media) {
                                        if (mediaSeen.has(media)) return;
                                        mediaSeen.add(media);
                                        function state(eventName) {
                                            var source = media.currentSrc || media.src || "";
                                            say("[SF-PLAYER] " + eventName +
                                                " tag=" + media.tagName +
                                                " host=" + (source ? safeHost(source) : "(fonte ainda não definida)") +
                                                " readyState=" + media.readyState +
                                                " networkState=" + media.networkState +
                                                " paused=" + media.paused +
                                                " duration=" + (Number.isFinite(media.duration) ? Math.round(media.duration) : "indefinida") +
                                                " error=" + (media.error ? media.error.code : "nenhum"));
                                        }
                                        ["loadstart", "loadedmetadata", "loadeddata", "canplay",
                                         "playing", "waiting", "stalled", "pause", "ended", "error"].forEach(function (name) {
                                            media.addEventListener(name, function () { state("evento=" + name); });
                                        });
                                        state("elemento de mídia encontrado");
                                    });
                                }
                                inspectMedia();

                                var pending = false;
                                var observer = new MutationObserver(function () {
                                    if (pending) return;
                                    pending = true;
                                    setTimeout(function () {
                                        pending = false;
                                        reportPlayerControls();
                                        inspectMedia();
                                    }, 120);
                                });
                                observer.observe(doc.documentElement, {
                                    childList: true,
                                    subtree: true,
                                    attributes: true,
                                    attributeFilter: ["class", "style", "hidden", "aria-hidden", "disabled"]
                                });
                                say("monitorando mudanças de visibilidade dos botões e servidores.");
                            } catch (error) {
                                say("não foi possível observar o conteúdo interno: " +
                                    (error && error.name ? error.name : "erro"));
                            }
                        }

                        frame.addEventListener("load", function () {
                            setTimeout(attachDiagnostics, 0);
                        });
                        if (frame.contentDocument && frame.contentDocument.readyState === "complete") {
                            setTimeout(attachDiagnostics, 0);
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showEmbeddedPlayer(sourcePageUrl: String): Boolean {
        val hostActivity = CommonActivity.activity ?: return false
        if (hostActivity.isFinishing || hostActivity.isDestroyed) return false

        hostActivity.runOnUiThread {
            if (hostActivity.isFinishing || hostActivity.isDestroyed) return@runOnUiThread

            val webView = WebView(hostActivity)
            val progress = ProgressBar(hostActivity)
            val root = FrameLayout(hostActivity).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    progress,
                    FrameLayout.LayoutParams(52, 52, Gravity.CENTER)
                )
            }

            val popupViews = mutableListOf<WebView>()

            val dialog = Dialog(hostActivity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(root)
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(false)

            var wrapperLoaded = false
            var scanStarted = false
            var fullscreenDialog: Dialog? = null
            var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
            val handler = Handler(Looper.getMainLooper())

            // Painel temporário de diagnóstico. Mostra erros de JavaScript e
            // códigos HTTP sem expor query strings, cookies ou tokens.
            val diagnosticText = TextView(hostActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0xD9000000.toInt())
                setTextIsSelectable(true)
            }
            val diagnosticScroll = ScrollView(hostActivity).apply {
                visibility = View.GONE
                addView(
                    diagnosticText,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            val density = hostActivity.resources.displayMetrics.density
            val diagnosticHeight = (184 * density).toInt()
            root.addView(
                diagnosticScroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    diagnosticHeight,
                    Gravity.BOTTOM
                )
            )
            // O painel começa recolhido para não cobrir os botões do player.
            val diagnosticToggle = TextView(hostActivity).apply {
                text = "LOG"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(14, 10, 14, 10)
                setBackgroundColor(0xDD202020.toInt())
                elevation = 8f * density
                isClickable = true
                isFocusable = false
                setOnClickListener {
                    val showing = diagnosticScroll.visibility != View.VISIBLE
                    diagnosticScroll.visibility = if (showing) View.VISIBLE else View.GONE
                    text = if (showing) "FECHAR LOG" else "LOG"
                    if (showing) {
                        diagnosticScroll.bringToFront()
                        diagnosticScroll.post { diagnosticScroll.fullScroll(View.FOCUS_DOWN) }
                        bringToFront()
                    }
                }
            }
            root.addView(
                diagnosticToggle,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (42 * density).toInt(),
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = (10 * density).toInt()
                    rightMargin = (10 * density).toInt()
                }
            )

            // Botão de retorno visível somente quando uma janela nova é aberta.
            // Ele fecha apenas a janela ativa e retorna ao player original.
            val popupCloseButton = TextView(hostActivity).apply {
                text = "VOLTAR AO PLAYER"
                visibility = View.GONE
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(14, 10, 14, 10)
                setBackgroundColor(0xDD202020.toInt())
                elevation = 10f * density
                isClickable = true
                isFocusable = true
            }
            root.addView(
                popupCloseButton,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (42 * density).toInt(),
                    Gravity.TOP or Gravity.START
                ).apply {
                    topMargin = (10 * density).toInt()
                    leftMargin = (10 * density).toInt()
                }
            )

            fun safeAddress(raw: String?): String {
                if (raw.isNullOrBlank()) return "(endereço indisponível)"
                val uri = runCatching { Uri.parse(raw) }.getOrNull()
                    ?: return "(endereço inválido)"
                val host = uri.host ?: return "(endereço sem domínio)"
                return host + (uri.encodedPath ?: "/")
            }

            fun logDiagnostic(message: String) {
                val safeMessage = message
                    .replace(Regex("(?i)(token|cookie|authorization)\\s*[:=]\\s*[^\\s,;]+")) { match ->
                        "${match.groupValues[1]}=[oculto]"
                    }
                    .take(260)
                diagnosticText.append("• $safeMessage\n")
                val maxChars = 9000
                if (diagnosticText.length() > maxChars) {
                    diagnosticText.setText(diagnosticText.text.takeLast(7000))
                }
                if (diagnosticScroll.visibility == View.VISIBLE) {
                    diagnosticScroll.post { diagnosticScroll.fullScroll(View.FOCUS_DOWN) }
                }
            }

            @SuppressLint("SetJavaScriptEnabled")
            fun configurePlayerWebView(target: WebView, reference: WebView? = null) {
                target.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    // A configuração recomendada pela Cloudflare para WebView móvel.
                    loadWithOverviewMode = true
                    textZoom = 100
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    cacheMode = WebSettings.LOAD_DEFAULT
                    safeBrowsingEnabled = true
                    // Pop-ups mantêm exatamente o mesmo User-Agent da janela principal.
                    if (reference != null) {
                        userAgentString = reference.settings.userAgentString
                    }
                }
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(target, true)
                    flush()
                }
            }

            fun closeTopPopup() {
                val popup = popupViews.lastOrNull() ?: return
                root.removeView(popup)
                popup.stopLoading()
                popup.destroy()
                popupViews.remove(popup)

                val previous = popupViews.lastOrNull() ?: webView
                previous.visibility = View.VISIBLE
                previous.bringToFront()

                popupCloseButton.visibility =
                    if (popupViews.isEmpty()) View.GONE else View.VISIBLE
                if (diagnosticScroll.visibility == View.VISIBLE) {
                    diagnosticScroll.bringToFront()
                }
                if (popupCloseButton.visibility == View.VISIBLE) {
                    popupCloseButton.bringToFront()
                }
                diagnosticToggle.bringToFront()
                logDiagnostic("Janela fechada pelo usuário; retorno à tela anterior.")
            }

            popupCloseButton.setOnClickListener { closeTopPopup() }

            fun diagnosticWebViewClient(
                onFinished: (String?) -> Unit = {}
            ): WebViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (isBlockedAdNavigation(uri)) {
                        logDiagnostic("Redirecionamento publicitário bloqueado: ${safeAddress(uri.toString())}")
                        val activeView = view
                        if (activeView != null && popupViews.contains(activeView)) {
                            handler.post {
                                if (popupViews.lastOrNull() === activeView) {
                                    closeTopPopup()
                                }
                            }
                        }
                        return true
                    }

                    // Mantém about:blank/srcdoc disponíveis para o funcionamento dos iframes.
                    val scheme = uri.scheme?.lowercase()
                    if (scheme != "http" && scheme != "https" && scheme != "about") {
                        logDiagnostic("Navegação externa/esquema não suportado bloqueado.")
                        val activeView = view
                        if (activeView != null && popupViews.contains(activeView)) {
                            handler.post {
                                if (popupViews.lastOrNull() === activeView) closeTopPopup()
                            }
                        }
                        return true
                    }

                    logDiagnostic(
                        "Navegação permitida: ${request.method} ${safeAddress(uri.toString())}"
                    )
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (!isBlockedAdResource(uri)) return null

                    handler.post {
                        logDiagnostic("Recurso de anúncio/rastreamento bloqueado: ${safeAddress(uri.toString())}")
                    }
                    return android.webkit.WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "No Content",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    logDiagnostic("Abrindo: ${safeAddress(url)}")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    logDiagnostic("Página carregada: ${safeAddress(url)}")
                    onFinished(url)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: android.webkit.RenderProcessGoneDetail?
                ): Boolean {
                    val didCrash = detail?.didCrash()
                    handler.post {
                        if (view != null) {
                            val wasMainWebView = view === webView
                            (view.parent as? ViewGroup)?.removeView(view)
                            popupViews.remove(view)
                            runCatching { view.destroy() }
                            logDiagnostic(
                                "Processo de renderização WebView encerrado; " +
                                    "principal=$wasMainWebView; didCrash=$didCrash."
                            )
                            if (wasMainWebView) {
                                progress.visibility = View.GONE
                                Toast.makeText(
                                    hostActivity,
                                    "O mecanismo da página foi encerrado. Feche e abra o player novamente.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val previous = popupViews.lastOrNull() ?: webView
                                previous.visibility = View.VISIBLE
                                previous.bringToFront()
                                popupCloseButton.visibility =
                                    if (popupViews.isEmpty()) View.GONE else View.VISIBLE
                                popupCloseButton.bringToFront()
                                diagnosticToggle.bringToFront()
                            }
                        }
                    }
                    return true
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val status = errorResponse?.statusCode ?: return
                    if (status >= 400 && request != null) {
                        logDiagnostic(
                            "HTTP $status ${request.method} ${safeAddress(request.url.toString())}"
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    val failedHost = request?.url?.host.orEmpty().lowercase()
                    val isCloudflareResource = failedHost == "challenges.cloudflare.com" ||
                        failedHost.endsWith(".challenges.cloudflare.com")
                    if (request != null && error != null &&
                        (request.isForMainFrame || isCloudflareResource || error.errorCode != -2)
                    ) {
                        logDiagnostic(
                            "ERRO WebView ${error.errorCode}: ${error.description} — ${safeAddress(request.url.toString())}"
                        )
                    }
                }
            }

            logDiagnostic("Diagnóstico SuperFlix v3.2 iniciado.")

            fun closeFullscreen() {
                val activeDialog = fullscreenDialog
                fullscreenDialog = null
                activeDialog?.setOnDismissListener(null)
                if (activeDialog?.isShowing == true) activeDialog.dismiss()
                webView.visibility = View.VISIBLE
                val callback = fullscreenCallback
                fullscreenCallback = null
                callback?.onCustomViewHidden()
            }

            fun parseEmbedCandidates(rawResult: String?): List<String> {
                if (rawResult.isNullOrBlank() || rawResult == "null") return emptyList()
                val jsonString = runCatching {
                    JSONTokener(rawResult).nextValue() as? String
                }.getOrNull() ?: return emptyList()
                val array = runCatching { JSONArray(jsonString) }.getOrNull()
                    ?: return emptyList()
                return (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }

            fun loadEmbed(embedUrl: String) {
                if (wrapperLoaded || !dialog.isShowing) return
                wrapperLoaded = true
                val embedUri = Uri.parse(embedUrl)
                val embedBaseUrl = "${embedUri.scheme}://${embedUri.authority}/"
                webView.loadDataWithBaseURL(
                    embedBaseUrl,
                    iframeHtml(embedUrl),
                    "text/html",
                    "UTF-8",
                    null
                )
            }

            fun scanForEmbed(attempt: Int) {
                if (!dialog.isShowing || wrapperLoaded) return
                webView.evaluateJavascript(findIframeSourcesScript) { result ->
                    if (!dialog.isShowing || wrapperLoaded) return@evaluateJavascript
                    val embedUrl = parseEmbedCandidates(result)
                        .firstNotNullOfOrNull { validatedEmbedUrl(it) }

                    if (embedUrl != null) {
                        logDiagnostic("Iframe localizado: ${safeAddress(embedUrl)}")
                        loadEmbed(embedUrl)
                    } else if (attempt < 14) {
                        handler.postDelayed({ scanForEmbed(attempt + 1) }, 650L)
                    } else {
                        logDiagnostic("Iframe não localizado após 15 tentativas.")
                        progress.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                        Toast.makeText(
                            hostActivity,
                            "Não foi possível localizar o código do iframe nesta página.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            val activeWebViewPackage = WebView.getCurrentWebViewPackage()
            logDiagnostic(
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); " +
                    "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            logDiagnostic(
                "WebView ativo: ${activeWebViewPackage?.packageName ?: "não identificado"} " +
                    "versão ${activeWebViewPackage?.versionName ?: "desconhecida"} " +
                    "(code ${activeWebViewPackage?.longVersionCode ?: -1L})"
            )
            configurePlayerWebView(webView)
            logDiagnostic(
                "Configuração: JS=${webView.settings.javaScriptEnabled}; " +
                    "DOM=${webView.settings.domStorageEnabled}; " +
                    "cookies=${CookieManager.getInstance().acceptCookie()}; " +
                    "terceiros=${CookieManager.getInstance().acceptThirdPartyCookies(webView)}; " +
                    "overview=${webView.settings.loadWithOverviewMode}; " +
                    "mixedContent=${webView.settings.mixedContentMode}"
            )
            // Mantém o User-Agent nativo do provedor; não falsifica navegador ou dispositivo.
            logDiagnostic("User-Agent nativo: ${webView.settings.userAgentString}")

            webView.setBackgroundColor(Color.BLACK)
            webView.visibility = View.INVISIBLE
            // A URL /filme/ID já é a página de incorporação/reprodução.
            // Exibi-la diretamente evita procurar um segundo iframe que não existe.
            webView.webViewClient = diagnosticWebViewClient {
                webView.visibility = View.VISIBLE
                progress.visibility = View.GONE
                logDiagnostic("Página HTML de incorporação carregada; o iframe do player está sendo exibido.")
            }
            webView.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    logDiagnostic("Toque recebido pela WebView.")
                }
                false
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    if (consoleMessage != null) {
                        logDiagnostic(
                            "JS ${consoleMessage.messageLevel()}: ${consoleMessage.message().take(220)} — ${safeAddress(consoleMessage.sourceId())}:${consoleMessage.lineNumber()}"
                        )
                    }
                    return true
                }
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    if (!dialog.isShowing || resultMsg == null) return false
                    logDiagnostic("O player solicitou uma nova janela (popup).")

                    // Abre pop-ups do próprio fluxo do player dentro do mesmo
                    // diálogo, em vez de deixar o toque sem resposta.
                    val popup = WebView(hostActivity)
                    configurePlayerWebView(popup, webView)
                    popup.setBackgroundColor(Color.BLACK)
                    popup.webViewClient = diagnosticWebViewClient {
                        progress.visibility = View.GONE
                    }
                    popup.webChromeClient = this
                    root.addView(
                        popup,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    popup.bringToFront()
                    progress.bringToFront()
                    popupViews.add(popup)
                    popupCloseButton.visibility = View.VISIBLE
                    popupCloseButton.bringToFront()
                    if (diagnosticScroll.visibility == View.VISIBLE) {
                        diagnosticScroll.bringToFront()
                    }
                    diagnosticToggle.bringToFront()
                    popupCloseButton.bringToFront()

                    val transport = resultMsg.obj as? WebView.WebViewTransport
                        ?: run {
                            root.removeView(popup)
                            popup.destroy()
                            popupViews.remove(popup)
                            return false
                        }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    val popup = window ?: return
                    root.removeView(popup)
                    popup.stopLoading()
                    popup.destroy()
                    popupViews.remove(popup)
                    val previous = popupViews.lastOrNull() ?: webView
                    previous.visibility = View.VISIBLE
                    previous.bringToFront()
                    popupCloseButton.visibility =
                        if (popupViews.isEmpty()) View.GONE else View.VISIBLE
                    if (diagnosticScroll.visibility == View.VISIBLE) {
                        diagnosticScroll.bringToFront()
                    }
                    if (popupCloseButton.visibility == View.VISIBLE) {
                        popupCloseButton.bringToFront()
                    }
                    diagnosticToggle.bringToFront()
                }

                override fun onShowCustomView(
                    view: View?,
                    callback: CustomViewCallback?
                ) {
                    if (view == null) return
                    if (fullscreenDialog != null) {
                        callback?.onCustomViewHidden()
                        return
                    }

                    webView.visibility = View.GONE
                    fullscreenCallback = callback
                    val fullDialog = Dialog(
                        hostActivity,
                        android.R.style.Theme_Black_NoTitleBar_Fullscreen
                    )
                    fullscreenDialog = fullDialog
                    fullDialog.setContentView(view)
                    fullDialog.setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK &&
                            event.action == KeyEvent.ACTION_UP
                        ) {
                            closeFullscreen()
                            true
                        } else {
                            false
                        }
                    }
                    fullDialog.show()
                    fullDialog.window?.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                }

                override fun onHideCustomView() {
                    closeFullscreen()
                }
            }

            dialog.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    if (fullscreenDialog != null) {
                        closeFullscreen()
                    } else {
                        val activePopup = popupViews.lastOrNull()
                        if (activePopup != null) {
                            if (activePopup.canGoBack()) {
                                activePopup.goBack()
                            } else {
                                closeTopPopup()
                            }
                        } else if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            dialog.dismiss()
                        }
                    }
                    true
                } else {
                    false
                }
            }

            dialog.setOnDismissListener {
                fullscreenDialog?.setOnDismissListener(null)
                fullscreenDialog?.dismiss()
                fullscreenDialog = null
                handler.removeCallbacksAndMessages(null)
                popupViews.toList().forEach { popup ->
                    popup.stopLoading()
                    (popup.parent as? ViewGroup)?.removeView(popup)
                    popup.destroy()
                }
                popupViews.clear()
                runCatching { webView.stopLoading() }
                (webView.parent as? ViewGroup)?.removeView(webView)
                runCatching { webView.destroy() }
            }

            dialog.show()
            dialog.window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
                addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            logDiagnostic("Carregando o mesmo formato do t.html: iframe src=${safeAddress(sourcePageUrl)}")
            loadEmbed(sourcePageUrl)
        }

        return true
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        val sourcePageUrl=absoluteUrl(data) ?: return false
        if (!iframePlayerPathAllowed(sourcePageUrl) || !Regex("^/filme/[^/]+/?$", RegexOption.IGNORE_CASE).matches(Uri.parse(sourcePageUrl).path.orEmpty())) return false
        return showEmbeddedPlayer(sourcePageUrl)
    }
}
