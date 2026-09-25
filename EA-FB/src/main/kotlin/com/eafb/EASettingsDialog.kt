package com.eafb

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * EA-FB-owned settings window, not a change to CloudStream's global interface.
 * Built with Android widgets so a TV remote can focus, select and scroll rows.
 * Only real TMDb categories have working toggles; unintegrated playback sources
 * are never displayed as fake working switches.
 */
object EASettingsDialog {
    private val NAVY = Color.rgb(7, 22, 45)
    private val PANEL = Color.rgb(16, 37, 69)
    private val ROW = Color.rgb(22, 49, 84)
    private val YELLOW = Color.rgb(255, 208, 0)
    private val WHITE = Color.rgb(239, 245, 255)
    private val MUTED = Color.rgb(172, 191, 216)
    private val BORDER = Color.rgb(53, 78, 113)

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(ctx: Context, fill: Int, border: Int = BORDER, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, radius).toFloat()
            setStroke(dp(ctx, 1), border)
        }

    private fun text(ctx: Context, value: String, size: Float, color: Int = WHITE, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun vertical(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private fun horizontal(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    private fun margin(ctx: Context, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(ctx, top), 0, dp(ctx, bottom))
        }

    private fun button(ctx: Context, title: String, click: () -> Unit): TextView =
        text(ctx, title, 16f, WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 14), dp(ctx, 11), dp(ctx, 14), dp(ctx, 11))
            background = shape(ctx, PANEL)
            isFocusable = true
            isClickable = true
            setOnClickListener { click() }
            setOnFocusChangeListener { _, focused ->
                background = shape(ctx, if (isSelected) YELLOW else ROW, if (focused) YELLOW else BORDER)
                setTextColor(if (isSelected) NAVY else WHITE)
            }
        }

    private fun section(ctx: Context, target: LinearLayout, heading: String, hint: String? = null) {
        target.addView(text(ctx, heading, 18f, YELLOW, true), margin(ctx, 22, 8))
        if (hint != null) {
            target.addView(text(ctx, hint, 13f, MUTED), margin(ctx, 0, 10))
        }
    }

    private fun categoryRow(ctx: Context, category: CatalogCategory): View {
        val row = horizontal(ctx).apply {
            background = shape(ctx, PANEL)
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
            isFocusable = true
            isClickable = true
        }
        row.addView(text(ctx, category.title, 16f), LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val enabled = Switch(ctx).apply {
            isChecked = EASettings.categoryEnabled(category.id)
            showText = false
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(YELLOW, MUTED)
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.rgb(120, 104, 25), BORDER)
            )
            setOnCheckedChangeListener { _, state ->
                EASettings.setCategoryEnabled(category.id, state)
            }
        }
        row.addView(enabled)
        row.setOnClickListener { enabled.isChecked = !enabled.isChecked }
        row.setOnFocusChangeListener { _, focused ->
            row.background = shape(ctx, if (focused) ROW else PANEL, if (focused) YELLOW else BORDER)
        }
        return row
    }

    fun show(ctx: Context) {
        val dialog = Dialog(ctx)
        val root = vertical(ctx).apply {
            setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 16))
            background = shape(ctx, NAVY, YELLOW, 20)
        }

        // A clear EA-FB wordmark replaces the anonymous stock plugin popup.
        val header = horizontal(ctx)
        val symbol = text(ctx, "EA", 25f, NAVY, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            background = shape(ctx, YELLOW, YELLOW, 10)
        }
        header.addView(symbol)
        val brand = vertical(ctx).apply { setPadding(dp(ctx, 12), 0, 0, 0) }
        brand.addView(text(ctx, "EA-FB  AYARLARI", 23f, WHITE, true))
        brand.addView(text(ctx, "Film  •  Dizi  •  Canlı TV", 13f, MUTED))
        header.addView(brand)
        root.addView(header, margin(ctx, 0, 18))

        val tabs = horizontal(ctx)
        var isCategories = true
        lateinit var render: () -> Unit
        val categoryTab = button(ctx, "KATEGORİLER") { isCategories = true; render() }
        val sourceTab = button(ctx, "KAYNAKLAR") { isCategories = false; render() }
        tabs.addView(categoryTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(ctx, 8)
        })
        tabs.addView(sourceTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabs, margin(ctx, 0, 10))

        val content = vertical(ctx)
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = text(ctx, "Seçimler otomatik kaydedilir. Ana sayfada güncellemek için EA-FB'yi yeniden aç.", 12f, MUTED)
        root.addView(footer, margin(ctx, 12, 0))

        render = {
            categoryTab.isSelected = isCategories
            sourceTab.isSelected = !isCategories
            categoryTab.background = shape(ctx, if (isCategories) YELLOW else PANEL)
            categoryTab.setTextColor(if (isCategories) NAVY else WHITE)
            sourceTab.background = shape(ctx, if (!isCategories) YELLOW else PANEL)
            sourceTab.setTextColor(if (!isCategories) NAVY else WHITE)
            content.removeAllViews()
            scroll.scrollTo(0, 0)

            if (isCategories) {
                section(ctx, content, "İÇERİK SIRALAMASI",
                    "Platform ve tür listelerini sıralar. Trend, vizyon ve en yüksek puanlı listelerin kendi sırası korunur.")
                val sortRow = horizontal(ctx)
                val sortButtons = mutableMapOf<CatalogSortMode, TextView>()
                for (mode in CatalogSortMode.entries) {
                    val current = button(ctx, mode.title) {
                        EASettings.setSortMode(mode)
                        sortButtons.forEach { (itemMode, b) ->
                            val selected = itemMode == mode
                            b.isSelected = selected
                            b.background = shape(ctx, if (selected) YELLOW else PANEL)
                            b.setTextColor(if (selected) NAVY else WHITE)
                        }
                    }
                    val selected = EASettings.sortMode() == mode
                    current.isSelected = selected
                    current.background = shape(ctx, if (selected) YELLOW else PANEL)
                    current.setTextColor(if (selected) NAVY else WHITE)
                    sortButtons[mode] = current
                    sortRow.addView(current, LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = dp(ctx, 5)
                    })
                }
                content.addView(sortRow, margin(ctx, 0, 4))

                val bulk = horizontal(ctx)
                val commands = listOf(
                    "Tümünü Aç" to { EASettings.setAllCategories(true); render() },
                    "Tümünü Kapat" to { EASettings.setAllCategories(false); render() },
                    "Varsayılan" to { EASettings.restoreDefaults(); render() }
                )
                for ((title, action) in commands) {
                    bulk.addView(button(ctx, title, action), LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = dp(ctx, 5)
                    })
                }
                content.addView(bulk, margin(ctx, 12, 4))

                val groups = listOf(
                    "POPÜLER VE TRENDLER" to setOf(
                        "trending", "now-playing", "popular-movie", "popular-tv", "top-movie", "top-tv"
                    ),
                    "DİJİTAL PLATFORMLAR" to setOf(
                        "netflix-movie", "netflix-tv", "disney-movie", "disney-tv",
                        "amazon-movie", "amazon-tv", "apple-movie", "apple-tv",
                        "max-movie", "max-tv", "paramount-tv", "mubi-movie"
                    ),
                    "TÜRLER" to setOf(
                        "action", "sci-fi", "horror", "comedy", "animation-movie", "animation-tv"
                    )
                )
                for ((groupTitle, ids) in groups) {
                    section(ctx, content, groupTitle)
                    for (cat in HomeCategories.all.filter { it.id in ids && it.tmdbPath != null }) {
                        content.addView(categoryRow(ctx, cat), margin(ctx, 0, 5))
                    }
                }
                section(ctx, content, "BELGESELLER")
                content.addView(text(ctx,
                    "BelgeselX kategorileri henüz veri kaynağına bağlı değil. " +
                        "Çalışmayan aç/kapat seçenekleri gösterilmiyor.", 14f, MUTED),
                    margin(ctx, 0, 14))
            } else {
                section(ctx, content, "FİLM VE DİZİ KAYNAKLARI")
                content.addView(text(ctx,
                    "EA-FB'ye henüz doğrulanmış harici film/dizi oynatma kaynağı bağlanmadı. " +
                        "Başka eklentilerdeki kaynaklar otomatik olarak burada çalışmaz.", 16f),
                    margin(ctx, 0, 16))
                val empty = text(ctx, "BAĞLI KAYNAK: 0", 17f, YELLOW, true).apply {
                    gravity = Gravity.CENTER
                    background = shape(ctx, PANEL)
                    setPadding(dp(ctx, 16), dp(ctx, 26), dp(ctx, 16), dp(ctx, 26))
                }
                content.addView(empty, margin(ctx, 0, 16))
                content.addView(text(ctx,
                    "Doğrulanıp izin verilen kaynaklar eklendikçe bu sayfada ayrı " +
                        "açma/kapatma düğmeleri olacak. Canlı TV bu listenin dışında tutulur.",
                    14f, MUTED), margin(ctx, 0, 16))
            }
        }
        render()
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        val metrics = ctx.resources.displayMetrics
        dialog.window?.setLayout(minOf(dp(ctx, 1000), (metrics.widthPixels * 0.9f).toInt()),
            (metrics.heightPixels * 0.86f).toInt())
        categoryTab.requestFocus()
    }
}
