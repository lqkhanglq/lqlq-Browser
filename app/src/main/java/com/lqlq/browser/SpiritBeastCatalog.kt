package com.lqlq.browser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Danh mục Linh Thú nguyên bản của lqlq Browser.
 *
 * Không sử dụng tên gọi, hình ảnh hoặc thiết kế từ thương hiệu bên ngoài.
 * Icon hiện tại là ký hiệu Unicode nhẹ để tránh làm nặng APK; sau này có thể
 * thay bằng WebP/PNG riêng cho từng sinh vật mà không đổi logic lưu trữ.
 */
object SpiritBeastCatalog {

    data class Beast(
        val id: String,
        val name: String,
        val family: String,
        val rarity: String,
        val icon: String,
        val description: String,
        val habitat: String,
        val baseCatchChance: Double
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("family", family)
            put("rarity", rarity)
            put("icon", icon)
            put("description", description)
            put("habitat", habitat)
            put("baseCatchChance", baseCatchChance)
        }
    }

    val all: List<Beast> = listOf(
        Beast("mac_ho", "Mặc Hồ", "Linh Thú", "Thường", "🦊", "Linh hồ thích ẩn mình trong những vùng đất đầy chữ viết và tri thức.", "Tri Thức", 0.76),
        Beast("thu_dieu", "Thư Điểu", "Linh Thú", "Thường", "🐦", "Mang theo những mẩu ký ức nhỏ từ các trang sách đã đi qua.", "Văn Tự", 0.78),
        Beast("am_mieu", "Âm Miêu", "Yêu Thú", "Thường", "🐈", "Yêu miêu nhạy cảm với nhịp điệu, âm thanh và lời kể.", "Âm Thanh", 0.74),
        Beast("hoa_diep", "Họa Điệp", "Linh Thú", "Thường", "🦋", "Đôi cánh phản chiếu màu sắc của những miền hình ảnh.", "Hình Ảnh", 0.72),
        Beast("kim_thu", "Kim Thử", "Yêu Thú", "Thường", "🐹", "Rất giỏi phát hiện đồ vật, cửa hàng và kho báu nhỏ.", "Kho Báu", 0.70),
        Beast("vo_danh_thu", "Vô Danh Thú", "Ma Thú", "Thường", "👁️", "Sinh vật lạ thường xuất hiện ở những vùng đất chưa thể phân loại.", "Vô Danh", 0.68),
        Beast("dien_ho", "Điện Hồ", "Hồn Thú", "Hiếm", "⚡", "Chạy dọc những luồng dữ liệu và để lại tia sáng xanh.", "Công Nghệ", 0.49),
        Beast("khien_linh", "Khiên Linh", "Hồn Thú", "Hiếm", "🛡️", "Một linh thể hộ vệ thường xuất hiện sau những lần Shield làm nhiệm vụ.", "Bảo Hộ", 0.47),
        Beast("phong_ung", "Phong Ưng", "Linh Thú", "Hiếm", "🦅", "Bay rất nhanh giữa những miền tin tức và sự kiện.", "Tin Tức", 0.46),
        Beast("anh_lang", "Ảnh Lang", "Ma Thú", "Hiếm", "🐺", "Ẩn trong bóng tối của những trang có nhiều chuyển động và lớp phủ.", "Bóng Tối", 0.43),
        Beast("mong_linh", "Mộng Linh", "Hồn Thú", "Sử Thi", "🌙", "Kết tinh từ những câu chuyện dài và giấc mơ của người đọc.", "Truyện", 0.28),
        Beast("tinh_nao", "Tinh Não", "Hồn Thú", "Sử Thi", "🧠", "Một hồn thể tò mò sống gần các vùng đất trí tuệ nhân tạo.", "AI", 0.27),
        Beast("lien_hoa_linh", "Liên Hoa Linh", "Linh Thú", "Sử Thi", "🪷", "Nở ra ở những vùng đất yên tĩnh, sạch sẽ và ít nhiễu loạn.", "Tĩnh Lặng", 0.26),
        Beast("co_thu_linh", "Cổ Thụ Linh", "Thần Thú", "Sử Thi", "🌳", "Lưu giữ ký ức của những vùng đất đã tồn tại rất lâu.", "Cổ Xưa", 0.24),
        Beast("du_lieu_long", "Dữ Liệu Long", "Thần Thú", "Huyền Thoại", "🐉", "Thần long đi xuyên qua vô số dòng dữ liệu giữa các vùng đất.", "Vạn Giới", 0.12),
        Beast("hoa_phuong", "Hỏa Phượng", "Thần Thú", "Huyền Thoại", "🔥", "Chỉ xuất hiện khi một hành trình được tiếp tục với ý chí mạnh mẽ.", "Hỏa Giới", 0.10),
        Beast("thien_muc", "Thiên Mục", "Thần Thú", "Thần Thoại", "🔮", "Đôi mắt nhìn xuyên qua vô số cánh cổng của Vạn Giới.", "Huyền Bí", 0.045),
        Beast("hu_vo_ky_lan", "Hư Vô Kỳ Lân", "Thần Thú", "Thần Thoại", "🦄", "Sinh vật cực hiếm chỉ để lại dấu chân ánh sáng trên hành trình dài.", "Hư Vô", 0.035)
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): Beast? = byId[id]

    fun toJson(): JSONArray = JSONArray().apply {
        all.forEach { put(it.toJson()) }
    }

    /**
     * Chọn sinh vật theo URL/title, có ưu tiên môi trường phù hợp nhưng vẫn giữ
     * tính bất ngờ. Seed ổn định theo URL + ngày giúp tránh kết quả nhảy liên tục
     * chỉ vì callback lặp lại.
     */
    fun choose(url: String, title: String, dayKey: String): Beast {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        val text = "$host ${title.lowercase(Locale.ROOT)}"
        val preferredHabitat = when {
            listOf("book", "novel", "truyen", "wiki", "read", "docs").any { text.contains(it) } -> setOf("Tri Thức", "Văn Tự", "Truyện", "Cổ Xưa")
            listOf("youtube", "music", "audio", "sound", "video").any { text.contains(it) } -> setOf("Âm Thanh", "Hình Ảnh")
            listOf("ai", "chatgpt", "gemini", "claude", "grok", "perplexity").any { text.contains(it) } -> setOf("AI", "Công Nghệ", "Huyền Bí")
            listOf("news", "bao", "tin").any { text.contains(it) } -> setOf("Tin Tức")
            listOf("shop", "store", "mall", "buy", "sale").any { text.contains(it) } -> setOf("Kho Báu")
            listOf("image", "photo", "art", "gallery").any { text.contains(it) } -> setOf("Hình Ảnh")
            else -> setOf("Vạn Giới", "Vô Danh", "Bóng Tối", "Tĩnh Lặng")
        }

        val weighted = mutableListOf<Beast>()
        all.forEach { beast ->
            val rarityWeight = when (beast.rarity) {
                "Thường" -> 18
                "Hiếm" -> 7
                "Sử Thi" -> 3
                "Huyền Thoại" -> 1
                else -> 1
            }
            repeat(rarityWeight + if (preferredHabitat.contains(beast.habitat)) 12 else 0) {
                weighted += beast
            }
        }
        val seed = "$url|$dayKey|lqlq-spirit".hashCode().absoluteValue
        return weighted[seed % weighted.size]
    }

    fun rarityRank(rarity: String): Int = when (rarity) {
        "Thần Thoại" -> 5
        "Huyền Thoại" -> 4
        "Sử Thi" -> 3
        "Hiếm" -> 2
        else -> 1
    }
}
