package com.eafb

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.app
import java.security.SecureRandom
import java.util.concurrent.CancellationException

/** Isolated experimental LAND FastPlay. Direct HTTPS HLS; never opens loopback.
 *  Never logs nonce, sp, X-Sp, URLs or segment data.
 */
internal class LandFastPlayResolver {
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val random = SecureRandom()
    private fun trace(stage: String, result: String) = Log.i("EA-FB-LAND", "fastplay/" + stage + " " + result)
    private fun nonce6(): String = buildString(6) {
        repeat(6) { append("0123456789abcdefghijklmnopqrstuvwxyz"[random.nextInt(36)]) }
    }

    suspend fun resolve(embedUrl: String, label: String, site: String): SourceLink? {
        val referer = "https://fastplay.mom/"
        val embedHeaders = mapOf("User-Agent" to ua)
        val spg = try {
            val response = app.get(embedUrl, headers = embedHeaders, referer = site.trimEnd('/') + "/")
            trace("L4-embed", "http=" + response.code)
            if (response.code !in 200..299) return null
            LandFastPlayCodec.spgOperands(response.text)
        } catch (cancel: CancellationException) { throw cancel }
          catch (e: Exception) { trace("L4-embed", "failure=" + e.javaClass.simpleName); return null }
        if (spg == null) { trace("L5-spg", "found=false"); return null }
        trace("L5-spg", "found=true")
        val playerUrl = try {
            LandFastPlayCodec.playerUrl(
                LandFastPlayCodec.xorDecrypt(
                    Base64.decode(spg.first, Base64.DEFAULT),
                    Base64.decode(spg.second, Base64.DEFAULT)
                ) ?: return null
            )
        } catch (e: Exception) { trace("L5-xor", "failure=" + e.javaClass.simpleName); return null }
        if (playerUrl == null) { trace("L5-xor", "https=false"); return null }
        val player = try {
            app.get(playerUrl, headers = embedHeaders, referer = embedUrl)
        } catch (cancel: CancellationException) { throw cancel }
          catch (e: Exception) { trace("L6-player", "failure=" + e.javaClass.simpleName); return null }
        trace("L6-player", "http=" + player.code)
        if (player.code !in 200..299) return null
        val sp = LandFastPlayCodec.sp(player.text)
        val masterUrl = LandFastPlayCodec.streamUrl(player.text, playerUrl)
        trace("L6-player", "sp=" + (sp != null) + " master=" + (masterUrl != null))
        if (sp == null || masterUrl == null) return null
        val token = LandFastPlayCodec.xSp(sp, LandFastPlayCodec.timestamp(player.text, System.currentTimeMillis()), nonce6())
            ?: return null
        val streamHeaders = mapOf("User-Agent" to ua, "X-Sp" to token)
        val master = try {
            app.get(masterUrl, headers = streamHeaders, referer = referer)
        } catch (cancel: CancellationException) { throw cancel }
          catch (e: Exception) { trace("L7-master", "failure=" + e.javaClass.simpleName); return null }
        trace("L7-master", "http=" + master.code + " hls=" + LandFastPlayCodec.isHls(master.text))
        if (master.code !in 200..299 || !LandFastPlayCodec.isHls(master.text)) return null
        val subs = LandFastPlayCodec.subtitles(master.text, masterUrl).map { (lang, url) -> SourceSubtitle(lang, url) }
        trace("L8-link", "https=true subtitles=" + subs.size + " quality=" + (LandFastPlayCodec.quality(master.text) ?: 0))
        return SourceLink(provider = "hdfilmcehennemi-land", url = masterUrl,
            quality = LandFastPlayCodec.quality(master.text),
            audioLanguage = null, subtitleLanguage = null, referer = referer, isHls = true,
            displayName = label + " • FastPlay (test)", subtitles = subs,
            headers = streamHeaders)
    }
}
