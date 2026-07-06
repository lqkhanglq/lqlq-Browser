package com.lqlq.browser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

/**
 * Kho dữ liệu native cho Hồ sơ Phiêu lưu.
 *
 * Hồ sơ hoàn toàn tùy chọn và lưu cục bộ. Trình duyệt, Shield, TTS và các công
 * cụ vẫn hoạt động bình thường khi người dùng không tạo hồ sơ.
 */
class AdventureProfileStore(context: Context) {

    companion object {
        const val DAILY_CRYSTAL_LIMIT = 30

        private const val PREFS_NAME = "lqlq_adventure_profile_v2"
        private const val LEGACY_PREFS_NAME = "lqlq_adventure_profile_v1"
        private const val KEY_CREATED = "profile_created"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_AVATAR_ID = "avatar_id"
        private const val KEY_CUSTOM_AVATAR = "custom_avatar"
        private const val KEY_CRYSTALS = "crystals"
        private const val KEY_TOTAL_SHIELD_PROTECTS = "total_shield_protects"
        private const val KEY_TOTAL_DISCOVERIES = "total_discoveries"
        private const val KEY_REWARDED_TODAY = "rewarded_today"
        private const val KEY_REWARD_DAY = "reward_day"
        private const val KEY_EFFECTS_ENABLED = "effects_enabled"
        private const val KEY_LOOT_ENABLED = "loot_enabled"
        private const val KEY_BEASTS_ENABLED = "spirit_beasts_enabled"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_ORB_BASIC = "orb_basic"
        private const val KEY_ORB_SILVER = "orb_silver"
        private const val KEY_ORB_GOLD = "orb_gold"
        private const val KEY_TOTAL_ENCOUNTERS = "total_beast_encounters"
        private const val KEY_TOTAL_CAPTURES = "total_beast_captures"
        private const val KEY_CAPTURE_PITY = "capture_pity"
        private const val KEY_COLLECTION = "spirit_beast_collection"
        private const val KEY_PORTRAIT_SET = "character_portrait_set"
        private const val KEY_PORTRAIT_VERSION = "character_portrait_version"
        private const val KEY_IDENTITY_CREDITS = "identity_change_credits"
        private const val KEY_PORTRAIT_CREDITS = "portrait_change_credits"
        private const val KEY_EQUIPPED_CARDS = "equipped_card_ids"
        const val MAX_EQUIPPED_CARDS = 10

        private val ALLOWED_AVATARS = setOf(
            "guardian",
            "swordsman",
            "scholar",
            "ranger",
            "mage",
            "dragon",
            "lotus",
            "comet"
        )

        private val ORB_TYPES = setOf("basic", "silver", "gold")

        private data class ShopItem(
            val id: String,
            val name: String,
            val orbType: String,
            val amount: Int,
            val cost: Int,
            val description: String
        )

        private fun shopItems(): Map<String, ShopItem> = listOf(
            ShopItem("basic_pack", "Túi Linh Cầu Thô", "basic", 3, 15, "3 Linh Cầu cơ bản cho những Linh Thú dễ thu phục."),
            ShopItem("silver_orb", "Linh Cầu Bạc", "silver", 1, 35, "Tăng đáng kể tỷ lệ thu phục Linh Thú hiếm."),
            ShopItem("gold_orb", "Linh Cầu Hoàng Kim", "gold", 1, 90, "Dành cho Linh Thú Sử Thi, Huyền Thoại và Thần Thoại."),
            ShopItem("identity_card", "Thẻ Đổi Danh Tính", "identity", 1, 30, "Cho phép đổi lại biệt danh và avatar ở menu một lần."),
            ShopItem("portrait_card", "Thẻ Đổi Ngoại Hình", "portrait", 1, 30, "Cho phép đặt lại ảnh ngoại hình lớn trong Hồ sơ một lần.")
        ).associateBy { it.id }

        private fun shopCatalogJson(): JSONArray = JSONArray().apply {
            shopItems().values.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("orbType", item.orbType)
                    put("amount", item.amount)
                    put("cost", item.cost)
                    put("description", item.description)
                })
            }
        }
    }

    data class RewardResult(
        val profileCreated: Boolean,
        val rewarded: Boolean,
        val amount: Int,
        val dailyLimitReached: Boolean,
        val snapshot: Snapshot
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("profileCreated", profileCreated)
            put("rewarded", rewarded)
            put("amount", amount)
            put("dailyLimitReached", dailyLimitReached)
            put("state", snapshot.toJson())
        }
    }

    data class CaptureResult(
        val success: Boolean,
        val error: String,
        val orbType: String,
        val chance: Double,
        val roll: Double,
        val beast: SpiritBeastCatalog.Beast?,
        val snapshot: Snapshot
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ok", error.isBlank())
            put("success", success)
            put("error", error)
            put("orbType", orbType)
            put("chance", chance)
            put("roll", roll)
            put("beast", beast?.toJson() ?: JSONObject.NULL)
            put("state", snapshot.toJson())
        }
    }

    data class PurchaseResult(
        val ok: Boolean,
        val error: String,
        val itemId: String,
        val snapshot: Snapshot
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ok", ok)
            put("error", error)
            put("itemId", itemId)
            put("state", snapshot.toJson())
        }
    }

    data class CollectionEntry(
        val beastId: String,
        val count: Int,
        val firstDomain: String,
        val firstCaughtAt: Long,
        val lastCaughtAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("beastId", beastId)
            put("count", count)
            put("firstDomain", firstDomain)
            put("firstCaughtAt", firstCaughtAt)
            put("lastCaughtAt", lastCaughtAt)
            SpiritBeastCatalog.find(beastId)?.let { put("beast", it.toJson()) }
        }
    }

    data class Snapshot(
        val exists: Boolean,
        val nickname: String,
        val avatarId: String,
        val customAvatarData: String,
        val crystals: Int,
        val totalShieldProtects: Int,
        val totalDiscoveries: Int,
        val rewardedToday: Int,
        val dailyLimit: Int,
        val effectsEnabled: Boolean,
        val lootEnabled: Boolean,
        val spiritBeastsEnabled: Boolean,
        val createdAt: Long,
        val level: Int,
        val rankTitle: String,
        val orbBasic: Int,
        val orbSilver: Int,
        val orbGold: Int,
        val totalBeastEncounters: Int,
        val totalBeastCaptures: Int,
        val collection: List<CollectionEntry>,
        val portraitSet: Boolean,
        val portraitVersion: Int,
        val identityChangeCredits: Int,
        val portraitChangeCredits: Int,
        val equippedCardIds: List<String>
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("exists", exists)
            put("nickname", nickname)
            put("avatarId", avatarId)
            put("customAvatarData", customAvatarData)
            put("crystals", crystals)
            put("totalShieldProtects", totalShieldProtects)
            put("totalDiscoveries", totalDiscoveries)
            put("rewardedToday", rewardedToday)
            put("dailyLimit", dailyLimit)
            put("effectsEnabled", effectsEnabled)
            put("lootEnabled", lootEnabled)
            put("spiritBeastsEnabled", spiritBeastsEnabled)
            put("createdAt", createdAt)
            put("level", level)
            put("rankTitle", rankTitle)
            put("orbBasic", orbBasic)
            put("orbSilver", orbSilver)
            put("orbGold", orbGold)
            put("totalBeastEncounters", totalBeastEncounters)
            put("totalBeastCaptures", totalBeastCaptures)
            put("collectionCount", collection.size)
            put("catalogCount", SpiritBeastCatalog.all.size)
            put("collection", JSONArray().apply { collection.forEach { put(it.toJson()) } })
            put("portraitSet", portraitSet)
            put("portraitVersion", portraitVersion)
            put("identityChangeCredits", identityChangeCredits)
            put("portraitChangeCredits", portraitChangeCredits)
            put("equippedCardIds", JSONArray().apply { equippedCardIds.forEach { put(it) } })
            put("catalog", SpiritBeastCatalog.toJson())
            put("shop", shopCatalogJson())
            put("storage", "device")
        }
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    @Synchronized
    fun hasProfile(): Boolean = prefs.getBoolean(KEY_CREATED, false)

    @Synchronized
    fun snapshot(): Snapshot {
        ensureV31Defaults()
        normalizeDailyCounterIfNeeded()
        val exists = prefs.getBoolean(KEY_CREATED, false)
        val discoveries = prefs.getInt(KEY_TOTAL_DISCOVERIES, 0).coerceAtLeast(0)
        val level = levelFor(discoveries)
        return Snapshot(
            exists = exists,
            nickname = prefs.getString(KEY_NICKNAME, "").orEmpty(),
            avatarId = prefs.getString(KEY_AVATAR_ID, "guardian") ?: "guardian",
            customAvatarData = prefs.getString(KEY_CUSTOM_AVATAR, "").orEmpty(),
            crystals = prefs.getInt(KEY_CRYSTALS, 0).coerceAtLeast(0),
            totalShieldProtects = prefs.getInt(KEY_TOTAL_SHIELD_PROTECTS, 0).coerceAtLeast(0),
            totalDiscoveries = discoveries,
            rewardedToday = prefs.getInt(KEY_REWARDED_TODAY, 0).coerceAtLeast(0),
            dailyLimit = DAILY_CRYSTAL_LIMIT,
            effectsEnabled = prefs.getBoolean(KEY_EFFECTS_ENABLED, true),
            lootEnabled = prefs.getBoolean(KEY_LOOT_ENABLED, true),
            spiritBeastsEnabled = prefs.getBoolean(KEY_BEASTS_ENABLED, true),
            createdAt = prefs.getLong(KEY_CREATED_AT, 0L),
            level = level,
            rankTitle = rankTitleFor(level),
            orbBasic = prefs.getInt(KEY_ORB_BASIC, 0).coerceAtLeast(0),
            orbSilver = prefs.getInt(KEY_ORB_SILVER, 0).coerceAtLeast(0),
            orbGold = prefs.getInt(KEY_ORB_GOLD, 0).coerceAtLeast(0),
            totalBeastEncounters = prefs.getInt(KEY_TOTAL_ENCOUNTERS, 0).coerceAtLeast(0),
            totalBeastCaptures = prefs.getInt(KEY_TOTAL_CAPTURES, 0).coerceAtLeast(0),
            collection = readCollection(),
            portraitSet = prefs.getBoolean(KEY_PORTRAIT_SET, false),
            portraitVersion = prefs.getInt(KEY_PORTRAIT_VERSION, 0),
            identityChangeCredits = prefs.getInt(KEY_IDENTITY_CREDITS, 0).coerceAtLeast(0),
            portraitChangeCredits = prefs.getInt(KEY_PORTRAIT_CREDITS, 0).coerceAtLeast(0),
            equippedCardIds = readEquippedCardIds()
        )
    }

    private fun readEquippedCardIds(): List<String> {
        val raw = prefs.getString(KEY_EQUIPPED_CARDS, "[]").orEmpty()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { id -> id.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeEquippedCardIds(ids: List<String>) {
        prefs.edit().putString(KEY_EQUIPPED_CARDS, JSONArray(ids).toString()).apply()
    }

    @Synchronized
    fun equipCard(cardId: String): Snapshot {
        check(hasProfile()) { "Chưa có Hồ sơ Phiêu lưu." }
        val clean = cardId.trim()
        require(clean.isNotBlank()) { "Thẻ không hợp lệ." }
        val current = readEquippedCardIds()
        if (!current.contains(clean)) {
            check(current.size < MAX_EQUIPPED_CARDS) { "Chỉ có thể gắn tối đa $MAX_EQUIPPED_CARDS Thẻ Kỳ Vật." }
            writeEquippedCardIds(current + clean)
        }
        return snapshot()
    }

    @Synchronized
    fun unequipCard(cardId: String): Snapshot {
        check(hasProfile()) { "Chưa có Hồ sơ Phiêu lưu." }
        writeEquippedCardIds(readEquippedCardIds().filterNot { it == cardId.trim() })
        return snapshot()
    }

    @Synchronized
    fun createProfile(nickname: String, avatarId: String, customAvatarData: String = ""): Snapshot {
        val cleanNickname = validateNickname(nickname)
        val cleanAvatar = validateAvatar(avatarId)
        val cleanCustomAvatar = validateCustomAvatar(customAvatarData)
        val now = System.currentTimeMillis()
        val dayKey = currentDayKey()

        prefs.edit()
            .putBoolean(KEY_CREATED, true)
            .putString(KEY_NICKNAME, cleanNickname)
            .putString(KEY_AVATAR_ID, cleanAvatar)
            .putString(KEY_CUSTOM_AVATAR, cleanCustomAvatar)
            .putInt(KEY_CRYSTALS, 0)
            .putInt(KEY_TOTAL_SHIELD_PROTECTS, 0)
            .putInt(KEY_TOTAL_DISCOVERIES, 0)
            .putInt(KEY_REWARDED_TODAY, 0)
            .putString(KEY_REWARD_DAY, dayKey)
            .putBoolean(KEY_EFFECTS_ENABLED, true)
            .putBoolean(KEY_LOOT_ENABLED, true)
            .putBoolean(KEY_BEASTS_ENABLED, true)
            .putLong(KEY_CREATED_AT, now)
            .putInt(KEY_ORB_BASIC, 5)
            .putInt(KEY_ORB_SILVER, 1)
            .putInt(KEY_ORB_GOLD, 0)
            .putInt(KEY_TOTAL_ENCOUNTERS, 0)
            .putInt(KEY_TOTAL_CAPTURES, 0)
            .putInt(KEY_CAPTURE_PITY, 0)
            .putString(KEY_COLLECTION, "{}")
            .putBoolean(KEY_PORTRAIT_SET, false)
            .putInt(KEY_PORTRAIT_VERSION, 0)
            .apply()

        return snapshot()
    }

    @Synchronized
    fun updateProfile(nickname: String, avatarId: String, customAvatarData: String = ""): Snapshot {
        check(hasProfile()) { "Chưa có Hồ sơ Phiêu lưu." }
        val credits = prefs.getInt(KEY_IDENTITY_CREDITS, 0).coerceAtLeast(0)
        check(credits > 0) { "Cần Thẻ Đổi Danh Tính (mua ở Cửa Hàng Linh Thạch) để đổi biệt danh/avatar." }
        prefs.edit()
            .putString(KEY_NICKNAME, validateNickname(nickname))
            .putString(KEY_AVATAR_ID, validateAvatar(avatarId))
            .putString(KEY_CUSTOM_AVATAR, validateCustomAvatar(customAvatarData))
            .putInt(KEY_IDENTITY_CREDITS, credits - 1)
            .apply()
        return snapshot()
    }

    /**
     * Ngoại hình nhân vật (ảnh lớn trong bảng Hồ sơ) được đặt miễn phí một lần
     * duy nhất lúc tạo/lần đầu chỉnh sửa. Từ lần thứ hai trở đi bị khóa cho
     * đến khi có cơ chế đổi trả phí bằng Linh Thạch (chưa triển khai) — hàm
     * này chỉ đóng vai trò "cổng" kiểm tra, việc lưu file ảnh do
     * CharacterPortraitStore đảm nhiệm bên ngoài.
     */
    @Synchronized
    fun markPortraitSet(): Snapshot {
        check(hasProfile()) { "Chưa có Hồ sơ Phiêu lưu." }
        val alreadySet = prefs.getBoolean(KEY_PORTRAIT_SET, false)
        val editor = prefs.edit()
        if (alreadySet) {
            val credits = prefs.getInt(KEY_PORTRAIT_CREDITS, 0).coerceAtLeast(0)
            check(credits > 0) { "Cần Thẻ Đổi Ngoại Hình (mua ở Cửa Hàng Linh Thạch) để đổi lại ảnh ngoại hình." }
            editor.putInt(KEY_PORTRAIT_CREDITS, credits - 1)
        }
        val version = prefs.getInt(KEY_PORTRAIT_VERSION, 0) + 1
        editor.putBoolean(KEY_PORTRAIT_SET, true).putInt(KEY_PORTRAIT_VERSION, version).apply()
        return snapshot()
    }

    @Synchronized
    fun setEffectsEnabled(enabled: Boolean): Snapshot = updateFlag(KEY_EFFECTS_ENABLED, enabled)

    @Synchronized
    fun setLootEnabled(enabled: Boolean): Snapshot = updateFlag(KEY_LOOT_ENABLED, enabled)

    @Synchronized
    fun setSpiritBeastsEnabled(enabled: Boolean): Snapshot = updateFlag(KEY_BEASTS_ENABLED, enabled)

    @Synchronized
    fun deleteProfile(): Snapshot {
        prefs.edit().clear().apply()
        return snapshot()
    }

    @Synchronized
    fun recordShieldProtection(): RewardResult {
        if (!hasProfile()) return RewardResult(false, false, 0, false, snapshot())
        val total = prefs.getInt(KEY_TOTAL_SHIELD_PROTECTS, 0).coerceAtLeast(0) + 1
        prefs.edit().putInt(KEY_TOTAL_SHIELD_PROTECTS, total).apply()
        return RewardResult(true, false, 0, false, snapshot())
    }

    @Synchronized
    fun collectRealmCrystal(): RewardResult {
        if (!hasProfile()) return RewardResult(false, false, 0, false, snapshot())

        normalizeDailyCounterIfNeeded()
        val rewardedToday = prefs.getInt(KEY_REWARDED_TODAY, 0).coerceAtLeast(0)
        val crystals = prefs.getInt(KEY_CRYSTALS, 0).coerceAtLeast(0)
        val discoveries = prefs.getInt(KEY_TOTAL_DISCOVERIES, 0).coerceAtLeast(0) + 1
        val canReward = rewardedToday < DAILY_CRYSTAL_LIMIT

        val editor = prefs.edit().putInt(KEY_TOTAL_DISCOVERIES, discoveries)
        if (canReward) {
            editor.putInt(KEY_CRYSTALS, crystals + 1)
                .putInt(KEY_REWARDED_TODAY, rewardedToday + 1)
        }
        editor.apply()

        return RewardResult(true, canReward, if (canReward) 1 else 0, !canReward, snapshot())
    }

    @Synchronized
    fun attemptCapture(beastId: String, domain: String, orbType: String): CaptureResult {
        if (!hasProfile()) {
            return CaptureResult(false, "Chưa có Hồ sơ Phiêu lưu.", orbType, 0.0, 1.0, null, snapshot())
        }
        val beast = SpiritBeastCatalog.find(beastId)
            ?: return CaptureResult(false, "Linh Thú không hợp lệ.", orbType, 0.0, 1.0, null, snapshot())
        val cleanOrb = orbType.lowercase(Locale.ROOT)
        if (!ORB_TYPES.contains(cleanOrb)) {
            return CaptureResult(false, "Loại Linh Cầu không hợp lệ.", cleanOrb, 0.0, 1.0, beast, snapshot())
        }

        val orbKey = orbKey(cleanOrb)
        val orbCount = prefs.getInt(orbKey, 0).coerceAtLeast(0)
        if (orbCount <= 0) {
            return CaptureResult(false, "Bạn không còn loại Linh Cầu này.", cleanOrb, 0.0, 1.0, beast, snapshot())
        }

        val pity = prefs.getInt(KEY_CAPTURE_PITY, 0).coerceIn(0, 5)
        val multiplier = when (cleanOrb) {
            "silver" -> 1.35
            "gold" -> 1.8
            else -> 1.0
        }
        // TẠM THỜI ĐỂ TEST (v0.32.1): luôn thành công. Đổi lại
        // "min(0.95, beast.baseCatchChance * multiplier + pity * 0.04)" khi test xong.
        val chance = 1.0
        val roll = Random.nextDouble()
        val success = roll <= chance
        val now = System.currentTimeMillis()
        val cleanDomain = domain.trim().take(180)
        val encounters = prefs.getInt(KEY_TOTAL_ENCOUNTERS, 0).coerceAtLeast(0) + 1
        val editor = prefs.edit()
            .putInt(orbKey, orbCount - 1)
            .putInt(KEY_TOTAL_ENCOUNTERS, encounters)

        if (success) {
            val collection = readCollectionObject()
            val old = collection.optJSONObject(beast.id)
            val firstDomain = old?.optString("firstDomain").orEmpty().ifBlank { cleanDomain }
            val firstCaughtAt = old?.optLong("firstCaughtAt", now) ?: now
            val count = (old?.optInt("count", 0) ?: 0) + 1
            collection.put(beast.id, JSONObject().apply {
                put("count", count)
                put("firstDomain", firstDomain)
                put("firstCaughtAt", firstCaughtAt)
                put("lastCaughtAt", now)
            })
            editor.putString(KEY_COLLECTION, collection.toString())
                .putInt(KEY_TOTAL_CAPTURES, prefs.getInt(KEY_TOTAL_CAPTURES, 0).coerceAtLeast(0) + 1)
                .putInt(KEY_CAPTURE_PITY, 0)
        } else {
            editor.putInt(KEY_CAPTURE_PITY, (pity + 1).coerceAtMost(5))
        }
        editor.apply()

        return CaptureResult(success, "", cleanOrb, chance, roll, beast, snapshot())
    }

    @Synchronized
    fun purchaseShopItem(itemId: String): PurchaseResult {
        if (!hasProfile()) return PurchaseResult(false, "Chưa có Hồ sơ Phiêu lưu.", itemId, snapshot())
        val item = shopItems()[itemId]
            ?: return PurchaseResult(false, "Vật phẩm cửa hàng không hợp lệ.", itemId, snapshot())
        val crystals = prefs.getInt(KEY_CRYSTALS, 0).coerceAtLeast(0)
        if (crystals < item.cost) {
            return PurchaseResult(false, "Không đủ Linh Thạch.", itemId, snapshot())
        }
        val editor = prefs.edit().putInt(KEY_CRYSTALS, crystals - item.cost)
        when (item.orbType) {
            "identity" -> {
                val current = prefs.getInt(KEY_IDENTITY_CREDITS, 0).coerceAtLeast(0)
                editor.putInt(KEY_IDENTITY_CREDITS, current + item.amount)
            }
            "portrait" -> {
                val current = prefs.getInt(KEY_PORTRAIT_CREDITS, 0).coerceAtLeast(0)
                editor.putInt(KEY_PORTRAIT_CREDITS, current + item.amount)
            }
            else -> {
                val key = orbKey(item.orbType)
                val current = prefs.getInt(key, 0).coerceAtLeast(0)
                editor.putInt(key, current + item.amount)
            }
        }
        editor.apply()
        return PurchaseResult(true, "", itemId, snapshot())
    }

    /** Dùng bởi tính năng mua thêm ô Túi hành trang (DynamicLootStore), trừ Linh Thạch ở đây vì đó là kho lưu Linh Thạch duy nhất. */
    @Synchronized
    fun spendCrystals(amount: Int): Boolean {
        if (!hasProfile() || amount <= 0) return false
        val crystals = prefs.getInt(KEY_CRYSTALS, 0).coerceAtLeast(0)
        if (crystals < amount) return false
        prefs.edit().putInt(KEY_CRYSTALS, crystals - amount).apply()
        return true
    }


    private fun ensureV31Defaults() {
        if (!prefs.getBoolean(KEY_CREATED, false)) return
        val editor = prefs.edit()
        var changed = false
        if (!prefs.contains(KEY_BEASTS_ENABLED)) {
            editor.putBoolean(KEY_BEASTS_ENABLED, true)
            changed = true
        }
        if (!prefs.contains(KEY_ORB_BASIC)) {
            editor.putInt(KEY_ORB_BASIC, 5)
            changed = true
        }
        if (!prefs.contains(KEY_ORB_SILVER)) {
            editor.putInt(KEY_ORB_SILVER, 1)
            changed = true
        }
        if (!prefs.contains(KEY_ORB_GOLD)) {
            editor.putInt(KEY_ORB_GOLD, 0)
            changed = true
        }
        if (!prefs.contains(KEY_TOTAL_ENCOUNTERS)) {
            editor.putInt(KEY_TOTAL_ENCOUNTERS, 0)
            changed = true
        }
        if (!prefs.contains(KEY_TOTAL_CAPTURES)) {
            editor.putInt(KEY_TOTAL_CAPTURES, 0)
            changed = true
        }
        if (!prefs.contains(KEY_CAPTURE_PITY)) {
            editor.putInt(KEY_CAPTURE_PITY, 0)
            changed = true
        }
        if (!prefs.contains(KEY_COLLECTION)) {
            editor.putString(KEY_COLLECTION, "{}")
            changed = true
        }
        if (changed) editor.apply()
    }

    private fun updateFlag(key: String, enabled: Boolean): Snapshot {
        check(hasProfile()) { "Chưa có Hồ sơ Phiêu lưu." }
        prefs.edit().putBoolean(key, enabled).apply()
        return snapshot()
    }

    private fun readCollection(): List<CollectionEntry> {
        val source = readCollectionObject()
        val result = mutableListOf<CollectionEntry>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val obj = source.optJSONObject(id) ?: continue
            if (SpiritBeastCatalog.find(id) == null) continue
            result += CollectionEntry(
                beastId = id,
                count = obj.optInt("count", 1).coerceAtLeast(1),
                firstDomain = obj.optString("firstDomain"),
                firstCaughtAt = obj.optLong("firstCaughtAt", 0L),
                lastCaughtAt = obj.optLong("lastCaughtAt", 0L)
            )
        }
        return result.sortedWith(
            compareByDescending<CollectionEntry> { SpiritBeastCatalog.rarityRank(SpiritBeastCatalog.find(it.beastId)?.rarity.orEmpty()) }
                .thenBy { SpiritBeastCatalog.find(it.beastId)?.name.orEmpty() }
        )
    }

    private fun readCollectionObject(): JSONObject = try {
        JSONObject(prefs.getString(KEY_COLLECTION, "{}").orEmpty())
    } catch (_: Exception) {
        JSONObject()
    }

    private fun orbKey(type: String): String = when (type) {
        "silver" -> KEY_ORB_SILVER
        "gold" -> KEY_ORB_GOLD
        else -> KEY_ORB_BASIC
    }

    private fun validateNickname(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        val length = clean.codePointCount(0, clean.length)
        require(length in 2..20) { "Biệt danh cần từ 2 đến 20 ký tự." }
        require(clean.none { it == '\n' || it == '\r' || it == '\t' }) { "Biệt danh chứa ký tự không hợp lệ." }
        return clean
    }

    private fun validateAvatar(raw: String): String {
        val clean = raw.trim().lowercase(Locale.ROOT)
        require(ALLOWED_AVATARS.contains(clean)) { "Avatar không hợp lệ." }
        return clean
    }

    private fun validateCustomAvatar(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return ""
        require(clean.startsWith("data:image/")) { "Ảnh đại diện tùy chỉnh không hợp lệ." }
        require(clean.length <= 450_000) { "Ảnh đại diện quá lớn. Hãy chọn ảnh nhỏ hơn." }
        return clean
    }

    private fun normalizeDailyCounterIfNeeded() {
        val today = currentDayKey()
        if (prefs.getString(KEY_REWARD_DAY, null) == today) return
        prefs.edit().putString(KEY_REWARD_DAY, today).putInt(KEY_REWARDED_TODAY, 0).apply()
    }

    fun currentDayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun levelFor(discoveries: Int): Int = when {
        discoveries >= 120 -> 8
        discoveries >= 80 -> 7
        discoveries >= 50 -> 6
        discoveries >= 30 -> 5
        discoveries >= 18 -> 4
        discoveries >= 9 -> 3
        discoveries >= 3 -> 2
        else -> 1
    }

    private fun rankTitleFor(level: Int): String = when (level) {
        1 -> "Khám Phá Giả"
        2 -> "Phiêu Lưu Giả"
        3 -> "Mạo Hiểm Giả"
        4 -> "Lữ Khách Linh Thạch"
        5 -> "Thợ Săn Cổ Vật"
        6 -> "Hộ Vệ Vùng Đất"
        7 -> "Nhà Thám Hiểm Huyền Bí"
        else -> "Đại Mạo Hiểm Gia"
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_CREATED)) return
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (!legacy.getBoolean(KEY_CREATED, false)) return
        prefs.edit()
            .putBoolean(KEY_CREATED, true)
            .putString(KEY_NICKNAME, legacy.getString(KEY_NICKNAME, "Nhà mạo hiểm"))
            .putString(KEY_AVATAR_ID, legacy.getString(KEY_AVATAR_ID, "guardian"))
            .putInt(KEY_CRYSTALS, legacy.getInt(KEY_CRYSTALS, 0))
            .putInt(KEY_TOTAL_SHIELD_PROTECTS, legacy.getInt(KEY_TOTAL_SHIELD_PROTECTS, 0))
            .putInt(KEY_REWARDED_TODAY, legacy.getInt(KEY_REWARDED_TODAY, 0))
            .putString(KEY_REWARD_DAY, legacy.getString(KEY_REWARD_DAY, currentDayKey()))
            .putBoolean(KEY_EFFECTS_ENABLED, legacy.getBoolean(KEY_EFFECTS_ENABLED, true))
            .putBoolean(KEY_LOOT_ENABLED, true)
            .putBoolean(KEY_BEASTS_ENABLED, true)
            .putLong(KEY_CREATED_AT, legacy.getLong(KEY_CREATED_AT, System.currentTimeMillis()))
            .putInt(KEY_ORB_BASIC, 5)
            .putInt(KEY_ORB_SILVER, 1)
            .putString(KEY_COLLECTION, "{}")
            .apply()
    }
}
