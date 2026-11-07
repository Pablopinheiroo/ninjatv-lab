package com.superflix.filmes

import com.lagradost.cloudstream3.TvType
import com.superflix.common.SuperflixCatalogProvider

class SuperflixFilmesProvider : SuperflixCatalogProvider(
    providerName = "Filmes Superflix",
    category = "filme",
    mediaType = TvType.Movie,
    movie = true,
    anime = false,
    genrePages = listOf(
        "acao" to "Ação",
        "aventura" to "Aventura",
        "animacao" to "Animação",
        "comedia" to "Comédia",
        "crime" to "Crime",
        "documentario" to "Documentário",
        "drama" to "Drama",
        "familia" to "Família",
        "fantasia" to "Fantasia",
        "faroeste" to "Faroeste",
        "ficcao-cientifica" to "Ficção científica",
        "guerra" to "Guerra",
        "historia" to "História",
        "misterio" to "Mistério",
        "musica" to "Música",
        "romance" to "Romance",
        "terror" to "Terror",
        "thriller" to "Thriller"
    )
)
