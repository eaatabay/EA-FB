package com.eafb

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
 * V40 lazy title-only row updater.
 *
 * Network work happens elsewhere, after the initial detail response. This object
 * only updates currently bound episode title TextViews. No RecyclerView
 * dependency and no global-layout polling.
 */
internal object EpisodeTitleStyle {
    private const val PROVIDER = "EA-FB V65 LAND TEST"
    internal data class TitleEpisode(
        val season: Int,
        val episode: Int,
        val name: String,
        val original: String? = null
    )

    private val main = Handler(Looper.getMainLooper())
    private val titles = ConcurrentHashMap<String, Map<Pair<Int, Int>, TitleEpisode>>()
    private val registered = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<FragmentActivity, Boolean>()
    )
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
    private val observedSeasonRoots = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )
    private val seasonSignatures = java.util.WeakHashMap<View, String>()
    private val pendingSeasonRenders = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )

    fun install(context: Context) {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = attach(activity)
            override fun onActivityResumed(activity: Activity) {
                attach(activity)
                main.post { renderRegistered() }
                main.postDelayed({ renderRegistered() }, 350)
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        attach(context as? Activity)
    }

    fun publish(seriesUrl: String, episodeTitles: Collection<TitleEpisode>) {
        if (episodeTitles.isEmpty()) return
        titles[seriesUrl] = episodeTitles.associateBy { it.season to it.episode }
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
                    scheduleFirstBindRenders(fragment)
                }
                override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                    scheduleFirstBindRenders(fragment)
                }
            }, true
        )
        main.post { host.supportFragmentManager.fragments.forEach(::renderTree) }
    }

    // CloudStream binds/rebinds the visible episode rows after fragment creation.
    // Bounded retries cover first-open and season selection without layout polling.
    // Focus and scroll observers below continue to handle later recycled rows.
    private fun scheduleFirstBindRenders(fragment: Fragment) {
        val root = fragment.view ?: return
        for (delay in listOf(0L, 250L, 750L, 1600L)) {
            main.postDelayed({
                if (fragment.view === root && root.isAttachedToWindow) {
                    renderFragment(fragment)
                }
            }, delay)
        }
    }

    private fun renderTree(fragment: Fragment) {
        renderFragment(fragment)
        fragment.childFragmentManager.fragments.forEach(::renderTree)
    }

    private fun renderFragment(fragment: Fragment) {
        val args = fragment.arguments ?: return
        if (args.getString("apiName") != PROVIDER) return
        val url = args.getString("url") ?: return
        if (!url.contains("/tv/")) return
        val episodeTitles = titles[url] ?: return
        val root = fragment.view ?: return
        val activity = fragment.activity as? FragmentActivity ?: return
        observeEpisodeScrolling(root, fragment)
        observeEpisodeFocus(root, fragment, activity)
        observeSeasonSelection(root, fragment, activity)
        renderRows(root, episodeTitles, activity)
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
            if (newFocus == null || !insideEpisodeRow(newFocus, root, holderIds)) {
                return@addOnGlobalFocusChangeListener
            }
            synchronized(pendingFocusRenders) {
                if (!pendingFocusRenders.add(root)) return@addOnGlobalFocusChangeListener
            }
            main.post {
                synchronized(pendingFocusRenders) { pendingFocusRenders.remove(root) }
                if (root.isAttachedToWindow) renderFragment(fragment)
            }
        }
    }

    // A season switch rebinds episode rows without entering the episode list.
    // Observe layout changes, not a repeating timer: render only when the visible
    // season selector changes, then retry briefly while CloudStream binds rows.
    private fun observeSeasonSelection(root: View, fragment: Fragment, activity: FragmentActivity) {
        synchronized(observedSeasonRoots) {
            if (!observedSeasonRoots.add(root)) return
        }
        val initial = selectedSeason(root, activity)
        synchronized(seasonSignatures) {
            seasonSignatures[root] = "${initial.visible}:${initial.season}"
        }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (!root.isAttachedToWindow || fragment.view !== root) return@addOnGlobalLayoutListener
            val selection = selectedSeason(root, activity)
            val signature = "${selection.visible}:${selection.season}"
            val changed = synchronized(seasonSignatures) {
                if (seasonSignatures[root] == signature) false
                else {
                    seasonSignatures[root] = signature
                    true
                }
            }
            if (changed) scheduleSeasonRenders(root, fragment)
        }
    }

    private fun scheduleSeasonRenders(root: View, fragment: Fragment) {
        synchronized(pendingSeasonRenders) {
            if (!pendingSeasonRenders.add(root)) return
        }
        for (delay in listOf(0L, 180L, 450L, 950L)) {
            main.postDelayed({
                if (fragment.view === root && root.isAttachedToWindow) renderFragment(fragment)
            }, delay)
        }
        main.postDelayed({
            synchronized(pendingSeasonRenders) { pendingSeasonRenders.remove(root) }
        }, 1000L)
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
                return SeasonSelection(
                    true,
                    EpisodeRowPolicy.seasonNumber(button.text?.toString().orEmpty(), seasonWord)
                )
            }
        }

        val tvId = activity.resources.getIdentifier("result_season_selection", "id", activity.packageName)
        if (tvId != 0) {
            val selector = root.findViewById<View>(tvId)
            if (selector != null && selector.visibility == View.VISIBLE) {
                return SeasonSelection(
                    true,
                    selectedSeasonText(selector)?.let {
                        EpisodeRowPolicy.seasonNumber(it, seasonWord)
                    }
                )
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
        episodeTitles: Map<Pair<Int, Int>, TitleEpisode>,
        activity: FragmentActivity
    ) {
        val holderIds = listOf("episode_holder_large", "episode_holder").mapNotNull { name ->
            activity.resources.getIdentifier(name, "id", activity.packageName).takeIf { it != 0 }
        }
        val textId = activity.resources.getIdentifier("episode_text", "id", activity.packageName)
        val episodeStringId = activity.resources.getIdentifier("episode", "string", activity.packageName)
        val episodeWord = episodeStringId.takeIf { it != 0 }?.let(activity::getString)
        if (holderIds.isEmpty() || textId == 0) return
        val selection = selectedSeason(root, activity)
        val seen = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<TextView, Boolean>()
        )

        holderIds.flatMap { findViews(root, it) }.forEach row@ { holder ->
            val textView = holder.findViewById<TextView>(textId) ?: return@row
            if (!seen.add(textView)) return@row
            val episodeNo = EpisodeRowPolicy.episodeNumber(
                textView.text?.toString().orEmpty(), episodeWord
            ) ?: return@row
            val title = selection.season?.let { episodeTitles[it to episodeNo] }
                ?: if (selection.visible) return@row
                else episodeTitles.values.filter { it.episode == episodeNo }.singleOrNull()
                ?: return@row
            val mainLine = "$episodeNo. ${title.name}"
            val original = title.original?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals(title.name, ignoreCase = true) }
            textView.text = if (original == null) {
                mainLine
            } else {
                val value = SpannableStringBuilder(mainLine)
                    .append("\n")
                    .append(original)
                val start = mainLine.length + 1
                value.setSpan(
                    RelativeSizeSpan(0.78f), start, value.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val base = textView.currentTextColor
                val faded = Color.argb(
                    (Color.alpha(base) * 0.55f).toInt().coerceIn(0, 255),
                    Color.red(base), Color.green(base), Color.blue(base)
                )
                value.setSpan(
                    ForegroundColorSpan(faded), start, value.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                value
            }
            textView.visibility = View.VISIBLE
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
}
