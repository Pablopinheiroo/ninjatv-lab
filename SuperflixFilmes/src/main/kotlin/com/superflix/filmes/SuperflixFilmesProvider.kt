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
import android.widget.ScrollView
import android.widget.TextView
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
        private const val DOCUMENTED_TEST_ID = "1135465"

        // Domínios oficiais/aliases conhecidos do SuperFlix. O domínio da
        // página pode redirecionar, enquanto o iframe já usa o domínio atual.
        private val allowedPlayerHosts = setOf(
            "superflixapi.boats",
            "superflixapi.monster",
            "superflixapi.fun",
            "superflixapi.baby",
            "superflixapi.pro"
        )

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
        val knownHost = uri.host
            ?.lowercase()
            ?.let { it in allowedPlayerHosts }
            ?: false

        // Mantém HTTPS e o caminho esperado do player. Aceita os aliases
        // conhecidos porque o iframe pode usar domínio diferente da página.
        if (uri.scheme != "https" ||
            !knownHost ||
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
            "Filme de teste SuperFlix — ID 1135465",
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
                                var pending = false;
                                var observer = new MutationObserver(function () {
                                    if (pending) return;
                                    pending = true;
                                    setTimeout(function () {
                                        pending = false;
                                        reportPlayerControls();
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

            fun diagnosticWebViewClient(
                onFinished: (String?) -> Unit = {}
            ): WebViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    if (request != null) {
                        logDiagnostic(
                            "Navegação solicitada: ${request.method} ${safeAddress(request.url.toString())}"
                        )
                    }
                    return false
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
                    if (request != null && error != null &&
                        (request.isForMainFrame || error.errorCode != -2)
                    ) {
                        logDiagnostic(
                            "ERRO WebView ${error.errorCode}: ${error.description} — ${safeAddress(request.url.toString())}"
                        )
                    }
                }
            }

            logDiagnostic("Diagnóstico SuperFlix v2.7 iniciado.")

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

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                // Alguns botões de idioma do player abrem o parceiro em uma
                // nova janela. Sem suporte a múltiplas janelas, o toque não faz nada.
                setSupportMultipleWindows(true)
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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
                    popup.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
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
                    if (diagnosticScroll.visibility == View.VISIBLE) {
                        diagnosticScroll.bringToFront()
                    }
                    diagnosticToggle.bringToFront()
                    popupViews.add(popup)

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
                    previous.bringToFront()
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
                popupViews.toList().forEach { popup ->
                    popup.stopLoading()
                    (popup.parent as? ViewGroup)?.removeView(popup)
                    popup.destroy()
                }
                popupViews.clear()
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
