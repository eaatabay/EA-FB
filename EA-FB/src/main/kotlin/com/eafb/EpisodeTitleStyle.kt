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
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * V40 lazy title-only row updater.
 *
 * Network work happens elsewhere, after the initial detail response. This object
 * only updates currently bound episode title TextViews. No RecyclerView
 * dependency and no global-layout polling.
 */
internal object EpisodeTitleStyle {
    private const val PROVIDER = "EA-FB V6 STAGING"
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
    private data class EpisodeListWatch(
        val adapter: RecyclerView.Adapter<*>,
        val observer: RecyclerView.AdapterDataObserver
    )
    // The host's episode adapter replaces/rebinds rows after each season change.
    // Observe that one list rather than polling every global layout on long series.
    private val episodeListWatches = java.util.WeakHashMap<RecyclerView, EpisodeListWatch>()
    private val pendingEpisodeListRenders = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<RecyclerView, Boolean>()
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
                override fun onFragmentViewDestroyed(
                    fm: FragmentManager, fragment: Fragment, view: View
                ) {
                    releaseEpisodeListWatch(view, fragment.activity as? FragmentActivity)
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
        observeEpisodeList(root, fragment, activity)
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

    /**
     * CloudStream ResultFragmentTv submits a new episode list when the selected
     * season changes. An adapter data observer fires even when TV focus stays on
     * the season selector and no user scroll is generated.
     */
    private fun observeEpisodeList(root: View, fragment: Fragment, activity: FragmentActivity) {
        val id = activity.resources.getIdentifier("result_episodes", "id", activity.packageName)
        if (id == 0) return
        val list = root.findViewById<RecyclerView>(id) ?: return
        val adapter = list.adapter ?: return
        val prior = episodeListWatches[list]
        if (prior?.adapter === adapter) return
        prior?.adapter?.unregisterAdapterDataObserver(prior.observer)

        val fragmentRef = WeakReference(fragment)
        val listRef = WeakReference(list)
        val observer = object : RecyclerView.AdapterDataObserver() {
            private fun changed() = scheduleEpisodeListRender(fragmentRef, listRef)
            override fun onChanged() = changed()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = changed()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = changed()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = changed()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = changed()
        }
        adapter.registerAdapterDataObserver(observer)
        episodeListWatches[list] = EpisodeListWatch(adapter, observer)
    }

    private fun scheduleEpisodeListRender(
        fragmentRef: WeakReference<Fragment>, listRef: WeakReference<RecyclerView>
    ) {
        val list = listRef.get() ?: return
        if (!pendingEpisodeListRenders.add(list)) return
        // Adapter notifications precede the actual row bind/layout. Repaint at
        // the next UI turn and once more after binding; never an endless poll.
        for (delay in listOf(48L, 220L)) {
            main.postDelayed({
                val fragment = fragmentRef.get()
                if (fragment != null && fragment.view?.isAttachedToWindow == true &&
                    listRef.get()?.isAttachedToWindow == true) {
                    renderFragment(fragment)
                }
                if (delay == 220L) {
                    listRef.get()?.let { pendingEpisodeListRenders.remove(it) }
                }
            }, delay)
        }
    }

    private fun releaseEpisodeListWatch(root: View, activity: FragmentActivity?) {
        val host = activity ?: return
        val id = host.resources.getIdentifier(
            "result_episodes", "id", host.packageName
        )
        if (id == 0) return
        val list = root.findViewById<RecyclerView>(id) ?: return
        episodeListWatches.remove(list)?.let {
            it.adapter.unregisterAdapterDataObserver(it.observer)
        }
        pendingEpisodeListRenders.remove(list)
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
