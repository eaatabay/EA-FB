package com.eafb

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/** Public EA-FB has no login or API key entry screen. */
@CloudstreamPlugin
class EAPlugin : Plugin() {
    override fun load(context: Context) {
        EASettings.initialize(context)
        registerMainAPI(EAProvider())
        openSettings = { uiContext -> EASettingsDialog.show(uiContext) }
    }
}
