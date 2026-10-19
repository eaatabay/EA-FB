package com.eafb

import android.os.Process
import android.content.Context
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import com.lagradost.cloudstream3.plugins.PluginManager

/** Run before preferences, callbacks or provider registration. Reads host inventory only. */
internal object SeriesTestIsolation {
    private fun verifiedSeparateApp(context: Context, uid: Int): Boolean = runCatching {
        val root = checkNotNull(context.getExternalFilesDir(null))
        val file = File(root, "ea-fb-blue-isolation-8502.json")
        check(file.length() in 1..4096)
        val proof = JSONObject(file.readText())
        check(proof.getInt("schema") == 1 && proof.getString("hostPackage") == context.packageName)
        val pm = context.packageManager
        fun identity(name: String): SeriesTestIsolationPolicy.AppIdentity {
            val app = pm.getApplicationInfo(name, 0)
            return SeriesTestIsolationPolicy.AppIdentity(name, app.uid, app.dataDir, app.processName,
                pm.getPackagesForUid(app.uid)?.toList() ?: emptyList())
        }
        val host = identity(context.packageName)
        val other = identity(proof.getString("otherPackage"))
        check(proof.getInt("hostUid") == host.uid && proof.getInt("otherUid") == other.uid)
        check(proof.getString("hostDataDir") == host.dataDir && proof.getString("otherDataDir") == other.dataDir)
        val process = File("/proc/self/cmdline").readText().substringBefore('\u0000')
        SeriesTestIsolationPolicy.separateApps(uid, host, other, process)
    }.getOrDefault(false)

    fun requireSafe(context: Context) {
        val names = (PluginManager.getPluginsOnline().toList() + PluginManager.getPluginsLocal().toList())
            .map { item ->
                val entryPoint = ZipFile(item.filePath).use { archive ->
                    val entry = checkNotNull(archive.getEntry("manifest.json")) { "Test kurulumu envanteri doğrulanamadı" }
                    archive.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()).getString("pluginClassName") }
                }
                SeriesTestIsolationPolicy.inventoryName(item.internalName, entryPoint)
            }
        val uid = Process.myUid()
        check(SeriesTestIsolationPolicy.canLoad(uid, names, uid < 100000 && verifiedSeparateApp(context, uid))) {
            "EA-FB 8502: ayrı profil veya ADB ile doğrulanmış ayrı uygulama gerekir. Aynı uygulamadaki eski/diğer EA-FB eklentilerini kaldırın. İzolasyon bilgisi eksikse test engellenir."
        }
    }
}
