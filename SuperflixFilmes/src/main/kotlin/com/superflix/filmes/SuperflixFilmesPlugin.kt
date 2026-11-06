package com.superflix.filmes

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SuperflixFilmesPlugin : Plugin() {
    override fun load(context: Context) {
        SuperflixFilmesProvider.appContext = context.applicationContext
        registerMainAPI(SuperflixFilmesProvider())
    }
}
