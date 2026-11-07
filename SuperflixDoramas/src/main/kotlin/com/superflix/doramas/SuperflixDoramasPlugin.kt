package com.superflix.doramas

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SuperflixDoramasPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SuperflixDoramasProvider())
    }
}
