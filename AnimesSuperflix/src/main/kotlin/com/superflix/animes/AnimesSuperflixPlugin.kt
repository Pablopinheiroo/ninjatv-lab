package com.superflix.animes

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimesSuperflixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimesSuperflixProvider())
    }
}
