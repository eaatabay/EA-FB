package com.eafb

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** A separate, focusable film-series row on CloudStream's TV detail screen. */
internal object FilmSeriesRail {
    internal data class Card(
        val title: String,
        val url: String,
        val poster: String?,
        val releaseDate: String?,
        val rating: Double?
    )

    private const val MARKER = "ea-fb-film-series-row"
    private const val PROVIDER = "EA-FB V6 STAGING"
    private val main = Handler(Looper.getMainLooper())
    private val entries = ConcurrentHashMap<String, List<Card>>()
    private val images = Executors.newFixedThreadPool(2)
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

    fun publish(url: String, cards: List<Card>) {
        if (cards.size < 2) entries.remove(url) else entries[url] = cards
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

    // Detail fragments live inside the navigation fragment. Visit existing child views
    // when the collection response arrives, as well as newly created views.
    private fun renderTree(fragment: Fragment) {
        renderFragment(fragment)
        fragment.childFragmentManager.fragments.forEach(::renderTree)
    }

    private fun renderFragment(fragment: Fragment) {
        val root = fragment.view ?: return
        val args = fragment.arguments ?: return
        if (args.getString("apiName") != PROVIDER) return
        val url = args.getString("url") ?: return
        val activity = fragment.activity as? FragmentActivity ?: return
        val anchorId = activity.resources.getIdentifier(
            "result_recommendations_holder", "id", activity.packageName
        )
        if (anchorId == 0) return
        val anchor = root.findViewById<View>(anchorId) ?: return
        val parent = anchor.parent as? LinearLayout ?: return
        val existing = parent.findViewWithTag<View>(MARKER)
        existing?.let { parent.removeView(it) }
        val cards = entries[url] ?: return
        val rail = makeRail(activity, cards)
        rail.tag = MARKER
        parent.addView(rail, parent.indexOfChild(anchor))
    }

    private fun makeRail(activity: FragmentActivity, cards: List<Card>): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 14), 0, dp(activity, 10))
        }
        column.addView(TextView(activity).apply {
            text = "Film Serisi"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        cards.forEachIndexed { index, card ->
            val tile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                contentDescription = "${index + 1}. ${card.title}${card.releaseDate?.let { " • $it" } ?: ""}"
                setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 5))
                setOnClickListener { activity.loadResult(card.url, PROVIDER, card.title) }
                setOnFocusChangeListener { _, focused ->
                    background = if (focused) outline(Color.YELLOW) else null
                }
            }
            val posterFrame = FrameLayout(activity)
            val poster = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = outline(Color.DKGRAY)
            }
            posterFrame.addView(
                poster,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            card.rating?.takeIf { it > 0.0 && it <= 10.0 }?.let { rating ->
                posterFrame.addView(TextView(activity).apply {
                    text = String.format(java.util.Locale.ROOT, "★ %.1f", rating)
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(activity, 6), dp(activity, 2), dp(activity, 6), dp(activity, 2))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(11, 22, 47))
                        setStroke(1, Color.YELLOW)
                        cornerRadius = dp(activity, 5).toFloat()
                    }
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(activity, 25),
                    Gravity.TOP or Gravity.END
                ).apply {
                    setMargins(0, dp(activity, 6), dp(activity, 6), 0)
                })
            }
            card.releaseDate?.take(4)?.toIntOrNull()?.let { year ->
                posterFrame.addView(TextView(activity).apply {
                    text = year.toString()
                    setTextColor(Color.YELLOW)
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(activity, 6), dp(activity, 2), dp(activity, 6), dp(activity, 2))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(11, 22, 47))
                        setStroke(1, Color.YELLOW)
                        cornerRadius = dp(activity, 5).toFloat()
                    }
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(activity, 25),
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dp(activity, 6), dp(activity, 6), 0, 0)
                })
            }
            tile.addView(posterFrame, LinearLayout.LayoutParams(dp(activity, 116), dp(activity, 174)))
            loadPoster(card.poster, poster)
            tile.addView(TextView(activity).apply {
                text = card.title
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 13f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(activity, 116), dp(activity, 42)))
            row.addView(tile, LinearLayout.LayoutParams(dp(activity, 124), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        return column
    }

    private fun loadPoster(url: String?, image: ImageView) {
        if (url?.startsWith("https://image.tmdb.org/t/p/") != true) return
        images.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                try {
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 512_000) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        if (bytes.size > 512_000) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) main.post { if (image.isAttachedToWindow) image.setImageBitmap(bitmap) }
        }
    }

    private fun outline(color: Int) = GradientDrawable().apply {
        setColor(Color.rgb(11, 22, 47))
        setStroke(2, color)
        cornerRadius = 8f
    }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
