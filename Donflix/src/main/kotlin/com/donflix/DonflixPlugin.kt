package com.donflix

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonflixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DonflixProvider())
    }
}
