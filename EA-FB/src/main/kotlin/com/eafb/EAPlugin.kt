package com.eafb

import android.app.AlertDialog
import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/** Public EA-FB has no login or API key entry screen. */
@CloudstreamPlugin
class EAPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(EAProvider())
        openSettings = { uiContext ->
            AlertDialog.Builder(uiContext)
                .setTitle("EA-FB • Katalog")
                .setMessage("TMDb bağlantısı otomatik. Kullanıcının API anahtarı alması veya girmesi gerekmez.")
                .setPositiveButton("Tamam", null)
                .show()
        }
    }
}
