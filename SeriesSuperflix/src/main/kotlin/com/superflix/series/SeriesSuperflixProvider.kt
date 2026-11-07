package com.superflix.series

import com.lagradost.cloudstream3.TvType
import com.superflix.common.SuperflixCatalogProvider

class SeriesSuperflixProvider : SuperflixCatalogProvider(
    providerName = "Séries Superflix",
    category = "serie",
    mediaType = TvType.TvSeries,
    movie = false,
    anime = false,
    genrePages = listOf(
        "acao" to "Ação",
        "aventura" to "Aventura",
        "comedia" to "Comédia",
        "crime" to "Crime",
        "documentario" to "Documentário",
        "drama" to "Drama",
        "familia" to "Família",
        "fantasia" to "Fantasia",
        "ficcao-cientifica" to "Ficção científica",
        "guerra" to "Guerra",
        "historia" to "História",
        "misterio" to "Mistério",
        "romance" to "Romance",
        "terror" to "Terror",
        "thriller" to "Suspense"
    )
)
