package com.lqlq.browser

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Cầu nối riêng của Hồ sơ Phiêu lưu. Chỉ được gắn vào WebView giao diện shell,
 * không bao giờ gắn vào WebView của website bên ngoài.
 */
class AdventureProfileBridge(
    private val activity: MainActivity,
    private val store: AdventureProfileStore,
    private val dynamicLootStore: DynamicLootStore,
    private val portraitStore: CharacterPortraitStore
) {

    @JavascriptInterface
    fun getProfileState(): String = dynamicLootStore.appendTo(store.snapshot().toJson()).toString()

    @JavascriptInterface
    fun createProfile(nickname: String, avatarId: String): String =
        mutate { store.createProfile(nickname, avatarId) }

    @JavascriptInterface
    fun updateProfile(nickname: String, avatarId: String): String =
        mutate { store.updateProfile(nickname, avatarId) }

    @JavascriptInterface
    fun createProfileAdvanced(nickname: String, avatarId: String, customAvatarData: String): String =
        mutate { store.createProfile(nickname, avatarId, customAvatarData) }

    @JavascriptInterface
    fun updateProfileAdvanced(nickname: String, avatarId: String, customAvatarData: String): String =
        mutate { store.updateProfile(nickname, avatarId, customAvatarData) }

    @JavascriptInterface
    fun setEffectsEnabled(enabled: Boolean): String =
        mutate { store.setEffectsEnabled(enabled) }

    @JavascriptInterface
    fun setLootEnabled(enabled: Boolean): String =
        mutate { store.setLootEnabled(enabled) }

    @JavascriptInterface
    fun setSpiritBeastsEnabled(enabled: Boolean): String =
        mutate { store.setSpiritBeastsEnabled(enabled) }

    @JavascriptInterface
    fun purchaseShopItem(itemId: String): String {
        val result = store.purchaseShopItem(itemId)
        activity.dispatchAdventureProfileState(result.snapshot)
        return result.toJson().toString()
    }

    @JavascriptInterface
    fun purchaseInventorySlot(): String {
        return try {
            if (!store.spendCrystals(DynamicLootStore.SLOT_PRICE_CRYSTALS)) {
                error("Không đủ Linh Thạch.")
            }
            dynamicLootStore.increaseSlotCapacity()
            val snapshot = store.snapshot()
            activity.dispatchAdventureProfileState(snapshot)
            JSONObject().apply {
                put("ok", true)
                put("state", dynamicLootStore.appendTo(snapshot.toJson()))
            }.toString()
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không mở được ô hành trang.")
                put("state", dynamicLootStore.appendTo(store.snapshot().toJson()))
            }.toString()
        }
    }

    @JavascriptInterface
    fun deleteProfile(): String {
        dynamicLootStore.clear()
        portraitStore.clear()
        return mutate { store.deleteProfile() }
    }

    @JavascriptInterface
    fun setCharacterPortrait(dataUri: String): String {
        return try {
            val snapshot = store.snapshot()
            if (snapshot.portraitSet && snapshot.portraitChangeCredits <= 0) {
                error("Cần Thẻ Đổi Ngoại Hình (mua ở Cửa Hàng Linh Thạch) để đổi lại ảnh ngoại hình.")
            }
            if (!portraitStore.save(dataUri)) {
                error("Không lưu được ảnh ngoại hình. Hãy thử ảnh khác.")
            }
            mutate { store.markPortraitSet() }
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không đặt được ngoại hình nhân vật.")
                put("state", dynamicLootStore.appendTo(store.snapshot().toJson()))
            }.toString()
        }
    }

    @JavascriptInterface
    fun setDynamicLootEnabled(enabled: Boolean): String {
        return try {
            dynamicLootStore.setEnabled(enabled)
            val snapshot = store.snapshot()
            activity.dispatchAdventureProfileState(snapshot)
            JSONObject().apply {
                put("ok", true)
                put("state", dynamicLootStore.appendTo(snapshot.toJson()))
            }.toString()
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không cập nhật được Kỳ Vật động.")
                put("state", dynamicLootStore.appendTo(store.snapshot().toJson()))
            }.toString()
        }
    }

    private fun mutate(block: () -> AdventureProfileStore.Snapshot): String {
        return try {
            val snapshot = block()
            activity.dispatchAdventureProfileState(snapshot)
            JSONObject().apply {
                put("ok", true)
                put("state", dynamicLootStore.appendTo(snapshot.toJson()))
            }.toString()
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không cập nhật được Hồ sơ Phiêu lưu.")
                put("state", dynamicLootStore.appendTo(store.snapshot().toJson()))
            }.toString()
        }
    }
}
