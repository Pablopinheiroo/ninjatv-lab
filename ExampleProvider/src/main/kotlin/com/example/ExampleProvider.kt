package com.example

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

private data class EducationalMovie(
    val id: String,
    val title: String,
    val year: Int,
    val description: String,
    val durationMinutes: Int,
    val videoUrl: String
)

/**
 * Educational provider example.
 *
 * Demonstrates homepage, search, details and playback using an openly licensed
 * film made available as public test media for the ExoPlayer project. No third-party streaming sites are
 * scraped or queried.
 */
class ExampleProvider : MainAPI() {
    override var mainUrl = "https://peach.blender.org"
    override var name = "NinjaTV Lab - Filmes Abertos"
    override var lang = "pt-BR"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "open-movies" to "Filmes abertos para teste"
    )

    private val educationalMovies = listOf(
        EducationalMovie(
            id = "big-buck-bunny",
            title = "Big Buck Bunny",
            year = 2008,
            description = "Curta-metragem de animação produzido pela Blender Foundation. " +
                "Este item é usado como demonstração técnica do catálogo, da busca, " +
                "da página de detalhes e do player.",
            durationMinutes = 10,
            videoUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
        )
    )

    private fun EducationalMovie.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title,
            url = "$mainUrl/educational-demo/$id",
            type = TvType.Movie,
            fix = false
        ) {
            year = this@toSearchResponse.year
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val results = if (page == 1) {
            educationalMovies.map { it.toSearchResponse() }
        } else {
            emptyList()
        }

        return newHomePageResponse(
            name = request.name.ifBlank { "Filmes abertos para teste" },
            list = results,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        return educationalMovies
            .filter { it.title.contains(normalizedQuery, ignoreCase = true) }
            .map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val movieId = url.substringAfterLast("/")
        val movie = educationalMovies.firstOrNull { it.id == movieId }
            ?: throw ErrorLoadingException("Item educacional não encontrado.")

        return newMovieLoadResponse(
            name = movie.title,
            url = url,
            type = TvType.Movie,
            dataUrl = movie.id
        ) {
            year = movie.year
            duration = movie.durationMinutes
            plot = movie.description +
                "\n\nLicença: Creative Commons Attribution 3.0 (CC BY 3.0)." +
                "\nAtribuição: Blender Foundation | www.blender.org." +
                "\nFonte e licença: https://peach.blender.org/about/." +
                "\nO filme é reproduzido na versão original, com os créditos finais preservados."
            tags = listOf("Filme aberto", "CC BY 3.0", "Demonstração educacional")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val movie = educationalMovies.firstOrNull { it.id == data } ?: return false

        val link = newExtractorLink(
            source = "Blender Foundation (ExoPlayer test media)",
            name = "${movie.title} - MP4",
            url = movie.videoUrl,
            type = ExtractorLinkType.VIDEO
        )

        callback(link)
        return true
    }
}
