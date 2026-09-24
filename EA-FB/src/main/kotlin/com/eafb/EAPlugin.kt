package com.eafb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

/** One visible CloudStream provider; film, series and TV remain internal modules. */
@CloudstreamPlugin
class EAPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(EAProvider())
    }
}
