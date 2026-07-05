package com.lqlq.browser

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URI
import java.util.Locale

/**
 * Bộ chuyển thẻ native, không phụ thuộc DOM/JavaScript/z-index của shell WebView.
 * Giao diện lấy cảm hứng từ lưới thẻ Chrome nhưng chỉ dùng placeholder nhẹ,
 * không chụp bitmap WebView trên UI thread.
 */
class NativeTabSwitcherView(
    context: Context,
    private val callbacks: Callbacks
) : FrameLayout(context) {

    interface Callbacks {
        fun onNewTab()
        fun onSelectTab(tabId: String)
        fun onCloseTab(tabId: String)
        fun onCloseOtherTabs()
        fun onCloseAllTabs()
        fun onDismiss()
    }

    private val countText: TextView
    private val searchInput: EditText
    private val recyclerView: RecyclerView
    private val adapter = TabAdapter(
        onSelect = callbacks::onSelectTab,
        onClose = callbacks::onCloseTab
    )

    var isOpen: Boolean = false
        private set

    init {
        visibility = View.GONE
        setBackgroundColor(Color.rgb(238, 243, 248))
        isClickable = true
        isFocusable = true
        elevation = dp(24).toFloat()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(
            header,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(70))
        )

        val newTab = TextView(context).apply {
            text = "+"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            background = rounded(Color.rgb(24, 166, 74), dp(20))
            contentDescription = "Mở thẻ mới"
            setOnClickListener { callbacks.onNewTab() }
        }
        header.addView(newTab, LinearLayout.LayoutParams(dp(56), dp(56)))

        val centerSpacerLeft = View(context)
        header.addView(centerSpacerLeft, LinearLayout.LayoutParams(0, 1, 1f))

        val modeChip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(Color.rgb(217, 224, 232), dp(22))
        }
        header.addView(modeChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(52)))

        countText = TextView(context).apply {
            text = "1"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(32, 33, 36))
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.WHITE, dp(17), Color.rgb(217, 224, 232), dp(1))
        }
        modeChip.addView(countText, LinearLayout.LayoutParams(dp(42), dp(42)))

        val gridGlyph = TextView(context).apply {
            text = "▦"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 99, 104))
            contentDescription = "Chế độ lưới"
        }
        modeChip.addView(gridGlyph, LinearLayout.LayoutParams(dp(46), dp(42)))

        val centerSpacerRight = View(context)
        header.addView(centerSpacerRight, LinearLayout.LayoutParams(0, 1, 1f))

        val menu = TextView(context).apply {
            text = "⋮"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(32, 33, 36))
            contentDescription = "Tùy chọn thẻ"
            setOnClickListener { showMenu(this) }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(56)))

        searchInput = EditText(context).apply {
            hint = "Tìm thẻ của bạn"
            textSize = 18f
            isSingleLine = true
            setTextColor(Color.rgb(32, 33, 36))
            setHintTextColor(Color.rgb(95, 99, 104))
            setPadding(dp(20), 0, dp(20), 0)
            background = rounded(Color.rgb(217, 224, 232), dp(28))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    adapter.filter(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(
            searchInput,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).apply {
                bottomMargin = dp(12)
            }
        )

        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = this@NativeTabSwitcherView.adapter
            clipToPadding = false
            setPadding(0, 0, 0, dp(12))
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addItemDecoration(GridSpacingDecoration(dp(10)))
        }
        content.addView(
            recyclerView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    fun show(tabs: List<BrowserTab>, activeTabId: String) {
        submitTabs(tabs, activeTabId)
        searchInput.setText("")
        visibility = View.VISIBLE
        alpha = 0f
        translationY = dp(18).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(170).start()
        isOpen = true
        requestFocus()
    }

    fun hide(notify: Boolean = true) {
        if (!isOpen && visibility != View.VISIBLE) return
        isOpen = false
        searchInput.clearFocus()
        context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(windowToken, 0)
        animate().alpha(0f).translationY(dp(14).toFloat()).setDuration(130)
            .withEndAction {
                visibility = View.GONE
                alpha = 1f
                translationY = 0f
            }
            .start()
        if (notify) callbacks.onDismiss()
    }

    fun submitTabs(tabs: List<BrowserTab>, activeTabId: String) {
        countText.text = if (tabs.size > 99) ":D" else tabs.size.coerceAtLeast(1).toString()
        adapter.submit(tabs, activeTabId)
    }

    private fun showMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add("Thẻ mới")
            menu.add("Đóng các thẻ khác")
            menu.add("Đóng tất cả thẻ")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Thẻ mới" -> callbacks.onNewTab()
                    "Đóng các thẻ khác" -> callbacks.onCloseOtherTabs()
                    "Đóng tất cả thẻ" -> callbacks.onCloseAllTabs()
                }
                true
            }
            show()
        }
    }

    private fun rounded(
        fill: Int,
        radius: Int,
        strokeColor: Int? = null,
        strokeWidth: Int = 0
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class TabAdapter(
        private val onSelect: (String) -> Unit,
        private val onClose: (String) -> Unit
    ) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

        private var allTabs: List<BrowserTab> = emptyList()
        private var visibleTabs: List<BrowserTab> = emptyList()
        private var activeTabId: String = ""
        private var query: String = ""

        init {
            setHasStableIds(true)
        }

        fun submit(tabs: List<BrowserTab>, activeId: String) {
            allTabs = tabs.map { it.copy() }
            activeTabId = activeId
            applyFilter()
        }

        fun filter(value: String) {
            query = value.trim().lowercase(Locale.getDefault())
            applyFilter()
        }

        private fun applyFilter() {
            visibleTabs = if (query.isBlank()) {
                allTabs
            } else {
                allTabs.filter { tab ->
                    tab.title.lowercase(Locale.getDefault()).contains(query) ||
                        tab.url.lowercase(Locale.getDefault()).contains(query)
                }
            }
            notifyDataSetChanged()
        }

        override fun getItemId(position: Int): Long = visibleTabs[position].id.hashCode().toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabAdapter.TabViewHolder {
            return TabViewHolder(createCardView(parent.context))
        }

        override fun onBindViewHolder(holder: TabAdapter.TabViewHolder, position: Int) {
            holder.bind(visibleTabs[position], visibleTabs[position].id == activeTabId)
        }

        override fun getItemCount(): Int = visibleTabs.size

        private fun createCardView(context: Context): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipToOutline = true
                elevation = dp(2).toFloat()
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(270)
                )
            }
        }

        inner class TabViewHolder(private val card: LinearLayout) : RecyclerView.ViewHolder(card) {
            private val title: TextView
            private val close: TextView
            private val previewLetter: TextView
            private val previewDomain: TextView
            private val previewUrl: TextView

            init {
                val header = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(6), dp(5), dp(4))
                }
                card.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))

                val icon = TextView(context).apply {
                    text = "●"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(24, 166, 74))
                }
                header.addView(icon, LinearLayout.LayoutParams(dp(28), dp(36)))

                title = TextView(context).apply {
                    textSize = 16f
                    setTextColor(Color.rgb(32, 33, 36))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })

                close = TextView(context).apply {
                    text = "×"
                    textSize = 31f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(95, 99, 104))
                    contentDescription = "Đóng thẻ"
                }
                header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))

                val preview = FrameLayout(context).apply {
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = rounded(Color.WHITE, dp(14))
                }
                card.addView(preview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(dp(7), 0, dp(7), dp(8))
                })

                previewLetter = TextView(context).apply {
                    textSize = 54f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(24, 166, 74))
                    typeface = Typeface.DEFAULT_BOLD
                }
                preview.addView(
                    previewLetter,
                    FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                )

                val bottomCopy = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                    background = rounded(Color.argb(230, 247, 250, 253), dp(10))
                }
                preview.addView(
                    bottomCopy,
                    FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                )

                previewDomain = TextView(context).apply {
                    textSize = 13f
                    setTextColor(Color.rgb(32, 33, 36))
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                bottomCopy.addView(previewDomain)

                previewUrl = TextView(context).apply {
                    textSize = 10f
                    setTextColor(Color.rgb(95, 99, 104))
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                bottomCopy.addView(previewUrl)
            }

            fun bind(tab: BrowserTab, active: Boolean) {
                title.text = tab.title.ifBlank { "Thẻ mới" }
                previewLetter.text = title.text.toString().trim().firstOrNull()?.uppercase() ?: "T"
                previewDomain.text = domainOf(tab.url).ifBlank { "Trang chủ lqlq" }
                previewUrl.text = tab.url.ifBlank { "Nhấn để nhập địa chỉ hoặc tìm kiếm" }

                val hue = positiveHash(tab.url.ifBlank { tab.title }) % 360
                val fill = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.06f, 0.98f))
                val stroke = if (active) Color.rgb(24, 166, 74) else Color.rgb(217, 224, 232)
                card.background = rounded(fill, dp(22), stroke, dp(if (active) 4 else 1))

                card.setOnClickListener { onSelect(tab.id) }
                close.setOnClickListener { onClose(tab.id) }
            }

            private fun domainOf(url: String): String {
                if (url.isBlank()) return ""
                return try {
                    URI(url).host?.removePrefix("www.").orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }

            private fun positiveHash(value: String): Int = value.hashCode() and Int.MAX_VALUE
        }
    }

    private class GridSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % 2
            outRect.left = if (column == 0) 0 else spacing / 2
            outRect.right = if (column == 0) spacing / 2 else 0
            outRect.bottom = spacing
        }
    }
}
