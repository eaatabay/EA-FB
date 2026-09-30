package com.eafb

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** TV-only styling for future EA-FB episodes; movie details are never registered. */
internal object EpisodeUpcomingStyle {
    private const val PROVIDER = "EA-FB V6 STAGING"
    private const val BADGE_TAG = "ea-fb-upcoming-badge"
    private const val DATE_TAG = "ea-fb-upcoming-date" // V30
    private val main = Handler(Looper.getMainLooper())
    internal data class FutureEpisode(val season: Int, val episode: Int, val date: Long, val name: String? = null)
    private val dates = ConcurrentHashMap<String, Map<Pair<Int, Int>, FutureEpisode>>()
    private val observedScrollRoots = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )
    private val pendingScrollRenders = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )
    private val observedFocusRoots = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )
    private val pendingFocusRenders = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )
    private val registered = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<FragmentActivity, Boolean>()
    )

    fun install(context: Context) {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
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

    fun publish(seriesUrl: String, futureEpisodes: Collection<FutureEpisode>) {
        dates[seriesUrl] = futureEpisodes.associate { (it.season to it.episode) to it }
        main.post { renderRegistered() }
        main.postDelayed({ renderRegistered() }, 250)
        main.postDelayed({ renderRegistered() }, 900)
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
        synchronized(registered) { if (!registered.add(host)) return }
        host.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager, fragment: Fragment, view: View, state: Bundle?
                ) {
                    main.post { renderFragment(fragment) }
                    main.postDelayed({ renderFragment(fragment) }, 250)
                    main.postDelayed({ renderFragment(fragment) }, 750)
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
        val args = fragment.arguments ?: return
        if (args.getString("apiName") != PROVIDER) return
        val url = args.getString("url") ?: return
        val futureEpisodes = dates[url] ?: return
        if (!url.contains("/tv/")) return
        val root = fragment.view ?: return
        val activity = fragment.activity as? FragmentActivity ?: return

        // Keep V35's cheap scroll trigger and add a TV-focus trigger for rows that
        // bind only after the fixed first-render windows. No RecyclerView dependency
        // and no global-layout scan storm on long shows such as The Simpsons.
        observeEpisodeScrolling(root, fragment)
        observeEpisodeFocus(root, fragment, activity)
        renderRows(root, futureEpisodes, activity)
    }

    private fun observeEpisodeScrolling(root: View, fragment: Fragment) {
        synchronized(observedScrollRoots) {
            if (!observedScrollRoots.add(root)) return
        }
        root.viewTreeObserver.addOnScrollChangedListener {
            synchronized(pendingScrollRenders) {
                if (!pendingScrollRenders.add(root)) return@addOnScrollChangedListener
            }
            main.postDelayed({
                synchronized(pendingScrollRenders) { pendingScrollRenders.remove(root) }
                if (root.isAttachedToWindow) renderFragment(fragment)
            }, 120)
        }
    }

    private fun observeEpisodeFocus(root: View, fragment: Fragment, activity: FragmentActivity) {
        synchronized(observedFocusRoots) {
            if (!observedFocusRoots.add(root)) return
        }
        val holderIds = listOf("episode_holder_large", "episode_holder").mapNotNull { name ->
            activity.resources.getIdentifier(name, "id", activity.packageName).takeIf { it != 0 }
        }.toSet()
        if (holderIds.isEmpty()) return
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null || !insideEpisodeRow(newFocus, root, holderIds)) return@addOnGlobalFocusChangeListener
            synchronized(pendingFocusRenders) {
                if (!pendingFocusRenders.add(root)) return@addOnGlobalFocusChangeListener
            }
            main.post {
                synchronized(pendingFocusRenders) { pendingFocusRenders.remove(root) }
                if (root.isAttachedToWindow) renderFragment(fragment)
            }
        }
    }

    private fun insideEpisodeRow(view: View, root: View, holderIds: Set<Int>): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.id in holderIds) return true
            if (current === root) break
            current = current.parent as? View
        }
        return false
    }

    private data class SeasonSelection(val visible: Boolean, val season: Int?)

    private fun selectedSeason(root: View, activity: FragmentActivity): SeasonSelection {
        val seasonStringId = activity.resources.getIdentifier("season", "string", activity.packageName)
        val seasonWord = seasonStringId.takeIf { it != 0 }?.let(activity::getString)

        val phoneId = activity.resources.getIdentifier("result_season_button", "id", activity.packageName)
        if (phoneId != 0) {
            val button = root.findViewById<TextView>(phoneId)
            if (button != null && button.visibility == View.VISIBLE) {
                return SeasonSelection(true, EpisodeRowPolicy.seasonNumber(button.text?.toString().orEmpty(), seasonWord))
            }
        }

        val tvId = activity.resources.getIdentifier("result_season_selection", "id", activity.packageName)
        if (tvId != 0) {
            val selector = root.findViewById<View>(tvId)
            if (selector != null && selector.visibility == View.VISIBLE) {
                return SeasonSelection(true, selectedSeasonText(selector)?.let {
                    EpisodeRowPolicy.seasonNumber(it, seasonWord)
                })
            }
        }
        return SeasonSelection(false, null)
    }

    private fun selectedSeasonText(root: View): String? {
        if (root is TextView && root.visibility == View.VISIBLE && root.isSelected) {
            return root.text?.toString()
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                selectedSeasonText(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun renderRows(
        root: View,
        futureEpisodes: Map<Pair<Int, Int>, FutureEpisode>,
        activity: FragmentActivity
    ) {
        val holderIds = listOf("episode_holder_large", "episode_holder").mapNotNull { name ->
            activity.resources.getIdentifier(name, "id", activity.packageName).takeIf { it != 0 }
        }
        val dateId = activity.resources.getIdentifier("episode_date", "id", activity.packageName)
        val posterId = activity.resources.getIdentifier("episode_poster", "id", activity.packageName)
        val textId = activity.resources.getIdentifier("episode_text", "id", activity.packageName)
        val episodeStringId = activity.resources.getIdentifier("episode", "string", activity.packageName)
        val episodeWord = episodeStringId.takeIf { it != 0 }?.let(activity::getString)
        val seasonSelection = selectedSeason(root, activity)
        if (holderIds.isEmpty() || textId == 0) return

        // The large layout contains both holder ids. Deduplicate by its one title
        // TextView so a row is decorated once instead of once per nested holder.
        val seenTitles = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<TextView, Boolean>()
        )
        holderIds.flatMap { findViews(root, it) }.forEach row@ { holder ->
            val textView = holder.findViewById<TextView>(textId) ?: return@row
            if (!seenTitles.add(textView)) return@row

            // Recycled rows can retain our old badge. Remove either poster or compact
            // decoration before deciding whether THIS bound row is future.
            holder.findViewWithTag<View>(BADGE_TAG)?.let { badge ->
                (badge.parent as? ViewGroup)?.removeView(badge)
            }
            holder.findViewWithTag<View>(DATE_TAG)?.let { date ->
                (date.parent as? ViewGroup)?.removeView(date)
            }

            val dateView = if (dateId != 0) holder.findViewById<TextView>(dateId) else null
            val episodeText = textView.text?.toString().orEmpty()
            val episodeNo = EpisodeRowPolicy.episodeNumber(episodeText, episodeWord) ?: return@row

            val now = System.currentTimeMillis()
            val rowName = EpisodeRowPolicy.rowName(episodeText, episodeWord)
            val candidates = futureEpisodes.values.filter {
                it.episode == episodeNo && it.date > now
            }
            val nameMatches = candidates.filter { candidate ->
                rowName.isNotBlank() &&
                    candidate.name?.trim()?.equals(rowName, ignoreCase = true) == true
            }
            // V37: publish already stores exact (season, episode) keys. Use the host's
            // selected season when it is exposed by the phone button or TV selector.
            // If a visible selector cannot yet be resolved, fail closed unless the
            // row title uniquely identifies one candidate; never pick an arbitrary
            // same-number episode from another season.
            val future = seasonSelection.season?.let { season ->
                futureEpisodes[season to episodeNo]?.takeIf { it.date > now }
            } ?: if (seasonSelection.visible) {
                nameMatches.singleOrNull()
            } else {
                candidates.singleOrNull() ?: nameMatches.singleOrNull()
            } ?: return@row

            future.name?.takeIf { it.isNotBlank() }?.let { actualName ->
                textView.apply {
                    text = "$episodeNo. $actualName"
                    visibility = View.VISIBLE
                }
            }

            val dateLabel = longTurkishDate(future.date)
            if (dateView != null) {
                dateView.text = dateLabel
                dateView.visibility = View.VISIBLE
            } else {
                val parent = textView.parent as? ViewGroup ?: return@row
                val existing = parent.findViewWithTag<TextView>(DATE_TAG)
                if (existing != null) existing.text = dateLabel else parent.addView(TextView(activity).apply {
                    tag = DATE_TAG
                    text = dateLabel
                    textSize = textView.textSize / activity.resources.displayMetrics.scaledDensity
                    setTextColor(textView.currentTextColor)
                    setPadding(dp(activity, 8), 0, dp(activity, 8), 0)
                })
            }

            val badge = upcomingBadge(activity)
            val posterFrame = if (posterId != 0) {
                holder.findViewById<View>(posterId)?.parent as? FrameLayout
            } else null
            if (posterFrame != null) {
                posterFrame.addView(badge, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    leftMargin = dp(activity, 4)
                    topMargin = dp(activity, 4)
                })
            } else {
                // Compact CloudStream episode rows have no poster at all. Put the
                // same badge immediately before the title instead of dropping it.
                val parent = textView.parent as? ViewGroup ?: return@row
                val index = parent.indexOfChild(textView).takeIf { it >= 0 } ?: return@row
                val params = if (parent is LinearLayout) {
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        rightMargin = dp(activity, 6)
                    }
                } else {
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                parent.addView(badge, index, params)
            }
        }
    }

    private fun upcomingBadge(activity: FragmentActivity) = TextView(activity).apply {
        tag = BADGE_TAG
        text = "YAKINDA"
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.rgb(7, 22, 45))
        gravity = Gravity.CENTER
        setPadding(dp(activity, 7), dp(activity, 2), dp(activity, 7), dp(activity, 2))
        background = GradientDrawable().apply {
            setColor(Color.rgb(255, 208, 0))
            cornerRadius = dp(activity, 6).toFloat()
        }
    }

    private fun findViews(root: View, id: Int): List<View> {
        val out = mutableListOf<View>()
        fun walk(view: View) {
            if (view.id == id) out += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return out
    }

    private fun longTurkishDate(millis: Long): String =
        SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr", "TR")).format(Date(millis))

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
