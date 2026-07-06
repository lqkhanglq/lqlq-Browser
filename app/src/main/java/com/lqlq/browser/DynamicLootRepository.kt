package com.lqlq.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Lấy Kỳ Vật động từ Cloudflare Worker nếu đã cấu hình; tự động rơi về
 * Wikipedia/Wikimedia khi Worker chưa deploy, hết hạn mức hoặc lỗi mạng.
 */
class DynamicLootRepository {

    data class FetchedLoot(
        val item: DynamicLootItem,
        val bitmap: Bitmap
    )

    companion object {
        // Khớp với THEME_QUERY_MAP phía Worker — id cố định do người dùng
        // chọn trong <select>, ánh xạ sang từ khóa tìm kiếm tiếng Anh khi
        // dùng đường Wikipedia dự phòng (Worker chưa cấu hình/lỗi mạng).
        private val THEME_SEARCH_QUERIES = mapOf(
            "landmark" to "famous landmark",
            "football" to "footballer",
            "comic" to "comic book superhero",
            "ancient" to "ancient historical figure",
            "animal" to "animal species",
            "plant" to "plant species"
        )
    }

    fun fetchRandom(seed: String, rarity: String, locale: String = "vi", theme: String = ""): FetchedLoot {
        val endpoint = BuildConfig.DYNAMIC_LOOT_ENDPOINT.trim()
        if (endpoint.isNotBlank() && !endpoint.contains("YOUR-WORKER", ignoreCase = true)) {
            runCatching { fetchFromWorker(endpoint, seed, rarity, locale, theme) }
                .getOrNull()
                ?.let { return it }
        }
        return fetchFromWikipedia(seed, rarity, locale, theme)
    }

    private fun fetchFromWorker(
        endpoint: String,
        seed: String,
        rarity: String,
        locale: String,
        theme: String
    ): FetchedLoot {
        val separator = if (endpoint.contains('?')) '&' else '?'
        val themeParam = if (theme.isNotBlank()) "&theme=${encode(theme)}" else ""
        val url = endpoint.trimEnd('/') + "/api/random-loot" + separator +
            "seed=${encode(seed)}&rarity=${encode(rarity)}&locale=${encode(locale)}&mode=auto$themeParam"
        val json = JSONObject(readText(url))
        if (!json.optBoolean("ok", true)) {
            error(json.optString("error", "Dynamic Loot Worker trả lỗi."))
        }
        val itemJson = json.optJSONObject("item") ?: json
        val item = DynamicLootItem.fromJson(itemJson)
        require(item.imageUrl.isNotBlank()) { "Worker không trả ảnh." }
        return FetchedLoot(item, loadBitmap(item.imageUrl))
    }

