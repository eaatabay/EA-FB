package com.eafb

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/** One CloudStream extension containing all EA-FB sections. */
@CloudstreamPlugin
class EAPlugin : Plugin() {
    override fun load(context: Context) {
        EASettings.initialize(context)
        registerMainAPI(EAProvider())

        openSettings = { uiContext ->
            val tokenInput = EditText(uiContext).apply {
                setSingleLine(true)
                hint = "TMDb API Read Access Token"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(EASettings.tmdbToken())
            }
            AlertDialog.Builder(uiContext)
                .setTitle("EA-FB • Katalog Ayarları")
                .setMessage("TMDb Read Access Token'ı yalnız bu cihazda saklanır. GitHub'a ekleme.")
                .setView(tokenInput)
                .setPositiveButton("Kaydet") { _, _ -> EASettings.setTmdbToken(tokenInput.text.toString()) }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
    }
}
