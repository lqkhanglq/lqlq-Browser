package com.lqlq.browser

import org.json.JSONObject
import java.util.Locale

/** Một thẻ sưu tập động lấy từ LQLQ Dynamic Loot Engine hoặc Wikimedia. */
data class DynamicLootItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val rarity: String,
    val stars: Int,
    val imageUrl: String,
    val localImageName: String = "",
    val sourceType: String,
    val sourceUrl: String,
    val attribution: String = "",
    val license: String = "",
    val generated: Boolean = false
) {
    /**
     * Mỗi thẻ cộng 1 trong 3 thuộc tính (HP/ATK/MANA) cho nhân vật, chọn
     * ngẫu nhiên nhưng ổn định theo id (băm), độ lớn theo số sao — không
     * cần AI, tự phân phối ở phía app để luôn nhất quán mỗi lần hiển thị.
     */
    val statType: String get() = statTypeFor(id)
    val statValue: Int get() = statValueFor(statType, stars)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("description", description)
        put("rarity", rarity)
        put("stars", stars.coerceIn(1, 5))
        put("imageUrl", imageUrl)
        put("localImageName", localImageName)
        put(
            "displayImageUrl",
            if (localImageName.isNotBlank()) {
                "https://appassets.androidapp.com/dynamic-loot/${localImageName}"
            } else imageUrl
        )
        put("sourceType", sourceType)
        put("sourceUrl", sourceUrl)
        put("attribution", attribution)
        put("license", license)
        put("generated", generated)
        put("statType", statType)
        put("statValue", statValue)
    }

    companion object {
        fun fromJson(source: JSONObject): DynamicLootItem {
            val rawName = source.optString("name").trim().ifBlank { "Kỳ Vật Vô Danh" }
            val rawId = source.optString("id").trim().ifBlank {
                "dynamic-${rawName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')}-${System.currentTimeMillis()}"
            }
            return DynamicLootItem(
                id = rawId.take(180),
                name = rawName.take(100),
                category = source.optString("category", "Kỳ Vật").trim().ifBlank { "Kỳ Vật" }.take(60),
                description = source.optString("description", "Một phát hiện bí ẩn từ Vạn Giới.").trim()
                    .ifBlank { "Một phát hiện bí ẩn từ Vạn Giới." }.take(420),
                rarity = normalizeRarity(source.optString("rarity")),
                stars = source.optInt("stars", starsForRarity(source.optString("rarity"))).coerceIn(1, 5),
                imageUrl = source.optString("imageUrl").ifBlank { source.optString("imageDataUri") },
                localImageName = source.optString("localImageName"),
                sourceType = source.optString("sourceType", "knowledge").take(32),
                sourceUrl = source.optString("sourceUrl").take(600),
                attribution = source.optString("attribution").take(300),
                license = source.optString("license").take(120),
                generated = source.optBoolean("generated", false)
            )
        }

        fun normalizeRarity(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
            "thần thoại", "mythic" -> "Thần Thoại"
            "huyền thoại", "legendary" -> "Huyền Thoại"
            "sử thi", "epic" -> "Sử Thi"
            "hiếm", "rare" -> "Hiếm"
            else -> "Thường"
        }

        fun starsForRarity(rarity: String): Int = when (normalizeRarity(rarity)) {
            "Thần Thoại" -> 5
            "Huyền Thoại" -> 5
            "Sử Thi" -> 4
            "Hiếm" -> 3
            else -> 1
        }

        fun rarityRank(rarity: String): Int = when (normalizeRarity(rarity)) {
            "Thần Thoại" -> 5
            "Huyền Thoại" -> 4
            "Sử Thi" -> 3
            "Hiếm" -> 2
            else -> 1
        }

        private val STAT_TYPES = listOf("HP", "ATK", "MANA")

        fun statTypeFor(id: String): String {
            val hash = id.fold(0) { acc, c -> acc * 31 + c.code }
            return STAT_TYPES[Math.floorMod(hash, STAT_TYPES.size)]
        }

        fun statValueFor(statType: String, stars: Int): Int {
            val clean = stars.coerceIn(1, 5)
            return if (statType == "HP") clean * 10 else clean
        }
    }
}
