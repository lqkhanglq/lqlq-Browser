package com.lqlq.browser.automation.script

data class ContentDurationPolicy(
    val isListicle: Boolean,
    val targetItemCount: Int?,
    val targetSceneCount: Int,
    val targetDurationMs: Long,
    val includeIntro: Boolean,
    val includeOutro: Boolean,
    val explicitDuration: Boolean
) {
    companion object {
        private val LISTICLE_REGEX = Regex(
            "(\\d{1,2})\\s*(cau noi|cau|cach|bai hoc|thoi quen|meo|ly do|buoc|dieu|tips?)",
            RegexOption.IGNORE_CASE
        )
        private val SECONDS_REGEX = Regex(
            "(\\d{1,3})\\s*(giay|s|sec|secs|second|seconds)",
            RegexOption.IGNORE_CASE
        )
        private val MINUTES_REGEX = Regex(
            "(\\d{1,2})\\s*(phut|min|minute|minutes)",
            RegexOption.IGNORE_CASE
        )

        fun fromTopic(
            topic: String,
            desiredDurationSeconds: Int? = null,
            requestedSceneCount: Int? = null
        ): ContentDurationPolicy {
            val normalized = normalizeVietnamese(topic.trim())
            val itemCount = parseListicleItemCount(normalized)
            val explicitDurationMs = desiredDurationSeconds
                ?.takeIf { it > 0 }
                ?.let { it * 1000L }
                ?: parseExplicitDurationMs(normalized)
            val isListicle = itemCount != null
            val includeIntro = isListicle
            val includeOutro = isListicle
            val inferredSceneCount = when {
                isListicle -> itemCount!! + (if (includeIntro) 1 else 0) + (if (includeOutro) 1 else 0)
                else -> 3
            }
            val targetSceneCount = requestedSceneCount
                ?.takeIf { it > 0 }
                ?.coerceIn(1, 24)
                ?: inferredSceneCount
            val targetDurationMs = explicitDurationMs ?: when {
                !isListicle -> 35_000L
                itemCount!! <= 5 -> 35_000L
                itemCount <= 10 -> 60_000L
                else -> 80_000L
            }
            return ContentDurationPolicy(
                isListicle = isListicle,
                targetItemCount = itemCount,
                targetSceneCount = targetSceneCount,
                targetDurationMs = targetDurationMs,
                includeIntro = includeIntro,
                includeOutro = includeOutro,
                explicitDuration = explicitDurationMs != null
            )
        }

        private fun parseListicleItemCount(topic: String): Int? {
            return LISTICLE_REGEX.find(topic)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        private fun parseExplicitDurationMs(topic: String): Long? {
            SECONDS_REGEX.find(topic)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds ->
                return seconds * 1000L
            }
            MINUTES_REGEX.find(topic)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { minutes ->
                return minutes * 60_000L
            }
            return null
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
            }
        }
    }
}
