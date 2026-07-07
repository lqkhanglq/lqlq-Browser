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
    fun getProfileState(): String = appendState(store.snapshot()).toString()

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
    fun setCollectionTheme(theme: String): String = mutate { store.setCollectionTheme(theme) }

    @JavascriptInterface
    fun equipCard(cardId: String): String = mutate { store.equipCard(cardId) }

    @JavascriptInterface
    fun unequipCard(cardId: String): String {
        // Tháo thẻ trả nó về túi hành trang — nếu túi đã đầy thì không còn
        // chỗ để trả về, phải chặn lại (giống logic tháo đồ trong game).
        val snapshot = store.snapshot()
        if (snapshot.equippedCardIds.contains(cardId)) {
            val backpackUsedAfter = dynamicLootStore.backpackUsed(snapshot.equippedCardIds.toSet()) + 1
            if (backpackUsedAfter > dynamicLootStore.slotCapacity()) {
                return JSONObject().apply {
                    put("ok", false)
                    put("error", "Túi hành trang đã đầy, không thể tháo thẻ ra. Hãy mua thêm ô hoặc xóa bớt thẻ trước.")
                    put("state", appendState(snapshot))
                }.toString()
            }
        }
        return mutate { store.unequipCard(cardId) }
    }

    @JavascriptInterface
    fun deleteCard(cardId: String): String {
        return try {
            dynamicLootStore.delete(cardId)
            mutate { store.unequipCard(cardId) }
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không xóa được thẻ.")
                put("state", appendState(store.snapshot()))
            }.toString()
        }
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
                put("state", appendState(snapshot))
            }.toString()
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không mở được ô hành trang.")
                put("state", appendState(store.snapshot()))
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
                put("state", appendState(store.snapshot()))
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
                put("state", appendState(snapshot))
            }.toString()
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không cập nhật được Kỳ Vật động.")
                put("state", appendState(store.snapshot()))
            }.toString()
        }
    }

    /** Chỉ số nhân vật (HP/ATK/MANA) chỉ tính từ các Thẻ Kỳ Vật đang GẮN vào nhân vật. */
    private fun appendState(snapshot: AdventureProfileStore.Snapshot): JSONObject =
        dynamicLootStore.appendTo(snapshot.toJson(), snapshot.equippedCardIds.toSet())

    private fun mutate(block: () -> AdventureProfileStore.Snapshot): String {
        return try {
            val snapshot = block()
            activity.dispatchAdventureProfileState(snapshot)
            JSONObject().apply {
                put("ok", true)
                put("state", appendState(snapshot))
            }.toString()
        } catch (error: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "Không cập nhật được Hồ sơ Phiêu lưu.")
                put("state", appendState(store.snapshot()))
            }.toString()
        }
    }
}
