package com.lqlq.browser.automation.visual

import com.lqlq.browser.automation.connector.image.AutomationImageProviders
import com.lqlq.browser.automation.image.ScenePrompt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, deterministic bridge between SCENE_PROMPTS and the future renderer.
 *
 * The intent is to stop treating every scene as "generate a fresh AI image".
 * For cheap/high-volume shorts this planner prefers reusable templates and stock
 * media; paid AI image/video can still be selected later per scene.
 */
data class VisualAssetPlanRequest(
    val topic: String,
    val contentType: String,
    val scenePrompts: List<ScenePrompt>,
    val selectedProviderId: String?,
    val selectedProviderCostType: String?,
    val targetAspectRatio: String
)

data class VisualAssetPlan(
    val sceneId: String,
    val ordinal: Int,
    val strategy: String,
    val preferredProviderId: String?,
    val assetQuery: String,
    val templateId: String,
    val renderMode: String,
    val durationMs: Long,
    val rationale: String
)

interface VisualAssetPlanner {
    fun planAssets(request: VisualAssetPlanRequest): List<VisualAssetPlan>
}

class HeuristicVisualAssetPlanner : VisualAssetPlanner {
    override fun planAssets(request: VisualAssetPlanRequest): List<VisualAssetPlan> {
        val providerId = request.selectedProviderId?.trim()?.lowercase()?.ifBlank { null }
        val topicKind = classifyTopic(request.topic, request.contentType)
        val strategy = chooseStrategy(providerId, request.selectedProviderCostType, topicKind)
        val templateId = chooseTemplate(topicKind, request.targetAspectRatio)
        val renderMode = when (strategy) {
            STRATEGY_STOCK_VIDEO_LOOP -> "stock_video_loop_voice_caption_mix"
            STRATEGY_STOCK_PHOTO_KEN_BURNS -> "stock_photo_ken_burns_voice_caption_mix"
            STRATEGY_AI_IMAGE_KEN_BURNS -> "ai_image_ken_burns_voice_caption_mix"
            STRATEGY_PREMIUM_IMAGE_TO_VIDEO -> "premium_image_to_video_then_mix"
            else -> "template_background_voice_caption_mix"
        }

        return request.scenePrompts.sortedBy { it.ordinal }.map { scene ->
            VisualAssetPlan(
                sceneId = scene.sceneId,
                ordinal = scene.ordinal,
                strategy = strategy,
                preferredProviderId = providerId,
                assetQuery = buildAssetQuery(request.topic, scene, topicKind),
                templateId = templateId,
                renderMode = renderMode,
                durationMs = DEFAULT_SCENE_DURATION_MS,
                rationale = buildRationale(strategy, topicKind, providerId)
            )
        }
    }

    private fun classifyTopic(topic: String, contentType: String): String {
        val text = "$topic $contentType".lowercase()
        return when {
            listOf("cham ngon", "châm ngôn", "cau noi", "câu nói", "quote", "quotes", "tip", "tips", "giao tiep", "giao tiếp").any { token -> text.contains(token) } -> TOPIC_QUOTES_TIPS
            listOf("truyen", "truyện", "story", "ke chuyen", "kể chuyện", "review phim", "phim").any { token -> text.contains(token) } -> TOPIC_STORY
            listOf("huong dan", "hướng dẫn", "tutorial", "lesson", "bai hoc", "bài học").any { token -> text.contains(token) } -> TOPIC_EDUCATION
            else -> TOPIC_GENERAL_SHORT
        }
    }

    private fun chooseStrategy(
        providerId: String?,
        costType: String?,
        topicKind: String
    ): String {
        if (providerId == AutomationImageProviders.COVERR) return STRATEGY_STOCK_VIDEO_LOOP
        if (providerId == AutomationImageProviders.PEXELS || providerId == AutomationImageProviders.PIXABAY) {
            return STRATEGY_STOCK_PHOTO_KEN_BURNS
        }
        if (costType?.equals("STOCK_MEDIA", ignoreCase = true) == true) return STRATEGY_STOCK_PHOTO_KEN_BURNS
        if (topicKind == TOPIC_QUOTES_TIPS && providerId == null) return STRATEGY_REUSABLE_BACKGROUND_TEMPLATE
        if (providerId == AutomationImageProviders.OPENAI_IMAGES || providerId == AutomationImageProviders.CLOUDFLARE_WORKERS_AI) {
            return STRATEGY_AI_IMAGE_KEN_BURNS
        }
        return STRATEGY_REUSABLE_BACKGROUND_TEMPLATE
    }

    private fun chooseTemplate(topicKind: String, aspectRatio: String): String {
        val ratio = aspectRatio.replace(Regex("[^0-9:]"), "").ifBlank { "9:16" }
        return when (topicKind) {
            TOPIC_QUOTES_TIPS -> "shorts_${ratio}_quote_stack_kenburns_v1"
            TOPIC_STORY -> "shorts_${ratio}_story_cards_kenburns_v1"
            TOPIC_EDUCATION -> "shorts_${ratio}_lesson_cards_kenburns_v1"
            else -> "shorts_${ratio}_clean_caption_kenburns_v1"
        }
    }

