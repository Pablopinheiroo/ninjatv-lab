package com.superflix.doramas

import com.lagradost.cloudstream3.TvType
import com.superflix.common.SuperflixCatalogProvider

class DoramasSuperflixProvider : SuperflixCatalogProvider(
    providerName = "Doramas Superflix",
    category = "dorama",
    mediaType = TvType.TvSeries,
    movie = false,
    anime = false,
    genrePages = listOf(
        "acao" to "Ação",
        "comedia" to "Comédia",
        "crime" to "Crime",
        "drama" to "Drama",
        "fantasia" to "Fantasia",
        "historico" to "Histórico",
        "misterio" to "Mistério",
        "romance" to "Romance",
        "suspense" to "Suspense",
        "terror" to "Terror"
    )
)
