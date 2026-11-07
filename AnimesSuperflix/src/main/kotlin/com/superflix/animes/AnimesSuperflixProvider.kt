package com.superflix.animes

import com.lagradost.cloudstream3.TvType
import com.superflix.common.SuperflixCatalogProvider

class AnimesSuperflixProvider : SuperflixCatalogProvider(
    providerName = "Animes Superflix",
    category = "anime",
    mediaType = TvType.Anime,
    movie = false,
    anime = true,
    genrePages = listOf(
        "acao" to "Ação",
        "aventura" to "Aventura",
        "comedia" to "Comédia",
        "drama" to "Drama",
        "fantasia" to "Fantasia",
        "ficcao-cientifica" to "Ficção científica",
        "horror" to "Terror",
        "misterio" to "Mistério",
        "romance" to "Romance",
        "shounen" to "Shounen",
        "shoujo" to "Shoujo",
        "seinen" to "Seinen",
        "josei" to "Josei",
        "slice-of-life" to "Cotidiano",
        "sports" to "Esportes",
        "supernatural" to "Sobrenatural",
        "thriller" to "Suspense"
    )
)
