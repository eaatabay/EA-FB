package com.eafb

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.util.concurrent.ConcurrentHashMap

/**
 * TV-detail metadata enhancement for EA-FB V6 staging.
 *
 * CloudStream exposes the Film / year / duration row as a FlowLayout containing
 * result_meta_duration. Add source-labelled ratings and genres after duration.
 * If that host anchor is unavailable, native tags remain untouched as fallback.
 */
internal object DetailMetaRow {
    internal data class Meta(
        val imdb: Double?,
        val tmdb: Double?,
        val genres: List<String>
    )

    private const val PROVIDER = "EA-FB V6 STAGING"
    private const val MARKER_PREFIX = "ea-fb-detail-meta-"
    private val main = Handler(Looper.getMainLooper())
    private val entries = ConcurrentHashMap<String, Meta>()
    private val registered = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<FragmentActivity, Boolean>()
    )

    fun install(context: Context) {
        val application = context.applicationContext as? Application ?: return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = attach(activity)
            override fun onActivityResumed(activity: Activity) = attach(activity)
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        attach(context as? Activity)
    }

    fun publish(url: String, imdb: Double?, tmdb: Double?, genres: List<String>) {
        entries[url] = Meta(imdb, tmdb, genres)
        main.post {
            synchronized(registered) {
                registered.toList().forEach { activity ->
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.supportFragmentManager.fragments.forEach(::renderTree)
                    }
                }
            }
        }
    }

    private fun attach(activity: Activity?) {
        val host = activity as? FragmentActivity ?: return
        synchronized(registered) {
            if (!registered.add(host)) return
        }
        host.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager, fragment: Fragment, view: View, state: Bundle?
                ) { main.post { renderFragment(fragment) } }
            }, true
        )
        main.post { host.supportFragmentManager.fragments.forEach(::renderTree) }
    }

    private fun renderTree(fragment: Fragment) {
        renderFragment(fragment)
        fragment.childFragmentManager.fragments.forEach(::renderTree)
    }

    private fun renderFragment(fragment: Fragment) {
        val root = fragment.view ?: return
        val args = fragment.arguments ?: return
        if (args.getString("apiName") != PROVIDER) return
        val url = args.getString("url") ?: return
        val meta = entries[url] ?: return
        val activity = fragment.activity as? FragmentActivity ?: return

        val durationId = activity.resources.getIdentifier(
            "result_meta_duration", "id", activity.packageName
        )
        if (durationId == 0) return
        val duration = root.findViewById<View>(durationId) ?: return
        val row = duration.parent as? ViewGroup ?: return

        // Idempotent re-render when load() or fragment lifecycle publishes again.
        val old = (0 until row.childCount).map { row.getChildAt(it) }
            .filter { it.tag?.toString()?.startsWith(MARKER_PREFIX) == true }
        old.forEach(row::removeView)

        val additions = buildList {
            meta.imdb?.let { add(Label("IMDb " + oneDecimal(it) + "/10", Color.YELLOW)) }
            meta.tmdb?.let { add(Label("TMDb " + oneDecimal(it) + "/10", Color.rgb(57, 255, 20))) }
            meta.genres.forEach { add(Label(it, Color.WHITE)) }
        }
        additions.forEachIndexed { index, label ->
            row.addView(TextView(activity).apply {
                tag = MARKER_PREFIX + index
                text = label.text
                textSize = duration.resources.getDimension(duration.resources.getIdentifier("result_meta_text_size", "dimen", activity.packageName)).takeIf { it > 0f }?.let { it / activity.resources.displayMetrics.scaledDensity } ?: (duration as? TextView)?.textSize?.let { it / activity.resources.displayMetrics.scaledDensity } ?: 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(label.color)
                setPadding(dp(activity, 4), 0, dp(activity, 4), 0)
            })
        }

        // Only hide the ordinary chip row after the duration-row enhancement
        // succeeded. If CloudStream changes its layout, chips remain the fallback.
        val tagsId = activity.resources.getIdentifier("result_tag", "id", activity.packageName)
        if (tagsId != 0 && additions.isNotEmpty()) {
            root.findViewById<View>(tagsId)?.visibility = View.GONE
        }
    }

    private data class Label(val text: String, val color: Int)

    private fun oneDecimal(value: Double) =
        String.format(java.util.Locale.ROOT, "%.1f", value)

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
