package com.superflix.series

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SuperflixSeriesPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SuperflixSeriesProvider())
    }
}