    private fun buildAssetQuery(topic: String, scene: ScenePrompt, topicKind: String): String {
        val canonicalTopic = extractCanonicalTopic(topic)
        val preferredQuery = cleanStockQuery(scene.stockSearchQuery)
        if (preferredQuery.isNotBlank()) {
            return diversifyStockQuery(preferredQuery, topicKind, scene.ordinal)
        }
        val preferredDirection = cleanStockQuery(scene.visualDirection)
        if (preferredDirection.isNotBlank()) {
            return diversifyStockQuery(preferredDirection, topicKind, scene.ordinal)
        }
        val seed = when (topicKind) {
            TOPIC_QUOTES_TIPS -> topicKindFallbackQuery(scene.ordinal)
            TOPIC_STORY -> "cinematic dramatic atmosphere"
            TOPIC_EDUCATION -> "learning technology workspace"
            else -> "cinematic mobile vertical background"
        }
        val cleanedTopic = normalizeQueryWords(canonicalTopic, 5)
        val cleanedSummary = normalizeQueryWords(scene.summary, 5)
        return listOf(seed, cleanedTopic, cleanedSummary)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_QUERY_CHARS_FOR_STOCK)
    }

    private fun cleanStockQuery(value: String): String {
        if (value.isBlank()) return ""
        val cleaned = value
            .substringAfter(':', value)
            .replace(Regex("(?i)\\b(scene|video|shorts|1080x1920|format)\\b"), " ")
            .replace(Regex("\\b9:16\\b"), " ")
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(MAX_QUERY_CHARS_FOR_STOCK)
    }

    private fun diversifyStockQuery(query: String, topicKind: String, ordinal: Int): String {
        if (topicKind != TOPIC_QUOTES_TIPS) {
            return query
        }
        val normalized = normalizeVietnamese(query)
        val generic = normalized in setOf("giao tiep", "quotes", "tips", "communication")
        return if (generic) topicKindFallbackQuery(ordinal) else query
    }

    private fun extractCanonicalTopic(userInput: String): String {
        userInput
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .firstOrNull { normalizeVietnamese(it).startsWith("chu de:") }
            ?.substringAfter(':', "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return userInput.replace(Regex("\\s+"), " ").trim()
    }

    private fun topicKindFallbackQuery(ordinal: Int): String {
        return when (ordinal) {
            1 -> "communication intro"
            2 -> "confident speaker"
            3 -> "active listening"
            4 -> "friendly conversation"
            5 -> "teamwork meeting"
            6 -> "coffee conversation"
            7 -> "public speaking"
            8 -> "thoughtful discussion"
            9 -> "apology conversation"
            10 -> "respectful feedback"
            11 -> "calm office talk"
            else -> "successful communication"
        }
    }

    private fun normalizeQueryWords(value: String, maxWords: Int): String {
        val normalized = value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return ""
        return normalized.split(' ')
            .filter { it.length >= 3 }
            .take(maxWords)
            .joinToString(" ")
    }

    private fun normalizeVietnamese(value: String): String {
        return buildString(value.length) {
            value.lowercase().forEach { character ->
                append(
                    when (character) {
                        'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                        'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                        'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                        'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                        'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                        'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                        'đ' -> 'd'
                        else -> character
                    }
                )
            }
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun buildRationale(strategy: String, topicKind: String, providerId: String?): String {
        return when (strategy) {
            STRATEGY_STOCK_VIDEO_LOOP -> "Dung stock video loop de giam chi phi AI video va giu nhịp shorts. Topic=$topicKind provider=$providerId."
            STRATEGY_STOCK_PHOTO_KEN_BURNS -> "Dung stock photo + Ken Burns/parallax 2.5D; phu hop san xuat so luong lon, tranh phai tao moi 10 anh AI. Topic=$topicKind provider=$providerId."
            STRATEGY_AI_IMAGE_KEN_BURNS -> "Dung AI image cho scene quan trong roi render Ken Burns; re hon image-to-video moi canh. Topic=$topicKind provider=$providerId."
            STRATEGY_PREMIUM_IMAGE_TO_VIDEO -> "Chi dung image-to-video tra phi cho scene premium/canh mo dau, khong dung mac dinh moi canh."
            else -> "Dung template nen co san + subtitle/voice, uu tien toc do va chi phi thap. Topic=$topicKind."
        }
    }

    companion object {
        const val STRATEGY_STOCK_VIDEO_LOOP = "STOCK_VIDEO_LOOP"
        const val STRATEGY_STOCK_PHOTO_KEN_BURNS = "STOCK_PHOTO_KEN_BURNS"
        const val STRATEGY_AI_IMAGE_KEN_BURNS = "AI_IMAGE_KEN_BURNS"
        const val STRATEGY_PREMIUM_IMAGE_TO_VIDEO = "PREMIUM_IMAGE_TO_VIDEO"
        const val STRATEGY_REUSABLE_BACKGROUND_TEMPLATE = "REUSABLE_BACKGROUND_TEMPLATE"

        private const val TOPIC_QUOTES_TIPS = "quotes_tips"
        private const val TOPIC_STORY = "story"
        private const val TOPIC_EDUCATION = "education"
        private const val TOPIC_GENERAL_SHORT = "general_short"
        private const val DEFAULT_SCENE_DURATION_MS = 4_500L
        private const val MAX_QUERY_CHARS = 180
        private const val MAX_QUERY_CHARS_FOR_STOCK = 80
    }
}

object VisualAssetPlanJson {
    fun encode(plans: List<VisualAssetPlan>): String {
        return JSONObject()
            .put("schema", "lqlq.visual_asset_plan.v1")
            .put("plans", JSONArray().apply {
                plans.sortedBy { it.ordinal }.forEach { plan ->
                    put(
                        JSONObject()
                            .put("sceneId", plan.sceneId)
                            .put("ordinal", plan.ordinal)
                            .put("strategy", plan.strategy)
                            .put("preferredProviderId", plan.preferredProviderId)
                            .put("assetQuery", plan.assetQuery)
                            .put("templateId", plan.templateId)
                            .put("renderMode", plan.renderMode)
                            .put("durationMs", plan.durationMs)
                            .put("rationale", plan.rationale)
                    )
                }
            })
            .toString(2)
    }
}
