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
        val genres: List<String>,
        val nextEpisode: String? = null,
        val nextEpisodeUntilMillis: Long? = null
    )

    private const val PROVIDER = CleanTestIdentity.NAME
    private const val MARKER_PREFIX = "ea-fb-detail-meta-"
    private const val EPISODE_PANEL_SCALE = 0.90f
    private val main = Handler(Looper.getMainLooper())
    private val entries = ConcurrentHashMap<String, Meta>()
    private val registered = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<FragmentActivity, Boolean>()
    )
    private val observedFocusRoots = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )
    private val pendingFocusRenders = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
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

    fun publish(
        url: String, imdb: Double?, tmdb: Double?, genres: List<String>,
        nextEpisode: String? = null, nextEpisodeUntilMillis: Long? = null
    ) {
        entries[url] = Meta(imdb, tmdb, genres, nextEpisode, nextEpisodeUntilMillis)
        // V38: load() can spend longer than the fragment's first lifecycle window
        // collecting season metadata. Keep retries bounded and cheap, but let the
        // host bind its result views before the last passes.
        main.post { renderRegistered() }
        main.postDelayed({ renderRegistered() }, 300)
        main.postDelayed({ renderRegistered() }, 900)
        main.postDelayed({ renderRegistered() }, 1800)
    }

    private fun renderRegistered() {
        synchronized(registered) {
            registered.toList().forEach { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.supportFragmentManager.fragments.forEach(::renderTree)
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
                ) {
                    main.post { renderFragment(fragment) }
                    main.postDelayed({ renderFragment(fragment) }, 500)
                    main.postDelayed({ renderFragment(fragment) }, 1200)
                }
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

        // V39: CloudStream binds/clears its native next-air views after provider
        // load on the first visit. TV focus moves happen after that binding, so a
        // throttled focus hook restores only our exact long-date row. This avoids
        // global-layout polling and does not touch episode-row decoration.
        observeDetailFocus(root, fragment)
        renderNextEpisode(root, meta, activity)

        // CloudStream's movie layout may expose its generic "coming soon"
        // placeholder even when EA-FB has a normal movie detail response.
        // Hide only that stock movie placeholder; TV upcoming UI is separate.
        // These are CloudStream's generic empty-state placeholders, not EA-FB
        // collection/recommendation headings. They can be surfaced by the host
        // after our async detail response, so hide both variants on EA-FB details.
        listOf("result_coming_soon", "result_tv_coming_soon").forEach { name ->
            val id = activity.resources.getIdentifier(name, "id", activity.packageName)
            if (id != 0) root.findViewById<View>(id)?.visibility = View.GONE
        }

        // Keep LoadResponse.score for watch-status/bookmark rails, but remove
        // CloudStream's unlabeled native score from this detail page. EA-FB's
        // source-labelled IMDb (yellow) and TMDb (green) values remain visible.
        val nativeRatingId = activity.resources.getIdentifier(
            "result_meta_rating", "id", activity.packageName
        )
        if (nativeRatingId != 0) {
            findViews(root, nativeRatingId).forEach { it.visibility = View.GONE }
        }

        // TV-only visual refinement: CloudStream exposes the complete season +
        // episode area as episode_holder_tv. Scaling that one container keeps
        // buttons, cards, text and spacing proportional instead of shrinking
        // individual labels. Pivot at the outer/top edge so the panel stays
        // anchored to the side of the screen.
        if (url.contains("/tv/")) {
            scaleEpisodePanel(root, activity)
        }

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
                textSize = (duration as? TextView)?.textSize?.let { it / activity.resources.displayMetrics.scaledDensity } ?: 14f
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

    private fun observeDetailFocus(root: View, fragment: Fragment) {
        synchronized(observedFocusRoots) {
            if (!observedFocusRoots.add(root)) return
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null) return@addOnGlobalFocusChangeListener
            synchronized(pendingFocusRenders) {
                if (!pendingFocusRenders.add(root)) return@addOnGlobalFocusChangeListener
            }
            main.postDelayed({
                synchronized(pendingFocusRenders) { pendingFocusRenders.remove(root) }
                if (root.isAttachedToWindow) renderNextEpisodeOnly(fragment)
            }, 80)
        }
    }

    private fun renderNextEpisodeOnly(fragment: Fragment) {
        val root = fragment.view ?: return
        val args = fragment.arguments ?: return
        if (args.getString("apiName") != PROVIDER) return
        val url = args.getString("url") ?: return
        val meta = entries[url] ?: return
        val activity = fragment.activity as? FragmentActivity ?: return
        renderNextEpisode(root, meta, activity)
    }

    private fun renderNextEpisode(root: View, meta: Meta, activity: FragmentActivity) {
        val holderId = activity.resources.getIdentifier("result_next_airing_holder", "id", activity.packageName)
        val nextId = activity.resources.getIdentifier("result_next_airing", "id", activity.packageName)
        val timeId = activity.resources.getIdentifier("result_next_airing_time", "id", activity.packageName)
        if (holderId == 0 || nextId == 0 || timeId == 0) return
        val label = meta.nextEpisode?.takeIf {
            meta.nextEpisodeUntilMillis?.let { until ->
                System.currentTimeMillis() < until
            } == true
        }
        if (label == null) {
            // A recycled CloudStream detail fragment may still display a
            // previous next-air label. This provider has no future airing:
            // clear only its own next-air row on every focus/render pass.
            root.findViewById<View>(holderId)?.visibility = View.GONE
            root.findViewById<TextView>(nextId)?.apply {
                text = ""
                visibility = View.GONE
            }
            root.findViewById<TextView>(timeId)?.apply {
                text = ""
                visibility = View.GONE
            }
            return
        }
        root.findViewById<View>(holderId)?.visibility = View.VISIBLE
        root.findViewById<TextView>(nextId)?.apply { text = label; visibility = View.VISIBLE }
        root.findViewById<TextView>(timeId)?.apply { text = ""; visibility = View.GONE }
    }

    private fun scaleEpisodePanel(root: View, activity: FragmentActivity) {
        val id = activity.resources.getIdentifier(
            "episode_holder_tv", "id", activity.packageName
        )
        if (id == 0) return
        val panel = root.findViewById<View>(id) ?: return
        panel.post {
            if (!panel.isAttachedToWindow || panel.width <= 0) return@post
            panel.pivotX = if (panel.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                0f
            } else {
                panel.width.toFloat()
            }
            panel.pivotY = 0f
            panel.scaleX = EPISODE_PANEL_SCALE
            panel.scaleY = EPISODE_PANEL_SCALE
        }
    }

    private fun findViews(root: View, id: Int): List<View> {
        val out = mutableListOf<View>()
        fun walk(view: View) {
            if (view.id == id) out += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return out
    }

    private data class Label(val text: String, val color: Int)

    private fun oneDecimal(value: Double) =
        String.format(java.util.Locale.ROOT, "%.1f", value)

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
