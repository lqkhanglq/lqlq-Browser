package com.lqlq.browser.automation.metadata

class HeuristicMetadataGenerator : MetadataGenerator {

    override fun generate(request: MetadataGenerationRequest): MetadataPlan {
        val normalizedTopic = request.topic.trim().replace(Regex("\\s+"), " ")
        val normalizedLanguage = request.language.trim().ifBlank { "vi" }
        val category = inferCategory(normalizedTopic, request.generatedText)
        val baseTitle = buildBaseTitle(normalizedTopic)
        val shortTitle = baseTitle.take(50)
        val description = buildDescription(normalizedTopic, request)
        val hashtags = buildHashtags(normalizedTopic, category)
        val thumbnailText = buildThumbnailText(normalizedTopic)
        val safetyNotes = buildSafetyNotes(request)
        val youtubeDescription = buildString {
            append(description)
            if (hashtags.isNotEmpty()) {
                append("\n\n")
                append(hashtags.joinToString(" "))
            }
        }
        return MetadataPlan(
            title = baseTitle,
            shortTitle = shortTitle,
            description = description,
            hashtags = hashtags,
            language = normalizedLanguage,
            category = category,
            thumbnailText = thumbnailText,
            platforms = linkedMapOf(
                "youtube_shorts" to PlatformMetadata(
                    title = baseTitle,
                    description = youtubeDescription,
                    hashtags = hashtags,
                    visibility = "private"
                ),
                "tiktok" to PlatformMetadata(
                    caption = "$shortTitle\n${hashtags.joinToString(" ")}",
                    hashtags = hashtags
                ),
                "facebook_reels" to PlatformMetadata(
                    caption = "$description\n${hashtags.joinToString(" ")}",
                    hashtags = hashtags
                )
            ),
            sourceSceneCount = request.sceneCount,
            sourceVoiceDurationMs = request.voiceDurationMs,
            sourceVideoDurationMs = request.videoDurationMs,
            safetyNotes = safetyNotes
        )
    }

    private fun buildBaseTitle(topic: String): String {
        return topic
            .removePrefix("Chủ đề:")
            .removePrefix("Chu de:")
            .trim()
            .ifBlank { "Video ngắn tự động từ lqlq Browser" }
            .let { value ->
                if (value.length <= 80) value else value.take(77).trimEnd() + "..."
            }
    }

    private fun buildDescription(topic: String, request: MetadataGenerationRequest): String {
        val durationSeconds = (request.videoDurationMs.takeIf { it > 0L } ?: request.voiceDurationMs) / 1000L
        val summaryLine = if (request.generatedText.isBlank()) {
            "Video ngắn được tạo tự động từ chủ đề: $topic."
        } else {
            request.generatedText
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.take(180)
                ?.trim()
                .orEmpty()
        }
        return buildString {
            append("Chủ đề: $topic\n")
            append("Video dọc ${request.sceneCount} cảnh")
            if (durationSeconds > 0L) {
                append(" · khoảng ${durationSeconds}s")
            }
            append(".\n")
            if (summaryLine.isNotBlank()) {
                append(summaryLine)
            }
        }.trim()
    }

    private fun buildHashtags(topic: String, category: String): List<String> {
        val lowered = topic.lowercase()
        val tags = linkedSetOf(
            "#shorts",
            "#reels",
            "#tiktok"
        )
        when (category) {
            "communication" -> tags += listOf(
                "#giaotiep",
                "#kynanggiaotiep",
                "#phattrienbanthan",
                "#baihoccuocsong",
                "#dongluc"
            )
            "motivation" -> tags += listOf(
                "#dongluc",
                "#truyencamhung",
                "#phattrienbanthan",
                "#thanhcong",
                "#baihoccuocsong"
            )
            else -> tags += listOf(
                "#giaoduc",
                "#chiase",
                "#phattrienbanthan",
                "#baihoccuocsong",
                "#dongluc"
            )
        }
        if (lowered.contains("giao tiếp") || lowered.contains("giao tiep")) {
            tags += "#giaotiep"
            tags += "#kynanggiaotiep"
        }
        return tags.take(12)
    }

    private fun buildThumbnailText(topic: String): String {
        val compact = topic.replace(Regex("[\"“”]"), "").trim()
        return if (compact.length <= 30) compact else compact.take(27).trimEnd() + "..."
    }

    private fun inferCategory(topic: String, generatedText: String): String {
        val normalized = "$topic $generatedText".lowercase()
        return when {
            normalized.contains("giao tiếp") || normalized.contains("giao tiep") -> "communication"
            normalized.contains("động lực") || normalized.contains("dong luc") || normalized.contains("truyền cảm hứng") -> "motivation"
            else -> "education"
        }
    }

    private fun buildSafetyNotes(request: MetadataGenerationRequest): List<String> {
        val notes = mutableListOf<String>()
        if (request.videoDurationMs in 1 until 10_000L) {
            notes += "Video khá ngắn, nên kiểm tra lại trước khi đăng."
        }
        if (request.imageCount < request.sceneCount) {
            notes += "Số ảnh ít hơn số cảnh, nên review hình ảnh/video trước khi publish."
        }
        if (request.generatedText.length > 4_000) {
            notes += "Mô tả script dài, nên kiểm tra title và description để tránh quá tải."
        }
        return notes.take(4)
    }
}
