package com.lqlq.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Nguồn dữ liệu duy nhất cho hệ thống thẻ của trình duyệt.
 *
 * JavaScript chỉ giữ một bản sao để vẽ thanh địa chỉ và các thành phần cũ;
 * mọi thao tác tạo/chọn/đóng/khôi phục thẻ đều đi qua lớp native này.
 */
data class BrowserTab(
    val id: String,
    var title: String,
    var url: String,
    var isLoading: Boolean = false,
    var lastAccessedAt: Long = System.currentTimeMillis()
)

class BrowserTabStore(context: Context) {

    companion object {
        const val MODE_NORMAL = "normal"
        const val MODE_PRIVATE = "private"

        private const val PREFS_NAME = "lqlq_native_tabs"
        private const val KEY_NORMAL_SESSION = "normal_session_v1"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tabsByMode = linkedMapOf(
        MODE_NORMAL to mutableListOf<BrowserTab>(),
        MODE_PRIVATE to mutableListOf()
    )
    private val activeIds = mutableMapOf<String, String>()

    var currentMode: String = MODE_NORMAL
        private set

    init {
        restoreNormalSession()
        ensureModeHasTab(MODE_NORMAL)
        ensureModeHasTab(MODE_PRIVATE)
    }

    @Synchronized
    fun setMode(mode: String): BrowserTab {
        currentMode = normalizeMode(mode)
        ensureModeHasTab(currentMode)
        return currentTab()
    }

    @Synchronized
    fun tabs(mode: String = currentMode): List<BrowserTab> =
        listFor(mode).map { it.copy() }

    @Synchronized
    fun activeTabId(mode: String = currentMode): String {
        ensureModeHasTab(mode)
        return activeIds[normalizeMode(mode)]!!
    }

    @Synchronized
    fun currentTab(mode: String = currentMode): BrowserTab {
        val normalized = normalizeMode(mode)
        ensureModeHasTab(normalized)
        val list = listFor(normalized)
        val active = activeIds[normalized]
        return list.firstOrNull { it.id == active } ?: list.first().also {
            activeIds[normalized] = it.id
        }
    }

    @Synchronized
    fun findTab(id: String, mode: String = currentMode): BrowserTab? {
        val normalized = normalizeMode(mode)
        return listFor(normalized).firstOrNull { it.id == id }
            ?: tabsByMode.entries
                .asSequence()
                .filter { it.key != normalized }
                .flatMap { it.value.asSequence() }
                .firstOrNull { it.id == id }
    }

    @Synchronized
    fun createTab(
        url: String = "",
        title: String = titleFromUrl(url),
        mode: String = currentMode
    ): BrowserTab {
        val normalized = normalizeMode(mode)
        val tab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { titleFromUrl(url) },
            url = url,
            lastAccessedAt = System.currentTimeMillis()
        )
        listFor(normalized).add(tab)
        activeIds[normalized] = tab.id
        persistIfNeeded(normalized)
        return tab
    }

    @Synchronized
    fun ensureTab(
        id: String,
        url: String = "",
        title: String = titleFromUrl(url),
        mode: String = currentMode
    ): BrowserTab {
        val normalized = normalizeMode(mode)
        val existing = listFor(normalized).firstOrNull { it.id == id }
        if (existing != null) return existing

        val tab = BrowserTab(
            id = id.ifBlank { UUID.randomUUID().toString() },
            title = title.ifBlank { titleFromUrl(url) },
            url = url,
            lastAccessedAt = System.currentTimeMillis()
        )
        listFor(normalized).add(tab)
        activeIds[normalized] = tab.id
        persistIfNeeded(normalized)
        return tab
    }

    @Synchronized
    fun selectTab(id: String, mode: String = currentMode): BrowserTab? {
        val normalized = normalizeMode(mode)
        val tab = listFor(normalized).firstOrNull { it.id == id } ?: return null
        tab.lastAccessedAt = System.currentTimeMillis()
        activeIds[normalized] = tab.id
        persistIfNeeded(normalized)
        return tab
    }

    @Synchronized
    fun updateTab(
        id: String,
        url: String? = null,
        title: String? = null,
        loading: Boolean? = null,
        mode: String = currentMode,
        persist: Boolean = true
    ): BrowserTab? {
        val requestedMode = normalizeMode(mode)
        val actualMode = tabsByMode.entries.firstOrNull { (_, tabs) ->
            tabs.any { it.id == id }
        }?.key ?: requestedMode
        val tab = listFor(actualMode).firstOrNull { it.id == id } ?: return null
        if (url != null) tab.url = url
        if (!title.isNullOrBlank()) tab.title = title
        if (loading != null) tab.isLoading = loading
        tab.lastAccessedAt = System.currentTimeMillis()
        if (persist) persistIfNeeded(actualMode)
        return tab
    }