    private fun fetchFromWikipedia(seed: String, rarity: String, locale: String, theme: String = ""): FetchedLoot {
        val languageOrder = linkedSetOf(
            locale.lowercase(Locale.ROOT).substringBefore('-').takeIf { it in setOf("vi", "en") } ?: "vi",
            "en"
        )
        val searchTerm = THEME_SEARCH_QUERIES[theme.trim().lowercase(Locale.ROOT)].orEmpty()
        var lastError: Throwable? = null
        languageOrder.forEach { language ->
            repeat(3) { attempt ->
                try {
                    // Có mục tiêu sưu tập do người dùng chọn (vd "anime") thì tìm
                    // theo chủ đề đó (generator=search) thay vì lấy trang ngẫu nhiên.
                    val query = if (searchTerm.isNotBlank()) {
                        "https://$language.wikipedia.org/w/api.php" +
                            "?action=query&generator=search&gsrsearch=${encode(searchTerm)}&gsrlimit=20" +
                            "&prop=pageimages%7Cextracts%7Cpageprops" +
                            "&piprop=thumbnail&pithumbsize=640" +
                            "&exintro=1&explaintext=1&exsentences=2&format=json&origin=*"
                    } else {
                        "https://$language.wikipedia.org/w/api.php" +
                            "?action=query&generator=random&grnnamespace=0&grnlimit=12" +
                            "&prop=pageimages%7Cextracts%7Cpageprops" +
                            "&piprop=thumbnail&pithumbsize=640" +
                            "&exintro=1&explaintext=1&exsentences=2&format=json&origin=*"
                    }
                    val root = JSONObject(readText(query))
                    val pages = root.optJSONObject("query")?.optJSONObject("pages")
                        ?: error("Wikipedia không trả danh sách trang.")
                    val candidates = mutableListOf<JSONObject>()
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val page = pages.optJSONObject(keys.next()) ?: continue
                        val image = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                        val extract = page.optString("extract").trim()
                        if (image.startsWith("http") && extract.length >= 24) candidates += page
                    }
                    if (candidates.isEmpty()) error("Không tìm thấy bài ngẫu nhiên có ảnh.")
                    val indexSeed = "$seed|$language|$attempt".hashCode().absoluteValue
                    val page = candidates[indexSeed % candidates.size]
                    val pageId = page.optLong("pageid", 0L)
                    val title = page.optString("title", "Kỳ Vật Vô Danh").trim()
                    val extract = page.optString("extract").trim().replace(Regex("\\s+"), " ")
                    val imageUrl = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                    val category = classify(title, extract)
                    val normalizedRarity = DynamicLootItem.normalizeRarity(rarity)
                    val qid = page.optJSONObject("pageprops")?.optString("wikibase_item").orEmpty()
                    val item = DynamicLootItem(
                        id = if (qid.isNotBlank()) "wikidata-$qid" else "wikipedia-$language-$pageId",
                        name = title.take(100),
                        category = category,
                        description = extract.take(420),
                        rarity = normalizedRarity,
                        stars = DynamicLootItem.starsForRarity(normalizedRarity),
                        imageUrl = imageUrl,
                        sourceType = "wikimedia",
                        sourceUrl = "https://$language.wikipedia.org/?curid=$pageId",
                        attribution = "Wikipedia / Wikimedia Commons",
                        license = "Xem giấy phép tại trang nguồn",
                        generated = false
                    )
                    return FetchedLoot(item, loadBitmap(imageUrl))
                } catch (error: Throwable) {
                    lastError = error
                }
            }
        }
        throw IllegalStateException("Không lấy được Kỳ Vật ngẫu nhiên.", lastError)
    }

    private fun classify(title: String, description: String): String {
        val text = "$title $description".lowercase(Locale.ROOT)
        return when {
            listOf("sinh năm", "mất năm", "nhà văn", "họa sĩ", "ca sĩ", "scientist", "born", "politician", "actor", "human").any(text::contains) -> "Nhân vật"
            listOf("loài", "động vật", "chim", "cá", "thú", "species", "animal", "bird", "mammal", "fish").any(text::contains) -> "Động vật"
            listOf("thực vật", "cây", "hoa", "plant", "tree", "flower").any(text::contains) -> "Thực vật"
            listOf("thành phố", "quốc gia", "núi", "sông", "đảo", "city", "country", "mountain", "river", "island").any(text::contains) -> "Địa danh"
            listOf("tòa nhà", "công trình", "nhà thờ", "đền", "building", "church", "temple", "bridge").any(text::contains) -> "Công trình"
            listOf("tranh", "tượng", "tác phẩm", "painting", "sculpture", "artwork", "novel", "film").any(text::contains) -> "Tác phẩm"
            listOf("hành tinh", "ngôi sao", "thiên hà", "planet", "star", "galaxy", "asteroid").any(text::contains) -> "Thiên thể"
            listOf("thiết bị", "máy", "vũ khí", "tàu", "xe", "device", "machine", "weapon", "ship", "vehicle").any(text::contains) -> "Đồ vật"
            else -> "Kỳ Vật"
        }
    }

    private fun loadBitmap(source: String): Bitmap {
        if (source.startsWith("data:image/")) {
            val comma = source.indexOf(',')
            require(comma > 0) { "Dữ liệu ảnh AI không hợp lệ." }
            val bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT)
            return BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
                ?: error("Không giải mã được ảnh AI.")
        }
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "lqlq-browser-android/0.32 (dynamic-loot)")
            setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
        }
        return try {
            connection.connect()
            require(connection.responseCode in 200..299) { "Không tải được ảnh (${connection.responseCode})." }
            BitmapFactory.decodeStream(connection.inputStream) ?: error("Ảnh không hợp lệ.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "lqlq-browser-android/0.32 (dynamic-loot)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.connect()
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val bytes = ByteArrayOutputStream().use { output ->
                stream.use { input -> input.copyTo(output) }
                output.toByteArray()
            }
            val body = String(bytes, StandardCharsets.UTF_8)
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}: ${body.take(180)}" }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
