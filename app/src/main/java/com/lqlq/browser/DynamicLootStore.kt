package com.lqlq.browser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kho riêng cho Kỳ Vật/Thẻ Vạn Giới động.
 *
 * Tách khỏi AdventureProfileStore để hệ nội dung động có thể mở rộng mà không
 * làm hỏng dữ liệu Linh Thạch, Linh Cầu và Linh Thú offline hiện tại.
 */
class DynamicLootStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "lqlq_dynamic_loot_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_COLLECTION = "collection"
        private const val KEY_TOTAL_ENCOUNTERS = "total_encounters"
        private const val KEY_TOTAL_COLLECTED = "total_collected"
        private const val KEY_SLOT_CAPACITY = "slot_capacity"

        // Chỉ số nền của nhân vật khi chưa có Thẻ Kỳ Vật nào.
        private const val BASE_HP = 100
        private const val BASE_ATK = 10
        private const val BASE_MANA = 10

        const val DEFAULT_SLOT_CAPACITY = 20
        const val SLOT_PRICE_CRYSTALS = 10
    }

    data class CollectionEntry(
        val item: DynamicLootItem,
        val count: Int,
        val firstDomain: String,
        val firstCollectedAt: Long,
        val lastCollectedAt: Long
    ) {
        fun toJson(): JSONObject = item.toJson().apply {
            put("count", count)
            put("firstDomain", firstDomain)
            put("firstCollectedAt", firstCollectedAt)
            put("lastCollectedAt", lastCollectedAt)
        }
    }

    data class CollectResult(
        val ok: Boolean,
        val error: String,
        val entry: CollectionEntry?,
        val state: JSONObject
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ok", ok)
            put("error", error)
            put("entry", entry?.toJson() ?: JSONObject.NULL)
            put("dynamic", state)
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    @Synchronized
    fun setEnabled(enabled: Boolean): JSONObject {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        return stateJson()
    }

    @Synchronized
    fun clear(): JSONObject {
        prefs.edit().clear().apply()
        return stateJson()
    }

    @Synchronized
    fun noteEncounter() {
        val next = prefs.getInt(KEY_TOTAL_ENCOUNTERS, 0).coerceAtLeast(0) + 1
        prefs.edit().putInt(KEY_TOTAL_ENCOUNTERS, next).apply()
    }

    @Synchronized
    fun slotCapacity(): Int = prefs.getInt(KEY_SLOT_CAPACITY, DEFAULT_SLOT_CAPACITY).coerceAtLeast(DEFAULT_SLOT_CAPACITY)

    @Synchronized
    fun increaseSlotCapacity(): Int {
        val next = slotCapacity() + 1
        prefs.edit().putInt(KEY_SLOT_CAPACITY, next).apply()
        return next
    }

    @Synchronized
    fun collect(item: DynamicLootItem, domain: String): CollectResult {
        if (item.id.isBlank()) {
            return CollectResult(false, "Thẻ Vạn Giới không hợp lệ.", null, stateJson())
        }
        val now = System.currentTimeMillis()
        val cleanDomain = domain.trim().take(180)
        val collection = readCollectionObject()
        val old = collection.optJSONObject(item.id)
        if (old == null && collection.length() >= slotCapacity()) {
            return CollectResult(false, "Túi hành trang đã đầy. Hãy mua thêm ô hoặc dọn bớt trước khi nhặt Thẻ Kỳ Vật mới.", null, stateJson())
        }
        val count = (old?.optInt("count", 0) ?: 0) + 1
        val firstDomain = old?.optString("firstDomain").orEmpty().ifBlank { cleanDomain }
        val firstCollectedAt = old?.optLong("firstCollectedAt", now) ?: now

        val stored = item.toJson().apply {
            put("count", count)
            put("firstDomain", firstDomain)
            put("firstCollectedAt", firstCollectedAt)
            put("lastCollectedAt", now)
        }
        collection.put(item.id, stored)

        prefs.edit()
            .putString(KEY_COLLECTION, collection.toString())
            .putInt(KEY_TOTAL_COLLECTED, prefs.getInt(KEY_TOTAL_COLLECTED, 0).coerceAtLeast(0) + 1)
            .apply()

        val entry = CollectionEntry(item, count, firstDomain, firstCollectedAt, now)
        return CollectResult(true, "", entry, stateJson())
    }

    @Synchronized
    fun delete(itemId: String): Boolean {
        val collection = readCollectionObject()
        if (!collection.has(itemId)) return false
        collection.remove(itemId)
        prefs.edit().putString(KEY_COLLECTION, collection.toString()).apply()
        return true
    }

    /**
     * Chỉ số nhân vật (HP/ATK/MANA) chỉ được cộng từ những Thẻ Kỳ Vật ĐANG
     * GẮN vào nhân vật (equippedIds) — thẻ còn nằm trong túi hành trang mà
     * chưa gắn thì KHÔNG cộng chỉ số. Tháo thẻ ra là chỉ số giảm lại ngay.
     */
    @Synchronized
    fun stateJson(equippedIds: Set<String> = emptySet()): JSONObject {
        val collection = readCollection()
        var hp = BASE_HP
        var atk = BASE_ATK
        var mana = BASE_MANA
        collection.forEach { entry ->
            if (!equippedIds.contains(entry.item.id)) return@forEach
            val bonus = entry.item.statValue * entry.count
            when (entry.item.statType) {
                "HP" -> hp += bonus
                "ATK" -> atk += bonus
                else -> mana += bonus
            }
        }
        return JSONObject().apply {
            put("dynamicLootEnabled", isEnabled())
            put("dynamicCollectionCount", collection.size)
            put("dynamicTotalEncounters", prefs.getInt(KEY_TOTAL_ENCOUNTERS, 0).coerceAtLeast(0))
            put("dynamicTotalCollected", prefs.getInt(KEY_TOTAL_COLLECTED, 0).coerceAtLeast(0))
            put("dynamicCollection", JSONArray().apply { collection.forEach { put(it.toJson()) } })
            put("characterHp", hp)
            put("characterAtk", atk)
            put("characterMana", mana)
            put("slotCapacity", slotCapacity())
            put("slotUsed", collection.size)
            put("slotPriceCrystals", SLOT_PRICE_CRYSTALS)
        }
    }

    @Synchronized
    fun appendTo(target: JSONObject, equippedIds: Set<String> = emptySet()): JSONObject {
        val state = stateJson(equippedIds)
        val keys = state.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            target.put(key, state.opt(key))
        }
        return target
    }

    private fun readCollection(): List<CollectionEntry> {
        val source = readCollectionObject()
        val result = mutableListOf<CollectionEntry>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val obj = source.optJSONObject(id) ?: continue
            val item = runCatching { DynamicLootItem.fromJson(obj) }.getOrNull() ?: continue
            result += CollectionEntry(
                item = item,
                count = obj.optInt("count", 1).coerceAtLeast(1),
                firstDomain = obj.optString("firstDomain"),
                firstCollectedAt = obj.optLong("firstCollectedAt", 0L),
                lastCollectedAt = obj.optLong("lastCollectedAt", 0L)
            )
        }
        return result.sortedWith(
            compareByDescending<CollectionEntry> { DynamicLootItem.rarityRank(it.item.rarity) }
                .thenByDescending { it.lastCollectedAt }
                .thenBy { it.item.name }
        )
    }

    private fun readCollectionObject(): JSONObject = try {
        JSONObject(prefs.getString(KEY_COLLECTION, "{}").orEmpty())
    } catch (_: Exception) {
        JSONObject()
    }
}
