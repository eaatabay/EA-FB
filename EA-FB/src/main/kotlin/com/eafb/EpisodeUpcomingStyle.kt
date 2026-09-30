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
import androidx.recyclerview.widget.RecyclerView
import android.widget.FrameLayout
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
    private val observedLists = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<RecyclerView, Boolean>()
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

        // V35: observe only RecyclerView child attachment. The old global-layout
        // hook rescanned the whole detail tree on every image/layout pass and
        // became expensive on long shows such as The Simpsons.
        observeEpisodeLists(root, fragment, url)
        renderRows(root, futureEpisodes, activity)
    }

    private fun observeEpisodeLists(root: View, fragment: Fragment, url: String) {
        findRecyclerViews(root).forEach { list ->
            synchronized(observedLists) {
                if (!observedLists.add(list)) return@forEach
            }
            list.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        main.post {
                            val current = dates[url] ?: return@post
                            val activity = fragment.activity as? FragmentActivity ?: return@post
                            if (fragment.view != null && view.isAttachedToWindow) {
                                renderRows(view, current, activity)
                            }
                        }
                    }

                    override fun onChildViewDetachedFromWindow(view: View) = Unit
                }
            )
        }
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
        val playId = activity.resources.getIdentifier("episode_play_icon", "id", activity.packageName)
        val textId = activity.resources.getIdentifier("episode_text", "id", activity.packageName)
        if (holderIds.isEmpty() || textId == 0) return

        // The large layout contains both holder ids. Deduplicate by its one title
        // TextView so a row is decorated once instead of once per nested holder.
        val seenTitles = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<TextView, Boolean>()
        )
        holderIds.flatMap { findViews(root, it) }.forEach row@ { holder ->
            val textView = holder.findViewById<TextView>(textId) ?: return@row
            if (!seenTitles.add(textView)) return@row

            // RecyclerView recycles episode rows. Always clear our decoration first;
            // otherwise a future row's badge can leak onto an already-aired episode.
            if (posterId != 0) {
                holder.findViewById<View>(posterId)?.let { poster ->
                    (poster.parent as? FrameLayout)?.findViewWithTag<View>(BADGE_TAG)?.let { badge ->
                        (badge.parent as? ViewGroup)?.removeView(badge)
                    }
                }
            }

            val dateView = if (dateId != 0) holder.findViewById<TextView>(dateId) else null
            val episodeText = textView.text?.toString().orEmpty()
            val episodeNo = Regex("""^\s*(\d+)\.""").find(episodeText)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@row

            // CloudStream's large EpisodeAdapter explicitly hides this icon only
            // while the bound Episode.airDate is in the future, and restores it
            // for aired/unknown-date rows. No localized countdown parsing needed.
            val nativePlay = if (playId != 0) holder.findViewById<View>(playId) else null
            val hostMarksUpcoming = nativePlay != null &&
                nativePlay.visibility != View.VISIBLE
            if (!hostMarksUpcoming) return@row

            val now = System.currentTimeMillis()
            val rowName = episodeText.substringAfter('.', "").trim()
            val candidates = futureEpisodes.values.filter {
                it.episode == episodeNo && it.date > now
            }
            val future = candidates.singleOrNull()
                ?: candidates.firstOrNull { candidate ->
                    candidate.name?.trim()?.equals(rowName, ignoreCase = true) == true
                }
                ?: candidates.minByOrNull { it.date }
                ?: return@row

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

            if (posterId == 0) return@row
            val poster = holder.findViewById<View>(posterId) ?: return@row
            val frame = poster.parent as? FrameLayout ?: return@row
            if (frame.findViewWithTag<View>(BADGE_TAG) == null) {
                frame.addView(TextView(activity).apply {
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
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    leftMargin = dp(activity, 4)
                    topMargin = dp(activity, 4)
                })
            }
        }
    }

    private fun findRecyclerViews(root: View): List<RecyclerView> {
        val out = mutableListOf<RecyclerView>()
        fun walk(view: View) {
            if (view is RecyclerView) out += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return out
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
