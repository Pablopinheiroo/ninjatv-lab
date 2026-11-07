package com.superflix.animes

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SuperflixAnimesPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SuperflixAnimesProvider())
    }
}
