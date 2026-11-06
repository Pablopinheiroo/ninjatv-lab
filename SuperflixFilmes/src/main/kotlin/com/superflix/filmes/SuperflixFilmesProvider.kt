package com.superflix.filmes

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
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
import android.widget.Toast
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONTokener
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Catálogo SuperFlix. A reprodução utiliza o iframe oferecido pela própria
 * página do conteúdo, exibido em uma WebView dentro do NinjaTV.
 */
class SuperflixFilmesProvider : MainAPI() {
    override var mainUrl = BASE_URL
    override var name = "Superflix Filmes"
    override var lang = "pt-BR"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie)

    companion object {
        const val BASE_URL = "https://superflixapi.boats"
        private const val DOCUMENTED_TEST_ID = "tt0068646"

        private val moviePathRegex = Regex(
            "^/filme/([^/]+)/?$",
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

    private fun validatedEmbedUrl(raw: String?): String? {
        val candidate = absoluteUrl(raw) ?: return null
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val expectedHost = Uri.parse(mainUrl).host

        if (uri.scheme != "https" ||
            !uri.host.equals(expectedHost, ignoreCase = true) ||
            moviePathRegex.matchEntire(uri.path.orEmpty()) == null
        ) {
            return null
        }
        return candidate
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
            base + separator + "page=" + page
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
        val searchUrl = "$mainUrl/lista?category=pesquisa&q=$encoded&format=html"
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

        val document = runCatching {
            app.get(canonicalUrl, referer = mainUrl).document
        }.getOrNull()

        val title = pageTitle(document, "Filme $id")
        val poster = pagePoster(document)
        val plot = pagePlot(document)
        val year = yearFrom(title, canonicalUrl, document?.text())

        // CustomMedia faz o clique em Assistir ser encaminhado ao loadLinks
        // da extensão, sem abrir o player de vídeo padrão.
        return newMovieLoadResponse(
            title,
            canonicalUrl,
            TvType.CustomMedia,
            canonicalUrl
        ) {
            posterUrl = poster
            this.year = year
            this.plot = plot
        }
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
                        min-height: 100%;
                        background: #000;
                        overflow: hidden;
                    }
                    iframe {
                        display: block;
                        width: 100%;
                        aspect-ratio: 16 / 9;
                        height: auto;
                        max-width: 100%;
                        border: 0;
                        border-radius: 12px;
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
                    frameborder="0"
                    scrolling="no">
                </iframe>
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
                webView.loadDataWithBaseURL(
                    "$mainUrl/",
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
                        loadEmbed(embedUrl)
                    } else if (attempt < 14) {
                        handler.postDelayed({ scanForEmbed(attempt + 1) }, 650L)
                    } else {
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

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.setBackgroundColor(Color.BLACK)
            webView.visibility = View.INVISIBLE
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (wrapperLoaded) {
                        webView.visibility = View.VISIBLE
                        progress.visibility = View.GONE
                    } else if (!scanStarted) {
                        scanStarted = true
                        scanForEmbed(0)
                    }
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
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
                    } else if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        dialog.dismiss()
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
                webView.stopLoading()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
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
            webView.loadUrl(sourcePageUrl)
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

        val sourcePageUrl = absoluteUrl(data) ?: return false
        val uri = runCatching { Uri.parse(sourcePageUrl) }.getOrNull() ?: return false

        if (uri.scheme != "https" ||
            !uri.host.equals(Uri.parse(mainUrl).host, ignoreCase = true) ||
            moviePathRegex.matchEntire(uri.path.orEmpty()) == null
        ) {
            return false
        }

        return showEmbeddedPlayer(sourcePageUrl)
    }
}
