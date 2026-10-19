package com.eafb

import android.os.Process
import java.util.zip.ZipFile
import org.json.JSONObject
import com.lagradost.cloudstream3.plugins.PluginManager

/** Run before preferences, callbacks or provider registration. Reads host inventory only. */
internal object SeriesTestIsolation {
    fun requireSafe() {
        val names = (PluginManager.getPluginsOnline().toList() + PluginManager.getPluginsLocal().toList())
            .map { item ->
                val entryPoint = ZipFile(item.filePath).use { archive ->
                    val entry = checkNotNull(archive.getEntry("manifest.json")) { "Test kurulumu envanteri doğrulanamadı" }
                    archive.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()).getString("pluginClassName") }
                }
                SeriesTestIsolationPolicy.inventoryName(item.internalName, entryPoint)
            }
        check(SeriesTestIsolationPolicy.canLoad(Process.myUid(), names)) {
            "EA-FB SERIES CODEX TEST yalnız ayrı Android kullanıcı profilinde ve başka EA-FB eklentisi bulunmayan test kurulumunda çalışır. Ana V83 kurulumuna yüklemeyin."
        }
    }
}
