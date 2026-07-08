package com.lqlq.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserTabStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearPrefs() {
        context.getSharedPreferences("lqlq_native_tabs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun normalAndPrivateTabsPersistAcrossStoreInstances() {
        val store = BrowserTabStore(context)

        val normal = store.createTab(
            url = "https://example.com/page",
            mode = BrowserTabStore.MODE_NORMAL
        )
        store.updateTab(normal.id, title = "Example", mode = BrowserTabStore.MODE_NORMAL)

        val privateTab = store.createTab(
            url = "https://private.example/secret",
            mode = BrowserTabStore.MODE_PRIVATE
        )
        store.updateTab(privateTab.id, title = "Private", mode = BrowserTabStore.MODE_PRIVATE)

        val restored = BrowserTabStore(context)

        assertTrue(restored.tabs(BrowserTabStore.MODE_NORMAL).any { it.url == "https://example.com/page" })
        assertTrue(restored.tabs(BrowserTabStore.MODE_PRIVATE).any { it.url == "https://private.example/secret" })
    }

    @Test
    fun incognitoTabsDoNotPersistAcrossStoreInstances() {
        val store = BrowserTabStore(context)

        val incognito = store.createTab(
            url = "https://incognito.example/hidden",
            mode = BrowserTabStore.MODE_INCOGNITO
        )
        store.updateTab(incognito.id, title = "Hidden", mode = BrowserTabStore.MODE_INCOGNITO)

        assertTrue(store.tabs(BrowserTabStore.MODE_INCOGNITO).any { it.url == "https://incognito.example/hidden" })

        val restored = BrowserTabStore(context)
        val restoredTabs = restored.tabs(BrowserTabStore.MODE_INCOGNITO)

        assertEquals(1, restoredTabs.size)
        assertEquals("", restoredTabs.first().url)
        assertEquals("Thẻ mới", restoredTabs.first().title)
    }

    @Test
    fun closeAllTabsKeepsOneBlankTab() {
        val store = BrowserTabStore(context)
        store.createTab(url = "https://one.example", mode = BrowserTabStore.MODE_NORMAL)
        store.createTab(url = "https://two.example", mode = BrowserTabStore.MODE_NORMAL)

        val remaining = store.closeAllTabs(BrowserTabStore.MODE_NORMAL)

        assertEquals("", remaining.url)
        assertEquals("Thẻ mới", remaining.title)
        assertEquals(1, store.tabs(BrowserTabStore.MODE_NORMAL).size)
    }

    @Test
    fun ensureTabReusesExistingIdAndFindTabSearchesAcrossModes() {
        val store = BrowserTabStore(context)
        val created = store.ensureTab(
            id = "tab-fixed-id",
            url = "https://shared.example",
            mode = BrowserTabStore.MODE_PRIVATE
        )

        val reused = store.ensureTab(
            id = "tab-fixed-id",
            url = "https://ignored.example",
            mode = BrowserTabStore.MODE_PRIVATE
        )

        val foundFromNormalLookup = store.findTab(created.id, BrowserTabStore.MODE_NORMAL)

        assertEquals(created.id, reused.id)
        assertEquals("https://shared.example", reused.url)
        assertEquals(created.id, foundFromNormalLookup?.id)
    }

    @Test
    fun closeTabSelectsDifferentActiveTabWhenCurrentOneIsRemoved() {
        val store = BrowserTabStore(context)
        val first = store.createTab(url = "https://first.example", mode = BrowserTabStore.MODE_NORMAL)
        val second = store.createTab(url = "https://second.example", mode = BrowserTabStore.MODE_NORMAL)

        store.selectTab(first.id, BrowserTabStore.MODE_NORMAL)
        val next = store.closeTab(first.id, BrowserTabStore.MODE_NORMAL)

        assertNotEquals(first.id, next.id)
        assertEquals(second.id, next.id)
        assertFalse(store.tabs(BrowserTabStore.MODE_NORMAL).any { it.id == first.id })
    }
}