    /** Đóng thẻ và trả về thẻ đang hoạt động sau thao tác. */
    @Synchronized
    fun closeTab(id: String, mode: String = currentMode): BrowserTab {
        val normalized = normalizeMode(mode)
        val list = listFor(normalized)
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) list.removeAt(index)

        if (list.isEmpty()) {
            val blank = newBlankTab()
            list += blank
            activeIds[normalized] = blank.id
        } else if (activeIds[normalized] == id || list.none { it.id == activeIds[normalized] }) {
            val nextIndex = (index - 1).coerceIn(0, list.lastIndex)
            activeIds[normalized] = list[nextIndex].id
        }

        persistIfNeeded(normalized)
        return currentTab(normalized)
    }

    @Synchronized
    fun closeOtherTabs(keepId: String, mode: String = currentMode): BrowserTab {
        val normalized = normalizeMode(mode)
        val list = listFor(normalized)
        val keep = list.firstOrNull { it.id == keepId } ?: currentTab(normalized)
        list.clear()
        list += keep
        keep.lastAccessedAt = System.currentTimeMillis()
        activeIds[normalized] = keep.id
        persistIfNeeded(normalized)
        return keep
    }

    @Synchronized
    fun closeAllTabs(mode: String = currentMode): BrowserTab {
        val normalized = normalizeMode(mode)
        val list = listFor(normalized)
        list.clear()
        val blank = newBlankTab()
        list += blank
        activeIds[normalized] = blank.id
        persistIfNeeded(normalized)
        return blank
    }

    @Synchronized
    fun stateJson(mode: String = currentMode): String {
        val normalized = normalizeMode(mode)
        ensureModeHasTab(normalized)
        return JSONObject().apply {
            put("mode", normalized)
            put("activeTabId", activeIds[normalized])
            put("tabs", JSONArray().apply {
                listFor(normalized).forEach { tab ->
                    put(JSONObject().apply {
                        put("id", tab.id)
                        put("title", tab.title)
                        put("url", tab.url)
                        put("loading", tab.isLoading)
                    })
                }
            })
        }.toString()
    }

    private fun restoreNormalSession() {
        val raw = prefs.getString(KEY_NORMAL_SESSION, null) ?: return
        try {
            val root = JSONObject(raw)
            val array = root.optJSONArray("tabs") ?: JSONArray()
            val restored = tabsByMode.getValue(MODE_NORMAL)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                restored += BrowserTab(
                    id = id,
                    title = item.optString("title").ifBlank {
                        titleFromUrl(item.optString("url"))
                    },
                    url = item.optString("url"),
                    isLoading = false,
                    lastAccessedAt = item.optLong("lastAccessedAt", System.currentTimeMillis())
                )
            }
            val requestedActive = root.optString("activeTabId")
            activeIds[MODE_NORMAL] = restored.firstOrNull { it.id == requestedActive }?.id
                ?: restored.firstOrNull()?.id.orEmpty()
        } catch (_: Exception) {
            prefs.edit().remove(KEY_NORMAL_SESSION).apply()
            tabsByMode.getValue(MODE_NORMAL).clear()
            activeIds.remove(MODE_NORMAL)
        }
    }

    private fun persistIfNeeded(mode: String) {
        if (mode != MODE_NORMAL) return
        val json = JSONObject().apply {
            put("activeTabId", activeIds[MODE_NORMAL])
            put("tabs", JSONArray().apply {
                tabsByMode.getValue(MODE_NORMAL).forEach { tab ->
                    put(JSONObject().apply {
                        put("id", tab.id)
                        put("title", tab.title)
                        put("url", tab.url)
                        put("lastAccessedAt", tab.lastAccessedAt)
                    })
                }
            })
        }
        prefs.edit().putString(KEY_NORMAL_SESSION, json.toString()).apply()
    }

    private fun ensureModeHasTab(mode: String) {
        val normalized = normalizeMode(mode)
        val list = listFor(normalized)
        if (list.isEmpty()) list += newBlankTab()
        if (activeIds[normalized].isNullOrBlank() || list.none { it.id == activeIds[normalized] }) {
            activeIds[normalized] = list.first().id
        }
        persistIfNeeded(normalized)
    }

    private fun listFor(mode: String): MutableList<BrowserTab> =
        tabsByMode.getValue(normalizeMode(mode))

    private fun normalizeMode(mode: String): String =
        if (mode == MODE_PRIVATE) MODE_PRIVATE else MODE_NORMAL

    private fun newBlankTab() = BrowserTab(
        id = UUID.randomUUID().toString(),
        title = "Thẻ mới",
        url = ""
    )

    private fun titleFromUrl(url: String): String {
        if (url.isBlank()) return "Thẻ mới"
        return try {
            android.net.Uri.parse(url).host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
                ?: "Thẻ mới"
        } catch (_: Exception) {
            "Thẻ mới"
        }
    }
}
