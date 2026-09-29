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
import android.view.ViewTreeObserver
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
    private const val DATE_TAG = "ea-fb-upcoming-date"
    private val main = Handler(Looper.getMainLooper())
    internal data class FutureEpisode(val season: Int, val episode: Int, val date: Long)
    private val dates = ConcurrentHashMap<String, Map<Pair<Int, Int>, Long>>()
    private val observedRoots = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
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
        dates[seriesUrl] = futureEpisodes.associate { (it.season to it.episode) to it.date }
        main.post { renderRegistered() }
        main.postDelayed({ renderRegistered() }, 250)
        main.postDelayed({ renderRegistered() }, 900)
        main.postDelayed({ renderRegistered() }, 1800)
        main.postDelayed({ renderRegistered() }, 3000)
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
                    main.postDelayed({ renderFragment(fragment) }, 500)
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
        val futureDates = dates[url] ?: return
        if (!url.contains("/tv/")) return
        val root = fragment.view ?: return
        val activity = fragment.activity as? FragmentActivity ?: return
        synchronized(observedRoots) {
            if (observedRoots.add(root)) {
                root.viewTreeObserver.addOnGlobalLayoutListener {
                    if (root.isAttachedToWindow) main.post { renderFragment(fragment) }
                }
            }
        }
        val holderIds = listOf("episode_holder_large", "episode_holder").mapNotNull { name ->
            activity.resources.getIdentifier(name, "id", activity.packageName).takeIf { it != 0 }
        }
        val dateId = activity.resources.getIdentifier("episode_date", "id", activity.packageName)
        val posterId = activity.resources.getIdentifier("episode_poster", "id", activity.packageName)
        val textId = activity.resources.getIdentifier("episode_text", "id", activity.packageName)
        if (holderIds.isEmpty() || textId == 0) return
        holderIds.flatMap { findViews(root, it) }.forEach { holder ->
            val dateView = if (dateId != 0) holder.findViewById<TextView>(dateId) else null
            val episodeText = holder.findViewById<TextView>(textId)?.text?.toString().orEmpty()
            val episodeNo = Regex("""^\s*(\d+)\.""").find(episodeText)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val candidates = futureDates.filterKeys { key -> key.second == episodeNo }
            val parsed = candidates.values.minOrNull() ?: return@forEach
            val dateLabel = longTurkishDate(parsed)
            if (dateView != null) {
                dateView.text = dateLabel
                dateView.visibility = View.VISIBLE
            } else {
                val textView = holder.findViewById<TextView>(textId) ?: return@forEach
                val parent = textView.parent as? ViewGroup ?: return@forEach
                val existing = parent.findViewWithTag<TextView>(DATE_TAG)
                if (existing != null) existing.text = dateLabel else parent.addView(TextView(activity).apply {
                    tag = DATE_TAG
                    text = dateLabel
                    textSize = textView.textSize / activity.resources.displayMetrics.scaledDensity
                    setTextColor(textView.currentTextColor)
                    setPadding(dp(activity, 8), 0, dp(activity, 8), 0)
                })
            }

            if (posterId == 0) return@forEach
            val poster = holder.findViewById<View>(posterId) ?: return@forEach
            val frame = poster.parent as? FrameLayout ?: return@forEach
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
